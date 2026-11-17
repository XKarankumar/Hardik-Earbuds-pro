package com.example.security

import android.content.Context
import com.example.bluetooth.PairedAudioDevice
import com.example.data.local.SecurityEventEntity
import com.example.data.local.TrustedDeviceEntity
import com.example.data.preferences.SecurityPreferencesRepository
import com.example.data.preferences.SecurityVaultState
import com.example.data.repository.BudsRepository
import com.example.notifications.BudsNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SecurityUiState(
    val isSecurityCenterUnlocked: Boolean = false,
    val isOriginalOwnerDevice: Boolean = false,
    val biometricStatus: BiometricCapabilityStatus = BiometricCapabilityStatus(false, "Checking..."),
    val lockoutRemainingSeconds: Long = 0L,
    val activeGeneratedRecoveryKeyForDisplay: String? = null,
    val activeGeneratedOwnerKeyForDisplay: String? = null,
    val latestUnauthorizedAlert: SecurityEventEntity? = null,
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
)

sealed interface PinVerificationResult {
    data object Success : PinVerificationResult
    data class Invalid(val remainingAttemptsBeforeLockout: Int) : PinVerificationResult
    data class LockedOut(val remainingSeconds: Long) : PinVerificationResult
}

class SecurityCenterManager(
    private val context: Context,
    private val securityPrefs: SecurityPreferencesRepository,
    private val repository: BudsRepository,
    private val scope: CoroutineScope
) {
    val vaultState: StateFlow<SecurityVaultState> = securityPrefs.vaultFlow
        .stateIn(scope, SharingStarted.Eagerly, SecurityVaultState())

    val trustedDevices: StateFlow<List<TrustedDeviceEntity>> = repository.trustedDevices
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val securityEvents: StateFlow<List<SecurityEventEntity>> = repository.securityEvents
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unauthorizedAlerts: StateFlow<List<SecurityEventEntity>> = repository.unauthorizedAlerts
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(
        SecurityUiState(
            biometricStatus = BiometricAuthHelper.checkAvailability(context)
        )
    )
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            securityPrefs.vaultFlow.collect { vault ->
                val isBound = if (vault.isOwnerRegistered) {
                    SecurityCryptoManager.isCurrentDeviceBoundOwner(context, vault.boundDeviceFingerprintHash)
                } else false

                val unlockedByDefault = !vault.isOwnerRegistered || !vault.lockSensitiveSettingsWithPin
                _uiState.update { current ->
                    current.copy(
                        isOriginalOwnerDevice = isBound,
                        isSecurityCenterUnlocked = if (!vault.isOwnerRegistered || !vault.lockSensitiveSettingsWithPin) {
                            true
                        } else {
                            current.isSecurityCenterUnlocked
                        }
                    )
                }
            }
        }

        // Real-time lockout countdown ticker
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                val lockoutUntil = vaultState.value.lockoutUntilMillis
                val remaining = ((lockoutUntil - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                if (_uiState.value.lockoutRemainingSeconds != remaining) {
                    _uiState.update { it.copy(lockoutRemainingSeconds = remaining) }
                }
                delay(1000L)
            }
        }
    }

    fun refreshCapabilities() {
        val bio = BiometricAuthHelper.checkAvailability(context)
        val vault = vaultState.value
        val bound = if (vault.isOwnerRegistered) {
            SecurityCryptoManager.isCurrentDeviceBoundOwner(context, vault.boundDeviceFingerprintHash)
        } else false
        _uiState.update { it.copy(biometricStatus = bio, isOriginalOwnerDevice = bound) }
    }

    fun lockSecurityCenter() {
        if (vaultState.value.isOwnerRegistered && vaultState.value.lockSensitiveSettingsWithPin) {
            _uiState.update { it.copy(isSecurityCenterUnlocked = false) }
        }
    }

    fun markBiometricAuthenticated() {
        scope.launch {
            securityPrefs.clearPinFailures()
            repository.logSecurityEvent(
                eventType = "BIOMETRIC_VERIFIED",
                title = "Biometric Owner Verification Succeeded",
                description = "Owner verified identity via Android BiometricPrompt.",
                severity = "INFO"
            )
            _uiState.update {
                it.copy(
                    isSecurityCenterUnlocked = true,
                    feedbackMessage = "Biometric verification succeeded.",
                    isErrorFeedback = false
                )
            }
        }
    }

    fun clearDisplayedKeys() {
        _uiState.update {
            it.copy(
                activeGeneratedRecoveryKeyForDisplay = null,
                activeGeneratedOwnerKeyForDisplay = null
            )
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null, isErrorFeedback = false) }
    }

    fun registerOwner(
        displayName: String,
        pin: String,
        confirmPin: String,
        pinLength: Int,
        initialPrimaryDevice: PairedAudioDevice?
    ) {
        val cleanName = displayName.trim()
        if (cleanName.isBlank()) {
            setFeedback("Enter an Owner Name before registering.", isError = true)
            return
        }
        if (!SecurityCryptoManager.isPinFormatValid(pin, pinLength)) {
            setFeedback("Owner PIN must be exactly $pinLength numeric digits.", isError = true)
            return
        }
        if (pin != confirmPin) {
            setFeedback("PIN and Confirm PIN do not match.", isError = true)
            return
        }

        scope.launch {
            val artifact = SecurityCryptoManager.generateOwnerRegistration(context)
            val pinHash = SecurityCryptoManager.hashSecret(pin)

            securityPrefs.saveOwnerRegistration(
                displayName = cleanName,
                ownerKeyId = artifact.ownerKeyId,
                encryptedOwnerSecretBase64 = artifact.encryptedOwnerSecretBase64,
                boundDeviceModel = artifact.boundDeviceModel,
                boundDeviceFingerprintHash = artifact.boundDeviceFingerprintHash,
                keystoreBacked = artifact.keystoreBacked,
                pinHashBase64 = pinHash.hashBase64,
                pinSaltBase64 = pinHash.saltBase64,
                pinLength = pinLength,
                recoveryHashBase64 = artifact.recoveryHash.hashBase64,
                recoverySaltBase64 = artifact.recoveryHash.saltBase64
            )

            if (initialPrimaryDevice != null) {
                repository.upsertTrustedDevice(
                    TrustedDeviceEntity(
                        address = initialPrimaryDevice.address,
                        deviceName = initialPrimaryDevice.name,
                        brandName = initialPrimaryDevice.brand.displayName,
                        isPrimaryBuds = true,
                        ownerKeyId = artifact.ownerKeyId,
                        notes = "Registered at Owner Setup"
                    )
                )
            }

            repository.logSecurityEvent(
                eventType = "OWNER_REGISTERED",
                title = "Primary Owner Registered",
                description = "Owner '$cleanName' bound to ${artifact.boundDeviceModel} (Key ID: ${artifact.ownerKeyId}).",
                severity = "INFO"
            )

            _uiState.update {
                it.copy(
                    isSecurityCenterUnlocked = true,
                    isOriginalOwnerDevice = true,
                    activeGeneratedOwnerKeyForDisplay = artifact.rawOwnerKeyForDisplay,
                    activeGeneratedRecoveryKeyForDisplay = artifact.rawRecoveryKeyForDisplay,
                    feedbackMessage = "Owner registered! Save your Recovery Key offline right now.",
                    isErrorFeedback = false
                )
            }
        }
    }

    suspend fun verifyPinInternal(pinInput: String): PinVerificationResult {
        val vault = vaultState.value
        val now = System.currentTimeMillis()
        if (vault.lockoutUntilMillis > now) {
            val secs = ((vault.lockoutUntilMillis - now) / 1000L).coerceAtLeast(1L)
            return PinVerificationResult.LockedOut(secs)
        }

        val ok = SecurityCryptoManager.verifySecret(pinInput, vault.pinHashBase64, vault.pinSaltBase64)
        return if (ok) {
            securityPrefs.clearPinFailures()
            _uiState.update { it.copy(isSecurityCenterUnlocked = true, lockoutRemainingSeconds = 0L) }
            PinVerificationResult.Success
        } else {
            val newFailures = vault.failedPinAttempts + 1
            val lockoutDuration = SecurityCryptoManager.computeLockoutDurationMillis(newFailures)
            val lockoutUntil = if (lockoutDuration > 0L) now + lockoutDuration else 0L
            securityPrefs.recordPinFailure(newFailures, lockoutUntil)

            repository.logSecurityEvent(
                eventType = if (lockoutDuration > 0L) "LOCKOUT_TRIGGERED" else "PIN_FAILED",
                title = if (lockoutDuration > 0L) "Owner PIN Temporary Lockout" else "Incorrect Owner PIN Attempt",
                description = if (lockoutDuration > 0L) {
                    "Locked for ${lockoutDuration / 1000L}s after $newFailures failed attempts."
                } else {
                    "Failed PIN attempt #$newFailures."
                },
                severity = if (lockoutDuration > 0L) "CRITICAL" else "WARNING"
            )

            if (lockoutDuration > 0L) {
                PinVerificationResult.LockedOut(lockoutDuration / 1000L)
            } else {
                PinVerificationResult.Invalid((5 - newFailures).coerceAtLeast(0))
            }
        }
    }

    fun unlockWithPin(pinInput: String) {
        scope.launch {
            when (val res = verifyPinInternal(pinInput)) {
                PinVerificationResult.Success -> {
                    setFeedback("Security Center unlocked.", isError = false)
                }
                is PinVerificationResult.Invalid -> {
                    setFeedback(
                        "Incorrect PIN. ${res.remainingAttemptsBeforeLockout} attempt(s) before temporary lockout.",
                        isError = true
                    )
                }
                is PinVerificationResult.LockedOut -> {
                    setFeedback(
                        "Too many incorrect attempts. Locked for ${res.remainingSeconds}s.",
                        isError = true
                    )
                }
            }
        }
    }

    fun changePin(currentPin: String?, biometricVerified: Boolean, newPin: String, confirmNewPin: String, newLength: Int) {
        if (!SecurityCryptoManager.isPinFormatValid(newPin, newLength)) {
            setFeedback("New PIN must be exactly $newLength numeric digits.", isError = true)
            return
        }
        if (newPin != confirmNewPin) {
            setFeedback("New PIN and confirmation do not match.", isError = true)
            return
        }

        scope.launch {
            if (!biometricVerified) {
                val res = verifyPinInternal(currentPin.orEmpty())
                if (res !is PinVerificationResult.Success) {
                    val msg = when (res) {
                        is PinVerificationResult.Invalid -> "Current PIN incorrect (${res.remainingAttemptsBeforeLockout} left)."
                        is PinVerificationResult.LockedOut -> "Locked out for ${res.remainingSeconds}s."
                        PinVerificationResult.Success -> ""
                    }
                    setFeedback(msg, isError = true)
                    return@launch
                }
            }

            val newHash = SecurityCryptoManager.hashSecret(newPin)
            securityPrefs.updatePin(newHash.hashBase64, newHash.saltBase64, newLength)
            repository.logSecurityEvent(
                eventType = "PIN_CHANGED",
                title = "Owner PIN Updated",
                description = "Owner Lock updated to a new $newLength-digit salted PBKDF2 PIN.",
                severity = "INFO"
            )
            setFeedback("Owner PIN updated to $newLength-digit lock.", isError = false)
        }
    }

    fun resetPinWithRecoveryKey(
        recoveryKeyInput: String,
        newPin: String,
        confirmNewPin: String,
        newLength: Int
    ) {
        val normalized = SecurityCryptoManager.normalizeRecoveryKey(recoveryKeyInput)
        if (normalized.length < 16) {
            setFeedback("Enter your complete Recovery Key.", isError = true)
            return
        }
        if (!SecurityCryptoManager.isPinFormatValid(newPin, newLength)) {
            setFeedback("New PIN must be exactly $newLength numeric digits.", isError = true)
            return
        }
        if (newPin != confirmNewPin) {
            setFeedback("New PIN and confirmation do not match.", isError = true)
            return
        }

        scope.launch {
            val vault = vaultState.value
            val validKey = SecurityCryptoManager.verifySecret(
                normalized,
                vault.recoveryHashBase64,
                vault.recoverySaltBase64
            )
            if (!validKey) {
                repository.logSecurityEvent(
                    eventType = "RECOVERY_KEY_FAILED",
                    title = "Invalid Recovery Key Attempt",
                    description = "Forgot PIN verification failed cryptographic Recovery Key check.",
                    severity = "CRITICAL"
                )
                setFeedback("Recovery Key verification failed. No Gmail fallback exists — check your backup key.", isError = true)
                return@launch
            }

            val newPinHash = SecurityCryptoManager.hashSecret(newPin)
            val replacementKey = SecurityCryptoManager.generateRecoveryKey()
            val replacementHash = SecurityCryptoManager.hashSecret(SecurityCryptoManager.normalizeRecoveryKey(replacementKey))

            securityPrefs.updatePin(newPinHash.hashBase64, newPinHash.saltBase64, newLength)
            securityPrefs.updateRecoveryHash(replacementHash.hashBase64, replacementHash.saltBase64)

            val boundMatches = SecurityCryptoManager.isCurrentDeviceBoundOwner(context, vault.boundDeviceFingerprintHash)
            repository.logSecurityEvent(
                eventType = "RECOVERY_RESET",
                title = "PIN Reset via Recovery Key",
                description = "New $newLength-digit PIN created and replacement Recovery Key generated. Bound Owner Device match: $boundMatches.",
                severity = "WARNING"
            )

            _uiState.update {
                it.copy(
                    isSecurityCenterUnlocked = true,
                    activeGeneratedRecoveryKeyForDisplay = replacementKey,
                    feedbackMessage = "PIN reset! Save your NEW replacement Recovery Key immediately — the old key is now invalid.",
                    isErrorFeedback = false
                )
            }
        }
    }

    fun regenerateRecoveryKeyWithVerification(pinInput: String?, biometricVerified: Boolean) {
        scope.launch {
            if (!biometricVerified) {
                val res = verifyPinInternal(pinInput.orEmpty())
                if (res !is PinVerificationResult.Success) {
                    setFeedback("Owner verification failed.", isError = true)
                    return@launch
                }
            }
            val newKey = SecurityCryptoManager.generateRecoveryKey()
            val newHash = SecurityCryptoManager.hashSecret(SecurityCryptoManager.normalizeRecoveryKey(newKey))
            securityPrefs.updateRecoveryHash(newHash.hashBase64, newHash.saltBase64)
            repository.logSecurityEvent(
                eventType = "RECOVERY_KEY_ROTATED",
                title = "Recovery Key Rotated",
                description = "Owner generated a fresh replacement Recovery Key.",
                severity = "INFO"
            )
            _uiState.update {
                it.copy(
                    activeGeneratedRecoveryKeyForDisplay = newKey,
                    feedbackMessage = "New Recovery Key generated. Store it securely offline.",
                    isErrorFeedback = false
                )
            }
        }
    }

    fun addTrustedDeviceWithVerification(
        address: String,
        deviceName: String,
        brandName: String,
        isPrimaryBuds: Boolean,
        pinInput: String?,
        biometricVerified: Boolean
    ) {
        if (address.isBlank() || deviceName.isBlank()) return
        scope.launch {
            if (vaultState.value.isOwnerRegistered && !biometricVerified) {
                val res = verifyPinInternal(pinInput.orEmpty())
                if (res !is PinVerificationResult.Success) {
                    setFeedback("Owner PIN required to add a Trusted Device.", isError = true)
                    return@launch
                }
            }
            val ownerId = vaultState.value.ownerKeyId.ifBlank { "UNREGISTERED" }
            repository.upsertTrustedDevice(
                TrustedDeviceEntity(
                    address = address,
                    deviceName = deviceName,
                    brandName = brandName,
                    isPrimaryBuds = isPrimaryBuds,
                    ownerKeyId = ownerId,
                    notes = if (isPrimaryBuds) "Primary Buds" else "Owner Approved"
                )
            )
            repository.logSecurityEvent(
                eventType = "TRUSTED_DEVICE_ADDED",
                title = "Trusted Device Approved",
                description = "$deviceName ($address) added to Trusted Devices.",
                deviceName = deviceName,
                deviceAddress = address,
                severity = "INFO"
            )
            setFeedback("Added $deviceName to Trusted Devices.", isError = false)
        }
    }

    fun removeTrustedDeviceWithVerification(
        address: String,
        deviceName: String,
        pinInput: String?,
        biometricVerified: Boolean
    ) {
        scope.launch {
            if (vaultState.value.isOwnerRegistered && !biometricVerified) {
                val res = verifyPinInternal(pinInput.orEmpty())
                if (res !is PinVerificationResult.Success) {
                    setFeedback("Owner PIN required to remove a Trusted Device.", isError = true)
                    return@launch
                }
            }
            repository.removeTrustedDevice(address)
            repository.logSecurityEvent(
                eventType = "TRUSTED_DEVICE_REMOVED",
                title = "Trusted Device Removed",
                description = "$deviceName ($address) removed from Trusted Devices.",
                deviceName = deviceName,
                deviceAddress = address,
                severity = "WARNING"
            )
            setFeedback("Removed $deviceName from Trusted Devices.", isError = false)
        }
    }

    fun transferOwnershipWithVerification(
        newOwnerName: String,
        pinInput: String?,
        biometricVerified: Boolean
    ) {
        val cleanName = newOwnerName.trim()
        if (cleanName.isBlank()) {
            setFeedback("Enter the new Owner Name.", isError = true)
            return
        }
        scope.launch {
            if (!biometricVerified) {
                val res = verifyPinInternal(pinInput.orEmpty())
                if (res !is PinVerificationResult.Success) {
                    setFeedback("Owner verification failed for ownership transfer.", isError = true)
                    return@launch
                }
            }
            val artifact = SecurityCryptoManager.generateOwnerRegistration(context)
            securityPrefs.transferOwnership(
                newDisplayName = cleanName,
                newOwnerKeyId = artifact.ownerKeyId,
                newEncryptedSecretBase64 = artifact.encryptedOwnerSecretBase64,
                newBoundModel = artifact.boundDeviceModel,
                newBoundFingerprintHash = artifact.boundDeviceFingerprintHash,
                keystoreBacked = artifact.keystoreBacked,
                newRecoveryHashBase64 = artifact.recoveryHash.hashBase64,
                newRecoverySaltBase64 = artifact.recoveryHash.saltBase64
            )
            repository.logSecurityEvent(
                eventType = "OWNERSHIP_TRANSFERRED",
                title = "Ownership Transferred",
                description = "Ownership transferred to '$cleanName' (New Key ID: ${artifact.ownerKeyId}).",
                severity = "WARNING"
            )
            _uiState.update {
                it.copy(
                    activeGeneratedOwnerKeyForDisplay = artifact.rawOwnerKeyForDisplay,
                    activeGeneratedRecoveryKeyForDisplay = artifact.rawRecoveryKeyForDisplay,
                    feedbackMessage = "Ownership transferred to $cleanName. Save the new Recovery Key!",
                    isErrorFeedback = false
                )
            }
        }
    }

    fun setLostMode(
        enable: Boolean,
        targetName: String?,
        targetAddress: String?,
        lastSeenAt: Long?,
        batterySummary: String?,
        note: String,
        pinInput: String?,
        biometricVerified: Boolean
    ) {
        scope.launch {
            if (vaultState.value.isOwnerRegistered && vaultState.value.lockSensitiveSettingsWithPin && !biometricVerified) {
                val res = verifyPinInternal(pinInput.orEmpty())
                if (res !is PinVerificationResult.Success) {
                    setFeedback("Owner verification required to toggle Lost Mode.", isError = true)
                    return@launch
                }
            }

            securityPrefs.setLostMode(
                enabled = enable,
                deviceName = targetName,
                deviceAddress = targetAddress,
                lastSeenAt = lastSeenAt,
                batterySummary = batterySummary,
                note = note.trim()
            )
            repository.logSecurityEvent(
                eventType = if (enable) "LOST_MODE_ENABLED" else "LOST_MODE_DISABLED",
                title = if (enable) "Lost Mode Activated" else "Lost Mode Deactivated",
                description = if (enable) {
                    "Lost Mode active for ${targetName ?: "Earbuds"} (${targetAddress ?: "Unknown MAC"})."
                } else {
                    "Owner deactivated Lost Mode."
                },
                deviceName = targetName,
                deviceAddress = targetAddress,
                severity = if (enable) "CRITICAL" else "INFO"
            )
            if (enable) {
                BudsNotificationManager.showSecurityAlertNotification(
                    context = context,
                    title = "Lost Mode Active — HARDIK BUDS PRO",
                    message = "Monitoring Bluetooth connections for ${targetName ?: "your earbuds"}."
                )
            }
            setFeedback(
                if (enable) "Lost Mode activated." else "Lost Mode deactivated.",
                isError = false
            )
        }
    }

    fun evaluateConnectedDeviceSecurity(device: PairedAudioDevice) {
        scope.launch {
            val vault = vaultState.value
            val isTrusted = repository.isDeviceTrusted(device.address)

            if (vault.lostModeEnabled &&
                (vault.lostTargetDeviceAddress == null || vault.lostTargetDeviceAddress == device.address)
            ) {
                val msg = "Lost Mode Alert: ${device.name} (${device.address}) just connected!"
                repository.logSecurityEvent(
                    eventType = "LOST_DEVICE_RECONNECTED",
                    title = "Lost Earbuds Connection Detected",
                    description = msg,
                    deviceName = device.name,
                    deviceAddress = device.address,
                    severity = "CRITICAL"
                )
                BudsNotificationManager.showSecurityAlertNotification(
                    context = context,
                    title = "Lost Device Activity Detected",
                    message = msg
                )
            }

            if (vault.isOwnerRegistered && vault.unauthorizedDeviceAlertsEnabled && !isTrusted) {
                val event = SecurityEventEntity(
                    eventType = "UNAUTHORIZED_DEVICE_DETECTED",
                    title = "Unknown Bluetooth Device Connected",
                    description = "Untrusted device '${device.name}' (${device.brand.displayName}, ${device.address}) connected while Owner Lock monitoring is active.",
                    deviceName = device.name,
                    deviceAddress = device.address,
                    severity = "CRITICAL",
                    timestamp = System.currentTimeMillis()
                )
                repository.logSecurityEvent(
                    eventType = event.eventType,
                    title = event.title,
                    description = event.description,
                    deviceName = event.deviceName,
                    deviceAddress = event.deviceAddress,
                    severity = event.severity
                )
                _uiState.update { it.copy(latestUnauthorizedAlert = event) }
                BudsNotificationManager.showSecurityAlertNotification(
                    context = context,
                    title = "Unauthorized Bluetooth Device Alert",
                    message = "${device.name} (${device.address}) connected and is not in your Trusted Devices list."
                )
            }
        }
    }

    fun dismissLatestUnauthorizedAlert() {
        _uiState.update { it.copy(latestUnauthorizedAlert = null) }
    }

    fun setLockSensitiveSettings(enabled: Boolean) {
        scope.launch { securityPrefs.setLockSensitiveSettings(enabled) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        scope.launch { securityPrefs.setBiometricEnabled(enabled) }
    }

    fun setUnauthorizedAlertsEnabled(enabled: Boolean) {
        scope.launch { securityPrefs.setUnauthorizedAlertsEnabled(enabled) }
    }

    fun clearSecurityHistory() {
        scope.launch {
            repository.clearSecurityHistory()
            setFeedback("Cleared local security audit log.", isError = false)
        }
    }

    private fun setFeedback(message: String, isError: Boolean) {
        _uiState.update { it.copy(feedbackMessage = message, isErrorFeedback = isError) }
    }
}
