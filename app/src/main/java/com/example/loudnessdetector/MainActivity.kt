package com.example.loudnessdetector

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.loudnessdetector.ui.DeviceListViewModel
import com.example.loudnessdetector.ui.Screen
import com.example.loudnessdetector.ui.screens.DeviceDetailScreen
import com.example.loudnessdetector.ui.screens.EditDeviceScreen
import com.example.loudnessdetector.ui.screens.HomeScreen
import com.example.loudnessdetector.ui.theme.LoudnessDetectorTheme

class MainActivity : ComponentActivity() {
    
    private val CHANNEL_ID = "speech_alerts"
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission result handled
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        createNotificationChannel()
        requestNotificationPermission()
        
        setContent {
            LoudnessDetectorTheme {
                val navController = rememberNavController()
                val viewModel: DeviceListViewModel = viewModel()
                
                val devices by viewModel.devices.collectAsState()
                val isConnected by viewModel.isConnected.collectAsState()
                val recentAlerts by viewModel.recentAlerts.collectAsState()
                
                // Show notifications for new alerts
                LaunchedEffect(recentAlerts.size) {
                    if (recentAlerts.isNotEmpty()) {
                        val latest = recentAlerts.first()
                        showNotification(
                            "Speech Detected!",
                            latest.message
                        )
                    }
                }
                
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(
                            devices = devices,
                            isConnected = isConnected,
                            onDeviceClick = { deviceId ->
                                navController.navigate(Screen.DeviceDetail.createRoute(deviceId))
                            },
                            onAddDevice = {
                                navController.navigate(Screen.AddDevice.route)
                            },
                            onEditDevice = { deviceId ->
                                navController.navigate(Screen.EditDevice.createRoute(deviceId))
                            },
                            onDeleteDevice = { deviceId ->
                                viewModel.deleteDevice(deviceId)
                            }
                        )
                    }
                    
                    composable(
                        route = Screen.DeviceDetail.route,
                        arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
                        val device = devices.find { it.deviceId == deviceId }
                        
                        DeviceDetailScreen(
                            device = device,
                            alerts = recentAlerts,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateEdit = { navController.navigate(Screen.EditDevice.createRoute(deviceId)) },
                            onCalibrate = { viewModel.sendCommand(deviceId, "calibrate") }
                        )
                    }
                    
                    composable(
                        route = Screen.EditDevice.route,
                        arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
                        val device = devices.find { it.deviceId == deviceId }
                        
                        EditDeviceScreen(
                            device = device,
                            onNavigateBack = { navController.popBackStack() },
                            onSave = { updatedDevice ->
                                viewModel.updateDevice(updatedDevice)
                            }
                        )
                    }
                    
                    composable(Screen.AddDevice.route) {
                        EditDeviceScreen(
                            device = null,
                            onNavigateBack = { navController.popBackStack() },
                            onSave = { newDevice ->
                                viewModel.addDevice(newDevice)
                            }
                        )
                    }
                }
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Speech Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for speech detection events"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    
    private fun showNotification(title: String, message: String) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}