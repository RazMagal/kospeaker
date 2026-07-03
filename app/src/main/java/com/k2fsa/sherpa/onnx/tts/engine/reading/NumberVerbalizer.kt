package com.k2fsa.sherpa.onnx.tts.engine.reading

/**
 * Configuration for [NumberVerbalizer]. Each family of numeric constructs can be
 * toggled independently. All flags default to `true`, which is the recommended
 * setting for reading long-form prose aloud.
 *
 * @property verbalizeYears    Read 4-digit numbers in the year range as spoken
 *                             years (English pairing only; Hebrew always reads a
 *                             plain cardinal).
 * @property verbalizeOrdinals Read `1st`, `21st`, … as `first`, `twenty-first`
 *                             (English only).
 * @property verbalizeCurrency Read `$5`, `£1.50`, `₪7`, … with the spelled-out
 *                             currency word.
 * @property verbalizePercent  Read `50%` as `fifty percent` / `חמישים אחוז`.
 */
data class NumberOptions(
    val verbalizeYears: Boolean = true,
    val verbalizeOrdinals: Boolean = true,
    val verbalizeCurrency: Boolean = true,
    val verbalizePercent: Boolean = true,
)

/**
 * Converts digit sequences in a string into spoken words so that neural TTS
 * voices read numbers naturally.
 *
 * This is intentionally pure Kotlin/JVM with **no Android dependencies** so it
 * can be exercised by fast JVM unit tests. The public entry point is
 * [verbalize]; the behaviour is script-aware:
 *
 *  - **English** ([TextScript.LATIN], and also [TextScript.MIXED]/[TextScript.OTHER],
 *    which are treated as English for robustness): cardinals up to the billions,
 *    comma-grouped integers, decimals, spoken years, ordinals, percentages and
 *    `$`/`£`/`€` currency.
 *  - **Hebrew** ([TextScript.HEBREW]): integers `0`‥`999,999,999` rendered in the
 *    **masculine absolute (citation) form**. This is a deliberate simplification —
 *    Hebrew numbers normally agree in gender with the counted noun, but the reading
 *    pipeline has no reliable way to know that noun, so one consistent, widely
 *    understood form is used. This matters because `facebook/mms-tts-heb` has no
 *    digits in its ~32-symbol vocabulary, so a raw digit is silently dropped;
 *    spelling numbers out is the only way they survive.
 *
 * The rules are conservative and never throw: anything that does not look like a
 * recognised numeric token is left exactly as-is.
 */
object NumberVerbalizer {

    // ---------------------------------------------------------------------------
    // English vocabulary
    // ---------------------------------------------------------------------------

    private val EN_ONES = arrayOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen",
    )
    private val EN_TENS = arrayOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
    )
    private val EN_SCALES = listOf(
        1_000_000_000L to "billion",
        1_000_000L to "million",
        1_000L to "thousand",
    )

    // ---------------------------------------------------------------------------
    // Hebrew vocabulary — masculine absolute (citation) form.
    // Every literal below was generated from its Unicode code points and verified
    // character-by-character; do not "fix" the spelling by eye.
    // ---------------------------------------------------------------------------

    /** The connector ו (U+05D5, "and"), prefixed to the final component of a number. */
    private const val HE_VAV = "ו"

    private val HE_UNITS = arrayOf(
        "אפס", "אחד", "שניים", "שלושה", "ארבעה", "חמישה", "שישה", "שבעה", "שמונה", "תשעה",
    )
    private const val HE_TEN = "עשרה"
    private val HE_TEENS = arrayOf(
        "אחד עשר", "שנים עשר", "שלושה עשר", "ארבעה עשר", "חמישה עשר",
        "שישה עשר", "שבעה עשר", "שמונה עשר", "תשעה עשר",
    )
    private val HE_TENS = arrayOf(
        "", "", "עשרים", "שלושים", "ארבעים", "חמישים", "שישים", "שבעים", "שמונים", "תשעים",
    )

    /** Feminine counting form used before "hundreds" (3‥9); 1 and 2 are special-cased. */
    private val HE_HUNDREDS_COUNT = arrayOf(
        "", "", "", "שלוש", "ארבע", "חמש", "שש", "שבע", "שמונה", "תשע",
    )
    private const val HE_MEA = "מאה"
    private const val HE_MATAYIM = "מאתיים"
    private const val HE_MEOT = "מאות"
    private const val HE_ELEF = "אלף"
    private const val HE_ALPAYIM = "אלפיים"
    private const val HE_ALAPIM = "אלפים"

    /** Construct counting form used before "thousands" (3‥9). */
    private val HE_THOUSANDS_CONSTRUCT = arrayOf(
        "", "", "", "שלושת", "ארבעת", "חמשת", "ששת", "שבעת", "שמונת", "תשעת",
    )
    private const val HE_MILLION = "מיליון"
    private const val HE_SHNEI = "שני"
    private const val HE_POINT = "נקודה"
    private const val HE_PERCENT = "אחוז"
    private const val HE_SHEKEL_S = "שקל"
    private const val HE_SHEKEL_P = "שקלים"
    private const val HE_DOLLAR_S = "דולר"
    private const val HE_DOLLAR_P = "דולרים"
    private const val HE_EURO = "יורו"

    // ---------------------------------------------------------------------------
    // Token patterns
    // ---------------------------------------------------------------------------

    // Currency symbols are written as escapes so the '$' never starts a Kotlin
    // string template: $ = $, £ = £, € = €, ₪ = ₪.

    /**
     * A number: an optionally comma-grouped integer part (only a `,ddd` group counts
     * as a separator, so a list comma like `5, 6` is never swallowed) plus an
     * optional decimal fraction.
     */
    private const val NUM = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?"

    private val EN_TOKEN = Regex(
        "([\$£€])($NUM)" + // 1 symbol, 2 amount  (currency)
            "|($NUM)%" + //                   3 amount            (percent)
            "|([0-9]+)(st|nd|rd|th)(?![A-Za-z])" + // 4 digits, 5 suffix (ordinal)
            "|($NUM)", //                     6 amount            (plain)
    )

    private val HE_TOKEN = Regex(
        "([\$£€₪])($NUM)" + // 1 symbol, 2 amount (leading currency)
            "|($NUM)\\s*₪" + //             3 amount           (trailing shekel)
            "|($NUM)%" + //                      4 amount           (percent)
            "|($NUM)", //                        5 amount           (plain)
    )

    /**
     * Replaces every recognised numeric token in [text] with its spoken form,
     * choosing the English or Hebrew renderer from [script].
     *
     * @return the rewritten string; unrecognised input is returned unchanged.
     */
    fun verbalize(text: String, script: TextScript, options: NumberOptions = NumberOptions()): String {
        if (text.isEmpty()) return text
        return if (script == TextScript.HEBREW) verbalizeHebrew(text, options)
        else verbalizeEnglish(text, options)
    }

    // ---------------------------------------------------------------------------
    // English
    // ---------------------------------------------------------------------------

    private fun verbalizeEnglish(text: String, o: NumberOptions): String =
        EN_TOKEN.replace(text) { m ->
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> if (o.verbalizeCurrency) englishCurrency(g[1], g[2]) else m.value
                g[3].isNotEmpty() -> if (o.verbalizePercent) englishNumber(g[3]) + " percent" else m.value
                g[4].isNotEmpty() -> if (o.verbalizeOrdinals) englishOrdinal(g[4].toLong()) else m.value
                g[6].isNotEmpty() -> englishNumberOrYear(g[6], o)
                else -> m.value
            }
        }

    private fun englishNumber(s: String): String {
        val c = stripCommas(s)
        return if (c.contains('.')) englishDecimal(c) else englishCardinal(c.toLong())
    }

    private fun englishNumberOrYear(s: String, o: NumberOptions): String {
        val c = stripCommas(s)
        if (c.contains('.')) return englishDecimal(c)
        val n = c.toLong()
        // Only a bare (un-grouped) 4-digit number is a plausible spoken year.
        if (o.verbalizeYears && !s.contains(',') && c.length == 4 && n in 1100..2099) return englishYear(n.toInt())
        return englishCardinal(n)
    }

    private fun englishDecimal(c: String): String {
        val dot = c.indexOf('.')
        val intWords = englishCardinal(c.substring(0, dot).toLong())
        val frac = c.substring(dot + 1).map { EN_ONES[it - '0'] }.joinToString(" ")
        return "$intWords point $frac"
    }

    /** Renders 0‥999,999,999,999 as cardinal words; larger values fall back to digit-by-digit. */
    private fun englishCardinal(n: Long): String {
        if (n == 0L) return "zero"
        if (n < 0L) return "minus " + englishCardinal(-n)
        if (n >= 1_000_000_000_000L) return n.toString().map { EN_ONES[it - '0'] }.joinToString(" ")
        val parts = mutableListOf<String>()
        var rem = n
        for ((value, name) in EN_SCALES) {
            if (rem >= value) {
                parts.add(englishBelowThousand((rem / value).toInt()) + " " + name)
                rem %= value
            }
        }
        if (rem > 0L) parts.add(englishBelowThousand(rem.toInt()))
        return parts.joinToString(" ")
    }

    /** Renders 1‥999 (American style, no "and"). */
    private fun englishBelowThousand(n: Int): String {
        val parts = mutableListOf<String>()
        var rem = n
        if (rem >= 100) {
            parts.add(EN_ONES[rem / 100] + " hundred")
            rem %= 100
        }
        if (rem > 0) parts.add(englishTwoDigits(rem))
        return parts.joinToString(" ")
    }

    /** Renders 1‥99, hyphenating compound tens (e.g. `twenty-one`). */
    private fun englishTwoDigits(n: Int): String =
        if (n < 20) {
            EN_ONES[n]
        } else {
            val tens = EN_TENS[n / 10]
            val ones = n % 10
            if (ones == 0) tens else "$tens-${EN_ONES[ones]}"
        }

    /** Renders a year in [1100, 2099] the way it is spoken. */
    private fun englishYear(year: Int): String {
        if (year in 2000..2009) return englishCardinal(year.toLong()) // "two thousand (x)"
        val high = englishTwoDigits(year / 100)
        val low = year % 100
        return when {
            low == 0 -> "$high hundred" // "nineteen hundred"
            low < 10 -> "$high oh ${EN_ONES[low]}" // "nineteen oh five"
            else -> "$high ${englishTwoDigits(low)}" // "nineteen ninety-six" / "twenty ten"
        }
    }

    private fun englishOrdinal(n: Long): String {
        val card = englishCardinal(n)
        val sp = card.lastIndexOf(' ')
        val head = if (sp == -1) "" else card.substring(0, sp + 1)
        val lastWord = if (sp == -1) card else card.substring(sp + 1)
        val hy = lastWord.lastIndexOf('-')
        val prefix = if (hy == -1) "" else lastWord.substring(0, hy + 1)
        val core = if (hy == -1) lastWord else lastWord.substring(hy + 1)
        return head + prefix + ordinalizeWord(core)
    }

    private fun ordinalizeWord(word: String): String = when (word) {
        "one" -> "first"
        "two" -> "second"
        "three" -> "third"
        "five" -> "fifth"
        "eight" -> "eighth"
        "nine" -> "ninth"
        "twelve" -> "twelfth"
        else -> if (word.endsWith("y")) word.dropLast(1) + "ieth" else word + "th"
    }

    private data class Money(val one: String, val many: String, val subOne: String, val subMany: String)

    private fun moneyWords(symbol: String): Money = when (symbol) {
        "£" -> Money("pound", "pounds", "penny", "pence")
        "€" -> Money("euro", "euros", "cent", "cents")
        else -> Money("dollar", "dollars", "cent", "cents") // $
    }

    private fun englishCurrency(symbol: String, amount: String): String {
        val m = moneyWords(symbol)
        val c = stripCommas(amount)
        val dot = c.indexOf('.')
        if (dot == -1) {
            val whole = c.toLong()
            return englishCardinal(whole) + " " + if (whole == 1L) m.one else m.many
        }
        val whole = c.substring(0, dot).toLong()
        var frac = c.substring(dot + 1)
        if (frac.length == 1) frac += "0"
        val cents = frac.substring(0, 2).toLong()
        val wholeWords = englishCardinal(whole) + " " + if (whole == 1L) m.one else m.many
        if (cents == 0L) return wholeWords
        val centWords = englishCardinal(cents) + " " + if (cents == 1L) m.subOne else m.subMany
        if (whole == 0L) return centWords
        return "$wholeWords and $centWords"
    }

    // ---------------------------------------------------------------------------
    // Hebrew
    // ---------------------------------------------------------------------------

    private fun verbalizeHebrew(text: String, o: NumberOptions): String =
        HE_TOKEN.replace(text) { m ->
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> if (o.verbalizeCurrency) hebrewCurrency(g[1], g[2]) else m.value
                g[3].isNotEmpty() -> if (o.verbalizeCurrency) hebrewCurrency("₪", g[3]) else m.value
                g[4].isNotEmpty() -> if (o.verbalizePercent) hebrewNumber(g[4]) + " " + HE_PERCENT else m.value
                g[5].isNotEmpty() -> hebrewNumber(g[5])
                else -> m.value
            }
        }

    private fun hebrewNumber(s: String): String {
        val c = stripCommas(s)
        val dot = c.indexOf('.')
        if (dot == -1) return hebrewInteger(c.toLong())
        val intWords = hebrewInteger(c.substring(0, dot).toLong())
        val frac = c.substring(dot + 1).map { HE_UNITS[it - '0'] }.joinToString(" ")
        return "$intWords $HE_POINT $frac"
    }

    /** Renders 0‥999,999,999 in masculine absolute form; larger values fall back to digit-by-digit. */
    private fun hebrewInteger(n: Long): String {
        if (n == 0L) return HE_UNITS[0]
        if (n < 0L) return HE_UNITS[0] // unreachable via the digit-only regex
        if (n >= 1_000_000_000L) return n.toString().map { HE_UNITS[it - '0'] }.joinToString(" ")

        // Each (string, componentCount) group renders its own count; the count tells
        // the caller whether the group is a single element that needs the ו bridge.
        val groups = mutableListOf<Pair<String, Int>>()
        val millions = (n / 1_000_000L).toInt()
        val thousands = ((n / 1000L) % 1000L).toInt()
        val below = (n % 1000L).toInt()
        if (millions > 0) groups.add(hebrewMillionsGroup(millions))
        if (thousands > 0) groups.add(hebrewThousandsGroup(thousands))
        if (below > 0) {
            val els = hebrewBelowThousandElements(below)
            groups.add(joinFinalVav(els) to els.size)
        }

        val sb = StringBuilder()
        for ((idx, group) in groups.withIndex()) {
            val (str, count) = group
            if (idx == 0) {
                sb.append(str)
            } else {
                sb.append(' ')
                // A single-element trailing group ("...אלף וחמישה") takes the ו bridge;
                // a multi-element one already carries its own internal ו.
                if (count == 1) sb.append(HE_VAV).append(str) else sb.append(str)
            }
        }
        return sb.toString()
    }

    /** Ordered atomic components (hundreds / tens / units), without any ו connector. */
    private fun hebrewBelowThousandElements(n: Int): List<String> {
        val els = mutableListOf<String>()
        var rem = n
        if (rem >= 100) {
            val h = rem / 100
            els.add(
                when (h) {
                    1 -> HE_MEA
                    2 -> HE_MATAYIM
                    else -> HE_HUNDREDS_COUNT[h] + " " + HE_MEOT
                },
            )
            rem %= 100
        }
        when {
            rem == 0 -> {}
            rem < 10 -> els.add(HE_UNITS[rem])
            rem == 10 -> els.add(HE_TEN)
            rem <= 19 -> els.add(HE_TEENS[rem - 11])
            else -> {
                els.add(HE_TENS[rem / 10])
                if (rem % 10 != 0) els.add(HE_UNITS[rem % 10])
            }
        }
        return els
    }

    /** Joins components with spaces, prefixing ו to the final component. */
    private fun joinFinalVav(els: List<String>): String {
        if (els.isEmpty()) return ""
        if (els.size == 1) return els[0]
        val head = els.subList(0, els.size - 1).joinToString(" ")
        return head + " " + HE_VAV + els.last()
    }

    private fun hebrewThousandsGroup(count: Int): Pair<String, Int> = when {
        count == 1 -> HE_ELEF to 1
        count == 2 -> HE_ALPAYIM to 1
        count in 3..9 -> (HE_THOUSANDS_CONSTRUCT[count] + " " + HE_ALAPIM) to 1
        else -> {
            val els = hebrewBelowThousandElements(count)
            (joinFinalVav(els) + " " + HE_ELEF) to els.size
        }
    }

    private fun hebrewMillionsGroup(count: Int): Pair<String, Int> = when {
        count == 1 -> HE_MILLION to 1
        count == 2 -> (HE_SHNEI + " " + HE_MILLION) to 1
        else -> {
            val els = hebrewBelowThousandElements(count)
            (joinFinalVav(els) + " " + HE_MILLION) to els.size
        }
    }

    private fun hebrewCurrency(symbol: String, amount: String): String {
        val numWords = hebrewNumber(amount)
        val c = stripCommas(amount)
        val dot = c.indexOf('.')
        val whole = (if (dot == -1) c else c.substring(0, dot)).toLong()
        val word = when (symbol) {
            "₪" -> if (whole == 1L) HE_SHEKEL_S else HE_SHEKEL_P
            "\$" -> if (whole == 1L) HE_DOLLAR_S else HE_DOLLAR_P
            else -> HE_EURO // €
        }
        return "$numWords $word"
    }

    // ---------------------------------------------------------------------------

    private fun stripCommas(s: String): String = s.replace(",", "")
}
