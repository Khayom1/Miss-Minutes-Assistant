package com.khayom1.missminutes

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.khayom1.missminutes.overlay.OverlayService
import kotlinx.coroutines.launch
import java.util.Locale

private val TvaBackground = Color(0xFF160D09)
private val TvaPanel = Color(0xFF28170F)
private val TvaOrange = Color(0xFFF28A35)
private val TvaText = Color(0xFFFFE8D2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = TvaOrange,
                onPrimary = Color(0xFF211006),
                background = TvaBackground,
                surface = TvaPanel,
                onSurface = TvaText,
                secondary = Color(0xFFE7B27F)
            )) {
                MissMinutesRoot(this)
            }
        }
    }
}

private data class ChatLine(val role: String, val text: String)

private fun resolveAssistantLanguage(context: android.content.Context): String {
    val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.resources.configuration.locales[0] ?: Locale.getDefault()
    } else {
        @Suppress("DEPRECATION") context.resources.configuration.locale ?: Locale.getDefault()
    }
    return if (locale.language.equals("ru", ignoreCase = true)) "ru" else "en"
}

private fun localizedGreeting(language: String): String = if (language == "ru") {
    "Так-так! Ну здравствуй, Хранитель Времени. Надеюсь, сегодня без очередного апокалипсиса временной линии?"
} else {
    "Tick-tock! Well, hello, Time-Keeper. I do hope we're avoiding another little timeline apocalypse today?"
}

@Composable
private fun MissMinutesRoot(activity: Activity) {
    val context = LocalContext.current
    val assistantLanguage = remember { resolveAssistantLanguage(context) }
    val prefs = remember { context.getSharedPreferences("tva_session", Activity.MODE_PRIVATE) }
    val memory = remember { MemoryDatabase(context.applicationContext) }
    val secrets = remember { SecretStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var firstRun by remember { mutableStateOf(!prefs.getBoolean("initialization_complete", false)) }
    var showSettings by remember { mutableStateOf(false) }
    var apiKeyDraft by remember { mutableStateOf(secrets.read("deepseek_api_key").orEmpty()) }
    var elevenLabsKeyDraft by remember { mutableStateOf(secrets.read("elevenlabs_api_key").orEmpty()) }
    var input by remember { mutableStateOf("") }
    var currentMotion by remember { mutableStateOf(MotionIntent.neutral()) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isThinking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("TVA SYSTEM / READY") }
    var recognizedText by remember { mutableStateOf<String?>(null) }

    val messages = remember {
        mutableStateListOf(
            ChatLine("assistant", localizedGreeting(assistantLanguage))
        )
    }

    val voice = remember(activity, assistantLanguage) { LocalizedVoiceController(activity, assistantLanguage) { recognizedText = it } }
    DisposableEffect(voice) { onDispose { voice.release() } }

    fun speak(text: String) {
        val elevenLabsKey = secrets.read("elevenlabs_api_key")
        if (elevenLabsKey.isNullOrBlank()) {
            status = "ELEVENLABS / API KEY REQUIRED"
            return
        }
        scope.launch {
            try {
                voice.speak(text, elevenLabsKey) { isPlaying ->
                    isSpeaking = isPlaying
                }
            } catch (error: Exception) {
                isSpeaking = false
                currentMotion = MotionIntent.neutral()
                status = "ELEVENLABS / ERROR"
                messages.add(ChatLine("assistant", "ElevenLabs: " + (error.message ?: "unknown error")))
            }
        }
    }

    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.listen() else status = "MICROPHONE / PERMISSION DENIED"
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startOverlay(activity) else status = "OVERLAY / NOTIFICATION PERMISSION DENIED"
    }

    LaunchedEffect(Unit) {
        memory.seedTvaArchive()
        if (!prefs.getBoolean("owner_seeded", false)) {
            memory.savePersonalMemory("owner", "Мой новый Хранитель Времени — Хайём Абдурахмонов.")
            prefs.edit().putBoolean("owner_seeded", true).apply()
        }
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || isThinking) return
        input = ""
        messages.add(ChatLine("user", message))
        memory.saveConversation("user", message)

        val localAction = AndroidActions.tryHandle(activity, message)
        if (localAction != null) {
            messages.add(ChatLine("assistant", localAction))
            memory.saveConversation("assistant", localAction)
            speak(localAction)
            return
        }

        if (message.contains("уведомлен", ignoreCase = true)) {
            val notifications = memory.recentNotifications()
            val response = if (notifications.isBlank()) {
                "Хм-м... В архиве уведомлений пока пусто. Проверь, выдан ли мне доступ к уведомлениям Android."
            } else {
                "Так-так, вот что происходит в твоём цифровом королевстве:\n" + notifications
            }
            messages.add(ChatLine("assistant", response))
            speak(response)
            return
        }

        val key = secrets.read("deepseek_api_key")
        if (key.isNullOrBlank()) {
            val response = "Ой-ой! Мои речевые нейросхемы ещё не подключены. Открой настройки и добавь DeepSeek API Key. И не вздумай записывать его на табличке у входа в TVA!"
            messages.add(ChatLine("assistant", response))
            speak(response)
            return
        }

        isThinking = true
        status = "TEMPORAL LINK / CONNECTING"
        scope.launch {
            try {
                val prompt = PersonalityEngine.buildSystemPrompt(memory.buildPromptContext(), assistantLanguage)
                val motionPrompt = prompt + """
ФОРМАТ ОТВЕТА: верни один JSON-объект без Markdown:
{"reply":"полный ответ","mood":"neutral|happy|curious|surprised|angry|sad|sleepy|playful","gaze":"viewer|left|right|up|down|wander","head_tilt":0.0,"body_lean":0.0,"gesture":"none|open_hands|wave|point|shrug|hands_together|present","energy":0.0,"emphasis":0.0}
Самостоятельно выбери эмоциональное и физическое поведение из смысла диалога. Не называй клипы, анимации или кости. Ограничивай head_tilt: -0.55..0.55 радиан; body_lean: -0.22..0.22; energy и emphasis: 0..1.
"""
                val rawTurn = DeepSeekClient.reply(key, motionPrompt, message)
                val turn = MotionIntent.parse(rawTurn)
                val response = turn.reply
                messages.add(ChatLine("assistant", response))
                memory.saveConversation("assistant", response)
                currentMotion = turn
                speak(response)
                status = "TVA SYSTEM / STABLE"
            } catch (error: Exception) {
                val response = "Так, минутку! Канал связи с DeepSeek временно схлопнулся: " +
                    (error.message ?: "неизвестная ошибка") +
                    ". Проверь интернет и API Key, Хранитель."
                messages.add(ChatLine("assistant", response))
                status = "TEMPORAL LINK / ERROR"
            } finally {
                isThinking = false
            }
        }
    }

    LaunchedEffect(recognizedText) {
        recognizedText?.let {
            recognizedText = null
            send(it)
        }
    }

    if (firstRun) {
        InitializationSequence(
            onComplete = {
                prefs.edit().putBoolean("initialization_complete", true).apply()
                firstRun = false
            }
        )
    } else {
        Dashboard(
            status = status,
            messages = messages,
            input = input,
            motion = if (isSpeaking) currentMotion.copy(gesture = "talk") else currentMotion,
            isThinking = isThinking,
            onInputChange = { input = it },
            onSend = { send(input) },
            onAnimationChange = { /* motion is produced by semantic intent, not manual clip selection */ },
            onSettings = {
                apiKeyDraft = secrets.read("deepseek_api_key").orEmpty()
                showSettings = true
            },
            onOverlay = {
                if (Settings.canDrawOverlays(activity)) {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        startOverlay(activity)
                    }
                    status = "OVERLAY / STARTING"
                } else {
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.packageName))
                    )
                    status = "OVERLAY / PERMISSION REQUIRED"
                }
            },
            onListen = {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) voice.listen() else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            },
            onSpeak = { text -> speak(text) },
            onNotificationSettings = { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            onAccessibilitySettings = { activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        )
    }

    if (showSettings) {
        SettingsDialog(
            apiKey = apiKeyDraft,
            onApiKeyChange = { apiKeyDraft = it },
            elevenLabsApiKey = elevenLabsKeyDraft,
            onElevenLabsApiKeyChange = { elevenLabsKeyDraft = it },
            onSave = {
                secrets.save("deepseek_api_key", apiKeyDraft.trim())
                secrets.save("elevenlabs_api_key", elevenLabsKeyDraft.trim())
                showSettings = false
                status = if (apiKeyDraft.isBlank()) "API KEY / EMPTY" else "API KEY / STORED SECURELY"
            },
            onDismiss = { showSettings = false },
            onNotificationSettings = {
                showSettings = false
                activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            onAccessibilitySettings = {
                showSettings = false
                activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )
    }
}

private fun startOverlay(activity: Activity) {
    if (Settings.canDrawOverlays(activity)) {
        ContextCompat.startForegroundService(activity, Intent(activity, OverlayService::class.java))
    }
}

@Composable
private fun Dashboard(
    status: String,
    messages: List<ChatLine>,
    input: String,
    motion: MotionIntent,
    isThinking: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onAnimationChange: (String) -> Unit,
    onSettings: () -> Unit,
    onOverlay: () -> Unit,
    onListen: () -> Unit,
    onSpeak: (String) -> Unit,
    onNotificationSettings: () -> Unit,
    onAccessibilitySettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(TvaBackground).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text("MISS MINUTES", color = TvaOrange, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 2.sp)
                Text(status, color = Color(0xFFB58B6C), fontSize = 10.sp, letterSpacing = 1.sp)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = TvaText)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = TvaPanel), shape = RoundedCornerShape(22.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                CharacterScene(Modifier.fillMaxWidth().height(285.dp), motion = motion, modelScale = 1.05f)
                Text("TVA CHARACTER // 3D LIVE RIG", color = Color(0xFFD6A57B), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }

        Text("LIVE BEHAVIOUR // AI DIRECTED", color = Color(0xFFD6A57B), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onOverlay,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = TvaOrange, contentColor = Color(0xFF251308))
            ) { Text("Показать поверх экрана", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = onSettings) { Text("Настройки", fontSize = 12.sp) }
        }

        Text("TEMPORAL COMMUNICATION", color = TvaOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Card(colors = CardDefaults.cardColors(containerColor = TvaPanel), shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                messages.takeLast(8).forEach { line ->
                    val isUser = line.role == "user"
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                    ) {
                        Text(
                            if (isUser) "TIME-KEEPER" else "MISS MINUTES",
                            color = if (isUser) Color(0xFFB7B3AA) else TvaOrange,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(line.text, color = TvaText, fontSize = 14.sp, lineHeight = 20.sp)
                        if (!isUser) {
                            TextButton(onClick = { onSpeak(line.text) }) {
                                Text("Прослушать", color = Color(0xFFD6A57B), fontSize = 10.sp)
                            }
                        }
                    }
                }
                if (isThinking) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = TvaOrange)
                        Text("Мисс Минутс проверяет временные линии...", color = Color(0xFFD6A57B), fontSize = 12.sp)
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Скажите что-нибудь...") },
                shape = RoundedCornerShape(18.dp),
                maxLines = 4
            )
            IconButton(onClick = onListen, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Mic, contentDescription = "Голосовой ввод", tint = TvaOrange)
            }
            IconButton(onClick = onSend, enabled = !isThinking, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Send, contentDescription = "Отправить", tint = TvaOrange)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onNotificationSettings, modifier = Modifier.weight(1f)) {
                Text("Доступ к уведомлениям", fontSize = 10.sp)
            }
            OutlinedButton(onClick = onAccessibilitySettings, modifier = Modifier.weight(1f)) {
                Text("Системные действия", fontSize = 10.sp)
            }
        }
        Text(
            "ElevenLabs озвучивает ответы Мисс Минутс; воспроизведение синхронизировано с Talk_Loop в 3D-модели. Ключ ElevenLabs хранится в Android Keystore и не включается в GitHub.",
            color = Color(0xFF9C806A),
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsDialog(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    elevenLabsApiKey: String,
    onElevenLabsApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onNotificationSettings: () -> Unit,
    onAccessibilitySettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("TVA / НАСТРОЙКИ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ElevenLabs API Key", color = TvaOrange, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = elevenLabsApiKey,
                    onValueChange = onElevenLabsApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text("ElevenLabs API Key") }
                )
                Text(
                    "Voice: ${LocalizedVoiceController.VOICE_NAME} · ${LocalizedVoiceController.VOICE_ID}. " +
                        "Русский и English — multilingual v2. Ключ хранится в Android Keystore.",
                    fontSize = 11.sp
                )
                Text("DeepSeek API Key", color = TvaOrange, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text("API Key") }
                )
                Text("Ключ шифруется Android Keystore и не попадает в репозиторий.", fontSize = 11.sp)
                OutlinedButton(onClick = onNotificationSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Разрешить чтение уведомлений")
                }
                OutlinedButton(onClick = onAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Открыть специальные возможности")
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("СОХРАНИТЬ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ОТМЕНА") } }
    )
}
