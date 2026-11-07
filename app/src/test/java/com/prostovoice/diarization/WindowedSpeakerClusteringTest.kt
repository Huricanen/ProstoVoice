package com.prostovoice.diarization

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowedSpeakerClusteringTest {
    @Test
    fun `estimates one speaker for similar embeddings`() {
        val windows = (0 until 6).map { index ->
            WindowEmbedding(index * 500L, index * 500L + 1500L, floatArrayOf(1f, index * 0.001f))
        }
        assertEquals(1, WindowedSpeakerClustering.estimateClusterCount(windows))
    }

    @Test
    fun `estimates two speakers for separated embeddings`() {
        val windows = (0 until 8).map { index ->
            val vector = if (index < 4) floatArrayOf(1f, 0f) else floatArrayOf(0f, 1f)
            WindowEmbedding(index * 500L, index * 500L + 1500L, vector)
        }
        assertEquals(2, WindowedSpeakerClustering.estimateClusterCount(windows))
    }

    @Test
    fun `rejects a tiny fourth cluster`() {
        val windows = (0 until 13).map { index ->
            val vector = when {
                index < 4 -> floatArrayOf(1f, 0f, 0f, 0f)
                index < 8 -> floatArrayOf(0f, 1f, 0f, 0f)
                index < 12 -> floatArrayOf(0f, 0f, 1f, 0f)
                else -> floatArrayOf(0f, 0f, 0f, 1f)
            }
            WindowEmbedding(index * 500L, index * 500L + 1500L, vector)
        }
        assertEquals(3, WindowedSpeakerClustering.estimateClusterCount(windows))
    }

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
    fun `estimator collapses similar windows into one speaker`() {
        val result = WindowedSpeakerClustering.cluster(
            listOf(
                WindowEmbedding(0, 1500, floatArrayOf(1f, 0f)),
                WindowEmbedding(500, 2000, floatArrayOf(0.99f, 0.01f)),
                WindowEmbedding(1000, 2500, floatArrayOf(0.98f, 0.02f)),
            ),
            requestedClusters = WindowedSpeakerClustering.estimateClusterCount(
                listOf(
                    WindowEmbedding(0, 1500, floatArrayOf(1f, 0f)),
                    WindowEmbedding(500, 2000, floatArrayOf(0.99f, 0.01f)),
                    WindowEmbedding(1000, 2500, floatArrayOf(0.98f, 0.02f)),
                ),
            ),
        )

        assertEquals(listOf("speaker_1"), result.map { it.speaker }.distinct())
    }

    @Test
    fun `preserves rapid switching when embeddings are separated`() {
        val result = WindowedSpeakerClustering.cluster(
            (0 until 20).map { index ->
                WindowEmbedding(index * 500L, index * 500L + 1500L,
                    if (index % 2 == 0) floatArrayOf(1f, 0f) else floatArrayOf(0f, 1f))
            },
            requestedClusters = 2,
        )

        assertEquals(listOf("speaker_1", "speaker_2"), result.map { it.speaker }.distinct())
    }
}
