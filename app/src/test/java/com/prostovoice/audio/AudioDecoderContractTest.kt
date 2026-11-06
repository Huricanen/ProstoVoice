package com.prostovoice.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AudioDecoderContractTest {
    @Test
    fun chunkReportsSampleBoundaries() {
        val chunk = PcmAudioChunk(floatArrayOf(-1f, 0f, 1f), startSample = 16_000)

        assertEquals(16_003L, chunk.endSample)
    }

    @Test
    fun chunkRejectsOutOfRangeSamples() {
        assertThrows(IllegalArgumentException::class.java) {
            PcmAudioChunk(floatArrayOf(1.01f), startSample = 0)
        }
    }
}
