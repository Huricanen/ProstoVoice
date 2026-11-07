package com.prostovoice.domain

import kotlin.math.roundToInt

data class TranscriptSegment(
    val speaker: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

data class Transcript(
    val sourceName: String,
    val durationMs: Long,
    val language: String? = null,
    val segments: List<TranscriptSegment> = emptyList(),
    val processingTimeMs: Long = 0L,
)

data class AsrSegment(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

data class SpeakerSegment(
    val speaker: String,
    val startMs: Long,
    val endMs: Long,
)

object TranscriptAligner {
    fun normalizeSpeakerLabels(segments: List<SpeakerSegment>): List<SpeakerSegment> {
        val labels = linkedMapOf<String, String>()
        return segments
            .sortedWith(compareBy<SpeakerSegment> { it.startMs }.thenBy { it.endMs })
            .map { segment ->
                val normalized = labels.getOrPut(segment.speaker) { "speaker_${labels.size + 1}" }
                segment.copy(speaker = normalized)
            }
    }

    fun align(
        asrSegments: List<AsrSegment>,
        speakerSegments: List<SpeakerSegment>,
        durationMs: Long,
        shortSwitchThresholdMs: Long = 1_200L,
    ): List<TranscriptSegment> {
        val duration = durationMs.coerceAtLeast(0L)
        val speakers = speakerSegments.mapNotNull { segment ->
            val start = segment.startMs.coerceIn(0L, duration)
            val end = segment.endMs.coerceIn(0L, duration)
            if (end > start && segment.speaker.isNotBlank()) {
                segment.copy(startMs = start, endMs = end)
            } else {
                null
            }
        }

        val aligned = asrSegments.flatMapIndexed { index, segment ->
            val start = segment.startMs.coerceIn(0L, duration)
            val end = segment.endMs.coerceIn(0L, duration)
            if (end <= start || segment.text.isBlank()) return@flatMapIndexed emptyList()
            val boundaries = speakers.asSequence()
                .map { it.startMs }
                .filter { it > start && it < end }
                .distinct()
                .sorted()
                .toList()
            val wordCount = segment.text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
            val usableBoundaries = if (wordCount >= boundaries.size + 1) boundaries else emptyList()
            val intervals = listOf(start) + usableBoundaries + listOf(end)
            val pieces = splitText(segment.text, intervals.size - 1)
            intervals.zipWithNext().mapIndexedNotNull { pieceIndex, (pieceStart, pieceEnd) ->
                if (pieceEnd <= pieceStart) return@mapIndexedNotNull null
                val text = pieces.getOrNull(pieceIndex)?.trim().orEmpty()
                if (text.isBlank()) return@mapIndexedNotNull null
                val speaker = selectSpeaker(
                    speakers = speakers,
                    start = pieceStart,
                    end = pieceEnd,
                    preferTail = asrSegments.size > 1 && index == asrSegments.lastIndex && pieceIndex == intervals.lastIndex - 2,
                )
                TranscriptSegment(speaker, text, pieceStart, pieceEnd)
            }
        }

        val detectedSpeakers = aligned.map { it.speaker }.distinct().size
        val smoothingThreshold = if (detectedSpeakers >= 4) {
            minOf(shortSwitchThresholdMs, 600L)
        } else {
            shortSwitchThresholdMs
        }
        val smoothed = smoothShortSwitches(aligned, smoothingThreshold.coerceAtLeast(0L))
        return mergeAdjacent(smoothed)
    }

    private fun overlap(startA: Long, endA: Long, startB: Long, endB: Long): Long =
        (minOf(endA, endB) - maxOf(startA, startB)).coerceAtLeast(0L)

    private fun selectSpeaker(
        speakers: List<SpeakerSegment>,
        start: Long,
        end: Long,
        preferTail: Boolean,
    ): String {
        val overlaps = speakers
            .map { it to overlap(start, end, it.startMs, it.endMs) }
            .filter { it.second > 0L }
        if (overlaps.isEmpty()) {
            val previous = speakers
                .filter { it.endMs <= start }
                .maxByOrNull { it.endMs }
            val next = speakers
                .filter { it.startMs >= end }
                .minByOrNull { it.startMs }
            val previousGap = previous?.let { start - it.endMs } ?: Long.MAX_VALUE
            val nextGap = next?.let { it.startMs - end } ?: Long.MAX_VALUE
            val nearest = if (nextGap <= previousGap) next else previous
            return if (nearest != null && minOf(previousGap, nextGap) <= 1_000L) {
                nearest.speaker
            } else {
                "UNKNOWN"
            }
        }
        val majority = overlaps
            .maxWithOrNull(compareBy<Pair<SpeakerSegment, Long>> { it.second }
                .thenByDescending { it.first.startMs })!!.first.speaker
        if (!preferTail || end - start > 2_500L) return majority

        // Whisper may stretch a short final segment into the preceding turn.
        // Prefer the speaker covering its trailing 500 ms when that evidence is
        // present, so a short last utterance is not assigned to the prior turn.
        val tailStart = maxOf(start, end - 500L)
        val tail = speakers
            .map { it to overlap(tailStart, end, it.startMs, it.endMs) }
            .filter { it.second >= 150L }
            .maxWithOrNull(compareBy<Pair<SpeakerSegment, Long>> { it.second }
                .thenByDescending { it.first.endMs })
            ?.first?.speaker
        return tail ?: majority
    }

    private fun splitText(text: String, pieces: Int): List<String> {
        if (pieces <= 1) return listOf(text.trim())
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.size <= 1) return listOf(text.trim())
        return buildList {
            var from = 0
            repeat(pieces) { index ->
                val remainingPieces = pieces - index
                val remainingWords = words.size - from
                val count = if (remainingPieces == 1) remainingWords
                else maxOf(1, (remainingWords.toDouble() / remainingPieces).roundToInt())
                val to = minOf(words.size, from + count)
                add(words.subList(from, to).joinToString(" "))
                from = to
            }
        }
    }

    private fun smoothShortSwitches(
        segments: List<TranscriptSegment>,
        thresholdMs: Long,
    ): List<TranscriptSegment> {
        if (segments.size < 3 || thresholdMs == 0L) return segments
        val result = segments.toMutableList()
        for (index in 1 until result.lastIndex) {
            val previous = result[index - 1]
            val current = result[index]
            val next = result[index + 1]
            if (current.endMs - current.startMs <= thresholdMs &&
                previous.speaker == next.speaker && current.speaker != previous.speaker
            ) {
                result[index] = current.copy(speaker = previous.speaker)
            }
        }
        return result
    }

    private fun mergeAdjacent(segments: List<TranscriptSegment>): List<TranscriptSegment> {
        val result = mutableListOf<TranscriptSegment>()
        for (segment in segments) {
            val previous = result.lastOrNull()
            if (previous != null && previous.speaker == segment.speaker) {
                result[result.lastIndex] = previous.copy(
                    text = removeNearbyRepeatedPhrase(listOf(previous.text, segment.text).joinToString(" ")),
                    endMs = maxOf(previous.endMs, segment.endMs),
                )
            } else {
                result += segment
            }
        }
        return result
    }

    private fun removeNearbyRepeatedPhrase(text: String): String {
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        for (phraseSize in 5 downTo 2) {
            for (first in 0..words.size - phraseSize) {
                val secondStart = first + phraseSize + 1
                val secondEnd = minOf(words.size - phraseSize, first + phraseSize + 4)
                for (second in secondStart..secondEnd) {
                    val same = words.subList(first, first + phraseSize).indices.all { offset ->
                        words[first + offset].equals(words[second + offset], ignoreCase = true)
                    }
                    if (same) {
                        return (words.subList(0, first + phraseSize) +
                            words.subList(second + phraseSize, words.size)).joinToString(" ")
                    }
                }
            }
        }
        return text.trim()
    }
}
