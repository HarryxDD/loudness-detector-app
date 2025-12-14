package com.example.loudnessdetector.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.loudnessdetector.data.Device
import com.example.loudnessdetector.data.DeviceAlert
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    device: Device?,
    alerts: List<DeviceAlert>,
    onNavigateBack: () -> Unit,
    onNavigateEdit: () -> Unit,
    onCalibrate: () -> Unit,
    onRefresh: () -> Unit,
    onReset: () -> Unit
) {
    if (device == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Device not found")
        }
        return
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device.deviceName) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Device status card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (device.isOnline)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (device.isOnline) "🟢 Device Online" else "⚫ Device Offline",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${device.location.floor} - ${device.location.zone}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (device.location.description.isNotEmpty()) {
                                Text(
                                    text = device.location.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    if (device.isOnline) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${device.lastRms}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "RMS",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "%.3f".format(device.lastZcr),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ZCR",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
            
            // Control buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCalibrate,
                    modifier = Modifier.weight(1f),
                    enabled = device.isOnline
                ) {
                    Text("Calibrate")
                }
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                    enabled = device.isOnline
                ) {
                    Text("Refresh")
                }
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                    enabled = device.isOnline
                ) {
                    Text("Reset")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Alerts section
            Text(
                text = "Recent Alerts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val deviceAlerts = alerts.filter { it.deviceId == device.deviceId }
            
            if (deviceAlerts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No alerts yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(deviceAlerts) { alert ->
                        AlertCard(alert)
                    }
                }
            }
        }
    }
}

@Composable
fun AlertCard(alert: DeviceAlert) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (alert.type == "SPEECH")
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${alert.type} DETECTED",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "RMS: ${alert.rms} | ZCR: ${"%.3f".format(alert.zcr)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Text(
                text = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(Date(alert.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
