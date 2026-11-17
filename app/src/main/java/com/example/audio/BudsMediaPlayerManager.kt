package com.example.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LocalAudioTrack(
    val uriString: String,
    val title: String,
    val artist: String,
    val durationMs: Long
)

data class PlayerUiState(
    val playlist: List<LocalAudioTrack> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volumeFraction: Float = 0.7f,
    val isServiceConnected: Boolean = false
) {
    val currentTrack: LocalAudioTrack?
        get() = playlist.getOrNull(currentIndex)
}

class BudsMediaPlayerManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(Dispatchers.Main)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(
        PlayerUiState(volumeFraction = readSystemVolumeFraction())
    )
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    fun connect() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, BudsPlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future

        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    mediaController = controller
                    controller.addListener(playerListener)
                    syncFromController(controller)
                    startProgressPolling()
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun disconnect() {
        progressJob?.cancel()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }

    fun addLocalAudioUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val newTracks = uris.mapNotNull { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                extractTrackMetadata(uri)
            }
            if (newTracks.isEmpty()) return@launch

            scope.launch(Dispatchers.Main) {
                val controller = mediaController ?: return@launch
                val existing = _state.value.playlist
                val combined = existing + newTracks.filter { nt -> existing.none { it.uriString == nt.uriString } }
                val mediaItems = combined.map { track ->
                    MediaItem.Builder()
                        .setMediaId(track.uriString)
                        .setUri(Uri.parse(track.uriString))
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.artist)
                                .build()
                        )
                        .build()
                }
                val wasEmpty = existing.isEmpty()
                controller.setMediaItems(mediaItems, if (wasEmpty) 0 else controller.currentMediaItemIndex, if (wasEmpty) 0L else controller.currentPosition)
                controller.prepare()
                if (wasEmpty) controller.play()
                _state.update {
                    it.copy(
                        playlist = combined,
                        currentIndex = controller.currentMediaItemIndex.coerceAtLeast(0)
                    )
                }
            }
        }
    }

    fun playTrackAt(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(index)
            controller.play()
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE && controller.mediaItemCount > 0) {
                controller.prepare()
            }
            controller.play()
        }
    }

    fun nextTrack() {
        mediaController?.let {
            if (it.hasNextMediaItem()) it.seekToNextMediaItem()
        }
    }

    fun previousTrack() {
        mediaController?.let {
            if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0)
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs.coerceAtLeast(0L))
        _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun setVolumeFraction(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val target = (clamped * maxVol).toInt().coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        _state.update { it.copy(volumeFraction = clamped) }
    }

    fun refreshVolume() {
        _state.update { it.copy(volumeFraction = readSystemVolumeFraction()) }
    }

    private fun readSystemVolumeFraction(): Float {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (current.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            mediaController?.let(::syncFromController)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaController?.let(::syncFromController)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            mediaController?.let(::syncFromController)
        }
    }

    private fun syncFromController(controller: MediaController) {
        _state.update {
            it.copy(
                isServiceConnected = true,
                isPlaying = controller.isPlaying,
                currentIndex = controller.currentMediaItemIndex,
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = controller.duration.coerceAtLeast(0L),
                volumeFraction = readSystemVolumeFraction()
            )
        }
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaController?.let { c ->
                    if (c.isPlaying || c.playbackState == Player.STATE_READY) {
                        _state.update {
                            it.copy(
                                positionMs = c.currentPosition.coerceAtLeast(0L),
                                durationMs = c.duration.coerceAtLeast(0L),
                                isPlaying = c.isPlaying,
                                currentIndex = c.currentMediaItemIndex
                            )
                        }
                    }
                }
                delay(400L)
            }
        }
    }

    private fun extractTrackMetadata(uri: Uri): LocalAudioTrack {
        var displayName = "Local Audio Track"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    displayName = cursor.getString(idx) ?: displayName
                }
            }
        }

        var title: String? = null
        var artist: String? = null
        var duration = 0L

        val mmr = MediaMetadataRetriever()
        runCatching {
            mmr.setDataSource(context, uri)
            title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
        runCatching { mmr.release() }

        return LocalAudioTrack(
            uriString = uri.toString(),
            title = title?.takeIf { it.isNotBlank() } ?: displayName.substringBeforeLast('.'),
            artist = artist?.takeIf { it.isNotBlank() } ?: "Local File",
            durationMs = duration
        )
    }
}
