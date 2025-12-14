package com.example.loudnessdetector.firebase

import android.util.Log
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseManager(private val deviceId: String) {
    
    private val database = FirebaseDatabase.getInstance()
    private val TAG = "FirebaseManager"
    
    /**
     * Get real-time updates for device status (RMS, ZCR, etc.)
     */
    fun getDeviceStatus(): Flow<DeviceStatus?> = callbackFlow {
        val ref = database.getReference("devices/$deviceId/status")
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val data = snapshot.value as? Map<*, *>
                    if (data != null) {
                        val info = data["info"] as? Map<*, *>
                        
                        // Parse progress data from Firebase
                        val progressData = data["progress"] as? Map<*, *>
                        val progress = if (progressData != null) {
                            CalibrationInfo(
                                current = (progressData["current"] as? Long)?.toInt() ?: 0,
                                total = (progressData["total"] as? Long)?.toInt() ?: 0,
                                percentage = (progressData["percentage"] as? Long)?.toInt() ?: 0,
                                message = (progressData["message"] as? String) ?: ""
                            )
                        } else null
                        
                        val status = DeviceStatus(
                            timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis(),
                            lastRms = (info?.get("last_rms") as? Long)?.toInt() ?: 0,
                            lastZcr = (info?.get("last_zcr") as? Double) ?: 0.0,
                            alarmState = (info?.get("alarm_state") as? String) ?: "IDLE",
                            status = (data["status"] as? String) ?: "online",
                            type = (data["type"] as? String) ?: "status",
                            message = (data["message"] as? String),
                            progress = progress
                        )
                        trySend(status)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing device status", e)
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error listening to device status", error.toException())
                close(error.toException())
            }
        }
        
        ref.addValueEventListener(listener)
        
        awaitClose {
            ref.removeEventListener(listener)
        }
    }
    
    /**
     * Get last 10 messages (alerts and calibration results)
     */
    suspend fun getRecentMessages(limit: Int = 10): List<DeviceMessage> {
        return try {
            val snapshot = database.getReference("devices/$deviceId/messages")
                .orderByChild("timestamp")
                .limitToLast(limit)
                .get()
                .await()
            
            val messages = mutableListOf<DeviceMessage>()
            snapshot.children.forEach { child ->
                val data = child.value as? Map<*, *>
                if (data != null) {
                    messages.add(DeviceMessage(
                        id = child.key ?: "",
                        timestamp = (data["timestamp"] as? Long) ?: 0L,
                        type = (data["type"] as? String) ?: "alert",
                        label = (data["label"] as? String),
                        rms = (data["rms"] as? Long)?.toInt(),
                        zcr = (data["zcr"] as? Double),
                        deviceId = (data["device_id"] as? String) ?: deviceId
                    ))
                }
            }
            
            messages.sortedByDescending { it.timestamp }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching messages", e)
            emptyList()
        }
    }
    
    /**
     * Listen to real-time message updates (for alerts)
     */
    fun listenToMessages(): Flow<DeviceMessage?> = callbackFlow {
        val ref = database.getReference("devices/$deviceId/messages")
        
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val data = snapshot.value as? Map<*, *>
                    if (data != null) {
                        val message = DeviceMessage(
                            id = snapshot.key ?: "",
                            timestamp = (data["timestamp"] as? Long) ?: 0L,
                            type = (data["type"] as? String) ?: "alert",
                            label = (data["label"] as? String),
                            rms = (data["rms"] as? Long)?.toInt(),
                            zcr = (data["zcr"] as? Double),
                            deviceId = (data["device_id"] as? String) ?: deviceId
                        )
                        trySend(message)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message", e)
                }
            }
            
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Messages listener cancelled", error.toException())
                close(error.toException())
            }
        }
        
        ref.addChildEventListener(listener)
        
        awaitClose {
            ref.removeEventListener(listener)
        }
    }
    
    /**
     * Send calibration command to device via MQTT
     * Uses Firebase to store command, Python bridge forwards to MQTT
     */
    suspend fun triggerCalibration(): Boolean {
        return try {
            // Store command in Firebase, Python bridge will NOT forward it
            // So we need to publish directly to MQTT topic or use a different approach
            // For now, store in Firebase and let Python bridge see it
            val commandRef = database.getReference("library/$deviceId/command")
            commandRef.setValue(mapOf(
                "action" to "calibrate",
                "timestamp" to System.currentTimeMillis()
            )).await()
            Log.i(TAG, "Calibration command sent to library/$deviceId/command")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send calibration command", e)
            false
        }
    }
    
    /**
     * Update device info (floor, zone, description)
     */
    suspend fun updateDeviceInfo(floor: String, zone: String, description: String): Boolean {
        return try {
            val updates = mapOf(
                "devices/$deviceId/info/floor" to floor,
                "devices/$deviceId/info/zone" to zone,
                "devices/$deviceId/info/description" to description,
                "devices/$deviceId/info/updated_at" to System.currentTimeMillis()
            )
            database.reference.updateChildren(updates).await()
            Log.i(TAG, "Device info updated for $deviceId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update device info", e)
            false
        }
    }
    
    /**
     * Get device info (floor, zone, description)
     */
    suspend fun getDeviceInfo(): DeviceInfo? {
        return try {
            val snapshot = database.getReference("devices/$deviceId/info").get().await()
            val data = snapshot.value as? Map<*, *>
            if (data != null) {
                DeviceInfo(
                    floor = (data["floor"] as? String) ?: "",
                    zone = (data["zone"] as? String) ?: "",
                    description = (data["description"] as? String) ?: ""
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching device info", e)
            null
        }
    }
}

/**
 * Data models for Firebase
 */
data class DeviceStatus(
    val timestamp: Long = 0,
    val lastRms: Int = 0,
    val lastZcr: Double = 0.0,
    val alarmState: String = "IDLE",
    val status: String = "online",
    val type: String = "status",
    val message: String? = null,  // For calibration progress messages
    val progress: CalibrationInfo? = null
)

data class DeviceMessage(
    val id: String = "",
    val timestamp: Long = 0,
    val type: String = "alert",
    val label: String? = null,
    val rms: Int? = null,
    val zcr: Double? = null,
    val deviceId: String = ""
)

data class CalibrationInfo(
    val current: Int = 0,
    val message: String = "",
    val percentage: Int = 0,
    val total: Int = 0,
)

data class DeviceInfo(
    val floor: String = "",
    val zone: String = "",
    val description: String = ""
)
