# Smart Librarian Android App

Simple Android monitoring app for the IoT Privacy-Preserving Security System (Pico W).

## Features

- **Real-time MQTT monitoring** of Pico W device
- **Speech detection alerts** with notifications
- **Remote calibration** - trigger calibration from phone
- **Live audio metrics** - RMS and ZCR values
- **Alert history** - last 20 speech detection events
- **Device status** - connection and online/offline status

## Quick Start

1. **Open in Android Studio**
   - File → Open → Select `loudness-detector-app` folder
   - Wait for Gradle sync

2. **Build and Run**
   - Connect Android device or start emulator
   - Click Run ️

3. **Grant Permissions**
   - Allow notification permission when prompted

4. **Connect to Device**
   - App auto-connects to `broker.hivemq.com`
   - Subscribes to `library/device001/alert` and `library/device001/status`
   - Make sure your Pico W is running with the same DEVICE_ID

## How to Use

### Dashboard
- **Green indicator** = Connected to MQTT
- **Red indicator** = Disconnected
- Shows device name and current status

### Live Data Card
- **RMS**: Current loudness level
- **ZCR**: Current zero-crossing rate (frequency)

### Control Buttons
- **Calibrate**: Starts remote calibration on Pico W (takes 10 seconds)
- **Refresh**: Requests current device info
- **Reset Alarm**: Clears alarm LED on Pico W

### Alerts List
- Shows recent speech detection events
- Each card displays:
  - Time of detection
  - RMS and ZCR values
  - Confidence level (HIGH/MEDIUM/LOW)

### Notifications
- Push notification when speech detected
- Works even when app is in background

## Configuration 

To change device ID, edit `MqttManager.kt`:

```kotlin
private val DEVICE_ID = "device001"  // Change to device002, device003, etc.
```

## Architecture 

```
MainActivity
  ├─ MqttManager (MQTT connection & messaging)
  ├─ MainViewModel (app state & data)
  └─ DashboardScreen (UI)
```

### Key Files
- `MainActivity.kt` - Main activity with UI and notification handling
- `MqttManager.kt` - MQTT client wrapper
- `MainViewModel.kt` - State management for alerts and device data
- `data/AlertMessage.kt` - Data models for MQTT messages

## MQTT Topics

**Subscribe (receive from Pico):**
- `library/device001/alert` - Speech detection events
- `library/device001/status` - Device status, calibration progress

**Publish (send to Pico):**
- `library/device001/command` - Remote commands

## Commands

Send these JSON commands to control Pico W:

```json
{"action": "calibrate"}     // Start calibration
{"action": "get_info"}      // Get device info
{"action": "reset_alarm"}   // Reset alarm LED
{"action": "get_status"}    // Get current status
```

## Troubleshooting

**App won't connect:**
- Check internet connection
- Verify Pico W is running and connected to same MQTT broker
- Check device ID matches in both app and Pico config

**No notifications:**
- Grant notification permission in Settings
- Check notification channel is enabled

**No alerts showing:**
- Verify Pico W is detecting speech
- Check MQTT topics match in both systems
- Look at Logcat for MQTT messages

## Build Requirements

- Android Studio Hedgehog or newer
- Android SDK 24+ (Android 7.0+)
- Kotlin 2.0+
- Gradle 8.11+

## Dependencies

- Eclipse Paho MQTT (1.2.5) - MQTT client
- Jetpack Compose - UI framework
- Gson - JSON parsing
- Material 3 - Design components
