package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.LocatorChannel
import com.example.audio.PlayerUiState
import com.example.audio.SpatialAudioStatus
import com.example.bluetooth.BluetoothDashboardState
import com.example.core.AppConstants
import com.example.core.PermissionStatusSnapshot
import com.example.devices.ConnectionState
import com.example.equalizer.EqualizerUiState
import com.example.ui.components.BatteryRingGauge
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.MetallicDivider
import com.example.ui.components.MetallicPillButton
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.SilverOutlinedButton
import com.example.ui.components.StatusDot
import com.example.ui.components.TelemetryBadge
import com.example.ui.theme.LocalLuxuryColors

@Composable
fun DashboardScreen(
    bluetoothState: BluetoothDashboardState,
    permissions: PermissionStatusSnapshot,
    equalizerState: EqualizerUiState,
    spatialState: SpatialAudioStatus,
    playerState: PlayerUiState,
    isLocatorPlaying: Boolean,
    locatorChannel: LocatorChannel,
    onRequestPermissions: () -> Unit,
    onShowPopup: () -> Unit,
    onPreviewExternalPopup: () -> Unit,
    onFastPair: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenCompanionApp: (String, List<String>) -> Unit,
    onToggleFindEarbuds: (LocatorChannel) -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToStudio: () -> Unit,
    onNavigateToBattery: () -> Unit
) {
    val lux = LocalLuxuryColors.current
    val active = bluetoothState.activeDevice
    val caps = bluetoothState.activeCapabilities
    val connState = bluetoothState.connectionState

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 780.dp)
                .fillMaxSize()
                .testTag("dashboard_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Editorial Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = AppConstants.APP_NAME,
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${AppConstants.APP_VERSION} · Acoustic Companion",
                            style = MaterialTheme.typography.labelMedium,
                            color = lux.telemetryAccent
                        )
                    }
                    TelemetryBadge(
                        text = connState.label.uppercase(),
                        accentColor = when (connState) {
                            ConnectionState.CONNECTED -> lux.statusPositive
                            ConnectionState.CONNECTING -> lux.statusWarning
                            ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Permission Banner if Bluetooth runtime permission is missing
            if (!permissions.hasBluetoothCorePermissions) {
                item {
                    LiquidGlassCard(elevated = true) {
                        Text(
                            text = "Bluetooth Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = lux.statusWarning
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Grant Bluetooth Scan & Connect permissions so HARDIK BUDS PRO can read real paired earbuds, BLE GATT battery telemetry, and Fast Pair beacons.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        MetallicPillButton(
                            text = "Grant Bluetooth Access",
                            icon = Icons.Rounded.Bluetooth,
                            onClick = onRequestPermissions,
                            testTag = "grant_bluetooth_button"
                        )
                    }
                }
            }

            // Hero Luxury Earbuds Card
            item {
                LiquidGlassCard(elevated = true, cornerRadius = 28.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            StatusDot(
                                color = when (connState) {
                                    ConnectionState.CONNECTED -> lux.statusPositive
                                    ConnectionState.CONNECTING -> lux.statusWarning
                                    ConnectionState.DISCONNECTED -> lux.statusCritical
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = active?.name ?: "OnePlus Nord Buds 3 Pro Profile",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (active?.supportsFastPair == true) {
                            TelemetryBadge(text = "FAST PAIR")
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            !bluetoothState.isBluetoothSupported -> "Bluetooth hardware is unavailable on this device."
                            !bluetoothState.isBluetoothEnabled -> "Bluetooth is turned off. Enable Bluetooth to connect your earbuds."
                            active != null -> "${active.brand.displayName} · A2DP ${if (active.isA2dpConnected) "Active" else "Standby"} · HFP ${if (active.isHeadsetConnected) "Active" else "Standby"}"
                            else -> "Ready for pairing & BLE telemetry. Connect earbuds in Bluetooth Settings or Companion Pair."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF08090C))
                    ) {
                        Image(
                            painter = painterResource(
                                id = if (connState == ConnectionState.CONNECTED) {
                                    R.drawable.img_buds_hero
                                } else {
                                    R.drawable.img_buds_case_closed
                                }
                            ),
                            contentDescription = stringResource(R.string.cd_buds_hero),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val battery = active?.batteryTelemetry
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        BatteryRingGauge(
                            label = "Left",
                            percent = battery?.leftPercent ?: battery?.overallPercent,
                            isCharging = battery?.isLeftCharging
                        )
                        BatteryRingGauge(
                            label = "Right",
                            percent = battery?.rightPercent ?: battery?.overallPercent,
                            isCharging = battery?.isRightCharging
                        )
                        BatteryRingGauge(
                            label = "Case",
                            percent = battery?.casePercent,
                            isCharging = battery?.isCaseCharging
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (battery?.hasAnyBatteryValue == true) {
                            "Source: ${battery.source.label}"
                        } else {
                            "Battery values show \"--\" whenever hardware does not expose standard GATT/HFP telemetry."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetallicPillButton(
                            text = "Connection Popup",
                            icon = Icons.Rounded.Headphones,
                            onClick = onShowPopup,
                            modifier = Modifier.weight(1f),
                            testTag = "open_popup_button"
                        )
                        SilverOutlinedButton(
                            text = if (active != null) "BT Settings" else "Pair Nearby",
                            icon = if (active != null) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothSearching,
                            onClick = if (active != null) onOpenBluetoothSettings else onFastPair,
                            modifier = Modifier.weight(1f),
                            testTag = "hero_pair_button"
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    SilverOutlinedButton(
                        text = "Test System Notification / Overlay Popup",
                        icon = Icons.Rounded.NotificationsActive,
                        onClick = onPreviewExternalPopup,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "preview_external_popup_button"
                    )
                }
            }

            // OnePlus Nord Buds 3 Pro Dedicated Profile & Hardware Truthfulness Card
            item {
                val activeCaps = caps
                LiquidGlassCard {
                    SectionHeaderRow(
                        title = "OnePlus Nord Buds 3 Pro Integration",
                        subtitle = "Dedicated flagship profile & firmware boundary report",
                        trailing = {
                            TelemetryBadge(
                                text = if (activeCaps?.isOnePlusNordBuds3Pro == true && active != null) {
                                    "ACTIVE"
                                } else "PROFILE READY"
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = activeCaps?.protocolSummary
                            ?: "Supports A2DP/HFP connection monitoring, BLE GATT 0x180F/0x180A inspection, Google Fast Pair (0xFE2C), and direct HeyMelody handoff.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = activeCaps?.hardwareLimitations
                            ?: "ANC modes, Transparency, Dual Connection, and Touch remaps require authenticated HeyMelody firmware protocol.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val compName = activeCaps?.companionAppName ?: "HeyMelody"
                        val compPkgs = activeCaps?.companionPackageNames
                            ?: listOf("com.heymelody.android", "com.oplus.melody")
                        MetallicPillButton(
                            text = "Open $compName",
                            icon = Icons.Rounded.OpenInNew,
                            onClick = { onOpenCompanionApp(compName, compPkgs) },
                            modifier = Modifier.weight(1f),
                            testTag = "open_heymelody_button"
                        )
                        SilverOutlinedButton(
                            text = "GATT & Devices",
                            icon = Icons.Rounded.Tune,
                            onClick = onNavigateToDevices,
                            modifier = Modifier.weight(1f),
                            testTag = "goto_devices_button"
                        )
                    }
                }
            }

            // Quick Telemetry Grid: EQ, Spatial Audio, ANC & Touch
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiquidGlassCard(
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToStudio
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Equalizer,
                            contentDescription = null,
                            tint = lux.telemetryAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("10-Band EQ", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${equalizerState.selectedPresetName} · ${if (equalizerState.enabled) "DSP On" else "Bypassed"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LiquidGlassCard(
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToStudio
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SurroundSound,
                            contentDescription = null,
                            tint = lux.telemetryAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Spatializer", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (spatialState.isEnabled) "Active" else if (spatialState.isAvailable) "Ready (Off)" else "Not Active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiquidGlassCard(
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToStudio
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("ANC & Ambient", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (bluetoothState.ancState.isPublicGattControlSupported) "GATT Supported" else "OEM Companion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LiquidGlassCard(
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToBattery
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Battery Logs", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = playerState.currentTrack?.title ?: "History & Alerts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            // Find My Earbuds (Real AudioTrack Stereo Chime)
            item {
                LiquidGlassCard {
                    SectionHeaderRow(
                        title = "Find Earbuds (Acoustic Chime)",
                        subtitle = "Plays a high-frequency 2.6/3.4 kHz locator pulse to connected earbuds"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Safety Notice: Remove earbuds from your ears before triggering the locator chime.",
                        style = MaterialTheme.typography.labelSmall,
                        color = lux.statusWarning
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LocatorChannel.entries.forEach { channel ->
                            val isThisPlaying = isLocatorPlaying && locatorChannel == channel
                            if (isThisPlaying) {
                                MetallicPillButton(
                                    text = "Stop ${channel.label.substringBefore(" ")}",
                                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                                    onClick = { onToggleFindEarbuds(channel) },
                                    modifier = Modifier.weight(1f),
                                    testTag = "find_buds_${channel.name.lowercase()}"
                                )
                            } else {
                                SilverOutlinedButton(
                                    text = channel.label.substringBefore(" "),
                                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                                    onClick = { onToggleFindEarbuds(channel) },
                                    modifier = Modifier.weight(1f),
                                    testTag = "find_buds_${channel.name.lowercase()}"
                                )
                            }
                        }
                    }
                }
            }

            item {
                MetallicDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${AppConstants.APP_NAME} · ${AppConstants.COPYRIGHT_NOTICE}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
