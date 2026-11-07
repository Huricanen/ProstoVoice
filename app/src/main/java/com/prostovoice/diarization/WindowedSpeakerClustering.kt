package com.prostovoice.diarization

import com.prostovoice.domain.SpeakerSegment

import kotlin.math.sqrt

data class WindowEmbedding(val startMs: Long, val endMs: Long, val values: FloatArray)

/** Cosine k-means for short overlapping speaker windows. */
object WindowedSpeakerClustering {
    fun cluster(
        windows: List<WindowEmbedding>,
        requestedClusters: Int,
        singleSpeakerMaxDistance: Float = 0.20f,
    ): List<SpeakerSegment> {
        if (windows.isEmpty()) return emptyList()
        val dimensions = windows.first().values.size
        require(dimensions > 0 && windows.all { it.values.size == dimensions })
        val vectors = windows.map { normalize(it.values) }
        val global = normalize(FloatArray(dimensions).also { mean ->
            vectors.forEach { vector -> vector.forEachIndexed { index, value -> mean[index] += value } }
        })
        val maxGlobalDistance = vectors.maxOf { 1f - cosine(it, global) }
        val count = if (maxGlobalDistance <= singleSpeakerMaxDistance) {
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
}
