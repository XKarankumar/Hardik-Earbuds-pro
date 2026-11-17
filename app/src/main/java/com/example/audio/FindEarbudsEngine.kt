package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

enum class LocatorChannel(val label: String) {
    BOTH("Both Earbuds"),
    LEFT("Left Only"),
    RIGHT("Right Only")
}

class FindEarbudsEngine {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var playJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activeChannel = MutableStateFlow(LocatorChannel.BOTH)
    val activeChannel: StateFlow<LocatorChannel> = _activeChannel.asStateFlow()

    fun startLocatorTone(channel: LocatorChannel) {
        stop()
        _activeChannel.value = channel
        _isPlaying.value = true

        playJob = scope.launch {
            val sampleRate = 44100
            val bufferSamples = sampleRate / 2 // 0.5 sec chunk
            val minBufBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(bufferSamples * 4)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBufBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val leftGain = if (channel == LocatorChannel.RIGHT) 0f else 1f
            val rightGain = if (channel == LocatorChannel.LEFT) 0f else 1f
            @Suppress("DEPRECATION")
            track.setStereoVolume(leftGain, rightGain)

            runCatching {
                track.play()
                val pcm = ShortArray(bufferSamples * 2)
                var sampleIndex = 0L
                val maxDurationSamples = sampleRate * 20L // auto-stop after 20s safety limit

                while (isActive && sampleIndex < maxDurationSamples) {
                    for (i in 0 until bufferSamples) {
                        val tSec = (sampleIndex + i).toDouble() / sampleRate
                        val cyclePos = tSec % 1.0
                        // Pulsed dual-chirp locator tone: audible chirp during first 0.35s of each 1.0s cycle
                        val envelope = if (cyclePos < 0.35) {
                            sin(PI * (cyclePos / 0.35))
                        } else 0.0
                        val carrier = 0.65 * sin(2.0 * PI * 2600.0 * tSec) +
                            0.35 * sin(2.0 * PI * 3400.0 * tSec)
                        val sample = (carrier * envelope * 26000.0).toInt().toShort()
                        pcm[i * 2] = if (channel == LocatorChannel.RIGHT) 0 else sample
                        pcm[i * 2 + 1] = if (channel == LocatorChannel.LEFT) 0 else sample
                    }
                    track.write(pcm, 0, pcm.size)
                    sampleIndex += bufferSamples
                }
            }
            runCatching {
                track.stop()
                track.release()
            }
            _isPlaying.value = false
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        _isPlaying.value = false
    }
}
