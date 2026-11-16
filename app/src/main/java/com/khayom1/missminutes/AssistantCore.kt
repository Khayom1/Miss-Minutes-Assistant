package com.khayom1.missminutes

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.provider.AlarmClock
import android.util.Base64
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecretStore(context: Context) {
    private val preferences = context.getSharedPreferences("secure_secrets", Context.MODE_PRIVATE)
    private val alias = "miss_minutes_aes_key"

    @Synchronized
    fun save(name: String, value: String) {
        if (value.isBlank()) {
            check(preferences.edit().remove(name).commit()) { "Unable to remove encrypted secret" }
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = PAYLOAD_MAGIC + cipher.iv + encrypted
        check(preferences.edit().putString(name, Base64.encodeToString(payload, Base64.NO_WRAP)).commit()) {
            "Unable to persist encrypted secret"
        }
    }

    @Synchronized
    fun read(name: String): String? {
        return try {
            val encoded = preferences.getString(name, null) ?: return null
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            val versioned = payload.size > PAYLOAD_MAGIC.size + 12 &&
                payload.copyOfRange(0, PAYLOAD_MAGIC.size).contentEquals(PAYLOAD_MAGIC)
            val ivOffset = if (versioned) PAYLOAD_MAGIC.size else 0
            require(payload.size > ivOffset + 12)
            val key = getExistingKey() ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, payload.copyOfRange(ivOffset, ivOffset + 12)))
            if (versioned) cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
            val value = String(cipher.doFinal(payload.copyOfRange(ivOffset + 12, payload.size)), Charsets.UTF_8)
            if (!versioned) save(name, value)
            value
        } catch (_: Exception) {
            null
        }
    }

    private fun getExistingKey(): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(alias, null) as? SecretKey
    }

    private fun getOrCreateKey(): SecretKey {
        getExistingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        val PAYLOAD_MAGIC = byteArrayOf(0x4d.toByte(), 0x4d.toByte(), 0x4b.toByte(), 0x31.toByte())
    }
}
class MemoryDatabase(context: Context) : SQLiteOpenHelper(context, "miss_minutes_memory.db", null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE tva_memory(id INTEGER PRIMARY KEY AUTOINCREMENT, topic TEXT NOT NULL UNIQUE, body TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE personal_memory(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, body TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE notifications(id INTEGER PRIMARY KEY AUTOINCREMENT, package_name TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX idx_personal_time ON personal_memory(updated_at DESC)")
        db.execSQL("CREATE INDEX idx_notifications_time ON notifications(created_at DESC)")
        db.execSQL("CREATE INDEX idx_notifications_package_time ON notifications(package_name, created_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("CREATE INDEX IF NOT EXISTS idx_notifications_package_time ON notifications(package_name, created_at DESC)")
    }

    private companion object { const val DATABASE_VERSION = 2 }

    fun seedTvaArchive() {
        val entries = listOf(
            "TVA" to "Time Variance Authority — организация, управляющая временными линиями и вариантами событий.",
            "Loki" to "Локи — вариант, чья история тесно связана с TVA и временными линиями.",
            "He Who Remains" to "Тот, Кто Остаётся, создал структуру контроля TVA и связан с происхождением Мисс Минутс.",
            "Temporal Loom" to "Темпоральный Ткацкий Станок обрабатывал временные линии; его разрушение стало катастрофой для TVA.",
            "Miss Minutes" to "Архивная запись: Мисс Минутс — разумная анимированная часовая персонажка. Часть её памяти повреждена после катастрофы TVA.",
            "Ravonna Renslayer" to "Равонна Ренслейер — бывший судья TVA, связанная с управлением организации."
        )
        entries.forEach { (topic, body) ->
            val values = ContentValues().apply {
                put("topic", topic); put("body", body); put("updated_at", System.currentTimeMillis())
            }
            writableDatabase.insertWithOnConflict("tva_memory", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun savePersonalMemory(kind: String, body: String) {
        if (body.isBlank()) return
        val values = ContentValues().apply {
            put("kind", kind.take(40)); put("body", body.take(4000)); put("updated_at", System.currentTimeMillis())
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertOrThrow("personal_memory", null, values)
            db.execSQL("DELETE FROM personal_memory WHERE id NOT IN (SELECT id FROM personal_memory ORDER BY updated_at DESC, id DESC LIMIT 500)")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun saveConversation(role: String, body: String) = savePersonalMemory("dialogue_" + role.take(16), body)

    fun saveNotification(packageName: String, title: String, body: String) {
        if (title.isBlank() && body.isBlank()) return
        val values = ContentValues().apply {
            put("package_name", packageName.take(200))
            put("title", title.take(300))
            put("body", body.take(1200))
            put("created_at", System.currentTimeMillis())
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertOrThrow("notifications", null, values)
            db.execSQL("DELETE FROM notifications WHERE id NOT IN (SELECT id FROM notifications ORDER BY created_at DESC, id DESC LIMIT 50)")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun recentNotifications(): String = readRows(
        "SELECT package_name, title, body FROM notifications ORDER BY created_at DESC, id DESC LIMIT 8"
    ) { c -> "• " + c.getString(0) + " — " + c.getString(1) + ": " + c.getString(2) }

    fun buildPromptContext(): String {
        val tva = readRows("SELECT topic, body FROM tva_memory ORDER BY topic LIMIT 12") {
            it.getString(0) + ": " + it.getString(1)
        }
        val personal = readRows("SELECT kind, body FROM personal_memory ORDER BY updated_at DESC LIMIT 8") {
            it.getString(0) + ": " + it.getString(1)
        }
        return "TVA ARCHIVE:\n" + tva + "\n\nPERSONAL MEMORY:\n" + personal
    }

    private inline fun readRows(sql: String, map: (android.database.Cursor) -> String): String {
        val lines = ArrayList<String>()
        readableDatabase.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) lines.add(map(cursor))
        }
        return lines.joinToString("\n")
    }
}

object PersonalityEngine {
    fun buildSystemPrompt(memoryContext: String, language: String = "ru"): String =
        AppLocalization.systemPrompt(language, memoryContext)
}

internal const val MAX_USER_MESSAGE_CHARS = 12_000
private const val MAX_RESPONSE_BYTES = 1_048_576

internal fun parseDeepSeekChatCompletion(payload: String): String {
    val root = try { JSONObject(payload) } catch (_: Exception) {
        throw IllegalStateException("DeepSeek returned an invalid JSON response")
    }
    val choices = root.optJSONArray("choices") ?: throw IllegalStateException("DeepSeek response is missing choices")
    if (choices.length() == 0) throw IllegalStateException("DeepSeek returned no completion choices")
    val choice = choices.optJSONObject(0) ?: throw IllegalStateException("DeepSeek returned an invalid completion choice")
    when (choice.optString("finish_reason")) {
        "stop" -> Unit
        "length" -> throw IllegalStateException("DeepSeek response was truncated by the output-token limit")
        "content_filter" -> throw IllegalStateException("DeepSeek response was blocked by its content filter")
        "insufficient_system_resource" -> throw IllegalStateException("DeepSeek is temporarily unavailable")
        "aborted" -> throw IllegalStateException("DeepSeek aborted the response")
        else -> throw IllegalStateException("DeepSeek returned an incomplete or unsupported completion state")
    }
    val message = choice.optJSONObject("message") ?: throw IllegalStateException("DeepSeek response is missing its message")
    val content = (message.opt("content") as? String).orEmpty().trim()
    if (content.isBlank()) throw IllegalStateException("DeepSeek returned an empty response")
    return content
}

object DeepSeekClient {
    suspend fun reply(apiKey: String, systemPrompt: String, userMessage: String): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "DeepSeek API Key не задан" }
        require(userMessage.length <= MAX_USER_MESSAGE_CHARS) {
            "Сообщение слишком длинное: максимум $MAX_USER_MESSAGE_CHARS символов"
        }
        val connection = URL("https://api.deepseek.com/chat/completions").openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 45000
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer " + apiKey)
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")
        try {
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", userMessage))
            val body = JSONObject()
                .put("model", "deepseek-flash")
                .put("thinking", JSONObject().put("type", "disabled"))
                .put("stream", false)
                .put("max_tokens", 700)
                .put("temperature", 0.8)
                .put("messages", messages)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responsePayload = stream?.use { it.readBoundedText(MAX_RESPONSE_BYTES) }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching {
                    val error = JSONObject(responsePayload)
                    error.optJSONObject("error")?.optString("message").orEmpty().ifBlank { error.optString("message") }
                }.getOrNull().orEmpty().take(500)
                throw IllegalStateException("DeepSeek HTTP " + status + if (detail.isBlank()) "" else ": " + detail)
            }
            parseDeepSeekChatCompletion(responsePayload)
        } finally { connection.disconnect() }
    }
}
class RussianVoiceController(
    context: Context,
    private val onRecognized: (String) -> Unit,
    private val onListeningError: () -> Unit = {}
) :
    TextToSpeech.OnInitListener, RecognitionListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private val recognizer = if (SpeechRecognizer.isRecognitionAvailable(context)) {
        SpeechRecognizer.createSpeechRecognizer(context)
    } else null
    private var ready = false
    @Volatile private var languageTag = "ru-RU"
    @Volatile private var speechCompletion: (() -> Unit)? = null

    init { recognizer?.setRecognitionListener(this) }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            applyLanguage()
            tts.setSpeechRate(1.06f)
            tts.setPitch(1.10f)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    speechCompletion?.also { speechCompletion = null }?.invoke()
                }
                override fun onError(utteranceId: String?) {
                    speechCompletion?.also { speechCompletion = null }?.invoke()
                }
            })
        }
    }

    fun setLanguage(tag: String) {
        languageTag = tag
        if (ready) applyLanguage()
    }

    private fun applyLanguage() {
        val result = tts.setLanguage(Locale.forLanguageTag(languageTag))
        ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        if (!ready) {
            val fallbackTag = if (languageTag.startsWith("en", ignoreCase = true)) "en-US" else "ru-RU"
            val fallback = tts.setLanguage(Locale.forLanguageTag(fallbackTag))
            ready = fallback != TextToSpeech.LANG_MISSING_DATA && fallback != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    fun speak(text: String, onComplete: () -> Unit = {}) {
        if (!ready || text.isBlank()) {
            onComplete()
            return
        }
        speechCompletion = onComplete
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "miss-minutes-response")
        if (result == TextToSpeech.ERROR) {
            speechCompletion?.also { speechCompletion = null }?.invoke()
        }
    }

    fun listen() {
        val engine = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { engine.startListening(intent) }
    }

    override fun onResults(results: Bundle?) {
        val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        if (!spoken.isNullOrBlank()) onRecognized(spoken)
    }
    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onError(error: Int) { onListeningError() }
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    fun cancelListening() { runCatching { recognizer?.cancel() } }

    fun release() {
        cancelListening()
        recognizer?.destroy()
        tts.stop()
        speechCompletion?.also { speechCompletion = null }?.invoke()
        tts.shutdown()
    }
}

internal enum class LocalCommandType { HOME, BACK, TIMER, ALARM, OPEN_APP, NONE }

internal fun classifyLocalCommand(raw: String, language: String = "ru"): LocalCommandType {
    val code = AppLocalization.normalize(language)
    val command = raw.lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ").trimEnd('.', '!', '?')
    val home = setOf("домой", "на главный экран", "вернись домой", "вернись на главный экран", "перейди домой", "перейди на главный экран", "открой главный экран") + when (code) {
        "tg" -> setOf("хона", "ба хона", "экрани асосӣ", "ба экрани асосӣ", "ба саҳифаи асосӣ")
        "en" -> setOf("home", "go home", "home screen", "go to home screen", "return home")
        else -> emptySet()
    }
    val back = setOf("назад", "вернись назад", "шаг назад", "перейди назад") + when (code) {
        "tg" -> setOf("ақиб", "як қадам ақиб", "ба қафо")
        "en" -> setOf("back", "go back", "step back", "navigate back")
        else -> emptySet()
    }
    val timerPrefixes = when (code) {
        "tg" -> listOf("таймер мон", "таймерро мон", "таймерро фаъол кун", "таймер")
        "en" -> listOf("set a timer", "set timer", "start a timer", "start timer", "timer")
        else -> listOf("поставь таймер", "поставь мне таймер", "установи таймер", "установи мне таймер", "запусти таймер", "создай таймер", "таймер")
    }
    val alarmPrefixes = when (code) {
        "tg" -> listOf("ҳушдор мон", "соати зангдор мон", "будильник мон", "ҳушдор")
        "en" -> listOf("set an alarm", "set alarm", "create an alarm", "alarm")
        else -> listOf("поставь будильник", "поставь мне будильник", "установи будильник", "установи мне будильник", "создай будильник", "будильник")
    }
    val openPrefixes = when (code) {
        "tg" -> listOf("кушо", "оғоз кун", "фаъол кун")
        "en" -> listOf("open", "launch", "start")
        else -> listOf("открой", "запусти", "включи")
    }
    return when {
        command in home -> LocalCommandType.HOME
        command in back -> LocalCommandType.BACK
        timerPrefixes.any { command == it || command.startsWith("$it ") } -> LocalCommandType.TIMER
        alarmPrefixes.any { command == it || command.startsWith("$it ") } -> LocalCommandType.ALARM
        openPrefixes.any { command.startsWith("$it ") } -> LocalCommandType.OPEN_APP
        else -> LocalCommandType.NONE
    }
}

internal fun parseTimerDurationSeconds(value: String): Int? {
    val match = Regex("(\\d{1,3})\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|сония(?:ҳо)?|дақиқа(?:ҳо)?|соат(?:ҳо)?|секунд|секунды|секунду|сек|минут|минуты|минуту|мин|час|часа|часов)")
        .find(value.lowercase(Locale.ROOT)) ?: return null
    val amount = match.groupValues[1].toIntOrNull() ?: return null
    val unit = match.groupValues[2]
    val seconds = when {
        unit.startsWith("сек") || unit.startsWith("сон") || unit.startsWith("sec") -> amount
        unit.startsWith("hour") || unit.startsWith("hr") || unit.startsWith("час") || unit.startsWith("соат") -> amount * 3600
        else -> amount * 60
    }
    return seconds.takeIf { it in 1..86_400 }
}

object AndroidActions {
    fun tryHandle(context: Context, raw: String, language: String = "ru"): String? {
        val code = AppLocalization.normalize(language)
        val text = raw.lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ").trimEnd('.', '!', '?')
        fun message(key: String, vararg args: Any): String =
            String.format(Locale.ROOT, AppLocalization.text(code, key), *args)
        when (classifyLocalCommand(text, code)) {
            LocalCommandType.HOME -> return if (DeviceAccessibilityService.performHome()) message("action_home_success")
                else message("action_accessibility_needed")
            LocalCommandType.BACK -> return if (DeviceAccessibilityService.performBack()) message("action_back_success")
                else message("action_accessibility_needed")
            LocalCommandType.TIMER -> {
                val match = Regex("(\\\\d{1,3})\\\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|сония(?:ҳо)?|дақиқа(?:ҳо)?|соат(?:ҳо)?|секунд|секунды|секунду|сек|минут|минуты|минуту|мин|час|часа|часов)").find(text)
                    ?: return message("action_timer_usage")
                val amount = match.groupValues[1].toIntOrNull() ?: return message("action_time_invalid")
                val unit = match.groupValues[2]
                val seconds = when {
                    unit.startsWith("сек") || unit.startsWith("сон") -> amount
                    unit.startsWith("hour") || unit.startsWith("hr") || unit.startsWith("час") || unit.startsWith("соат") -> amount * 3600
                    else -> amount * 60
                }
                if (seconds <= 0 || seconds > 86_400) return message("action_time_invalid")
                val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, "Miss Minutes — reminder")
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                return launch(context, intent, message("action_timer_success"))
            }
            LocalCommandType.ALARM -> {
                val match = Regex("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b").find(text)
                    ?: return message("action_alarm_usage")
                val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, match.groupValues[1].toInt())
                    .putExtra(AlarmClock.EXTRA_MINUTES, match.groupValues[2].toInt())
                    .putExtra(AlarmClock.EXTRA_MESSAGE, "Miss Minutes")
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                return launch(context, intent, message("action_alarm_success", match.value))
            }
            LocalCommandType.OPEN_APP -> {
                val prefix = when (code) {
                    "tg" -> listOf("кушо", "оғоз кун", "фаъол кун")
                    "en" -> listOf("open", "launch", "start")
                    else -> listOf("открой", "запусти", "включи")
                }.firstOrNull { text.startsWith("$it ") }.orEmpty()
                val requested = text.removePrefix(prefix).trim()
                if (requested.isBlank()) return null
                val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val item = context.packageManager.queryIntentActivities(query, 0).firstOrNull {
                    it.loadLabel(context.packageManager).toString().lowercase(Locale.ROOT).contains(requested)
                }
                if (item != null) {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(item.activityInfo.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        return try {
                            context.startActivity(launchIntent)
                            message("action_open_success", item.loadLabel(context.packageManager).toString())
                        } catch (_: Exception) { message("action_open_failed", requested) }
                    }
                }
                return message("action_not_found", requested)
            }
            LocalCommandType.NONE -> return null
        }
    }

    private fun launch(context: Context, intent: Intent, success: String): String = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        success
    } catch (_: Exception) {
        AppLocalization.text("en", "action_clock_unsupported")
    }
}
class NotificationReaderService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val databaseDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) { MemoryDatabase(applicationContext) }
    private val database: MemoryDatabase get() = databaseDelegate.value

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val item = sbn ?: return
        val extras = item.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val body = extras.getCharSequence("android.text")?.toString().orEmpty()
        if (title.isBlank() && body.isBlank()) return
        val packageName = item.packageName
        serviceScope.launch { runCatching { database.saveNotification(packageName, title, body) } }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (databaseDelegate.isInitialized()) databaseDelegate.value.close()
        super.onDestroy()
    }
}

class DeviceAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { super.onServiceConnected(); current = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }
    companion object {
        @Volatile private var current: DeviceAccessibilityService? = null
        fun performHome(): Boolean = current?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: false
        fun performBack(): Boolean = current?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ?: false
    }
}
