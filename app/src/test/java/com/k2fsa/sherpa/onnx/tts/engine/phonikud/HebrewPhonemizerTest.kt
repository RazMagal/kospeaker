package com.k2fsa.sherpa.onnx.tts.engine.phonikud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [HebrewPhonemizer].
 *
 * The expected phonemizations are REAL reference examples taken from the phonikud repository's
 * authoritative test table:
 *   thewh1teagle/phonikud  tests/phonemize_test_tables/basic.csv
 * That table is written in the "plain" schema; this port is fixed to the "modern" schema, so the
 * three final replacements (x -> χ, r -> ʁ, g -> ɡ) are applied to the reference values. Every
 * expected string below equals `plainToModern(basic.csv value)` and was cross-checked with a
 * faithful reference reimplementation of the phonikud FST.
 *
 * Expected outputs use explicit \u escapes so the exact IPA code points are verifiable:
 *   ʃ=U+0283  ʔ=U+0294  ˈ=U+02C8  ʁ=U+0281  χ=U+03C7  ɡ=U+0261  ʒ=U+0292
 */
class HebrewPhonemizerTest {

    private fun phon(s: String) = HebrewPhonemizer.phonemize(s)

    // ---- Word-level reference examples (basic.csv, modern schema) ------------------

    @Test fun shalom() = assertEquals("ʃalˈom", phon("שָׁלוֹם"))

    @Test fun erevGivenStress() = assertEquals("ʔˈeʁev", phon("עֶ֫רֶב"))

    @Test fun hashemDageshShin() = assertEquals("haʃˈem", phon("הַשֵּׁם"))

    @Test fun shmurimInitialSilentShvaAndShuruk() =
        assertEquals("ʃmuʁˈim", phon("שְׁמוּרִים"))

    @Test fun morehFinalHeElided() = assertEquals("moʁˈe", phon("מוֹרֶה"))

    @Test fun morahKamatz() = assertEquals("moʁˈa", phon("מוֹרָה"))

    @Test fun israelSinAndVoweledAlef() =
        assertEquals("jisʁaʔˈel", phon("יִשְׂרָאֵל"))

    @Test fun kanEmKriahSilentAlef() = assertEquals("kˈan", phon("כָּאן"))

    @Test fun loFinalGlottalStopStripped() = assertEquals("lˈo", phon("לֹ֫א"))

    @Test fun zotEmKriah() = assertEquals("zˈot", phon("זֹאת"))

    @Test fun bayitDageshBet() = assertEquals("bˈajit", phon("בַּ֫יִת"))

    @Test fun kachModernHet() = assertEquals("kˈaχ", phon("כָּ֫ךְ"))

    @Test fun giluachModernGimelAndPatahGnuva() =
        assertEquals("ɡilˈuaχ", phon("גִּלּ֫וּחַ"))

    @Test fun samtaSinAndDageshTav() = assertEquals("sˈamta", phon("שַׂ֫מְתָּ"))

    @Test fun reachYudKriahAndPatahGnuvaAndModernResh() =
        assertEquals("ʁˈeaχ", phon("רֵ֫יחַ"))

    @Test fun chemaModernHetAndFinalHeElided() =
        assertEquals("χemʔˈa", phon("חֶמְאָה"))

    @Test fun negedModernGimel() = assertEquals("nˈeɡed", phon("נֶ֫גֶד"))

    @Test fun kolKamatzKatan() = assertEquals("kˈol", phon("כׇּל"))

    @Test fun machshevonMetegVocalShva() =
        assertEquals("maχʃevˈon", phon("מַחְשֽבוֹן"))

    @Test fun jonratimGereshGimel() =
        assertEquals("midʒonʁatˈim", phon("מִגּ'וֹנְרָטִים"))

    @Test fun davarDageshDalet() = assertEquals("davˈaʁ", phon("דָּבָר"))

    @Test fun chayalDoubledYud() = assertEquals("χajˈal", phon("חַיָּל"))

    // ---- Sentence (space preserved, per-word phonemization) -----------------------

    @Test fun sentenceTwoWords() =
        assertEquals("ʃalˈom ʔˈeʁev", phon("שָׁלוֹם עֶ֫רֶב"))

    @Test fun sentencePreservesPunctuation() {
        // preserve_punctuation = true: the comma and space survive post_clean.
        assertEquals("ʃalˈom, ʁˈeaχ", phon("שָׁלוֹם, רֵ֫יחַ"))
    }

    // ---- Structural properties ----------------------------------------------------

    /** Every emitted symbol must belong to the phoneme inventory (or be a space). */
    @Test fun outputUsesOnlyValidInventorySymbols() {
        val valid = PhonikudLexicon.SET_PHONEMES + ' '
        for (w in listOf("שָׁלוֹם", "יִשְׂרָאֵל", "גִּלּ֫וּחַ", "מִגּ'וֹנְרָטִים")) {
            val out = phon(w)
            assertTrue("unexpected symbol in '$out'", out.all { it in valid })
        }
    }

    /** predict_stress = true: a single voweled word must carry exactly one stress mark. */
    @Test fun stressIsPredictedForUnstressedWord() {
        val out = phon("שָׁלוֹם") // no explicit stress in the input
        assertTrue("stress mark missing in '$out'", out.contains(PhonikudLexicon.STRESS_PHONEME))
        assertEquals(1, out.count { it == PhonikudLexicon.STRESS_PHONEME_CHAR })
    }

    /** normalize(): Hebrew makaf (U+05BE) becomes a hyphen, then post_clean turns it into a space. */
    @Test fun makafBecomesSpace() {
        // "עַל־כֵּן" (with makaf) -> two words separated by a space.
        val out = phon("עַל־כֵּן")
        assertTrue("expected a word boundary space in '$out'", out.contains(' '))
    }

    // ---- Component-level checks ---------------------------------------------------

    @Test fun postNormalizeStripsFinalGlottalAndFinalH() {
        assertEquals("mor", HebrewPhonemizer.postNormalize("morʔ")) // ʔ$
        assertEquals("mor", HebrewPhonemizer.postNormalize("morh"))            // h$
        assertEquals("i", HebrewPhonemizer.postNormalize("ij"))               // ij$ -> i
    }

    @Test fun sortStressMovesMarkBeforeFirstVowel() {
        // ["r","ˈ","i"] -> the standalone stress slot is emptied and the mark is reinserted
        // just before the first vowel (sort_stress does not itself drop empties; _clean does).
        assertEquals(listOf("r", "", "ˈi"), HebrewPhonemizer.sortStress(listOf("r", "ˈ", "i")))
    }
}
