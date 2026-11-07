package com.prostovoice.diarization

/** Runtime parameters that can be swept against a fixed diarization reference. */
data class DiarizationConfig(
    val numClusters: Int = 0,
    val threshold: Float = 0.75f,
    val windowShiftRatio: Float = 0.1f,
    val minDurationOnSeconds: Float = 0.4f,
    val minDurationOffSeconds: Float = 0.3f,
) {
    init {
        require(numClusters >= 0) { "numClusters must be non-negative" }
        require(threshold in 0f..2f) { "threshold must be between 0 and 2" }
        require(windowShiftRatio > 0f && windowShiftRatio <= 1f) {
            "windowShiftRatio must be greater than 0 and at most 1"
        }
        require(minDurationOnSeconds >= 0f) { "minDurationOnSeconds must be non-negative" }
        require(minDurationOffSeconds >= 0f) { "minDurationOffSeconds must be non-negative" }
    }
}
