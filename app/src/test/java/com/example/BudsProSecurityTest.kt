package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.bluetooth.PairedAudioDevice
import com.example.devices.BatterySource
import com.example.devices.BatteryTelemetry
import com.example.devices.EarbudsBrand
import com.example.integrations.OnePlusNordBuds3ProIntegration
import com.example.security.PinVerificationResult
import com.example.security.SecurityCenterManager
import com.example.security.SecurityCryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BudsProSecurityTest {

    @Test
    fun `pin format validation enforces 4 or 6 numeric digits only`() {
        assertTrue(SecurityCryptoManager.isPinFormatValid("1234", 4))
        assertTrue(SecurityCryptoManager.isPinFormatValid("098765", 6))
        assertFalse(SecurityCryptoManager.isPinFormatValid("123", 4))
        assertFalse(SecurityCryptoManager.isPinFormatValid("12345", 6))
        assertFalse(SecurityCryptoManager.isPinFormatValid("12ab", 4))
        assertFalse(SecurityCryptoManager.isPinFormatValid("12345678", 8))
    }

    @Test
    fun `pbkdf2 salted hashing produces unique hashes and verifies accurately`() {
        val hash1 = SecurityCryptoManager.hashSecret("4829")
        val hash2 = SecurityCryptoManager.hashSecret("4829")

        assertNotEquals(hash1.saltBase64, hash2.saltBase64)
        assertNotEquals(hash1.hashBase64, hash2.hashBase64)

        assertTrue(SecurityCryptoManager.verifySecret("4829", hash1.hashBase64, hash1.saltBase64))
        assertTrue(SecurityCryptoManager.verifySecret("4829", hash2.hashBase64, hash2.saltBase64))
        assertFalse(SecurityCryptoManager.verifySecret("0000", hash1.hashBase64, hash1.saltBase64))
        assertFalse(SecurityCryptoManager.verifySecret("", hash1.hashBase64, hash1.saltBase64))
    }

    @Test
    fun `recovery key generation normalization and lockout thresholds work as specified`() {
        val key = SecurityCryptoManager.generateRecoveryKey()
        val normalized = SecurityCryptoManager.normalizeRecoveryKey("  ${key.lowercase().replace("-", "")}  ")
        assertEquals(key, normalized)
        assertEquals(6, key.split("-").size)

        assertEquals(0L, SecurityCryptoManager.computeLockoutDurationMillis(1))
        assertEquals(0L, SecurityCryptoManager.computeLockoutDurationMillis(4))
        assertEquals(30_000L, SecurityCryptoManager.computeLockoutDurationMillis(5))
        assertEquals(60_000L, SecurityCryptoManager.computeLockoutDurationMillis(6))
        assertEquals(120_000L, SecurityCryptoManager.computeLockoutDurationMillis(7))
        assertEquals(300_000L, SecurityCryptoManager.computeLockoutDurationMillis(9))
    }

    @Test
    fun `owner registration binds device fingerprint and encrypts secret`() {
        val context = ApplicationProvider.getApplicationContext<BudsProApplication>()
        val artifact = SecurityCryptoManager.generateOwnerRegistration(context)
        assertTrue(artifact.ownerKeyId.isNotBlank())
        assertTrue(artifact.rawOwnerKeyForDisplay.startsWith("HBP-OWN-"))
        assertTrue(SecurityCryptoManager.isCurrentDeviceBoundOwner(context, artifact.boundDeviceFingerprintHash))

        val decrypted = SecurityCryptoManager.decryptOwnerSecret(artifact.encryptedOwnerSecretBase64)
        assertNotNull(decrypted)
        assertTrue(decrypted!!.startsWith(artifact.rawOwnerKeyForDisplay))
    }

    @Test
    fun `oneplus target device integration recognizes OnePlus Buds 3 Pro and Nord Buds 3 Pro`() {
        assertTrue(OnePlusNordBuds3ProIntegration.isExactNordBuds3Pro("OnePlus Buds 3 Pro"))
        assertTrue(OnePlusNordBuds3ProIntegration.isExactNordBuds3Pro("OnePlus Nord Buds 3 Pro"))
    }

    @Test
    fun `end to end security center lifecycle registration pin lock forgot recovery trusted devices transfer lost mode and unauthorized alerts`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<BudsProApplication>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = SecurityCenterManager(
            context = app,
            securityPrefs = app.securityPreferencesRepository,
            repository = app.repository,
            scope = scope
        )

        try {
            withTimeout(20_000L) {
                val onePlusBuds = PairedAudioDevice(
                    name = "OnePlus Buds 3 Pro",
                    address = "AA:BB:CC:DD:EE:01",
                    brand = EarbudsBrand.ONEPLUS,
                    isConnected = true,
                    isA2dpConnected = true,
                    isHeadsetConnected = true,
                    isAudioClass = true,
                    supportsFastPair = true,
                    batteryTelemetry = BatteryTelemetry(
                        leftPercent = 85,
                        rightPercent = 80,
                        casePercent = 60,
                        source = BatterySource.BLE_GATT_BATTERY_SERVICE
                    )
                )

                // 1. Register Owner with 4-digit PIN
                manager.registerOwner(
                    displayName = "Hardik Kumar",
                    pin = "2468",
                    confirmPin = "2468",
                    pinLength = 4,
                    initialPrimaryDevice = onePlusBuds
                )

                val firstRecoveryKey = manager.uiState
                    .first { it.activeGeneratedRecoveryKeyForDisplay != null }
                    .activeGeneratedRecoveryKeyForDisplay!!
                manager.clearDisplayedKeys()

                val registeredVault = app.securityPreferencesRepository.vaultFlow.first { it.isOwnerRegistered }
                assertEquals("Hardik Kumar", registeredVault.ownerDisplayName)
                assertEquals(4, registeredVault.pinLength)
                assertTrue(registeredVault.hasPinConfigured)
                assertTrue(registeredVault.hasRecoveryKeyConfigured)

                // Verify initial primary device auto-trusted
                val trustedAfterReg = app.repository.trustedDevices.first { it.isNotEmpty() }
                assertEquals("AA:BB:CC:DD:EE:01", trustedAfterReg.first().address)

                // 2. Trusted device connected should NOT raise unauthorized alert
                manager.evaluateConnectedDeviceSecurity(onePlusBuds)
                delay(250)
                assertNull(manager.uiState.value.latestUnauthorizedAlert)

                // 3. Unknown Bluetooth device connected MUST raise Unauthorized Device Alert & event
                val unknownDevice = PairedAudioDevice(
                    name = "Unknown Headset X",
                    address = "11:22:33:44:55:99",
                    brand = EarbudsBrand.GENERIC,
                    isConnected = true,
                    isA2dpConnected = true,
                    isHeadsetConnected = false,
                    isAudioClass = true,
                    batteryTelemetry = BatteryTelemetry()
                )
                manager.evaluateConnectedDeviceSecurity(unknownDevice)
                val alert = manager.uiState.first { it.latestUnauthorizedAlert != null }.latestUnauthorizedAlert
                assertNotNull(alert)
                assertEquals("11:22:33:44:55:99", alert?.deviceAddress)

                // 4. Adding trusted device requires valid Owner PIN (wrong PIN denied)
                manager.clearFeedback()
                manager.addTrustedDeviceWithVerification(
                    address = "22:33:44:55:66:77",
                    deviceName = "Spare Buds",
                    brandName = "OnePlus",
                    isPrimaryBuds = false,
                    pinInput = "9999",
                    biometricVerified = false
                )
                manager.uiState.first { it.isErrorFeedback && it.feedbackMessage != null }
                assertFalse(app.repository.isDeviceTrusted("22:33:44:55:66:77"))

                manager.addTrustedDeviceWithVerification(
                    address = "22:33:44:55:66:77",
                    deviceName = "Spare Buds",
                    brandName = "OnePlus",
                    isPrimaryBuds = false,
                    pinInput = "2468",
                    biometricVerified = false
                )
                app.repository.trustedDevices.first { list -> list.any { it.address == "22:33:44:55:66:77" } }
                assertTrue(app.repository.isDeviceTrusted("22:33:44:55:66:77"))

                // 5. Remove trusted device with PIN
                manager.removeTrustedDeviceWithVerification(
                    address = "22:33:44:55:66:77",
                    deviceName = "Spare Buds",
                    pinInput = "2468",
                    biometricVerified = false
                )
                app.repository.trustedDevices.first { list -> list.none { it.address == "22:33:44:55:66:77" } }
                assertFalse(app.repository.isDeviceTrusted("22:33:44:55:66:77"))

                // 6. Change PIN to 6-digit PIN
                manager.changePin(
                    currentPin = "2468",
                    biometricVerified = false,
                    newPin = "135790",
                    confirmNewPin = "135790",
                    newLength = 6
                )
                val afterChange = app.securityPreferencesRepository.vaultFlow.first { it.pinLength == 6 }
                assertEquals(6, afterChange.pinLength)
                assertEquals(PinVerificationResult.Success, manager.verifyPinInternal("135790"))

                // 7. Trigger temporary lockout after 5 wrong PIN attempts
                manager.lockSecurityCenter()
                assertFalse(manager.uiState.value.isSecurityCenterUnlocked)
                repeat(4) {
                    val res = manager.verifyPinInternal("000000")
                    assertTrue(res is PinVerificationResult.Invalid)
                }
                val lockoutRes = manager.verifyPinInternal("000000")
                assertTrue(lockoutRes is PinVerificationResult.LockedOut)
                val lockedValidAttempt = manager.verifyPinInternal("135790")
                assertTrue(lockedValidAttempt is PinVerificationResult.LockedOut)

                // 8. Forgot PIN (No Gmail): wrong Recovery Key must fail, valid Recovery Key resets PIN + rotates Recovery Key
                manager.clearFeedback()
                manager.resetPinWithRecoveryKey("AAAA-BBBB-CCCC-DDDD-EEEE-FFFF", "7777", "7777", 4)
                val failedRecoveryUi = manager.uiState.first { it.isErrorFeedback && it.feedbackMessage != null }
                assertTrue(failedRecoveryUi.isErrorFeedback)

                manager.resetPinWithRecoveryKey(firstRecoveryKey, "7777", "7777", 4)
                val rotatedRecoveryKey = manager.uiState
                    .first { it.activeGeneratedRecoveryKeyForDisplay != null }
                    .activeGeneratedRecoveryKeyForDisplay!!
                val afterRecovery = app.securityPreferencesRepository.vaultFlow.first { it.pinLength == 4 && it.failedPinAttempts == 0 }
                assertEquals(PinVerificationResult.Success, manager.verifyPinInternal("7777"))
                assertNotEquals(firstRecoveryKey, rotatedRecoveryKey)

                // Verify old Recovery Key hash is invalidated and new replacement key verifies
                assertFalse(
                    SecurityCryptoManager.verifySecret(
                        SecurityCryptoManager.normalizeRecoveryKey(firstRecoveryKey),
                        afterRecovery.recoveryHashBase64,
                        afterRecovery.recoverySaltBase64
                    )
                )
                assertTrue(
                    SecurityCryptoManager.verifySecret(
                        SecurityCryptoManager.normalizeRecoveryKey(rotatedRecoveryKey),
                        afterRecovery.recoveryHashBase64,
                        afterRecovery.recoverySaltBase64
                    )
                )

                // 9. Lost Mode activation, reconnection detection, and deactivation
                manager.setLostMode(
                    enable = true,
                    targetName = onePlusBuds.name,
                    targetAddress = onePlusBuds.address,
                    lastSeenAt = 1_700_000_000_000L,
                    batterySummary = "L 85% · R 80% · Case 60%",
                    note = "Left at Desk",
                    pinInput = "7777",
                    biometricVerified = false
                )
                app.securityPreferencesRepository.vaultFlow.first { it.lostModeEnabled }

                manager.evaluateConnectedDeviceSecurity(onePlusBuds)
                val events = app.repository.securityEvents.first { list ->
                    list.any { it.eventType == "LOST_DEVICE_RECONNECTED" }
                }
                assertTrue(events.any { it.eventType == "LOST_DEVICE_RECONNECTED" })

                manager.setLostMode(
                    enable = false,
                    targetName = null,
                    targetAddress = null,
                    lastSeenAt = null,
                    batterySummary = null,
                    note = "",
                    pinInput = "7777",
                    biometricVerified = false
                )
                app.securityPreferencesRepository.vaultFlow.first { !it.lostModeEnabled }

                // 10. Ownership transfer rotates Owner Key ID
                val oldKeyId = app.securityPreferencesRepository.vaultFlow.first().ownerKeyId
                manager.transferOwnershipWithVerification("New Owner", "7777", false)
                val transferredVault = app.securityPreferencesRepository.vaultFlow.first { it.ownerDisplayName == "New Owner" }
                assertNotEquals(oldKeyId, transferredVault.ownerKeyId)
            }
        } finally {
            scope.cancel()
        }
    }
}
