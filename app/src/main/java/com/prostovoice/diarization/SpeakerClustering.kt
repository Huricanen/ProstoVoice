package com.prostovoice.diarization

import kotlin.math.sqrt

data class SpeakerEmbedding(
    val startMs: Long,
    val endMs: Long,
    val values: FloatArray,
)

data class ClusteredSpeakerSegment(
    val startMs: Long,
    val endMs: Long,
    val speaker: String,
)

/** Deterministic agglomerative clustering with cosine distance. */
object SpeakerClustering {
    fun cluster(
        embeddings: List<SpeakerEmbedding>,
        distanceThreshold: Float = 0.35f,
    ): List<ClusteredSpeakerSegment> {
        if (embeddings.isEmpty()) return emptyList()
        require(distanceThreshold in 0f..2f)
        val dimensions = embeddings.first().values.size
        require(dimensions > 0 && embeddings.all { it.values.size == dimensions })
        val clusters = embeddings.map { mutableListOf(it) }.toMutableList()
        while (clusters.size > 1) {
            var bestDistance = Float.POSITIVE_INFINITY
            var bestA = -1
            var bestB = -1
            for (a in 0 until clusters.lastIndex) {
                for (b in a + 1 until clusters.size) {
                    val distance = averageDistance(clusters[a], clusters[b])
                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestA = a
                        bestB = b
                    }
                }
            }
            if (bestA < 0 || bestDistance > distanceThreshold) break
            clusters[bestA].addAll(clusters.removeAt(bestB))
        }
        return clusters.sortedBy { it.minOf(SpeakerEmbedding::startMs) }
            .mapIndexed { index, cluster ->
                ClusteredSpeakerSegment(
                    startMs = cluster.minOf(SpeakerEmbedding::startMs),
                    endMs = cluster.maxOf(SpeakerEmbedding::endMs),
                    speaker = "speaker_${index + 1}",
                )
            }
    }

    private fun averageDistance(
        left: List<SpeakerEmbedding>,
        right: List<SpeakerEmbedding>,
    ): Float = left.sumOf { a -> right.sumOf { b -> cosineDistance(a.values, b.values).toDouble() } }
        .toFloat() / (left.size * right.size)

    private fun cosineDistance(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (index in a.indices) {
            dot += a[index] * b[index]
            normA += a[index] * a[index]
            normB += b[index] * b[index]
        }
        if (normA == 0.0 || normB == 0.0) return 1f
        return (1.0 - dot / (sqrt(normA) * sqrt(normB))).toFloat()
    }
}
