package com.prostovoice.diarization

import org.junit.Assert.assertEquals
import org.junit.Test
import com.prostovoice.domain.SpeakerSegment
import com.prostovoice.domain.TranscriptAligner
import com.prostovoice.domain.AsrWord

class SpeakerClusteringTest {
    @Test
    fun `normalizes sparse native labels by first appearance`() {
        val result = TranscriptAligner.normalizeSpeakerLabels(
            listOf(
                SpeakerSegment("speaker_6", 2000, 3000),
                SpeakerSegment("speaker_1", 0, 1000),
                SpeakerSegment("speaker_6", 4000, 5000),
                SpeakerSegment("speaker_4", 3000, 4000),
            ),
        )
        assertEquals(
            listOf("speaker_1", "speaker_2", "speaker_3", "speaker_2"),
            result.map { it.speaker },
        )
    }

    @Test
    fun `assigns words to speakers at word timestamps`() {
        val result = TranscriptAligner.alignWords(
            words = listOf(
                AsrWord("Первый", 0, 500),
                AsrWord("голос", 500, 1000),
                AsrWord("второй", 1200, 1700),
            ),
            speakerSegments = listOf(
                SpeakerSegment("speaker_6", 0, 1100),
                SpeakerSegment("speaker_1", 1100, 2000),
            ),
            durationMs = 2_000,
        )
        assertEquals(listOf("speaker_1", "speaker_2"), result.map { it.speaker })
        assertEquals("Первый голос", result.first().text)
    }

    @Test
    fun `groups close cosine vectors and assigns stable labels`() {
        val result = SpeakerClustering.cluster(
            listOf(
                SpeakerEmbedding(0, 1000, floatArrayOf(1f, 0f)),
                SpeakerEmbedding(1000, 2000, floatArrayOf(0.99f, 0.01f)),
                SpeakerEmbedding(2000, 3000, floatArrayOf(0f, 1f)),
            ),
        )
        assertEquals(listOf("speaker_1", "speaker_2"), result.map { it.speaker })
        assertEquals(0L, result.first().startMs)
        assertEquals(3000L, result.last().endMs)
    }
}
