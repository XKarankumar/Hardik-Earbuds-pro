package com.example.equalizer

import android.media.audiofx.Equalizer
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

data class EqBandSpec(
    val index: Int,
    val frequencyHz: Int,
    val label: String
)

data class EqPreset(
    val name: String,
    val gainsDb: List<Float>,
    val isBuiltIn: Boolean = true
)

data class HardwareEqDiagnostics(
    val audioSessionId: Int = 0,
    val hardwareAudioEffectAvailable: Boolean = false,
    val hardwareBandCount: Int = 0,
    val hardwareCenterFreqsHz: List<Int> = emptyList(),
    val hardwareRangeDb: Pair<Float, Float> = -12f to 12f,
    val note: String = "In-app 10-band Biquad DSP active on Media3 ExoPlayer audio pipeline."
)

data class EqualizerUiState(
    val enabled: Boolean = true,
    val selectedPresetName: String = "Normal",
    val bandGainsDb: List<Float> = List(10) { 0f },
    val customPresets: List<EqPreset> = emptyList(),
    val diagnostics: HardwareEqDiagnostics = HardwareEqDiagnostics()
)

object EqualizerCatalog {
    val bands: List<EqBandSpec> = listOf(
        EqBandSpec(0, 60, "60 Hz"),
        EqBandSpec(1, 120, "120 Hz"),
        EqBandSpec(2, 250, "250 Hz"),
        EqBandSpec(3, 500, "500 Hz"),
        EqBandSpec(4, 1000, "1 kHz"),
        EqBandSpec(5, 2000, "2 kHz"),
        EqBandSpec(6, 4000, "4 kHz"),
        EqBandSpec(7, 8000, "8 kHz"),
        EqBandSpec(8, 12000, "12 kHz"),
        EqBandSpec(9, 16000, "16 kHz")
    )

    val builtInPresets: List<EqPreset> = listOf(
        EqPreset("Normal", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("Bass Boost", listOf(6.0f, 5.0f, 3.5f, 1.5f, 0f, 0f, 0.5f, 1.0f, 1.0f, 0.5f)),
        EqPreset("Deep Bass", listOf(8.5f, 7.0f, 4.5f, 1.0f, -1.0f, 0f, 1.0f, 1.5f, 1.5f, 1.0f)),
        EqPreset("Rock", listOf(4.5f, 3.0f, 1.5f, -1.5f, -2.0f, 0.5f, 3.0f, 4.5f, 4.0f, 3.0f)),
        EqPreset("Pop", listOf(-1.0f, 1.5f, 3.5f, 4.0f, 2.5f, 0f, -1.0f, 1.5f, 2.5f, 2.0f)),
        EqPreset("Classical", listOf(3.0f, 2.5f, 1.5f, 0f, -1.0f, 0f, 1.5f, 3.0f, 4.0f, 3.5f)),
        EqPreset("Jazz", listOf(3.5f, 2.0f, 0.5f, 2.0f, -1.5f, 1.5f, 2.5f, 2.0f, 3.0f, 2.5f)),
        EqPreset("Vocal", listOf(-3.0f, -2.0f, 0f, 3.5f, 5.5f, 5.0f, 3.0f, 1.0f, -0.5f, -1.5f)),
        EqPreset("Treble Boost", listOf(-1.0f, -0.5f, 0f, 0f, 1.0f, 2.5f, 4.5f, 6.0f, 7.0f, 6.5f)),
        EqPreset("Custom", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
    )
}

/**
 * Genuine 10-band cascaded Biquad peaking EQ AudioProcessor for Jetpack Media3 ExoPlayer.
 * Processes PCM 16-bit audio in real time at 60, 120, 250, 500, 1k, 2k, 4k, 8k, 12k, 16k Hz.
 */
@OptIn(UnstableApi::class)
class TenBandBiquadAudioProcessor : BaseAudioProcessor() {

    private class BiquadState {
        @Volatile var b0 = 1.0
        @Volatile var b1 = 0.0
        @Volatile var b2 = 0.0
        @Volatile var a1 = 0.0
        @Volatile var a2 = 0.0
        var z1 = DoubleArray(8)
        var z2 = DoubleArray(8)
    }

    private val filters = Array(10) { BiquadState() }
    @Volatile var enabled: Boolean = true
    private var gainsDb = FloatArray(10) { 0f }
    private var sampleRateHz = 44100

    fun updateSettings(enabled: Boolean, newGainsDb: List<Float>) {
        this.enabled = enabled
        for (i in 0 until 10) {
            gainsDb[i] = (newGainsDb.getOrNull(i) ?: 0f).coerceIn(-12f, 12f)
        }
        recomputeAllCoefficients()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRateHz = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        recomputeAllCoefficients()
        return inputAudioFormat
    }

    override fun onFlush() {
        for (filter in filters) {
            filter.z1.fill(0.0)
            filter.z2.fill(0.0)
        }
    }

    override fun onReset() {
        onFlush()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        val channels = inputAudioFormat.channelCount.coerceIn(1, 8)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        if (!enabled || gainsDb.all { kotlin.math.abs(it) < 0.05f }) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val frameCount = remaining / (2 * channels)
        for (frame in 0 until frameCount) {
            for (ch in 0 until channels) {
                var sample = inputBuffer.short.toDouble() / 32768.0
                for (band in 0 until 10) {
                    val f = filters[band]
                    val out = f.b0 * sample + f.z1[ch]
                    f.z1[ch] = f.b1 * sample - f.a1 * out + f.z2[ch]
                    f.z2[ch] = f.b2 * sample - f.a2 * out
                    sample = out
                }
                val clamped = (sample * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
                outputBuffer.putShort(clamped)
            }
        }
        outputBuffer.flip()
    }

    private fun recomputeAllCoefficients() {
        val sr = sampleRateHz.toDouble()
        for (i in 0 until 10) {
            val fc = EqualizerCatalog.bands[i].frequencyHz.toDouble()
            val gainDb = gainsDb[i].toDouble()
            val f = filters[i]

            if (fc >= sr * 0.48 || kotlin.math.abs(gainDb) < 0.05) {
                f.b0 = 1.0
                f.b1 = 0.0
                f.b2 = 0.0
                f.a1 = 0.0
                f.a2 = 0.0
                continue
            }

            // Audio EQ Cookbook peaking filter
            val a = 10.0.pow(gainDb / 40.0)
            val omega = 2.0 * PI * fc / sr
            val sinW = sin(omega)
            val cosW = cos(omega)
            val q = 1.41
            val alpha = sinW / (2.0 * q)

            val b0 = 1.0 + alpha * a
            val b1 = -2.0 * cosW
            val b2 = 1.0 - alpha * a
            val a0 = 1.0 + alpha / a
            val a1 = -2.0 * cosW
            val a2 = 1.0 - alpha / a

            f.b0 = b0 / a0
            f.b1 = b1 / a0
            f.b2 = b2 / a0
            f.a1 = a1 / a0
            f.a2 = a2 / a0
        }
    }
}

/**
 * Coordinates the 10-band Media3 Biquad DSP processor and inspects Android AudioEffect.Equalizer
 * hardware band capabilities for transparent user diagnostics.
 */
object EqualizerController {
    @OptIn(UnstableApi::class)
    val audioProcessor = TenBandBiquadAudioProcessor()

    private var androidEqualizer: Equalizer? = null

    private val _state = MutableStateFlow(EqualizerUiState())
    val state: StateFlow<EqualizerUiState> = _state.asStateFlow()

    @OptIn(UnstableApi::class)
    fun applySettings(enabled: Boolean, presetName: String, gainsDb: List<Float>) {
        val normalized = if (gainsDb.size == 10) gainsDb else List(10) { 0f }
        audioProcessor.updateSettings(enabled, normalized)
        _state.update {
            it.copy(
                enabled = enabled,
                selectedPresetName = presetName,
                bandGainsDb = normalized
            )
        }
    }

    fun updateCustomPresets(presets: List<EqPreset>) {
        _state.update { it.copy(customPresets = presets) }
    }

    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) return
        releaseAudioEffect()
        runCatching {
            val eq = Equalizer(0, audioSessionId)
            val numBands = eq.numberOfBands.toInt().coerceAtLeast(0)
            val range = eq.bandLevelRange
            val minDb = (range.getOrNull(0) ?: -1500) / 100f
            val maxDb = (range.getOrNull(1) ?: 1500) / 100f
            val centers = (0 until numBands).map { b ->
                eq.getCenterFreq(b.toShort()) / 1000
            }
            // Keep Android AudioEffect flat so our precise 10-band Biquad processor doesn't double-boost
            eq.enabled = false
            androidEqualizer = eq

            _state.update {
                it.copy(
                    diagnostics = HardwareEqDiagnostics(
                        audioSessionId = audioSessionId,
                        hardwareAudioEffectAvailable = true,
                        hardwareBandCount = numBands,
                        hardwareCenterFreqsHz = centers,
                        hardwareRangeDb = minDb to maxDb,
                        note = "Active 10-band Biquad DSP runs in Media3 playback pipeline (60 Hz – 16 kHz). Android HAL reports $numBands native AudioEffect band(s). Processing applies to in-app Media3 playback only, not external apps."
                    )
                )
            }
        }.onFailure {
            _state.update {
                it.copy(
                    diagnostics = HardwareEqDiagnostics(
                        audioSessionId = audioSessionId,
                        hardwareAudioEffectAvailable = false,
                        note = "In-app 10-band Biquad DSP active on Media3 playback. Native AudioEffect inspection unavailable on this audio session."
                    )
                )
            }
        }
    }

    fun releaseAudioEffect() {
        runCatching { androidEqualizer?.release() }
        androidEqualizer = null
    }
}
