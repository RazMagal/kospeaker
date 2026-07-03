package com.k2fsa.sherpa.onnx.tts.engine.phonikud

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for [HebrewCharTokenizer]. The vocab is supplied by the caller; here we use a
 * tiny synthetic vocab with the standard BERT special tokens.
 */
class HebrewCharTokenizerTest {

    private val vocab = mapOf(
        "[PAD]" to 0, "[UNK]" to 1, "[CLS]" to 2, "[SEP]" to 3,
        "ש" to 10, "ל" to 11, "ו" to 12, "ם" to 13,
    )

    @Test fun framesWithClsSepAndOffsets() {
        val e = HebrewCharTokenizer.tokenize("שלום", vocab)
        assertArrayEquals(longArrayOf(2, 10, 11, 12, 13, 3), e.inputIds)
        assertArrayEquals(longArrayOf(1, 1, 1, 1, 1, 1), e.attentionMask)
        assertArrayEquals(longArrayOf(0, 0, 0, 0, 0, 0), e.tokenTypeIds)
        assertEquals(
            listOf(0 to 0, 0 to 1, 1 to 2, 2 to 3, 3 to 4, 0 to 0),
            e.offsets,
        )
    }

    @Test fun unknownCharMapsToUnk() {
        val e = HebrewCharTokenizer.tokenize("שx", vocab)
        assertArrayEquals(longArrayOf(2, 10, 1, 3), e.inputIds) // x -> [UNK]=1
    }

    @Test fun padsToRequestedLength() {
        val e = HebrewCharTokenizer.tokenize("שלום", vocab, padToLength = 8)
        assertArrayEquals(longArrayOf(2, 10, 11, 12, 13, 3, 0, 0), e.inputIds)
        assertArrayEquals(longArrayOf(1, 1, 1, 1, 1, 1, 0, 0), e.attentionMask)
        assertEquals(8, e.tokenTypeIds.size)
        assertEquals(0 to 0, e.offsets.last())
    }

    @Test fun emptyTextIsJustClsSep() {
        val e = HebrewCharTokenizer.tokenize("", vocab)
        assertArrayEquals(longArrayOf(2, 3), e.inputIds)
    }
}
