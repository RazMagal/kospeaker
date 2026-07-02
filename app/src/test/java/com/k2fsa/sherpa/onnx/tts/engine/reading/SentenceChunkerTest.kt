package com.k2fsa.sherpa.onnx.tts.engine.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [SentenceChunker]. These run without an Android device.
 */
class SentenceChunkerTest {

    // --- Basic sentence splitting ---------------------------------------------

    @Test
    fun splitsOnSentenceBoundaries() {
        val chunks = SentenceChunker.chunk(
            "The quick brown fox jumps over the lazy dog. She sells sea shells by the seashore.",
        )
        assertEquals(2, chunks.size)
        assertTrue(chunks[0].endsWith("dog."))
        assertTrue(chunks[1].startsWith("She"))
    }

    @Test
    fun splitsOnEllipsisBoundary() {
        val chunks = SentenceChunker.chunk(
            "The storm was approaching very quickly now... Everyone ran for shelter and safety.",
        )
        assertEquals(2, chunks.size)
        assertTrue(chunks[0].endsWith("..."))
    }

    // --- Cases that must NOT split --------------------------------------------

    @Test
    fun doesNotSplitOnAbbreviations() {
        val chunks = SentenceChunker.chunk(
            "Mr. Smith and Dr. Jones walked to the old town hall together this morning.",
        )
        assertEquals(1, chunks.size)
    }

    @Test
    fun doesNotSplitOnDecimals() {
        val chunks = SentenceChunker.chunk(
            "The value of pi is 3.14 and it never really ends in this universe.",
        )
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].contains("3.14"))
    }

    @Test
    fun doesNotSplitOnInitials() {
        val chunks = SentenceChunker.chunk(
            "J. R. R. Tolkien wrote a very long fantasy novel about a magic ring.",
        )
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].contains("Tolkien"))
    }

    // --- Length management -----------------------------------------------------

    @Test
    fun splitsLongSentenceOnClausePunctuation() {
        val long = "Alpha, beta, gamma, delta, epsilon, zeta, eta, theta, iota, " +
            "kappa, lambda, mu, nu, xi, omicron, pi, rho, sigma, tau, upsilon."
        val chunks = SentenceChunker.chunk(long, maxChars = 40)

        assertTrue("expected multiple chunks", chunks.size > 1)
        assertTrue("every chunk must respect maxChars", chunks.all { it.length <= 40 })
        assertTrue("no blank chunks", chunks.none { it.isBlank() })
        // Content is preserved across the split.
        assertTrue(chunks.joinToString(" ").contains("omicron"))
    }

    @Test
    fun mergesVeryShortFragments() {
        val chunks = SentenceChunker.chunk(
            "Yes. Sure. Okay. The meeting is now finally over for good.",
        )
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].contains("Yes"))
        assertTrue(chunks[0].contains("meeting"))
    }

    @Test
    fun neverReturnsBlankChunks() {
        val chunks = SentenceChunker.chunk(
            "First sentence here. Second one follows. Third and final sentence to read now.",
        )
        assertEquals(2, chunks.size)
        assertTrue(chunks.none { it.isBlank() })
    }

    // --- Edge cases ------------------------------------------------------------

    @Test
    fun emptyAndBlankYieldEmptyList() {
        assertTrue(SentenceChunker.chunk("").isEmpty())
        assertTrue(SentenceChunker.chunk("   \n\t ").isEmpty())
    }

    @Test
    fun singleWordAndUnterminatedTextArePreserved() {
        assertEquals(listOf("Hello"), SentenceChunker.chunk("Hello"))
        assertEquals(listOf("Hello world"), SentenceChunker.chunk("Hello world"))
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals(
            listOf("Hello there friend."),
            SentenceChunker.chunk("  Hello there friend.  "),
        )
    }
}
