package com.example.integrations

import com.example.devices.AncCapabilityState
import com.example.devices.AncMode
import com.example.devices.EarbudsBrand
import com.example.devices.EarbudsCapabilities
import com.example.devices.TouchActionOption
import com.example.devices.TouchGestureConfig
import com.example.devices.TouchGestureType

interface EarbudsIntegration {
    val brand: EarbudsBrand
    fun matchesDevice(deviceName: String?, hasFastPairService: Boolean = false): Boolean
    fun describeCapabilities(deviceName: String?, hasBatteryGatt: Boolean, hasFastPair: Boolean): EarbudsCapabilities
    fun buildAncState(deviceName: String?, isConnected: Boolean, discoveredCustomControlUuid: Boolean): AncCapabilityState
    fun buildTouchConfigs(isConnected: Boolean, discoveredCustomControlUuid: Boolean): List<TouchGestureConfig>
}

object OnePlusNordBuds3ProIntegration : EarbudsIntegration {
    override val brand = EarbudsBrand.ONEPLUS

    private val nameKeywords = listOf(
        "oneplus nord buds 3 pro",
        "oneplus buds 3 pro",
        "nord buds 3 pro",
        "buds 3 pro",
        "nord buds 3",
        "nord buds",
        "oneplus buds"
    )

    override fun matchesDevice(deviceName: String?, hasFastPairService: Boolean): Boolean {
        val lower = deviceName?.lowercase().orEmpty()
        return nameKeywords.any { lower.contains(it) } || lower.startsWith("oneplus")
    }

    fun isExactNordBuds3Pro(deviceName: String?): Boolean {
        val lower = deviceName?.lowercase().orEmpty()
        return lower.contains("buds 3 pro")
    }

    override fun describeCapabilities(
        deviceName: String?,
        hasBatteryGatt: Boolean,
        hasFastPair: Boolean
    ): EarbudsCapabilities {
        val exact = isExactNordBuds3Pro(deviceName)
        return EarbudsCapabilities(
            brand = EarbudsBrand.ONEPLUS,
            profileTitle = if (exact) "OnePlus Nord Buds 3 Pro" else (deviceName ?: "OnePlus Nord Buds 3 Pro Profile"),
            isOnePlusNordBuds3Pro = exact || deviceName == null,
            supportsStandardBatteryService = hasBatteryGatt,
            supportsFastPairDetection = hasFastPair || exact,
            supportsAncOverPublicGatt = false,
            supportsTouchOverPublicGatt = false,
            supportsDualConnectionStatus = false,
            companionAppName = "HeyMelody",
            companionPackageNames = listOf("com.heymelody.android", "com.oplus.melody"),
            protocolSummary = "Bluetooth Classic (A2DP / HFP / AVRCP), Google Fast Pair (0xFE2C), and standard BLE GATT inspection. ANC, Transparency, BassWave, Dual Connection, and Touch remapping use proprietary Oplus/HeyMelody RFCOMM/GATT channels.",
            hardwareLimitations = "OnePlus Nord Buds 3 Pro firmware gates ANC levels (Smart/Max/Moderate/Mild), Transparency, Dual Connection toggling, and Touch Gesture writes behind authenticated HeyMelody/Oplus protocol frames. Standard third-party Android apps can read Bluetooth connection state, system/GATT battery telemetry where exposed, AudioEffect/Media3 EQ, and Android Spatializer, and can launch HeyMelody directly for firmware-authenticated controls."
        )
    }

    override fun buildAncState(
        deviceName: String?,
        isConnected: Boolean,
        discoveredCustomControlUuid: Boolean
    ): AncCapabilityState {
        if (!isConnected) {
            return AncCapabilityState(
                isPublicGattControlSupported = false,
                currentConfirmedMode = null,
                supportedModes = emptyList(),
                windNoiseReductionSupported = false,
                adaptiveAncSupported = false,
                statusExplanation = "Connect your OnePlus Nord Buds 3 Pro to inspect live Bluetooth & GATT capabilities.",
                requiresCompanionApp = true
            )
        }
        return AncCapabilityState(
            isPublicGattControlSupported = false,
            currentConfirmedMode = null,
            supportedModes = listOf(AncMode.ANC_ON, AncMode.TRANSPARENCY, AncMode.OFF, AncMode.ADAPTIVE),
            windNoiseReductionSupported = false,
            adaptiveAncSupported = false,
            statusExplanation = "Hardware capability detected: OnePlus Nord Buds 3 Pro supports 49dB Adaptive ANC, Transparency, and Wind Noise Reduction in hardware, but firmware does not confirm ANC commands over unauthenticated public BLE GATT. Use HeyMelody for confirmed ANC switching.",
            requiresCompanionApp = true
        )
    }

    override fun buildTouchConfigs(
        isConnected: Boolean,
        discoveredCustomControlUuid: Boolean
    ): List<TouchGestureConfig> {
        val note = if (isConnected) {
            "OnePlus Nord Buds 3 Pro touch mapping requires authenticated HeyMelody / Oplus firmware session."
        } else {
            "Connect OnePlus Nord Buds 3 Pro and open HeyMelody to customize hardware touch gestures."
        }
        return listOf(
            TouchGestureConfig(
                gesture = TouchGestureType.SINGLE_TAP,
                isSupportedByPublicProtocol = false,
                currentAction = TouchActionOption.PLAY_PAUSE,
                limitationNote = note
            ),
            TouchGestureConfig(
                gesture = TouchGestureType.DOUBLE_TAP,
                isSupportedByPublicProtocol = false,
                currentAction = TouchActionOption.NEXT_TRACK,
                limitationNote = note
            ),
            TouchGestureConfig(
                gesture = TouchGestureType.TRIPLE_TAP,
                isSupportedByPublicProtocol = false,
                currentAction = TouchActionOption.PREVIOUS_TRACK,
                limitationNote = note
            ),
            TouchGestureConfig(
                gesture = TouchGestureType.LONG_PRESS,
                isSupportedByPublicProtocol = false,
                currentAction = TouchActionOption.ANC_SWITCH,
                limitationNote = note
            )
        )
    }
}

class StandardBrandIntegration(
    override val brand: EarbudsBrand,
    private val keywords: List<String>,
    private val companionName: String?,
    private val companionPackages: List<String>,
    private val summary: String,
    private val limitations: String
) : EarbudsIntegration {

    override fun matchesDevice(deviceName: String?, hasFastPairService: Boolean): Boolean {
        val lower = deviceName?.lowercase().orEmpty()
        return keywords.any { lower.contains(it) }
    }

    override fun describeCapabilities(
        deviceName: String?,
        hasBatteryGatt: Boolean,
        hasFastPair: Boolean
    ): EarbudsCapabilities {
        return EarbudsCapabilities(
            brand = brand,
            profileTitle = deviceName ?: "${brand.displayName} Audio Device",
            isOnePlusNordBuds3Pro = false,
            supportsStandardBatteryService = hasBatteryGatt,
            supportsFastPairDetection = hasFastPair,
            supportsAncOverPublicGatt = false,
            supportsTouchOverPublicGatt = false,
            supportsDualConnectionStatus = false,
            companionAppName = companionName,
            companionPackageNames = companionPackages,
            protocolSummary = summary,
            hardwareLimitations = limitations
        )
    }

    override fun buildAncState(
        deviceName: String?,
        isConnected: Boolean,
        discoveredCustomControlUuid: Boolean
    ): AncCapabilityState {
        return AncCapabilityState(
            isPublicGattControlSupported = false,
            currentConfirmedMode = null,
            supportedModes = emptyList(),
            windNoiseReductionSupported = false,
            adaptiveAncSupported = false,
            statusExplanation = if (isConnected) {
                "${brand.displayName} earbuds do not expose public unauthenticated BLE GATT ANC controls on Android. ${companionName?.let { "Open $it to manage hardware ANC." } ?: ""}"
            } else {
                "Connect your ${brand.displayName} earbuds to inspect active profiles and GATT services."
            },
            requiresCompanionApp = companionName != null
        )
    }

    override fun buildTouchConfigs(
        isConnected: Boolean,
        discoveredCustomControlUuid: Boolean
    ): List<TouchGestureConfig> {
        val note = companionName?.let {
            "Gesture remapping is managed via $it (not exposed via public Bluetooth SIG GATT)."
        } ?: "Device does not expose public touch gesture configuration over standard Android Bluetooth APIs."

        return TouchGestureType.entries.map { gesture ->
            TouchGestureConfig(
                gesture = gesture,
                isSupportedByPublicProtocol = false,
                currentAction = TouchActionOption.UNAVAILABLE,
                limitationNote = note
            )
        }
    }
}

object EarbudsIntegrationRegistry {

    private val brandIntegrations: List<EarbudsIntegration> = listOf(
        OnePlusNordBuds3ProIntegration,
        StandardBrandIntegration(
            brand = EarbudsBrand.SAMSUNG,
            keywords = listOf("galaxy buds", "samsung"),
            companionName = "Galaxy Wearable",
            companionPackages = listOf("com.samsung.android.app.watchmanager"),
            summary = "A2DP / HFP / AVRCP, Samsung Scalable Codec (proprietary), and standard Battery/Device Info telemetry.",
            limitations = "360 Audio, ANC, Ambient Sound, and Touchpad configuration require Galaxy Wearable."
        ),
        StandardBrandIntegration(
            brand = EarbudsBrand.SONY,
            keywords = listOf("wf-1000", "wh-1000", "linkbuds", "sony"),
            companionName = "Sony Sound Connect",
            companionPackages = listOf("com.sony.songpal.mdr"),
            summary = "A2DP / LDAC / HFP / Fast Pair, with standard Android Spatializer & HFP battery reporting.",
            limitations = "Sony DSEE Extreme, Adaptive Sound Control, and hardware ANC levels require Sony Sound Connect."
        ),
        StandardBrandIntegration(
            brand = EarbudsBrand.JBL,
            keywords = listOf("jbl", "tune", "live pro", "tour pro"),
            companionName = "JBL Headphones",
            companionPackages = listOf("jbl.stc.com"),
            summary = "A2DP / HFP / Fast Pair and standard BLE GATT service discovery.",
            limitations = "Ambient Aware, TalkThru, and custom JBL gesture maps require JBL Headphones app."
        ),
        StandardBrandIntegration(
            brand = EarbudsBrand.BOAT,
            keywords = listOf("boat", "airdopes", "nirvana"),
            companionName = "boAt Hearables",
            companionPackages = listOf("com.boat.hearables"),
            summary = "Bluetooth A2DP / HFP / AVRCP audio routing with HFP battery reporting where supported by firmware.",
            limitations = "ENx, BEAST mode, and custom touch settings require boAt Hearables on supported models."
        ),
        StandardBrandIntegration(
            brand = EarbudsBrand.REALME,
            keywords = listOf("realme", "buds air", "buds t"),
            companionName = "realme Link",
            companionPackages = listOf("com.realme.link"),
            summary = "A2DP / HFP / Fast Pair and standard Bluetooth SIG GATT discovery.",
            limitations = "ANC strength, Golden Sound, and touch customization require realme Link."
        ),
        StandardBrandIntegration(
            brand = EarbudsBrand.NOTHING,
            keywords = listOf("nothing", "ear (1)", "ear (2)", "ear (a)", "cmf buds"),
            companionName = "Nothing X",
            companionPackages = listOf("com.nothing.smartcenter"),
            summary = "A2DP / LHDC / Fast Pair and standard BLE GATT inspection.",
            limitations = "Pinch/touch controls, Low Lag mode, and ANC levels require Nothing X."
        )
    )

    private val genericIntegration = StandardBrandIntegration(
        brand = EarbudsBrand.GENERIC,
        keywords = emptyList(),
        companionName = null,
        companionPackages = emptyList(),
        summary = "Standard Android Bluetooth Classic (A2DP, HFP, AVRCP) & BLE GATT (0x180F Battery, 0x180A Device Info).",
        limitations = "Generic Bluetooth audio devices do not support vendor ANC or touch remapping over public Android APIs."
    )

    fun resolve(deviceName: String?, hasFastPairService: Boolean = false): EarbudsIntegration {
        return brandIntegrations.firstOrNull { it.matchesDevice(deviceName, hasFastPairService) }
            ?: genericIntegration
    }

    fun allSupportedBrands(): List<EarbudsBrand> = EarbudsBrand.entries
}
