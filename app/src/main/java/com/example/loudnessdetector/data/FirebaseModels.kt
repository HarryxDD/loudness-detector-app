package com.example.loudnessdetector.data

/**
 * Detection data from Firebase
 */
data class Detection(
    val timestamp: Long = 0,
    val label: String = "",
    val rms: Int = 0,
    val zcr: Double = 0.0,
    val confidence: String = "",
    val alarm: Boolean = false,
    val device_id: String = ""
)

/**
 * Summary statistics from Firebase
 */
data class DeviceSummary(
    val timestamp: Long = 0,
    val count: Int = 0,
    val avg_rms: Double = 0.0,
    val avg_zcr: Double = 0.0,
    val speech_count: Int = 0,
    val noise_count: Int = 0,
    val silent_count: Int = 0,
    val alarm_count: Int = 0
)
