package com.k2fsa.sherpa.onnx.tts.engine.phonikud

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for [PiperTokenizer].
 *
 * The phoneme ids come from the real 157-entry `phoneme_id_map` in the phonikud voice config
 * (thewh1teagle/phonikud-tts-checkpoints/model.config.json). Framing follows piper-onnx:
 *   [bos, pad, id(p1), pad, id(p2), pad, ..., id(pN), pad, eos]
 * with bos=^=1, eos=$=2, pad=_=0.
 */
class PiperTokenizerTest {

    // ʃ=96 a=14 l=24 o=27 m=25 ˈ=120 ; t=32 s=31 d=17 ʒ=108
    @Test fun framesShalomWithPadInterleavingBosEos() {
        assertArrayEquals(
            longArrayOf(1, 0, 96, 0, 14, 0, 24, 0, 27, 0, 25, 0, 2),
            PiperTokenizer.phonemesToIds("ʃalom"),
        )
    }

    @Test fun includesStressMarkId() {
        // ʃalˈom : the stress ˈ (U+02C8) maps to id 120.
        assertArrayEquals(
            longArrayOf(1, 0, 96, 0, 14, 0, 24, 0, 120, 0, 27, 0, 25, 0, 2),
            PiperTokenizer.phonemesToIds("ʃalˈom"),
        )
    }

    @Test fun decomposesMultiCharTsAffricate() {
        // "ts" is not a single key; it decomposes into t(32) + s(31).
        assertArrayEquals(
            longArrayOf(1, 0, 32, 0, 31, 0, 2),
            PiperTokenizer.phonemesToIds("ts"),
        )
    }

    @Test fun decomposesMultiCharDzhAffricate() {
        // "dʒ" -> d(17) + ʒ(108)
        assertArrayEquals(
            longArrayOf(1, 0, 17, 0, 108, 0, 2),
            PiperTokenizer.phonemesToIds("dʒ"),
        )
    }

    @Test fun decomposesMultiCharTshAffricate() {
        // "tʃ" -> t(32) + ʃ(96)
        assertArrayEquals(
            longArrayOf(1, 0, 32, 0, 96, 0, 2),
            PiperTokenizer.phonemesToIds("tʃ"),
        )
    }

    @Test fun skipsSymbolsNotInMap() {
        // A raw Hebrew letter (U+05E9) never appears in Piper phonemes and is not in the map;
        // it is dropped, and the rest is still framed.
        assertArrayEquals(
            longArrayOf(1, 0, 96, 0, 14, 0, 2),
            PiperTokenizer.phonemesToIds("ʃשa"),
        )
    }

    @Test fun emptyStringYieldsBosPadEos() {
        // tokens = [BOS]; BOS is in the map so it gets id+pad, then EOS.
        assertArrayEquals(longArrayOf(1, 0, 2), PiperTokenizer.phonemesToIds(""))
    }

    @Test fun realWordModernSchemaResh() {
        // davˈaʁ : d17 a14 v34 ˈ120 a14 ʁ94
        assertArrayEquals(
            longArrayOf(1, 0, 17, 0, 14, 0, 34, 0, 120, 0, 14, 0, 94, 0, 2),
            PiperTokenizer.phonemesToIds("davˈaʁ"),
        )
    }

    // ---- Map / constant sanity ----------------------------------------------------

    @Test fun specialTokenIds() {
        assertEquals(1L, PiperTokenizer.PHONEME_ID_MAP.getValue("^"))
        assertEquals(2L, PiperTokenizer.PHONEME_ID_MAP.getValue("$"))
        assertEquals(0L, PiperTokenizer.PHONEME_ID_MAP.getValue("_"))
    }

    @Test fun mapHasAll157Entries() {
        assertEquals(157, PiperTokenizer.PHONEME_ID_MAP.size)
    }

    @Test fun inferenceScalesAndSampleRate() {
        assertEquals(0.667f, PiperTokenizer.NOISE_SCALE, 0f)
        assertEquals(1.0f, PiperTokenizer.LENGTH_SCALE, 0f)
        assertEquals(0.8f, PiperTokenizer.NOISE_W, 0f)
        assertEquals(22050, PiperTokenizer.SAMPLE_RATE)
    }

    @Test fun spellsOutHebrewAffricateChiViaModernSchema() {
        // χ (U+03C7) maps to id 127.
        assertArrayEquals(
            longArrayOf(1, 0, 127, 0, 14, 0, 2),
            PiperTokenizer.phonemesToIds("χa"),
        )
    }
}
