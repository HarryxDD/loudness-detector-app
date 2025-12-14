package com.example.loudnessdetector.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loudnessdetector.firebase.DeviceInfo
import com.example.loudnessdetector.firebase.DeviceMessage
import com.example.loudnessdetector.firebase.DeviceStatus
import com.example.loudnessdetector.firebase.FirebaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Device Detail Screen
 * Shows current status, recent alerts, and allows calibration/editing
 */
class DeviceDetailViewModel(private val deviceId: String) : ViewModel() {
    
    private val firebaseManager = FirebaseManager(deviceId)
    
    private val _deviceStatus = MutableStateFlow<DeviceStatus?>(null)
    val deviceStatus: StateFlow<DeviceStatus?> = _deviceStatus.asStateFlow()
    
    private val _recentMessages = MutableStateFlow<List<DeviceMessage>>(emptyList())
    val recentMessages: StateFlow<List<DeviceMessage>> = _recentMessages.asStateFlow()
    
    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()
    
    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating.asStateFlow()
    
    private val _calibrationProgress = MutableStateFlow<String?>(null)
    val calibrationProgress: StateFlow<String?> = _calibrationProgress.asStateFlow()
    
    init {
        // Start listening to device status
        viewModelScope.launch {
            firebaseManager.getDeviceStatus().collect { status ->
                _deviceStatus.value = status
            }
        }
        
        // Listen for calibration progress
        listenToCalibrationProgress()
        
        // Start listening to new messages
        viewModelScope.launch {
            firebaseManager.listenToMessages().collect { message ->
                if (message != null) {
                    // Add new message to the list
                    val current = _recentMessages.value.toMutableList()
                    current.add(0, message)
                    // Keep only last 10
                    if (current.size > 10) {
                        current.removeAt(current.size - 1)
                    }
                    _recentMessages.value = current
                }
            }
        }
        
        // Load initial data
        loadRecentMessages()
        loadDeviceInfo()
    }
    
    private fun loadRecentMessages() {
        viewModelScope.launch {
            val messages = firebaseManager.getRecentMessages(10)
            _recentMessages.value = messages
        }
    }
    
    private fun loadDeviceInfo() {
        viewModelScope.launch {
            val info = firebaseManager.getDeviceInfo()
            _deviceInfo.value = info
        }
    }
    
    fun triggerCalibration() {
        viewModelScope.launch {
            _isCalibrating.value = true
            val success = firebaseManager.triggerCalibration()
            if (success) {
                // Calibration command sent successfully
                // The calibration result will come through the messages listener
            }
            // Keep calibrating state for a few seconds
            kotlinx.coroutines.delay(3000)
            _isCalibrating.value = false
        }
    }
    
    fun updateDeviceInfo(floor: String, zone: String, description: String) {
        viewModelScope.launch {
            val success = firebaseManager.updateDeviceInfo(floor, zone, description)
            if (success) {
                _deviceInfo.value = DeviceInfo(floor, zone, description)
            }
        }
    }
    
    private fun listenToCalibrationProgress() {
        viewModelScope.launch {
            firebaseManager.getDeviceStatus().collect { status ->
                // Check if type is calibration_progress
                if (status?.type == "calibration_progress") {
                    _isCalibrating.value = true
                    // Use the message field for progress display
                    _calibrationProgress.value = status.message ?: "Calibrating..."
                } else if (status?.type == "calibration" || status?.type == "status") {
                    // Calibration finished or normal status
                    if (_isCalibrating.value) {
                        _isCalibrating.value = false
                        _calibrationProgress.value = null
                    }
                }
            }
        }
    }
}
