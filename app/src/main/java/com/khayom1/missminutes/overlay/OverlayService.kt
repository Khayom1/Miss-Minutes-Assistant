package com.khayom1.missminutes.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewTreeLifecycleOwner
import com.khayom1.missminutes.CharacterScene
import com.khayom1.missminutes.MainActivity
import kotlinx.coroutines.delay
import kotlin.math.abs

class OverlayService : LifecycleService() {
    private lateinit var manager: WindowManager
    private lateinit var frame: DraggableFrame
    private lateinit var layout: WindowManager.LayoutParams
    private var attached = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        layout = WindowManager.LayoutParams(
            (218 * density).toInt(),
            (250 * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * density).toInt()
            y = (150 * density).toInt()
        }
        frame = DraggableFrame(this) { dx, dy ->
            layout.x += dx
            layout.y += dy
            runCatching { manager.updateViewLayout(frame, layout) }
        }
        val compose = ComposeView(this).apply {
            ViewTreeLifecycleOwner.set(this, this@OverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                OverlayCharacter(
                    onStop = { stopSelf() },
                    onCompact = { compact ->
                        layout.width = ((if (compact) 78 else 218) * density).toInt()
                        layout.height = ((if (compact) 78 else 250) * density).toInt()
                        runCatching { manager.updateViewLayout(frame, layout) }
                    }
                )
            }
        }
        frame.addView(compose, FrameLayout.LayoutParams(-1, -1))
        try {
            manager.addView(frame, layout)
            attached = true
        } catch (_: Exception) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        if (attached) runCatching { manager.removeView(frame) }
        attached = false
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Miss Minutes overlay", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Мисс Минутс активна")
            .setContentText("Перетащи персонажа по экрану.")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Остановить", stop)
            .build()
    }

    private class DraggableFrame(context: Context, private val move: (Int, Int) -> Unit) : FrameLayout(context) {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var startX = 0f
        private var startY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var drag = false

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX; startY = event.rawY
                    lastX = startX; lastY = startY
                    drag = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - startX) > slop || abs(event.rawY - startY) > slop) drag = true
                }
            }
            return drag
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val x = event.rawX; val y = event.rawY
                    move((x - lastX).toInt(), (y - lastY).toInt())
                    lastX = x; lastY = y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drag = false
            }
            return true
        }
    }

    companion object {
        private const val CHANNEL_ID = "miss_minutes_overlay"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_STOP = "com.khayom1.missminutes.action.STOP_OVERLAY"
    }
}

@Composable
private fun OverlayCharacter(onStop: () -> Unit, onCompact: (Boolean) -> Unit) {
    var compact by remember { mutableStateOf(false) }
    var clip by remember { mutableStateOf("Idle_Breathe") }

    LaunchedEffect(compact) {
        if (!compact) {
            while (true) {
                delay(26000)
                clip = "Look_Around"
                delay(2200)
                clip = "Blink"
                delay(700)
                clip = "Idle_Breathe"
            }
        }
    }

    if (compact) {
        Surface(color = Color(0xEE5C2D17), shape = CircleShape, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("MM", color = Color(0xFFFFE8D2), fontWeight = FontWeight.Black, fontSize = 20.sp)
                IconButton(
                    onClick = { compact = false; onCompact(false) },
                    modifier = Modifier.align(Alignment.BottomEnd).size(28.dp)
                ) { Icon(Icons.Default.OpenInFull, contentDescription = "Развернуть", tint = Color.White) }
            }
        }
    } else {
        Surface(color = Color(0xE91A100B), shape = CircleShape, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TVA / MM", color = Color(0xFFFFB46D), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row {
                        IconButton(onClick = { compact = true; onCompact(true) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Remove, contentDescription = "Свернуть", tint = Color.White)
                        }
                        IconButton(onClick = onStop, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
                        }
                    }
                }
                CharacterScene(Modifier.fillMaxWidth().height(205.dp), clip)
                Text("Так-так, я здесь!", color = Color(0xFFFFE8D2), fontSize = 11.sp, modifier = Modifier.padding(bottom = 5.dp))
            }
        }
    }
}
