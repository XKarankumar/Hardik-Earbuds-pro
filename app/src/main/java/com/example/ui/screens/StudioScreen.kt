package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.audio.PlayerUiState
import com.example.audio.SpatialAudioStatus
import com.example.bluetooth.BluetoothDashboardState
import com.example.equalizer.EqualizerUiState
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.MetallicPillButton
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.SilverOutlinedButton
import com.example.ui.components.TelemetryBadge
import com.example.ui.theme.LocalLuxuryColors

@Composable
fun StudioScreen(
    bluetoothState: BluetoothDashboardState,
    equalizerState: EqualizerUiState,
    playerState: PlayerUiState,
    spatialState: SpatialAudioStatus,
    onToggleEq: (Boolean) -> Unit,
    onSelectPreset: (String) -> Unit,
    onUpdateEqBand: (Int, Float) -> Unit,
    onSaveCustomPreset: (String) -> Unit,
    onPickAudioFiles: () -> Unit,
    onPlayTrackAt: (Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onRefreshSpatial: () -> Unit,
    onOpenSoundSettings: () -> Unit,
    onOpenCompanionApp: (String, List<String>) -> Unit
) {
    val lux = LocalLuxuryColors.current
    val caps = bluetoothState.activeCapabilities
    val ancState = bluetoothState.ancState

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 780.dp)
                .fillMaxSize()
                .testTag("studio_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SectionHeaderRow(
                    title = "Acoustic Studio",
                    subtitle = "10-Band DSP Equalizer, Media3 Player, Spatializer, ANC & Touch Matrix"
                )
            }

            item {
                MediaPlayerCard(
                    playerState = playerState,
                    onPickAudioFiles = onPickAudioFiles,
                    onPlayTrackAt = onPlayTrackAt,
                    onTogglePlayPause = onTogglePlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSeekTo = onSeekTo,
                    onVolumeChange = onVolumeChange
                )
            }

            item {
                EqualizerCard(
                    eqState = equalizerState,
                    onToggleEnabled = onToggleEnabled@{ enabled -> onToggleEq(enabled) },
                    onSelectPreset = onSelectPreset,
                    onUpdateBand = onUpdateEqBand,
                    onSaveCustomPreset = onSaveCustomPreset
                )
            }

            // Spatial Audio Hardware Detection Card
            item {
                LiquidGlassCard {
                    SectionHeaderRow(
                        title = "Android Spatial Audio",
                        subtitle = "Real system Spatializer & Head Tracking telemetry",
                        trailing = {
                            SilverOutlinedButton(
                                text = "Refresh",
                                icon = Icons.Rounded.Refresh,
                                onClick = onRefreshSpatial,
                                testTag = "refresh_spatial_button"
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TelemetryBadge(
                            text = if (spatialState.isEnabled) "SPATIAL ON" else if (spatialState.isAvailable) "AVAILABLE" else "UNAVAILABLE",
                            accentColor = if (spatialState.isEnabled) lux.statusPositive else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TelemetryBadge(
                            text = if (spatialState.isHeadTrackerAvailable) "HEAD TRACKER YES" else "HEAD TRACKER NO",
                            accentColor = if (spatialState.isHeadTrackerAvailable) lux.telemetryAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Route: ${spatialState.activeAudioRouteSummary} · Level: ${spatialState.immersiveLevelLabel} · 5.1 Spatializable: ${if (spatialState.canSpatializeMultiChannel51) "Yes" else "No"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = spatialState.statusExplanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SilverOutlinedButton(
                        text = "Open System Sound Settings",
                        icon = Icons.Rounded.Settings,
                        onClick = onOpenSoundSettings,
                        testTag = "open_sound_settings_button"
                    )
                }
            }

            // ANC & Transparency Hardware Capability Card
            item {
                LiquidGlassCard {
                    SectionHeaderRow(
                        title = "ANC, Transparency & Wind Reduction",
                        subtitle = "Real-time hardware capability & protocol verification",
                        trailing = {
                            TelemetryBadge(
                                text = if (ancState.isPublicGattControlSupported) "GATT CONTROL" else "FIRMWARE GATED"
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = ancState.statusExplanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val compName = caps?.companionAppName ?: "HeyMelody"
                    val compPkgs = caps?.companionPackageNames ?: listOf("com.heymelody.android", "com.oplus.melody")
                    if (ancState.requiresCompanionApp || bluetoothState.activeDevice == null) {
                        MetallicPillButton(
                            text = "Open $compName for Confirmed ANC",
                            icon = Icons.Rounded.OpenInNew,
                            onClick = { onOpenCompanionApp(compName, compPkgs) },
                            testTag = "studio_open_companion_anc"
                        )
                    }
                }
            }

            // Touch Controls Matrix
            item {
                LiquidGlassCard {
                    SectionHeaderRow(
                        title = "Touch Control Customization",
                        subtitle = "Only device-supported public protocol controls are editable"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val touchList = bluetoothState.touchConfigs
                    if (touchList.isEmpty()) {
                        Text(
                            text = "Connect compatible earbuds to inspect gesture protocol capabilities.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        touchList.forEach { slot ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .let { it },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(slot.gesture.label, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = slot.limitationNote,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TelemetryBadge(
                                    text = if (slot.isSupportedByPublicProtocol) slot.currentAction.label else "UNAVAILABLE",
                                    accentColor = if (slot.isSupportedByPublicProtocol) lux.statusPositive else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}
