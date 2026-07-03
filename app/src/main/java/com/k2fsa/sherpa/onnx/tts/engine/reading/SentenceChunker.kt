package com.k2fsa.sherpa.onnx.tts.engine.reading

/**
 * Splits a (normally already [TextNormalizer]-cleaned) utterance into
 * sentence-sized chunks.
 *
 * Why chunk at all? On the weak CPUs of e-ink readers a single long paragraph
 * takes a noticeable time to synthesize before the *first* sample is produced.
 * By handing the TTS engine one sentence (or clause) at a time, `TtsService`
 * can start playing audio for chunk *n* while conceptually only ever holding one
 * chunk's worth of latency, and a Stop request can interrupt cleanly between
 * chunks instead of waiting for a whole paragraph.
 *
 * This is intentionally pure Kotlin/JVM with **no Android dependencies** so the
 * splitting rules can be unit-tested quickly on the JVM.
 *
 * The splitter is deliberately abbreviation-, decimal- and initial-aware so it
 * behaves sensibly even if it is fed raw text that never passed through
 * [TextNormalizer].
 */
object SentenceChunker {

    /** Chunks shorter than this are treated as fragments and merged forward. */
    private const val MIN_CHUNK_CHARS = 30

    /** Sentence-terminating characters. `…` is handled too in case it survived normalization. */
    private const val TERMINATORS = ".!?…"

    /** Closing punctuation that may follow a terminator, e.g. the `"` in `said."`. */
    private const val CLOSERS = "\"')]"

    /** Clause-level punctuation used to break over-long sentences into pause-sized pieces. */
    private const val CLAUSE_PUNCT = ";,:"

    /**
     * Abbreviations whose trailing period must **not** be treated as a sentence
     * end. Compared case-insensitively against the word preceding the period.
     * (Single-letter "words" such as the initials in `J. R. R.` are handled
     * separately, so they are not listed here.)
     */
    private val ABBREVIATIONS = setOf(
        "mr", "mrs", "ms", "dr", "prof", "st", "sr", "jr", "gen", "gov", "sen",
        "rev", "hon", "capt", "sgt", "col", "no", "vol", "fig", "pp", "al",
        "inc", "ltd", "co", "dept", "ave", "rd", "mt", "vs", "etc", "cf", "viz",
    )

    /**
     * Splits [text] into chunks that are each at most [maxChars] characters long.
     *
     * Guarantees:
     *  - blank input yields an empty list;
     *  - the returned list never contains blank/empty strings;
     *  - abbreviations, decimals (`3.14`) and initials (`J. R. R.`) do not cause
     *    spurious splits;
     *  - sentences longer than [maxChars] are broken on clause punctuation
     *    (`; , :`) and, as a last resort, on word boundaries;
     *  - very short fragments are merged forward so playback is not choppy.
     */
    fun chunk(text: String, maxChars: Int = 200): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val cap = maxChars.coerceAtLeast(1)
        // Hebrew (and mixed Hebrew/Latin) text has no letter case, so a new
        // sentence starts with an ordinary letter, not a capital. Detect that up
        // front and relax the boundary rule accordingly; pure Latin keeps the
        // strict uppercase heuristic so English chunking is unchanged.
        val caseless = isCaselessScript(detectScript(trimmed))
        val sentences = splitIntoSentences(trimmed, caseless)
        val bounded = sentences.flatMap { splitLongSentence(it, cap) }
        return mergeShortFragments(bounded, cap)
    }

    /** Scripts whose sentences do not begin with an upper-case letter. */
    private fun isCaselessScript(script: TextScript): Boolean =
        script == TextScript.HEBREW || script == TextScript.MIXED

    /**
     * Scans [text] once and cuts it at genuine sentence boundaries: a run of
     * terminators, an optional closing quote/bracket, whitespace, and then a
     * capital letter, digit or opening quote. A lone `.` preceded by an
     * abbreviation or a single letter is skipped so it does not split.
     */
    private fun splitIntoSentences(text: String, caseless: Boolean): List<String> {
        val sentences = mutableListOf<String>()
        val n = text.length
        var start = 0
        var i = 0

        while (i < n) {
            if (text[i] !in TERMINATORS) {
                i++
                continue
            }

            // Extend across a run of terminators, e.g. "?!" or the "..." from an ellipsis.
            var runEnd = i
            while (runEnd + 1 < n && text[runEnd + 1] in TERMINATORS) runEnd++

            // Absorb a single closing quote/bracket that belongs to this sentence.
            var end = runEnd
            if (end + 1 < n && text[end + 1] in CLOSERS) end++

            // A boundary requires whitespace followed by the start of a new sentence.
            val after = end + 1
            val nextStart = if (after < n && text[after].isWhitespace())
                text.indexOfFirst(after) { !it.isWhitespace() } else -1
            val isRealBoundary = nextStart != -1 &&
                isSentenceStart(text[nextStart], caseless) &&
                // Only a single '.' can be a false positive (abbreviation / initial).
                !(i == runEnd && text[i] == '.' && isNonBreakingPeriod(text, i))

            if (isRealBoundary) {
                sentences.add(text.substring(start, end + 1).trim())
                start = nextStart
                i = nextStart
            } else {
                i = runEnd + 1
            }
        }

        if (start < n) {
            val tail = text.substring(start).trim()
            if (tail.isNotEmpty()) sentences.add(tail)
        }
        return sentences
    }

    /**
     * True for characters that can legitimately begin a new sentence. A leading
     * quote/paren or a digit qualifies in any script, as does an upper-case
     * letter. For [caseless] scripts (Hebrew) there is no case, so a Hebrew
     * letter after the terminator also opens a new sentence — this is the fix
     * that lets Hebrew split at `. ` where the old uppercase-only rule never did.
     */
    private fun isSentenceStart(c: Char, caseless: Boolean): Boolean =
        c in "\"'(«¿¡" || c.isDigit() || c.isUpperCase() || (caseless && isHebrew(c.code))

    /**
     * Decides whether the period at [dotIndex] belongs to an abbreviation or an
     * initial rather than ending a sentence, by inspecting the letters directly
     * in front of it. A single letter is treated as an initial (`J.`, and also
     * covers the `g` in `e.g.`); a known token is treated as an abbreviation.
     */
    private fun isNonBreakingPeriod(text: String, dotIndex: Int): Boolean {
        var s = dotIndex - 1
        while (s >= 0 && text[s].isLetter()) s--
        val word = text.substring(s + 1, dotIndex)
        if (word.isEmpty()) return false
        if (word.length == 1) return true
        return word.lowercase() in ABBREVIATIONS
    }

    /**
     * If [sentence] exceeds [maxChars], breaks it into clause-sized pieces on
     * `; , :` boundaries, greedily packing them back up to [maxChars]; any clause
     * still too long is wrapped on word boundaries. Short sentences pass through
     * unchanged.
     */
    private fun splitLongSentence(sentence: String, maxChars: Int): List<String> {
        if (sentence.length <= maxChars) return listOf(sentence)

        val out = mutableListOf<String>()
        val buffer = StringBuilder()
        for (clause in splitOnClausePunctuation(sentence)) {
            val units = if (clause.length > maxChars) hardWrapOnSpaces(clause, maxChars) else listOf(clause)
            for (unit in units) {
                appendPacked(out, buffer, unit, maxChars)
            }
        }
        if (buffer.isNotEmpty()) out.add(buffer.toString())
        return out
    }

    /** Breaks a sentence after any `; , :` that is followed by whitespace, keeping the punctuation. */
    private fun splitOnClausePunctuation(sentence: String): List<String> {
        val pieces = mutableListOf<String>()
        var start = 0
        for (i in sentence.indices) {
            if (sentence[i] in CLAUSE_PUNCT && i + 1 < sentence.length && sentence[i + 1].isWhitespace()) {
                val piece = sentence.substring(start, i + 1).trim()
                if (piece.isNotEmpty()) pieces.add(piece)
                start = i + 1
            }
        }
        val tail = sentence.substring(start).trim()
        if (tail.isNotEmpty()) pieces.add(tail)
        return pieces
    }

    /** Splits an over-long, punctuation-free clause on spaces, hard-cutting any single giant word. */
    private fun hardWrapOnSpaces(clause: String, maxChars: Int): List<String> {
        val out = mutableListOf<String>()
        val buffer = StringBuilder()
        for (rawWord in clause.split(' ')) {
            if (rawWord.isEmpty()) continue
            var word = rawWord
            while (word.length > maxChars) {
                if (buffer.isNotEmpty()) {
                    out.add(buffer.toString())
                    buffer.setLength(0)
                }
                out.add(word.substring(0, maxChars))
                word = word.substring(maxChars)
            }
            appendPacked(out, buffer, word, maxChars)
        }
        if (buffer.isNotEmpty()) out.add(buffer.toString())
        return out
    }

    /**
     * Greedily merges fragments shorter than [MIN_CHUNK_CHARS] into the current
     * chunk so single-word sentences ("Yes." "No.") do not each become their own
     * choppy utterance, while never exceeding [maxChars]. Normal-length sentences
     * remain one chunk each.
     */
    private fun mergeShortFragments(pieces: List<String>, maxChars: Int): List<String> {
        val minChars = MIN_CHUNK_CHARS.coerceAtMost(maxChars)
        val out = mutableListOf<String>()
        val buffer = StringBuilder()
        for (raw in pieces) {
            val piece = raw.trim()
            if (piece.isEmpty()) continue
            when {
                buffer.isEmpty() -> buffer.append(piece)
                buffer.length < minChars && buffer.length + 1 + piece.length <= maxChars ->
                    buffer.append(' ').append(piece)
                else -> {
                    out.add(buffer.toString())
                    buffer.setLength(0)
                    buffer.append(piece)
                }
            }
        }
        if (buffer.isNotEmpty()) out.add(buffer.toString())
        return out
    }

    /**
     * Appends [unit] to [buffer], flushing [buffer] into [out] first if adding
     * the unit (plus a joining space) would exceed [maxChars].
     */
    private fun appendPacked(out: MutableList<String>, buffer: StringBuilder, unit: String, maxChars: Int) {
        when {
            buffer.isEmpty() -> buffer.append(unit)
            buffer.length + 1 + unit.length <= maxChars -> buffer.append(' ').append(unit)
            else -> {
                out.add(buffer.toString())
                buffer.setLength(0)
                buffer.append(unit)
            }
        }
    }

    /** Index of the first character at or after [from] matching [predicate], or -1. */
    private inline fun String.indexOfFirst(from: Int, predicate: (Char) -> Boolean): Int {
        for (idx in from until length) {
            if (predicate(this[idx])) return idx
        }
        return -1
    }
}
