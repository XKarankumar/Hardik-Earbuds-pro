package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "buds_pro_security_vault"
)

data class SecurityVaultState(
    val isOwnerRegistered: Boolean = false,
    val ownerDisplayName: String = "",
    val ownerKeyId: String = "",
    val encryptedOwnerSecretBase64: String? = null,
    val boundDeviceModel: String = "",
    val boundDeviceFingerprintHash: String = "",
    val registeredAtMillis: Long = 0L,
    val keystoreBacked: Boolean = false,
    val pinHashBase64: String? = null,
    val pinSaltBase64: String? = null,
    val pinLength: Int = 4,
    val recoveryHashBase64: String? = null,
    val recoverySaltBase64: String? = null,
    val lockSensitiveSettingsWithPin: Boolean = true,
    val biometricUnlockEnabled: Boolean = true,
    val failedPinAttempts: Int = 0,
    val lockoutUntilMillis: Long = 0L,
    val unauthorizedDeviceAlertsEnabled: Boolean = true,
    val lostModeEnabled: Boolean = false,
    val lostModeActivatedAt: Long? = null,
    val lostTargetDeviceName: String? = null,
    val lostTargetDeviceAddress: String? = null,
    val lostLastSeenAt: Long? = null,
    val lostLastBatterySummary: String? = null,
    val lostNote: String = ""
) {
    val hasPinConfigured: Boolean
        get() = !pinHashBase64.isNullOrBlank() && !pinSaltBase64.isNullOrBlank()

    val hasRecoveryKeyConfigured: Boolean
        get() = !recoveryHashBase64.isNullOrBlank() && !recoverySaltBase64.isNullOrBlank()
}

class SecurityPreferencesRepository(private val context: Context) {

    private object Keys {
        val OWNER_REGISTERED = booleanPreferencesKey("owner_registered")
        val OWNER_DISPLAY_NAME = stringPreferencesKey("owner_display_name")
        val OWNER_KEY_ID = stringPreferencesKey("owner_key_id")
        val ENCRYPTED_OWNER_SECRET = stringPreferencesKey("encrypted_owner_secret")
        val BOUND_DEVICE_MODEL = stringPreferencesKey("bound_device_model")
        val BOUND_DEVICE_FINGERPRINT = stringPreferencesKey("bound_device_fingerprint")
        val REGISTERED_AT = longPreferencesKey("registered_at")
        val KEYSTORE_BACKED = booleanPreferencesKey("keystore_backed")

        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_LENGTH = intPreferencesKey("pin_length")

        val RECOVERY_HASH = stringPreferencesKey("recovery_hash")
        val RECOVERY_SALT = stringPreferencesKey("recovery_salt")

        val LOCK_SETTINGS_WITH_PIN = booleanPreferencesKey("lock_settings_with_pin")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val FAILED_PIN_ATTEMPTS = intPreferencesKey("failed_pin_attempts")
        val LOCKOUT_UNTIL = longPreferencesKey("lockout_until")

        val UNAUTHORIZED_ALERTS_ENABLED = booleanPreferencesKey("unauthorized_alerts_enabled")
        val LOST_MODE_ENABLED = booleanPreferencesKey("lost_mode_enabled")
        val LOST_MODE_ACTIVATED_AT = longPreferencesKey("lost_mode_activated_at")
        val LOST_DEVICE_NAME = stringPreferencesKey("lost_device_name")
        val LOST_DEVICE_ADDRESS = stringPreferencesKey("lost_device_address")
        val LOST_LAST_SEEN_AT = longPreferencesKey("lost_last_seen_at")
        val LOST_LAST_BATTERY = stringPreferencesKey("lost_last_battery")
        val LOST_NOTE = stringPreferencesKey("lost_note")
    }

    val vaultFlow: Flow<SecurityVaultState> = context.securityDataStore.data.map { prefs ->
        SecurityVaultState(
            isOwnerRegistered = prefs[Keys.OWNER_REGISTERED] ?: false,
            ownerDisplayName = prefs[Keys.OWNER_DISPLAY_NAME].orEmpty(),
            ownerKeyId = prefs[Keys.OWNER_KEY_ID].orEmpty(),
            encryptedOwnerSecretBase64 = prefs[Keys.ENCRYPTED_OWNER_SECRET],
            boundDeviceModel = prefs[Keys.BOUND_DEVICE_MODEL].orEmpty(),
            boundDeviceFingerprintHash = prefs[Keys.BOUND_DEVICE_FINGERPRINT].orEmpty(),
            registeredAtMillis = prefs[Keys.REGISTERED_AT] ?: 0L,
            keystoreBacked = prefs[Keys.KEYSTORE_BACKED] ?: false,
            pinHashBase64 = prefs[Keys.PIN_HASH],
            pinSaltBase64 = prefs[Keys.PIN_SALT],
            pinLength = prefs[Keys.PIN_LENGTH] ?: 4,
            recoveryHashBase64 = prefs[Keys.RECOVERY_HASH],
            recoverySaltBase64 = prefs[Keys.RECOVERY_SALT],
            lockSensitiveSettingsWithPin = prefs[Keys.LOCK_SETTINGS_WITH_PIN] ?: true,
            biometricUnlockEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: true,
            failedPinAttempts = prefs[Keys.FAILED_PIN_ATTEMPTS] ?: 0,
            lockoutUntilMillis = prefs[Keys.LOCKOUT_UNTIL] ?: 0L,
            unauthorizedDeviceAlertsEnabled = prefs[Keys.UNAUTHORIZED_ALERTS_ENABLED] ?: true,
            lostModeEnabled = prefs[Keys.LOST_MODE_ENABLED] ?: false,
            lostModeActivatedAt = prefs[Keys.LOST_MODE_ACTIVATED_AT],
            lostTargetDeviceName = prefs[Keys.LOST_DEVICE_NAME],
            lostTargetDeviceAddress = prefs[Keys.LOST_DEVICE_ADDRESS],
            lostLastSeenAt = prefs[Keys.LOST_LAST_SEEN_AT],
            lostLastBatterySummary = prefs[Keys.LOST_LAST_BATTERY],
            lostNote = prefs[Keys.LOST_NOTE].orEmpty()
        )
    }

    suspend fun saveOwnerRegistration(
        displayName: String,
        ownerKeyId: String,
        encryptedOwnerSecretBase64: String,
        boundDeviceModel: String,
        boundDeviceFingerprintHash: String,
        keystoreBacked: Boolean,
        pinHashBase64: String,
        pinSaltBase64: String,
        pinLength: Int,
        recoveryHashBase64: String,
        recoverySaltBase64: String
    ) {
        context.securityDataStore.edit { prefs ->
            prefs[Keys.OWNER_REGISTERED] = true
            prefs[Keys.OWNER_DISPLAY_NAME] = displayName
            prefs[Keys.OWNER_KEY_ID] = ownerKeyId
            prefs[Keys.ENCRYPTED_OWNER_SECRET] = encryptedOwnerSecretBase64
            prefs[Keys.BOUND_DEVICE_MODEL] = boundDeviceModel
            prefs[Keys.BOUND_DEVICE_FINGERPRINT] = boundDeviceFingerprintHash
            prefs[Keys.REGISTERED_AT] = System.currentTimeMillis()
            prefs[Keys.KEYSTORE_BACKED] = keystoreBacked
            prefs[Keys.PIN_HASH] = pinHashBase64
            prefs[Keys.PIN_SALT] = pinSaltBase64
            prefs[Keys.PIN_LENGTH] = pinLength
            prefs[Keys.RECOVERY_HASH] = recoveryHashBase64
            prefs[Keys.RECOVERY_SALT] = recoverySaltBase64
            prefs[Keys.FAILED_PIN_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL] = 0L
        }
    }

    suspend fun updatePin(pinHashBase64: String, pinSaltBase64: String, pinLength: Int) {
        context.securityDataStore.edit { prefs ->
            prefs[Keys.PIN_HASH] = pinHashBase64
            prefs[Keys.PIN_SALT] = pinSaltBase64
            prefs[Keys.PIN_LENGTH] = pinLength
            prefs[Keys.FAILED_PIN_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL] = 0L
        }
    }

    suspend fun updateRecoveryHash(recoveryHashBase64: String, recoverySaltBase64: String) {
        context.securityDataStore.edit { prefs ->
            prefs[Keys.RECOVERY_HASH] = recoveryHashBase64
            prefs[Keys.RECOVERY_SALT] = recoverySaltBase64
        }
    }

    suspend fun recordPinFailure(failedAttempts: Int, lockoutUntilMillis: Long) {
        context.securityDataStore.edit { prefs ->
            prefs[Keys.FAILED_PIN_ATTEMPTS] = failedAttempts
            prefs[Keys.LOCKOUT_UNTIL] = lockoutUntilMillis
        }
    }

    suspend fun clearPinFailures() {
        context.securityDataStore.edit { prefs ->
            prefs[Keys.FAILED_PIN_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL] = 0L
        }
    }

    suspend fun setLockSensitiveSettings(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.LOCK_SETTINGS_WITH_PIN] = enabled }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setUnauthorizedAlertsEnabled(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.UNAUTHORIZED_ALERTS_ENABLED] = enabled }
    }

    suspend fun setLostMode(
        enabled: Boolean,
        deviceName: String?,
        deviceAddress: String?,
        lastSeenAt: Long?,
        batterySummary: String?,
        note: String
    ) {
        context.securityDataStore.edit { prefs ->
            prefs[Keys.LOST_MODE_ENABLED] = enabled
            if (enabled) {
                prefs[Keys.LOST_MODE_ACTIVATED_AT] = System.currentTimeMillis()
                deviceName?.let { prefs[Keys.LOST_DEVICE_NAME] = it } ?: prefs.remove(Keys.LOST_DEVICE_NAME)
                deviceAddress?.let { prefs[Keys.LOST_DEVICE_ADDRESS] = it } ?: prefs.remove(Keys.LOST_DEVICE_ADDRESS)
                lastSeenAt?.let { prefs[Keys.LOST_LAST_SEEN_AT] = it } ?: prefs.remove(Keys.LOST_LAST_SEEN_AT)
                batterySummary?.let { prefs[Keys.LOST_LAST_BATTERY] = it } ?: prefs.remove(Keys.LOST_LAST_BATTERY)
                prefs[Keys.LOST_NOTE] = note
            } else {
                prefs.remove(Keys.LOST_MODE_ACTIVATED_AT)
                prefs[Keys.LOST_NOTE] = ""
            }
        }
    }

    suspend fun transferOwnership(
        newDisplayName: String,
        newOwnerKeyId: String,
        newEncryptedSecretBase64: String,
        newBoundModel: String,
        newBoundFingerprintHash: String,
        keystoreBacked: Boolean,
        newRecoveryHashBase64: String,
        newRecoverySaltBase64: String
    ) {
        context.securityDataStore.edit { prefs ->
            prefs[Keys.OWNER_DISPLAY_NAME] = newDisplayName
            prefs[Keys.OWNER_KEY_ID] = newOwnerKeyId
            prefs[Keys.ENCRYPTED_OWNER_SECRET] = newEncryptedSecretBase64
            prefs[Keys.BOUND_DEVICE_MODEL] = newBoundModel
            prefs[Keys.BOUND_DEVICE_FINGERPRINT] = newBoundFingerprintHash
            prefs[Keys.REGISTERED_AT] = System.currentTimeMillis()
            prefs[Keys.KEYSTORE_BACKED] = keystoreBacked
            prefs[Keys.RECOVERY_HASH] = newRecoveryHashBase64
            prefs[Keys.RECOVERY_SALT] = newRecoverySaltBase64
        }
    }
}
