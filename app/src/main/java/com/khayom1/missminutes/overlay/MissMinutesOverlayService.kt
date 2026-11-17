package com.khayom1.missminutes.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.khayom1.missminutes.MainActivity
import com.khayom1.missminutes.backup.AndroidAppSettingsStore

/**
 * User-started floating entry point. It never listens to the microphone or executes
 * device actions; it only opens the app. Android's overlay permission is checked
 * again at every start and during dragging; the notification always exposes Stop.
 */
class MissMinutesOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var bubble: View? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (OverlayServicePolicy.shouldStop(action)) {
            stopSelf(startId)
            return OverlayServicePolicy.RESTART_POLICY
        }
        // START_STICKY may redeliver a null intent after process death. Never
        // recreate a user-controlled overlay without a fresh explicit tap.
        if (!OverlayServicePolicy.shouldStart(action)) {
            stopSelf(startId)
            return OverlayServicePolicy.RESTART_POLICY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf(startId)
            return OverlayServicePolicy.RESTART_POLICY
        }

        return try {
            val strings = OverlayServicePolicy.strings(
                AndroidAppSettingsStore(this).loadAppearance().language
            )
            createNotificationChannel(strings)
            val type = if (Build.VERSION.SDK_INT >= 34) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(strings), type)
            showBubble(strings)
            // Overlay is deliberately non-sticky: Android must not restart it
            // without the user's explicit foreground interaction.
            OverlayServicePolicy.RESTART_POLICY
        } catch (_: SecurityException) {
            stopSelf(startId)
            OverlayServicePolicy.RESTART_POLICY
        } catch (_: WindowManager.BadTokenException) {
            stopSelf(startId)
            OverlayServicePolicy.RESTART_POLICY
        } catch (_: IllegalStateException) {
            stopSelf(startId)
            OverlayServicePolicy.RESTART_POLICY
        } catch (_: IllegalArgumentException) {
            stopSelf(startId)
            OverlayServicePolicy.RESTART_POLICY
        }
    }

    private fun showBubble(strings: OverlayStrings) {
        if (bubble != null) return
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val view = TextView(this).apply {
            text = strings.bubbleLabel
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            contentDescription = strings.bubbleDescription
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(28).toFloat()
                setColor(Color.rgb(35, 43, 64))
                setStroke(dp(1), Color.rgb(150, 180, 255))
            }
            isClickable = true
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setOnClickListener {
                val launch = Intent(this@MissMinutesOverlayService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(launch)
            }
        }
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(180)
        }
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = layout.x
                    startY = layout.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (!dragged && (kotlin.math.abs(deltaX) > touchSlop ||
                            kotlin.math.abs(deltaY) > touchSlop)
                    ) dragged = true
                    if (dragged) {
                        if (!Settings.canDrawOverlays(this)) {
                            stopSelf()
                            return@setOnTouchListener true
                        }
                        val metrics = resources.displayMetrics
                        val bounded = OverlayServicePolicy.clampPosition(
                            x = startX - deltaX.toInt(),
                            y = startY + deltaY.toInt(),
                            screenWidthPixels = metrics.widthPixels,
                            screenHeightPixels = metrics.heightPixels,
                            bubbleWidthPixels = view.width,
                            bubbleHeightPixels = view.height
                        )
                        layout.x = bounded.x
                        layout.y = bounded.y
                        try {
                            manager.updateViewLayout(view, layout)
                        } catch (_: SecurityException) {
                            stopSelf()
                        } catch (_: WindowManager.BadTokenException) {
                            stopSelf()
                        } catch (_: IllegalArgumentException) {
                            stopSelf()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        manager.addView(view, layout)
        windowManager = manager
        bubble = view
    }

    private fun createNotificationChannel(strings: OverlayStrings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    strings.channelName,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = strings.channelDescription
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(strings: OverlayStrings): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MissMinutesOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(strings.notificationTitle)
            .setContentText(strings.notificationText)
            .setContentIntent(openIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, strings.stopAction, stopIntent)
            .build()
    }

    override fun onDestroy() {
        bubble?.let { view ->
            runCatching { windowManager?.removeViewImmediate(view) }
        }
        bubble = null
        windowManager = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.khayom1.missminutes.overlay.START"
        const val ACTION_STOP = "com.khayom1.missminutes.overlay.STOP"
        private const val CHANNEL_ID = "miss_minutes_overlay"
        private const val NOTIFICATION_ID = 4102
    }
}
