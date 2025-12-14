package com.example.loudnessdetector.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "devices")

class DeviceRepository(private val context: Context) {
    
    private val gson = Gson()
    private val DEVICES_KEY = stringPreferencesKey("devices")
    
    val devices: Flow<List<Device>> = context.dataStore.data.map { preferences ->
        val json = preferences[DEVICES_KEY] ?: "[]"
        val type = object : TypeToken<List<DeviceEntity>>() {}.type
        val entities: List<DeviceEntity> = gson.fromJson(json, type)
        entities.map { it.toDevice() }
    }
    
    suspend fun saveDevices(devices: List<Device>) {
        android.util.Log.d("DeviceRepository", "Saving ${devices.size} devices")
        devices.forEach { device ->
            android.util.Log.d("DeviceRepository", "  - ${device.deviceId}: online=${device.isOnline}")
        }
        context.dataStore.edit { preferences ->
            val entities = devices.map { it.toEntity() }
            preferences[DEVICES_KEY] = gson.toJson(entities)
        }
        android.util.Log.d("DeviceRepository", "Devices saved successfully")
    }
    
    suspend fun addDevice(device: Device) {
        context.dataStore.data.collect { preferences ->
            val json = preferences[DEVICES_KEY] ?: "[]"
            val type = object : TypeToken<List<DeviceEntity>>() {}.type
            val entities: MutableList<DeviceEntity> = gson.fromJson(json, type)
            val currentDevices = entities.map { it.toDevice() }.toMutableList()
            
            if (currentDevices.none { it.deviceId == device.deviceId }) {
                currentDevices.add(device)
                saveDevices(currentDevices)
            }
            return@collect
        }
    }
    
    suspend fun updateDevice(device: Device) {
        context.dataStore.data.collect { preferences ->
            val json = preferences[DEVICES_KEY] ?: "[]"
            val type = object : TypeToken<List<DeviceEntity>>() {}.type
            val entities: MutableList<DeviceEntity> = gson.fromJson(json, type)
            val currentDevices = entities.map { it.toDevice() }.toMutableList()
            
            val index = currentDevices.indexOfFirst { it.deviceId == device.deviceId }
            if (index != -1) {
                currentDevices[index] = device
                saveDevices(currentDevices)
            }
            return@collect
        }
    }
    
    suspend fun deleteDevice(deviceId: String) {
        context.dataStore.data.collect { preferences ->
            val json = preferences[DEVICES_KEY] ?: "[]"
            val type = object : TypeToken<List<DeviceEntity>>() {}.type
            val entities: MutableList<DeviceEntity> = gson.fromJson(json, type)
            val currentDevices = entities.map { it.toDevice() }.toMutableList()
            
            currentDevices.removeAll { it.deviceId == deviceId }
            saveDevices(currentDevices)
            return@collect
        }
    }
}
