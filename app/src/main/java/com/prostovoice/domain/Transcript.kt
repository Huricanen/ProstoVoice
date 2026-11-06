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
