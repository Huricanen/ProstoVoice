package com.prostovoice.diarization

import org.junit.Assert.assertEquals
import org.junit.Test

class DiarizationConfigTest {
    @Test
    fun `defaults preserve current runtime profile`() {
        val config = DiarizationConfig()
        assertEquals(0, config.numClusters)
        assertEquals(0.75f, config.threshold)
        assertEquals(0.4f, config.minDurationOnSeconds)
        assertEquals(0.3f, config.minDurationOffSeconds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative cluster count`() {
        DiarizationConfig(numClusters = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects threshold outside cosine distance range`() {
        DiarizationConfig(threshold = 2.1f)
    }
}
