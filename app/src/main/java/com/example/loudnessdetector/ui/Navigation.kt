package com.example.loudnessdetector.ui

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object DeviceDetail : Screen("device/{deviceId}") {
        fun createRoute(deviceId: String) = "device/$deviceId"
    }
    object EditDevice : Screen("edit/{deviceId}") {
        fun createRoute(deviceId: String) = "edit/$deviceId"
    }
    object AddDevice : Screen("add")
}
