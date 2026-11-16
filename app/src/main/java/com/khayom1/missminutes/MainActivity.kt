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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.khayom1.missminutes.overlay.OverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

internal data class ChatLine(val role: String, val text: String)

internal const val MAX_VISIBLE_MESSAGES = 100

internal fun appendMessage(messages: MutableList<ChatLine>, line: ChatLine) {
    if (messages.size >= MAX_VISIBLE_MESSAGES) messages.removeAt(0)
    messages.add(line)
}

@Composable
private fun MissMinutesRoot(activity: Activity) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("tva_session", Activity.MODE_PRIVATE) }
    val memory = remember { MemoryDatabase(context.applicationContext) }
    DisposableEffect(memory) { onDispose { memory.close() } }
    val secrets = remember { SecretStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var firstRun by remember { mutableStateOf(!prefs.getBoolean("initialization_complete", false)) }
    var showSettings by remember { mutableStateOf(false) }
    var apiKeyDraft by remember { mutableStateOf("") }
    var providerNameDraft by remember { mutableStateOf("DeepSeek") }
    var providerBaseUrlDraft by remember { mutableStateOf("https://api.deepseek.com") }
    var providerModelDraft by remember { mutableStateOf("deepseek-flash") }
    var input by remember { mutableStateOf("") }
    var selectedAnimation by remember { mutableStateOf("Idle_Breathe") }
    var isThinking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("TVA SYSTEM / READY") }
    var recognizedText by remember { mutableStateOf<String?>(null) }

    val messages = remember {
        mutableStateListOf(
            ChatLine("assistant", "Так-так! Ну здравствуй, Хранитель Времени. Надеюсь, сегодня без очередного апокалипсиса временной линии?")
        )
    }

    val voice = remember(activity) { RussianVoiceController(activity) { recognizedText = it } }
    DisposableEffect(voice) { onDispose { voice.release() } }
    val activityLifecycle = (activity as? LifecycleOwner)?.lifecycle
    DisposableEffect(voice, activityLifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) voice.cancelListening()
        }
        activityLifecycle?.addObserver(observer)
        onDispose { activityLifecycle?.removeObserver(observer) }
    }

    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.listen() else status = "MICROPHONE / PERMISSION DENIED"
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startOverlay(activity) else status = "OVERLAY / NOTIFICATION PERMISSION DENIED"
    }

    LaunchedEffect(Unit) {
        providerNameDraft = prefs.getString("ai_provider_name", "DeepSeek") ?: "DeepSeek"
        providerBaseUrlDraft = prefs.getString("ai_provider_base_url", "https://api.deepseek.com") ?: "https://api.deepseek.com"
        providerModelDraft = prefs.getString("ai_provider_model", "deepseek-flash") ?: "deepseek-flash"
        apiKeyDraft = withContext(Dispatchers.IO) {
            (secrets.read("ai_primary_api_key") ?: secrets.read("deepseek_api_key")).orEmpty()
        }
        withContext(Dispatchers.IO) {
            memory.seedTvaArchive()
            if (!prefs.getBoolean("owner_seeded", false)) {
                memory.savePersonalMemory("owner", "Мой новый Хранитель Времени — Хайём Абдурахмонов.")
                prefs.edit().putBoolean("owner_seeded", true).apply()
            }
        }
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || isThinking) return
        input = ""
        appendMessage(messages, ChatLine("user", message))
        scope.launch(Dispatchers.IO) { memory.saveConversation("user", message) }
        val localAction = AndroidActions.tryHandle(activity, message)
        if (localAction != null) {
            appendMessage(messages, ChatLine("assistant", localAction))
            scope.launch(Dispatchers.IO) { memory.saveConversation("assistant", localAction) }
            voice.speak(localAction)
            return
        }
        if (message.contains("уведомлен", ignoreCase = true)) {
            scope.launch {
                val notifications = withContext(Dispatchers.IO) { memory.recentNotifications() }
                val response = if (notifications.isBlank()) {
                    "Хм-м... В архиве уведомлений пока пусто. Проверь, выдан ли мне доступ к уведомлениям Android."
                } else {
                    "Так-так, вот что происходит в твоём цифровом королевстве:\n" + notifications
                }
                appendMessage(messages, ChatLine("assistant", response))
                withContext(Dispatchers.IO) { memory.saveConversation("assistant", response) }
                voice.speak(response)
            }
            return
        }
        isThinking = true
        status = "TEMPORAL LINK / CONNECTING"
        scope.launch {
            try {
                val key = withContext(Dispatchers.IO) {
                    secrets.read("ai_primary_api_key") ?: secrets.read("deepseek_api_key")
                }
                if (key.isNullOrBlank()) {
                    val response = "Ой-ой! Мои речевые нейросхемы ещё не подключены. Открой настройки и добавь API Key выбранного AI-провайдера. И не вздумай записывать его на табличке у входа в TVA!"
                    appendMessage(messages, ChatLine("assistant", response))
                    voice.speak(response)
                    return@launch
                }
                val memoryContext = withContext(Dispatchers.IO) { memory.buildPromptContext() }
                val provider = ProviderConfig(
                    id = "primary",
                    displayName = prefs.getString("ai_provider_name", "DeepSeek") ?: "DeepSeek",
                    baseUrl = prefs.getString("ai_provider_base_url", "https://api.deepseek.com") ?: "https://api.deepseek.com",
                    modelId = prefs.getString("ai_provider_model", "deepseek-flash") ?: "deepseek-flash"
                )
                val response = AiConversationService.reply(
                    provider,
                    key,
                    listOf(
                        AiMessage("system", PersonalityEngine.buildSystemPrompt(memoryContext)),
                        AiMessage("user", message)
                    )
                )
                appendMessage(messages, ChatLine("assistant", response))
                withContext(Dispatchers.IO) { memory.saveConversation("assistant", response) }
                selectedAnimation = "Talk_Loop"
                voice.speak(response)
                status = "TVA SYSTEM / STABLE"
            } catch (error: Exception) {
                val response = "Так, минутку! Канал связи с AI-провайдером временно схлопнулся: " +
                    (error.message ?: "неизвестная ошибка") + ". Проверь интернет и API Key, Хранитель."
                appendMessage(messages, ChatLine("assistant", response))
                status = "TEMPORAL LINK / ERROR"
            } finally { isThinking = false }
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
            selectedAnimation = selectedAnimation,
            isThinking = isThinking,
            onInputChange = { input = it },
            onSend = { send(input) },
            onAnimationChange = { selectedAnimation = it },
            onSettings = {
                showSettings = true
                scope.launch {
                    providerNameDraft = prefs.getString("ai_provider_name", "DeepSeek") ?: "DeepSeek"
                    providerBaseUrlDraft = prefs.getString("ai_provider_base_url", "https://api.deepseek.com") ?: "https://api.deepseek.com"
                    providerModelDraft = prefs.getString("ai_provider_model", "deepseek-flash") ?: "deepseek-flash"
                    apiKeyDraft = withContext(Dispatchers.IO) {
                        (secrets.read("ai_primary_api_key") ?: secrets.read("deepseek_api_key")).orEmpty()
                    }
                }
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
            onSpeak = { text -> voice.speak(text) },
            onNotificationSettings = { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            onAccessibilitySettings = { activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        )
    }

    if (showSettings) {
        SettingsDialog(
            providerName = providerNameDraft,
            onProviderNameChange = { providerNameDraft = it },
            baseUrl = providerBaseUrlDraft,
            onBaseUrlChange = { providerBaseUrlDraft = it },
            modelId = providerModelDraft,
            onModelIdChange = { providerModelDraft = it },
            apiKey = apiKeyDraft,
            onApiKeyChange = { apiKeyDraft = it },
            onSave = {
                val nameToSave = providerNameDraft.trim()
                val baseUrlToSave = providerBaseUrlDraft.trim()
                val modelToSave = providerModelDraft.trim()
                val keyToSave = apiKeyDraft.trim()
                scope.launch {
                    try {
                        require(nameToSave.isNotBlank()) { "Provider name is required" }
                        require(modelToSave.isNotBlank()) { "Model ID is required" }
                        resolveOpenAiChatEndpoint(baseUrlToSave)
                        withContext(Dispatchers.IO) {
                            secrets.save("ai_primary_api_key", keyToSave)
                            secrets.save("deepseek_api_key", "")
                        }
                        prefs.edit()
                            .putString("ai_provider_name", nameToSave)
                            .putString("ai_provider_base_url", baseUrlToSave)
                            .putString("ai_provider_model", modelToSave)
                            .apply()
                        showSettings = false
                        status = if (keyToSave.isBlank()) "AI PROVIDER / SAVED WITHOUT KEY" else "AI PROVIDER / KEY STORED SECURELY"
                    } catch (error: Exception) {
                        status = "AI PROVIDER / INVALID CONFIG: " + (error.message ?: "unknown error")
                    }
                }
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
    selectedAnimation: String,
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
                CharacterScene(Modifier.fillMaxWidth().height(285.dp), selectedAnimation)
                Text("TVA CHARACTER // 3D LIVE RIG", color = Color(0xFFD6A57B), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }

        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val clips = listOf(
                "Idle_Breathe", "Blink", "Wave_Greeting", "Pointing", "Talk_Loop", "Laugh",
                "Surprised", "Angry_Expression", "Sleep_Idle", "Glitch_Recovery", "Walk_Cycle",
                "Run_Cycle", "Look_Around", "Peek_Around", "Spin_In_Place", "Nervous_Glance", "Cheerful_Bounce"
            )
            clips.forEach { clip ->
                OutlinedButton(
                    onClick = { onAnimationChange(clip) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedAnimation == clip) Color(0xFF69371D) else Color.Transparent,
                        contentColor = TvaText
                    )
                ) { Text(clip.replace("_", " "), fontSize = 10.sp) }
            }
        }

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
            "DeepSeek API оплачивается по условиям аккаунта провайдера. Ключ хранится в Android Keystore; без ключа доступны 3D-персонаж и локальные функции.",
            color = Color(0xFF9C806A),
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsDialog(
    providerName: String,
    onProviderNameChange: (String) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    modelId: String,
    onModelIdChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onNotificationSettings: () -> Unit,
    onAccessibilitySettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("TVA / НАСТРОЙКИ") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Профиль OpenAI-совместимого провайдера", color = TvaOrange, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = providerName,
                    onValueChange = onProviderNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Имя провайдера") }
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("HTTPS Base URL") },
                    placeholder = { Text("https://api.example.com/v1") }
                )
                OutlinedTextField(
                    value = modelId,
                    onValueChange = onModelIdChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("ID модели") }
                )
                Text("API Key", color = TvaOrange, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text("API Key") }
                )
                Text("Ключ шифруется через Android Keystore. Используется протокол OpenAI Chat Completions через HTTPS.", fontSize = 11.sp)
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
