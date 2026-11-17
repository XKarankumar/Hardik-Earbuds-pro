package com.example.core

import java.util.UUID

object AppConstants {
    const val APP_NAME = "HARDIK BUDS PRO"
    const val APP_VERSION = "ULTIMATE PRO V2.0"
    const val VERSION_NAME = "2.0.0"
    const val OWNER_NAME = "HARDIK KUMAR"
    const val COPYRIGHT_NOTICE = "© 2026 Hardik Kumar. All Rights Reserved."

    // Notification Channels
    const val CHANNEL_CONNECTION = "buds_connection_alerts"
    const val CHANNEL_BATTERY = "buds_low_battery_alerts"
    const val CHANNEL_PLAYBACK = "buds_media_playback"

    const val NOTIF_ID_CONNECTION = 2001
    const val NOTIF_ID_BATTERY = 2002

    // Standard Bluetooth SIG UUIDs
    val UUID_BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val UUID_BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    val UUID_DEVICE_INFO_SERVICE: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    val UUID_MANUFACTURER_NAME: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
    val UUID_MODEL_NUMBER: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
    val UUID_FIRMWARE_REVISION: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
    val UUID_HARDWARE_REVISION: UUID = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb")
    val UUID_SOFTWARE_REVISION: UUID = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")

    // Google Fast Pair Service UUID (0xFE2C)
    val UUID_FAST_PAIR_SERVICE: UUID = UUID.fromString("0000fe2c-0000-1000-8000-00805f9b34fb")

    // Generic Audio / LE Audio Service UUIDs for informational discovery
    val UUID_PUBLISHED_AUDIO_CAPABILITIES: UUID = UUID.fromString("00001850-0000-1000-8000-00805f9b34fb")
    val UUID_AUDIO_STREAM_CONTROL: UUID = UUID.fromString("0000184e-0000-1000-8000-00805f9b34fb")
    val UUID_VOLUME_CONTROL: UUID = UUID.fromString("00001844-0000-1000-8000-00805f9b34fb")

    // System broadcast action for Bluetooth battery level changes (API 27+)
    const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

    fun resolveSigName(uuid: UUID): String {
        return when (uuid) {
            UUID_BATTERY_SERVICE -> "Battery Service (0x180F)"
            UUID_BATTERY_LEVEL -> "Battery Level (0x2A19)"
            UUID_DEVICE_INFO_SERVICE -> "Device Information (0x180A)"
            UUID_MANUFACTURER_NAME -> "Manufacturer Name (0x2A29)"
            UUID_MODEL_NUMBER -> "Model Number (0x2A24)"
            UUID_FIRMWARE_REVISION -> "Firmware Revision (0x2A26)"
            UUID_HARDWARE_REVISION -> "Hardware Revision (0x2A27)"
            UUID_SOFTWARE_REVISION -> "Software Revision (0x2A28)"
            UUID_FAST_PAIR_SERVICE -> "Google Fast Pair Service (0xFE2C)"
            UUID_PUBLISHED_AUDIO_CAPABILITIES -> "LE Audio PACS (0x1850)"
            UUID_AUDIO_STREAM_CONTROL -> "LE Audio ASCS (0x184E)"
            UUID_VOLUME_CONTROL -> "LE Audio VCP (0x1844)"
            else -> {
                val shortHex = uuid.toString().substring(4, 8).uppercase()
                if (uuid.toString().endsWith("-0000-1000-8000-00805f9b34fb", ignoreCase = true)) {
                    "Bluetooth SIG (0x$shortHex)"
                } else {
                    "Vendor / Proprietary GATT"
                }
            }
        }
    }
}
