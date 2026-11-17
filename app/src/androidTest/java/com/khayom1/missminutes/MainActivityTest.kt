package com.khayom1.missminutes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import android.Manifest
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.khayom1.missminutes.ai.AiCompletion
import com.khayom1.missminutes.ai.AiMessage
import com.khayom1.missminutes.ai.AiProvider
import com.khayom1.missminutes.ai.AiProviderException
import com.khayom1.missminutes.ai.DeepSeekApiKeyStore
import kotlinx.coroutines.CompletableDeferred
import com.khayom1.missminutes.ai.AiRequest
import com.khayom1.missminutes.ai.AuthFlowResult
import com.khayom1.missminutes.ai.SessionManager
import com.khayom1.missminutes.ai.TtsProvider
import com.khayom1.missminutes.ai.SpeechInputProvider
import com.khayom1.missminutes.ai.SessionStore
import com.khayom1.missminutes.ai.SupabaseAuthRepository
import com.khayom1.missminutes.ai.SupabaseAuthSession
import com.khayom1.missminutes.voice.WakeWordServiceState
import com.khayom1.missminutes.memory.AndroidKeystoreConversationCipher
import com.khayom1.missminutes.memory.LocalConversationStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    private val microphonePermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)
    private val composeRule = createComposeRule()

    @get:Rule
    val testRule = RuleChain.outerRule(microphonePermissionRule).around(composeRule)

    private lateinit var sessionManager: SessionManager
    private lateinit var aiProvider: FakeAiProvider
    private lateinit var apiKeyStore: MemoryApiKeyStore
    private lateinit var conversationStore: LocalConversationStore
    private lateinit var conversationDatabaseName: String

    private class MemoryStore : SessionStore {
        private var session: SupabaseAuthSession? = null

        override fun load(): SupabaseAuthSession? = session

        override fun save(session: SupabaseAuthSession) {
            this.session = session
        }

        override fun clear() {
            session = null
        }
    }

    private class MemoryApiKeyStore : DeepSeekApiKeyStore {
        var key: String? = null
        override fun load(): String? = key
        override fun save(apiKey: String) { key = apiKey }
        override fun clear() { key = null }
    }

    private class FakeAuthRepository : SupabaseAuthRepository {
        override suspend fun signIn(email: String, password: String) = testSession(email)

        override suspend fun signUp(email: String, password: String) =
            AuthFlowResult(session = null, needsEmailConfirmation = true)

        override suspend fun refresh(refreshToken: String) = testSession("user@example.test")

        override suspend fun signOut(accessToken: String) = Unit

        private fun testSession(email: String) = SupabaseAuthSession(
            accessToken = "test-user-access-token",
            refreshToken = "test-user-refresh-token",
            expiresAtEpochSeconds = 10_000L,
            userId = "test-user-${email.hashCode()}",
            email = email
        )
    }

    private class FakeAiProvider : AiProvider {
        var lastRequest: AiRequest? = null
        var nextError: AiProviderException? = null
        var pendingCompletion: CompletableDeferred<AiCompletion>? = null

        override suspend fun complete(request: AiRequest): AiCompletion {
            lastRequest = request
            nextError?.let { throw it }
            pendingCompletion?.let { return it.await() }
            return AiCompletion(
                content = "Mock AI response",
                model = "test-mock"
            )
        }
    }

    @Before
    fun setUp() {
        sessionManager = SessionManager(
            repository = FakeAuthRepository(),
            store = MemoryStore(),
            nowEpochSeconds = { 1_000L }
        )
        aiProvider = FakeAiProvider()
        apiKeyStore = MemoryApiKeyStore()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        conversationDatabaseName = "ui-memory-test-${UUID.randomUUID()}.db"
        conversationStore = LocalConversationStore(
            context,
            AndroidKeystoreConversationCipher("miss_minutes_test_conversation_aes_v1"),
            conversationDatabaseName
        )
        composeRule.setContent {
            com.khayom1.missminutes.ui.MissMinutesApp(
                sessionManager = sessionManager,
                aiProvider = aiProvider,
                apiKeyStore = apiKeyStore,
                ttsProvider = object : TtsProvider {
                    override fun speak(text: String, onDone: () -> Unit, onError: () -> Unit) = onDone()
                    override fun stop() = Unit
                    override fun close() = Unit
                },
                speechInputProvider = object : SpeechInputProvider {
                    override fun start(
                        locale: java.util.Locale,
                        onResult: (String) -> Unit,
                        onError: () -> Unit
                    ) = onError()
                    override fun stop() = Unit
                    override fun close() = Unit
                },
                conversationStore = conversationStore
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Sign in to Miss Minutes")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun tearDown() {
        conversationStore.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(conversationDatabaseName)
    }

    private fun waitForChatScreen() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("chat_input").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun signIn() {
        composeRule.onNodeWithTag("auth_email").performTextInput("user@example.test")
        composeRule.onNodeWithTag("auth_password").performTextInput("correct-horse-battery")
        composeRule.onNodeWithTag("auth_submit").performClick()
        waitForChatScreen()
        composeRule.onNodeWithText("How can I help?").assertIsDisplayed()
    }

    @Test
    fun guestModeOpensAssistantWithoutSupabaseRegistration() {
        composeRule.onNodeWithTag("auth_continue_guest").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_continue_guest").performClick()
        waitForChatScreen()

        composeRule.onNodeWithText("How can I help?").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_input").performTextInput("Hello without an account")
        composeRule.onNodeWithTag("chat_send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Mock AI response").fetchSemanticsNodes().isNotEmpty()
        }

        assertTrue(conversationStore.listConversations("guest-local").isNotEmpty())
        assertTrue(conversationStore.listConversations("test-user-" + "user@example.test".hashCode()).isEmpty())
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Guest (local only)").assertIsDisplayed()
    }

    @Test
    fun guestProviderCredentialFailureDoesNotForceAccountRegistration() {
        composeRule.onNodeWithTag("auth_continue_guest").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_continue_guest").performClick()
        waitForChatScreen()

        aiProvider.nextError = AiProviderException.MissingApiKey()
        composeRule.onNodeWithTag("chat_input").performTextInput("Check guest credentials")
        composeRule.onNodeWithTag("chat_send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(
                "Add your DeepSeek API key in Settings to use AI replies. Guest conversations remain on this device."
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("chat_input").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in to Miss Minutes").assertDoesNotExist()
    }

    @Test
    fun authenticationScreenAcceptsSignInBeforeOpeningAssistant() {
        composeRule.onNodeWithText("Sign in to Miss Minutes").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_submit").assertIsDisplayed()

        signIn()

        composeRule.onNodeWithText("How can I help?").assertIsDisplayed()
        composeRule.onNodeWithText("Dashboard").performClick()
        composeRule.onNodeWithText("user@example.test").assertIsDisplayed()
    }

    @Test
    fun bottomNavigationOpensDashboardHistoryAndSettings() {
        signIn()

        composeRule.onNodeWithText("Dashboard").performClick()
        composeRule.onNodeWithText("Your workspace").assertIsDisplayed()
        composeRule.onNodeWithText("AI provider").assertIsDisplayed()

        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithText("Conversation history").assertIsDisplayed()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("App settings").assertIsDisplayed()
    }

    @Test
    fun chatComposerCallsInjectedProviderAndRendersItsResponse() {
        signIn()

        composeRule.onNodeWithTag("chat_input").performTextInput("Check the authenticated chat")
        composeRule.onNodeWithTag("chat_send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Mock AI response")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Check the authenticated chat").assertIsDisplayed()
        composeRule.onNodeWithText("Mock AI response").performScrollTo().assertIsDisplayed()
        assertEquals(
            AiMessage(
                com.khayom1.missminutes.ai.AiRole.USER,
                "Check the authenticated chat"
            ),
            aiProvider.lastRequest?.messages?.last()
        )
    }

    @Test
    fun historyPersistsConversationAndCanOpenIt() {
        signIn()
        composeRule.onNodeWithTag("chat_input").performTextInput("Persistent conversation title")
        composeRule.onNodeWithTag("chat_send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Mock AI response").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithText("Persistent conversation title").assertIsDisplayed()
        composeRule.onNodeWithText("Open").performClick()
        // The same conversation title is also rendered in History, so wait for
        // the Chat-only composer before asserting that navigation completed.
        waitForChatScreen()
        composeRule.onNodeWithText("How can I help?").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Mock AI response").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Mock AI response").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun lateResponseFromPreviousConversationDoesNotAppearInNewConversation() {
        signIn()
        val lateCompletion = CompletableDeferred<AiCompletion>()
        aiProvider.pendingCompletion = lateCompletion

        composeRule.onNodeWithTag("chat_input").performTextInput("Original conversation prompt")
        composeRule.onNodeWithTag("chat_send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Thinking…").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithText("New conversation").performClick()
        // Navigation happens after the new row is committed and loaded off the UI thread.
        waitForChatScreen()
        composeRule.onNodeWithText("How can I help?").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_input").assertIsDisplayed()

        lateCompletion.complete(AiCompletion(content = "Late original conversation response", model = "test-mock"))
        composeRule.waitForIdle()
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Late original conversation response").fetchSemanticsNodes().size
        )
    }

    @Test
    fun savedMemoryIsSentOnlyAfterExplicitOptIn() {
        signIn()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithTag("memory_input").performScrollTo()
        composeRule.onNodeWithTag("memory_input").performTextInput("Prefers concise Tajik explanations")
        composeRule.onNodeWithText("Save memory").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onAllNodesWithText("Prefers concise Tajik explanations").fetchSemanticsNodes().isNotEmpty()
        }

        val memorySwitch = composeRule.onNodeWithTag("memory_context_toggle")
        memorySwitch.performScrollTo().assertIsOff()
        memorySwitch.performClick()
        composeRule.onNodeWithText("Chat").performClick()
        composeRule.onNodeWithTag("chat_input").performTextInput("How should you explain?")
        composeRule.onNodeWithTag("chat_send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            aiProvider.lastRequest?.messages?.any { it.role.name == "SYSTEM" } == true
        }
        val requestMessages = aiProvider.lastRequest!!.messages
        assertEquals("SYSTEM", requestMessages.first().role.name)
        assertFalse(requestMessages.first().content.contains("Prefers concise Tajik explanations"))
        val memoryContext = requestMessages[1]
        assertEquals("USER", memoryContext.role.name)
        assertTrue(memoryContext.content.contains("Prefers concise Tajik explanations"))
    }

    @Test
    fun chatExposesUserInitiatedOfflineDictationControl() {
        signIn()
        composeRule.onNodeWithTag("speech_input").assertIsDisplayed()
        composeRule.onNodeWithText("Dictate").performClick()
        composeRule.onNodeWithText(
            "On-device speech recognition is unavailable or could not understand the speech."
        ).assertIsDisplayed()
    }

    @Test
    fun expiredSessionClearsPreviousAccountConversationBeforeReauthentication() {
        signIn()
        aiProvider.nextError = AiProviderException.AuthenticationRequired()

        composeRule.onNodeWithTag("chat_input")
            .performTextInput("Private conversation from previous account")
        composeRule.onNodeWithTag("chat_send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Sign in to Miss Minutes")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Sign in to Miss Minutes").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Private conversation from previous account")
                .fetchSemanticsNodes().size
        )

        composeRule.onNodeWithTag("auth_email").performTextInput("new-user@example.test")
        composeRule.onNodeWithTag("auth_password").performTextInput("correct-horse-battery")
        composeRule.onNodeWithTag("auth_submit").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("How can I help?")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Dashboard").performClick()
        composeRule.onNodeWithText("new-user@example.test").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Private conversation from previous account")
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun inFlightAiResponseIsDiscardedAfterSignOutAndAccountSwitch() {
        signIn()
        val lateCompletion = CompletableDeferred<AiCompletion>()
        aiProvider.pendingCompletion = lateCompletion

        composeRule.onNodeWithTag("chat_input")
            .performTextInput("Private pending prompt from old account")
        composeRule.onNodeWithTag("chat_send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Thinking…")
                .fetchSemanticsNodes().isNotEmpty()
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WakeWordServiceState.setEnabled(context, true)
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Sign out").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            !WakeWordServiceState.isEnabled(context) &&
                composeRule.onAllNodesWithText("Sign in to Miss Minutes")
                    .fetchSemanticsNodes().isNotEmpty()
        }

        lateCompletion.complete(
            AiCompletion(content = "Late response from old account", model = "test-mock")
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("auth_email").performTextInput("new-user@example.test")
        composeRule.onNodeWithTag("auth_password").performTextInput("correct-horse-battery")
        composeRule.onNodeWithTag("auth_submit").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("How can I help?")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Dashboard").performClick()
        composeRule.onNodeWithText("new-user@example.test").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Private pending prompt from old account")
                .fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Late response from old account")
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun settingsSavesAndRemovesUserOwnedDeepSeekKey() {
        signIn()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithTag("deepseek_api_key")
            .performTextInput("sk-user-owned-test-key-123456")
        composeRule.onNodeWithText("Save API key").performClick()
        composeRule.waitUntil(timeoutMillis = 2_000) {
            apiKeyStore.key == "sk-user-owned-test-key-123456"
        }
        composeRule.onNodeWithText("Key is stored securely on this device.").assertIsDisplayed()
        composeRule.onNodeWithText("Remove key").performClick()
        composeRule.waitUntil(timeoutMillis = 2_000) { apiKeyStore.key == null }
        assertEquals(null, apiKeyStore.key)
    }

    @Test
    fun settingsShowsMicrophoneDisclosureBeforeListeningIsEnabled() {
        signIn()
        composeRule.onNodeWithText("Settings").performClick()

        composeRule.onNodeWithTag("wake_phrase_disclosure").assertIsDisplayed()
        composeRule.onNodeWithText("Offline wake phrase").assertIsDisplayed()
        composeRule.onNodeWithTag("wake_phrase_toggle").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Enable microphone listening").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsCanToggleDarkTheme() {
        signIn()
        composeRule.onNodeWithText("Settings").performClick()
        val themeSwitch = composeRule.onNodeWithTag("dark_mode_switch")
        themeSwitch.assertIsOff()
        themeSwitch.performClick()
        themeSwitch.assertIsOn()
    }

    @Test
    fun settingsSwitchesBetweenSupportedLanguages() {
        signIn()
        composeRule.onNodeWithText("Settings").performClick()

        composeRule.onNodeWithText("Русский").performClick()
        composeRule.onNodeWithText("Настройки приложения").assertIsDisplayed()
        composeRule.onNodeWithText("Язык").assertIsDisplayed()

        composeRule.onNodeWithText("Тоҷикӣ").performClick()
        composeRule.onNodeWithText("Танзимоти барнома").assertIsDisplayed()
        composeRule.onNodeWithText("Забон").assertIsDisplayed()
    }

    @Test
    fun signUpShowsEmailConfirmationWithoutCreatingAnAuthenticatedSession() {
        composeRule.onNodeWithTag("auth_toggle_mode").performClick()
        composeRule.onNodeWithTag("auth_email").performTextInput("new-user@example.test")
        composeRule.onNodeWithTag("auth_password").performTextInput("strong-password")
        composeRule.onNodeWithTag("auth_submit").performClick()

        composeRule.onNodeWithText(
            "Check your email to confirm your account, then sign in."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Sign in").assertIsDisplayed()
    }
}
