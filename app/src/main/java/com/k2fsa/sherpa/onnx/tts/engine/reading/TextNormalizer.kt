package com.k2fsa.sherpa.onnx.tts.engine.reading

/**
 * Configuration for [TextNormalizer]. Every clean-up step can be toggled
 * independently so callers can tune the behaviour (or unit-test one step in
 * isolation). All flags default to `true`, which is the recommended setting for
 * reading long-form EPUB prose aloud.
 *
 * @property collapseWhitespace     Collapse runs of whitespace/newlines (including
 *                                  non-breaking and other Unicode spaces) into a
 *                                  single ASCII space and trim the result.
 * @property stripInvisibleChars    Remove soft hyphens and zero-width characters
 *                                  that EPUB layout engines insert for hyphenation.
 * @property normalizeQuotes        Replace curly/typographic quotes with straight
 *                                  ASCII `'` and `"`.
 * @property normalizeEllipsis      Replace the single ellipsis glyph `…` with `...`.
 * @property dashesToPauses         Turn a spaced em/en dash into a comma so the
 *                                  voice pauses naturally instead of stumbling.
 * @property expandAbbreviations    Expand a small, curated set of common English
 *                                  abbreviations (see [TextNormalizer.ABBREVIATION_RULES]).
 * @property stripBrackets          Drop bracketed numeric citations such as `[12]`.
 * @property stripSuperscriptDigits Drop Unicode superscript digits used as
 *                                  footnote markers (e.g. `word²`).
 */
data class NormalizerOptions(
    val collapseWhitespace: Boolean = true,
    val stripInvisibleChars: Boolean = true,
    val normalizeQuotes: Boolean = true,
    val normalizeEllipsis: Boolean = true,
    val dashesToPauses: Boolean = true,
    val expandAbbreviations: Boolean = true,
    val stripBrackets: Boolean = true,
    val stripSuperscriptDigits: Boolean = true,
)

/**
 * Normalizes raw EPUB prose into a form that neural text-to-speech engines
 * (Piper / sherpa-onnx) pronounce naturally.
 *
 * This is intentionally pure Kotlin/JVM with **no Android dependencies** so it
 * can be exercised by fast JVM unit tests. The public entry point is
 * [normalize]; each transformation is a small private helper guarded by a flag
 * in [NormalizerOptions].
 *
 * The rules are deliberately conservative: the goal is to smooth over the noise
 * that real e-readers pass through (soft hyphens, footnote markers, typographic
 * punctuation) without "creatively" rewriting the author's words.
 */
object TextNormalizer {

    /** Soft hyphen, the zero-width family, the word joiner and the BOM. */
    private val INVISIBLE_CHARS = Regex("[\\u00AD\\u200B\\u200C\\u200D\\u2060\\uFEFF]")

    /** Unicode superscript digits ⁰¹²³⁴⁵⁶⁷⁸⁹ frequently used as footnote markers. */
    private val SUPERSCRIPT_DIGITS = Regex("[\\u00B9\\u00B2\\u00B3\\u2070\\u2074-\\u2079]+")

    /**
     * Bracketed numeric citation such as `[12]`, `[1, 2]` or `[3-5]`.
     * Only all-numeric bracket contents are removed, so annotations like
     * `[sic]` or `[emphasis added]` are preserved.
     */
    private val BRACKET_CITATION = Regex("\\[\\s*\\d+(?:\\s*[,;\\u2013-]\\s*\\d+)*\\s*\\]")

    /** A spaced em/en dash (or a spaced ASCII double hyphen) acting as a pause. */
    private val SPACED_DASH = Regex("\\s+(?:\\u2014|\\u2013|\\u2015|--)\\s+")

    /** Any run of whitespace, including Unicode separators like the non-breaking space. */
    private val ANY_WHITESPACE = Regex("[\\p{Z}\\s]+")

    /** A space sitting in front of closing punctuation, left over after clean-up. */
    private val SPACE_BEFORE_PUNCT = Regex(" +([,.;:!?])")

    /**
     * Ordered abbreviation-expansion rules. Order matters only where one pattern
     * could shadow another; the entries here are mutually exclusive by design.
     *
     * Notes on the trickier, context-dependent cases:
     *  - `St.` is only expanded to "Saint" when it introduces a capitalized word
     *    (`St. Paul`), so a trailing street reference like `Main St.` is left alone.
     *  - `No.` is only expanded to "Number" when a digit follows (`No. 5`), so the
     *    word "No." meaning a refusal is left alone.
     */
    private val ABBREVIATION_RULES: List<Pair<Regex, String>> = listOf(
        Regex("\\bMrs\\.") to "Missus",
        Regex("\\bMr\\.") to "Mister",
        Regex("\\bMs\\.") to "Miss",
        Regex("\\bDr\\.") to "Doctor",
        Regex("\\bProf\\.") to "Professor",
        Regex("\\bSt\\.(?=\\s+[A-Z])") to "Saint",
        Regex("\\bNo\\.(?=\\s*\\d)") to "Number",
        Regex("(?i)\\bvs\\.") to "versus",
        Regex("(?i)\\betc\\.") to "et cetera",
        Regex("(?i)\\be\\.g\\.") to "for example",
        Regex("(?i)\\bi\\.e\\.") to "that is",
    )

    /**
     * Normalizes [text] for speech synthesis.
     *
     * The steps run in an order chosen so that later steps see already-cleaned
     * input (for example, invisible characters are stripped before abbreviation
     * matching, and whitespace is collapsed last so earlier edits cannot leave
     * behind double spaces).
     *
     * @return the normalized string; never `null`, and empty for blank input.
     */
    fun normalize(text: String, options: NormalizerOptions = NormalizerOptions()): String {
        var s = text

        if (options.stripInvisibleChars) s = INVISIBLE_CHARS.replace(s, "")
        if (options.normalizeQuotes) s = normalizeQuotes(s)
        if (options.normalizeEllipsis) s = s.replace("…", "...")
        if (options.stripSuperscriptDigits) s = SUPERSCRIPT_DIGITS.replace(s, "")
        if (options.stripBrackets) s = BRACKET_CITATION.replace(s, "")
        if (options.expandAbbreviations) s = expandAbbreviations(s)
        if (options.dashesToPauses) s = SPACED_DASH.replace(s, ", ")
        if (options.collapseWhitespace) s = collapseWhitespace(s)

        return s
    }

    /** Replaces curly single/double quotes (and their low-9 variants) with ASCII quotes. */
    private fun normalizeQuotes(text: String): String = text
        .replace('‘', '\'') // ‘ left single
        .replace('’', '\'') // ’ right single (also the apostrophe in contractions)
        .replace('‚', '\'') // ‚ single low-9
        .replace('‛', '\'') // ‛ single high-reversed-9
        .replace('“', '"')  // “ left double
        .replace('”', '"')  // ” right double
        .replace('„', '"')  // „ double low-9
        .replace('‟', '"')  // ‟ double high-reversed-9

    /** Applies every rule in [ABBREVIATION_RULES] in sequence. */
    private fun expandAbbreviations(text: String): String {
        var s = text
        for ((pattern, replacement) in ABBREVIATION_RULES) {
            s = pattern.replace(s, replacement)
        }
        return s
    }

    /**
     * Collapses all whitespace to single ASCII spaces, removes any space that
     * ended up in front of closing punctuation (a common by-product of stripping
     * citations), and trims the ends.
     */
    private fun collapseWhitespace(text: String): String {
        val collapsed = ANY_WHITESPACE.replace(text, " ")
        val tidied = SPACE_BEFORE_PUNCT.replace(collapsed, "$1")
        return tidied.trim()
    }
}
