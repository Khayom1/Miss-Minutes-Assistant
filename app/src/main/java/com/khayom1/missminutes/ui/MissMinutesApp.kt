package com.khayom1.missminutes.ui

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khayom1.missminutes.ProductIdentity
import com.khayom1.missminutes.ai.AiCompletion
import com.khayom1.missminutes.ai.AiMessage
import com.khayom1.missminutes.ai.AiProvider
import com.khayom1.missminutes.ai.AiProviderException
import com.khayom1.missminutes.ai.AiRequest
import com.khayom1.missminutes.ai.AiRole
import com.khayom1.missminutes.ai.DeepSeekApiKeyStore
import com.khayom1.missminutes.ai.SessionManager
import com.khayom1.missminutes.ai.SupabaseAuthException
import kotlinx.coroutines.CancellationException
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
    val aiRateLimited: String
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
        gatewayNotice = "Messages use your authenticated Supabase session and the private AI gateway.",
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
        historyEmpty = "Saved conversations will appear here in a later phase.",
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
        aiRateLimited = "The AI request limit was reached. Try again shortly."
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
        gatewayNotice = "Сообщения отправляются через защищённый шлюз с вашей сессией Supabase.",
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
        historyEmpty = "Сохранённые диалоги появятся в одном из следующих этапов.",
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
        aiRateLimited = "Достигнут лимит запросов ИИ. Попробуйте немного позже."
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
        gatewayNotice = "Паёмҳо тавассути сессияи тасдиқшудаи Supabase ва шлюзи муҳофизатшуда мераванд.",
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
        historyEmpty = "Гуфтугӯҳои сабтшуда дар марҳилаи баъдӣ пайдо мешаванд.",
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
        aiRateLimited = "Маҳдудияти дархостҳои AI расид. Каме баъд кӯшиш кунед."
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissMinutesApp(
    sessionManager: SessionManager,
    aiProvider: AiProvider,
    apiKeyStore: DeepSeekApiKeyStore
) {
    val scope = rememberCoroutineScope()
    var selectedScreenName by rememberSaveable { mutableStateOf(AppScreen.Chat.name) }
    var selectedLanguageName by rememberSaveable { mutableStateOf(AppLanguage.English.name) }
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
    var apiKeyConfigured by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    var apiKeyMessage by remember { mutableStateOf<String?>(null) }
    var nextMessageId by remember { mutableStateOf(0L) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val language = AppLanguage.valueOf(selectedLanguageName)
    val copy = copyFor(language)

    LaunchedEffect(apiKeyStore) {
        apiKeyConfigured = runCatching { !apiKeyStore.load().isNullOrBlank() }.getOrDefault(false)
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
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
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
            sessionGeneration += 1
            isAuthenticated = false
            userEmail = ""
            authEmail = ""
            authPassword = ""
            messages.clear()
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
        try {
            apiKeyStore.save(candidate)
            apiKeyConfigured = true
            apiKeyInput = ""
            apiKeyMessage = when (language) {
                AppLanguage.English -> "API key saved securely on this device."
                AppLanguage.Russian -> "API-ключ надёжно сохранён на этом устройстве."
                AppLanguage.Tajik -> "Калиди API дар ҳамин телефон бехатар нигоҳ дошта шуд."
            }
        } catch (_: IllegalArgumentException) {
            apiKeyMessage = when (language) {
                AppLanguage.English -> "The key format looks invalid. Check your DeepSeek key."
                AppLanguage.Russian -> "Неверный формат ключа. Проверьте ключ DeepSeek."
                AppLanguage.Tajik -> "Формати калид нодуруст аст. Калиди DeepSeek-ро санҷед."
            }
        } catch (_: Exception) {
            apiKeyMessage = when (language) {
                AppLanguage.English -> "Could not save the key securely."
                AppLanguage.Russian -> "Не удалось безопасно сохранить ключ."
                AppLanguage.Tajik -> "Калидро бехатар нигоҳ доштан нашуд."
            }
        }
    }

    val clearApiKey: () -> Unit = {
        runCatching { apiKeyStore.clear() }
        apiKeyConfigured = false
        apiKeyInput = ""
        apiKeyMessage = when (language) {
            AppLanguage.English -> "API key removed from this device."
            AppLanguage.Russian -> "API-ключ удалён с этого устройства."
            AppLanguage.Tajik -> "Калиди API аз телефон пок карда шуд."
        }
    }

    val submitMessage: () -> Unit = {
        val value = draft.trim()
        if (value.isNotEmpty() && value.length <= 8_000 && !isSending) {
            val requestMessages = messages.takeLast(19).map { message ->
                AiMessage(
                    role = if (message.isUser) AiRole.USER else AiRole.ASSISTANT,
                    content = message.text
                )
            }.toMutableList()
            requestMessages.add(AiMessage(AiRole.USER, value))
            while (
                requestMessages.size > 1 &&
                requestMessages.sumOf { it.content.length } > 16_000
            ) {
                requestMessages.removeAt(0)
            }

            val request = try {
                AiRequest(messages = requestMessages)
            } catch (_: IllegalArgumentException) {
                chatError = copy.conversationTooLong
                null
            }

            if (request != null) {
                isSending = true
                nextMessageId += 1
                messages.add(ChatMessage(nextMessageId, value, true))
                if (messages.size > 100) messages.removeAt(0)
                draft = ""
                chatError = null
                val requestGeneration = sessionGeneration

                scope.launch {
                    try {
                        val completion: AiCompletion = aiProvider.complete(request)
                        if (requestGeneration != sessionGeneration) return@launch
                        nextMessageId += 1
                        messages.add(ChatMessage(nextMessageId, completion.content, false))
                        if (messages.size > 100) messages.removeAt(0)
                        aiHasResponded = true
                    } catch (_: AiProviderException.AuthenticationRequired) {
                        if (requestGeneration != sessionGeneration) return@launch
                        sessionGeneration += 1
                        authBusy = true
                        isAuthenticated = false
                        userEmail = ""
                        authEmail = ""
                        authPassword = ""
                        messages.clear()
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
                                onSend = submitMessage
                            )
                            AppScreen.Dashboard -> DashboardScreen(
                                copy = copy,
                                email = userEmail,
                                aiStatus = if (aiHasResponded) {
                                    copy.providerResponding
                                } else {
                                    copy.providerNotVerified
                                }
                            )
                            AppScreen.History -> HistoryScreen(copy)
                            AppScreen.Settings -> SettingsScreen(
                                copy = copy,
                                language = language,
                                darkTheme = darkTheme,
                                email = userEmail,
                                isBusy = authBusy,
                                apiKeyConfigured = apiKeyConfigured,
                                apiKeyInput = apiKeyInput,
                                apiKeyMessage = apiKeyMessage,
                                onApiKeyInputChange = { apiKeyInput = it.take(512) },
                                onSaveApiKey = saveApiKey,
                                onClearApiKey = clearApiKey,
                                onLanguageChange = { selectedLanguageName = it.name },
                                onDarkThemeChange = { darkTheme = it },
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
private fun MessageBubble(message: ChatMessage, youLabel: String) {
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
        }
    }
}

@Composable
private fun DashboardScreen(
    copy: UiCopy,
    email: String,
    aiStatus: String
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
    email: String,
    isBusy: Boolean,
    apiKeyConfigured: Boolean,
    apiKeyInput: String,
    apiKeyMessage: String?,
    onApiKeyInputChange: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onClearApiKey: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(copy.account, fontWeight = FontWeight.SemiBold)
            Text(email, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onSignOut, enabled = !isBusy) {
                Text(if (isBusy) copy.wait else copy.signOut)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(copy.about, fontWeight = FontWeight.SemiBold)
            Text("${ProductIdentity.NAME} · ${copy.version} ${ProductIdentity.VERSION_NAME}")
        }
    }
}
