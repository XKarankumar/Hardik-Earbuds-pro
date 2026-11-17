package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.security.BiometricCapabilityStatus
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.MetallicPillButton
import com.example.ui.components.SilverOutlinedButton
import com.example.ui.components.TelemetryBadge
import com.example.ui.theme.LocalLuxuryColors

@Composable
fun OwnerRegistrationCard(
    onRegister: (displayName: String, pin: String, confirmPin: String, pinLength: Int) -> Unit
) {
    var ownerName by remember { mutableStateOf("Hardik Kumar") }
    var pinLength by remember { mutableIntStateOf(4) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    LiquidGlassCard(elevated = true, cornerRadius = 28.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = LocalLuxuryColors.current.telemetryAccent)
            Spacer(modifier = Modifier.padding(4.dp))
            Text("Register Primary Owner Device", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Bind this Android phone as Primary Owner with an Android Keystore cryptographic Owner Key, salted PBKDF2 PIN, and offline Recovery Key (no Gmail required).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = ownerName,
            onValueChange = { ownerName = it },
            label = { Text("Owner Display Name") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("owner_reg_name_input")
        )
        Spacer(modifier = Modifier.height(10.dp))

        Text("PIN Length", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(4, 6).forEach { len ->
                FilterChip(
                    selected = pinLength == len,
                    onClick = {
                        pinLength = len
                        pin = pin.take(len)
                        confirmPin = confirmPin.take(len)
                    },
                    label = { Text("$len-Digit PIN") },
                    modifier = Modifier.testTag("reg_pin_length_$len")
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { v -> pin = v.filter { it.isDigit() }.take(pinLength) },
            label = { Text("Create $pinLength-Digit PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("owner_reg_pin_input")
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = confirmPin,
            onValueChange = { v -> confirmPin = v.filter { it.isDigit() }.take(pinLength) },
            label = { Text("Confirm $pinLength-Digit PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("owner_reg_confirm_pin_input")
        )

        Spacer(modifier = Modifier.height(14.dp))
        MetallicPillButton(
            text = "Register Owner & Generate Keys",
            icon = Icons.Rounded.Key,
            onClick = { onRegister(ownerName, pin, confirmPin, pinLength) },
            modifier = Modifier.fillMaxWidth(),
            testTag = "owner_register_submit_button"
        )
    }
}

@Composable
fun OwnerLockGateCard(
    pinLength: Int,
    lockoutRemainingSeconds: Long,
    biometricEnabled: Boolean,
    biometricStatus: BiometricCapabilityStatus,
    onUnlockWithPin: (String) -> Unit,
    onTriggerBiometric: () -> Unit,
    onOpenForgotPin: () -> Unit
) {
    val lux = LocalLuxuryColors.current
    var pinInput by remember { mutableStateOf("") }
    val lockedOut = lockoutRemainingSeconds > 0L

    LiquidGlassCard(elevated = true, cornerRadius = 28.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = lux.telemetryAccent)
                Spacer(modifier = Modifier.padding(4.dp))
                Text("Owner Security Lock", style = MaterialTheme.typography.titleLarge)
            }
            TelemetryBadge(text = "$pinLength-DIGIT PIN")
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (lockedOut) {
                "Temporary lockout active after repeated incorrect attempts. Try again in ${lockoutRemainingSeconds}s or use Recovery Key."
            } else {
                "Verify Owner identity to unlock Owner Profile, Trusted Devices, Recovery Key, and Lost Mode controls."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (lockedOut) lux.statusCritical else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = pinInput,
            onValueChange = { v -> pinInput = v.filter { it.isDigit() }.take(pinLength) },
            label = { Text("Enter $pinLength-Digit Owner PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            enabled = !lockedOut,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lock_gate_pin_input")
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetallicPillButton(
                text = if (lockedOut) "Locked (${lockoutRemainingSeconds}s)" else "Unlock",
                icon = Icons.Rounded.Lock,
                enabled = !lockedOut && pinInput.length == pinLength,
                onClick = {
                    val attempt = pinInput
                    pinInput = ""
                    onUnlockWithPin(attempt)
                },
                modifier = Modifier.weight(1f),
                testTag = "lock_gate_unlock_button"
            )
            if (biometricEnabled && biometricStatus.canAuthenticate) {
                SilverOutlinedButton(
                    text = "Biometric",
                    icon = Icons.Rounded.Fingerprint,
                    onClick = onTriggerBiometric,
                    modifier = Modifier.weight(1f),
                    testTag = "lock_gate_biometric_button"
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        SilverOutlinedButton(
            text = "Forgot PIN? Verify with Recovery Key (No Gmail)",
            icon = Icons.Rounded.Key,
            onClick = onOpenForgotPin,
            modifier = Modifier.fillMaxWidth(),
            testTag = "lock_gate_forgot_pin_button"
        )
    }
}

@Composable
fun ChangePinDialog(
    currentPinLength: Int,
    onDismiss: () -> Unit,
    onSubmit: (currentPin: String, newPin: String, confirmPin: String, newLength: Int) -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var newLength by remember { mutableIntStateOf(currentPinLength) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Owner PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { currentPin = it.filter { c -> c.isDigit() }.take(currentPinLength) },
                    label = { Text("Current $currentPinLength-Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.testTag("change_pin_current_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(4, 6).forEach { len ->
                        FilterChip(
                            selected = newLength == len,
                            onClick = {
                                newLength = len
                                newPin = newPin.take(len)
                                confirmPin = confirmPin.take(len)
                            },
                            label = { Text("$len-Digit") }
                        )
                    }
                }
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter { c -> c.isDigit() }.take(newLength) },
                    label = { Text("New $newLength-Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.testTag("change_pin_new_input")
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter { c -> c.isDigit() }.take(newLength) },
                    label = { Text("Confirm New PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.testTag("change_pin_confirm_input")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(currentPin, newPin, confirmPin, newLength)
                    onDismiss()
                },
                modifier = Modifier.testTag("change_pin_submit_button")
            ) { Text("Update PIN") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ForgotPinNoGmailDialog(
    isOriginalOwnerDevice: Boolean,
    boundDeviceModel: String,
    onDismiss: () -> Unit,
    onSubmitRecovery: (recoveryKey: String, newPin: String, confirmPin: String, newLength: Int) -> Unit
) {
    var recoveryKey by remember { mutableStateOf("") }
    var newLength by remember { mutableIntStateOf(4) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forgot PIN — Cryptographic Recovery") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "No Gmail or email verification is used. Verify your offline Recovery Key to set a new PIN and rotate your Recovery Key.",
                    style = MaterialTheme.typography.bodySmall
                )
                TelemetryBadge(
                    text = if (isOriginalOwnerDevice) {
                        "Original Device Verified ($boundDeviceModel)"
                    } else {
                        "Device Mismatch (Expected: $boundDeviceModel)"
                    }
                )
                OutlinedTextField(
                    value = recoveryKey,
                    onValueChange = { recoveryKey = it.uppercase() },
                    label = { Text("Recovery Key (XXXX-XXXX-...)") },
                    singleLine = true,
                    modifier = Modifier.testTag("forgot_pin_recovery_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(4, 6).forEach { len ->
                        FilterChip(
                            selected = newLength == len,
                            onClick = {
                                newLength = len
                                newPin = newPin.take(len)
                                confirmPin = confirmPin.take(len)
                            },
                            label = { Text("$len-Digit") }
                        )
                    }
                }
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter { c -> c.isDigit() }.take(newLength) },
                    label = { Text("New $newLength-Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.testTag("forgot_pin_new_input")
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter { c -> c.isDigit() }.take(newLength) },
                    label = { Text("Confirm New PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.testTag("forgot_pin_confirm_input")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmitRecovery(recoveryKey, newPin, confirmPin, newLength)
                    onDismiss()
                },
                modifier = Modifier.testTag("forgot_pin_submit_button")
            ) { Text("Verify & Reset") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RecoveryKeyRevealDialog(
    ownerKey: String?,
    recoveryKey: String,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup Cryptographic Keys") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Store your Recovery Key securely offline. It is hashed with PBKDF2-HMAC-SHA256 and cannot be retrieved in plain text after you close this dialog.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (!ownerKey.isNullOrBlank()) {
                    Text("Owner Key:", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = ownerKey,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Text("Recovery Key (No-Gmail PIN Reset):", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = recoveryKey,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.testTag("revealed_recovery_key_text")
                )
                SilverOutlinedButton(
                    text = if (copied) "Copied to Clipboard" else "Copy Recovery Key",
                    icon = Icons.Rounded.ContentCopy,
                    onClick = {
                        clipboard.setText(AnnotatedString(recoveryKey))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("recovery_dialog_close_button")
            ) { Text("I Saved It") }
        }
    )
}

@Composable
fun PinApprovalDialog(
    title: String,
    subtitle: String,
    pinLength: Int,
    biometricAvailable: Boolean,
    onDismiss: () -> Unit,
    onApproveWithPin: (String) -> Unit,
    onApproveWithBiometric: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(pinLength) },
                    label = { Text("$pinLength-Digit Owner PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.testTag("approval_pin_input")
                )
                if (biometricAvailable) {
                    SilverOutlinedButton(
                        text = "Verify with Fingerprint / Biometric",
                        icon = Icons.Rounded.Fingerprint,
                        onClick = {
                            onDismiss()
                            onApproveWithBiometric()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApproveWithPin(pin)
                    onDismiss()
                },
                enabled = pin.length == pinLength,
                modifier = Modifier.testTag("approval_pin_confirm_button")
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TransferOwnershipDialog(
    pinLength: Int,
    onDismiss: () -> Unit,
    onConfirmTransfer: (newOwnerName: String, ownerPin: String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transfer Ownership") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Generates a new Owner Key & Replacement Recovery Key bound to the new owner.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Owner Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(pinLength) },
                    label = { Text("Verify Current $pinLength-Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmTransfer(newName, pin)
                    onDismiss()
                },
                enabled = newName.isNotBlank() && pin.length == pinLength
            ) { Text("Transfer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
