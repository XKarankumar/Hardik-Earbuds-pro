package com.example.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.core.AppConstants

/**
 * Optional floating connection card shown outside the app ONLY when the user has explicitly
 * enabled overlay popups and granted SYSTEM_ALERT_WINDOW permission. Falls back automatically
 * to BudsNotificationManager when overlay permission is not available.
 */
class ConnectionOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoDismiss = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME) ?: AppConstants.APP_NAME
        val statusText = intent?.getStringExtra(EXTRA_STATUS_TEXT) ?: "Connected"
        val batterySummary = intent?.getStringExtra(EXTRA_BATTERY_SUMMARY) ?: "Telemetry active"

        removeExistingOverlay()
        showLuxuryCard(deviceName, statusText, batterySummary)

        handler.removeCallbacks(autoDismiss)
        handler.postDelayed(autoDismiss, 6500L)
        return START_NOT_STICKY
    }

    private fun showLuxuryCard(deviceName: String, statusText: String, batterySummary: String) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val cardBg = GradientDrawable().apply {
            setColor(Color.parseColor("#EE0F1117"))
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), Color.parseColor("#66E5E9F2"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }

        val brandLabel = TextView(this).apply {
            text = "${AppConstants.APP_NAME} · ${statusText.uppercase()}"
            setTextColor(Color.parseColor("#7DD3FC"))
            textSize = 11f
        }

        val titleLabel = TextView(this).apply {
            text = deviceName
            setTextColor(Color.parseColor("#F6F8FC"))
            textSize = 18f
            setPadding(0, dp(4), 0, dp(4))
        }

        val batteryLabel = TextView(this).apply {
            text = batterySummary
            setTextColor(Color.parseColor("#9BA5B8"))
            textSize = 13f
            setPadding(0, 0, 0, dp(12))
        }

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val openButton = Button(this).apply {
            text = "Open Hub"
            setTextColor(Color.parseColor("#07080B"))
            setBackgroundColor(Color.parseColor("#E5E9F2"))
            setOnClickListener {
                val launch = Intent(this@ConnectionOverlayService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("open_popup", true)
                }
                startActivity(launch)
                stopSelf()
            }
        }

        val dismissButton = Button(this).apply {
            text = "Dismiss"
            setTextColor(Color.parseColor("#E5E9F2"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { stopSelf() }
        }

        actionsRow.addView(openButton)
        actionsRow.addView(dismissButton)

        root.addView(brandLabel)
        root.addView(titleLabel)
        root.addView(batteryLabel)
        root.addView(actionsRow)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(40)
            horizontalMargin = 0.04f
        }

        overlayView = root
        runCatching { wm.addView(root, params) }.onFailure { stopSelf() }
    }

    private fun removeExistingOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
            overlayView = null
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoDismiss)
        removeExistingOverlay()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_DEVICE_NAME = "extra_device_name"
        private const val EXTRA_STATUS_TEXT = "extra_status_text"
        private const val EXTRA_BATTERY_SUMMARY = "extra_battery_summary"

        fun showOrFallback(
            context: Context,
            allowOverlay: Boolean,
            deviceName: String,
            statusText: String,
            batterySummary: String
        ): Boolean {
            if (allowOverlay && Settings.canDrawOverlays(context)) {
                val intent = Intent(context, ConnectionOverlayService::class.java).apply {
                    putExtra(EXTRA_DEVICE_NAME, deviceName)
                    putExtra(EXTRA_STATUS_TEXT, statusText)
                    putExtra(EXTRA_BATTERY_SUMMARY, batterySummary)
                }
                context.startService(intent)
                return true
            }
            return false
        }
    }
}
