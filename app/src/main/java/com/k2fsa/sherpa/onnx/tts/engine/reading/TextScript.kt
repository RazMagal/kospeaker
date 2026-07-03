package com.k2fsa.sherpa.onnx.tts.engine.reading

/**
 * The writing system a piece of text is (predominantly) written in.
 *
 * Only the scripts the reading pipeline needs to behave differently for are
 * modelled explicitly; everything else collapses into [OTHER].
 *
 *  - [LATIN]  – English and other Latin-alphabet prose (has upper/lower case).
 *  - [HEBREW] – Hebrew prose (right-to-left, no letter case, own punctuation).
 *  - [MIXED]  – a substantial amount of *both* Latin and Hebrew letters.
 *  - [OTHER]  – neither script is present in any meaningful amount (digits,
 *               punctuation, whitespace, or some other script entirely).
 */
enum class TextScript { LATIN, HEBREW, MIXED, OTHER }

/**
 * How many times more letters the leading script must have than the trailing
 * one before it is considered to "clearly dominate". A ratio of 4 means the
 * winning script has to make up at least ~80% of the counted letters; anything
 * more balanced than that is reported as [TextScript.MIXED].
 */
private const val DOMINANCE_RATIO = 4

/**
 * True for a Hebrew *consonant* code point (`U+05D0`‥`U+05EA`, alef‥tav, final
 * forms included). This is the sub-range of the Hebrew block that actually
 * carries letters; the surrounding block (`U+0590`‥`U+05FF`) also holds niqqud,
 * cantillation marks and punctuation, none of which should count as a "letter"
 * for script detection.
 */
fun isHebrew(cp: Int): Boolean = cp in 0x05D0..0x05EA

/**
 * True for a Latin letter: ASCII `A`‥`Z` / `a`‥`z` plus the accented letters of
 * Latin-1 Supplement (excluding the `×`/`÷` math signs) and Latin Extended-A/B,
 * so words like "café" or "naïve" still count as Latin.
 */
fun isLatinLetter(cp: Int): Boolean =
    cp in 0x41..0x5A ||
        cp in 0x61..0x7A ||
        (cp in 0x00C0..0x00FF && cp != 0x00D7 && cp != 0x00F7) ||
        cp in 0x0100..0x024F

/**
 * Detects the dominant [TextScript] of [text] by counting Hebrew consonants
 * versus Latin letters (all other characters are ignored).
 *
 * Thresholds:
 *  - if neither script contributes a single letter → [TextScript.OTHER];
 *  - if one script has at least [DOMINANCE_RATIO]× the letters of the other
 *    (which also covers the "only one script present" case) → that script;
 *  - otherwise both are substantial and roughly balanced → [TextScript.MIXED].
 */
fun detectScript(text: String): TextScript {
    var hebrew = 0
    var latin = 0
    for (ch in text) {
        val cp = ch.code
        when {
            isHebrew(cp) -> hebrew++
            isLatinLetter(cp) -> latin++
        }
    }

    if (hebrew + latin == 0) return TextScript.OTHER
    return when {
        hebrew >= DOMINANCE_RATIO * latin -> TextScript.HEBREW // includes latin == 0
        latin >= DOMINANCE_RATIO * hebrew -> TextScript.LATIN  // includes hebrew == 0
        else -> TextScript.MIXED
    }
}
