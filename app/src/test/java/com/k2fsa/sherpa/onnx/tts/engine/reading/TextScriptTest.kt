package com.k2fsa.sherpa.onnx.tts.engine.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [detectScript] and its letter-classification helpers.
 * All Hebrew literals are real UTF-8 text.
 */
class TextScriptTest {

    @Test
    fun pureHebrewIsHebrew() {
        assertEquals(TextScript.HEBREW, detectScript("שלום עולם"))
        // Even with a stray digit and punctuation the letters are all Hebrew.
        assertEquals(TextScript.HEBREW, detectScript("מה שלומך היום? 7"))
    }

    @Test
    fun pureEnglishIsLatin() {
        assertEquals(TextScript.LATIN, detectScript("The quick brown fox."))
        // Accented Latin still counts as Latin.
        assertEquals(TextScript.LATIN, detectScript("café naïve résumé"))
    }

    @Test
    fun balancedHebrewAndLatinIsMixed() {
        // "hello" = 5 Latin letters, "שלום" = 4 Hebrew letters: neither dominates.
        assertEquals(TextScript.MIXED, detectScript("hello שלום"))
    }

    @Test
    fun oneLatinWordInLongHebrewStaysHebrew() {
        // A lone English abbreviation inside Hebrew prose must not flip the script.
        assertEquals(TextScript.HEBREW, detectScript("המורה Mr. כהן הגיע אל הבית הגדול"))
    }

    @Test
    fun digitsAndPunctuationOnlyAreOther() {
        assertEquals(TextScript.OTHER, detectScript("12345 -- 67.89 !!! ,.;:"))
        assertEquals(TextScript.OTHER, detectScript(""))
        assertEquals(TextScript.OTHER, detectScript("   \n\t "))
    }

    @Test
    fun isHebrewCoversConsonantRangeOnly() {
        assertTrue(isHebrew(0x05D0))  // alef
        assertTrue(isHebrew(0x05EA))  // tav
        assertTrue(isHebrew(0x05DD))  // final mem
        assertFalse(isHebrew(0x05BE)) // maqaf (punctuation)
        assertFalse(isHebrew(0x05B8)) // qamats (niqqud)
        assertFalse(isHebrew(0x0041)) // 'A'
    }

    @Test
    fun isLatinLetterCoversAsciiAndAccented() {
        assertTrue(isLatinLetter('A'.code))
        assertTrue(isLatinLetter('z'.code))
        assertTrue(isLatinLetter('é'.code))
        assertFalse(isLatinLetter('5'.code))
        assertFalse(isLatinLetter('×'.code))     // multiplication sign, not a letter
        assertFalse(isLatinLetter(0x05D0))       // Hebrew alef
    }
}
