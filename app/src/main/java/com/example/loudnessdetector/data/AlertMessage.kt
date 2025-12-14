package com.example.loudnessdetector.data

data class AlertMessage(
    val type: String = "",
    val device_id: String = "",
    val device_name: String = "",
    val timestamp: Long = 0L,
    val event: String = "",
    val confidence: String = "",
    val rms: Int = 0,
    val zcr: Double = 0.0,
    val alarm_triggered: Boolean = false
)

data class StatusMessage(
    val type: String = "",
    val device_id: String = "",
    val device_name: String = "",
    val timestamp: Long = 0L,
    val status: String = ""
)

data class CalibrationProgress(
    val type: String = "",
    val device_id: String = "",
    val timestamp: Long = 0L,
    val progress: Progress = Progress()
) {
    data class Progress(
        val current: Int = 0,
        val total: Int = 0,
        val percentage: Int = 0,
        val message: String = ""
    )
}

data class CalibrationComplete(
    val type: String = "",
    val device_id: String = "",
    val timestamp: Long = 0L,
    val calibration_complete: Boolean = false,
    val new_thresholds: Map<String, Any>? = null,
    val error: String? = null
)

data class DeviceInfo(
    val type: String = "",
    val device_id: String = "",
    val device_name: String = "",
    val timestamp: Long = 0L,
    val status: String = "",
    val current_thresholds: Map<String, Any> = emptyMap(),
    val location: Map<String, Any> = emptyMap()
)
