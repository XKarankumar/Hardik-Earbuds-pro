package com.example.devices

enum class EarbudsBrand(val displayName: String) {
    ONEPLUS("OnePlus"),
    SAMSUNG("Samsung"),
    SONY("Sony"),
    JBL("JBL"),
    BOAT("boAt"),
    REALME("Realme"),
    NOTHING("Nothing"),
    GENERIC("Bluetooth Audio")
}

enum class ConnectionState(val label: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting"),
    CONNECTED("Connected")
}

enum class BatterySource(val label: String, val accuracySummary: String) {
    BLE_GATT_BATTERY_SERVICE(
        label = "BLE GATT Battery Service (0x180F)",
        accuracySummary = "Direct GATT characteristic 0x2A19 read from device"
    ),
    HFP_VENDOR_AT_REPORT(
        label = "HFP Vendor AT Event",
        accuracySummary = "Reported by earbuds over Bluetooth Hands-Free AT telemetry"
    ),
    ANDROID_BATTERY_BROADCAST(
        label = "Android Bluetooth Battery Broadcast",
        accuracySummary = "System-reported Bluetooth battery level (0–100%)"
    ),
    UNAVAILABLE(
        label = "Not Exposed by Hardware",
        accuracySummary = "Device has not exposed battery telemetry over public Android APIs"
    )
}

data class BatteryTelemetry(
    val leftPercent: Int? = null,
    val rightPercent: Int? = null,
    val casePercent: Int? = null,
    val overallPercent: Int? = null,
    val isLeftCharging: Boolean? = null,
    val isRightCharging: Boolean? = null,
    val isCaseCharging: Boolean? = null,
    val source: BatterySource = BatterySource.UNAVAILABLE,
    val lastUpdatedMillis: Long = System.currentTimeMillis()
) {
    val hasAnyBatteryValue: Boolean
        get() = leftPercent != null || rightPercent != null || casePercent != null || overallPercent != null

    val lowestAvailablePercent: Int?
        get() = listOfNotNull(leftPercent, rightPercent, overallPercent).minOrNull()
}

enum class AncMode(val label: String) {
    ANC_ON("ANC On"),
    TRANSPARENCY("Transparency"),
    OFF("Noise Control Off"),
    ADAPTIVE("Adaptive ANC"),
    UNSUPPORTED("Requires Companion / Proprietary Protocol")
}

data class AncCapabilityState(
    val isPublicGattControlSupported: Boolean = false,
    val currentConfirmedMode: AncMode? = null,
    val supportedModes: List<AncMode> = emptyList(),
    val windNoiseReductionSupported: Boolean = false,
    val adaptiveAncSupported: Boolean = false,
    val statusExplanation: String = "No earbuds connected.",
    val requiresCompanionApp: Boolean = false
)

enum class TouchGestureType(val label: String) {
    SINGLE_TAP("Single Tap"),
    DOUBLE_TAP("Double Tap"),
    TRIPLE_TAP("Triple Tap"),
    LONG_PRESS("Long Press")
}

enum class TouchActionOption(val label: String) {
    PLAY_PAUSE("Play / Pause"),
    PREVIOUS_TRACK("Previous Track"),
    NEXT_TRACK("Next Track"),
    VOLUME_UP("Volume Up"),
    VOLUME_DOWN("Volume Down"),
    ANC_SWITCH("ANC Mode Switch"),
    VOICE_ASSISTANT("Voice Assistant"),
    UNAVAILABLE("Not Exposed via Public BLE")
}

data class TouchGestureConfig(
    val gesture: TouchGestureType,
    val isSupportedByPublicProtocol: Boolean,
    val currentAction: TouchActionOption,
    val limitationNote: String
)

data class EarbudsCapabilities(
    val brand: EarbudsBrand,
    val profileTitle: String,
    val isOnePlusNordBuds3Pro: Boolean = false,
    val supportsStandardBatteryService: Boolean = true,
    val supportsFastPairDetection: Boolean = false,
    val supportsAncOverPublicGatt: Boolean = false,
    val supportsTouchOverPublicGatt: Boolean = false,
    val supportsDualConnectionStatus: Boolean = false,
    val companionAppName: String? = null,
    val companionPackageNames: List<String> = emptyList(),
    val protocolSummary: String,
    val hardwareLimitations: String
)
