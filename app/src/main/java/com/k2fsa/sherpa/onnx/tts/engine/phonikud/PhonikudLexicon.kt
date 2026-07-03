package com.k2fsa.sherpa.onnx.tts.engine.phonikud

/**
 * Constant tables for the Hebrew phonemizer.
 *
 * Faithful port of thewh1teagle/phonikud `phonikud/lexicon.py`
 * (https://github.com/thewh1teagle/phonikud/blob/main/phonikud/lexicon.py).
 *
 * PURE Kotlin/JVM: no Android, no ONNX. Every non-ASCII character is written with an
 * explicit `\uXXXX` escape so the exact code point is verifiable against the source.
 */
object PhonikudLexicon {

    // --- Non standard diacritics (lexicon.py) ---
    const val VOCAL_SHVA_DIACRITIC = "ֽ"    // Meteg  (marks Vocal Shva -> "e")
    const val HATAMA_DIACRITIC = "֫"        // Ole    (marks stress)
    const val PREFIX_DIACRITIC = "|"             // Vertical bar
    const val NIKUD_HASER_DIACRITIC = "֯"   // Masora circle, not in use (skips the letter)
    const val EN_GERESH = "'"

    // Char forms for cheap membership checks
    const val VOCAL_SHVA_CHAR = 'ֽ'
    const val HATAMA_CHAR = '֫'
    const val PREFIX_CHAR = '|'
    const val NIKUD_HASER_CHAR = '֯'

    /** STRESS_PHONEME = "ˈ" (U+02C8, visually looks like a single quote). */
    const val STRESS_PHONEME = "ˈ"
    const val STRESS_PHONEME_CHAR = 'ˈ'

    val SPECIAL_PHONEMES = listOf("w")

    /**
     * MODERN_SCHEMA final replacements (lexicon.py). Applied per-word at the very end.
     *  x -> χ (Het, U+03C7), r -> ʁ (Resh, U+0281), g -> ɡ (Gimel, U+0261)
     */
    val MODERN_SCHEMA: Map<String, String> = linkedMapOf(
        "x" to "χ", // Het
        "r" to "ʁ", // Resh
        "g" to "ɡ", // Gimel
    )

    // --- Geresh (lexicon.py GERESH_PHONEMES) ---
    val GERESH_PHONEMES: Map<String, String> = mapOf(
        "ג" to "dʒ", // ג -> dʒ
        "ז" to "ʒ",  // ז -> ʒ
        "ת" to "ta",      // ת -> ta
        "צ" to "tʃ", // צ -> tʃ
        "ץ" to "tʃ", // ץ -> tʃ
    )

    // --- Consonants (lexicon.py LETTERS_PHONEMES) ---
    val LETTERS_PHONEMES: Map<String, String> = mapOf(
        "א" to "ʔ", // א Alef      -> ʔ
        "ב" to "v",      // ב Bet       -> v
        "ג" to "g",      // ג Gimel     -> g
        "ד" to "d",      // ד Dalet     -> d
        "ה" to "h",      // ה He        -> h
        "ו" to "v",      // ו Vav       -> v
        "ז" to "z",      // ז Zayin     -> z
        "ח" to "x",      // ח Het       -> x
        "ט" to "t",      // ט Tet       -> t
        "י" to "j",      // י Yod       -> j
        "ך" to "x",      // ך Haf sofit -> x
        "כ" to "x",      // כ Haf       -> x
        "ל" to "l",      // ל Lamed     -> l
        "ם" to "m",      // ם Mem sofit -> m
        "מ" to "m",      // מ Mem       -> m
        "ן" to "n",      // ן Nun sofit -> n
        "נ" to "n",      // נ Nun       -> n
        "ס" to "s",      // ס Samekh    -> s
        "ע" to "ʔ", // ע Ayin      -> ʔ (only voweled)
        "פ" to "f",      // פ Fey       -> f
        "ף" to "f",      // ף Fey sofit -> f
        "ץ" to "ts",     // ץ Tsadik sofit -> ts
        "צ" to "ts",     // צ Tsadik    -> ts
        "ק" to "k",      // ק Kuf       -> k
        "ר" to "r",      // ר Resh      -> r
        "ש" to "ʃ", // ש Shin      -> ʃ
        "ת" to "t",      // ת Taf       -> t
        // Beged Kefet (base letter + Dagesh U+05BC)
        "בּ" to "b", // בּ -> b
        "כּ" to "k", // כּ -> k
        "פּ" to "p", // פּ -> p
        // Shin / Sin (base ש U+05E9 + shin dot U+05C1 / sin dot U+05C2)
        "שׁ" to "ʃ", // שׁ -> ʃ
        "שׂ" to "s",      // שׂ -> s
        "'" to "",
    )

    // --- Vowel diacritics (lexicon.py NIKUD_PHONEMES). Keyed by single Char. ---
    val NIKUD_PHONEMES: Map<Char, String> = mapOf(
        'ִ' to "i", // Hiriq
        'ֱ' to "e", // Hataf segol
        'ֵ' to "e", // Tsere
        'ֶ' to "e", // Segol
        'ֲ' to "a", // Hataf Patah
        'ַ' to "a", // Patah
        'ׇ' to "o", // Kamatz katan
        'ֹ' to "o", // Holam
        'ֺ' to "o", // Holam haser for vav
        'ֻ' to "u", // Qubuts
        'ֳ' to "o", // Hataf qamats
        'ָ' to "a", // Kamatz
        HATAMA_CHAR to STRESS_PHONEME,      // Stress (Hat'ama) U+05AB -> ˈ
        VOCAL_SHVA_CHAR to "e",             // Vocal Shva U+05BD -> e
    )

    // --- DEDUPLICATE (lexicon.py) ---
    val DEDUPLICATE: Map<String, String> = linkedMapOf(
        "׳" to "'", // Hebrew geresh -> regular geresh
        "־" to "-", // Hebrew Makaf -> hyphen
    )

    /** PUNCTUATION = set(".,!? ") */
    val PUNCTUATION: Set<Char> = setOf('.', ',', '!', '?', ' ')

    // --- NIKUD name table (lexicon.py NIKUD) ---
    const val NIKUD_PATAH = 'ַ'
    const val NIKUD_KAMATZ = 'ָ'
    const val NIKUD_HIRIK = 'ִ'
    const val NIKUD_SEGOL = 'ֶ'
    const val NIKUD_TSERE = 'ֵ'
    const val NIKUD_HOLAM = 'ֹ'
    const val NIKUD_KUBUTS = 'ֻ'
    const val NIKUD_SHVA = 'ְ'
    const val NIKUD_HATAF_KAMATZ = 'ֳ'
    const val NIKUD_DAGESH = 'ּ'
    const val NIKUD_SIN = 'ׂ'
    const val NIKUD_VAV_HOLAM = 'ֺ'

    /** NIKUD_PATAH_LIKE_PATTERN = "[ַ-ָ]" (patah, kamatz) */
    val NIKUD_PATAH_LIKE_PATTERN: Regex = Regex("[ַ-ָ]")

    /**
     * SET_PHONEMES: the set of every single character that may legitimately appear in a
     * phoneme string. Built (as in lexicon.py) from the union of all values of
     * NIKUD_PHONEMES, LETTERS_PHONEMES, GERESH_PHONEMES, MODERN_SCHEMA and SPECIAL_PHONEMES.
     *
     * lexicon.py stores whole phoneme strings in a Python set and `_clean`/`post_clean`
     * test single-character membership (`c in SET_PHONEMES`). A `Set<Char>` of every
     * component character reproduces that behaviour exactly.
     */
    val SET_PHONEMES: Set<Char> = buildSet<Char> {
        NIKUD_PHONEMES.values.forEach { addAll(it.toList()) }
        LETTERS_PHONEMES.values.forEach { addAll(it.toList()) }
        GERESH_PHONEMES.values.forEach { addAll(it.toList()) }
        MODERN_SCHEMA.values.forEach { addAll(it.toList()) }
        SPECIAL_PHONEMES.forEach { addAll(it.toList()) }
    }
}
