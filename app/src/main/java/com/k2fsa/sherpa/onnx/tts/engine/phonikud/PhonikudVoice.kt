package com.k2fsa.sherpa.onnx.tts.engine.phonikud

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File

/**
 * ANDROID / ONNX wrapper around the phonikud Piper VITS voice (shaul.onnx, single speaker).
 *
 * Pipeline: diacritized Hebrew -> [HebrewPhonemizer] -> [PiperTokenizer] ids -> Piper VITS ->
 * PCM float @ 22050 Hz.
 *
 * The pure phonemizing/tokenizing logic lives in the sibling PURE-Kotlin classes; this class
 * only handles ONNX I/O.
 *
 * !!! ON-DEVICE UNVERIFIED !!!
 * The Piper input names (input / input_lengths / scales), tensor shapes and the [1,1,N] output
 * layout follow the reference thewh1teagle/piper-onnx runner but have NOT been validated on a
 * device. `sid` is intentionally omitted (single-speaker model). Input names are detected via
 * [OrtSession.getInputNames]; only the standard names actually present are fed.
 *
 * Requires model file in [modelDir]:
 *   - shaul.onnx  (the Piper VITS voice)
 */
class PhonikudVoice(modelDir: String) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputNames: Set<String>

    val sampleRate: Int = PiperTokenizer.SAMPLE_RATE // 22050

    init {
        val modelPath = File(modelDir, "shaul.onnx").absolutePath
        session = env.createSession(modelPath, OrtSession.SessionOptions())
        inputNames = session.inputNames
    }

    @Suppress("UNCHECKED_CAST")
    fun synthesize(diacritizedHebrew: String): FloatArray {
        val phonemes = HebrewPhonemizer.phonemize(diacritizedHebrew)
        val ids = PiperTokenizer.phonemesToIds(phonemes)
        if (ids.isEmpty()) return FloatArray(0)

        // input: int64[1, T]; input_lengths: int64[1] = {T}; scales: float32[3]
        val inputTensor = OnnxTensor.createTensor(env, arrayOf(ids))
        val lengthTensor = OnnxTensor.createTensor(env, longArrayOf(ids.size.toLong()))
        val scaleTensor = OnnxTensor.createTensor(
            env,
            floatArrayOf(
                PiperTokenizer.NOISE_SCALE,  // 0.667
                PiperTokenizer.LENGTH_SCALE, // 1.0
                PiperTokenizer.NOISE_W,      // 0.8
            ),
        )

        val inputs = LinkedHashMap<String, OnnxTensor>()
        if ("input" in inputNames) inputs["input"] = inputTensor
        if ("input_lengths" in inputNames) inputs["input_lengths"] = lengthTensor
        if ("scales" in inputNames) inputs["scales"] = scaleTensor
        // sid omitted: single-speaker model.

        val result = session.run(inputs)
        try {
            // output: float32[1, 1, N] -> flatten batch/channel.
            val audio = result.get(0).value as Array<Array<FloatArray>>
            return audio[0][0]
        } finally {
            result.close()
            inputTensor.close()
            lengthTensor.close()
            scaleTensor.close()
        }
    }

    override fun close() {
        try {
            session.close()
        } catch (_: Exception) {
        }
    }
}
