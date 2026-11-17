package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.bluetooth.BluetoothDashboardState
import com.example.core.AppConstants
import com.example.devices.ConnectionState
import com.example.ui.theme.LocalLuxuryColors

@Composable
fun ConnectionPopupSheet(
    visible: Boolean,
    bluetoothState: BluetoothDashboardState,
    onDismiss: () -> Unit,
    onConnectOrSettings: () -> Unit,
    onFastPair: () -> Unit,
    onOpenCompanionApp: (String, List<String>) -> Unit,
    onInspectGatt: (String) -> Unit
) {
    val lux = LocalLuxuryColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "buds_float")
    val floatOffsetY by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_y"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 360f),
            initialOffsetY = { it / 2 }
        ),
        exit = fadeOut(tween(180)) + slideOutVertically(
            animationSpec = tween(200),
            targetOffsetY = { it / 2 }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.68f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            LiquidGlassCard(
                elevated = true,
                cornerRadius = 32.dp,
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .testTag("connection_popup_card")
            ) {
                val active = bluetoothState.activeDevice
                val caps = bluetoothState.activeCapabilities
                val connState = bluetoothState.connectionState
                val battery = active?.batteryTelemetry

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(
                            color = when (connState) {
                                ConnectionState.CONNECTED -> lux.statusPositive
                                ConnectionState.CONNECTING -> lux.statusWarning
                                ConnectionState.DISCONNECTED -> lux.statusCritical
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${AppConstants.APP_NAME} · ${connState.label.uppercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = lux.telemetryAccent
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("popup_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_close)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = active?.name ?: "OnePlus Nord Buds 3 Pro / Audio Hub",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = when (connState) {
                        ConnectionState.CONNECTED -> active?.brand?.displayName?.plus(" · Active Audio Link")
                            ?: "Active Audio Link"
                        ConnectionState.CONNECTING -> "Establishing Bluetooth link..."
                        ConnectionState.DISCONNECTED -> "No earbuds currently connected"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(165.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF08090C)),
                    contentAlignment = Alignment.Center
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
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(y = floatOffsetY.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Real Battery Telemetry Row (Left, Right, Case — never invented)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BatteryRingGauge(
                        label = "Left",
                        percent = battery?.leftPercent ?: battery?.overallPercent,
                        isCharging = battery?.isLeftCharging,
                        ringSize = 66.dp
                    )
                    BatteryRingGauge(
                        label = "Right",
                        percent = battery?.rightPercent ?: battery?.overallPercent,
                        isCharging = battery?.isRightCharging,
                        ringSize = 66.dp
                    )
                    BatteryRingGauge(
                        label = "Case",
                        percent = battery?.casePercent,
                        isCharging = battery?.isCaseCharging,
                        ringSize = 66.dp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (battery?.hasAnyBatteryValue == true) {
                        "Source: ${battery.source.label}"
                    } else {
                        "Battery telemetry: ${battery?.source?.accuracySummary ?: "Connect earbuds to query standard Bluetooth/GATT battery"}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // ANC / Transparency Quick Controls (Honest hardware capability reporting)
                val companionName = caps?.companionAppName
                val companionPkgs = caps?.companionPackageNames.orEmpty()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SilverOutlinedButton(
                        text = if (companionName != null) "ANC ($companionName)" else "ANC Status",
                        icon = Icons.Rounded.GraphicEq,
                        onClick = {
                            if (companionName != null && companionPkgs.isNotEmpty()) {
                                onOpenCompanionApp(companionName, companionPkgs)
                            }
                        },
                        enabled = companionName != null,
                        modifier = Modifier.weight(1f),
                        testTag = "popup_anc_button"
                    )
                    SilverOutlinedButton(
                        text = if (companionName != null) "Transparency" else "Ambient",
                        icon = Icons.Rounded.Hearing,
                        onClick = {
                            if (companionName != null && companionPkgs.isNotEmpty()) {
                                onOpenCompanionApp(companionName, companionPkgs)
                            }
                        },
                        enabled = companionName != null,
                        modifier = Modifier.weight(1f),
                        testTag = "popup_transparency_button"
                    )
                }

                if (connState == ConnectionState.CONNECTED) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Hardware ANC confirmation is gated by OEM firmware; buttons open ${companionName ?: "system controls"} safely.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (active != null) {
                        MetallicPillButton(
                            text = "Refresh GATT",
                            icon = Icons.Rounded.Refresh,
                            onClick = { onInspectGatt(active.address) },
                            modifier = Modifier.weight(1f),
                            testTag = "popup_refresh_gatt_button"
                        )
                        SilverOutlinedButton(
                            text = "Bluetooth",
                            icon = Icons.Rounded.OpenInNew,
                            onClick = onConnectOrSettings,
                            modifier = Modifier.weight(1f),
                            testTag = "popup_bluetooth_settings_button"
                        )
                    } else {
                        MetallicPillButton(
                            text = "Pair Nearby",
                            icon = Icons.Rounded.Bluetooth,
                            onClick = onFastPair,
                            modifier = Modifier.weight(1f),
                            testTag = "popup_fast_pair_button"
                        )
                        SilverOutlinedButton(
                            text = "BT Settings",
                            icon = Icons.Rounded.BluetoothConnected,
                            onClick = onConnectOrSettings,
                            modifier = Modifier.weight(1f),
                            testTag = "popup_connect_settings_button"
                        )
                    }
                }
            }
        }
    }
}
