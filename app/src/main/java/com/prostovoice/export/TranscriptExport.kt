package com.prostovoice.export

import com.prostovoice.domain.Transcript

fun Transcript.toTxt(): String = buildString {
    segments.forEach { segment ->
        append("[")
        append(formatTime(segment.startMs))
        append(" - ")
        append(formatTime(segment.endMs))
        append("] ")
        append(segment.speaker)
        append(":\n")
        append(segment.text)
        append("\n\n")
    }
}

fun Transcript.toJson(): String = buildString {
    append("{\n  \"transcript\": [")
    if (segments.isNotEmpty()) append("\n")
    segments.forEachIndexed { index, segment ->
        append("    {\"speaker_id\": \"").append(escape(segment.speaker))
        append("\", \"start_ms\": ").append(segment.startMs)
        append(", \"end_ms\": ").append(segment.endMs)
        append(", \"text\": \"").append(escape(segment.text)).append("\"}")
        if (index != segments.lastIndex) append(",")
        append("\n")
    }
    append("  ]\n")
    append("}\n")
}

private fun escape(value: String): String = buildString {
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '\"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000
    return "%02d:%02d:%02d.%03d".format(
        totalSeconds / 3600,
        (totalSeconds % 3600) / 60,
        totalSeconds % 60,
        milliseconds.coerceAtLeast(0L) % 1000,
    )
}
