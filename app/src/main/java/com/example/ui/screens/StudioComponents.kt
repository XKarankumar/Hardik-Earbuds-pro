package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.PlayerUiState
import com.example.equalizer.EqualizerCatalog
import com.example.equalizer.EqualizerUiState
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.MetallicPillButton
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.SilverOutlinedButton
import com.example.ui.components.TelemetryBadge
import com.example.ui.theme.LocalLuxuryColors
import java.util.Locale

@Composable
fun EqualizerCard(
    eqState: EqualizerUiState,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectPreset: (String) -> Unit,
    onUpdateBand: (Int, Float) -> Unit,
    onSaveCustomPreset: (String) -> Unit
) {
    val lux = LocalLuxuryColors.current
    var showSaveDialog by remember { mutableStateOf(false) }

    LiquidGlassCard(elevated = true, cornerRadius = 26.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("10-Band Precision Equalizer", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "60 Hz – 16 kHz Biquad IIR DSP · Preset: ${eqState.selectedPresetName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = lux.telemetryAccent
                )
            }
            Switch(
                checked = eqState.enabled,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier.testTag("eq_master_switch")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Frequency Response Curve Visualizer
        val lineColor = if (eqState.enabled) lux.telemetryAccent else MaterialTheme.colorScheme.outline
        val primaryColor = MaterialTheme.colorScheme.primary
        val zeroColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
        ) {
            val midY = size.height / 2f
            drawLine(zeroColor, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1.dp.toPx())
            val path = Path()
            val gains = eqState.bandGainsDb
            gains.forEachIndexed { idx, db ->
                val x = (idx.toFloat() / (gains.size - 1).coerceAtLeast(1)) * size.width
                val norm = (db.coerceIn(-12f, 12f) + 12f) / 24f
                val y = size.height - norm * size.height
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(listOf(lineColor, primaryColor)),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            gains.forEachIndexed { idx, db ->
                val x = (idx.toFloat() / (gains.size - 1).coerceAtLeast(1)) * size.width
                val norm = (db.coerceIn(-12f, 12f) + 12f) / 24f
                val y = size.height - norm * size.height
                drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Preset Chips (Built-in + Saved Custom)
        val allPresets = EqualizerCatalog.builtInPresets + eqState.customPresets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            allPresets.forEach { preset ->
                FilterChip(
                    selected = eqState.selectedPresetName == preset.name,
                    onClick = { onSelectPreset(preset.name) },
                    label = { Text(preset.name) },
                    modifier = Modifier.testTag("eq_preset_${preset.name.lowercase().replace(" ", "_")}")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 10-Band Frequency Sliders
        EqualizerCatalog.bands.forEach { band ->
            val gain = eqState.bandGainsDb.getOrElse(band.index) { 0f }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = band.label,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(62.dp)
                )
                Slider(
                    value = gain,
                    onValueChange = { onUpdateBand(band.index, it) },
                    valueRange = -12f..12f,
                    enabled = eqState.enabled,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("eq_band_slider_${band.frequencyHz}")
                )
                Text(
                    text = String.format(Locale.US, "%+.1fdB", gain),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(58.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SilverOutlinedButton(
                text = "Save Custom Preset",
                icon = Icons.Rounded.Save,
                onClick = { showSaveDialog = true },
                testTag = "save_eq_preset_button"
            )
            SilverOutlinedButton(
                text = "Reset Flat",
                onClick = { onSelectPreset("Normal") },
                testTag = "reset_eq_button"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = eqState.diagnostics.note +
                if (eqState.diagnostics.hardwareBandCount > 0) {
                    " Native AudioEffect centers: ${eqState.diagnostics.hardwareCenterFreqsHz.joinToString { "${it}Hz" }}."
                } else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showSaveDialog) {
        var presetName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Custom EQ Preset") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Preset Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveCustomPreset(presetName)
                        showSaveDialog = false
                    },
                    enabled = presetName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun MediaPlayerCard(
    playerState: PlayerUiState,
    onPickAudioFiles: () -> Unit,
    onPlayTrackAt: (Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    val lux = LocalLuxuryColors.current
    LiquidGlassCard(elevated = true) {
        SectionHeaderRow(
            title = "Media3 Local Audio Player",
            subtitle = "Background ExoPlayer + AudioFocus + 10-Band EQ DSP",
            trailing = {
                SilverOutlinedButton(
                    text = "Add Audio",
                    icon = Icons.Rounded.FolderOpen,
                    onClick = onPickAudioFiles,
                    testTag = "pick_audio_files_button"
                )
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        val currentTrack = playerState.currentTrack
        if (currentTrack == null) {
            Text(
                text = "No local audio files loaded. Tap 'Add Audio' to choose MP3, FLAC, WAV, AAC, or M4A tracks from your device via Android Storage Access Framework.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = currentTrack.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = currentTrack.artist,
                style = MaterialTheme.typography.bodySmall,
                color = lux.telemetryAccent
            )
            Spacer(modifier = Modifier.height(8.dp))

            val duration = playerState.durationMs.coerceAtLeast(1L)
            Slider(
                value = playerState.positionMs.coerceIn(0L, duration).toFloat(),
                onValueChange = { onSeekTo(it.toLong()) },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.testTag("player_seek_slider")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(playerState.positionMs), style = MaterialTheme.typography.labelSmall)
                Text(formatDuration(playerState.durationMs), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious, modifier = Modifier.testTag("player_prev_button")) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous track")
                }
                Spacer(modifier = Modifier.width(12.dp))
                MetallicPillButton(
                    text = if (playerState.isPlaying) "Pause" else "Play",
                    icon = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    onClick = onTogglePlayPause,
                    testTag = "player_play_pause_button"
                )
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(onClick = onNext, modifier = Modifier.testTag("player_next_button")) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next track")
                }
            }

            if (playerState.playlist.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Queue (${playerState.playlist.size} tracks)", style = MaterialTheme.typography.labelMedium)
                playerState.playlist.forEachIndexed { idx, track ->
                    TextButton(
                        onClick = { onPlayTrackAt(idx) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${if (idx == playerState.currentIndex) "▶ " else ""}${track.title}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Media volume")
            Spacer(modifier = Modifier.width(8.dp))
            Slider(
                value = playerState.volumeFraction,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                modifier = Modifier
                    .weight(1f)
                    .testTag("player_volume_slider")
            )
            Spacer(modifier = Modifier.width(8.dp))
            TelemetryBadge(text = "${(playerState.volumeFraction * 100).toInt()}%")
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%d:%02d", min, sec)
}
