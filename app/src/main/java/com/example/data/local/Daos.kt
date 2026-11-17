package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceHistoryDao {
    @Query("SELECT * FROM device_history ORDER BY isFavorite DESC, lastConnectedAt DESC")
    fun observeAllHistory(): Flow<List<DeviceHistoryEntity>>

    @Query("SELECT * FROM device_history WHERE address = :address LIMIT 1")
    suspend fun getByAddress(address: String): DeviceHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: DeviceHistoryEntity)

    @Query("UPDATE device_history SET isFavorite = :favorite WHERE address = :address")
    suspend fun setFavorite(address: String, favorite: Boolean)

    @Query("DELETE FROM device_history WHERE address = :address")
    suspend fun deleteByAddress(address: String)

    @Query("DELETE FROM device_history")
    suspend fun clearAll()
}

@Dao
interface DeviceProfileDao {
    @Query("SELECT * FROM device_profiles ORDER BY updatedAt DESC")
    fun observeAllProfiles(): Flow<List<DeviceProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: DeviceProfileEntity)

    @Query("DELETE FROM device_profiles WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface BatteryHistoryDao {
    @Query("SELECT * FROM battery_history ORDER BY timestamp DESC LIMIT 100")
    fun observeRecentBatteryLogs(): Flow<List<BatteryHistoryEntity>>

    @Query("SELECT * FROM battery_history WHERE deviceAddress = :address ORDER BY timestamp DESC LIMIT 40")
    fun observeBatteryForDevice(address: String): Flow<List<BatteryHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(entry: BatteryHistoryEntity)

    @Query("DELETE FROM battery_history")
    suspend fun clearAll()
}

@Dao
interface CustomEqPresetDao {
    @Query("SELECT * FROM custom_eq_presets ORDER BY updatedAt DESC")
    fun observeAllPresets(): Flow<List<CustomEqPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreset(preset: CustomEqPresetEntity)

    @Query("DELETE FROM custom_eq_presets WHERE name = :name")
    suspend fun deleteByName(name: String)
}
