package com.example.loudnessdetector

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttManager(private val context: Context) {
    
    private val TAG = "MqttManager"
    private val BROKER_URL = "tcp://test.mosquitto.org:1883"
    private val CLIENT_ID = "android_${System.currentTimeMillis()}"
    
    private val TOPIC_ALERT_WILDCARD = "library/+/alert"
    private val TOPIC_STATUS_WILDCARD = "library/+/status"
    private val TOPIC_ALERT_HISTORY_WILDCARD = "library/+/alert_history"
    
    private var mqttClient: MqttClient? = null
    private val gson = Gson()
    private var isConnecting = false
    
    var onAlertReceived: ((deviceId: String, message: String) -> Unit)? = null
    var onStatusReceived: ((deviceId: String, message: String) -> Unit)? = null
    var onAlertHistoryReceived: ((deviceId: String, message: String) -> Unit)? = null
    var onConnectionChanged: ((Boolean) -> Unit)? = null
    
    fun connect() {
        if (isConnecting) {
            Log.d(TAG, "Already connecting, skipping...")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            isConnecting = true
            try {
                // Force IPv4
                System.setProperty("java.net.preferIPv4Stack", "true")
                
                val persistence = MemoryPersistence()
                mqttClient = MqttClient(BROKER_URL, CLIENT_ID, persistence)
                
                mqttClient?.setCallback(object : MqttCallback {
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        message?.let {
                            val payload = String(it.payload)
                            Log.d(TAG, "MESSAGE ARRIVED!")
                            Log.d(TAG, "   Topic: $topic")
                            Log.d(TAG, "   Payload: $payload")
                            
                            // Extract deviceId from topic (library/device001/alert -> device001)
                            val deviceId = topic?.split("/")?.getOrNull(1) ?: "unknown"
                            
                            CoroutineScope(Dispatchers.Main).launch {
                                when {
                                    topic?.endsWith("/alert") == true -> {
                                        Log.d(TAG, "Alert from $deviceId")
                                        onAlertReceived?.invoke(deviceId, payload)
                                    }
                                    topic?.endsWith("/status") == true -> {
                                        Log.d(TAG, "Status from $deviceId")
                                        onStatusReceived?.invoke(deviceId, payload)
                                    }
                                    topic?.endsWith("/alert_history") == true -> {
                                        Log.d(TAG, "Alert history from $deviceId")
                                        onAlertHistoryReceived?.invoke(deviceId, payload)
                                    }
                                    else -> {
                                        Log.d(TAG, "Unknown topic: $topic")
                                    }
                                }
                            }
                        }
                    }
                    
                    override fun connectionLost(cause: Throwable?) {
                        Log.e(TAG, "Connection lost: ${cause?.message}")
                        CoroutineScope(Dispatchers.Main).launch {
                            onConnectionChanged?.invoke(false)
                        }
                    }
                    
                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                        Log.d(TAG, "Delivery complete")
                    }
                })
                
                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 30
                    keepAliveInterval = 60
                    isAutomaticReconnect = true
                    maxReconnectDelay = 10000
                }
                
                Log.d(TAG, "Attempting to connect to $BROKER_URL...")
                mqttClient?.connect(options)
                Log.d(TAG, "Connected to MQTT broker!")
                
                withContext(Dispatchers.Main) {
                    onConnectionChanged?.invoke(true)
                }
                subscribeToTopics()
                
            } catch (e: Exception) {
                Log.e(TAG, "Connect error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onConnectionChanged?.invoke(false)
                }
            } finally {
                isConnecting = false
            }
        }
    }
    
    private fun subscribeToTopics() {
        try {
            mqttClient?.subscribe(TOPIC_ALERT_WILDCARD, 0)
            Log.d(TAG, "Subscribed to $TOPIC_ALERT_WILDCARD")
            
            mqttClient?.subscribe(TOPIC_STATUS_WILDCARD, 0)
            Log.d(TAG, "Subscribed to $TOPIC_STATUS_WILDCARD")
            
            mqttClient?.subscribe(TOPIC_ALERT_HISTORY_WILDCARD, 0)
            Log.d(TAG, "Subscribed to $TOPIC_ALERT_HISTORY_WILDCARD")
        } catch (e: Exception) {
            Log.e(TAG, "Subscribe error: ${e.message}", e)
        }
    }
    
    fun requestAlertHistory(deviceId: String) {
        sendCommand(deviceId, "get_alert_history")
    }
    
    fun sendCommand(deviceId: String, action: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isConnected()) {
                    Log.w(TAG, "Not connected, cannot send command: $action")
                    return@launch
                }
                
                val topic = "library/$deviceId/command"
                val command = JsonObject().apply {
                    addProperty("action", action)
                }
                
                val message = MqttMessage(command.toString().toByteArray()).apply {
                    qos = 0
                }
                
                Log.d(TAG, "Publishing to $topic: ${command}")
                mqttClient?.publish(topic, message)
                Log.d(TAG, "Command sent: $action")
            } catch (e: Exception) {
                Log.e(TAG, "Send command error: ${e.message}", e)
            }
        }
    }
    
    fun disconnect() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                mqttClient?.disconnect()
                mqttClient?.close()
                withContext(Dispatchers.Main) {
                    onConnectionChanged?.invoke(false)
                }
                Log.d(TAG, "Disconnected from MQTT broker")
            } catch (e: Exception) {
                Log.e(TAG, "Disconnect error: ${e.message}", e)
            }
        }
    }
    
    fun isConnected(): Boolean = mqttClient?.isConnected == true
}
