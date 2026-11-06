package com.prostovoice.audio

import com.prostovoice.domain.AsrSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioChunkerTest {
    @Test
    fun splitUsesOverlapAndPreservesAllSamples() {
        val chunks = AudioChunker.split(
            samples = FloatArray(65_000),
            chunkDurationMs = 2_000,
            overlapMs = 500,
        )

        assertEquals(listOf(0L, 24_000L, 48_000L), chunks.map { it.startSample })
        assertEquals(32_000, chunks[0].samples.size)
        assertEquals(17_000, chunks.last().samples.size)
        assertEquals(65_000L, chunks.last().endSample)
    }

    @Test
    fun deduplicateAsrSegmentsDropsOverlappingDuplicateText() {
        val result = AudioChunker.deduplicateAsrSegments(
            listOf(
                AsrSegment("Привет всем", 28_000, 30_000),
                AsrSegment("привет   всем", 29_500, 31_500),
                AsrSegment("Следующая фраза", 31_500, 33_000),
            ),
        )

        assertEquals(listOf("Привет всем", "Следующая фраза"), result.map { it.text })
    }
}
