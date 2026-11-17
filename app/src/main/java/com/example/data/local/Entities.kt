package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_history")
data class DeviceHistoryEntity(
    @PrimaryKey val address: String,
    val name: String,
    val brandName: String,
    val lastConnectedAt: Long,
    val connectionCount: Int,
    val firmwareVersion: String?,
    val supportsFastPair: Boolean,
    val isFavorite: Boolean = false
)

@Entity(tableName = "device_profiles")
data class DeviceProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val profileName: String,
    val brandName: String,
    val deviceAddress: String?,
    val preferredEqPreset: String,
    val spatialAudioPreferred: Boolean,
    val notes: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "battery_history")
data class BatteryHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val deviceAddress: String,
    val deviceName: String,
    val leftPercent: Int?,
    val rightPercent: Int?,
    val casePercent: Int?,
    val overallPercent: Int?,
    val sourceLabel: String,
    val isCharging: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_eq_presets")
data class CustomEqPresetEntity(
    @PrimaryKey val name: String,
    val gainsDbCsv: String,
    val updatedAt: Long = System.currentTimeMillis()
)
