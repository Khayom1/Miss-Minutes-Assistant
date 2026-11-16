package com.khayom1.missminutes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khayom1.missminutes.ProductIdentity

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
    val previewNotice: String,
    val you: String,
    val dashboardTitle: String,
    val dashboardBody: String,
    val aiProvider: String,
    val voice: String,
    val memory: String,
    val deviceTools: String,
    val notConnected: String,
    val historyTitle: String,
    val historyEmpty: String,
    val settingsTitle: String,
    val language: String,
    val appearance: String,
    val darkMode: String,
    val darkModeBody: String,
    val about: String,
    val version: String
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
        previewNotice = "UI preview · AI services will be connected in P2.",
        you = "You",
        dashboardTitle = "Your workspace",
        dashboardBody = "A clear view of assistant capabilities and their connection status.",
        aiProvider = "AI provider",
        voice = "Voice",
        memory = "Memory",
        deviceTools = "Android tools",
        notConnected = "Not connected",
        historyTitle = "Conversation history",
        historyEmpty = "Your saved conversations will appear here.",
        settingsTitle = "App settings",
        language = "Language",
        appearance = "Appearance",
        darkMode = "Dark theme",
        darkModeBody = "Use a dark color scheme throughout the app.",
        about = "About",
        version = "Version"
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
        previewNotice = "Предпросмотр интерфейса · Подключение ИИ появится в P2.",
        you = "Вы",
        dashboardTitle = "Ваше пространство",
        dashboardBody = "Состояние возможностей помощника и подключённых сервисов.",
        aiProvider = "ИИ-провайдер",
        voice = "Голос",
        memory = "Память",
        deviceTools = "Инструменты Android",
        notConnected = "Не подключено",
        historyTitle = "История диалогов",
        historyEmpty = "Сохранённые диалоги будут отображаться здесь.",
        settingsTitle = "Настройки приложения",
        language = "Язык",
        appearance = "Оформление",
        darkMode = "Тёмная тема",
        darkModeBody = "Использовать тёмную цветовую схему приложения.",
        about = "О приложении",
        version = "Версия"
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
        previewNotice = "Намоиши интерфейс · Хидматҳои AI дар марҳилаи P2 пайваст мешаванд.",
        you = "Шумо",
        dashboardTitle = "Муҳити кории шумо",
        dashboardBody = "Ҳолати имкониятҳо ва пайвасти хидматҳои ёвар.",
        aiProvider = "Провайдери AI",
        voice = "Овоз",
        memory = "Хотира",
        deviceTools = "Абзорҳои Android",
        notConnected = "Пайваст нест",
        historyTitle = "Таърихи гуфтугӯҳо",
        historyEmpty = "Гуфтугӯҳои сабтшуда дар ин ҷо намоиш дода мешаванд.",
        settingsTitle = "Танзимоти барнома",
        language = "Забон",
        appearance = "Намуди зоҳирӣ",
        darkMode = "Мавзӯи торик",
        darkModeBody = "Дар тамоми барнома мавзӯи торик истифода шавад.",
        about = "Дар бораи барнома",
        version = "Версия"
    )
}

@Composable
fun MissMinutesApp() {
    var selectedScreenName by rememberSaveable { mutableStateOf(AppScreen.Chat.name) }
    var selectedLanguageName by rememberSaveable { mutableStateOf(AppLanguage.English.name) }
    var darkTheme by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val screen = AppScreen.valueOf(selectedScreenName)
    val language = AppLanguage.valueOf(selectedLanguageName)
    val copy = copyFor(language)

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
                        onDraftChange = { draft = it },
                        onSend = {
                            val value = draft.trim()
                            if (value.isNotEmpty()) {
                                messages.add(
                                    ChatMessage(
                                        id = System.currentTimeMillis(),
                                        text = value,
                                        isUser = true
                                    )
                                )
                                draft = ""
                            }
                        }
                    )
                    AppScreen.Dashboard -> DashboardScreen(copy)
                    AppScreen.History -> HistoryScreen(copy)
                    AppScreen.Settings -> SettingsScreen(
                        copy = copy,
                        language = language,
                        darkTheme = darkTheme,
                        onLanguageChange = { selectedLanguageName = it.name },
                        onDarkThemeChange = { darkTheme = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(
    copy: UiCopy,
    messages: List<ChatMessage>,
    draft: String,
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
            if (messages.isEmpty()) {
                item {
                    Text(
                        text = copy.previewNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            items(messages, key = { it.id }) { message ->
                MessageBubble(message = message, youLabel = copy.you)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(copy.composerHint) },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            Button(
                onClick = onSend,
                enabled = draft.isNotBlank()
            ) {
                Text(copy.send)
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
private fun DashboardScreen(copy: UiCopy) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(copy.dashboardTitle, style = MaterialTheme.typography.headlineMedium)
        Text(copy.dashboardBody, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StatusCard(copy.aiProvider, copy.notConnected)
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
    onLanguageChange: (AppLanguage) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit
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
                    onCheckedChange = onDarkThemeChange
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(copy.about, fontWeight = FontWeight.SemiBold)
            Text("${ProductIdentity.NAME} · ${copy.version} ${ProductIdentity.VERSION_NAME}")
        }
    }
}
