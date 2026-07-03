package com.k2fsa.sherpa.onnx.tts.engine.phonikud

import java.text.Normalizer

/**
 * Hebrew Phonemizer — faithful pure-Kotlin port of thewh1teagle/phonikud.
 *
 * Ported from:
 *  - phonikud/hebrew.py      (the rule-based FST: `_letter`, `_vav`, `_shin`, `_vav_vowel`, `phonemize_word`)
 *  - phonikud/phonemize.py   (the `phonemize` entry / heb_replace_callback flow)
 *  - phonikud/utils.py       (`normalize`, `get_letters`, `post_normalize`, `post_clean`,
 *                             `sort_stress`, `mark_vocal_shva`, `sort_hatama`, `add_milra_hatama`)
 *  - phonikud/variants.py    (`Letter`)
 *  - phonikud/syllables.py   (`get_syllables`, used by `add_milra_hatama`)
 *  - phonikud/__init__.py    (default arguments)
 *
 * Fixed defaults for this port (matching __init__.py + the requested TTS configuration):
 *   preserve_punctuation = true, preserve_stress = true, predict_stress = true,
 *   predict_vocal_shva = true, use_post_normalize = true, schema = "modern".
 *
 * NOT ported (and why):
 *   - use_expander: the Expander (number/date/dictionary expansion) is a large separate
 *     component. This core assumes already-diacritized Hebrew input, so the expander is a
 *     no-op for such words; it is intentionally skipped (equivalent to use_expander=false).
 *   - fallback (Latin g2p) and hyper-phoneme `[word](/ipa/)` injection are not needed for
 *     diacritized Hebrew; the hyper-phoneme regex is applied but is a no-op on normal input.
 *
 * PURE Kotlin/JVM: no Android, no ONNX.
 */
object HebrewPhonemizer {

    private val L = PhonikudLexicon

    // NIKUD constant Chars used by the FST (hebrew.py: _D,_SH,_HO,_HI,_KA,_PA,_TS,_SE,_KU,_VS,_SI,_HK)
    private const val D = 'ּ'  // Dagesh
    private const val SH = 'ְ' // Shva
    private const val HO = 'ֹ' // Holam
    private const val HI = 'ִ' // Hirik
    private const val KA = 'ָ' // Kamatz
    private const val PA = 'ַ' // Patah
    private const val TS = 'ֵ' // Tsere
    private const val SE = 'ֶ' // Segol
    private const val KU = 'ֻ' // Kubuts
    private const val VS = 'ֽ' // Vocal Shva (meteg)
    private const val SI = 'ׂ' // Sin dot
    private const val HK = 'ֳ' // Hataf Kamatz

    // hebrew.py: _PAT_RE = re.compile(NIKUD_PATAH_LIKE_PATTERN)  -> [ַ-ָ]
    private val PAT_RE = L.NIKUD_PATAH_LIKE_PATTERN

    // hebrew.py: _GNUVA = {"ח": "ax", "ה": "ah", "ע": "a"} (Patah gnuva on a final guttural)
    private val GNUVA = mapOf("ח" to "ax", "ה" to "ah", "ע" to "a")

    // ------------------------------------------------------------------
    // variants.py — Letter
    // ------------------------------------------------------------------
    /**
     * A Hebrew letter plus its diacritics.
     *  - [char]    : the base letter (NFD-normalized), a 1-char String.
     *  - [allDiac] : all following marks incl. stress (HATAMA) and prefix ("|"). Mutable.
     *  - [diac]    : allDiac WITHOUT HATAMA and PREFIX. Computed once at construction
     *                (as in variants.py), so later mutation of [allDiac] does not change it
     *                — safe because mutations only ever touch HATAMA / PREFIX.
     */
    class Letter(rawChar: String, rawDiac: String) {
        val char: String = normalize(rawChar)
        var allDiac: String = normalize(rawDiac)
        val diac: String = allDiac.filter { it != L.HATAMA_CHAR && it != L.PREFIX_CHAR }

        override fun toString(): String = char + allDiac
    }

    // ------------------------------------------------------------------
    // utils.py — normalize / get_letters / post_normalize / post_clean / sort_stress
    // ------------------------------------------------------------------

    private val SORT_DIAC_RE = Regex("(\\p{L})(\\p{M}+)")
    private val LETTERS_RE = Regex("(\\p{L})([\\p{M}'|]*)")

    /**
     * utils.normalize: NFD-decompose, sort each letter's diacritics by code point, then
     * apply the punctuation-dedup replacements.
     */
    fun normalize(text: String): String {
        var t = Normalizer.normalize(text, Normalizer.Form.NFD)
        // NORMALIZE_PATTERNS: sort diacritics (sorted() sorts characters by code point)
        t = SORT_DIAC_RE.replace(t) { m ->
            m.groupValues[1] + m.groupValues[2].toCharArray().sorted().joinToString("")
        }
        // NORMALIZE_PATTERNS: gershayim U+05F4 -> "  ; geresh U+05F3 -> '
        t = t.replace("״", "\"").replace("׳", "'")
        // DEDUPLICATE: geresh U+05F3 -> ' ; makaf U+05BE -> -
        for ((k, v) in L.DEDUPLICATE) t = t.replace(k, v)
        return t
    }

    /** utils.get_letters: split a word into Letter(base, marks) via `(\p{L})([\p{M}'|]*)`. */
    fun getLetters(word: String): MutableList<Letter> =
        LETTERS_RE.findAll(word).map { Letter(it.groupValues[1], it.groupValues[2]) }.toMutableList()

    /** utils.post_normalize: per-word cleanup applied to the joined phoneme string. */
    fun postNormalize(phonemes: String): String =
        phonemes.split(" ").joinToString(" ") { word ->
            var w = word
            w = w.replace(Regex("ʔ$"), "")   // remove glottal stop ʔ from end
            w = w.replace(Regex("h$"), "")         // remove h from end
            w = w.replace(Regex("ˈh$"), "")   // remove ˈh from end
            w = w.replace(Regex("ij$"), "i")       // j after i -> i
            w
        }

    /** utils.post_clean: keep only valid phoneme chars / spaces / punctuation; "-" -> " ". */
    fun postClean(phonemes: String): String {
        val sb = StringBuilder()
        for (c in phonemes) {
            when {
                c == '-' -> sb.append(' ')
                c in L.SET_PHONEMES || c == ' ' || c in L.PUNCTUATION -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** utils.sort_stress: move the stress mark to just before the first vowel. */
    fun sortStress(syllable: List<String>): List<String> {
        val vowels = "aeiou"
        val text = syllable.joinToString("")
        if (L.STRESS_PHONEME !in text || !text.any { it in vowels }) return syllable
        val out = syllable.map { it.replace(L.STRESS_PHONEME, "") }.toMutableList()
        for (i in out.indices) {
            val s = out[i]
            val idx = s.indexOfFirst { it in vowels }
            if (idx >= 0) {
                out[i] = s.substring(0, idx) + L.STRESS_PHONEME + s[idx] + s.substring(idx + 1)
                break
            }
        }
        return out
    }

    /**
     * utils.mark_vocal_shva: context-independent prediction of a leading Vocal Shva; adds a
     * meteg (U+05BD) to the first letter. Returns the (possibly) modified word.
     *
     * NOTE: In phonemize.py this is invoked as `mark_vocal_shva(word)` with the RETURN VALUE
     * DISCARDED, so in the actual phonemize flow it is a no-op on the word. It is provided
     * here for completeness/testing and is intentionally NOT applied in [phonemize] (see below).
     */
    fun markVocalShva(word: String): String {
        val letters = getLetters(word)
        if (letters.isEmpty()) return word
        when {
            letters[0].char.isNotEmpty() && letters[0].char[0] in "למנרי" -> // למנרי
                letters[0].allDiac += L.VOCAL_SHVA_DIACRITIC
            letters.size > 1 && letters[1].char.isNotEmpty() && letters[1].char[0] in "אעה" -> // אעה
                letters[0].allDiac += L.VOCAL_SHVA_DIACRITIC
            letters[0].char.isNotEmpty() && letters[0].char[0] in "וכלב" && // וכלב
                L.PREFIX_CHAR in letters[0].allDiac ->
                letters[0].allDiac += L.VOCAL_SHVA_DIACRITIC
        }
        // Ensure the prefix character stays last
        for (letter in letters) {
            if (L.PREFIX_CHAR in letter.allDiac) {
                letter.allDiac = letter.allDiac.replace("|", "") + "|"
            }
        }
        return letters.joinToString("") { it.toString() }
    }

    /** utils.sort_hatama: move HATAMA off a nikud-haser letter onto the next letter. */
    fun sortHatama(letters: MutableList<Letter>): MutableList<Letter> {
        for (i in 0 until letters.size - 1) {
            val ad = letters[i].allDiac
            if (L.HATAMA_CHAR in ad && L.NIKUD_HASER_CHAR in ad) {
                letters[i].allDiac = ad.filter { it != L.HATAMA_CHAR }
                letters[i + 1].allDiac += L.HATAMA_DIACRITIC
            }
        }
        return letters
    }

    /** utils.add_milra_hatama: mark stress on the first letter of the last syllable. */
    fun addMilraHatama(word: String): String {
        val syllables = getSyllables(word)
        if (syllables.isEmpty()) return word
        val stressIndex = if (syllables.size == 1) 0 else syllables.size - 1
        val milra = syllables[stressIndex]
        val letters = getLetters(milra)
        if (letters.isEmpty()) return word
        letters[0].allDiac += L.HATAMA_DIACRITIC
        syllables[stressIndex] = letters.joinToString("") { it.toString() }
        return syllables.joinToString("")
    }

    // ------------------------------------------------------------------
    // syllables.py — get_syllables (used by add_milra_hatama)
    // ------------------------------------------------------------------

    // VOWEL_DIACS = U+05B1..U+05BB + U+05C7 + U+05BD  (note: Shva U+05B0 handled separately)
    private val VOWEL_DIACS: Set<Char> =
        (0x05B1..0x05BB).map { it.toChar() }.toSet() + 'ׇ' + 'ֽ'

    private fun hasVowelDiacs(s: String): Boolean {
        if (s == "וּ") return true // "וּ" (vav + dagesh = shuruk)
        return VOWEL_DIACS.any { it in s }
    }

    fun getSyllables(word: String): MutableList<String> {
        val letters = getLetters(word)
        val syllables = mutableListOf<String>()
        var cur = ""
        var vowelState = false
        var i = 0
        while (i < letters.size) {
            val letter = letters[i]
            val hasVowel = hasVowelDiacs(letter.toString()) || (i == 0 && SH in letter.allDiac)
            // Look ahead for one/two upcoming vav (U+05D5)
            val vav1 = i + 2 < letters.size && letters[i + 2].char == "ו"
            val vav2 = i + 3 < letters.size && letters[i + 3].char == "ו"

            if (hasVowel) {
                if (vowelState) {
                    syllables.add(cur); cur = letter.toString()
                } else {
                    cur += letter.toString()
                }
                vowelState = true
            } else {
                cur += letter.toString()
            }

            i += 1

            if (vav1 && vav2) {
                if (cur.isNotEmpty()) {
                    syllables.add(cur + letters[i].toString()); cur = ""
                }
                cur = letters[i + 1].toString() + letters[i + 2].toString()
                i += 3
                vowelState = true
            } else if (vav1 && letters[i + 1].diac.isNotEmpty()) {
                if (cur.isNotEmpty()) {
                    syllables.add(cur); cur = ""
                }
                vowelState = false
            }
        }
        if (cur.isNotEmpty()) syllables.add(cur)
        return syllables
    }

    // ------------------------------------------------------------------
    // hebrew.py — the FST
    // ------------------------------------------------------------------

    private fun vowelsOf(cur: Letter): List<String> =
        cur.allDiac.map { L.NIKUD_PHONEMES[it] ?: "" }

    private fun stressOf(cur: Letter): List<String> =
        if (L.HATAMA_CHAR in cur.allDiac) listOf(L.STRESS_PHONEME) else emptyList()

    /** hebrew.py `_clean`: keep phoneme strings whose every char is a valid phoneme char. */
    private fun clean(out: List<String>): List<String> =
        out.filter { it.isNotEmpty() && it.all { c -> c in L.SET_PHONEMES } }

    /** hebrew.py `_out`. */
    private fun out(cur: Letter, con: String, vow: List<String>? = null, skip: Int = 0): Pair<List<String>, Int> {
        val base = if (con.isNotEmpty()) listOf(con) else emptyList()
        val combined = base + (vow ?: vowelsOf(cur))
        return clean(sortStress(combined)) to skip
    }

    /** hebrew.py `_vav_vowel`. */
    private fun vavVowel(d: String): String? {
        if (PAT_RE.containsMatchIn(d)) return "va"
        if (TS in d || SE in d || VS in d) return "ve"
        if (HO in d) return "o"
        if (KU in d || D in d) return "u"
        if (HI in d) return "vi"
        return null
    }

    /** hebrew.py `phonemize_word`. */
    fun phonemizeWord(letters: List<Letter>): List<String> {
        val phonemes = mutableListOf<String>()
        var i = 0
        while (i < letters.size) {
            val prev = if (i > 0) letters[i - 1] else null
            val nxt = if (i + 1 < letters.size) letters[i + 1] else null
            val (p, skip) = letterRule(letters[i], prev, nxt)
            phonemes.addAll(p)
            i += skip + 1
        }
        return phonemes
    }

    /** hebrew.py `_letter`. */
    private fun letterRule(cur: Letter, prev: Letter?, nxt: Letter?): Pair<List<String>, Int> {
        val d = cur.diac
        val ch = cur.char
        val s = stressOf(cur)

        // Skip letter marked as nikud haser
        if (L.NIKUD_HASER_CHAR in cur.allDiac) return emptyList<String>() to 0

        // Geresh overrides consonant (tav-geresh also skips vowels)
        if ('\'' in d && ch in L.GERESH_PHONEMES) {
            return out(cur, L.GERESH_PHONEMES.getValue(ch), vow = if (ch == "ת") emptyList() else null)
        }

        // Dagesh beged-kefet overrides consonant
        if (D in d && (ch + D) in L.LETTERS_PHONEMES) {
            return out(cur, L.LETTERS_PHONEMES.getValue(ch + D))
        }

        // Vav — complex vowel/consonant
        if (ch == "ו" && L.NIKUD_HASER_CHAR !in cur.allDiac) {
            return vav(cur, prev, nxt)
        }

        // Shin / Sin
        if (ch == "ש") {
            return shin(cur, prev, nxt)
        }

        // Patah gnuva — final patah on a guttural (ח/ה/ע -> ax/ah/a)
        if (nxt == null && PA in d && ch in GNUVA) {
            return out(cur, GNUVA.getValue(ch), vow = s)
        }

        // Kamatz before hataf-kamatz -> 'o' (Kamatz katan)
        if (KA in d && nxt != null && HK in nxt.diac) {
            return out(cur, L.LETTERS_PHONEMES[ch] ?: "", vow = listOf("o") + s)
        }

        // Em kriah — silent alef mid-word (not before vav)
        if (ch == "א" && d.isEmpty() && prev != null && nxt != null && nxt.char != "ו") {
            return out(cur, "")
        }

        // Yud kriah — silent yud mid-word
        if (ch == "י" && d.isEmpty() && prev != null && nxt != null &&
            (prev.char + prev.diac) != "אֵ" && // not after "אֵ"
            !(nxt.char == "ו" && nxt.diac.isNotEmpty() && SH !in nxt.diac)
        ) {
            return out(cur, "")
        }

        // Default: consonant + vowels
        return out(cur, L.LETTERS_PHONEMES[ch] ?: "")
    }

    /** hebrew.py `_shin`. */
    private fun shin(cur: Letter, prev: Letter?, nxt: Letter?): Pair<List<String>, Int> {
        if (SI in cur.diac) {
            if (nxt != null && nxt.char == "ש" && nxt.diac.isEmpty() && PAT_RE.containsMatchIn(cur.diac)) {
                return out(cur, "sa", vow = stressOf(cur), skip = 1)
            }
            return out(cur, "s")
        }
        if (cur.diac.isEmpty() && prev != null && SI in prev.diac) {
            return out(cur, "s")
        }
        return out(cur, L.LETTERS_PHONEMES["ש"] ?: "")
    }

    /** hebrew.py `_vav` (complex vav: o/u/ve/va/vi, double-vav, shuruk, etc.). */
    private fun vav(cur: Letter, prev: Letter?, nxt: Letter?): Pair<List<String>, Int> {
        val d = cur.diac
        val s = stressOf(cur)
        if (prev != null && SH in prev.diac && HO in d) {
            return out(cur, "vo", vow = s)
        }
        if (nxt != null && nxt.char == "ו") {
            val dd = d + nxt.diac
            if (HO in dd) return out(cur, "vo", vow = s, skip = 1)
            if (d == nxt.diac) return out(cur, "vu", vow = s, skip = 1)
            val v = vavVowel(d)
            if (v != null) return out(cur, v, vow = s)
            if (SH in d && nxt.diac.isEmpty()) return out(cur, "v", vow = s)
            return out(cur, "", vow = s)
        }
        val v = vavVowel(d)
        if (v != null) return out(cur, v, vow = s)
        if (SH in d && prev == null) return out(cur, "ve", vow = s)
        if (nxt != null && d.isEmpty()) return out(cur, "", vow = s)
        return out(cur, "v", vow = s)
    }

    // ------------------------------------------------------------------
    // phonemize.py — the entry point
    // ------------------------------------------------------------------

    // HE_PATTERN = [ְ-ת + non-standard diacritics + "]+
    // non-standard = meteg U+05BD, ole U+05AB, vertical bar |, masora U+05AF, en-geresh '
    private val HE_PATTERN = Regex("[ְ-תֽ֫|֯'\"]+")
    private val HYPER_PATTERN = Regex("\\[(.+?)\\]\\(/(.+?)/\\)")

    /**
     * Public entry. Given diacritized Hebrew, returns the phoneme string.
     * Matches phonikud.phonemize(text) with the fixed defaults documented above.
     */
    fun phonemize(diacritizedHebrew: String): String {
        var text = normalize(diacritizedHebrew)

        // heb_replace_callback for each Hebrew run
        text = HE_PATTERN.replace(text) { m -> hebReplace(m.value, m.range.first, text) }

        // hyper-phonemes `[word](/ipa/)` -> ipa. No-op on normal diacritized input.
        text = HYPER_PATTERN.replace(text) { m -> m.groupValues[2] }

        // preserve_punctuation = true, preserve_stress = true -> no stripping.
        // use_post_normalize = true -> post_clean.
        return postClean(text)
    }

    private fun hebReplace(word: String, startOffset: Int, original: String): String {
        // Skip if it starts with '[' (hyper-phoneme marker)
        if (startOffset > 0 && original[startOffset - 1] == '[') return word

        var w = word
        // predict_vocal_shva=true: phonemize.py calls `mark_vocal_shva(word)` but DISCARDS the
        // return value, so it is a no-op here. Faithfully replicated (see markVocalShva()).

        // predict_stress=true: add milra hatama unless the word already carries stress.
        if (L.HATAMA_CHAR !in w) {
            w = addMilraHatama(w)
        }

        val letters = getLetters(w)
        sortHatama(letters)

        var phonemes = phonemizeWord(letters).joinToString("")
        // use_post_normalize
        phonemes = postNormalize(phonemes)
        // schema = "modern"
        for ((k, v) in L.MODERN_SCHEMA) phonemes = phonemes.replace(k, v)
        return phonemes
    }
}
