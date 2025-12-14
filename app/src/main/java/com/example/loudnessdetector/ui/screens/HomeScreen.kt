package com.example.loudnessdetector.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.loudnessdetector.data.Device
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    devices: List<Device>,
    isConnected: Boolean,
    onDeviceClick: (String) -> Unit,
    onAddDevice: () -> Unit,
    onEditDevice: (String) -> Unit,
    onDeleteDevice: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sound Detection Devices") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isConnected) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.errorContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDevice) {
                Icon(Icons.Default.Add, contentDescription = "Add Device")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Connection status
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isConnected) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.errorContainer,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isConnected) "🟢 MQTT Connected" else "🔴 MQTT Disconnected",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Device list
            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No devices found",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Add a device or wait for auto-discovery",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(devices) { device ->
                        DeviceCard(
                            device = device,
                            onClick = { onDeviceClick(device.deviceId) },
                            onEdit = { onEditDevice(device.deviceId) },
                            onDelete = { onDeleteDevice(device.deviceId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: Device,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // Debug logging
    android.util.Log.d("HomeScreen", "DeviceCard rendering: ${device.deviceId} isOnline=${device.isOnline}")
    
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${device.location.floor} - ${device.location.zone}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (device.isOnline) "🟢 Online" else "⚫ Offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (device.isOnline) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (device.isOnline) {
                        Text(
                            text = "RMS: ${device.lastRms} | ZCR: ${"%.3f".format(device.lastZcr)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val lastSeenTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            .format(Date(device.lastSeen))
                        Text(
                            text = "Last seen: $lastSeenTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
