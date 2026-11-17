package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DeviceHistoryEntity::class,
        DeviceProfileEntity::class,
        BatteryHistoryEntity::class,
        CustomEqPresetEntity::class,
        TrustedDeviceEntity::class,
        SecurityEventEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class BudsProDatabase : RoomDatabase() {
    abstract fun deviceHistoryDao(): DeviceHistoryDao
    abstract fun deviceProfileDao(): DeviceProfileDao
    abstract fun batteryHistoryDao(): BatteryHistoryDao
    abstract fun customEqPresetDao(): CustomEqPresetDao
    abstract fun trustedDeviceDao(): TrustedDeviceDao
    abstract fun securityEventDao(): SecurityEventDao

    companion object {
        @Volatile
        private var INSTANCE: BudsProDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `trusted_devices` (
                        `address` TEXT NOT NULL,
                        `deviceName` TEXT NOT NULL,
                        `brandName` TEXT NOT NULL,
                        `isPrimaryBuds` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        `lastVerifiedAt` INTEGER NOT NULL,
                        `ownerKeyId` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        PRIMARY KEY(`address`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `security_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `deviceName` TEXT,
                        `deviceAddress` TEXT,
                        `severity` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): BudsProDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BudsProDatabase::class.java,
                    "hardik_buds_pro.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
