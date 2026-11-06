package com.prostovoice.export

import com.prostovoice.domain.Transcript
import com.prostovoice.domain.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptExportTest {
    @Test
    fun `json uses transcript array and required fields`() {
        val transcript = Transcript(
            sourceName = "ignored-name",
            durationMs = 5000,
            language = "ru",
            segments = listOf(TranscriptSegment("SPEAKER_00", "Привет", 120, 3400)),
        )

        assertEquals(
            """{
  "transcript": [
    {"speaker_id": "SPEAKER_00", "start_ms": 120, "end_ms": 3400, "text": "Привет"}
  ]
}
""",
            transcript.toJson(),
        )
    }

    @Test
    fun `json escapes text characters`() {
        val transcript = Transcript(
            sourceName = "ignored",
            durationMs = 0,
            segments = listOf(TranscriptSegment("S1", "Он сказал \"да\"\\нет\nследующая", 0, 1)),
        )

        val json = transcript.toJson()
        assertTrue(json.contains("Он сказал \\\"да\\\"\\\\нет\\nследующая"))
        assertTrue(json.startsWith("{\n  \"transcript\": ["))
        assertTrue(json.trimEnd().endsWith("}"))
    }

    @Test
    fun `txt format remains speaker and timestamp based`() {
        val transcript = Transcript(
            sourceName = "sample",
            durationMs = 1000,
            segments = listOf(TranscriptSegment("S1", "text", 1234, 5678)),
        )

        assertEquals("[00:00:01.234 - 00:00:05.678] S1:\ntext\n\n", transcript.toTxt())
    }
}
