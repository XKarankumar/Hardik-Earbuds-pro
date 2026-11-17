package com.example.data.repository

import com.example.data.local.BatteryHistoryDao
import com.example.data.local.BatteryHistoryEntity
import com.example.data.local.BudsProDatabase
import com.example.data.local.CustomEqPresetDao
import com.example.data.local.CustomEqPresetEntity
import com.example.data.local.DeviceHistoryDao
import com.example.data.local.DeviceHistoryEntity
import com.example.data.local.DeviceProfileDao
import com.example.data.local.DeviceProfileEntity
import com.example.data.local.SecurityEventDao
import com.example.data.local.SecurityEventEntity
import com.example.data.local.TrustedDeviceDao
import com.example.data.local.TrustedDeviceEntity
import com.example.devices.BatteryTelemetry
import com.example.devices.EarbudsBrand
import kotlinx.coroutines.flow.Flow

class BudsRepository(
    database: BudsProDatabase
) {
    private val deviceHistoryDao: DeviceHistoryDao = database.deviceHistoryDao()
    private val deviceProfileDao: DeviceProfileDao = database.deviceProfileDao()
    private val batteryHistoryDao: BatteryHistoryDao = database.batteryHistoryDao()
    private val customEqPresetDao: CustomEqPresetDao = database.customEqPresetDao()
    private val trustedDeviceDao: TrustedDeviceDao = database.trustedDeviceDao()
    private val securityEventDao: SecurityEventDao = database.securityEventDao()

    val deviceHistory: Flow<List<DeviceHistoryEntity>> = deviceHistoryDao.observeAllHistory()
    val deviceProfiles: Flow<List<DeviceProfileEntity>> = deviceProfileDao.observeAllProfiles()
    val batteryLogs: Flow<List<BatteryHistoryEntity>> = batteryHistoryDao.observeRecentBatteryLogs()
    val customEqPresets: Flow<List<CustomEqPresetEntity>> = customEqPresetDao.observeAllPresets()
    val trustedDevices: Flow<List<TrustedDeviceEntity>> = trustedDeviceDao.observeTrustedDevices()
    val securityEvents: Flow<List<SecurityEventEntity>> = securityEventDao.observeAllEvents()
    val unauthorizedAlerts: Flow<List<SecurityEventEntity>> = securityEventDao.observeUnauthorizedAlerts()

    suspend fun recordDeviceConnection(
        address: String,
        name: String,
        brand: EarbudsBrand,
        firmwareVersion: String?,
        supportsFastPair: Boolean
    ) {
        val existing = deviceHistoryDao.getByAddress(address)
        val updated = DeviceHistoryEntity(
            address = address,
            name = name,
            brandName = brand.displayName,
            lastConnectedAt = System.currentTimeMillis(),
            connectionCount = (existing?.connectionCount ?: 0) + 1,
            firmwareVersion = firmwareVersion ?: existing?.firmwareVersion,
            supportsFastPair = supportsFastPair || (existing?.supportsFastPair == true),
            isFavorite = existing?.isFavorite ?: false
        )
        deviceHistoryDao.upsert(updated)
    }

    suspend fun toggleFavoriteDevice(address: String, favorite: Boolean) {
        deviceHistoryDao.setFavorite(address, favorite)
    }

    suspend fun deleteHistoryDevice(address: String) {
        deviceHistoryDao.deleteByAddress(address)
    }

    suspend fun saveProfile(profile: DeviceProfileEntity) {
        deviceProfileDao.upsertProfile(profile)
    }

    suspend fun deleteProfile(id: Int) {
        deviceProfileDao.deleteById(id)
    }

    suspend fun logBatterySnapshot(
        address: String,
        deviceName: String,
        telemetry: BatteryTelemetry
    ) {
        if (!telemetry.hasAnyBatteryValue) return
        batteryHistoryDao.insertLog(
            BatteryHistoryEntity(
                deviceAddress = address,
                deviceName = deviceName,
                leftPercent = telemetry.leftPercent,
                rightPercent = telemetry.rightPercent,
                casePercent = telemetry.casePercent,
                overallPercent = telemetry.overallPercent,
                sourceLabel = telemetry.source.label,
                isCharging = (telemetry.isLeftCharging == true) ||
                    (telemetry.isRightCharging == true) ||
                    (telemetry.isCaseCharging == true),
                timestamp = telemetry.lastUpdatedMillis
            )
        )
    }

    suspend fun saveCustomEqPreset(name: String, gainsDb: List<Float>) {
        if (gainsDb.size != 10) return
        customEqPresetDao.upsertPreset(
            CustomEqPresetEntity(
                name = name.trim(),
                gainsDbCsv = gainsDb.joinToString(",")
            )
        )
    }

    suspend fun deleteCustomEqPreset(name: String) {
        customEqPresetDao.deleteByName(name)
    }

    suspend fun isDeviceTrusted(address: String): Boolean {
        return trustedDeviceDao.getByAddress(address) != null
    }

    suspend fun upsertTrustedDevice(device: TrustedDeviceEntity) {
        trustedDeviceDao.upsertTrustedDevice(device)
    }

    suspend fun removeTrustedDevice(address: String) {
        trustedDeviceDao.deleteByAddress(address)
    }

    suspend fun clearTrustedDevices() {
        trustedDeviceDao.clearAll()
    }

    suspend fun logSecurityEvent(
        eventType: String,
        title: String,
        description: String,
        deviceName: String? = null,
        deviceAddress: String? = null,
        severity: String = "INFO"
    ) {
        securityEventDao.insertEvent(
            SecurityEventEntity(
                eventType = eventType,
                title = title,
                description = description,
                deviceName = deviceName,
                deviceAddress = deviceAddress,
                severity = severity
            )
        )
    }

    suspend fun clearSecurityHistory() {
        securityEventDao.clearAll()
    }

    suspend fun clearAllLocalLogs() {
        deviceHistoryDao.clearAll()
        batteryHistoryDao.clearAll()
    }
}
