package com.example.loudnessdetector.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.loudnessdetector.data.Device
import com.example.loudnessdetector.data.DeviceLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDeviceScreen(
    device: Device?,
    onNavigateBack: () -> Unit,
    onSave: (Device) -> Unit
) {
    var newDeviceId by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf(device?.deviceName ?: "") }
    var floor by remember { mutableStateOf(device?.location?.floor ?: "") }
    var zone by remember { mutableStateOf(device?.location?.zone ?: "") }
    var description by remember { mutableStateOf(device?.location?.description ?: "") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (device == null) "Add Device" else "Edit Device") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (device != null) {
                // Show device ID (read-only)
                OutlinedTextField(
                    value = device.deviceId,
                    onValueChange = {},
                    label = { Text("Device ID") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    supportingText = { Text("Device ID cannot be changed") }
                )
            } else {
                // For new devices, allow entering device ID
                OutlinedTextField(
                    value = newDeviceId,
                    onValueChange = { newDeviceId = it },
                    label = { Text("Device ID") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g., device001") }
                )
            }
            
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("Device Name") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g., Library Sensor 1") }
            )
            
            OutlinedTextField(
                value = floor,
                onValueChange = { floor = it },
                label = { Text("Floor") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g., Floor 1") }
            )
            
            OutlinedTextField(
                value = zone,
                onValueChange = { zone = it },
                label = { Text("Zone") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g., Reading Area") }
            )
            
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Additional notes about location") },
                minLines = 3,
                maxLines = 5
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = {
                    val updatedDevice = if (device != null) {
                        device.copy(
                            deviceName = deviceName,
                            location = DeviceLocation(floor, zone, description)
                        )
                    } else {
                        Device(
                            deviceId = newDeviceId,
                            deviceName = deviceName,
                            location = DeviceLocation(floor, zone, description),
                            isOnline = false,
                            lastSeen = System.currentTimeMillis(),
                            lastRms = 0,
                            lastZcr = 0.0
                        )
                    }
                    onSave(updatedDevice)
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = if (device != null) {
                    deviceName.isNotBlank() && floor.isNotBlank() && zone.isNotBlank()
                } else {
                    newDeviceId.isNotBlank() && deviceName.isNotBlank() && floor.isNotBlank() && zone.isNotBlank()
                }
            ) {
                Text("Save")
            }
        }
    }
}
