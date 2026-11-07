package com.prostovoice.storage

import android.content.Context
import com.prostovoice.domain.Transcript
import com.prostovoice.domain.TranscriptSegment
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(val id: String, val uri: String, val transcript: Transcript)

class HistoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val file get() = appContext.getFileStreamPath(FILE_NAME)

    fun load(): List<HistoryItem> = runCatching {
        if (!file.isFile) return emptyList()
        val entries = JSONArray(file.readText(Charsets.UTF_8))
        buildList {
            for (index in 0 until entries.length()) {
                val item = entries.getJSONObject(index)
                add(HistoryItem(item.optString("id"), item.optString("uri"), decode(item.getJSONObject("transcript"))))
            }
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun add(uri: String, transcript: Transcript) {
        val updated = (load().filterNot { it.uri == uri } + HistoryItem(
            id = "${System.currentTimeMillis()}_${uri.hashCode()}", uri = uri, transcript = transcript,
        )).takeLast(MAX_ITEMS)
        val temporary = appContext.getFileStreamPath("$FILE_NAME.tmp")
        temporary.writeText(JSONArray(updated.map { item ->
            JSONObject().apply {
                put("id", item.id)
                put("uri", item.uri)
                put("transcript", encode(item.transcript))
            }
        }).toString(), Charsets.UTF_8)
        if (!temporary.renameTo(file)) error("Не удалось сохранить библиотеку обработок")
    }

    private fun encode(transcript: Transcript) = JSONObject().apply {
        put("source_name", transcript.sourceName); put("duration_ms", transcript.durationMs)
        put("processing_time_ms", transcript.processingTimeMs)
        transcript.language?.let { put("language", it) }
        put("transcript", JSONArray(transcript.segments.map { segment -> JSONObject().apply {
            put("speaker_id", segment.speaker); put("start_ms", segment.startMs)
            put("end_ms", segment.endMs); put("text", segment.text)
        } }))
    }

    private fun decode(value: JSONObject): Transcript {
        val entries = value.optJSONArray("transcript") ?: JSONArray()
        val segments = buildList {
            for (index in 0 until entries.length()) {
                val item = entries.getJSONObject(index)
                add(TranscriptSegment(item.optString("speaker_id", "UNKNOWN"), item.optString("text"), item.optLong("start_ms"), item.optLong("end_ms")))
            }
        }
        return Transcript(value.optString("source_name", "audio"), value.optLong("duration_ms"), value.optString("language").takeIf { it.isNotBlank() }, segments, value.optLong("processing_time_ms"))
    }

    private companion object { const val FILE_NAME = "transcription_history.json"; const val MAX_ITEMS = 50 }
}
