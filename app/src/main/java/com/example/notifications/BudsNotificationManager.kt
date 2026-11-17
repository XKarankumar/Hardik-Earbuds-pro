package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.core.AppConstants
import com.example.core.PermissionManager
import com.example.devices.BatteryTelemetry

object BudsNotificationManager {

    fun ensureChannels(context: Context, customSoundUri: Uri? = null) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val audioAttr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val connectionChannel = NotificationChannel(
            AppConstants.CHANNEL_CONNECTION,
            context.getString(R.string.notif_channel_connection_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_connection_desc)
            if (customSoundUri != null) {
                setSound(customSoundUri, audioAttr)
            }
        }

        val batteryChannel = NotificationChannel(
            AppConstants.CHANNEL_BATTERY,
            context.getString(R.string.notif_channel_battery_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_battery_desc)
        }

        val playbackChannel = NotificationChannel(
            AppConstants.CHANNEL_PLAYBACK,
            context.getString(R.string.notif_channel_playback_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_playback_desc)
        }

        manager.createNotificationChannel(connectionChannel)
        manager.createNotificationChannel(batteryChannel)
        manager.createNotificationChannel(playbackChannel)
    }

    fun showConnectionNotification(
        context: Context,
        deviceName: String,
        connected: Boolean,
        battery: BatteryTelemetry?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionManager.snapshot(context).postNotificationsGranted
        ) {
            return
        }
        ensureChannels(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_popup", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            101,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val batteryText = if (connected && battery != null && battery.hasAnyBatteryValue) {
            buildList {
                battery.leftPercent?.let { add("L $it%") }
                battery.rightPercent?.let { add("R $it%") }
                battery.casePercent?.let { add("Case $it%") }
                if (battery.leftPercent == null && battery.rightPercent == null) {
                    battery.overallPercent?.let { add("Battery $it%") }
                }
            }.joinToString(" · ")
        } else if (connected) {
            "Connected · Tap to open HARDIK BUDS PRO hub"
        } else {
            "Disconnected · Tap to reconnect or manage devices"
        }

        val notification = NotificationCompat.Builder(context, AppConstants.CHANNEL_CONNECTION)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (connected) "$deviceName Connected" else "$deviceName Disconnected")
            .setContentText(batteryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(batteryText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(AppConstants.NOTIF_ID_CONNECTION, notification)
        }
    }

    fun showLowBatteryNotification(
        context: Context,
        deviceName: String,
        percent: Int
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionManager.snapshot(context).postNotificationsGranted
        ) {
            return
        }
        ensureChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            102,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, AppConstants.CHANNEL_BATTERY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Low Earbuds Battery: $percent%")
            .setContentText("$deviceName is running low on battery. Place earbuds in case to recharge.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(AppConstants.NOTIF_ID_BATTERY, notification)
        }
    }
}
