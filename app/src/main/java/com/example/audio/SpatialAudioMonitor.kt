package com.example.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.Spatializer
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SpatialAudioStatus(
    val apiSupported: Boolean = false,
    val immersiveLevelLabel: String = "Unsupported on Android < 12L",
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val isHeadTrackerAvailable: Boolean = false,
    val canSpatializeMultiChannel51: Boolean = false,
    val activeAudioRouteSummary: String = "Speaker / Default",
    val statusExplanation: String = "Checking Android Spatializer capabilities..."
)

class SpatialAudioMonitor(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _status = MutableStateFlow(SpatialAudioStatus())
    val status: StateFlow<SpatialAudioStatus> = _status.asStateFlow()

    private var spatializerListener: Any? = null
    private var headTrackerListener: Any? = null

    fun start() {
        refresh()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            val spatializer = runCatching { audioManager.spatializer }.getOrNull() ?: return
            val stateListener = object : Spatializer.OnSpatializerStateChangedListener {
                override fun onSpatializerEnabledChanged(spat: Spatializer, enabled: Boolean) {
                    refresh()
                }

                override fun onSpatializerAvailableChanged(spat: Spatializer, available: Boolean) {
                    refresh()
                }
            }
            spatializerListener = stateListener
            runCatching { spatializer.addOnSpatializerStateChangedListener(context.mainExecutor, stateListener) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val htListener = Spatializer.OnHeadTrackerAvailableListener { _, _ -> refresh() }
                headTrackerListener = htListener
                runCatching { spatializer.addOnHeadTrackerAvailableListener(context.mainExecutor, htListener) }
            }
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            val spatializer = runCatching { audioManager.spatializer }.getOrNull() ?: return
            (spatializerListener as? Spatializer.OnSpatializerStateChangedListener)?.let {
                runCatching { spatializer.removeOnSpatializerStateChangedListener(it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                (headTrackerListener as? Spatializer.OnHeadTrackerAvailableListener)?.let {
                    runCatching { spatializer.removeOnHeadTrackerAvailableListener(it) }
                }
            }
        }
    }

    fun refresh() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val btOutput = devices.firstOrNull {
            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER
        }
        val routeLabel = if (btOutput != null) {
            "${btOutput.productName ?: "Bluetooth Audio"} (${if (btOutput.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET) "BLE Audio" else "A2DP"})"
        } else {
            "Device Speaker / Wired"
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2) {
            _status.value = SpatialAudioStatus(
                apiSupported = false,
                immersiveLevelLabel = "Requires Android 12L (API 32+)",
                isAvailable = false,
                isEnabled = false,
                isHeadTrackerAvailable = false,
                canSpatializeMultiChannel51 = false,
                activeAudioRouteSummary = routeLabel,
                statusExplanation = "System Spatializer hardware telemetry requires Android 12L / Android 13 or newer. Your device is running Android ${Build.VERSION.RELEASE}."
            )
            return
        }

        val spatializer = runCatching { audioManager.spatializer }.getOrNull()
        if (spatializer == null) {
            _status.value = SpatialAudioStatus(
                apiSupported = false,
                immersiveLevelLabel = "Unavailable",
                activeAudioRouteSummary = routeLabel,
                statusExplanation = "No Android Spatializer service reported by system AudioManager."
            )
            return
        }

        val level = spatializer.immersiveAudioLevel
        val levelLabel = when (level) {
            Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_MULTICHANNEL -> "Multichannel Spatializer"
            Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE -> "None (Not Supported by Device OEM)"
            else -> "OEM Spatializer (Level $level)"
        }

        val available = spatializer.isAvailable
        val enabled = spatializer.isEnabled
        val headTracker = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            spatializer.isHeadTrackerAvailable
        } else false

        val canSpatialize51 = runCatching {
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val fmt = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_5POINT1)
                .build()
            spatializer.canBeSpatialized(attr, fmt)
        }.getOrDefault(false)

        val explanation = when {
            level == Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE ->
                "This Android device's audio HAL does not include a system Spatializer effect. No Dolby Atmos or virtual spatial claims are simulated."
            !available ->
                "System Spatializer exists, but is currently unavailable for the active audio route ($routeLabel). Connect compatible wireless earbuds."
            !enabled ->
                "Spatializer is supported for $routeLabel, but currently toggled OFF in Android System Settings."
            else ->
                "Spatializer is ACTIVE on $routeLabel. Multi-channel 5.1 media tracks are eligible for spatial rendering."
        }

        _status.value = SpatialAudioStatus(
            apiSupported = true,
            immersiveLevelLabel = levelLabel,
            isAvailable = available,
            isEnabled = enabled,
            isHeadTrackerAvailable = headTracker,
            canSpatializeMultiChannel51 = canSpatialize51,
            activeAudioRouteSummary = routeLabel,
            statusExplanation = explanation
        )
    }

    fun openSystemSoundSettings() {
        val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
