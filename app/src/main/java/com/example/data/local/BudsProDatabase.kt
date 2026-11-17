package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DeviceHistoryEntity::class,
        DeviceProfileEntity::class,
        BatteryHistoryEntity::class,
        CustomEqPresetEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BudsProDatabase : RoomDatabase() {
    abstract fun deviceHistoryDao(): DeviceHistoryDao
    abstract fun deviceProfileDao(): DeviceProfileDao
    abstract fun batteryHistoryDao(): BatteryHistoryDao
    abstract fun customEqPresetDao(): CustomEqPresetDao

    companion object {
        @Volatile
        private var INSTANCE: BudsProDatabase? = null

        fun getInstance(context: Context): BudsProDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BudsProDatabase::class.java,
                    "hardik_buds_pro.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
