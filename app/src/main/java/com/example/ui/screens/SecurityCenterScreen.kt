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
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddModerator
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.audio.LocatorChannel
import com.example.bluetooth.BluetoothDashboardState
import com.example.bluetooth.PairedAudioDevice
import com.example.data.local.SecurityEventEntity
import com.example.data.local.TrustedDeviceEntity
import com.example.data.preferences.SecurityVaultState
import com.example.security.SecurityUiState
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.MetallicPillButton
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.SilverOutlinedButton
import com.example.ui.components.TelemetryBadge
import com.example.ui.theme.LocalLuxuryColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface PendingPinApproval {
    data class AddTrusted(val device: PairedAudioDevice) : PendingPinApproval
    data class RemoveTrusted(val device: TrustedDeviceEntity) : PendingPinApproval
    data class ToggleLostMode(val enable: Boolean, val note: String) : PendingPinApproval
    data object RotateRecoveryKey : PendingPinApproval
}

@Composable
fun SecurityCenterScreen(
    vaultState: SecurityVaultState,
    securityUi: SecurityUiState,
    bluetoothState: BluetoothDashboardState,
    trustedDevices: List<TrustedDeviceEntity>,
    securityEvents: List<SecurityEventEntity>,
    onRegisterOwner: (String, String, String, Int) -> Unit,
    onUnlockWithPin: (String) -> Unit,
    onTriggerBiometricUnlock: () -> Unit,
    onLockNow: () -> Unit,
    onSetLockSensitive: (Boolean) -> Unit,
    onSetBiometricEnabled: (Boolean) -> Unit,
    onSetUnauthorizedAlerts: (Boolean) -> Unit,
    onChangePin: (String?, Boolean, String, String, Int) -> Unit,
    onResetPinWithRecovery: (String, String, String, Int) -> Unit,
    onRotateRecoveryKey: (String?, Boolean) -> Unit,
    onAddTrustedDevice: (PairedAudioDevice, String?, Boolean) -> Unit,
    onRemoveTrustedDevice: (TrustedDeviceEntity, String?, Boolean) -> Unit,
    onTransferOwnership: (String, String?, Boolean) -> Unit,
    onToggleLostMode: (Boolean, String, String?, Boolean) -> Unit,
    onTriggerFindChime: (LocatorChannel) -> Unit,
    onDismissUnauthorizedBanner: () -> Unit,
    onClearDisplayedKeys: () -> Unit,
    onClearFeedback: () -> Unit,
    onClearSecurityHistory: () -> Unit,
    onRequestBiometricForApproval: ((Boolean) -> Unit) -> Unit
) {
    val lux = LocalLuxuryColors.current
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showForgotPinDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var pendingApproval by remember { mutableStateOf<PendingPinApproval?>(null) }
    var lostNoteInput by remember { mutableStateOf(vaultState.lostNote) }
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 780.dp)
                .fillMaxSize()
                .testTag("security_center_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SectionHeaderRow(
                    title = "Security Center",
                    subtitle = "Hardware-bound Owner Key, PBKDF2 PIN Lock, Recovery, Anti-Theft & Lost Mode",
                    trailing = {
                        if (vaultState.isOwnerRegistered && securityUi.isSecurityCenterUnlocked && vaultState.lockSensitiveSettingsWithPin) {
                            SilverOutlinedButton(
                                text = "Lock",
                                icon = Icons.Rounded.Lock,
                                onClick = onLockNow,
                                testTag = "security_lock_now_button"
                            )
                        }
                    }
                )
            }

            // Feedback Banner
            if (!securityUi.feedbackMessage.isNullOrBlank()) {
                item {
                    LiquidGlassCard(elevated = true) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = securityUi.feedbackMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (securityUi.isErrorFeedback) lux.statusCritical else lux.statusPositive,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onClearFeedback) {
                                Icon(Icons.Rounded.LockOpen, contentDescription = "Dismiss message")
                            }
                        }
                    }
                }
            }

            // Live Unauthorized Device Alert Banner
            securityUi.latestUnauthorizedAlert?.let { alert ->
                item {
                    LiquidGlassCard(elevated = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.GppBad, contentDescription = null, tint = lux.statusCritical)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "UNAUTHORIZED DEVICE ALERT",
                                style = MaterialTheme.typography.titleMedium,
                                color = lux.statusCritical
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${alert.deviceName ?: "Unknown Device"} (${alert.deviceAddress ?: "No MAC"}) connected at ${fmt.format(Date(alert.timestamp))}.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SilverOutlinedButton(
                            text = "Acknowledge Alert",
                            onClick = onDismissUnauthorizedBanner,
                            testTag = "dismiss_unauthorized_alert_button"
                        )
                    }
                }
            }

            // Step 1: Owner Registration if not registered yet
            if (!vaultState.isOwnerRegistered) {
                item {
                    OwnerRegistrationCard(onRegister = onRegisterOwner)
                }
            } else if (!securityUi.isSecurityCenterUnlocked && vaultState.lockSensitiveSettingsWithPin) {
                // Step 2: Owner Lock Gate when locked
                item {
                    OwnerLockGateCard(
                        pinLength = vaultState.pinLength,
                        lockoutRemainingSeconds = securityUi.lockoutRemainingSeconds,
                        biometricEnabled = vaultState.biometricUnlockEnabled,
                        biometricStatus = securityUi.biometricStatus,
                        onUnlockWithPin = onUnlockWithPin,
                        onTriggerBiometric = onTriggerBiometricUnlock,
                        onOpenForgotPin = { showForgotPinDialog = true }
                    )
                }
            } else {
                // Unlocked Full 9-Section Security Center

                // 1. Owner Profile
                item {
                    LiquidGlassCard(elevated = true) {
                        SectionHeaderRow(
                            title = "1. Owner Profile",
                            subtitle = "Cryptographically bound to ${vaultState.boundDeviceModel}",
                            trailing = {
                                TelemetryBadge(
                                    text = if (securityUi.isOriginalOwnerDevice) "ORIGINAL DEVICE" else "UNVERIFIED DEVICE",
                                    accentColor = if (securityUi.isOriginalOwnerDevice) lux.statusPositive else lux.statusWarning
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Owner: ${vaultState.ownerDisplayName}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Owner Key ID: ${vaultState.ownerKeyId} · Storage: ${if (vaultState.keystoreBacked) "Android Keystore AES-256-GCM" else "PBKDF2 / AES-GCM"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = lux.telemetryAccent
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SilverOutlinedButton(
                            text = "Transfer Ownership",
                            icon = Icons.Rounded.SwapHoriz,
                            onClick = { showTransferDialog = true },
                            testTag = "transfer_ownership_button"
                        )
                    }
                }

                // 2. Owner Lock + 3. Change PIN + 4. Forgot PIN + 5. Recovery Key
                item {
                    LiquidGlassCard {
                        SectionHeaderRow(
                            title = "2–5. Owner Lock, PIN & Recovery",
                            subtitle = "Salted PBKDF2-HMAC-SHA256 (${vaultState.pinLength}-Digit) · No Gmail",
                            trailing = {
                                TelemetryBadge(text = "${vaultState.pinLength}-DIGIT ACTIVE", accentColor = lux.statusPositive)
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Require PIN for Security Center", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Locks sensitive controls after session lock",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = vaultState.lockSensitiveSettingsWithPin,
                                onCheckedChange = onSetLockSensitive,
                                modifier = Modifier.testTag("switch_lock_security_center")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Fingerprint / BiometricPrompt", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = securityUi.biometricStatus.statusLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = vaultState.biometricUnlockEnabled,
                                onCheckedChange = onSetBiometricEnabled,
                                enabled = securityUi.biometricStatus.canAuthenticate,
                                modifier = Modifier.testTag("switch_biometric_auth")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SilverOutlinedButton(
                                text = "Change PIN",
                                icon = Icons.Rounded.Password,
                                onClick = { showChangePinDialog = true },
                                modifier = Modifier.weight(1f),
                                testTag = "open_change_pin_button"
                            )
                            SilverOutlinedButton(
                                text = "Forgot PIN",
                                icon = Icons.Rounded.Key,
                                onClick = { showForgotPinDialog = true },
                                modifier = Modifier.weight(1f),
                                testTag = "open_forgot_pin_button"
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SilverOutlinedButton(
                            text = "Rotate & Backup Recovery Key",
                            icon = Icons.Rounded.VerifiedUser,
                            onClick = { pendingApproval = PendingPinApproval.RotateRecoveryKey },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "rotate_recovery_key_button"
                        )
                    }
                }

                // 6. Trusted Devices
                item {
                    LiquidGlassCard {
                        SectionHeaderRow(
                            title = "6. Trusted Devices (${trustedDevices.size})",
                            subtitle = "Owner PIN or Biometric required to approve or remove"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (trustedDevices.isEmpty()) {
                            Text(
                                text = "No trusted earbuds registered yet. Approve a paired device below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            trustedDevices.forEach { td ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(td.deviceName, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "${td.brandName} · ${td.address} · Approved ${fmt.format(Date(td.addedAt))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { pendingApproval = PendingPinApproval.RemoveTrusted(td) },
                                        modifier = Modifier.testTag("remove_trusted_${td.address}")
                                    ) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Remove trusted device")
                                    }
                                }
                            }
                        }

                        val candidates = bluetoothState.pairedDevices.filter { paired ->
                            trustedDevices.none { it.address == paired.address }
                        }
                        if (candidates.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Available Paired Devices to Trust:", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            candidates.take(5).forEach { cand ->
                                SilverOutlinedButton(
                                    text = "Trust ${cand.name}",
                                    icon = Icons.Rounded.AddModerator,
                                    onClick = { pendingApproval = PendingPinApproval.AddTrusted(cand) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    testTag = "approve_trusted_${cand.address}"
                                )
                            }
                        }
                    }
                }

                // 7. Unauthorized Device Alerts
                item {
                    LiquidGlassCard {
                        SectionHeaderRow(
                            title = "7. Unauthorized Device Alerts",
                            subtitle = "Monitors Android Bluetooth connection events (ACL / A2DP / HFP)"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Alert on Unknown Connections", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Flags connected devices not present in Trusted Devices. Does not claim detection of passive nearby scans or failed pairings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = vaultState.unauthorizedDeviceAlertsEnabled,
                                onCheckedChange = onSetUnauthorizedAlerts,
                                modifier = Modifier.testTag("switch_unauthorized_alerts")
                            )
                        }
                    }
                }

                // 9. Lost Mode
                item {
                    val activeLost = vaultState.lostModeEnabled
                    LiquidGlassCard(elevated = true) {
                        SectionHeaderRow(
                            title = "9. Lost Mode",
                            subtitle = "Last known Bluetooth telemetry & recovery checklist (no fake GPS)",
                            trailing = {
                                TelemetryBadge(
                                    text = if (activeLost) "LOST MODE ACTIVE" else "STANDBY",
                                    accentColor = if (activeLost) lux.statusCritical else lux.statusPositive
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (activeLost) {
                            Text(
                                text = "Target: ${vaultState.lostTargetDeviceName ?: "OnePlus Buds 3 Pro"} (${vaultState.lostTargetDeviceAddress ?: "No MAC recorded"})\n" +
                                    "Activated: ${vaultState.lostModeActivatedAt?.let { fmt.format(Date(it)) } ?: "Now"}\n" +
                                    "Last Seen: ${vaultState.lostLastSeenAt?.let { fmt.format(Date(it)) } ?: "Check Device History"}\n" +
                                    "Last Battery: ${vaultState.lostLastBatterySummary ?: "Not exposed"}\n" +
                                    "Recovery Instructions:\n" +
                                    "1. Walk near your last connected area and watch for automatic Bluetooth reconnection alerts.\n" +
                                    "2. Trigger the Find Earbuds acoustic chime below if earbuds are out of the case and connected.\n" +
                                    "3. If your earbuds support Google Fast Pair Find My Device, open Google's Find My Device app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetallicPillButton(
                                    text = "Deactivate Lost Mode",
                                    icon = Icons.Rounded.Shield,
                                    onClick = {
                                        pendingApproval = PendingPinApproval.ToggleLostMode(false, "")
                                    },
                                    modifier = Modifier.weight(1f),
                                    testTag = "deactivate_lost_mode_button"
                                )
                                SilverOutlinedButton(
                                    text = "Ring Buds",
                                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                                    onClick = { onTriggerFindChime(LocatorChannel.BOTH) },
                                    modifier = Modifier.weight(1f),
                                    testTag = "lost_mode_ring_button"
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = lostNoteInput,
                                onValueChange = { lostNoteInput = it },
                                label = { Text("Optional Recovery Note (e.g. Left at Studio)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            MetallicPillButton(
                                text = "Activate Lost Mode",
                                icon = Icons.Rounded.ReportProblem,
                                onClick = {
                                    pendingApproval = PendingPinApproval.ToggleLostMode(true, lostNoteInput)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "activate_lost_mode_button"
                            )
                        }
                    }
                }

                // 8. Security History
                item {
                    SectionHeaderRow(
                        title = "8. Security Event History (${securityEvents.size})",
                        subtitle = "Tamper-evident local audit trail",
                        trailing = {
                            if (securityEvents.isNotEmpty()) {
                                SilverOutlinedButton(
                                    text = "Clear",
                                    onClick = onClearSecurityHistory,
                                    testTag = "clear_security_history_button"
                                )
                            }
                        }
                    )
                }

                if (securityEvents.isEmpty()) {
                    item {
                        LiquidGlassCard {
                            Text(
                                text = "No security events recorded yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(securityEvents, key = { it.id }) { ev ->
                        LiquidGlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = when (ev.severity) {
                                            "CRITICAL" -> lux.statusCritical
                                            "WARNING" -> lux.statusWarning
                                            else -> lux.telemetryAccent
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(ev.title, style = MaterialTheme.typography.titleSmall)
                                }
                                TelemetryBadge(
                                    text = ev.severity,
                                    accentColor = when (ev.severity) {
                                        "CRITICAL" -> lux.statusCritical
                                        "WARNING" -> lux.statusWarning
                                        else -> lux.telemetryAccent
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ev.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = fmt.format(Date(ev.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    securityUi.activeGeneratedRecoveryKeyForDisplay?.let { recKey ->
        RecoveryKeyRevealDialog(
            ownerKey = securityUi.activeGeneratedOwnerKeyForDisplay,
            recoveryKey = recKey,
            onDismiss = onClearDisplayedKeys
        )
    }

    if (showChangePinDialog) {
        ChangePinDialog(
            currentPinLength = vaultState.pinLength,
            onDismiss = { showChangePinDialog = false },
            onSubmit = { cur, newP, conf, len -> onChangePin(cur, false, newP, conf, len) }
        )
    }

    if (showForgotPinDialog) {
        ForgotPinNoGmailDialog(
            isOriginalOwnerDevice = securityUi.isOriginalOwnerDevice,
            boundDeviceModel = vaultState.boundDeviceModel.ifBlank { "Unregistered" },
            onDismiss = { showForgotPinDialog = false },
            onSubmitRecovery = onResetPinWithRecovery
        )
    }

    if (showTransferDialog) {
        TransferOwnershipDialog(
            pinLength = vaultState.pinLength,
            onDismiss = { showTransferDialog = false },
            onConfirmTransfer = { newName, pin -> onTransferOwnership(newName, pin, false) }
        )
    }

    pendingApproval?.let { action ->
        val (title, subtitle) = when (action) {
            is PendingPinApproval.AddTrusted ->
                "Approve Trusted Device" to "Verify Owner PIN to trust ${action.device.name}"
            is PendingPinApproval.RemoveTrusted ->
                "Remove Trusted Device" to "Verify Owner PIN to remove ${action.device.deviceName}"
            is PendingPinApproval.ToggleLostMode ->
                (if (action.enable) "Activate Lost Mode" else "Deactivate Lost Mode") to "Owner verification required"
            PendingPinApproval.RotateRecoveryKey ->
                "Rotate Recovery Key" to "Verify Owner PIN to generate a new replacement Recovery Key"
        }

        PinApprovalDialog(
            title = title,
            subtitle = subtitle,
            pinLength = vaultState.pinLength,
            biometricAvailable = vaultState.biometricUnlockEnabled && securityUi.biometricStatus.canAuthenticate,
            onDismiss = { pendingApproval = null },
            onApproveWithPin = { pin ->
                when (action) {
                    is PendingPinApproval.AddTrusted -> onAddTrustedDevice(action.device, pin, false)
                    is PendingPinApproval.RemoveTrusted -> onRemoveTrustedDevice(action.device, pin, false)
                    is PendingPinApproval.ToggleLostMode -> onToggleLostMode(action.enable, action.note, pin, false)
                    PendingPinApproval.RotateRecoveryKey -> onRotateRecoveryKey(pin, false)
                }
            },
            onApproveWithBiometric = {
                onRequestBiometricForApproval { ok ->
                    if (ok) {
                        when (action) {
                            is PendingPinApproval.AddTrusted -> onAddTrustedDevice(action.device, null, true)
                            is PendingPinApproval.RemoveTrusted -> onRemoveTrustedDevice(action.device, null, true)
                            is PendingPinApproval.ToggleLostMode -> onToggleLostMode(action.enable, action.note, null, true)
                            PendingPinApproval.RotateRecoveryKey -> onRotateRecoveryKey(null, true)
                        }
                    }
                }
            }
        )
    }
}
