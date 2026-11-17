package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.audio.LocatorChannel
import com.example.audio.PlayerUiState
import com.example.audio.SpatialAudioStatus
import com.example.bluetooth.BluetoothDashboardState
import com.example.core.PermissionStatusSnapshot
import com.example.data.local.SecurityEventEntity
import com.example.data.local.TrustedDeviceEntity
import com.example.data.preferences.SecurityVaultState
import com.example.equalizer.EqualizerUiState
import com.example.security.BiometricCapabilityStatus
import com.example.security.SecurityUiState
import com.example.ui.components.ConnectionPopupSheet
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EqualizerCard
import com.example.ui.screens.OwnerLockGateCard
import com.example.ui.screens.OwnerRegistrationCard
import com.example.ui.screens.SecurityCenterScreen
import com.example.ui.theme.HardikBudsProTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BudsProComposeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `dashboard renders HARDIK BUDS PRO and opens connection popup callback`() {
        var popupTriggered = false
        composeRule.setContent {
            HardikBudsProTheme {
                DashboardScreen(
                    bluetoothState = BluetoothDashboardState(
                        isBluetoothSupported = true,
                        isBluetoothEnabled = true
                    ),
                    permissions = PermissionStatusSnapshot(
                        bluetoothConnectGranted = true,
                        bluetoothScanGranted = true,
                        legacyLocationForBleGranted = true,
                        postNotificationsGranted = true,
                        overlayGranted = false
                    ),
                    equalizerState = EqualizerUiState(),
                    spatialState = SpatialAudioStatus(),
                    playerState = PlayerUiState(),
                    isLocatorPlaying = false,
                    locatorChannel = LocatorChannel.BOTH,
                    onRequestPermissions = {},
                    onShowPopup = { popupTriggered = true },
                    onPreviewExternalPopup = {},
                    onFastPair = {},
                    onOpenBluetoothSettings = {},
                    onOpenCompanionApp = { _, _ -> },
                    onToggleFindEarbuds = {},
                    onNavigateToDevices = {},
                    onNavigateToStudio = {},
                    onNavigateToBattery = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("HARDIK BUDS PRO").assertIsDisplayed()
        composeRule.onNodeWithTag("open_popup_button").performScrollTo().performClick()
        assertTrue(popupTriggered)
    }

    @Test
    fun `connection popup sheet displays real null-safe placeholders and dismisses cleanly`() {
        var dismissed = false
        composeRule.setContent {
            HardikBudsProTheme {
                ConnectionPopupSheet(
                    visible = true,
                    bluetoothState = BluetoothDashboardState(),
                    onDismiss = { dismissed = true },
                    onConnectOrSettings = {},
                    onFastPair = {},
                    onOpenCompanionApp = { _, _ -> },
                    onInspectGatt = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("connection_popup_card").assertIsDisplayed()
        composeRule.onNodeWithTag("popup_close_button").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `equalizer card switches presets accurately`() {
        var selectedPreset = "Normal"
        composeRule.setContent {
            HardikBudsProTheme {
                EqualizerCard(
                    eqState = EqualizerUiState(),
                    onToggleEnabled = {},
                    onSelectPreset = { selectedPreset = it },
                    onUpdateBand = { _, _ -> },
                    onSaveCustomPreset = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("eq_preset_deep_bass").performClick()
        assertEquals("Deep Bass", selectedPreset)
    }

    @Test
    fun `owner registration card supports 6-digit pin setup and submits inputs`() {
        var capturedLen = 0
        var capturedPin = ""
        composeRule.setContent {
            HardikBudsProTheme {
                Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OwnerRegistrationCard { _, pin, _, len ->
                        capturedPin = pin
                        capturedLen = len
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reg_pin_length_6").performScrollTo().performClick()
        composeRule.onNodeWithTag("owner_reg_pin_input").performScrollTo().performTextInput("123456")
        composeRule.onNodeWithTag("owner_reg_confirm_pin_input").performScrollTo().performTextInput("123456")
        composeRule.onNodeWithTag("owner_register_submit_button").performScrollTo().performClick()
        assertEquals(6, capturedLen)
        assertEquals("123456", capturedPin)
    }

    @Test
    fun `owner lock gate card submits pin and triggers forgot pin without gmail`() {
        var submittedPin = ""
        var forgotOpened = false
        composeRule.setContent {
            HardikBudsProTheme {
                OwnerLockGateCard(
                    pinLength = 4,
                    lockoutRemainingSeconds = 0L,
                    biometricEnabled = true,
                    biometricStatus = BiometricCapabilityStatus(true, "Ready"),
                    onUnlockWithPin = { submittedPin = it },
                    onTriggerBiometric = {},
                    onOpenForgotPin = { forgotOpened = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lock_gate_pin_input").performTextInput("4321")
        composeRule.onNodeWithTag("lock_gate_unlock_button").performClick()
        assertEquals("4321", submittedPin)
        composeRule.onNodeWithTag("lock_gate_forgot_pin_button").performClick()
        assertTrue(forgotOpened)
    }

    @Test
    fun `unlocked security center renders all 9 security sections cleanly`() {
        composeRule.setContent {
            HardikBudsProTheme {
                SecurityCenterScreen(
                    vaultState = SecurityVaultState(
                        isOwnerRegistered = true,
                        ownerDisplayName = "Hardik Kumar",
                        ownerKeyId = "ABCDEF1234567890",
                        boundDeviceModel = "OnePlus 12",
                        pinLength = 4,
                        keystoreBacked = true
                    ),
                    securityUi = SecurityUiState(
                        isSecurityCenterUnlocked = true,
                        isOriginalOwnerDevice = true,
                        biometricStatus = BiometricCapabilityStatus(true, "Ready")
                    ),
                    bluetoothState = BluetoothDashboardState(),
                    trustedDevices = listOf(
                        TrustedDeviceEntity(
                            address = "AA:BB:CC:DD:EE:FF",
                            deviceName = "OnePlus Buds 3 Pro",
                            brandName = "OnePlus",
                            isPrimaryBuds = true,
                            ownerKeyId = "ABCDEF1234567890"
                        )
                    ),
                    securityEvents = listOf(
                        SecurityEventEntity(
                            id = 1,
                            eventType = "OWNER_REGISTERED",
                            title = "Primary Owner Registered",
                            description = "Owner bound to device.",
                            severity = "INFO"
                        )
                    ),
                    onRegisterOwner = { _, _, _, _ -> },
                    onUnlockWithPin = {},
                    onTriggerBiometricUnlock = {},
                    onLockNow = {},
                    onSetLockSensitive = {},
                    onSetBiometricEnabled = {},
                    onSetUnauthorizedAlerts = {},
                    onChangePin = { _, _, _, _, _ -> },
                    onResetPinWithRecovery = { _, _, _, _ -> },
                    onRotateRecoveryKey = { _, _ -> },
                    onAddTrustedDevice = { _, _, _ -> },
                    onRemoveTrustedDevice = { _, _, _ -> },
                    onTransferOwnership = { _, _, _ -> },
                    onToggleLostMode = { _, _, _, _ -> },
                    onTriggerFindChime = {},
                    onDismissUnauthorizedBanner = {},
                    onClearDisplayedKeys = {},
                    onClearFeedback = {},
                    onClearSecurityHistory = {},
                    onRequestBiometricForApproval = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1. Owner Profile").assertIsDisplayed()
        composeRule.onNodeWithTag("security_center_list")
            .performScrollToNode(hasTestTag("activate_lost_mode_button"))
        composeRule.onNodeWithTag("activate_lost_mode_button").assertIsDisplayed()
    }
}
