package com.prostovoice.diarization

import com.prostovoice.domain.SpeakerSegment

import kotlin.math.sqrt

data class WindowEmbedding(val startMs: Long, val endMs: Long, val values: FloatArray)

/** Cosine k-means for short overlapping speaker windows. */
object WindowedSpeakerClustering {
    /** Selects the smallest useful speaker count from the same window embeddings. */
    fun estimateClusterCount(windows: List<WindowEmbedding>, maxClusters: Int = 4): Int {
        if (windows.isEmpty()) return 0
        val dimensions = windows.first().values.size
        require(dimensions > 0 && windows.all { it.values.size == dimensions })
        val vectors = windows.map { normalize(it.values) }
        val limit = maxClusters.coerceIn(1, windows.size)
        if (limit == 1) return 1
        val maxPairwiseDistance = vectors.indices.maxOf { left ->
            (left + 1 until vectors.size).maxOfOrNull { right ->
                1f - cosine(vectors[left], vectors[right])
            } ?: 0f
        }
        if (maxPairwiseDistance <= 0.15f) return 1
        val scores = (2..limit).associateWith { k -> silhouetteScore(vectors, k) }
        val best = scores.maxByOrNull { (k, score) -> score - (k - 1) * 0.02f }
        return if (best == null || best.value < 0.05f) 1 else best.key
    }

    fun cluster(
        windows: List<WindowEmbedding>,
        requestedClusters: Int,
        singleSpeakerMaxDistance: Float = 0.50f,
        temporalGuardMinDurationMs: Long = 45_000L,
    ): List<SpeakerSegment> {
        if (windows.isEmpty()) return emptyList()
        val dimensions = windows.first().values.size
        require(dimensions > 0 && windows.all { it.values.size == dimensions })
        val vectors = windows.map { normalize(it.values) }
        val durationMs = windows.maxOf { it.endMs } - windows.minOf { it.startMs }
        val global = normalize(FloatArray(dimensions).also { mean ->
            vectors.forEach { vector -> vector.forEachIndexed { index, value -> mean[index] += value } }
        })
        val maxGlobalDistance = vectors.maxOf { 1f - cosine(it, global) }
        val count = if (durationMs >= temporalGuardMinDurationMs &&
            maxGlobalDistance <= singleSpeakerMaxDistance) {
            1
        } else {
            requestedClusters.coerceIn(1, windows.size)
        }
        val centroids = MutableList(count) { vectors[it * vectors.size / count].copyOf() }
        val assignments = IntArray(vectors.size)
        repeat(20) {
            vectors.forEachIndexed { index, vector ->
                assignments[index] = centroids.indices.maxBy { cosine(vector, centroids[it]) }
            }
            centroids.indices.forEach { cluster ->
                val members = vectors.indices.filter { assignments[it] == cluster }
                if (members.isNotEmpty()) {
                    val mean = FloatArray(dimensions)
                    members.forEach { member ->
                        vectors[member].forEachIndexed { index, value -> mean[index] += value }
                    }
                    centroids[cluster] = normalize(mean)
                }
            }
        }
        var switches = 0
        for (index in 1 until assignments.size) {
            if (assignments[index] != assignments[index - 1]) switches++
        }
        val switchRate = switches / assignments.size.coerceAtLeast(2).toFloat()
        if (count > 1 && durationMs >= temporalGuardMinDurationMs &&
            assignments.size >= 8 && switchRate > 0.12f) {
            assignments.fill(0)
        }
        val result = mutableListOf<SpeakerSegment>()
        windows.forEachIndexed { index, window ->
            val speaker = "speaker_${assignments[index] + 1}"
            val previous = result.lastOrNull()
            if (previous != null && previous.speaker == speaker && previous.endMs >= window.startMs - 600L) {
                result[result.lastIndex] = previous.copy(endMs = maxOf(previous.endMs, window.endMs))
            } else {
                result += SpeakerSegment(speaker, window.startMs, window.endMs)
            }
        }
        return result
    }

    private fun normalize(values: FloatArray): FloatArray {
        val norm = sqrt(values.sumOf { (it * it).toDouble() }).toFloat()
        return if (norm == 0f) values.copyOf() else FloatArray(values.size) { values[it] / norm }
    }

    private fun cosine(left: FloatArray, right: FloatArray): Float =
        left.indices.sumOf { (left[it] * right[it]).toDouble() }.toFloat()

    private fun silhouetteScore(vectors: List<FloatArray>, count: Int): Float {
        val assignments = kMeans(vectors, count).first
        if (assignments.toSet().size < 2) return 0f
        var total = 0f
        vectors.indices.forEach { index ->
            val own = assignments[index]
            val same = vectors.indices.filter { assignments[it] == own && it != index }
            val within = if (same.isEmpty()) 0f else same.map { 1f - cosine(vectors[index], vectors[it]) }.average().toFloat()
            val nearestOther = assignments.toSet().filter { it != own }.minOf { other ->
                val members = vectors.indices.filter { assignments[it] == other }
                members.map { 1f - cosine(vectors[index], vectors[it]) }.average().toFloat()
            }
            total += (nearestOther - within) / maxOf(within, nearestOther, 1e-6f)
        }
        return total / vectors.size
    }

    private fun kMeans(vectors: List<FloatArray>, count: Int): Pair<IntArray, List<FloatArray>> {
        val dimensions = vectors.first().size
        val centroids = MutableList(count) { vectors[it * vectors.size / count].copyOf() }
        val assignments = IntArray(vectors.size)
        repeat(20) {
            vectors.forEachIndexed { index, vector ->
                assignments[index] = centroids.indices.maxBy { cosine(vector, centroids[it]) }
            }
            centroids.indices.forEach { cluster ->
                val members = vectors.indices.filter { assignments[it] == cluster }
                if (members.isNotEmpty()) {
                    val mean = FloatArray(dimensions)
                    members.forEach { member ->
                        vectors[member].forEachIndexed { index, value -> mean[index] += value }
                    }
                    centroids[cluster] = normalize(mean)
                }
            }
        }
        return assignments.copyOf() to centroids
    }
}
