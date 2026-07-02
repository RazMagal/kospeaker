package com.k2fsa.sherpa.onnx.tts.engine.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [TextNormalizer]. These run without an Android device.
 */
class TextNormalizerTest {

    // --- Abbreviation expansion ------------------------------------------------

    @Test
    fun expandsPersonalTitles() {
        assertEquals(
            "Mister Smith met Missus Jones and Doctor Watson.",
            TextNormalizer.normalize("Mr. Smith met Mrs. Jones and Dr. Watson."),
        )
    }

    @Test
    fun expandsMsAndProfessor() {
        assertEquals(
            "Miss Lee introduced Professor Kim.",
            TextNormalizer.normalize("Ms. Lee introduced Prof. Kim."),
        )
    }

    @Test
    fun expandsLatinAndListAbbreviations() {
        assertEquals(
            "cats versus dogs, for example tabbies, et cetera",
            TextNormalizer.normalize("cats vs. dogs, e.g. tabbies, etc."),
        )
    }

    @Test
    fun expandsIe() {
        assertEquals("stop, that is halt", TextNormalizer.normalize("stop, i.e. halt"))
    }

    @Test
    fun stDotBecomesSaintOnlyBeforeAName() {
        // Followed by a capitalized name -> "Saint".
        assertEquals(
            "Meet me at Saint Paul's cathedral.",
            TextNormalizer.normalize("Meet me at St. Paul's cathedral."),
        )
        // Trailing street reference -> left untouched.
        val street = TextNormalizer.normalize("Turn left on Main St.")
        assertTrue(street.contains("Main St."))
        assertFalse(street.contains("Saint"))
    }

    @Test
    fun noDotBecomesNumberOnlyBeforeADigit() {
        assertEquals("Room Number 7 is here.", TextNormalizer.normalize("Room No. 7 is here."))
        val refusal = TextNormalizer.normalize("No. I will not.")
        assertTrue(refusal.contains("No."))
        assertFalse(refusal.contains("Number"))
    }

    // --- Dashes to pauses ------------------------------------------------------

    @Test
    fun spacedEmDashBecomesComma() {
        assertEquals("wait, really?", TextNormalizer.normalize("wait — really?"))
    }

    @Test
    fun spacedEnDashBecomesComma() {
        assertEquals("pages 3, 5 total", TextNormalizer.normalize("pages 3 – 5 total"))
    }

    @Test
    fun spacedDoubleHyphenBecomesComma() {
        assertEquals("yes, no", TextNormalizer.normalize("yes -- no"))
    }

    // --- Whitespace and invisible characters ----------------------------------

    @Test
    fun collapsesWhitespaceAndStripsSoftHyphen() {
        // "hel­lo" contains a soft hyphen inserted for on-screen justification.
        assertEquals(
            "hello world test",
            TextNormalizer.normalize("hel­lo\n\n  world\ttest"),
        )
    }

    @Test
    fun stripsZeroWidthCharacters() {
        // Zero-width space, ZWNJ, ZWJ and the byte-order mark, interleaved with letters.
        assertEquals("abcde", TextNormalizer.normalize("a​b‌c‍d﻿e"))
    }

    // --- Punctuation normalization --------------------------------------------

    @Test
    fun normalizesSmartQuotes() {
        assertEquals(
            "'hi' and \"bye\"",
            TextNormalizer.normalize("‘hi’ and “bye”"),
        )
    }

    @Test
    fun normalizesEllipsisGlyph() {
        assertEquals("well... ok", TextNormalizer.normalize("well… ok"))
    }

    // --- Footnote / reference artifacts ---------------------------------------

    @Test
    fun stripsBracketedNumericCitations() {
        assertEquals(
            "The cat sat there.",
            TextNormalizer.normalize("The cat[12] sat[3, 4] there."),
        )
    }

    @Test
    fun keepsNonNumericBracketedAnnotations() {
        assertEquals("mistake [sic] here", TextNormalizer.normalize("mistake [sic] here"))
    }

    @Test
    fun stripsSuperscriptFootnoteMarkers() {
        assertEquals("note here", TextNormalizer.normalize("note⁵ here"))
    }

    // --- Options / edge cases --------------------------------------------------

    @Test
    fun respectsDisabledOptions() {
        assertEquals(
            "Mr. Smith",
            TextNormalizer.normalize("Mr. Smith", NormalizerOptions(expandAbbreviations = false)),
        )
        assertEquals(
            "a — b",
            TextNormalizer.normalize("a — b", NormalizerOptions(dashesToPauses = false)),
        )
    }

    @Test
    fun blankInputReturnsEmpty() {
        assertEquals("", TextNormalizer.normalize(""))
        assertEquals("", TextNormalizer.normalize("   \n\t "))
    }

    @Test
    fun handlesRealWorldMixedParagraph() {
        assertEquals(
            "the fox, according to Doctor Smith, jumps...",
            TextNormalizer.normalize("the fox — according to Dr. Smith [3] — jumps…"),
        )
    }
}
