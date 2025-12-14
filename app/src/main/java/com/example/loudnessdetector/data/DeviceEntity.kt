package com.example.loudnessdetector.data

data class DeviceEntity(
    val deviceId: String,
    val deviceName: String,
    val floor: String,
    val zone: String,
    val description: String,
    val broker: String,
    val port: Int,
    val isOnline: Boolean,
    val lastSeen: Long,
    val lastRms: Int,
    val lastZcr: Double
)

fun DeviceEntity.toDevice(): Device {
    return Device(
        deviceId = deviceId,
        deviceName = deviceName,
        location = DeviceLocation(floor, zone, description),
        broker = broker,
        port = port,
        isOnline = isOnline, 
        lastSeen = lastSeen,
        lastRms = lastRms,
        lastZcr = lastZcr
    )
}

fun Device.toEntity(): DeviceEntity {
    return DeviceEntity(
        deviceId = deviceId,
        deviceName = deviceName,
        floor = location.floor,
        zone = location.zone,
        description = location.description,
        broker = broker,
        port = port,
        isOnline = isOnline,
        lastSeen = lastSeen,
        lastRms = lastRms,
        lastZcr = lastZcr
    )
}
