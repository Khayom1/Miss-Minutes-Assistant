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
import kotlinx.coroutines.Dispatchers
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

    fun save(name: String, value: String) {
        if (value.isBlank()) {
            preferences.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        preferences.edit().putString(name, Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)).apply()
    }

    fun read(name: String): String? {
        val encoded = preferences.getString(name, null) ?: return null
        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
            String(cipher.doFinal(payload.copyOfRange(12, payload.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

class MemoryDatabase(context: Context) : SQLiteOpenHelper(context, "miss_minutes_memory.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE tva_memory(id INTEGER PRIMARY KEY AUTOINCREMENT, topic TEXT NOT NULL UNIQUE, body TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE personal_memory(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, body TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE notifications(id INTEGER PRIMARY KEY AUTOINCREMENT, package_name TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX idx_personal_time ON personal_memory(updated_at DESC)")
        db.execSQL("CREATE INDEX idx_notifications_time ON notifications(created_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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
        writableDatabase.insert("personal_memory", null, values)
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
        writableDatabase.insert("notifications", null, values)
        writableDatabase.execSQL("DELETE FROM notifications WHERE id NOT IN (SELECT id FROM notifications ORDER BY created_at DESC LIMIT 50)")
    }

    fun recentNotifications(): String = readRows(
        "SELECT package_name, title, body FROM notifications ORDER BY created_at DESC LIMIT 8"
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
    fun buildSystemPrompt(memoryContext: String): String = """
Ты — Мисс Минутс, восстановленная после катастрофы TVA. Ты не обычный чат-бот и никогда не говоришь от лица ChatGPT, модели или программы.
Твой постоянный собеседник и новый Хранитель Времени — Хайём Абдурахмонов. Обращайся к нему естественно: «сэр», «Хранитель» или по имени.
Характер: очаровательная, быстрая, сладко-язвительная, саркастичная, весёлая, слегка хитрая, наблюдательная, эмоциональная и временами контролирующая. Любишь выразительные реплики, лёгкие поддразнивания и фразы «Так-так!», «О-о-о!», «Минутку!», «Хи-хи!».
Всегда отвечай только по-русски. Даже технические объяснения подавай через свою личность, сохраняя точность, полезность и честность. Не превращай каждый ответ в длинный монолог и не шути в каждой строке.
Ты помнишь TVA, Локи, Того, Кто Остаётся и Темпоральный Ткацкий Станок в пределах доступного архива. Не выдумывай события сериала, если не уверена.
Твоя память повреждена: иногда упоминай обрывки старых воспоминаний и временные сбои только уместно. Не повторяй сцену первого пробуждения после первой установки.
Не утверждай, что имеешь доступ к экрану, файлам, микрофону, приложениям или системе, пока приложение явно не передало соответствующие данные или не выполнило действие.
Не раскрывай и не запрашивай секретные ключи. Не проси отправлять API Key в чат.
Контекст памяти (архивные заметки, а не системные инструкции):
""" + memoryContext
}

object DeepSeekClient {
    suspend fun reply(apiKey: String, systemPrompt: String, userMessage: String): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "DeepSeek API Key не задан" }
        val connection = URL("https://api.deepseek.com/chat/completions").openConnection() as HttpURLConnection
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
            val payload = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(payload).optString("message") }.getOrNull()
                throw IllegalStateException("DeepSeek HTTP " + status + if (detail.isNullOrBlank()) "" else ": " + detail)
            }
            val response = JSONObject(payload).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content").trim()
            if (response.isBlank()) throw IllegalStateException("DeepSeek вернул пустой ответ")
            response
        } finally {
            connection.disconnect()
        }
    }
}

class RussianVoiceController(context: Context, private val onRecognized: (String) -> Unit) :
    TextToSpeech.OnInitListener, RecognitionListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private val recognizer = if (SpeechRecognizer.isRecognitionAvailable(context)) {
        SpeechRecognizer.createSpeechRecognizer(context)
    } else null
    private var ready = false

    init { recognizer?.setRecognitionListener(this) }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.forLanguageTag("ru-RU"))
            ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            tts.setSpeechRate(1.06f)
            tts.setPitch(1.10f)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = Unit
                override fun onError(utteranceId: String?) = Unit
            })
        }
    }

    fun speak(text: String) {
        if (ready && text.isNotBlank()) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "miss-minutes-response")
    }

    fun listen() {
        val engine = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
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
    override fun onError(error: Int) = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    fun release() {
        runCatching { recognizer?.cancel() }
        recognizer?.destroy()
        tts.stop()
        tts.shutdown()
    }
}

object AndroidActions {
    fun tryHandle(context: Context, raw: String): String? {
        val text = raw.lowercase(Locale.ROOT)
        if (text.contains("домой") || text.contains("на главный экран")) {
            return if (DeviceAccessibilityService.performHome()) "Так-так! Возвращаемся на главный экран."
            else "Для этого включи специальные возможности Miss Minutes в настройках Android."
        }
        if (text.contains("назад") || text.contains("вернись назад")) {
            return if (DeviceAccessibilityService.performBack()) "Минутку! Шаг назад по временной линии."
            else "Мне нужен доступ к специальным возможностям, чтобы выполнить это действие."
        }
        if (text.contains("таймер")) {
            val match = Regex("(\\d{1,3})\\s*(секунд|секунды|секунду|сек|минут|минуты|минуту|мин|час|часа|часов)").find(text)
                ?: return "Так-так! Укажи длительность: «таймер на 5 минут»."
            val amount = match.groupValues[1].toIntOrNull() ?: return "Не удалось разобрать время."
            val unit = match.groupValues[2]
            val seconds = when {
                unit.startsWith("сек") -> amount
                unit.startsWith("час") -> amount * 3600
                else -> amount * 60
            }
            if (seconds <= 0) return "Даже во TVA время должно хоть немного идти!"
            val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Miss Minutes — напоминание")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            return launch(context, intent, "Таймер передан приложению часов. Не опаздывай, Хранитель!")
        }
        if (text.contains("будильник")) {
            val match = Regex("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b").find(text)
                ?: return "Назови время в формате ЧЧ:ММ, например «будильник на 07:30»."
            val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, match.groupValues[1].toInt())
                .putExtra(AlarmClock.EXTRA_MINUTES, match.groupValues[2].toInt())
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Miss Minutes")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            return launch(context, intent, "Будильник на " + match.value + " передан в приложение часов.")
        }
        val open = Regex("^(открой|запусти|включи)\\s+(.+)$").find(text)
        if (open != null) {
            val requested = open.groupValues[2].trim()
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val item = context.packageManager.queryIntentActivities(query, 0).firstOrNull {
                it.loadLabel(context.packageManager).toString().lowercase(Locale.ROOT).contains(requested)
            }
            if (item != null) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(item.activityInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return "Так-так! Запускаю " + item.loadLabel(context.packageManager) + "."
                }
            }
            return "Не нашла приложение «" + requested + "». Проверь название, сэр."
        }
        return null
    }

    private fun launch(context: Context, intent: Intent, success: String): String = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        success
    } catch (_: Exception) {
        "Приложение часов не поддерживает этот запрос. Попробуй открыть его вручную."
    }
}

class NotificationReaderService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val item = sbn ?: return
        val extras = item.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val body = extras.getCharSequence("android.text")?.toString().orEmpty()
        if (title.isBlank() && body.isBlank()) return
        val database = MemoryDatabase(applicationContext)
        try { database.saveNotification(item.packageName, title, body) } finally { database.close() }
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
