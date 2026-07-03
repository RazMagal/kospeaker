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
        val diacritized = diacritizer.diacritize(hebrew)
        val audio = voice.synthesize(diacritized)
        android.util.Log.i(
            "KoSpeakerPhonikud",
            "in='${hebrew.take(40)}' niqqud='${diacritized.take(60)}' samples=${audio.size}",
        )
        return audio
    }

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
