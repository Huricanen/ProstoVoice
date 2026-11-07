package com.prostovoice.diarization

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowedSpeakerClusteringTest {
    @Test
    fun `clusters short windows and merges adjacent same speaker`() {
        val result = WindowedSpeakerClustering.cluster(
            listOf(
                WindowEmbedding(0, 1500, floatArrayOf(1f, 0f)),
                WindowEmbedding(500, 2000, floatArrayOf(0.99f, 0.01f)),
                WindowEmbedding(2000, 3500, floatArrayOf(0f, 1f)),
            ),
            requestedClusters = 2,
        )
        assertEquals(listOf("speaker_1", "speaker_2"), result.map { it.speaker })
        assertEquals(2000L, result.last().startMs)
    }

    @Test
    fun `collapses similar windows into one speaker`() {
        val result = WindowedSpeakerClustering.cluster(
            listOf(
                WindowEmbedding(0, 1500, floatArrayOf(1f, 0f)),
                WindowEmbedding(500, 2000, floatArrayOf(0.99f, 0.01f)),
                WindowEmbedding(1000, 2500, floatArrayOf(0.98f, 0.02f)),
            ),
            requestedClusters = 4,
        )

        assertEquals(listOf("speaker_1"), result.map { it.speaker }.distinct())
    }

    @Test
    fun `collapses unstable rapid cluster switching`() {
        val result = WindowedSpeakerClustering.cluster(
            (0 until 20).map { index ->
                WindowEmbedding(index * 500L, index * 500L + 1500L,
                    if (index % 2 == 0) floatArrayOf(1f, 0f) else floatArrayOf(0f, 1f))
            },
            requestedClusters = 4,
            singleSpeakerMaxDistance = 0f,
            temporalGuardMinDurationMs = 5_000L,
        )

        assertEquals(listOf("speaker_1"), result.map { it.speaker }.distinct())
    }
}
