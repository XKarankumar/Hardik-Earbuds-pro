package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.bluetooth.BluetoothDashboardState
import com.example.data.local.BatteryHistoryEntity
import com.example.data.preferences.AppPreferencesState
import com.example.ui.components.BatteryRingGauge
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.SilverOutlinedButton
import com.example.ui.components.TelemetryBadge
import com.example.ui.theme.LocalLuxuryColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BatteryScreen(
    bluetoothState: BluetoothDashboardState,
    batteryLogs: List<BatteryHistoryEntity>,
    preferences: AppPreferencesState,
    onRefreshBattery: () -> Unit,
    onToggleLowBatteryAlert: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit
) {
    val lux = LocalLuxuryColors.current
    val active = bluetoothState.activeDevice
    val telemetry = active?.batteryTelemetry

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 780.dp)
                .fillMaxSize()
                .testTag("battery_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SectionHeaderRow(
                    title = "Battery Telemetry Dashboard",
                    subtitle = "Live hardware-reported battery levels & source verification",
                    trailing = {
                        SilverOutlinedButton(
                            text = "Poll GATT",
                            icon = Icons.Rounded.Refresh,
                            onClick = onRefreshBattery,
                            enabled = active != null,
                            testTag = "battery_refresh_button"
                        )
                    }
                )
            }

            item {
                LiquidGlassCard(elevated = true, cornerRadius = 28.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = active?.name ?: "No Active Earbuds",
                            style = MaterialTheme.typography.titleLarge
                        )
                        TelemetryBadge(
                            text = telemetry?.source?.label ?: "Unavailable",
                            accentColor = if (telemetry?.hasAnyBatteryValue == true) lux.statusPositive else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        BatteryRingGauge(
                            label = "Left Bud",
                            percent = telemetry?.leftPercent ?: telemetry?.overallPercent,
                            isCharging = telemetry?.isLeftCharging,
                            ringSize = 84.dp
                        )
                        BatteryRingGauge(
                            label = "Right Bud",
                            percent = telemetry?.rightPercent ?: telemetry?.overallPercent,
                            isCharging = telemetry?.isRightCharging,
                            ringSize = 84.dp
                        )
                        BatteryRingGauge(
                            label = "Case",
                            percent = telemetry?.casePercent,
                            isCharging = telemetry?.isCaseCharging,
                            ringSize = 84.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Accuracy & Source: ${telemetry?.source?.accuracySummary ?: "Connect Bluetooth earbuds to read GATT Battery Service (0x180F) or HFP battery broadcasts."}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Low Battery Alert Settings
            item {
                LiquidGlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Low Battery Notification", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Notify when reported level drops to ${preferences.lowBatteryThreshold}% or below",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.lowBatteryAlertsEnabled,
                            onCheckedChange = onToggleLowBatteryAlert,
                            modifier = Modifier.testTag("low_battery_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = preferences.lowBatteryThreshold.toFloat(),
                        onValueChange = { onThresholdChange(it.toInt()) },
                        valueRange = 5f..40f,
                        steps = 6,
                        enabled = preferences.lowBatteryAlertsEnabled
                    )
                }
            }

            // Battery Trend Chart (only drawn when real logs exist!)
            item {
                LiquidGlassCard {
                    SectionHeaderRow(
                        title = "Recorded Battery Trend",
                        subtitle = "Plotted exclusively from real hardware telemetry snapshots"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val points = remember(batteryLogs) {
                        batteryLogs
                            .mapNotNull { it.overallPercent ?: it.leftPercent ?: it.rightPercent }
                            .take(20)
                            .reversed()
                    }
                    if (points.size < 2) {
                        Text(
                            text = "At least 2 real battery telemetry snapshots are needed to plot a charge/discharge curve.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val lineColor = lux.telemetryAccent
                        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        ) {
                            listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
                                val y = size.height * frac
                                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                            }
                            val path = Path()
                            points.forEachIndexed { idx, pct ->
                                val x = if (points.size <= 1) 0f else (idx.toFloat() / (points.size - 1)) * size.width
                                val y = size.height - (pct.coerceIn(0, 100) / 100f) * size.height
                                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(
                                path = path,
                                color = lineColor,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }

            item {
                SectionHeaderRow(
                    title = "Battery Log History (${batteryLogs.size})",
                    subtitle = "Persisted locally in Room"
                )
            }

            if (batteryLogs.isEmpty()) {
                item {
                    LiquidGlassCard {
                        Text(
                            text = "No battery snapshots logged yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(batteryLogs, key = { it.id }) { log ->
                    val fmt = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }
                    LiquidGlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(log.deviceName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${log.sourceLabel} · ${fmt.format(Date(log.timestamp))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val displayPct = listOfNotNull(
                                log.leftPercent?.let { "L:$it%" },
                                log.rightPercent?.let { "R:$it%" },
                                log.casePercent?.let { "C:$it%" }
                            ).ifEmpty {
                                listOfNotNull(log.overallPercent?.let { "$it%" })
                            }.joinToString(" ")
                            TelemetryBadge(text = displayPct.ifEmpty { "--" })
                        }
                    }
                }
            }
        }
    }
}
