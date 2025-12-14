package com.example.loudnessdetector.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.loudnessdetector.MainActivity
import com.example.loudnessdetector.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.database.FirebaseDatabase

/**
 * Firebase Cloud Messaging service for push notifications
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {
    
    private val TAG = "FCMService"
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        
        // Save token to SharedPreferences for later use
        getSharedPreferences("fcm", Context.MODE_PRIVATE)
            .edit()
            .putString("token", token)
            .apply()
        
        // Upload token to Firebase for the current user
        // Using hardcoded user ID - in production, use FirebaseAuth
        uploadTokenToFirebase(token, "user123")
    }
    
    private fun uploadTokenToFirebase(token: String, userId: String) {
        try {
            val database = FirebaseDatabase.getInstance()
            val tokenRef = database.getReference("users/$userId/fcmToken")
            tokenRef.setValue(token)
                .addOnSuccessListener {
                    Log.i(TAG, "FCM token uploaded to Firebase for user $userId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to upload FCM token to Firebase", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading FCM token", e)
        }
    }
    
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        Log.d(TAG, "Message received from: ${message.from}")
        
        // Handle notification
        message.notification?.let { notification ->
            showNotification(
                notification.title ?: "Alert",
                notification.body ?: "New detection"
            )
        }
        
        // Handle data payload
        if (message.data.isNotEmpty()) {
            Log.d(TAG, "Message data: ${message.data}")
            val deviceId = message.data["device_id"]
            val label = message.data["label"]
            val type = message.data["type"]
            
            if (type == "alarm") {
                showNotification(
                    "Speech Detected!",
                    "Device $deviceId detected $label"
                )
            }
        }
    }
    
    private fun showNotification(title: String, message: String) {
        val channelId = "alarm_channel"
        val notificationId = System.currentTimeMillis().toInt()
        
        createNotificationChannel(channelId)
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }
    
    private fun createNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for speech detection alarms"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
