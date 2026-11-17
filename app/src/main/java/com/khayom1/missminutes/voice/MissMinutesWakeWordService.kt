package com.khayom1.missminutes.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.khayom1.missminutes.MainActivity
import com.khayom1.missminutes.backup.AndroidAppSettingsStore
import java.util.Locale

/**
 * User-started, on-device-only wake phrase service.
 *
 * It deliberately fails closed when Android's on-device recognizer is unavailable;
 * it never falls back to a network recognizer or uploads microphone audio.
 */
class MissMinutesWakeWordService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var wakeHandled = false
    private val retryBackoff = RecognitionRetryBackoff()
    private var serviceCopy = VoiceServiceCopyProvider.forLanguage("en")

    private val restartListening = Runnable {
        if (active && !wakeHandled) startListening()
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        // Wait for the final transcript so a partial "Miss Minutes" prefix in a
        // longer utterance (for example, "Miss Minutes please") cannot wake the app.
        override fun onPartialResults(partialResults: Bundle?) = Unit

        override fun onResults(results: Bundle?) {
            if (containsExactWakePhrase(results)) {
                handleWakePhrase()
            } else {
                retryBackoff.reset()
                scheduleRestart(RESTART_DELAY_MS)
            }
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleRestart(retryBackoff.nextDelayMillis())
                else -> failClosed()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (WakeWordServicePolicy.shouldStop(intent?.action)) {
            stopWakeWordService()
            return START_NOT_STICKY
        }
        if (!WakeWordServicePolicy.shouldStart(intent?.action)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        serviceCopy = VoiceServiceCopyProvider.forLanguage(
            AndroidAppSettingsStore(this).loadAppearance().language
        )
        createNotificationChannel()
        if (active) return START_NOT_STICKY

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            publishState(false, serviceCopy.android12Required)
            postStatusNotification(serviceCopy.android12Required)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!isOnDeviceRecognizerAvailable()) {
            publishState(false, serviceCopy.recognizerUnavailable)
            postStatusNotification(
                serviceCopy.recognizerUnavailable
            )
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (!hasRequiredPermissions()) {
            publishState(false, serviceCopy.permissionsRequired)
            postStatusNotification(serviceCopy.permissionsRequired)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            startForegroundCompat()
            active = true
            wakeHandled = false
            retryBackoff.reset()
            publishState(true, serviceCopy.listeningState)
            if (!createOnDeviceRecognizer()) {
                failClosed()
                return START_NOT_STICKY
            }
            startListening()
        } catch (_: SecurityException) {
            failClosed()
        } catch (_: RuntimeException) {
            failClosed()
        }
        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun isOnDeviceRecognizerAvailable(): Boolean =
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(this) }.getOrDefault(false)

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createOnDeviceRecognizer(): Boolean {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) return false
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this).also {
            it.setRecognitionListener(recognitionListener)
        }
        return true
    }

    private fun hasRequiredPermissions(): Boolean {
        val microphoneGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        val notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val channelEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
        return microphoneGranted && notificationsGranted && notificationsEnabled && channelEnabled
    }

    private fun startForegroundCompat() {
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startListening() {
        if (!active || wakeHandled) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        try {
            recognizer?.startListening(intent) ?: failClosed()
        } catch (_: RuntimeException) {
            failClosed()
        }
    }

    private fun containsExactWakePhrase(bundle: Bundle?): Boolean {
        val candidates = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        return WakePhraseMatcher.matchesRecognitionCandidates(candidates)
    }

    private fun scheduleRestart(delayMillis: Long) {
        if (!active || wakeHandled) return
        mainHandler.removeCallbacks(restartListening)
        mainHandler.postDelayed(restartListening, delayMillis)
    }

    private fun handleWakePhrase() {
        if (wakeHandled || !active) return
        wakeHandled = true
        active = false
        mainHandler.removeCallbacks(restartListening)
        releaseRecognizer()
        publishState(false, serviceCopy.wakeHeard)
        stopForeground(STOP_FOREGROUND_REMOVE)
        postStatusNotification(serviceCopy.wakeHeard)
        stopSelf()
    }

    private fun failClosed() {
        active = false
        wakeHandled = true
        mainHandler.removeCallbacks(restartListening)
        releaseRecognizer()
        publishState(false, serviceCopy.stoppedWithError)
        stopForeground(STOP_FOREGROUND_REMOVE)
        postStatusNotification(serviceCopy.stoppedWithError)
        stopSelf()
    }

    private fun stopWakeWordService() {
        active = false
        wakeHandled = true
        mainHandler.removeCallbacks(restartListening)
        releaseRecognizer()
        publishState(false, serviceCopy.stopped)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publishState(enabled: Boolean, message: String) {
        WakeWordServiceState.setEnabled(this, enabled)
        sendBroadcast(
            Intent(ACTION_STATE_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_ENABLED, enabled)
                .putExtra(EXTRA_MESSAGE, message)
        )
    }

    private fun releaseRecognizer() {
        recognizer?.let { current ->
            runCatching { current.cancel() }
            runCatching { current.destroy() }
        }
        recognizer = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                serviceCopy.channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = serviceCopy.channelDescription
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            OPEN_REQUEST_CODE,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopService = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            Intent(this, MissMinutesWakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(serviceCopy.listeningTitle)
            .setContentText(serviceCopy.listeningText)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, serviceCopy.stopAction, stopService)
            .build()
    }

    private fun postStatusNotification(message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val openApp = PendingIntent.getActivity(
            this,
            OPEN_REQUEST_CODE,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Miss Minutes")
            .setContentText(message)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(STATUS_NOTIFICATION_ID, notification) }
    }

    override fun onDestroy() {
        active = false
        mainHandler.removeCallbacks(restartListening)
        releaseRecognizer()
        publishState(false, serviceCopy.stopped)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.khayom1.missminutes.voice.START_WAKE_LISTENING"
        const val ACTION_STOP = "com.khayom1.missminutes.voice.STOP_WAKE_LISTENING"
        const val ACTION_STATE_CHANGED = "com.khayom1.missminutes.voice.WAKE_STATE_CHANGED"
        const val EXTRA_ENABLED = "wake_enabled"
        const val EXTRA_MESSAGE = "wake_message"
        const val CHANNEL_ID = "miss_minutes_wake_phrase"
        const val NOTIFICATION_ID = 7301
        private const val STATUS_NOTIFICATION_ID = 7302
        private const val OPEN_REQUEST_CODE = 7303
        private const val STOP_REQUEST_CODE = 7304
        private const val RESTART_DELAY_MS = 500L
    }
}

object WakeWordServiceState {
    private const val PREFERENCES = "miss_minutes_voice_state"
    private const val ENABLED = "wake_word_enabled"
    private const val SERVICE_PID = "wake_service_pid"

    fun isEnabled(context: Context): Boolean {
        val preferences = context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val enabled = preferences.getBoolean(ENABLED, false)
        val recordedPid = preferences.getInt(SERVICE_PID, -1)
        if (enabled && recordedPid == Process.myPid()) return true
        if (enabled) {
            preferences.edit().putBoolean(ENABLED, false).remove(SERVICE_PID).apply()
        }
        return false
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val editor = context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED, enabled)
        if (enabled) editor.putInt(SERVICE_PID, Process.myPid()) else editor.remove(SERVICE_PID)
        editor.apply()
    }
}
