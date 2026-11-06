package com.prostovoice.storage

import android.content.Context
import com.prostovoice.domain.Transcript
import com.prostovoice.domain.TranscriptSegment
import org.json.JSONArray
import org.json.JSONObject

/** Stores the latest completed transcript in app-private storage for crash/restart recovery. */
class TranscriptStore(context: Context) {
    private val appContext = context.applicationContext
    private val file = appContext.getFileStreamPath(FILE_NAME)

    fun save(transcript: Transcript) {
        val temporary = appContext.getFileStreamPath("$FILE_NAME.tmp")
        temporary.writeText(encode(transcript), Charsets.UTF_8)
        if (!temporary.renameTo(file)) {
            temporary.delete()
            error("Не удалось сохранить последний результат")
        }
    }

    fun load(): Transcript? = runCatching {
        if (!file.isFile) return null
        decode(file.readText(Charsets.UTF_8))
    }.getOrNull()

    fun clear() {
        file.delete()
    }

    private fun encode(transcript: Transcript): String = JSONObject().apply {
        put("source_name", transcript.sourceName)
        put("duration_ms", transcript.durationMs)
        transcript.language?.let { put("language", it) }
        put("transcript", JSONArray().apply {
            transcript.segments.forEach { segment ->
                put(JSONObject().apply {
                    put("speaker_id", segment.speaker)
                    put("start_ms", segment.startMs)
                    put("end_ms", segment.endMs)
                    put("text", segment.text)
                })
            }
        })
    }.toString()

    private fun decode(value: String): Transcript {
        val root = JSONObject(value)
        val entries = root.optJSONArray("transcript") ?: JSONArray()
        val segments = buildList {
            for (index in 0 until entries.length()) {
                val item = entries.getJSONObject(index)
                add(TranscriptSegment(
                    speaker = item.optString("speaker_id", "UNKNOWN"),
                    text = item.optString("text"),
                    startMs = item.optLong("start_ms"),
                    endMs = item.optLong("end_ms"),
                ))
            }
        }
        return Transcript(
            sourceName = root.optString("source_name", "audio"),
            durationMs = root.optLong("duration_ms"),
            language = root.optString("language").takeIf { it.isNotBlank() },
            segments = segments,
        )
    }

    private companion object {
        const val FILE_NAME = "last_transcript.json"
    }
}
