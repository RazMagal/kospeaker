package com.k2fsa.sherpa.onnx.tts.engine.phonikud

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.io.File

/**
 * ANDROID / ONNX wrapper around the DictaBERT char-menaked diacritizer (nakdan).
 *
 * Pipeline: Hebrew text -> strip existing niqqud -> chunk -> [HebrewCharTokenizer] ->
 * int64 tensors -> phonikud.onnx -> per-head logits -> [DiacriticsPostprocessor] -> niqqud'd text.
 *
 * The pure decoding/tokenizing logic lives in the sibling PURE-Kotlin classes
 * ([HebrewCharTokenizer], [DiacriticsPostprocessor]); this class only handles ONNX I/O.
 *
 * !!! ON-DEVICE UNVERIFIED !!!
 * The ONNX input/output tensor shapes, output head names (nikud_logits / shin_logits /
 * additional_logits) and the CLS/SEP trimming were derived from the reference
 * thewh1teagle/phonikud-onnx model.py but have NOT been validated against the model running
 * on a device. Shapes/names may need adjustment after the first device run.
 *
 * Requires model files in [modelDir]:
 *   - phonikud.onnx  (the diacritizer)
 *   - tokenizer.json (HuggingFace tokenizers format; char-level model.vocab)
 */
class PhonikudDiacritizer(modelDir: String) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val vocab: Map<String, Int>

    // Existing niqqud / cantillation / Hebrew marks + prefix bar to remove before re-diacritizing.
    // U+0590..U+05C7 covers all niqqud & cantillation but NOT the letters (U+05D0..U+05EA).
    private val niqqudRegex = Regex("[֐-ׇ|]")

    init {
        val modelPath = File(modelDir, "phonikud.onnx").absolutePath
        session = env.createSession(modelPath, OrtSession.SessionOptions())
        vocab = loadVocab(File(modelDir, "tokenizer.json"))
    }

    /** Parse the char-level `model.vocab` map from a HuggingFace tokenizer.json. */
    private fun loadVocab(file: File): Map<String, Int> {
        val root = JSONObject(file.readText())
        val vocabJson = root.getJSONObject("model").getJSONObject("vocab")
        val map = HashMap<String, Int>(vocabJson.length())
        val keys = vocabJson.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = vocabJson.getInt(k)
        }
        // BERT special tokens must be present for HebrewCharTokenizer framing.
        for (t in listOf(
            HebrewCharTokenizer.CLS,
            HebrewCharTokenizer.SEP,
            HebrewCharTokenizer.PAD,
            HebrewCharTokenizer.UNK,
        )) {
            require(map.containsKey(t)) { "tokenizer.json vocab missing special token \"$t\"" }
        }
        return map
    }

    /** Add niqqud to (possibly bare) Hebrew text. */
    fun diacritize(hebrew: String): String {
        val stripped = niqqudRegex.replace(hebrew, "")
        if (stripped.isBlank()) return stripped
        val out = StringBuilder(stripped.length + stripped.length / 2)
        for (chunk in chunk(stripped, MAX_CHARS)) {
            if (chunk.isEmpty()) continue
            out.append(diacritizeChunk(chunk))
        }
        return out.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun diacritizeChunk(text: String): String {
        val enc = HebrewCharTokenizer.tokenize(text, vocab)
        val seq = enc.inputIds.size

        // int64 tensors of shape [1, seq]
        val inputIds = OnnxTensor.createTensor(env, arrayOf(enc.inputIds))
        val attentionMask = OnnxTensor.createTensor(env, arrayOf(enc.attentionMask))
        val tokenTypeIds = OnnxTensor.createTensor(env, arrayOf(enc.tokenTypeIds))

        val inputs = mapOf(
            "input_ids" to inputIds,
            "attention_mask" to attentionMask,
            "token_type_ids" to tokenTypeIds,
        )

        val result = session.run(inputs)
        try {
            // [1, seq, C] -> take batch 0 -> [seq, C]
            val nikud = (result.get("nikud_logits").get().value as Array<Array<FloatArray>>)[0]
            val shin = (result.get("shin_logits").get().value as Array<Array<FloatArray>>)[0]
            val additional = (result.get("additional_logits").get().value as Array<Array<FloatArray>>)[0]

            // Drop [CLS] at 0 and [SEP] at seq-1 so rows align 1:1 with source chars.
            val chars = text.toList()
            val count = minOf(seq - 2, chars.size)
            if (count <= 0) return text

            val nikudSlice = Array(count) { nikud[it + 1] }
            val shinSlice = Array(count) { shin[it + 1] }
            val addSlice = Array(count) { additional[it + 1] }

            return DiacriticsPostprocessor.postprocess(
                chars = chars.subList(0, count),
                nikudLogits = nikudSlice,
                shinLogits = shinSlice,
                additionalLogits = addSlice,
            )
        } finally {
            result.close()
            inputIds.close()
            attentionMask.close()
            tokenTypeIds.close()
        }
    }

    /** Split into <= [maxLen]-char chunks, preferring a break after '.' or newline. */
    private fun chunk(text: String, maxLen: Int): List<String> {
        if (text.length <= maxLen) return listOf(text)
        val out = ArrayList<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + maxLen, text.length)
            if (end < text.length) {
                val window = text.substring(start, end)
                val br = maxOf(window.lastIndexOf('.'), window.lastIndexOf('\n'))
                if (br > 0) end = start + br + 1
            }
            out.add(text.substring(start, end))
            start = end
        }
        return out
    }

    override fun close() {
        try {
            session.close()
        } catch (_: Exception) {
        }
    }

    private companion object {
        // BERT max positions 2048 minus [CLS] and [SEP].
        const val MAX_CHARS = 2046
    }
}
