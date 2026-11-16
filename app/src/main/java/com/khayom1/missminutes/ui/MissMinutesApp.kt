package com.khayom1.missminutes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khayom1.missminutes.ProductIdentity
import com.khayom1.missminutes.ai.ChatMessage
import com.khayom1.missminutes.ai.ChatProviderResult
import com.khayom1.missminutes.ai.ChatRequest
import com.khayom1.missminutes.ai.ChatRole
import com.khayom1.missminutes.ai.ProviderErrorCode
import com.khayom1.missminutes.ai.ProviderMode
import com.khayom1.missminutes.ai.ProviderRouter
import com.khayom1.missminutes.auth.AuthRepository
import com.khayom1.missminutes.auth.AuthResult
import com.khayom1.missminutes.auth.SessionStore
import kotlinx.coroutines.launch

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

private data class UiChatMessage(
    val id: Long,
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false
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
    val sending: String,
    val you: String,
    val dashboardTitle: String,
    val dashboardBody: String,
    val aiProvider: String,
    val voice: String,
    val memory: String,
    val deviceTools: String,
    val notConnected: String,
    val signedIn: String,
    val signInRequired: String,
    val localConfigured: String,
    val historyTitle: String,
    val historyEmpty: String,
    val settingsTitle: String,
    val language: String,
    val appearance: String,
    val darkMode: String,
    val darkModeBody: String,
    val about: String,
    val version: String,
    val providerMode: String,
    val remoteProvider: String,
    val localProvider: String,
    val localDescription: String,
    val account: String,
    val email: String,
    val password: String,
    val signIn: String,
    val signUp: String,
    val signOut: String,
    val signedInAs: String,
    val signInHint: String,
    val emailConfirmation: String,
    val authFailed: String,
    val remoteNotice: String,
    val localNotice: String,
    val providerNotConfigured: String,
    val localModelUnavailable: String,
    val rateLimited: String,
    val networkError: String,
    val timeoutError: String,
    val invalidResponse: String,
    val invalidMessage: String
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
        sending = "Working…",
        you = "You",
        dashboardTitle = "Your workspace",
        dashboardBody = "A clear view of assistant capabilities and their connection status.",
        aiProvider = "AI provider",
        voice = "Voice",
        memory = "Memory",
        deviceTools = "Android tools",
        notConnected = "Not connected",
        signedIn = "Signed in",
        signInRequired = "Sign in required",
        localConfigured = "Loopback endpoint configured; availability not verified",
        historyTitle = "Conversation history",
        historyEmpty = "Saved conversations will appear here after P4 storage is implemented.",
        settingsTitle = "App settings",
        language = "Language",
        appearance = "Appearance",
        darkMode = "Dark theme",
        darkModeBody = "Use a dark color scheme throughout the app.",
        about = "About",
        version = "Version",
        providerMode = "AI provider",
        remoteProvider = "Secure cloud",
        localProvider = "Local device",
        localDescription = "Local mode connects only to 127.0.0.1 on this device. Start a compatible local model server first.",
        account = "Account",
        email = "Email",
        password = "Password",
        signIn = "Sign in",
        signUp = "Create account",
        signOut = "Sign out",
        signedInAs = "Signed in as",
        signInHint = "Your password is sent only to Supabase Auth over HTTPS. The provider key never leaves the backend.",
        emailConfirmation = "Check your email to confirm the account, then sign in.",
        authFailed = "Authentication failed. Check the details and try again.",
        remoteNotice = "Cloud messages are sent through the authenticated backend. The DeepSeek key stays server-side.",
        localNotice = "Local messages are sent only to the loopback model server on this device.",
        providerNotConfigured = "The secure AI backend is not configured yet.",
        localModelUnavailable = "The local model server is unavailable. Start it on 127.0.0.1:5000 or select Secure cloud.",
        rateLimited = "Request limit reached. Wait a moment before trying again.",
        networkError = "Network request failed. Check your connection and try again.",
        timeoutError = "The request timed out. Try again.",
        invalidResponse = "The provider returned an invalid response.",
        invalidMessage = "The message could not be sent. Check its length and try again."
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
        sending = "Обработка…",
        you = "Вы",
        dashboardTitle = "Ваше пространство",
        dashboardBody = "Состояние возможностей помощника и подключённых сервисов.",
        aiProvider = "ИИ-провайдер",
        voice = "Голос",
        memory = "Память",
        deviceTools = "Инструменты Android",
        notConnected = "Не подключено",
        signedIn = "Вход выполнен",
        signInRequired = "Требуется вход",
        localConfigured = "Локальный адрес задан; доступность не проверена",
        historyTitle = "История диалогов",
        historyEmpty = "Сохранённые диалоги появятся здесь после реализации хранения в P4.",
        settingsTitle = "Настройки приложения",
        language = "Язык",
        appearance = "Оформление",
        darkMode = "Тёмная тема",
        darkModeBody = "Использовать тёмную цветовую схему приложения.",
        about = "О приложении",
        version = "Версия",
        providerMode = "ИИ-провайдер",
        remoteProvider = "Защищённый облачный",
        localProvider = "На устройстве",
        localDescription = "Локальный режим подключается только к 127.0.0.1 на этом устройстве. Сначала запустите совместимый локальный сервер модели.",
        account = "Учётная запись",
        email = "Электронная почта",
        password = "Пароль",
        signIn = "Войти",
        signUp = "Создать аккаунт",
        signOut = "Выйти",
        signedInAs = "Вы вошли как",
        signInHint = "Пароль передаётся только в Supabase Auth по HTTPS. Ключ провайдера остаётся на сервере.",
        emailConfirmation = "Подтвердите аккаунт по электронной почте, затем войдите.",
        authFailed = "Не удалось выполнить вход. Проверьте данные и повторите попытку.",
        remoteNotice = "Облачные сообщения проходят через авторизованный сервер. Ключ DeepSeek хранится только на сервере.",
        localNotice = "Локальные сообщения отправляются только на модельный сервер этого устройства.",
        providerNotConfigured = "Защищённый ИИ-сервер ещё не настроен.",
        localModelUnavailable = "Локальный сервер модели недоступен. Запустите его на 127.0.0.1:5000 или выберите облачный режим.",
        rateLimited = "Лимит запросов достигнут. Подождите немного.",
        networkError = "Ошибка сети. Проверьте подключение и повторите попытку.",
        timeoutError = "Время ожидания истекло. Попробуйте ещё раз.",
        invalidResponse = "Провайдер вернул некорректный ответ.",
        invalidMessage = "Не удалось отправить сообщение. Проверьте его длину."
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
        sending = "Кор карда истодааст…",
        you = "Шумо",
        dashboardTitle = "Муҳити кории шумо",
        dashboardBody = "Ҳолати имкониятҳо ва пайвасти хидматҳои ёвар.",
        aiProvider = "Провайдери AI",
        voice = "Овоз",
        memory = "Хотира",
        deviceTools = "Абзорҳои Android",
        notConnected = "Пайваст нест",
        signedIn = "Вуруд иҷро шуд",
        signInRequired = "Вуруд лозим аст",
        localConfigured = "Суроғаи маҳаллӣ танзим шудааст; дастрасӣ санҷида нашудааст",
        historyTitle = "Таърихи гуфтугӯҳо",
        historyEmpty = "Баъди илова шудани нигоҳдорӣ дар P4 гуфтугӯҳои сабтшуда ин ҷо пайдо мешаванд.",
        settingsTitle = "Танзимоти барнома",
        language = "Забон",
        appearance = "Намуди зоҳирӣ",
        darkMode = "Мавзӯи торик",
        darkModeBody = "Дар тамоми барнома мавзӯи торик истифода шавад.",
        about = "Дар бораи барнома",
        version = "Версия",
        providerMode = "Провайдери AI",
        remoteProvider = "Абри муҳофизатшуда",
        localProvider = "Дар дастгоҳ",
        localDescription = "Реҷаи маҳаллӣ танҳо ба 127.0.0.1-и ҳамин дастгоҳ пайваст мешавад. Аввал сервери модели маҳаллиро фаъол кунед.",
        account = "Ҳисоб",
        email = "Почтаи электронӣ",
        password = "Рамз",
        signIn = "Даромадан",
        signUp = "Сохтани ҳисоб",
        signOut = "Баромадан",
        signedInAs = "Шумо ворид шудед ҳамчун",
        signInHint = "Рамз танҳо тавассути HTTPS ба Supabase Auth фиристода мешавад. Калиди провайдер дар сервер мемонад.",
        emailConfirmation = "Ҳисобро аз почтаи электронӣ тасдиқ кунед ва баъд ворид шавед.",
        authFailed = "Вуруд иҷро нашуд. Маълумотро санҷида, боз кӯшиш кунед.",
        remoteNotice = "Паёмҳои абрӣ тавассути сервери тасдиқшуда мераванд. Калиди DeepSeek танҳо дар сервер мемонад.",
        localNotice = "Паёмҳои маҳаллӣ танҳо ба сервери модели ҳамин дастгоҳ фиристода мешаванд.",
        providerNotConfigured = "Сервери муҳофизатшудаи AI ҳоло танзим нашудааст.",
        localModelUnavailable = "Сервери модели маҳаллӣ дастрас нест. Онро дар 127.0.0.1:5000 фаъол кунед ё реҷаи абриро интихоб кунед.",
        rateLimited = "Ҳадди дархостҳо пур шуд. Каме интизор шавед.",
        networkError = "Хатои шабака. Пайвастро санҷида, боз кӯшиш кунед.",
        timeoutError = "Вақти интизорӣ гузашт. Боз кӯшиш кунед.",
        invalidResponse = "Провайдер ҷавоби нодуруст баргардонд.",
        invalidMessage = "Паём фиристода нашуд. Дарозии онро санҷед."
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissMinutesApp(
    authRepository: AuthRepository,
    sessionStore: SessionStore,
    providerRouter: ProviderRouter
) {
    var selectedScreenName by rememberSaveable { mutableStateOf(AppScreen.Chat.name) }
    var selectedLanguageName by rememberSaveable { mutableStateOf(AppLanguage.English.name) }
    var selectedProviderModeName by rememberSaveable { mutableStateOf(ProviderMode.REMOTE.name) }
    var darkTheme by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    val messages = remember { mutableStateListOf<UiChatMessage>() }
    val scope = rememberCoroutineScope()
    var signedInEmail by remember { mutableStateOf(sessionStore.currentSession?.email) }
    var authMessage by remember { mutableStateOf("") }
    var authBusy by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var nextMessageId by remember { mutableStateOf(0L) }

    val screen = AppScreen.valueOf(selectedScreenName)
    val language = AppLanguage.valueOf(selectedLanguageName)
    val providerMode = ProviderMode.valueOf(selectedProviderModeName)
    val copy = copyFor(language)

    fun nextId(): Long {
        nextMessageId += 1L
        return nextMessageId
    }

    fun providerErrorText(code: ProviderErrorCode): String = when (code) {
        ProviderErrorCode.NOT_CONFIGURED -> copy.providerNotConfigured
        ProviderErrorCode.AUTHENTICATION_REQUIRED -> copy.signInRequired
        ProviderErrorCode.INVALID_REQUEST -> copy.invalidMessage
        ProviderErrorCode.RATE_LIMITED -> copy.rateLimited
        ProviderErrorCode.NETWORK -> copy.networkError
        ProviderErrorCode.TIMEOUT -> copy.timeoutError
        ProviderErrorCode.SERVICE_UNAVAILABLE -> copy.networkError
        ProviderErrorCode.INVALID_RESPONSE -> copy.invalidResponse
        ProviderErrorCode.LOCAL_MODEL_UNAVAILABLE -> copy.localModelUnavailable
    }

    fun submitAuth(action: suspend () -> AuthResult) {
        if (authBusy) return
        authBusy = true
        scope.launch {
            try {
                when (val result = action()) {
                is AuthResult.SignedIn -> {
                    signedInEmail = result.session.email
                    authMessage = copy.signedIn
                }
                AuthResult.SignedOut -> {
                    signedInEmail = null
                    authMessage = ""
                }
                AuthResult.NeedsEmailConfirmation -> {
                    signedInEmail = null
                    authMessage = copy.emailConfirmation
                }
                is AuthResult.Failure -> {
                    signedInEmail = sessionStore.currentSession?.email
                    authMessage = copy.authFailed
                }
                }
            } catch (_: Exception) {
                signedInEmail = sessionStore.currentSession?.email
                authMessage = copy.authFailed
            } finally {
                authBusy = false
            }
        }
    }

    fun sendMessage() {
        val value = draft.trim()
        if (value.isEmpty() || sending) return

        messages.add(UiChatMessage(id = nextId(), text = value, isUser = true))
        draft = ""
        val requestMessages = messages
            .filterNot { it.isError }
            .map {
                ChatMessage(
                    role = if (it.isUser) ChatRole.USER else ChatRole.ASSISTANT,
                    content = it.text
                )
            }

        sending = true
        scope.launch {
            try {
                when (val result = providerRouter.complete(ChatRequest(requestMessages), providerMode)) {
                is ChatProviderResult.Success -> messages.add(
                    UiChatMessage(
                        id = nextId(),
                        text = result.response.content,
                        isUser = false
                    )
                )
                is ChatProviderResult.Failure -> messages.add(
                    UiChatMessage(
                        id = nextId(),
                        text = providerErrorText(result.error.code),
                        isUser = false,
                        isError = true
                    )
                )
                }
            } catch (_: Exception) {
                messages.add(
                    UiChatMessage(
                        id = nextId(),
                        text = copy.networkError,
                        isUser = false,
                        isError = true
                    )
                )
            } finally {
                sending = false
            }
        }
    }

    val providerStatus = when (providerMode) {
        ProviderMode.REMOTE -> if (signedInEmail == null) copy.signInRequired else copy.signedIn
        ProviderMode.LOCAL -> copy.localConfigured
    }

    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
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
                        sending = sending,
                        providerMode = providerMode,
                        onDraftChange = { draft = it },
                        onSend = ::sendMessage
                    )
                    AppScreen.Dashboard -> DashboardScreen(copy, providerStatus)
                    AppScreen.History -> HistoryScreen(copy)
                    AppScreen.Settings -> SettingsScreen(
                        copy = copy,
                        language = language,
                        darkTheme = darkTheme,
                        providerMode = providerMode,
                        signedInEmail = signedInEmail,
                        authMessage = authMessage,
                        authBusy = authBusy,
                        onLanguageChange = { selectedLanguageName = it.name },
                        onDarkThemeChange = { darkTheme = it },
                        onProviderModeChange = { selectedProviderModeName = it.name },
                        onSignIn = { email, password ->
                            submitAuth { authRepository.signIn(email, password) }
                        },
                        onSignUp = { email, password ->
                            submitAuth { authRepository.signUp(email, password) }
                        },
                        onSignOut = {
                            submitAuth { authRepository.signOut() }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(
    copy: UiCopy,
    messages: List<UiChatMessage>,
    draft: String,
    sending: Boolean,
    providerMode: ProviderMode,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
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
                MessageBubble(message = message, youLabel = copy.you)
            }
        }
        Text(
            text = if (providerMode == ProviderMode.REMOTE) copy.remoteNotice else copy.localNotice,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 2.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f).testTag("chat_input"),
                placeholder = { Text(copy.composerHint) },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            Button(
                onClick = onSend,
                enabled = draft.isNotBlank() && !sending
            ) {
                Text(if (sending) copy.sending else copy.send)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: UiChatMessage, youLabel: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                message.isError -> MaterialTheme.colorScheme.errorContainer
                message.isUser -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
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
        }
    }
}

@Composable
private fun DashboardScreen(copy: UiCopy, providerStatus: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(copy.dashboardTitle, style = MaterialTheme.typography.headlineMedium)
        Text(copy.dashboardBody, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StatusCard(copy.aiProvider, providerStatus)
        StatusCard(copy.voice, copy.notConnected)
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
private fun HistoryScreen(copy: UiCopy) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(copy.historyTitle, style = MaterialTheme.typography.headlineMedium)
        Text(copy.historyEmpty, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsScreen(
    copy: UiCopy,
    language: AppLanguage,
    darkTheme: Boolean,
    providerMode: ProviderMode,
    signedInEmail: String?,
    authMessage: String,
    authBusy: Boolean,
    onLanguageChange: (AppLanguage) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onProviderModeChange: (ProviderMode) -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onSignOut: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(copy.settingsTitle, style = MaterialTheme.typography.headlineMedium)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(copy.providerMode, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = providerMode == ProviderMode.REMOTE,
                    onClick = { onProviderModeChange(ProviderMode.REMOTE) },
                    label = { Text(copy.remoteProvider) }
                )
                FilterChip(
                    selected = providerMode == ProviderMode.LOCAL,
                    onClick = { onProviderModeChange(ProviderMode.LOCAL) },
                    label = { Text(copy.localProvider) }
                )
            }
            Text(copy.localDescription, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(copy.account, fontWeight = FontWeight.SemiBold)
            if (signedInEmail != null) {
                Text("${copy.signedInAs}: $signedInEmail")
                Button(onClick = onSignOut, enabled = !authBusy) {
                    Text(copy.signOut)
                }
            } else {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(copy.email) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(copy.password) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onSignIn(email, password); password = "" },
                        enabled = !authBusy && email.isNotBlank() && password.length >= 8
                    ) {
                        Text(copy.signIn)
                    }
                    Button(
                        onClick = { onSignUp(email, password); password = "" },
                        enabled = !authBusy && email.isNotBlank() && password.length >= 8
                    ) {
                        Text(copy.signUp)
                    }
                }
                Text(copy.signInHint, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (authMessage.isNotBlank()) {
                Text(authMessage, color = MaterialTheme.colorScheme.primary)
            }
        }

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

        Spacer(modifier = Modifier.padding(top = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(copy.about, fontWeight = FontWeight.SemiBold)
            Text("${ProductIdentity.NAME} · ${copy.version} ${ProductIdentity.VERSION_NAME}")
        }
    }
}
