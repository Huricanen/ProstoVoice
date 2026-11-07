package com.prostovoice.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptAlignerTest {
    @Test
    fun clampsIntervalsAndSelectsSpeakerByMaximumOverlap() {
        val result = TranscriptAligner.align(
            asrSegments = listOf(AsrSegment(" hello ", -100, 1_200)),
            speakerSegments = listOf(
                SpeakerSegment("A", 0, 500),
                SpeakerSegment("B", 500, 1_000),
            ),
            durationMs = 1_000,
        )

        assertEquals(1, result.size)
        assertEquals("A", result.single().speaker)
        assertEquals(0L, result.single().startMs)
        assertEquals(1_000L, result.single().endMs)
        assertEquals("hello", result.single().text)
    }

    @Test
    fun mergesAdjacentSameSpeakerAndSmoothsShortSwitch() {
        val result = TranscriptAligner.align(
            asrSegments = listOf(
                AsrSegment("one", 0, 1_000),
                AsrSegment("noise", 1_000, 1_200),
                AsrSegment("two", 1_200, 2_000),
            ),
            speakerSegments = listOf(
                SpeakerSegment("A", 0, 1_000),
                SpeakerSegment("B", 1_000, 1_200),
                SpeakerSegment("A", 1_200, 2_000),
            ),
            durationMs = 2_000,
            shortSwitchThresholdMs = 300,
        )

        assertEquals(1, result.size)
        assertEquals("A", result.single().speaker)
        assertEquals("one noise two", result.single().text)
    }

    @Test
    fun dropsInvalidAsrIntervalsAndUsesUnknownWithoutSpeakerOverlap() {
        val result = TranscriptAligner.align(
            asrSegments = listOf(
                AsrSegment("invalid", 2_000, 1_000),
                AsrSegment("unmatched", 100, 200),
            ),
            speakerSegments = emptyList(),
            durationMs = 1_000,
        )

        assertEquals(1, result.size)
        assertEquals("UNKNOWN", result.single().speaker)
        assertTrue(result.single().startMs >= 0L)
    }

    @Test
    fun finalShortUtteranceUsesSpeakerAtItsTail() {
        val result = TranscriptAligner.align(
            asrSegments = listOf(
                AsrSegment("long turn", 0, 2_000),
                AsrSegment("short final turn", 2_000, 3_000),
            ),
            speakerSegments = listOf(
                SpeakerSegment("A", 0, 2_650),
                SpeakerSegment("B", 2_650, 3_000),
            ),
            durationMs = 3_000,
        )

        assertEquals("B", result.last().speaker)
    }

    @Test
    fun splitsAsrTextWhenSpeakerChangesInsideOneAsrSegment() {
        val result = TranscriptAligner.align(
            asrSegments = listOf(AsrSegment("first person second person", 0, 2_000)),
            speakerSegments = listOf(
                SpeakerSegment("A", 0, 1_000),
                SpeakerSegment("B", 1_000, 2_000),
            ),
            durationMs = 2_000,
        )

        assertEquals(2, result.size)
        assertEquals(listOf("A", "B"), result.map { it.speaker })
        assertEquals("first person", result[0].text)
        assertEquals("second person", result[1].text)
    }

    @Test
    fun fillsShortDiarizationGapWithNearestSpeaker() {
        val result = TranscriptAligner.align(
            asrSegments = listOf(AsrSegment("hello", 400, 450)),
            speakerSegments = listOf(
                SpeakerSegment("A", 0, 400),
                SpeakerSegment("B", 450, 1_000),
            ),
            durationMs = 1_000,
        )

        assertEquals("B", result.single().speaker)
    }

    @Test
    fun removesPartialRepeatedPhraseWhenMergingAdjacentAsrSegments() {
        val result = TranscriptAligner.align(
            asrSegments = listOf(
                AsrSegment("Верно. Предложим ему", 55_000, 58_000),
                AsrSegment("за ему. предложим ему займы.", 58_000, 61_000),
            ),
            speakerSegments = listOf(SpeakerSegment("A", 55_000, 61_000)),
            durationMs = 61_000,
        )

        assertEquals("Верно. Предложим ему займы.", result.single().text)
    }
}
