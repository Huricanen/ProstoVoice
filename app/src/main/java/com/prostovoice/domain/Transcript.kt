package com.prostovoice.domain

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

        val aligned = asrSegments.mapIndexedNotNull { index, segment ->
            val start = segment.startMs.coerceIn(0L, duration)
            val end = segment.endMs.coerceIn(0L, duration)
            if (end <= start || segment.text.isBlank()) return@mapIndexedNotNull null
            val speaker = selectSpeaker(
                speakers = speakers,
                start = start,
                end = end,
                preferTail = asrSegments.size > 1 && index == asrSegments.lastIndex,
            )
            TranscriptSegment(speaker, segment.text.trim(), start, end)
        }

        val smoothed = smoothShortSwitches(aligned, shortSwitchThresholdMs.coerceAtLeast(0L))
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
        if (overlaps.isEmpty()) return "UNKNOWN"
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
                    text = listOf(previous.text, segment.text).joinToString(" "),
                    endMs = maxOf(previous.endMs, segment.endMs),
                )
            } else {
                result += segment
            }
        }
        return result
    }
}
