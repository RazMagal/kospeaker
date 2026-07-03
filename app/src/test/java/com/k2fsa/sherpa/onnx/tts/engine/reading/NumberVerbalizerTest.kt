package com.k2fsa.sherpa.onnx.tts.engine.reading

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for [NumberVerbalizer]. These run without an Android device.
 * All Hebrew literals are real UTF-8 text and were generated from their Unicode
 * code points, so the expected strings can be trusted character-by-character.
 */
class NumberVerbalizerTest {

    private fun en(text: String, options: NumberOptions = NumberOptions()) =
        NumberVerbalizer.verbalize(text, TextScript.LATIN, options)

    private fun he(text: String, options: NumberOptions = NumberOptions()) =
        NumberVerbalizer.verbalize(text, TextScript.HEBREW, options)

    // --- English: cardinals ----------------------------------------------------

    @Test
    fun englishCardinals() {
        assertEquals("zero", en("0"))
        assertEquals("seven", en("7"))
        assertEquals("forty-two", en("42"))
        assertEquals("one hundred", en("100"))
        assertEquals("nine hundred ninety-nine", en("999"))
        assertEquals("one thousand", en("1000"))
        assertEquals("one million", en("1000000"))
    }

    @Test
    fun englishCommaGroupedIntegers() {
        assertEquals("one thousand", en("1,000"))
        assertEquals("one thousand two hundred fifty", en("1,250"))
        assertEquals("two million five hundred thousand", en("2,500,000"))
    }

    @Test
    fun englishDecimalsAreReadDigitByDigit() {
        assertEquals("three point one four", en("3.14"))
        assertEquals("zero point five", en("0.5"))
    }

    // --- English: years --------------------------------------------------------

    @Test
    fun englishYears() {
        assertEquals("nineteen ninety-six", en("1996"))
        assertEquals("nineteen oh five", en("1905"))
        assertEquals("nineteen hundred", en("1900"))
        assertEquals("two thousand", en("2000"))
        assertEquals("two thousand five", en("2005"))
        assertEquals("twenty ten", en("2010"))
        assertEquals("twenty twenty-four", en("2024"))
    }

    @Test
    fun englishYearReadAsCardinalWhenDisabled() {
        assertEquals(
            "one thousand nine hundred ninety-six",
            en("1996", NumberOptions(verbalizeYears = false)),
        )
    }

    // --- English: ordinals -----------------------------------------------------

    @Test
    fun englishOrdinals() {
        assertEquals("first", en("1st"))
        assertEquals("second", en("2nd"))
        assertEquals("third", en("3rd"))
        assertEquals("twenty-first", en("21st"))
        assertEquals("one hundredth", en("100th"))
    }

    // --- English: percent and currency ----------------------------------------

    @Test
    fun englishPercent() {
        assertEquals("fifty percent", en("50%"))
    }

    @Test
    fun englishCurrency() {
        assertEquals("five dollars", en("\$5"))
        assertEquals("one dollar", en("\$1"))
        assertEquals("one dollar and fifty cents", en("\$1.50"))
        assertEquals("three pounds", en("£3"))
        assertEquals("ten euros", en("€10"))
    }

    @Test
    fun englishRealWorldSentence() {
        assertEquals(
            "In nineteen ninety-six he paid one thousand two hundred fifty dollars for three books.",
            en("In 1996 he paid \$1,250 for 3 books."),
        )
    }

    @Test
    fun englishFlagsSuppressVerbalization() {
        assertEquals("50%", en("50%", NumberOptions(verbalizePercent = false)))
        assertEquals("1st", en("1st", NumberOptions(verbalizeOrdinals = false)))
        assertEquals("\$5", en("\$5", NumberOptions(verbalizeCurrency = false)))
    }

    // --- Hebrew: cardinals (masculine absolute form) --------------------------

    @Test
    fun hebrewUnitsAndZero() {
        assertEquals("אפס", he("0"))
        assertEquals("חמישה", he("5"))
    }

    @Test
    fun hebrewTeen() {
        assertEquals("אחד עשר", he("11"))
    }

    @Test
    fun hebrewTensJoinWithVavConnector() {
        assertEquals("עשרים ואחד", he("21"))
        assertEquals("ארבעים ושניים", he("42"))
    }

    @Test
    fun hebrewHundreds() {
        assertEquals("מאה", he("100"))
        assertEquals("מאה שלושים ושבעה", he("137"))
    }

    @Test
    fun hebrewThousands() {
        assertEquals("אלף", he("1000"))
    }

    @Test
    fun hebrewYearReadAsPlainCardinal() {
        // No English-style century pairing: 2024 is a plain cardinal.
        assertEquals("אלפיים עשרים וארבעה", he("2024"))
    }

    @Test
    fun hebrewDecimal() {
        assertEquals("שלושה נקודה אחד ארבעה", he("3.14"))
    }

    @Test
    fun hebrewPercent() {
        assertEquals("חמישים אחוז", he("50%"))
    }

    @Test
    fun hebrewNumberInSentenceIsNotDropped() {
        // The Hebrew MMS voice has no digits, so "3" would vanish; it must become a word.
        assertEquals("יש לי שלושה ספרים", he("יש לי 3 ספרים"))
    }
}
