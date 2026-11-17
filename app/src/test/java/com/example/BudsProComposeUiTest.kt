package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.audio.LocatorChannel
import com.example.audio.PlayerUiState
import com.example.audio.SpatialAudioStatus
import com.example.bluetooth.BluetoothDashboardState
import com.example.core.PermissionStatusSnapshot
import com.example.equalizer.EqualizerUiState
import com.example.ui.components.ConnectionPopupSheet
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EqualizerCard
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
}
