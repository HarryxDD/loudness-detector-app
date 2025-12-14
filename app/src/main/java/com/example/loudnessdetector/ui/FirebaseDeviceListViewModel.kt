package com.example.loudnessdetector.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.loudnessdetector.data.Device
import com.example.loudnessdetector.data.DeviceAlert
import com.example.loudnessdetector.data.DeviceLocation
import com.example.loudnessdetector.data.DeviceRepository
import com.google.firebase.database.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel that uses Firebase Realtime Database instead of MQTT
 * Listens to device status updates from Firebase (populated by Python bridge)
 */
class FirebaseDeviceListViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = DeviceRepository(application)
    private val database = FirebaseDatabase.getInstance()
    private val TAG = "FirebaseDeviceListVM"
    
    // Alert cooldown: minimum 5 seconds between alerts from same device
    private val ALERT_COOLDOWN_MS = 5_000L
    private val lastAlertTime = mutableMapOf<String, Long>()
    
    // Track active listeners
    private val deviceListeners = mutableMapOf<String, ValueEventListener>()
    
    val devices: StateFlow<List<Device>> = repository.devices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Firebase is always "connected" - no broker connection needed
    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _recentAlerts = MutableStateFlow<List<DeviceAlert>>(emptyList())
    val recentAlerts: StateFlow<List<DeviceAlert>> = _recentAlerts.asStateFlow()
    
    init {
        setupFirebaseListeners()
    }
    
    private fun setupFirebaseListeners() {
        viewModelScope.launch {
            // Listen to changes in devices list
            devices.collect { deviceList ->
                // Set up listeners for each device
                deviceList.forEach { device ->
                    if (!deviceListeners.containsKey(device.deviceId)) {
                        listenToDevice(device.deviceId)
                    }
                }
                
                // Remove listeners for deleted devices
                val currentDeviceIds = deviceList.map { it.deviceId }.toSet()
                val toRemove = deviceListeners.keys.filter { it !in currentDeviceIds }
                toRemove.forEach { deviceId ->
                    stopListeningToDevice(deviceId)
                }
            }
        }
    }
    
    private fun listenToDevice(deviceId: String) {
        // Listen to device status updates from Firebase
        val statusRef = database.getReference("devices/$deviceId/status")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val data = snapshot.value as? Map<*, *> ?: return
                    val timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis()
                    val status = (data["status"] as? String) ?: "online"
                    
                    // Extract RMS and ZCR from info object
                    val info = data["info"] as? Map<*, *>
                    val rms = (info?.get("last_rms") as? Long)?.toInt() ?: 0
                    val zcr = (info?.get("last_zcr") as? Double) ?: 0.0
                    
                    val isOnline = status == "online"
                    updateDeviceStatus(deviceId, timestamp, rms, zcr, isOnline)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing status for $deviceId", e)
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase listener cancelled for $deviceId", error.toException())
            }
        }
        
        statusRef.addValueEventListener(listener)
        deviceListeners[deviceId] = listener
        
        // Also listen to messages for new alerts
        listenToAlerts(deviceId)
        
        // Listen to device info changes
        listenToDeviceInfo(deviceId)
    }
    
    private fun listenToDeviceInfo(deviceId: String) {
        val infoRef = database.getReference("devices/$deviceId/info")
        infoRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val info = snapshot.value as? Map<*, *> ?: return
                    val deviceName = (info["device_name"] as? String) ?: "Device ${deviceId.takeLast(3)}"
                    val floor = (info["floor"] as? String) ?: "Unknown"
                    val zone = (info["zone"] as? String) ?: "Unknown"
                    val description = (info["description"] as? String) ?: ""
                    
                    // Update device with new info
                    updateDeviceInfo(deviceId, deviceName, floor, zone, description)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing device info for $deviceId", e)
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Device info listener cancelled for $deviceId", error.toException())
            }
        })
    }
    
    private fun listenToAlerts(deviceId: String) {
        val messagesRef = database.getReference("devices/$deviceId/messages")
        messagesRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val data = snapshot.value as? Map<*, *> ?: return
                    
                    // Only show alerts, not calibration messages
                    val type = data["type"] as? String
                    if (type == "alert" || data.containsKey("label")) {
                        val timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis()
                        val rms = (data["rms"] as? Long)?.toInt() ?: 0
                        val zcr = (data["zcr"] as? Double) ?: 0.0
                        val label = data["label"] as? String ?: "UNKNOWN"
                        
                        handleAlert(deviceId, label, timestamp, rms, zcr)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing alert for $deviceId", e)
                }
            }
            
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Messages listener cancelled for $deviceId", error.toException())
            }
        })
    }
    
    private fun stopListeningToDevice(deviceId: String) {
        deviceListeners[deviceId]?.let { listener ->
            val statusRef = database.getReference("devices/$deviceId/status")
            statusRef.removeEventListener(listener)
            deviceListeners.remove(deviceId)
        }
    }
    
    private fun updateDeviceInfo(
        deviceId: String,
        deviceName: String,
        floor: String,
        zone: String,
        description: String
    ) {
        viewModelScope.launch {
            val currentDevices = devices.value.toMutableList()
            val existingDevice = currentDevices.find { it.deviceId == deviceId }
            
            if (existingDevice != null) {
                val updated = existingDevice.copy(
                    deviceName = deviceName,
                    location = DeviceLocation(floor, zone, description)
                )
                currentDevices[currentDevices.indexOf(existingDevice)] = updated
                repository.saveDevices(currentDevices)
            }
        }
    }
    
    private fun updateDeviceStatus(
        deviceId: String,
        timestamp: Long,
        rms: Int,
        zcr: Double,
        isOnline: Boolean
    ) {
        viewModelScope.launch {
            val currentDevices = devices.value.toMutableList()
            val existingDevice = currentDevices.find { it.deviceId == deviceId }
            
            if (existingDevice != null) {
                val updated = existingDevice.copy(
                    isOnline = isOnline,
                    lastSeen = timestamp,
                    lastRms = rms,
                    lastZcr = zcr
                )
                currentDevices[currentDevices.indexOf(existingDevice)] = updated
                repository.saveDevices(currentDevices)
            } else {
                // New device discovered - load info from Firebase
                loadDeviceInfoAndCreate(deviceId, timestamp, rms, zcr, isOnline, currentDevices)
            }
        }
    }
    
    private fun loadDeviceInfoAndCreate(
        deviceId: String,
        timestamp: Long,
        rms: Int,
        zcr: Double,
        isOnline: Boolean,
        currentDevices: MutableList<Device>
    ) {
        viewModelScope.launch {
            try {
                val infoSnapshot = database.getReference("devices/$deviceId/info").get().await()
                val info = infoSnapshot.value as? Map<*, *>
                
                val deviceName = (info?.get("device_name") as? String) ?: "Device ${deviceId.takeLast(3)}"
                val floor = (info?.get("floor") as? String) ?: "Unknown"
                val zone = (info?.get("zone") as? String) ?: "Unknown"
                val description = (info?.get("description") as? String) ?: ""
                
                val newDevice = Device(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    location = DeviceLocation(floor, zone, description),
                    isOnline = isOnline,
                    lastSeen = timestamp,
                    lastRms = rms,
                    lastZcr = zcr
                )
                currentDevices.add(newDevice)
                repository.saveDevices(currentDevices)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading device info for $deviceId", e)
                // Fallback to default values
                val newDevice = Device(
                    deviceId = deviceId,
                    deviceName = "Device ${deviceId.takeLast(3)}",
                    location = DeviceLocation("Unknown", "Unknown", ""),
                    isOnline = isOnline,
                    lastSeen = timestamp,
                    lastRms = rms,
                    lastZcr = zcr
                )
                currentDevices.add(newDevice)
                repository.saveDevices(currentDevices)
            }
        }
    }
    
    private fun handleAlert(
        deviceId: String,
        label: String,
        timestamp: Long,
        rms: Int,
        zcr: Double
    ) {
        // Check alert cooldown
        val currentTime = System.currentTimeMillis()
        val lastAlert = lastAlertTime[deviceId] ?: 0L
        if (currentTime - lastAlert < ALERT_COOLDOWN_MS) {
            return // Skip this alert, too soon
        }
        lastAlertTime[deviceId] = currentTime
        
        // Update device status (device is online if sending alerts)
        updateDeviceStatus(deviceId, timestamp, rms, zcr, true)
        
        val device = devices.value.find { it.deviceId == deviceId }
        val deviceName = device?.deviceName ?: deviceId
        
        val alert = DeviceAlert(
            deviceId = deviceId,
            message = "$label detected at $deviceName",
            type = label,
            timestamp = timestamp,
            rms = rms,
            zcr = zcr,
            confidence = "High"
        )
        
        val currentAlerts = _recentAlerts.value.toMutableList()
        currentAlerts.add(0, alert)
        // Limit to 10 alerts only
        if (currentAlerts.size > 10) {
            currentAlerts.removeAt(currentAlerts.size - 1)
        }
        _recentAlerts.value = currentAlerts
    }
    
    fun addDevice(device: Device) {
        viewModelScope.launch {
            repository.addDevice(device)
        }
    }
    
    fun updateDevice(device: Device) {
        viewModelScope.launch {
            // Update local repository
            repository.updateDevice(device)
            
            // Also update Firebase info node
            val updates = mapOf(
                "devices/${device.deviceId}/info/floor" to device.location.floor,
                "devices/${device.deviceId}/info/zone" to device.location.zone,
                "devices/${device.deviceId}/info/description" to device.location.description,
                "devices/${device.deviceId}/info/device_name" to device.deviceName,
                "devices/${device.deviceId}/info/updated_at" to System.currentTimeMillis()
            )
            database.reference.updateChildren(updates)
            Log.i(TAG, "Device info updated in Firebase for ${device.deviceId}")
        }
    }
    
    fun deleteDevice(deviceId: String) {
        viewModelScope.launch {
            stopListeningToDevice(deviceId)
            repository.deleteDevice(deviceId)
        }
    }
    
    fun sendCommand(deviceId: String, command: String) {
        // Send command to Firebase, Python bridge will forward to MQTT
        try {
            val commandRef = database.getReference("devices/$deviceId/commands")
            commandRef.push().setValue(mapOf(
                "command" to command,
                "timestamp" to System.currentTimeMillis()
            ))
            Log.i(TAG, "Command sent to $deviceId: $command")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send command", e)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Clean up all listeners
        deviceListeners.forEach { (deviceId, _) ->
            stopListeningToDevice(deviceId)
        }
    }
}
