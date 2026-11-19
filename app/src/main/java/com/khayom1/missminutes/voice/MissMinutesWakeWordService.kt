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
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var awaitingCommand = false
    private var recognitionLanguageTag = Locale.US.toLanguageTag()
    private val retryBackoff = RecognitionRetryBackoff()
    private val executionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var executionJob: Job? = null
    private var resumeAfterCommand = false

    private val restartListening = Runnable {
        if (active && !wakeHandled) startListening()
    }
    private val commandWaitTimeout = Runnable {
        if (active && awaitingCommand) {
            awaitingCommand = false
            publishState(true, "No command heard. Still listening for “Miss Minutes”.")
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        // Final transcripts are used so partial hypotheses cannot dispatch commands.
        override fun onPartialResults(partialResults: Bundle?) = Unit

        override fun onResults(results: Bundle?) {
            val candidates = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val primaryTranscript = candidates.firstOrNull().orEmpty().trim()
            val match = WakePhraseMatcher.parse(primaryTranscript)
            when {
                match?.command != null -> handleWakePhrase(match.command)
                match != null -> {
                    awaitingCommand = true
                    mainHandler.removeCallbacks(commandWaitTimeout)
                    mainHandler.postDelayed(commandWaitTimeout, COMMAND_WAIT_TIMEOUT_MS)
                    publishState(true, "Miss Minutes heard. Say your command now.")
                    retryBackoff.reset()
                    scheduleRestart(RESTART_DELAY_MS)
                }
                awaitingCommand && primaryTranscript.isNotBlank() -> handleWakePhrase(primaryTranscript)
                else -> {
                    retryBackoff.reset()
                    scheduleRestart(RESTART_DELAY_MS)
                }
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
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopWakeWordService()
            return START_NOT_STICKY
        }
        if (active) return START_NOT_STICKY
        recognitionLanguageTag = intent
            ?.getStringExtra(EXTRA_RECOGNITION_LANGUAGE)
            ?.let(Locale::forLanguageTag)
            ?.takeIf { it.language.isNotBlank() }
            ?.toLanguageTag()
            ?: Locale.US.toLanguageTag()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            publishState(false, "On-device speech recognition requires Android 12 or later.")
            postStatusNotification("On-device speech recognition requires Android 12 or later.")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!isOnDeviceRecognizerAvailable()) {
            publishState(false, "On-device speech recognition is unavailable.")
            postStatusNotification(
                "On-device speech recognition is unavailable. Wake phrase listening was not started."
            )
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (!hasRequiredPermissions()) {
            publishState(false, "Microphone and notification permissions are required.")
            postStatusNotification("Microphone and notification permissions are required.")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            startForegroundCompat()
            active = true
            wakeHandled = false
            awaitingCommand = false
            mainHandler.removeCallbacks(commandWaitTimeout)
            retryBackoff.reset()
            publishState(true, "Offline wake phrase listening is active.")
            val recognizerCreated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                createOnDeviceRecognizer()
            } else {
                false
            }
            if (!recognizerCreated) {
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

    private fun startForegroundCompat(notification: Notification = buildForegroundNotification()) {
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLanguageTag)
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

    private fun scheduleRestart(delayMillis: Long) {
        if (!active || wakeHandled) return
        mainHandler.removeCallbacks(restartListening)
        mainHandler.postDelayed(restartListening, delayMillis)
    }

    private fun handleWakePhrase(command: String?) {
        if (wakeHandled || !active) return
        wakeHandled = true
        active = false
        awaitingCommand = false
        mainHandler.removeCallbacks(commandWaitTimeout)
        mainHandler.removeCallbacks(restartListening)
        releaseRecognizer()
        if (!command.isNullOrBlank()) {
            resumeAfterCommand = true
            executeCapturedCommand(command)
            return
        }
        val message = "Miss Minutes heard. Tap to open the assistant."
        publishState(false, message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        postStatusNotification(message)
        stopSelf()
    }

    private fun executeCapturedCommand(command: String) {
        publishState(false, "Miss Minutes is working in the app currently visible on your screen.")
        try {
            startForegroundCompat(buildWorkingNotification())
        } catch (_: Exception) {
            publishPendingCommand(
                command,
                "Voice command saved. Android did not allow the background automation session to start."
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        executionJob?.cancel()
        executionJob = executionScope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) {
                    BackgroundVoiceCommandExecutor.execute(applicationContext, command)
                }
                when (outcome) {
                    is BackgroundVoiceCommandExecutor.Outcome.NeedsSetup -> {
                        resumeAfterCommand = false
                        publishPendingCommand(command, outcome.message)
                        postStatusNotification(outcome.message)
                    }
                    is BackgroundVoiceCommandExecutor.Outcome.Completed -> {
                        PendingVoiceCommandStore.acknowledge(this@MissMinutesWakeWordService)
                        postStatusNotification(outcome.message)
                    }
                }
            } catch (error: CancellationException) {
                resumeAfterCommand = false
                throw error
            } catch (_: Exception) {
                PendingVoiceCommandStore.acknowledge(this@MissMinutesWakeWordService)
                postStatusNotification("Miss Minutes could not complete the background command.")
            } finally {
                if (resumeAfterCommand) {
                    resumeWakeListeningAfterCommand()
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                executionJob = null
            }
        }
    }

    private fun resumeWakeListeningAfterCommand() {
        resumeAfterCommand = false
        active = true
        wakeHandled = false
        awaitingCommand = false
        mainHandler.removeCallbacks(commandWaitTimeout)
        retryBackoff.reset()
        try {
            startForegroundCompat()
            val recognizerCreated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                createOnDeviceRecognizer()
            } else {
                false
            }
            if (!recognizerCreated) {
                failClosed()
                return
            }
            publishState(true, "Request finished. Offline wake phrase listening is active again.")
            startListening()
        } catch (_: Exception) {
            failClosed()
        }
    }

    private fun publishPendingCommand(command: String, message: String) {
        val stored = PendingVoiceCommandStore.save(this, command)
        val statusMessage = if (stored) {
            message
        } else {
            "Voice command could not be saved securely. Please repeat it after reopening Miss Minutes."
        }
        publishState(false, statusMessage)
        sendBroadcast(
            Intent(ACTION_STATE_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_ENABLED, false)
                .putExtra(EXTRA_MESSAGE, statusMessage)
        )
    }

    private fun buildWorkingNotification(): Notification {
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
            .setContentTitle("Miss Minutes is working")
            .setContentText("Controlling the app currently visible on your screen.")
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop", stopService)
            .build()
    }

    private fun failClosed() {
        active = false
        wakeHandled = true
        awaitingCommand = false
        mainHandler.removeCallbacks(commandWaitTimeout)
        mainHandler.removeCallbacks(restartListening)
        releaseRecognizer()
        publishState(false, "Wake phrase listening stopped. Check on-device speech recognition and permissions.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        postStatusNotification("Wake phrase listening stopped. Check on-device speech recognition and permissions.")
        stopSelf()
    }

    private fun stopWakeWordService() {
        active = false
        wakeHandled = true
        awaitingCommand = false
        resumeAfterCommand = false
        executionJob?.cancel()
        executionJob = null
        PendingVoiceCommandStore.acknowledge(this)
        mainHandler.removeCallbacks(commandWaitTimeout)
        mainHandler.removeCallbacks(restartListening)
        releaseRecognizer()
        publishState(false, "Offline wake phrase listening is off.")
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
                "Miss Minutes wake phrase",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Visible while the user-enabled offline wake phrase listener is active."
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
            .setContentTitle("Miss Minutes is listening")
            .setContentText("Offline wake phrase: “Miss Minutes”")
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop listening", stopService)
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
        awaitingCommand = false
        resumeAfterCommand = false
        executionJob?.cancel()
        executionScope.cancel()
        mainHandler.removeCallbacks(commandWaitTimeout)
        mainHandler.removeCallbacks(restartListening)
        releaseRecognizer()
        publishState(false, "Offline wake phrase listening stopped.")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.khayom1.missminutes.voice.START_WAKE_LISTENING"
        const val ACTION_STOP = "com.khayom1.missminutes.voice.STOP_WAKE_LISTENING"
        const val ACTION_STATE_CHANGED = "com.khayom1.missminutes.voice.WAKE_STATE_CHANGED"
        const val EXTRA_ENABLED = "wake_enabled"
        const val EXTRA_MESSAGE = "wake_message"
        const val EXTRA_RECOGNITION_LANGUAGE = "recognition_language"
        const val CHANNEL_ID = "miss_minutes_wake_phrase"
        const val NOTIFICATION_ID = 7301
        private const val STATUS_NOTIFICATION_ID = 7302
        private const val OPEN_REQUEST_CODE = 7303
        private const val STOP_REQUEST_CODE = 7304
        private const val RESTART_DELAY_MS = 500L
        private const val COMMAND_WAIT_TIMEOUT_MS = 15_000L
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


/** One-shot command handoff; stale voice commands expire after ten minutes. */
object PendingVoiceCommandStore {
    private const val PREFERENCES = "miss_minutes_pending_voice"
    private const val COMMAND = "command_ciphertext"
    private const val LEGACY_COMMAND = "command"
    private const val CREATED_AT = "created_at"
    private const val MAX_AGE_MS = 10 * 60 * 1000L
    private const val CIPHER_OWNER = "pending_voice_command_v1"
    private val cipher by lazy {
        com.khayom1.missminutes.memory.AndroidKeystoreConversationCipher(
            "miss_minutes_pending_voice_aes_v1"
        )
    }

    @Synchronized
    fun save(context: Context, command: String): Boolean {
        val safeCommand = command.trim().take(8_000)
        if (safeCommand.isEmpty()) return false
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return try {
            val encrypted = cipher.encrypt(CIPHER_OWNER, safeCommand)
            preferences.edit()
                .remove(LEGACY_COMMAND)
                .putString(COMMAND, encrypted)
                .putLong(CREATED_AT, System.currentTimeMillis())
                .commit()
        } catch (_: Exception) {
            preferences.edit()
                .remove(COMMAND)
                .remove(LEGACY_COMMAND)
                .remove(CREATED_AT)
                .commit()
            false
        }
    }

    @Synchronized
    fun peek(context: Context): String? {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.contains(LEGACY_COMMAND)) {
            preferences.edit().remove(COMMAND).remove(LEGACY_COMMAND).remove(CREATED_AT).commit()
            return null
        }
        val encrypted = preferences.getString(COMMAND, null)
        val createdAt = preferences.getLong(CREATED_AT, 0L)
        if (encrypted.isNullOrBlank() || createdAt <= 0L ||
            System.currentTimeMillis() - createdAt !in 0..MAX_AGE_MS
        ) {
            acknowledge(context)
            return null
        }
        return runCatching { cipher.decrypt(CIPHER_OWNER, encrypted) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it.length <= 8_000 }
            ?: run {
                acknowledge(context)
                null
            }
    }

    @Synchronized
    fun acknowledge(context: Context) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove(COMMAND)
            .remove(LEGACY_COMMAND)
            .remove(CREATED_AT)
            .commit()
    }
}
