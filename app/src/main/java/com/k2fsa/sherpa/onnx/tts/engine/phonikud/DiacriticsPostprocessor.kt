package com.k2fsa.sherpa.onnx.tts.engine.phonikud

/**
 * Diacritizer (nakdan) post-processing — pure-Kotlin port of thewh1teagle/phonikud
 * `phonikud_onnx/src/phonikud_onnx/model.py` (the `predict()` output-assembly logic).
 *
 * This class contains ONLY the pure decoding logic. It takes the three already-computed logit
 * arrays and the aligned source characters, and returns the niqqud'd Hebrew string. NO ONNX,
 * NO Android, NO tokenizer here (the model is char-level, so each logit row aligns to exactly
 * one source character; special tokens must be excluded by the caller).
 *
 * Heads (model.py):
 *   - nikud_logits [seq][29]  -> argmax -> NIKUD_CLASSES
 *   - shin_logits  [seq][2]   -> argmax -> SHIN_CLASSES  (only meaningful on ש)
 *   - additional_logits [seq][3] -> per-channel `> 0` binary classifiers:
 *        [0] stress -> ֫ (U+05AB),  [1] vocal shva -> ֽ (U+05BD),  [2] prefix -> "|"
 *
 * Emission order per Hebrew letter (model.py): char + shin + nikud + stress + vocalShva + prefix.
 */
object DiacriticsPostprocessor {

    const val MAT_LECT_TOKEN = "<MAT_LECT>"

    // model.py NIKUD_CLASSES (29 entries)
    val NIKUD_CLASSES: List<String> = listOf(
        "",
        MAT_LECT_TOKEN,        // "<MAT_LECT>"
        "ּ",              // U+05BC
        "ְ",              // U+05B0
        "ֱ",              // U+05B1
        "ֲ",              // U+05B2
        "ֳ",              // U+05B3
        "ִ",              // U+05B4
        "ֵ",              // U+05B5
        "ֶ",              // U+05B6
        "ַ",              // U+05B7
        "ָ",              // U+05B8
        "ֹ",              // U+05B9
        "ֺ",              // U+05BA
        "ֻ",              // U+05BB
        "ְּ",        // U+05BC U+05B0
        "ֱּ",        // U+05BC U+05B1
        "ֲּ",        // U+05BC U+05B2
        "ֳּ",        // U+05BC U+05B3
        "ִּ",        // U+05BC U+05B4
        "ֵּ",        // U+05BC U+05B5
        "ֶּ",        // U+05BC U+05B6
        "ַּ",        // U+05BC U+05B7
        "ָּ",        // U+05BC U+05B8
        "ֹּ",        // U+05BC U+05B9
        "ֺּ",        // U+05BC U+05BA
        "ֻּ",        // U+05BC U+05BB
        "ׇ",              // U+05C7
        "ׇּ",        // U+05BC U+05C7
    )

    // model.py SHIN_CLASSES = ["ׁ", "ׂ"] (shin dot, sin dot)
    val SHIN_CLASSES: List<String> = listOf("ׁ", "ׂ")

    const val STRESS_CHAR = "֫"       // U+05AB "ole" marks stress
    const val VOCAL_SHVA_CHAR = "ֽ"   // U+05BD "meteg" marks vocal (mobile) shva
    const val PREFIX_CHAR = "|"

    private const val ALEF_ORD = 0x05D0
    private const val TAF_ORD = 0x05EA
    private val MATRES_LETTERS = setOf('א', 'ו', 'י') // אוי

    fun isHebrewLetter(c: Char): Boolean = c.code in ALEF_ORD..TAF_ORD
    fun isMatresLetter(c: Char): Boolean = c in MATRES_LETTERS

    private fun argmax(row: FloatArray): Int {
        var best = 0
        for (i in 1 until row.size) if (row[i] > row[best]) best = i
        return best
    }

    /**
     * Assemble the niqqud'd string.
     *
     * @param chars           the source characters, one per logit row (special tokens excluded).
     * @param nikudLogits     [seq][29]
     * @param shinLogits      [seq][2]
     * @param additionalLogits[seq][3]
     * @param markMatresLectionis  if non-null, replaces a predicted <MAT_LECT> on a matres letter
     *                             with this marker; if null, matres letters keep no diacritic.
     */
    fun postprocess(
        chars: List<Char>,
        nikudLogits: Array<FloatArray>,
        shinLogits: Array<FloatArray>,
        additionalLogits: Array<FloatArray>,
        markMatresLectionis: String? = null,
    ): String {
        val output = StringBuilder()
        for (idx in chars.indices) {
            val char = chars[idx]
            if (!isHebrewLetter(char)) {
                output.append(char)
                continue
            }

            var nikud = NIKUD_CLASSES[argmax(nikudLogits[idx])]
            val shin = if (char != 'ש') "" else SHIN_CLASSES[argmax(shinLogits[idx])]

            // Check for matres lectionis
            if (nikud == MAT_LECT_TOKEN) {
                if (!isMatresLetter(char)) {
                    nikud = "" // Don't allow matres on irrelevant letters
                } else if (markMatresLectionis != null) {
                    nikud = markMatresLectionis
                } else {
                    output.append(char)
                    continue
                }
            }

            val add = additionalLogits[idx]
            val stress = if (add[0] > 0f) STRESS_CHAR else ""
            val vocalShva = if (add[1] > 0f) VOCAL_SHVA_CHAR else ""
            val prefix = if (add[2] > 0f) PREFIX_CHAR else ""

            output.append(char).append(shin).append(nikud).append(stress).append(vocalShva).append(prefix)
        }
        return output.toString()
    }
}
