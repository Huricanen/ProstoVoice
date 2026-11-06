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
    append("{\n")
    append("  \"sourceName\": \"").append(escape(sourceName)).append("\",\n")
    append("  \"durationMs\": ").append(durationMs).append(",\n")
    append("  \"language\": ").append(language?.let { "\"${escape(it)}\"" } ?: "null").append(",\n")
    append("  \"segments\": [")
    if (segments.isNotEmpty()) append("\n")
    segments.forEachIndexed { index, segment ->
        append("    {\"speaker\": \"").append(escape(segment.speaker))
        append("\", \"text\": \"").append(escape(segment.text))
        append("\", \"startMs\": ").append(segment.startMs)
        append(", \"endMs\": ").append(segment.endMs).append("}")
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
