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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.bluetooth.BluetoothDashboardState
import com.example.core.PermissionStatusSnapshot
import com.example.data.local.DeviceHistoryEntity
import com.example.data.local.DeviceProfileEntity
import com.example.devices.EarbudsBrand
import com.example.integrations.EarbudsIntegrationRegistry
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.MetallicPillButton
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.SilverOutlinedButton
import com.example.ui.components.TelemetryBadge
import com.example.ui.theme.LocalLuxuryColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DevicesScreen(
    bluetoothState: BluetoothDashboardState,
    permissions: PermissionStatusSnapshot,
    deviceHistory: List<DeviceHistoryEntity>,
    deviceProfiles: List<DeviceProfileEntity>,
    onRequestPermissions: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onLaunchFastPair: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onBondDevice: (String) -> Unit,
    onInspectGatt: (String) -> Unit,
    onCloseGatt: () -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onSaveProfile: (String, EarbudsBrand, String?, String, Boolean, String) -> Unit,
    onDeleteProfile: (Int) -> Unit
) {
    val lux = LocalLuxuryColors.current
    var showProfileDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 780.dp)
                .fillMaxSize()
                .testTag("devices_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SectionHeaderRow(
                    title = "Bluetooth & Fast Pair Hub",
                    subtitle = "Paired audio devices, BLE scanner, live GATT services, and profiles"
                )
            }

            if (!permissions.hasBluetoothCorePermissions) {
                item {
                    LiquidGlassCard(elevated = true) {
                        Text(
                            text = "Permissions Needed for Device Discovery",
                            style = MaterialTheme.typography.titleMedium,
                            color = lux.statusWarning
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Grant Bluetooth Scan & Connect permissions to enumerate paired earbuds, inspect BLE GATT services, and discover Fast Pair devices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        MetallicPillButton(
                            text = "Allow Bluetooth Permissions",
                            icon = Icons.Rounded.Bluetooth,
                            onClick = onRequestPermissions,
                            testTag = "devices_request_perm_button"
                        )
                    }
                }
            }

            // Google Fast Pair & Nearby Pairing Guidance Card
            item {
                LiquidGlassCard(elevated = true) {
                    SectionHeaderRow(
                        title = "Google Fast Pair & Companion Pairing",
                        subtitle = "Official Android CompanionDeviceManager + System Bluetooth Pairing",
                        trailing = { TelemetryBadge(text = "0xFE2C") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Open your earbuds case near your phone in pairing mode.\n" +
                            "2. Tap 'Pair Nearby' to trigger Android's CompanionDeviceManager picker, or open Android Bluetooth Settings for native Google Fast Pair.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetallicPillButton(
                            text = "Pair Nearby",
                            icon = Icons.Rounded.BluetoothConnected,
                            onClick = onLaunchFastPair,
                            modifier = Modifier.weight(1f),
                            testTag = "devices_fast_pair_button"
                        )
                        SilverOutlinedButton(
                            text = "BT Settings",
                            icon = Icons.Rounded.OpenInNew,
                            onClick = onOpenBluetoothSettings,
                            modifier = Modifier.weight(1f),
                            testTag = "devices_bt_settings_button"
                        )
                    }
                }
            }

            // Paired Devices
            item {
                SectionHeaderRow(
                    title = "Paired Devices (${bluetoothState.pairedDevices.size})",
                    subtitle = "Tap Inspect GATT to read live 0x180F Battery & 0x180A Firmware"
                )
            }

            if (bluetoothState.pairedDevices.isEmpty()) {
                item {
                    LiquidGlassCard {
                        Text(
                            text = if (!bluetoothState.isBluetoothEnabled) {
                                "Bluetooth is disabled. Enable Bluetooth to view paired earbuds."
                            } else {
                                "No paired Bluetooth devices found yet. Use Pair Nearby or BT Settings above."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(bluetoothState.pairedDevices, key = { it.address }) { dev ->
                    LiquidGlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dev.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${dev.brand.displayName} · ${dev.address}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TelemetryBadge(
                                text = if (dev.isConnected) "CONNECTED" else "PAIRED",
                                accentColor = if (dev.isConnected) lux.statusPositive else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!dev.firmwareRevision.isNullOrBlank() || !dev.modelNumber.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = listOfNotNull(
                                    dev.manufacturerName?.let { "Mfg: $it" },
                                    dev.modelNumber?.let { "Model: $it" },
                                    dev.firmwareRevision?.let { "FW: $it" }
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = lux.telemetryAccent
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SilverOutlinedButton(
                                text = "Inspect GATT",
                                icon = Icons.Rounded.Memory,
                                onClick = { onInspectGatt(dev.address) },
                                modifier = Modifier.weight(1f)
                            )
                            SilverOutlinedButton(
                                text = "Reconnect",
                                icon = Icons.Rounded.Refresh,
                                onClick = onOpenBluetoothSettings,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Live BLE Scanner
            item {
                SectionHeaderRow(
                    title = "Nearby BLE & Fast Pair Scanner",
                    subtitle = bluetoothState.scanStatusMessage ?: "Scan nearby BLE beacons",
                    trailing = {
                        if (bluetoothState.isScanning) {
                            SilverOutlinedButton(
                                text = "Stop",
                                icon = Icons.Rounded.Stop,
                                onClick = onStopScan,
                                testTag = "stop_ble_scan_button"
                            )
                        } else {
                            MetallicPillButton(
                                text = "Scan BLE",
                                icon = Icons.Rounded.BluetoothSearching,
                                onClick = onStartScan,
                                testTag = "start_ble_scan_button"
                            )
                        }
                    }
                )
            }

            if (bluetoothState.scannedBleDevices.isEmpty()) {
                item {
                    LiquidGlassCard {
                        Text(
                            text = if (bluetoothState.isScanning) {
                                "Listening for nearby Bluetooth Low Energy advertisements..."
                            } else {
                                "Tap Scan BLE to discover nearby earbuds and Google Fast Pair (0xFE2C) advertisements."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(bluetoothState.scannedBleDevices, key = { it.address }) { ble ->
                    LiquidGlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ble.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${ble.brandHint.displayName} · ${ble.address} · ${ble.rssi} dBm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (ble.isFastPairAdvertised) {
                                TelemetryBadge(
                                    text = ble.fastPairModelIdHex?.let { "FP #$it" } ?: "FAST PAIR"
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SilverOutlinedButton(
                                text = "Pair / Bond",
                                icon = Icons.Rounded.Bluetooth,
                                onClick = { onBondDevice(ble.address) },
                                modifier = Modifier.weight(1f)
                            )
                            SilverOutlinedButton(
                                text = "Inspect GATT",
                                icon = Icons.Rounded.Memory,
                                onClick = { onInspectGatt(ble.address) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Live BLE GATT Service & Characteristic Inspector
            item {
                val gatt = bluetoothState.gattInspection
                LiquidGlassCard(elevated = true) {
                    SectionHeaderRow(
                        title = "BLE GATT Inspector",
                        subtitle = gatt.targetName?.let { "$it (${gatt.targetAddress})" } ?: "No active GATT target",
                        trailing = {
                            if (gatt.isConnected || gatt.isConnectingOrDiscovering) {
                                SilverOutlinedButton(
                                    text = "Disconnect",
                                    onClick = onCloseGatt,
                                    testTag = "gatt_disconnect_button"
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = gatt.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = lux.telemetryAccent
                    )
                    if (gatt.services.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        gatt.services.forEach { svc ->
                            Text(
                                text = "${svc.label} · ${svc.uuid}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            svc.characteristics.forEach { ch ->
                                Text(
                                    text = "  • ${ch.label} [${ch.propertiesSummary}]" +
                                        (ch.decodedValue?.let { " → $it" } ?: "") +
                                        (ch.rawHex?.let { " ($it)" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            // Device Profiles & Brand Architecture
            item {
                SectionHeaderRow(
                    title = "Custom Device Profiles (${deviceProfiles.size})",
                    subtitle = "Multi-brand profiles (${EarbudsIntegrationRegistry.allSupportedBrands().joinToString { it.displayName }})",
                    trailing = {
                        SilverOutlinedButton(
                            text = "New",
                            icon = Icons.Rounded.Add,
                            onClick = { showProfileDialog = true },
                            testTag = "add_device_profile_button"
                        )
                    }
                )
            }

            items(deviceProfiles, key = { it.id }) { profile ->
                LiquidGlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.profileName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${profile.brandName} · EQ: ${profile.preferredEqPreset} · Spatial: ${if (profile.spatialAudioPreferred) "Preferred" else "Off"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (profile.notes.isNotBlank()) {
                                Text(
                                    text = profile.notes,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = lux.telemetryAccent
                                )
                            }
                        }
                        IconButton(onClick = { onDeleteProfile(profile.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete profile")
                        }
                    }
                }
            }

            // Device Connection History
            item {
                SectionHeaderRow(
                    title = "Device Connection History (${deviceHistory.size})",
                    subtitle = "Stored locally via Room database"
                )
            }

            if (deviceHistory.isEmpty()) {
                item {
                    LiquidGlassCard {
                        Text(
                            text = "No device connection history recorded yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(deviceHistory, key = { it.address }) { item ->
                    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
                    LiquidGlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${item.brandName} · Connected ${item.connectionCount}x · Last: ${fmt.format(Date(item.lastConnectedAt))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onToggleFavorite(item.address, !item.isFavorite) }) {
                                Icon(
                                    imageVector = if (item.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = "Favorite device",
                                    tint = if (item.isFavorite) lux.telemetryAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        CreateDeviceProfileDialog(
            defaultAddress = bluetoothState.activeDevice?.address,
            defaultBrand = bluetoothState.activeDevice?.brand ?: EarbudsBrand.ONEPLUS,
            onDismiss = { showProfileDialog = false },
            onSave = { name, brand, eqPreset, notes ->
                onSaveProfile(name, brand, bluetoothState.activeDevice?.address, eqPreset, true, notes)
                showProfileDialog = false
            }
        )
    }
}

@Composable
private fun CreateDeviceProfileDialog(
    defaultAddress: String?,
    defaultBrand: EarbudsBrand,
    onDismiss: () -> Unit,
    onSave: (String, EarbudsBrand, String, String) -> Unit
) {
    var name by remember { mutableStateOf("OnePlus Nord Buds 3 Pro") }
    var eqPreset by remember { mutableStateOf("Deep Bass") }
    var notes by remember { mutableStateOf(defaultAddress?.let { "MAC: $it" } ?: "Daily Commute") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Earbuds Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = eqPreset,
                    onValueChange = { eqPreset = it },
                    label = { Text("Preferred EQ Preset") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (${defaultBrand.displayName})") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, defaultBrand, eqPreset, notes) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
