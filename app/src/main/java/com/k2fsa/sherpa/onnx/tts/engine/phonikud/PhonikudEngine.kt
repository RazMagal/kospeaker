package com.k2fsa.sherpa.onnx.tts.engine.phonikud

/**
 * Premium offline Hebrew voice = diacritizer + Piper VITS voice.
 *
 * Hebrew text -> [PhonikudDiacritizer] (adds niqqud) -> [PhonikudVoice] (phonemize + synth) ->
 * PCM float @ 22050 Hz.
 *
 * Build via [create]; both ONNX sessions are loaded eagerly there (each is large, ~308MB +
 * ~64MB, so callers should cache the engine — TtsEngine does).
 *
 * !!! ON-DEVICE UNVERIFIED !!! See [PhonikudDiacritizer] / [PhonikudVoice].
 */
class PhonikudEngine private constructor(
    private val diacritizer: PhonikudDiacritizer,
    private val voice: PhonikudVoice,
) : AutoCloseable {

    val sampleRate: Int = 22050

    /** Hebrew (optionally bare) -> PCM float @ 22050 Hz. */
    fun synthesize(hebrew: String): FloatArray {
        val diacritized = diacritize(hebrew)
        val audio = synthesizeDiacritized(diacritized)
        android.util.Log.i(
            "KoSpeakerPhonikud",
            "in='${hebrew.take(40)}' niqqud='${diacritized.take(60)}' samples=${audio.size}",
        )
        return audio
    }

    /**
     * Run ONLY the heavy 308MB diacritizer (bare Hebrew -> Hebrew with niqqud).
     * Split out from [synthesize] so callers can diacritize a whole utterance ONCE
     * and then stream the fast VITS voice sentence-by-sentence (see
     * [synthesizeDiacritized]). Runs the big ONNX session; call off the main thread.
     */
    fun diacritize(text: String): String = diacritizer.diacritize(text)

    /**
     * Run ONLY the fast VITS voice on already-[diacritize]d Hebrew -> PCM float @ 22050 Hz.
     * Cheap enough to call per sentence chunk for low-latency streaming.
     */
    fun synthesizeDiacritized(diacritized: String): FloatArray = voice.synthesize(diacritized)

    override fun close() {
        try {
            diacritizer.close()
        } catch (_: Exception) {
        }
        try {
            voice.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        /**
         * Load both models from [modelDir] (expects phonikud.onnx, tokenizer.json, shaul.onnx).
         * Throws if a model/tokenizer is missing or fails to load.
         */
        fun create(modelDir: String): PhonikudEngine {
            val diacritizer = PhonikudDiacritizer(modelDir)
            val voice = try {
                PhonikudVoice(modelDir)
            } catch (e: Exception) {
                diacritizer.close()
                throw e
            }
            return PhonikudEngine(diacritizer, voice)
        }
    }
}
