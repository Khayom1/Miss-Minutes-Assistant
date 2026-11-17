package com.khayom1.missminutes.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.khayom1.missminutes.ProductIdentity
import com.khayom1.missminutes.ai.AiCompletion
import com.khayom1.missminutes.ai.AiMessage
import com.khayom1.missminutes.ai.AiProvider
import com.khayom1.missminutes.ai.AiProviderException
import com.khayom1.missminutes.ai.AiRequest
import com.khayom1.missminutes.ai.AiRole
import com.khayom1.missminutes.ai.DeepSeekApiKeyStore
import com.khayom1.missminutes.ai.SessionManager
import com.khayom1.missminutes.ai.buildAiContextMessages
import com.khayom1.missminutes.ai.TtsProvider
import com.khayom1.missminutes.ai.SupabaseAuthException
import com.khayom1.missminutes.voice.MissMinutesWakeWordService
import com.khayom1.missminutes.voice.WakeWordServiceState
import com.khayom1.missminutes.memory.ConversationSummary
import com.khayom1.missminutes.memory.LocalConversationStore
import com.khayom1.missminutes.memory.MemoryItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.khayom1.missminutes.ai.SpeechInputProvider
import java.util.Locale

private enum class AppScreen(val symbol: String) {
    Chat("✦"),
    Dashboard("▦"),
    History("◷"),
    Settings("⚙")
}

private enum class AppLanguage(val nativeName: String) {
    English("English"),
    Russian("Русский"),
    Tajik("Тоҷикӣ")
}

private data class ChatMessage(
    val id: Long,
    val text: String,
    val isUser: Boolean
)

private data class UiCopy(
    val chat: String,
    val dashboard: String,
    val history: String,
    val settings: String,
    val welcomeTitle: String,
    val welcomeBody: String,
    val composerHint: String,
    val send: String,
    val gatewayNotice: String,
    val you: String,
    val dashboardTitle: String,
    val dashboardBody: String,
    val aiProvider: String,
    val voice: String,
    val memory: String,
    val deviceTools: String,
    val notConnected: String,
    val providerNotVerified: String,
    val providerResponding: String,
    val historyTitle: String,
    val historyEmpty: String,
    val settingsTitle: String,
    val language: String,
    val appearance: String,
    val darkMode: String,
    val darkModeBody: String,
    val about: String,
    val version: String,
    val authTitle: String,
    val authBody: String,
    val email: String,
    val password: String,
    val signIn: String,
    val createAccount: String,
    val switchToSignUp: String,
    val switchToSignIn: String,
    val wait: String,
    val emailConfirmation: String,
    val invalidCredentials: String,
    val authRateLimited: String,
    val authUnavailable: String,
    val sessionExpired: String,
    val signOut: String,
    val account: String,
    val sending: String,
    val sendFailed: String,
    val conversationTooLong: String,
    val aiRateLimited: String,
    val speak: String,
    val stopSpeaking: String,
    val voicePreparing: String,
    val voiceFailed: String,
    val voiceNeedsSetup: String,
    val dictate: String,
    val stopListening: String,
    val speechListening: String,
    val speechInputUnavailable: String,
    val microphonePermissionRequired: String
)

private fun copyFor(language: AppLanguage): UiCopy = when (language) {
    AppLanguage.English -> UiCopy(
        chat = "Chat",
        dashboard = "Dashboard",
        history = "History",
        settings = "Settings",
        welcomeTitle = "How can I help?",
        welcomeBody = "Your personal assistant, ready when you are.",
        composerHint = "Message Miss Minutes",
        send = "Send",
        gatewayNotice = "AI replies use your personal DeepSeek API key, encrypted on this device and sent directly to DeepSeek over HTTPS. Supabase is used only for sign-in.",
        you = "You",
        dashboardTitle = "Your workspace",
        dashboardBody = "A clear view of assistant capabilities and their connection status.",
        aiProvider = "AI provider",
        voice = "Voice",
        memory = "Memory",
        deviceTools = "Android tools",
        notConnected = "Not connected",
        providerNotVerified = "Not yet verified",
        providerResponding = "Responding",
        historyTitle = "Conversation history",
        historyEmpty = "No saved conversations yet.",
        settingsTitle = "App settings",
        language = "Language",
        appearance = "Appearance",
        darkMode = "Dark theme",
        darkModeBody = "Use a dark color scheme throughout the app.",
        about = "About",
        version = "Version",
        authTitle = "Sign in to Miss Minutes",
        authBody = "Use your Supabase account to access your assistant.",
        email = "Email",
        password = "Password",
        signIn = "Sign in",
        createAccount = "Create account",
        switchToSignUp = "New here? Create an account",
        switchToSignIn = "Already have an account? Sign in",
        wait = "Please wait…",
        emailConfirmation = "Check your email to confirm your account, then sign in.",
        invalidCredentials = "Email or password is incorrect.",
        authRateLimited = "Too many sign-in attempts. Try again later.",
        authUnavailable = "Authentication service is temporarily unavailable.",
        sessionExpired = "Your session expired. Please sign in again.",
        signOut = "Sign out",
        account = "Account",
        sending = "Thinking…",
        sendFailed = "No response received. Check your connection and server configuration.",
        conversationTooLong = "This conversation is too long to send. Start a new conversation.",
        aiRateLimited = "The AI request limit was reached. Try again shortly.",
        speak = "Speak",
        stopSpeaking = "Stop voice",
        voicePreparing = "Speaking with the device voice…",
        voiceFailed = "Voice playback failed. Check TTS configuration.",
        voiceNeedsSetup = "On-device voice",
        dictate = "Dictate",
        stopListening = "Stop",
        speechListening = "Listening…",
        speechInputUnavailable = "On-device speech recognition is unavailable or could not understand the speech.",
        microphonePermissionRequired = "Microphone permission is required for voice input."
    )
    AppLanguage.Russian -> UiCopy(
        chat = "Чат",
        dashboard = "Обзор",
        history = "История",
        settings = "Настройки",
        welcomeTitle = "Чем могу помочь?",
        welcomeBody = "Ваш персональный помощник всегда под рукой.",
        composerHint = "Сообщение Miss Minutes",
        send = "Отправить",
        gatewayNotice = "Ответы ИИ используют ваш личный API-ключ DeepSeek: он хранится на устройстве в зашифрованном виде и отправляется напрямую в DeepSeek по HTTPS. Supabase используется только для входа.",
        you = "Вы",
        dashboardTitle = "Ваше пространство",
        dashboardBody = "Состояние возможностей помощника и подключённых сервисов.",
        aiProvider = "ИИ-провайдер",
        voice = "Голос",
        memory = "Память",
        deviceTools = "Инструменты Android",
        notConnected = "Не подключено",
        providerNotVerified = "Ещё не проверено",
        providerResponding = "Отвечает",
        historyTitle = "История диалогов",
        historyEmpty = "Сохранённых диалогов пока нет.",
        settingsTitle = "Настройки приложения",
        language = "Язык",
        appearance = "Оформление",
        darkMode = "Тёмная тема",
        darkModeBody = "Использовать тёмную цветовую схему приложения.",
        about = "О приложении",
        version = "Версия",
        authTitle = "Вход в Miss Minutes",
        authBody = "Войдите через аккаунт Supabase, чтобы пользоваться помощником.",
        email = "Электронная почта",
        password = "Пароль",
        signIn = "Войти",
        createAccount = "Создать аккаунт",
        switchToSignUp = "Нет аккаунта? Создать",
        switchToSignIn = "Уже есть аккаунт? Войти",
        wait = "Подождите…",
        emailConfirmation = "Подтвердите аккаунт по ссылке в письме, затем войдите.",
        invalidCredentials = "Неверная почта или пароль.",
        authRateLimited = "Слишком много попыток входа. Попробуйте позже.",
        authUnavailable = "Сервис авторизации временно недоступен.",
        sessionExpired = "Сессия истекла. Войдите снова.",
        signOut = "Выйти",
        account = "Аккаунт",
        sending = "Думаю…",
        sendFailed = "Ответ не получен. Проверьте интернет и настройки сервера.",
        conversationTooLong = "Диалог слишком длинный. Начните новый диалог.",
        aiRateLimited = "Достигнут лимит запросов ИИ. Попробуйте немного позже.",
        speak = "Озвучить",
        stopSpeaking = "Остановить",
        voicePreparing = "Озвучивание системным голосом…",
        voiceFailed = "Не удалось воспроизвести голос. Проверьте настройки TTS.",
        voiceNeedsSetup = "Системный голос устройства",
        dictate = "Диктовать",
        stopListening = "Остановить",
        speechListening = "Слушаю…",
        speechInputUnavailable = "Офлайн-распознавание речи недоступно или не смогло распознать речь.",
        microphonePermissionRequired = "Для голосового ввода нужен доступ к микрофону."
    )
    AppLanguage.Tajik -> UiCopy(
        chat = "Чат",
        dashboard = "Панел",
        history = "Таърих",
        settings = "Танзимот",
        welcomeTitle = "Чӣ кӯмак лозим аст?",
        welcomeBody = "Ёвари шахсии шумо ҳамеша омода аст.",
        composerHint = "Ба Miss Minutes паём нависед",
        send = "Фиристодан",
        gatewayNotice = "Ҷавобҳои AI бо калиди шахсии DeepSeek-и шумо кор мекунанд: калид дар телефон рамзгузорӣ шуда, мустақиман тавассути HTTPS ба DeepSeek фиристода мешавад. Supabase танҳо барои ворид шудан истифода мешавад.",
        you = "Шумо",
        dashboardTitle = "Муҳити кории шумо",
        dashboardBody = "Ҳолати имкониятҳо ва пайвасти хидматҳои ёвар.",
        aiProvider = "Провайдери AI",
        voice = "Овоз",
        memory = "Хотира",
        deviceTools = "Абзорҳои Android",
        notConnected = "Пайваст нест",
        providerNotVerified = "Ҳанӯз санҷида нашудааст",
        providerResponding = "Ҷавоб медиҳад",
        historyTitle = "Таърихи гуфтугӯҳо",
        historyEmpty = "Ҳоло гуфтугӯи сабтшуда нест.",
        settingsTitle = "Танзимоти барнома",
        language = "Забон",
        appearance = "Намуди зоҳирӣ",
        darkMode = "Мавзӯи торик",
        darkModeBody = "Дар тамоми барнома мавзӯи торик истифода шавад.",
        about = "Дар бораи барнома",
        version = "Версия",
        authTitle = "Вуруд ба Miss Minutes",
        authBody = "Барои истифодаи ёвар бо ҳисоби Supabase ворид шавед.",
        email = "Почтаи электронӣ",
        password = "Рамз",
        signIn = "Ворид шудан",
        createAccount = "Сохтани ҳисоб",
        switchToSignUp = "Ҳисоб надоред? Сохтан",
        switchToSignIn = "Аллакай ҳисоб доред? Вуруд",
        wait = "Лутфан интизор шавед…",
        emailConfirmation = "Ҳисобро аз пайванди почтаи электронӣ тасдиқ кунед ва баъд ворид шавед.",
        invalidCredentials = "Почта ё рамз нодуруст аст.",
        authRateLimited = "Кӯшишҳои зиёд. Баъдтар дубора кӯшиш кунед.",
        authUnavailable = "Хидмати тасдиқи ҳисоб муваққатан дастнорас аст.",
        sessionExpired = "Сессия ба охир расид. Аз нав ворид шавед.",
        signOut = "Баромадан",
        account = "Ҳисоб",
        sending = "Фикр карда истодаам…",
        sendFailed = "Ҷавоб гирифта нашуд. Интернет ва танзимоти серверро санҷед.",
        conversationTooLong = "Гуфтугӯ хеле дароз аст. Гуфтугӯи нав оғоз кунед.",
        aiRateLimited = "Маҳдудияти дархостҳои AI расид. Каме баъд кӯшиш кунед.",
        speak = "Хондан",
        stopSpeaking = "Истодан",
        voicePreparing = "Бо овози худи телефон хонда мешавад…",
        voiceFailed = "Пахши овоз ноком шуд. Танзимоти TTS-ро санҷед.",
        voiceNeedsSetup = "Овози дохилии телефон",
        dictate = "Бо овоз навиштан",
        stopListening = "Қатъ кардан",
        speechListening = "Гӯш карда истодаам…",
        speechInputUnavailable = "Шинохти офлайнии нутқ дастрас нест ё суханро нафаҳмид.",
        microphonePermissionRequired = "Барои воридкунии овоз иҷозати микрофон лозим аст."
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissMinutesApp(
    sessionManager: SessionManager,
    aiProvider: AiProvider,
    ttsProvider: TtsProvider,
    apiKeyStore: DeepSeekApiKeyStore,
    speechInputProvider: SpeechInputProvider,
    conversationStore: LocalConversationStore
) {
    val scope = rememberCoroutineScope()
    var selectedLanguageName by rememberSaveable { mutableStateOf(AppLanguage.English.name) }
    val language = AppLanguage.valueOf(selectedLanguageName)
    val context = LocalContext.current
    var wakeEnabled by remember { mutableStateOf(WakeWordServiceState.isEnabled(context)) }
    var wakeMessage by remember { mutableStateOf<String?>(null) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: android.content.Context?, intent: Intent?) {
                if (intent?.action == MissMinutesWakeWordService.ACTION_STATE_CHANGED) {
                    wakeEnabled = intent.getBooleanExtra(MissMinutesWakeWordService.EXTRA_ENABLED, false)
                    wakeMessage = intent.getStringExtra(MissMinutesWakeWordService.EXTRA_MESSAGE)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(MissMinutesWakeWordService.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    val wakePermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
    val startWakeListening = {
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MissMinutesWakeWordService::class.java)
                    .setAction(MissMinutesWakeWordService.ACTION_START)
            )
            wakeEnabled = true
            wakeMessage = when (language) {
                AppLanguage.English -> "Offline listening requested. Check the persistent notification for the actual service state."
                AppLanguage.Russian -> "Запрос на офлайн-прослушивание отправлен. Фактический статус указан в постоянном уведомлении."
                AppLanguage.Tajik -> "Дархости гӯшкунии офлайн фиристода шуд. Ҳолати воқеӣ дар огоҳиномаи доимӣ нишон дода мешавад."
            }
        } catch (_: IllegalStateException) {
            wakeEnabled = false
            wakeMessage = when (language) {
                AppLanguage.English -> "Android did not allow the microphone service to start. Open the app and try again."
                AppLanguage.Russian -> "Android не разрешил запустить микрофонную службу. Откройте приложение и повторите попытку."
                AppLanguage.Tajik -> "Android иҷозаи оғоз кардани хидмати микрофонро надод. Барномаро кушода, боз кӯшиш кунед."
            }
        } catch (_: SecurityException) {
            wakeEnabled = false
            wakeMessage = when (language) {
                AppLanguage.English -> "Required microphone or notification permission is missing."
                AppLanguage.Russian -> "Нет необходимого разрешения на микрофон или уведомления."
                AppLanguage.Tajik -> "Иҷозати зарурии микрофон ё огоҳинома мавҷуд нест."
            }
        }
    }
    val wakePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = wakePermissions.all { permission ->
            grants[permission] == true ||
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            startWakeListening()
        } else {
            wakeEnabled = false
            wakeMessage = when (language) {
                AppLanguage.English -> "Listening was not started. Both microphone and notification permissions are required."
                AppLanguage.Russian -> "Прослушивание не запущено. Нужны разрешения на микрофон и уведомления."
                AppLanguage.Tajik -> "Гӯшкунӣ оғоз нашуд. Иҷозати микрофон ва огоҳинома зарур аст."
            }
        }
    }
    val enableWakeListening = {
        val allGranted = wakePermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) startWakeListening() else wakePermissionLauncher.launch(wakePermissions)
    }
    val disableWakeListening = {
        // Stop an existing listener without starting a new background service.
        // The service's onDestroy() owns recognizer cleanup and state publication.
        context.stopService(Intent(context, MissMinutesWakeWordService::class.java))
        WakeWordServiceState.setEnabled(context, false)
        wakeEnabled = false
        wakeMessage = when (language) {
            AppLanguage.English -> "Offline wake phrase listening is off."
            AppLanguage.Russian -> "Офлайн-распознавание фразы выключено."
            AppLanguage.Tajik -> "Гӯшкунии офлайн хомӯш аст."
        }
    }
    var selectedScreenName by rememberSaveable { mutableStateOf(AppScreen.Chat.name) }
    var darkTheme by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var authEmail by rememberSaveable { mutableStateOf("") }
    var authPassword by remember { mutableStateOf("") }
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(true) }
    // Authentication is restored from the encrypted session store, never from saved UI state.
    var isAuthenticated by remember { mutableStateOf(false) }
    var authBusy by remember { mutableStateOf(false) }
    var sessionGeneration by remember { mutableStateOf(0L) }
    var authMessage by remember { mutableStateOf<String?>(null) }
    var userEmail by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var chatError by remember { mutableStateOf<String?>(null) }
    var aiHasResponded by remember { mutableStateOf(false) }
    var speakingMessageId by remember { mutableStateOf<Long?>(null) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var speechError by remember { mutableStateOf<String?>(null) }
    var speechListening by remember { mutableStateOf(false) }
    var apiKeyConfigured by remember { mutableStateOf(false) }
    var apiKeySaving by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    var apiKeyMessage by remember { mutableStateOf<String?>(null) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var currentOwnerId by remember { mutableStateOf<String?>(null) }
    var activeConversationId by remember { mutableStateOf<String?>(null) }
    var conversationSummaries by remember { mutableStateOf<List<ConversationSummary>>(emptyList()) }
    var memoryItems by remember { mutableStateOf<List<MemoryItem>>(emptyList()) }
    var retentionDays by remember { mutableStateOf(LocalConversationStore.DEFAULT_RETENTION_DAYS) }
    var memoryContextEnabled by remember { mutableStateOf(false) }
    var memoryDraft by rememberSaveable { mutableStateOf("") }
    var memoryMessage by remember { mutableStateOf<String?>(null) }
    var exportPayload by remember { mutableStateOf<String?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var pendingDeleteConversationId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val copy = copyFor(language)

    suspend fun loadConversation(ownerId: String, preferredConversationId: String? = null) {
        val snapshot = withContext(Dispatchers.IO) {
            conversationStore.pruneExpired(ownerId)
            val initial = conversationStore.listConversations(ownerId)
            val selectedId = preferredConversationId?.takeIf { id -> initial.any { it.id == id } }
                ?: initial.firstOrNull()?.id
                ?: conversationStore.createConversation(ownerId)
            val latest = conversationStore.listConversations(ownerId)
            val storedMessages = conversationStore.getMessages(ownerId, selectedId)
            val storedMemory = conversationStore.listMemory(ownerId)
            val days = conversationStore.getRetentionDays(ownerId)
            val useMemoryContext = conversationStore.isMemoryContextEnabled(ownerId)
            Triple(selectedId, latest, Triple(storedMessages, storedMemory, Pair(days, useMemoryContext)))
        }
        currentOwnerId = ownerId
        activeConversationId = snapshot.first
        conversationSummaries = snapshot.second
        messages.clear()
        snapshot.third.first.forEach { item ->
            messages.add(
                ChatMessage(
                    id = item.id,
                    text = item.content,
                    isUser = item.role == LocalConversationStore.ROLE_USER
                )
            )
        }
        memoryItems = snapshot.third.second
        retentionDays = snapshot.third.third.first
        memoryContextEnabled = snapshot.third.third.second
        aiHasResponded = messages.any { !it.isUser }
    }

    val speechLocale = when (language) {
        AppLanguage.English -> Locale.US
        AppLanguage.Russian -> Locale("ru", "RU")
        AppLanguage.Tajik -> Locale("tg", "TJ")
    }
    val startSpeechInput: () -> Unit = {
        speechError = null
        speechListening = true
        speechInputProvider.start(
            locale = speechLocale,
            onResult = { transcript ->
                speechListening = false
                val separator = if (draft.isBlank()) "" else " "
                draft = (draft + separator + transcript).take(8_000)
            },
            onError = {
                speechListening = false
                speechError = copyFor(language).speechInputUnavailable
            }
        )
    }
    val speechPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startSpeechInput() else {
            speechListening = false
            speechError = copyFor(language).microphonePermissionRequired
        }
    }
    val requestSpeechInput: () -> Unit = {
        if (speechListening) {
            speechInputProvider.stop()
            speechListening = false
            speechError = null
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) startSpeechInput() else speechPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(apiKeyStore) {
        apiKeyConfigured = withContext(Dispatchers.IO) {
            runCatching { !apiKeyStore.load().isNullOrBlank() }.getOrDefault(false)
        }
    }

    LaunchedEffect(sessionManager) {
        // Saved Compose state can outlive a revoked/expired session. Start fail-closed
        // and derive authenticated UI exclusively from the Keystore-backed session.
        isAuthenticated = false
        userEmail = ""
        try {
            val session = sessionManager.restoreSession()
            if (session != null) {
                isAuthenticated = true
                userEmail = session.email.orEmpty()
                authEmail = session.email.orEmpty()
                loadConversation(session.userId)
            } else {
                disableWakeListening()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            disableWakeListening()
            authMessage = copy.authUnavailable
        } finally {
            isRestoring = false
        }
    }

    val submitAuth: () -> Unit = {
        if (!authBusy) {
            authBusy = true
            scope.launch {
                authMessage = null
                try {
                    if (isSignUp) {
                        val result = sessionManager.signUp(authEmail.trim(), authPassword)
                        val session = result.session
                        if (session != null) {
                            sessionGeneration += 1
                            isAuthenticated = true
                            userEmail = session.email ?: authEmail.trim()
                            authEmail = userEmail
                            authPassword = ""
                            loadConversation(session.userId)
                        } else {
                            isSignUp = false
                            authPassword = ""
                            authMessage = copy.emailConfirmation
                        }
                    } else {
                        val session = sessionManager.signIn(authEmail.trim(), authPassword)
                        sessionGeneration += 1
                        isAuthenticated = true
                        userEmail = session.email ?: authEmail.trim()
                        authEmail = userEmail
                        authPassword = ""
                        loadConversation(session.userId)
                    }
                } catch (_: SupabaseAuthException.InvalidCredentials) {
                    authMessage = copy.invalidCredentials
                } catch (_: SupabaseAuthException.RateLimited) {
                    authMessage = copy.authRateLimited
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    authMessage = copy.authUnavailable
                } finally {
                    authBusy = false
                }
            }
        }
    }

    val signOut: () -> Unit = {
        if (!authBusy) {
            authBusy = true
            disableWakeListening()
            sessionGeneration += 1
            isAuthenticated = false
            userEmail = ""
            authEmail = ""
            authPassword = ""
            messages.clear()
            currentOwnerId = null
            activeConversationId = null
            conversationSummaries = emptyList()
            memoryItems = emptyList()
            memoryDraft = ""
            ttsProvider.stop()
            speakingMessageId = null
            voiceError = null
            draft = ""
            aiHasResponded = false
            chatError = null
            isSending = false
            selectedScreenName = AppScreen.Chat.name
            scope.launch {
                try {
                    sessionManager.signOut()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Local credentials are cleared by SessionManager even if revocation fails.
                } finally {
                    authBusy = false
                }
            }
        }
    }

    val saveApiKey: () -> Unit = {
        val candidate = apiKeyInput.trim()
        if (candidate.isNotEmpty() && !apiKeySaving) {
            apiKeySaving = true
            apiKeyMessage = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { apiKeyStore.save(candidate) }
                }
                apiKeySaving = false
                result.onSuccess {
                    apiKeyConfigured = true
                    apiKeyInput = ""
                    apiKeyMessage = when (language) {
                        AppLanguage.English -> "API key saved securely on this device."
                        AppLanguage.Russian -> "API-ключ надёжно сохранён на этом устройстве."
                        AppLanguage.Tajik -> "Калиди API дар ҳамин телефон бехатар нигоҳ дошта шуд."
                    }
                }.onFailure { error ->
                    apiKeyMessage = if (error is IllegalArgumentException) {
                        when (language) {
                            AppLanguage.English -> "The key format looks invalid. Check your DeepSeek key."
                            AppLanguage.Russian -> "Неверный формат ключа. Проверьте ключ DeepSeek."
                            AppLanguage.Tajik -> "Формати калид нодуруст аст. Калиди DeepSeek-ро санҷед."
                        }
                    } else {
                        when (language) {
                            AppLanguage.English -> "Could not save the key securely."
                            AppLanguage.Russian -> "Не удалось безопасно сохранить ключ."
                            AppLanguage.Tajik -> "Калидро бехатар нигоҳ доштан нашуд."
                        }
                    }
                }
            }
        }
    }

    val clearApiKey: () -> Unit = {
        if (!apiKeySaving) {
            apiKeySaving = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { apiKeyStore.clear() }
                }
                apiKeySaving = false
                result.onSuccess {
                    apiKeyConfigured = false
                    apiKeyInput = ""
                    apiKeyMessage = when (language) {
                        AppLanguage.English -> "API key removed from this device."
                        AppLanguage.Russian -> "API-ключ удалён с этого устройства."
                        AppLanguage.Tajik -> "Калиди API аз телефон пок карда шуд."
                    }
                }.onFailure {
                    apiKeyMessage = when (language) {
                        AppLanguage.English -> "Could not remove the stored API key."
                        AppLanguage.Russian -> "Не удалось удалить сохранённый ключ."
                        AppLanguage.Tajik -> "Калиди нигоҳдошташударо пок карда нашуд."
                    }
                }
            }
        }
    }

    val submitMessage: () -> Unit = {
        val value = draft.trim()
        if (value.isNotEmpty() && value.length <= 8_000 && !isSending) {
            val requestMessages = buildAiContextMessages(
                history = messages.map { message ->
                    AiMessage(
                        role = if (message.isUser) AiRole.USER else AiRole.ASSISTANT,
                        content = message.text
                    )
                },
                currentUserMessage = value,
                savedMemory = memoryItems.map { it.content },
                includeSavedMemory = memoryContextEnabled
            )

            val request = try {
                AiRequest(messages = requestMessages)
            } catch (_: IllegalArgumentException) {
                chatError = copy.conversationTooLong
                null
            }

            if (request != null) {
                isSending = true
                draft = ""
                chatError = null
                val requestGeneration = sessionGeneration

                scope.launch {
                    try {
                        val ownerId = currentOwnerId ?: throw AiProviderException.AuthenticationRequired()
                        val conversationId = activeConversationId ?: withContext(Dispatchers.IO) {
                            conversationStore.createConversation(ownerId)
                        }.also { activeConversationId = it }
                        val userMessageId = withContext(Dispatchers.IO) {
                            conversationStore.appendMessage(
                                ownerId,
                                conversationId,
                                LocalConversationStore.ROLE_USER,
                                value
                            )
                        }
                        if (requestGeneration != sessionGeneration) return@launch
                        messages.add(ChatMessage(userMessageId, value, true))
                        conversationSummaries = withContext(Dispatchers.IO) {
                            conversationStore.listConversations(ownerId)
                        }

                        val completion: AiCompletion = aiProvider.complete(request)
                        if (requestGeneration != sessionGeneration) return@launch
                        val assistantMessageId = withContext(Dispatchers.IO) {
                            conversationStore.appendMessage(
                                ownerId,
                                conversationId,
                                LocalConversationStore.ROLE_ASSISTANT,
                                completion.content
                            )
                        }
                        if (requestGeneration != sessionGeneration) return@launch
                        messages.add(ChatMessage(assistantMessageId, completion.content, false))
                        conversationSummaries = withContext(Dispatchers.IO) {
                            conversationStore.listConversations(ownerId)
                        }
                        aiHasResponded = true
                    } catch (_: AiProviderException.AuthenticationRequired) {
                        if (requestGeneration != sessionGeneration) return@launch
                        sessionGeneration += 1
                        authBusy = true
                        disableWakeListening()
                        isAuthenticated = false
                        userEmail = ""
                        authEmail = ""
                        authPassword = ""
                        messages.clear()
                        currentOwnerId = null
                        activeConversationId = null
                        conversationSummaries = emptyList()
                        memoryItems = emptyList()
                        draft = ""
                        aiHasResponded = false
                        chatError = null
                        isSending = false
                        selectedScreenName = AppScreen.Chat.name
                        authMessage = copy.sessionExpired
                        try {
                            sessionManager.signOut()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            // SessionManager always clears local tokens in its finally block.
                        } finally {
                            authBusy = false
                        }
                    } catch (_: AiProviderException.RateLimited) {
                        if (requestGeneration == sessionGeneration) {
                            chatError = copy.aiRateLimited
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        if (requestGeneration == sessionGeneration) {
                            chatError = copy.sendFailed
                        }
                    } finally {
                        if (requestGeneration == sessionGeneration) {
                            isSending = false
                        }
                    }
                }
            }
        }
    }


    val openConversation: (String) -> Unit = { id ->
        currentOwnerId?.let { owner ->
            scope.launch {
                loadConversation(owner, id)
                selectedScreenName = AppScreen.Chat.name
            }
        }
    }
    val startNewConversation: () -> Unit = {
        currentOwnerId?.let { owner ->
            scope.launch {
                val id = withContext(Dispatchers.IO) { conversationStore.createConversation(owner) }
                loadConversation(owner, id)
                selectedScreenName = AppScreen.Chat.name
            }
        }
    }
    val deleteConversation: (String) -> Unit = { id ->
        currentOwnerId?.let { owner ->
            scope.launch {
                withContext(Dispatchers.IO) { conversationStore.deleteConversation(owner, id) }
                pendingDeleteConversationId = null
                if (activeConversationId == id) loadConversation(owner)
                else conversationSummaries = withContext(Dispatchers.IO) {
                    conversationStore.listConversations(owner)
                }
            }
        }
    }
    val exportLocalData: () -> Unit = {
        currentOwnerId?.let { owner ->
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { conversationStore.exportAll(owner) }
                }
                result.onSuccess {
                    exportPayload = it
                    exportError = null
                }.onFailure {
                    exportPayload = null
                    exportError = when (language) {
                        AppLanguage.English -> "Export is larger than the 2 MB safety limit. Delete older records and retry."
                        AppLanguage.Russian -> "Экспорт превышает безопасный лимит 2 МБ. Удалите старые записи и повторите попытку."
                        AppLanguage.Tajik -> "Содирот аз ҳадди бехатари 2 МБ зиёд аст. Сабтҳои кӯҳнаро пок карда, боз кӯшиш кунед."
                    }
                }
            }
        }
    }
    val deleteAllLocalData: () -> Unit = {
        currentOwnerId?.let { owner ->
            scope.launch {
                withContext(Dispatchers.IO) { conversationStore.deleteAll(owner) }
                confirmDeleteAll = false
                activeConversationId = null
                messages.clear()
                conversationSummaries = emptyList()
                memoryItems = emptyList()
                memoryDraft = ""
                aiHasResponded = false
            }
        }
    }
    val addMemory: () -> Unit = {
        val owner = currentOwnerId
        val value = memoryDraft.trim()
        if (owner != null && value.isNotEmpty()) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { conversationStore.addMemory(owner, value) }
                }.onSuccess {
                    memoryDraft = ""
                    memoryMessage = null
                    memoryItems = withContext(Dispatchers.IO) { conversationStore.listMemory(owner) }
                }.onFailure {
                    memoryMessage = when (language) {
                        AppLanguage.English -> "Memory could not be saved. Keep it under 2,000 characters."
                        AppLanguage.Russian -> "Не удалось сохранить память. Ограничение — 2 000 символов."
                        AppLanguage.Tajik -> "Хотира нигоҳ дошта нашуд. Ҳадди аксар 2 000 аломат аст."
                    }
                }
            }
        }
    }
    val deleteMemory: (String) -> Unit = { id ->
        currentOwnerId?.let { owner ->
            scope.launch {
                withContext(Dispatchers.IO) { conversationStore.deleteMemory(owner, id) }
                memoryItems = withContext(Dispatchers.IO) { conversationStore.listMemory(owner) }
            }
        }
    }
    val changeRetention: (Int) -> Unit = { days ->
        currentOwnerId?.let { owner ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    conversationStore.setRetentionDays(owner, days)
                    conversationStore.pruneExpired(owner)
                }
                retentionDays = days
                conversationSummaries = withContext(Dispatchers.IO) {
                    conversationStore.listConversations(owner)
                }
                memoryItems = withContext(Dispatchers.IO) { conversationStore.listMemory(owner) }
                if (activeConversationId == null || conversationSummaries.none { it.id == activeConversationId }) {
                    loadConversation(owner)
                }
            }
        }
    }

    val speakMessage: (ChatMessage) -> Unit = { message ->
        if (!message.isUser) {
            if (speakingMessageId == message.id) {
                ttsProvider.stop()
                speakingMessageId = null
            } else {
                ttsProvider.stop()
                speakingMessageId = message.id
                voiceError = null
                ttsProvider.speak(
                    text = message.text,
                    onDone = { speakingMessageId = null },
                    onError = {
                        speakingMessageId = null
                        voiceError = copy.voiceFailed
                    }
                )
            }
        }
    }

    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        when {
            isRestoring -> LoadingScreen()
            !isAuthenticated -> AuthScreen(
                copy = copy,
                email = authEmail,
                password = authPassword,
                isSignUp = isSignUp,
                isBusy = authBusy,
                message = authMessage,
                onEmailChange = { authEmail = it },
                onPasswordChange = { if (it.length <= 128) authPassword = it },
                onModeChange = {
                    isSignUp = !isSignUp
                    authMessage = null
                    authPassword = ""
                },
                onSubmit = submitAuth
            )
            else -> {
                val screen = AppScreen.valueOf(selectedScreenName)
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = ProductIdentity.NAME,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            AppScreen.entries.forEach { item ->
                                val label = when (item) {
                                    AppScreen.Chat -> copy.chat
                                    AppScreen.Dashboard -> copy.dashboard
                                    AppScreen.History -> copy.history
                                    AppScreen.Settings -> copy.settings
                                }
                                NavigationBarItem(
                                    selected = screen == item,
                                    onClick = { selectedScreenName = item.name },
                                    icon = { Text(item.symbol, fontSize = 18.sp) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (screen) {
                            AppScreen.Chat -> ChatScreen(
                                copy = copy,
                                messages = messages,
                                draft = draft,
                                isSending = isSending,
                                error = chatError,
                                onDraftChange = { draft = it },
                                onSend = submitMessage,
                                speakingMessageId = speakingMessageId,
                                voiceError = voiceError,
                                voicePreparing = copy.voicePreparing,
                                onSpeak = speakMessage,
                                speechListening = speechListening,
                                speechError = speechError,
                                onSpeechInput = requestSpeechInput
                            )
                            AppScreen.Dashboard -> DashboardScreen(
                                copy = copy,
                                email = userEmail,
                                aiStatus = if (aiHasResponded) {
                                    copy.providerResponding
                                } else {
                                    copy.providerNotVerified
                                },
                                voiceStatus = copy.voiceNeedsSetup
                            )
                            AppScreen.History -> HistoryScreen(
                                copy = copy,
                                language = language,
                                conversations = conversationSummaries,
                                exportPayload = exportPayload,
                                exportError = exportError,
                                pendingDeleteConversationId = pendingDeleteConversationId,
                                confirmDeleteAll = confirmDeleteAll,
                                onNewConversation = startNewConversation,
                                onOpenConversation = openConversation,
                                onRequestDelete = { pendingDeleteConversationId = it },
                                onConfirmDelete = { id -> deleteConversation(id) },
                                onCancelDelete = { pendingDeleteConversationId = null },
                                onExport = exportLocalData,
                                onDismissExport = { exportPayload = null },
                                onRequestDeleteAll = { confirmDeleteAll = true },
                                onConfirmDeleteAll = deleteAllLocalData,
                                onCancelDeleteAll = { confirmDeleteAll = false }
                            )
                            AppScreen.Settings -> SettingsScreen(
                                copy = copy,
                                language = language,
                                darkTheme = darkTheme,
                                email = userEmail,
                                isBusy = authBusy || apiKeySaving,
                                apiKeyConfigured = apiKeyConfigured,
                                apiKeyInput = apiKeyInput,
                                apiKeyMessage = apiKeyMessage,
                                onApiKeyInputChange = { apiKeyInput = it.take(512) },
                                onSaveApiKey = saveApiKey,
                                onClearApiKey = clearApiKey,
                                wakeEnabled = wakeEnabled,
                                wakeMessage = wakeMessage,
                                onEnableWakeListening = enableWakeListening,
                                onDisableWakeListening = disableWakeListening,
                                onLanguageChange = { selectedLanguageName = it.name },
                                onDarkThemeChange = { darkTheme = it },
                                memoryItems = memoryItems,
                                memoryDraft = memoryDraft,
                                memoryMessage = memoryMessage,
                                retentionDays = retentionDays,
                                memoryContextEnabled = memoryContextEnabled,
                                onMemoryDraftChange = { memoryDraft = it.take(LocalConversationStore.MAX_MEMORY_CHARS) },
                                onAddMemory = addMemory,
                                onDeleteMemory = deleteMemory,
                                onRetentionChange = changeRetention,
                                onMemoryContextChange = { enabled ->
                                    currentOwnerId?.let { owner ->
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                conversationStore.setMemoryContextEnabled(owner, enabled)
                                            }
                                            memoryContextEnabled = enabled
                                        }
                                    }
                                },
                                onSignOut = signOut
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AuthScreen(
    copy: UiCopy,
    email: String,
    password: String,
    isSignUp: Boolean,
    isBusy: Boolean,
    message: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onModeChange: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = ProductIdentity.NAME,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = copy.authTitle,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            text = copy.authBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            modifier = Modifier.fillMaxWidth().testTag("auth_email"),
            label = { Text(copy.email) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("auth_password"),
            label = { Text(copy.password) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() })
        )
        if (message != null) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }
        Button(
            onClick = onSubmit,
            enabled = !isBusy && email.isNotBlank() && (if (isSignUp) password.length >= 8 else password.isNotBlank()),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp).testTag("auth_submit")
        ) {
            Text(
                if (isBusy) copy.wait else if (isSignUp) copy.createAccount else copy.signIn
            )
        }
        TextButton(
            onClick = onModeChange,
            enabled = !isBusy,
            modifier = Modifier.testTag("auth_toggle_mode")
        ) {
            Text(if (isSignUp) copy.switchToSignIn else copy.switchToSignUp)
        }
    }
}

@Composable
private fun ChatScreen(
    copy: UiCopy,
    messages: List<ChatMessage>,
    draft: String,
    isSending: Boolean,
    error: String?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    speakingMessageId: Long?,
    voiceError: String?,
    voicePreparing: String,
    onSpeak: (ChatMessage) -> Unit,
    speechListening: Boolean,
    speechError: String?,
    onSpeechInput: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = copy.welcomeTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = copy.welcomeBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    youLabel = copy.you,
                    speakLabel = if (speakingMessageId == message.id) copy.stopSpeaking else copy.speak,
                    onSpeak = { onSpeak(message) }
                )
            }
        }
        if (voiceError != null || speechError != null) {
            Text(voiceError ?: speechError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } else if (speechListening) {
            Text(copy.speechListening, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else if (speakingMessageId != null) {
            Text(voicePreparing, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = copy.gatewayNotice,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 8_000) onDraftChange(it) },
                modifier = Modifier.weight(1f).testTag("chat_input"),
                placeholder = { Text(copy.composerHint) },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            TextButton(
                onClick = onSpeechInput,
                enabled = !isSending,
                modifier = Modifier.testTag("speech_input")
            ) {
                Text(if (speechListening) copy.stopListening else copy.dictate)
            }
            Button(
                onClick = onSend,
                enabled = draft.isNotBlank() && !isSending,
                modifier = Modifier.testTag("chat_send")
            ) {
                Text(if (isSending) copy.sending else copy.send)
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    youLabel: String,
    speakLabel: String,
    onSpeak: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (message.isUser) youLabel else ProductIdentity.NAME,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = message.text)
            if (!message.isUser) {
                TextButton(onClick = onSpeak, modifier = Modifier.testTag("tts_message")) {
                    Text(speakLabel)
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    copy: UiCopy,
    email: String,
    aiStatus: String,
    voiceStatus: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(copy.dashboardTitle, style = MaterialTheme.typography.headlineMedium)
        Text(copy.dashboardBody, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StatusCard(copy.account, email)
        StatusCard(copy.aiProvider, aiStatus)
        StatusCard(copy.voice, voiceStatus)
        StatusCard(copy.memory, copy.notConnected)
        StatusCard(copy.deviceTools, copy.notConnected)
    }
}

@Composable
private fun StatusCard(title: String, status: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                status,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun HistoryScreen(
    copy: UiCopy,
    language: AppLanguage,
    conversations: List<ConversationSummary>,
    exportPayload: String?,
    exportError: String?,
    pendingDeleteConversationId: String?,
    confirmDeleteAll: Boolean,
    onNewConversation: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onConfirmDelete: (String) -> Unit,
    onCancelDelete: () -> Unit,
    onExport: () -> Unit,
    onDismissExport: () -> Unit,
    onRequestDeleteAll: () -> Unit,
    onConfirmDeleteAll: () -> Unit,
    onCancelDeleteAll: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val labels = when (language) {
        AppLanguage.English -> listOf("New conversation", "Export all data", "Delete all local data", "Open", "Delete", "messages", "Cancel", "Delete", "Export preview; copying happens only when you choose Copy")
        AppLanguage.Russian -> listOf("Новый диалог", "Экспорт всех данных", "Удалить все локальные данные", "Открыть", "Удалить", "сообщений", "Отмена", "Удалить", "Предпросмотр экспорта; копирование выполняется только по вашему выбору")
        AppLanguage.Tajik -> listOf("Гуфтугӯи нав", "Содироти ҳамаи маълумот", "Пок кардани ҳамаи маълумоти маҳаллӣ", "Кушодан", "Пок кардан", "паём", "Бекор кардан", "Пок кардан", "Намунаи содирот; нусхабардорӣ танҳо бо интихоби шумо иҷро мешавад")
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(copy.historyTitle, style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNewConversation) { Text(labels[0]) }
            TextButton(onClick = onExport, enabled = conversations.isNotEmpty()) { Text(labels[1]) }
        }
        TextButton(onClick = onRequestDeleteAll, enabled = conversations.isNotEmpty()) {
            Text(labels[2], color = MaterialTheme.colorScheme.error)
        }
        if (exportError != null) {
            Text(exportError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (conversations.isEmpty()) {
            Text(copy.historyEmpty, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(conversations, key = { it.id }) { conversation ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(conversation.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${conversation.messageCount} ${labels[5]}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onOpenConversation(conversation.id) }) { Text(labels[3]) }
                                TextButton(onClick = { onRequestDelete(conversation.id) }) {
                                    Text(labels[4], color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (exportPayload != null) {
        AlertDialog(
            onDismissRequest = onDismissExport,
            title = { Text(if (language == AppLanguage.Tajik) "Содироти маълумот" else if (language == AppLanguage.Russian) "Экспорт данных" else "Export data") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(labels[8], style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (language == AppLanguage.Tajik) "Нусхабардорӣ маълумотро ба буфери мубодила мегузорад." else if (language == AppLanguage.Russian) "Копирование помещает данные в буфер обмена." else "Copy places the full export on the system clipboard.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(exportPayload.take(1_500), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(exportPayload))
                    onDismissExport()
                }) { Text(if (language == AppLanguage.Tajik) "Нусхабардорӣ" else if (language == AppLanguage.Russian) "Копировать" else "Copy export") }
            },
            dismissButton = {
                TextButton(onClick = onDismissExport) { Text(labels[6]) }
            }
        )
    }

    if (pendingDeleteConversationId != null) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text(if (language == AppLanguage.Tajik) "Гуфтугӯ пок шавад?" else if (language == AppLanguage.Russian) "Удалить диалог?" else "Delete conversation?") },
            text = { Text(if (language == AppLanguage.Tajik) "Ин амал танҳо ин гуфтугӯро аз телефони шумо пок мекунад." else if (language == AppLanguage.Russian) "Этот диалог будет удалён только с этого устройства." else "This conversation will be deleted from this device.") },
            confirmButton = {
                TextButton(onClick = { onConfirmDelete(pendingDeleteConversationId) }) {
                    Text(labels[7], color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = onCancelDelete) { Text(labels[6]) } }
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = onCancelDeleteAll,
            title = { Text(if (language == AppLanguage.Tajik) "Ҳамаи маълумоти маҳаллӣ пок шавад?" else if (language == AppLanguage.Russian) "Удалить все локальные данные?" else "Delete all local data?") },
            text = { Text(if (language == AppLanguage.Tajik) "Ҳамаи гуфтугӯҳо ва хотираҳои маҳаллӣ пок мешаванд. Ин амал барқарор намешавад." else if (language == AppLanguage.Russian) "Все локальные диалоги и сохранённые воспоминания будут удалены. Это действие нельзя отменить." else "All local conversations and saved memories will be deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = onConfirmDeleteAll) {
                    Text(if (language == AppLanguage.Tajik) "Ҳамаашро пок кун" else if (language == AppLanguage.Russian) "Удалить всё" else "Delete everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = onCancelDeleteAll) { Text(labels[6]) } }
        )
    }
}

@Composable
private fun SettingsScreen(
    copy: UiCopy,
    language: AppLanguage,
    darkTheme: Boolean,
    email: String,
    isBusy: Boolean,
    apiKeyConfigured: Boolean,
    apiKeyInput: String,
    apiKeyMessage: String?,
    onApiKeyInputChange: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onClearApiKey: () -> Unit,
    wakeEnabled: Boolean,
    wakeMessage: String?,
    onEnableWakeListening: () -> Unit,
    onDisableWakeListening: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    memoryItems: List<MemoryItem>,
    memoryDraft: String,
    memoryMessage: String?,
    retentionDays: Int,
    memoryContextEnabled: Boolean,
    onMemoryDraftChange: (String) -> Unit,
    onAddMemory: () -> Unit,
    onDeleteMemory: (String) -> Unit,
    onRetentionChange: (Int) -> Unit,
    onMemoryContextChange: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(copy.settingsTitle, style = MaterialTheme.typography.headlineMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(copy.language, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { item ->
                    FilterChip(
                        selected = language == item,
                        onClick = { onLanguageChange(item) },
                        label = { Text(item.nativeName) }
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(copy.appearance, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(copy.darkMode)
                    Text(
                        copy.darkModeBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = darkTheme,
                    onCheckedChange = onDarkThemeChange,
                    modifier = Modifier.testTag("dark_mode_switch")
                )
            }
        }
        val keyTitle = when (language) {
            AppLanguage.English -> "DeepSeek API key"
            AppLanguage.Russian -> "Ключ API DeepSeek"
            AppLanguage.Tajik -> "Калиди API-и DeepSeek"
        }
        val keyHint = when (language) {
            AppLanguage.English -> "Paste your personal API key. It is encrypted with Android Keystore and stays on this phone."
            AppLanguage.Russian -> "Вставьте личный API-ключ. Он шифруется Android Keystore и остаётся на этом телефоне."
            AppLanguage.Tajik -> "Калиди шахсии худро гузоред. Он бо Android Keystore рамзгузорӣ шуда, дар ҳамин телефон мемонад."
        }
        val keyPlaceholder = when (language) {
            AppLanguage.English -> "DeepSeek API key"
            AppLanguage.Russian -> "Ключ DeepSeek"
            AppLanguage.Tajik -> "Калиди DeepSeek"
        }
        val keySaved = when (language) {
            AppLanguage.English -> "Key is stored securely on this device."
            AppLanguage.Russian -> "Ключ безопасно хранится на устройстве."
            AppLanguage.Tajik -> "Калид дар телефон бехатар нигоҳ дошта мешавад."
        }
        val saveLabel = when (language) {
            AppLanguage.English -> "Save API key"
            AppLanguage.Russian -> "Сохранить ключ"
            AppLanguage.Tajik -> "Нигоҳ доштани калид"
        }
        val removeLabel = when (language) {
            AppLanguage.English -> "Remove key"
            AppLanguage.Russian -> "Удалить ключ"
            AppLanguage.Tajik -> "Пок кардани калид"
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(keyTitle, fontWeight = FontWeight.SemiBold)
            Text(keyHint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = onApiKeyInputChange,
                modifier = Modifier.fillMaxWidth().testTag("deepseek_api_key"),
                label = { Text(keyPlaceholder) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
            )
            if (apiKeyConfigured) {
                Text(keySaved, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            if (apiKeyMessage != null) {
                Text(apiKeyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSaveApiKey, enabled = apiKeyInput.isNotBlank() && !isBusy) {
                    Text(saveLabel)
                }
                if (apiKeyConfigured) {
                    TextButton(onClick = onClearApiKey, enabled = !isBusy) { Text(removeLabel) }
                }
            }
        }
        val wakeTitle = when (language) {
            AppLanguage.English -> "Offline wake phrase"
            AppLanguage.Russian -> "Офлайн-фраза активации"
            AppLanguage.Tajik -> "Ибораи бедоркунии офлайн"
        }
        val wakeDisclosure = when (language) {
            AppLanguage.English -> "When enabled, Miss Minutes keeps the microphone active through an Android foreground service while this feature is on. Audio is processed only by Android's on-device speech recognizer (Android 13+ with an installed offline English model); there is no cloud fallback or audio upload. If local recognition is unavailable, listening stops. A persistent notification is shown. Android may stop the service because of Force stop, permission changes or system restrictions."
            AppLanguage.Russian -> "При включении Miss Minutes использует микрофон через фоновую службу Android, пока функция активна. Звук обрабатывается только встроенным офлайн-распознавателем Android (Android 13+ с установленной офлайн-моделью английского языка); облачного резервного режима и отправки аудио нет. Если локальное распознавание недоступно, прослушивание остановится. Постоянное уведомление обязательно. Android может остановить службу при принудительной остановке, отзыве разрешений или системных ограничениях."
            AppLanguage.Tajik -> "Ҳангоми фаъол будан Miss Minutes микрофонро тавассути хидмати пешзаминаи Android истифода мебарад. Овоз танҳо дар дастгоҳ коркард мешавад (Android 13+ ва модели офлайнии забони англисӣ лозим аст); ба абр фиристода намешавад ва роҳи эҳтиётии онлайн вуҷуд надорад. Агар шинохти маҳаллӣ дастнорас бошад, гӯшкунӣ қатъ мешавад. Огоҳиномаи доимӣ нишон дода мешавад. Android метавонад хидматро ҳангоми Force stop, бекор шудани иҷозат ё маҳдудияти система қатъ кунад."
        }
        val wakeStart = when (language) {
            AppLanguage.English -> "Enable microphone listening"
            AppLanguage.Russian -> "Включить прослушивание"
            AppLanguage.Tajik -> "Фаъол кардани гӯшкунии микрофон"
        }
        val wakeStop = when (language) {
            AppLanguage.English -> "Stop listening"
            AppLanguage.Russian -> "Остановить прослушивание"
            AppLanguage.Tajik -> "Қатъ кардани гӯшкунӣ"
        }
        val wakeState = when (language) {
            AppLanguage.English -> if (wakeEnabled) "Listening requested" else "Not listening"
            AppLanguage.Russian -> if (wakeEnabled) "Прослушивание запрошено" else "Не прослушивает"
            AppLanguage.Tajik -> if (wakeEnabled) "Гӯшкунӣ дархост шудааст" else "Гӯш намекунад"
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(wakeTitle, fontWeight = FontWeight.SemiBold)
            Text("Miss Minutes", fontWeight = FontWeight.Medium)
            Text(
                wakeDisclosure,
                modifier = Modifier.testTag("wake_phrase_disclosure"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Text(wakeState, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            if (wakeMessage != null) {
                Text(wakeMessage, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = if (wakeEnabled) onDisableWakeListening else onEnableWakeListening,
                enabled = !isBusy,
                modifier = Modifier.testTag("wake_phrase_toggle")
            ) {
                Text(if (wakeEnabled) wakeStop else wakeStart)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when (language) {
                    AppLanguage.English -> "Local memory"
                    AppLanguage.Russian -> "Локальная память"
                    AppLanguage.Tajik -> "Хотираи маҳаллӣ"
                },
                fontWeight = FontWeight.SemiBold
            )
            Text(
                when (language) {
                    AppLanguage.English -> "Only items you explicitly save are stored. Content is encrypted with Android Keystore and is not synced to a server."
                    AppLanguage.Russian -> "Сохраняются только добавленные вами записи. Данные шифруются Android Keystore и не синхронизируются с сервером."
                    AppLanguage.Tajik -> "Танҳо маълумоте нигоҳ дошта мешавад, ки худатон илова мекунед. Он бо Android Keystore рамзгузорӣ шуда, ба сервер ҳамоҳанг намешавад."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when (language) {
                        AppLanguage.English -> "Include saved memory in AI requests"
                        AppLanguage.Russian -> "Добавлять сохранённую память в запросы ИИ"
                        AppLanguage.Tajik -> "Илова кардани хотира ба дархостҳои AI"
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
                Switch(
                    checked = memoryContextEnabled,
                    onCheckedChange = onMemoryContextChange,
                    modifier = Modifier.testTag("memory_context_toggle")
                )
            }
            Text(
                when (language) {
                    AppLanguage.English -> "Off by default. When enabled, saved notes are sent to DeepSeek with your next message."
                    AppLanguage.Russian -> "По умолчанию выключено. При включении сохранённые заметки отправляются в DeepSeek вместе со следующим сообщением."
                    AppLanguage.Tajik -> "Бо нобаёнӣ хомӯш аст. Ҳангоми фаъол кардан, хотира бо паёми навбатӣ ба DeepSeek фиристода мешавад."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = memoryDraft,
                onValueChange = onMemoryDraftChange,
                modifier = Modifier.fillMaxWidth().testTag("memory_input"),
                label = { Text(if (language == AppLanguage.Tajik) "Чиро дар хотир нигоҳ дорам?" else if (language == AppLanguage.Russian) "Что запомнить?" else "What should I remember?") },
                maxLines = 3
            )
            Button(onClick = onAddMemory, enabled = memoryDraft.isNotBlank()) {
                Text(if (language == AppLanguage.Tajik) "Дар хотира нигоҳ дор" else if (language == AppLanguage.Russian) "Сохранить в памяти" else "Save memory")
            }
            if (memoryMessage != null) {
                Text(memoryMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            memoryItems.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.content, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onDeleteMemory(item.id) }) {
                        Text(if (language == AppLanguage.Tajik) "Пок" else if (language == AppLanguage.Russian) "Удалить" else "Delete")
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (language == AppLanguage.Tajik) "Муҳлати нигоҳдорӣ" else if (language == AppLanguage.Russian) "Срок хранения" else "Retention period",
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (language == AppLanguage.Tajik) "Гуфтугӯҳо ва хотираҳои кӯҳна ба таври худкор пок мешаванд. 0 маънои бе муҳлатро дорад." else if (language == AppLanguage.Russian) "Старые диалоги и записи удаляются автоматически. 0 означает без ограничения срока." else "Older conversations and memories are automatically deleted. 0 means no automatic age-based deletion.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(30, 90, 365, 0).forEach { days ->
                    FilterChip(
                        selected = retentionDays == days,
                        onClick = { onRetentionChange(days) },
                        label = {
                            Text(
                                when (days) {
                                    0 -> if (language == AppLanguage.Tajik) "Ҳамеша" else if (language == AppLanguage.Russian) "Всегда" else "Never"
                                    365 -> if (language == AppLanguage.Tajik) "1 сол" else if (language == AppLanguage.Russian) "1 год" else "1 year"
                                    else -> "$days"
                                }
                            )
                        }
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(copy.account, fontWeight = FontWeight.SemiBold)
            Text(email, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onSignOut, enabled = !isBusy) {
                Text(if (isBusy) copy.wait else copy.signOut)
            }
        }
        Spacer(modifier = Modifier.padding(top = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(copy.about, fontWeight = FontWeight.SemiBold)
            Text("${ProductIdentity.NAME} · ${copy.version} ${ProductIdentity.VERSION_NAME}")
        }
    }
}
