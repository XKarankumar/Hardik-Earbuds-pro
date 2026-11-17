package com.example.ui.screens

import android.content.Context
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
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.AppConstants
import com.example.core.PermissionManager
import com.example.core.PermissionStatusSnapshot
import com.example.data.preferences.AppPreferencesState
import com.example.data.preferences.SecurityVaultState
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.SilverOutlinedButton
import com.example.ui.components.TelemetryBadge
import com.example.ui.theme.LocalLuxuryColors
import com.example.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    context: Context,
    preferences: AppPreferencesState,
    permissions: PermissionStatusSnapshot,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetInAppPopup: (Boolean) -> Unit,
    onSetConnectionNotifications: (Boolean) -> Unit,
    onSetOverlayPopup: (Boolean) -> Unit,
    onSetAutoReconnectHelper: (Boolean) -> Unit,
    onPickCustomAlertSound: () -> Unit,
    onClearCustomAlertSound: () -> Unit,
    onSetLocalOnlyPrivacy: (Boolean) -> Unit,
    onClearLocalLogs: () -> Unit,
    onRequestBluetoothPermissions: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    vaultState: SecurityVaultState = SecurityVaultState(),
    isSecurityUnlocked: Boolean = true,
    onNavigateToSecurity: () -> Unit = {}
) {
    val lux = LocalLuxuryColors.current

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 780.dp)
                .fillMaxSize()
                .testTag("settings_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SectionHeaderRow(
                    title = "Settings, Privacy & Permissions",
                    subtitle = "${AppConstants.APP_NAME} · ${AppConstants.APP_VERSION}"
                )
            }

            // Theme Appearance
            item {
                LiquidGlassCard(elevated = true) {
                    Text("Appearance Theme", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = preferences.themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                modifier = Modifier.testTag("theme_chip_${mode.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Connection Popup & Notifications
            item {
                LiquidGlassCard {
                    Text("Connection Popups & Alerts", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(10.dp))

                    SettingSwitchRow(
                        title = "In-App Connection Popup",
                        subtitle = "Show animated Liquid Glass card when earbuds connect",
                        checked = preferences.showInAppConnectionPopup,
                        onCheckedChange = onSetInAppPopup,
                        testTag = "setting_inapp_popup"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingSwitchRow(
                        title = "Connection Notifications",
                        subtitle = "Post connection/disconnection notification fallback",
                        checked = preferences.connectionNotificationsEnabled,
                        onCheckedChange = onSetConnectionNotifications,
                        testTag = "setting_conn_notifications"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingSwitchRow(
                        title = "System Overlay Popup (Opt-In)",
                        subtitle = if (permissions.overlayGranted) "Overlay permission granted" else "Requires Display Over Other Apps permission",
                        checked = preferences.overlayPopupEnabled && permissions.overlayGranted,
                        onCheckedChange = { enabled ->
                            if (enabled && !permissions.overlayGranted) {
                                onRequestOverlayPermission()
                            } else {
                                onSetOverlayPopup(enabled)
                            }
                        },
                        testTag = "setting_overlay_popup"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingSwitchRow(
                        title = "Auto-Reconnect Prompt",
                        subtitle = "Offer quick reconnect telemetry & Bluetooth settings shortcut",
                        checked = preferences.autoReconnectHelperEnabled,
                        onCheckedChange = onSetAutoReconnectHelper,
                        testTag = "setting_auto_reconnect"
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SilverOutlinedButton(
                            text = "Alert Sound",
                            icon = Icons.Rounded.MusicNote,
                            onClick = onPickCustomAlertSound,
                            modifier = Modifier.weight(1f),
                            testTag = "pick_alert_sound_button"
                        )
                        if (preferences.customAlertSoundUri != null) {
                            SilverOutlinedButton(
                                text = "Reset Sound",
                                onClick = onClearCustomAlertSound,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Permission Manager
            item {
                LiquidGlassCard {
                    SectionHeaderRow(
                        title = "Permission Manager",
                        subtitle = "Minimum required permissions with transparent rationale"
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    PermissionStatusRow(
                        label = "Bluetooth Scan & Connect",
                        granted = permissions.hasBluetoothCorePermissions,
                        onGrant = onRequestBluetoothPermissions
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PermissionStatusRow(
                        label = "Post Notifications (Android 13+)",
                        granted = permissions.postNotificationsGranted,
                        onGrant = onRequestNotificationPermission
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PermissionStatusRow(
                        label = "Overlay Window (Optional Popup)",
                        granted = permissions.overlayGranted,
                        onGrant = onRequestOverlayPermission
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SilverOutlinedButton(
                            text = "App Settings",
                            icon = Icons.Rounded.Settings,
                            onClick = { PermissionManager.openAppSettings(context) },
                            modifier = Modifier.weight(1f)
                        )
                        SilverOutlinedButton(
                            text = "Notif Settings",
                            icon = Icons.Rounded.Notifications,
                            onClick = { PermissionManager.openNotificationSettings(context) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Security & Privacy
            item {
                val pinGateLocked = vaultState.isOwnerRegistered &&
                    vaultState.lockSensitiveSettingsWithPin &&
                    !isSecurityUnlocked
                LiquidGlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = if (pinGateLocked) lux.statusWarning else lux.statusPositive
                            )
                            Spacer(modifier = Modifier.widthIn(min = 8.dp))
                            Text("Security & Privacy", style = MaterialTheme.typography.titleMedium)
                        }
                        if (pinGateLocked) {
                            TelemetryBadge(text = "PIN LOCKED", accentColor = lux.statusWarning)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingSwitchRow(
                        title = "Strict Local-Only Mode",
                        subtitle = if (pinGateLocked) {
                            "Locked by Owner Security PIN — unlock in Security Center to modify"
                        } else {
                            "Zero internet telemetry, zero analytics, Room & DataStore only"
                        },
                        checked = preferences.localOnlyPrivacyEnabled,
                        onCheckedChange = { enabled ->
                            if (pinGateLocked) {
                                onNavigateToSecurity()
                            } else {
                                onSetLocalOnlyPrivacy(enabled)
                            }
                        },
                        testTag = "setting_local_only"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SilverOutlinedButton(
                        text = if (pinGateLocked) "Unlock Security Center to Clear Logs" else "Clear Local Device & Battery History",
                        icon = Icons.Rounded.DeleteSweep,
                        onClick = {
                            if (pinGateLocked) {
                                onNavigateToSecurity()
                            } else {
                                onClearLocalLogs()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "clear_local_logs_button"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SilverOutlinedButton(
                        text = "Open Owner Security Center",
                        icon = Icons.Rounded.Security,
                        onClick = onNavigateToSecurity,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "settings_open_security_button"
                    )
                }
            }

            // Help, Troubleshooting & Hardware Limitations
            item {
                LiquidGlassCard {
                    Text("Help, Troubleshooting & Hardware Notes", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Battery shows '--': Some earbuds report battery only over Android private metadata or HFP AT commands when actively playing audio.\n" +
                            "• OnePlus Nord Buds 3 Pro ANC/Touch: Firmware locks ANC modes behind authenticated HeyMelody frames. Use 'Open HeyMelody' inside HARDIK BUDS PRO.\n" +
                            "• 10-Band EQ Scope: Real-time 10-band Biquad DSP processes in-app Media3 audio; Android blocks third-party apps from overriding other apps' audio streams on non-rooted devices.\n" +
                            "• Version: ${AppConstants.VERSION_NAME} (${AppConstants.APP_VERSION})\n" +
                            "• Owner: ${AppConstants.OWNER_NAME}\n" +
                            "• ${AppConstants.COPYRIGHT_NOTICE}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun PermissionStatusRow(
    label: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    val lux = LocalLuxuryColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        if (granted) {
            TelemetryBadge(text = "GRANTED", accentColor = lux.statusPositive)
        } else {
            SilverOutlinedButton(text = "Grant", onClick = onGrant)
        }
    }
}
