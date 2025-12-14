package com.example.loudnessdetector.data

data class Device(
    val deviceId: String,
    var deviceName: String,
    var location: DeviceLocation,
    var broker: String = "test.mosquitto.org",
    var port: Int = 1883,
    var isOnline: Boolean = false,
    var lastSeen: Long = 0L,
    var lastRms: Int = 0,
    var lastZcr: Double = 0.0
)

data class DeviceLocation(
    var floor: String = "Floor 1",
    var zone: String = "A",
    var description: String = ""
)

data class DeviceAlert(
    val deviceId: String,
    val message: String,
    val type: String,
    val timestamp: Long,
    val rms: Int,
    val zcr: Double,
    val confidence: String
)
