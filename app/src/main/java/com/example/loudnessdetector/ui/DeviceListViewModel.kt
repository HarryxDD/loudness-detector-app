package com.example.loudnessdetector.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.loudnessdetector.MqttManager
import com.example.loudnessdetector.data.Device
import com.example.loudnessdetector.data.DeviceAlert
import com.example.loudnessdetector.data.DeviceLocation
import com.example.loudnessdetector.data.DeviceRepository
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DeviceListViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = DeviceRepository(application)
    private val mqttManager = MqttManager(application)
    private val gson = Gson()
    
    // Offline detection: mark device offline if no update for 30 seconds
    private val OFFLINE_TIMEOUT_MS = 30_000L
    
    // Alert cooldown: minimum 5 seconds between alerts from same device
    private val ALERT_COOLDOWN_MS = 5_000L
    private val lastAlertTime = mutableMapOf<String, Long>()
    
    val devices: StateFlow<List<Device>> = repository.devices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _recentAlerts = MutableStateFlow<List<DeviceAlert>>(emptyList())
    val recentAlerts: StateFlow<List<DeviceAlert>> = _recentAlerts.asStateFlow()
    
    init {
        setupMqttCallbacks()
        connectMqtt()
        startOfflineDetection()
    }
    
    private fun setupMqttCallbacks() {
        mqttManager.onConnectionChanged = { connected ->
            _isConnected.value = connected
        }
        
        mqttManager.onStatusReceived = { deviceId, message ->
            handleStatusMessage(deviceId, message)
        }
        
        mqttManager.onAlertReceived = { deviceId, message ->
            handleAlertMessage(deviceId, message)
        }
    }
    
    private fun connectMqtt() {
        mqttManager.connect()
    }
    
    private fun startOfflineDetection() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000) // Check every 10 seconds
                checkOfflineDevices()
            }
        }
    }
    
    private fun checkOfflineDevices() {
        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            val currentDevices = devices.value.toMutableList()
            var hasChanges = false
            
            for (i in currentDevices.indices) {
                val device = currentDevices[i]
                if (device.isOnline && (currentTime - device.lastSeen) > OFFLINE_TIMEOUT_MS) {
                    currentDevices[i] = device.copy(isOnline = false)
                    hasChanges = true
                }
            }
            
            if (hasChanges) {
                repository.saveDevices(currentDevices)
            }
        }
    }
    
    private fun handleStatusMessage(deviceId: String, message: String) {
        try {
            val json = gson.fromJson(message, JsonObject::class.java)
            val rms = json.get("rms")?.asInt ?: 0
            val zcr = json.get("zcr")?.asDouble ?: 0.0
            
            viewModelScope.launch {
                val currentDevices = devices.value.toMutableList()
                val existingDevice = currentDevices.find { it.deviceId == deviceId }
                
                if (existingDevice != null) {
                    val updated = existingDevice.copy(
                        isOnline = true,
                        lastSeen = System.currentTimeMillis(),
                        lastRms = rms,
                        lastZcr = zcr
                    )
                    currentDevices[currentDevices.indexOf(existingDevice)] = updated
                } else {
                    // New device discovered
                    val newDevice = Device(
                        deviceId = deviceId,
                        deviceName = "Device ${deviceId.takeLast(3)}",
                        location = DeviceLocation("Unknown", "Unknown", "Auto-discovered"),
                        isOnline = true,
                        lastSeen = System.currentTimeMillis(),
                        lastRms = rms,
                        lastZcr = zcr
                    )
                    currentDevices.add(newDevice)
                }
                
                repository.saveDevices(currentDevices)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun handleAlertMessage(deviceId: String, message: String) {
        try {
            val json = gson.fromJson(message, JsonObject::class.java)
            val type = json.get("type")?.asString ?: "UNKNOWN"
            val rms = json.get("rms")?.asInt ?: 0
            val zcr = json.get("zcr")?.asDouble ?: 0.0
            
            // Check alert cooldown
            val currentTime = System.currentTimeMillis()
            val lastAlert = lastAlertTime[deviceId] ?: 0L
            if (currentTime - lastAlert < ALERT_COOLDOWN_MS) {
                return // Skip this alert, too soon
            }
            lastAlertTime[deviceId] = currentTime
            
            // Update device RMS/ZCR on alert as well
            viewModelScope.launch {
                val currentDevices = devices.value.toMutableList()
                val existingDevice = currentDevices.find { it.deviceId == deviceId }
                if (existingDevice != null) {
                    val updated = existingDevice.copy(
                        isOnline = true,
                        lastSeen = currentTime,
                        lastRms = rms,
                        lastZcr = zcr
                    )
                    currentDevices[currentDevices.indexOf(existingDevice)] = updated
                    repository.saveDevices(currentDevices)
                }
            }
            
            val device = devices.value.find { it.deviceId == deviceId }
            val deviceName = device?.deviceName ?: deviceId
            
            val alert = DeviceAlert(
                deviceId = deviceId,
                message = "$type detected at ${deviceName}",
                type = type,
                timestamp = currentTime,
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun addDevice(device: Device) {
        viewModelScope.launch {
            repository.addDevice(device)
        }
    }
    
    fun updateDevice(device: Device) {
        viewModelScope.launch {
            repository.updateDevice(device)
        }
    }
    
    fun deleteDevice(deviceId: String) {
        viewModelScope.launch {
            repository.deleteDevice(deviceId)
        }
    }
    
    fun sendCommand(deviceId: String, action: String) {
        mqttManager.sendCommand(deviceId, action)
    }
    
    override fun onCleared() {
        super.onCleared()
        mqttManager.disconnect()
    }
}
