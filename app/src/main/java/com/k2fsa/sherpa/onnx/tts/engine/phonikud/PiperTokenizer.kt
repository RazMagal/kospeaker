package com.k2fsa.sherpa.onnx.tts.engine.phonikud

/**
 * Piper phoneme framing — pure-Kotlin port of thewh1teagle/piper-onnx
 * (src/piper_onnx/__init__.py) plus the phoneme_id_map / inference scales from the phonikud
 * voice config (huggingface: thewh1teagle/phonikud-tts-checkpoints/model.config.json).
 *
 * Framing (piper `_phoneme_to_ids` with BOS inserted at position 0):
 *   phonemes = [BOS] + list(phoneme_string)
 *   for p in phonemes:  if p in map -> ids += map[p]; ids += map[PAD]
 *   ids += map[EOS]
 * i.e. [bos, pad, id(p1), pad, id(p2), pad, ..., id(pN), pad, eos].
 *
 * Multi-char phonemes (ts, tʃ, dʒ, …) are decomposed by iterating the string character by
 * character, because the map only contains single-character keys.
 *
 * PURE Kotlin/JVM: no Android, no ONNX.
 */
object PiperTokenizer {

    const val BOS = "^" // id 1
    const val EOS = "$" // id 2
    const val PAD = "_" // id 0

    // Inference scales / sample rate (model.config.json: inference + audio)
    const val NOISE_SCALE = 0.667f
    const val LENGTH_SCALE = 1.0f
    const val NOISE_W = 0.8f
    const val SAMPLE_RATE = 22050

    /**
     * The full 157-entry phoneme_id_map, embedded verbatim from model.config.json.
     * Every value in the source is a single-element list, stored here as a single Long.
     */
    val PHONEME_ID_MAP: Map<String, Long> = mapOf(
        " " to 3L,
        "!" to 4L,
        "\"" to 150L,
        "#" to 149L,
        "\$" to 2L,
        "'" to 5L,
        "(" to 6L,
        ")" to 7L,
        "," to 8L,
        "-" to 9L,
        "." to 10L,
        "0" to 130L,
        "1" to 131L,
        "2" to 132L,
        "3" to 133L,
        "4" to 134L,
        "5" to 135L,
        "6" to 136L,
        "7" to 137L,
        "8" to 138L,
        "9" to 139L,
        ":" to 11L,
        ";" to 12L,
        "?" to 13L,
        "X" to 156L,
        "^" to 1L,
        "_" to 0L,
        "a" to 14L,
        "b" to 15L,
        "c" to 16L,
        "d" to 17L,
        "e" to 18L,
        "f" to 19L,
        "g" to 154L,
        "h" to 20L,
        "i" to 21L,
        "j" to 22L,
        "k" to 23L,
        "l" to 24L,
        "m" to 25L,
        "n" to 26L,
        "o" to 27L,
        "p" to 28L,
        "q" to 29L,
        "r" to 30L,
        "s" to 31L,
        "t" to 32L,
        "u" to 33L,
        "v" to 34L,
        "w" to 35L,
        "x" to 36L,
        "y" to 37L,
        "z" to 38L,
        "æ" to 39L,
        "ç" to 40L,
        "ð" to 41L,
        "ø" to 42L,
        "ħ" to 43L,
        "ŋ" to 44L,
        "œ" to 45L,
        "ǀ" to 46L,
        "ǁ" to 47L,
        "ǂ" to 48L,
        "ǃ" to 49L,
        "ɐ" to 50L,
        "ɑ" to 51L,
        "ɒ" to 52L,
        "ɓ" to 53L,
        "ɔ" to 54L,
        "ɕ" to 55L,
        "ɖ" to 56L,
        "ɗ" to 57L,
        "ɘ" to 58L,
        "ə" to 59L,
        "ɚ" to 60L,
        "ɛ" to 61L,
        "ɜ" to 62L,
        "ɞ" to 63L,
        "ɟ" to 64L,
        "ɠ" to 65L,
        "ɡ" to 66L,
        "ɢ" to 67L,
        "ɣ" to 68L,
        "ɤ" to 69L,
        "ɥ" to 70L,
        "ɦ" to 71L,
        "ɧ" to 72L,
        "ɨ" to 73L,
        "ɪ" to 74L,
        "ɫ" to 75L,
        "ɬ" to 76L,
        "ɭ" to 77L,
        "ɮ" to 78L,
        "ɯ" to 79L,
        "ɰ" to 80L,
        "ɱ" to 81L,
        "ɲ" to 82L,
        "ɳ" to 83L,
        "ɴ" to 84L,
        "ɵ" to 85L,
        "ɶ" to 86L,
        "ɸ" to 87L,
        "ɹ" to 88L,
        "ɺ" to 89L,
        "ɻ" to 90L,
        "ɽ" to 91L,
        "ɾ" to 92L,
        "ʀ" to 93L,
        "ʁ" to 94L,
        "ʂ" to 95L,
        "ʃ" to 96L,
        "ʄ" to 97L,
        "ʈ" to 98L,
        "ʉ" to 99L,
        "ʊ" to 100L,
        "ʋ" to 101L,
        "ʌ" to 102L,
        "ʍ" to 103L,
        "ʎ" to 104L,
        "ʏ" to 105L,
        "ʐ" to 106L,
        "ʑ" to 107L,
        "ʒ" to 108L,
        "ʔ" to 109L,
        "ʕ" to 110L,
        "ʘ" to 111L,
        "ʙ" to 112L,
        "ʛ" to 113L,
        "ʜ" to 114L,
        "ʝ" to 115L,
        "ʟ" to 116L,
        "ʡ" to 117L,
        "ʢ" to 118L,
        "ʦ" to 155L,
        "ʰ" to 145L,
        "ʲ" to 119L,
        "ˈ" to 120L,
        "ˌ" to 121L,
        "ː" to 122L,
        "ˑ" to 123L,
        "˞" to 124L,
        "ˤ" to 146L,
        "̃" to 141L,
        "̧" to 140L,
        "̩" to 144L,
        "̪" to 142L,
        "̯" to 143L,
        "̺" to 152L,
        "̻" to 153L,
        "β" to 125L,
        "ε" to 147L,
        "θ" to 126L,
        "χ" to 127L,
        "ᵻ" to 128L,
        "↑" to 151L,
        "↓" to 148L,
        "ⱱ" to 129L,
    )

    /**
     * Convert a phoneme string to Piper input ids:
     *   [bos, pad, id(p1), pad, id(p2), pad, ..., id(pN), pad, eos]
     * Characters not present in [PHONEME_ID_MAP] are skipped (as in piper's `_phoneme_to_ids`).
     */
    fun phonemesToIds(phonemes: String): LongArray {
        val padId = PHONEME_ID_MAP.getValue(PAD)
        val ids = ArrayList<Long>(phonemes.length * 2 + 4)
        // phonemes = [BOS] + list(phoneme_string)
        val tokens = ArrayList<String>(phonemes.length + 1)
        tokens.add(BOS)
        for (c in phonemes) tokens.add(c.toString())
        for (p in tokens) {
            val id = PHONEME_ID_MAP[p] ?: continue
            ids.add(id)
            ids.add(padId)
        }
        ids.add(PHONEME_ID_MAP.getValue(EOS))
        return ids.toLongArray()
    }
}
