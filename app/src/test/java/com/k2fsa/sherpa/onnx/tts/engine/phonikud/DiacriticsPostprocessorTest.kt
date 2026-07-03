package com.k2fsa.sherpa.onnx.tts.engine.phonikud

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for [DiacriticsPostprocessor].
 *
 * Synthetic (one-hot) logits are fed in and the emitted niqqud string is asserted, exercising:
 * argmax nikud/shin selection, the `> 0` binary heads (stress ֫ / vocal-shva ֽ / prefix |),
 * MAT_LECT handling, and the per-letter emission order char+shin+nikud+stress+vocalShva+prefix.
 *
 * NIKUD_CLASSES index reference: 0="", 1=<MAT_LECT>, 2=ּ(U+05BC), 3=ְ, 4=ֱ, 5=ֲ, 6=ֳ, 7=ִ,
 * 8=ֵ, 9=ֶ, 10=ַ(patah U+05B7), 11=ָ(kamatz U+05B8), 12=ֹ(holam U+05B9), ...
 */
class DiacriticsPostprocessorTest {

    private val D = DiacriticsPostprocessor

    private fun oneHot(size: Int, idx: Int): FloatArray =
        FloatArray(size) { if (it == idx) 1f else 0f }

    /** No head fires ( < 0 ) */
    private val OFF = floatArrayOf(-1f, -1f, -1f)

    // Single letter + patah + stress
    @Test fun letterWithNikudAndStress() {
        val out = D.postprocess(
            chars = listOf('ב'),
            nikudLogits = arrayOf(oneHot(29, 10)), // ַ  U+05B7
            shinLogits = arrayOf(oneHot(2, 0)),
            additionalLogits = arrayOf(floatArrayOf(1f, -1f, -1f)), // stress on
        )
        assertEquals("בַ֫", out) // ב + patah + ole
    }

    // Shin with the SIN dot chosen
    @Test fun shinWithSinDot() {
        val out = D.postprocess(
            chars = listOf('ש'),
            nikudLogits = arrayOf(oneHot(29, 0)), // ""
            shinLogits = arrayOf(oneHot(2, 1)),   // ׂ  U+05C2 (sin)
            additionalLogits = arrayOf(OFF),
        )
        assertEquals("שׂ", out)
    }

    // Shin dot + kamatz, verifies char+shin+nikud ordering
    @Test fun shinWithShinDotAndKamatz() {
        val out = D.postprocess(
            chars = listOf('ש'),
            nikudLogits = arrayOf(oneHot(29, 11)), // ָ  U+05B8
            shinLogits = arrayOf(oneHot(2, 0)),    // ׁ  U+05C1 (shin)
            additionalLogits = arrayOf(OFF),
        )
        assertEquals("שָׁ", out)
    }

    // Full-word assembly: שלום -> שָׁלוֹם
    @Test fun assemblesFullWord() {
        val out = D.postprocess(
            chars = listOf('ש', 'ל', 'ו', 'ם'),
            nikudLogits = arrayOf(oneHot(29, 11), oneHot(29, 0), oneHot(29, 12), oneHot(29, 0)),
            shinLogits = arrayOf(oneHot(2, 0), oneHot(2, 0), oneHot(2, 0), oneHot(2, 0)),
            additionalLogits = arrayOf(OFF, OFF, OFF, OFF),
        )
        // ש + shin-dot + kamatz | ל | ו + holam | ם
        assertEquals("שָׁלוֹם", out)
    }

    // MAT_LECT predicted on a non-matres letter -> nikud dropped (model.py sets nikud="" and
    // FALLS THROUGH, so the binary heads still apply; here they are off, so we get a bare ב).
    @Test fun matLectOnNonMatresLetterDropsNikud() {
        val out = D.postprocess(
            chars = listOf('ב'),
            nikudLogits = arrayOf(oneHot(29, 1)), // <MAT_LECT>
            shinLogits = arrayOf(oneHot(2, 0)),
            additionalLogits = arrayOf(OFF),
        )
        assertEquals("ב", out) // just ב, no diacritics/marks
    }

    // MAT_LECT on a non-matres letter with heads ON -> nikud dropped but marks still emitted.
    @Test fun matLectOnNonMatresLetterStillAppliesHeads() {
        val out = D.postprocess(
            chars = listOf('ב'),
            nikudLogits = arrayOf(oneHot(29, 1)), // <MAT_LECT>
            shinLogits = arrayOf(oneHot(2, 0)),
            additionalLogits = arrayOf(floatArrayOf(1f, 1f, 1f)), // stress, vocal-shva, prefix
        )
        assertEquals("בֽ֫|", out) // ב + ole(֫) + meteg(ֽ) + prefix(|)
    }

    // MAT_LECT on a matres letter with no marker -> emit bare char, skip heads
    @Test fun matLectOnMatresLetterNoMarkerEmitsBareChar() {
        val out = D.postprocess(
            chars = listOf('ו'),
            nikudLogits = arrayOf(oneHot(29, 1)), // <MAT_LECT>
            shinLogits = arrayOf(oneHot(2, 0)),
            additionalLogits = arrayOf(floatArrayOf(1f, 1f, 1f)),
            markMatresLectionis = null,
        )
        assertEquals("ו", out)
    }

    // MAT_LECT on a matres letter WITH a marker -> marker substituted for the nikud
    @Test fun matLectOnMatresLetterWithMarker() {
        val out = D.postprocess(
            chars = listOf('א'),
            nikudLogits = arrayOf(oneHot(29, 1)), // <MAT_LECT>
            shinLogits = arrayOf(oneHot(2, 0)),
            additionalLogits = arrayOf(OFF),
            markMatresLectionis = "*",
        )
        assertEquals("א*", out)
    }

    // All three binary heads on, on a shin: verify full emission order
    @Test fun allHeadsOnEmissionOrder() {
        val out = D.postprocess(
            chars = listOf('ש'),
            nikudLogits = arrayOf(oneHot(29, 10)), // ַ  patah
            shinLogits = arrayOf(oneHot(2, 1)),    // ׂ  sin
            additionalLogits = arrayOf(floatArrayOf(1f, 1f, 1f)), // stress, vocal-shva, prefix
        )
        // char + shin + nikud + stress(֫) + vocalShva(ֽ) + prefix(|)
        assertEquals("שַֽׂ֫|", out)
    }

    // Threshold is strict ( > 0 ): a value of exactly 0 does NOT fire.
    @Test fun binaryHeadThresholdIsStrict() {
        val out = D.postprocess(
            chars = listOf('ל'),
            nikudLogits = arrayOf(oneHot(29, 0)),
            shinLogits = arrayOf(oneHot(2, 0)),
            additionalLogits = arrayOf(floatArrayOf(0f, 0f, 0f)), // exactly 0 -> off
        )
        assertEquals("ל", out)
    }

    // Non-Hebrew characters pass through unchanged.
    @Test fun nonHebrewCharPassesThrough() {
        val out = D.postprocess(
            chars = listOf('x'),
            nikudLogits = arrayOf(oneHot(29, 10)),
            shinLogits = arrayOf(oneHot(2, 0)),
            additionalLogits = arrayOf(floatArrayOf(1f, 1f, 1f)),
        )
        assertEquals("x", out)
    }

    // argmax picks the highest logit even when not one-hot.
    @Test fun argmaxPicksHighestLogit() {
        val row = FloatArray(29) { -5f }
        row[7] = 2.5f  // ִ  hiriq U+05B4
        row[10] = 1.0f
        val out = D.postprocess(
            chars = listOf('ל'),
            nikudLogits = arrayOf(row),
            shinLogits = arrayOf(oneHot(2, 0)),
            additionalLogits = arrayOf(OFF),
        )
        assertEquals("לִ", out)
    }

    // Constant-table sanity.
    @Test fun nikudClassesHave29Entries() {
        assertEquals(29, D.NIKUD_CLASSES.size)
        assertEquals("", D.NIKUD_CLASSES[0])
        assertEquals("<MAT_LECT>", D.NIKUD_CLASSES[1])
        assertEquals("ׇ", D.NIKUD_CLASSES[27]) // kamatz katan
    }
}
