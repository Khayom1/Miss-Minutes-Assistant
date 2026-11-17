package com.khayom1.missminutes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextContains
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
import com.khayom1.missminutes.ai.TtsProvider
import com.khayom1.missminutes.ai.SpeechInputProvider
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

    private lateinit var aiProvider: FakeAiProvider
    private lateinit var apiKeyStore: MemoryApiKeyStore
    private lateinit var conversationStore: LocalConversationStore
    private lateinit var conversationDatabaseName: String

    private class MemoryApiKeyStore : DeepSeekApiKeyStore {
        var key: String? = null
        override fun load(): String? = key
        override fun save(apiKey: String) { key = apiKey }
        override fun clear() { key = null }
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
        aiProvider = FakeAiProvider()
        apiKeyStore = MemoryApiKeyStore()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("app_preferences", 0).edit().clear().commit()
        context.getSharedPreferences("plugin_manifest_store", 0).edit().clear().commit()
        context.getSharedPreferences("plugin_user_consent", 0).edit().clear().commit()
        conversationDatabaseName = "ui-memory-test-${UUID.randomUUID()}.db"
        conversationStore = LocalConversationStore(
            context,
            AndroidKeystoreConversationCipher("miss_minutes_test_conversation_aes_v1"),
            conversationDatabaseName
        )
        composeRule.setContent {
            com.khayom1.missminutes.ui.MissMinutesApp(
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
        waitForChatScreen()
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

    private fun assertChatReady() {
        waitForChatScreen()
        composeRule.onNodeWithText("How can I help?").assertIsDisplayed()
    }

    @Test
    fun guestWorkspaceOpensAssistantWithoutAccount() {
        waitForChatScreen()

        composeRule.onNodeWithText("How can I help?").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_input").performTextInput("Hello without an account")
        composeRule.onNodeWithTag("chat_send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Mock AI response").fetchSemanticsNodes().isNotEmpty()
        }

        assertTrue(conversationStore.listConversations("guest-local").isNotEmpty())
    }

    @Test
    fun guestProviderCredentialFailureDoesNotForceAccountRegistration() {
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
        composeRule.onNodeWithTag("api_key_settings_action").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("deepseek_api_key").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Sign in to Miss Minutes").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun topControlsExposeHistoryNewChatAndSettingsOnly() {
        assertChatReady()

        composeRule.onNodeWithText("History").assertIsDisplayed()
        composeRule.onNodeWithText("New").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Dashboard").fetchSemanticsNodes().size)

        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithText("Conversation history").assertIsDisplayed()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onAllNodesWithText("App settings").get(0).assertIsDisplayed()

        composeRule.onNodeWithText("New").performClick()
        waitForChatScreen()
        composeRule.onNodeWithText("How can I help?").assertIsDisplayed()
    }

    @Test
    fun chatComposerCallsInjectedProviderAndRendersItsResponse() {
        assertChatReady()

        composeRule.onNodeWithTag("chat_input").performTextInput("Check the local guest chat")
        composeRule.onNodeWithTag("chat_send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Mock AI response")
                .fetchSemanticsNodes().isNotEmpty()
        }

        val ownerId = "guest-local"
        val savedConversation = conversationStore.listConversations(ownerId).single()
        val savedMessages = conversationStore.getMessages(ownerId, savedConversation.id)
        assertTrue(savedMessages.any { it.role == LocalConversationStore.ROLE_USER && it.content == "Check the local guest chat" })
        assertTrue(savedMessages.any { it.role == LocalConversationStore.ROLE_ASSISTANT && it.content == "Mock AI response" })
        composeRule.onNodeWithText("Mock AI response").performScrollTo().assertIsDisplayed()
        assertEquals(
            AiMessage(
                com.khayom1.missminutes.ai.AiRole.USER,
                "Check the local guest chat"
            ),
            aiProvider.lastRequest?.messages?.last()
        )
    }

    @Test
    fun historyPersistsConversationAndCanOpenIt() {
        assertChatReady()
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
        assertChatReady()
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
        assertChatReady()
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
        composeRule.onNodeWithText("New").performClick()
        waitForChatScreen()
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
        assertChatReady()
        composeRule.onNodeWithTag("speech_input").assertIsDisplayed()
        composeRule.onNodeWithText("Dictate").performClick()
        composeRule.onNodeWithText(
            "On-device speech recognition is unavailable or could not understand the speech."
        ).assertIsDisplayed()
    }

    @Test
    fun rejectedDeepSeekKeyShowsClearSettingsHintWithoutAccountGate() {
        assertChatReady()
        aiProvider.nextError = AiProviderException.InvalidApiKey()
        composeRule.onNodeWithTag("chat_input").performTextInput("Test rejected API key")
        composeRule.onNodeWithTag("chat_send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(
                "DeepSeek rejected this API key. Check it or replace it in Settings."
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("chat_input").assertIsDisplayed()
        composeRule.onNodeWithTag("api_key_settings_action").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("deepseek_api_key").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Sign in to Miss Minutes").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun rateLimitedProviderErrorDoesNotOfferCredentialSettingsAction() {
        assertChatReady()
        aiProvider.nextError = AiProviderException.RateLimited()
        composeRule.onNodeWithTag("chat_input").performTextInput("Check rate limit handling")
        composeRule.onNodeWithTag("chat_send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("The AI request limit was reached. Try again shortly.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            composeRule.onAllNodesWithTag("api_key_settings_action").fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun settingsSavesAndRemovesUserOwnedDeepSeekKey() {
        assertChatReady()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithTag("deepseek_api_key")
            .performTextInput("sk-user-owned-test-key-123456")
        composeRule.onNodeWithText("Save API key").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            apiKeyStore.key == "sk-user-owned-test-key-123456"
        }
        composeRule.onNodeWithText("Key is stored securely on this device.").assertIsDisplayed()
        composeRule.onNodeWithText("Remove key").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { apiKeyStore.key == null }
        assertEquals(null, apiKeyStore.key)
    }

    @Test
    fun settingsExposeExplicitFloatingAssistantControls() {
        assertChatReady()
        composeRule.onNodeWithText("Settings").performClick()

        composeRule.onNodeWithText("Floating assistant").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("overlay_start").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("overlay_stop").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsShowsMicrophoneDisclosureBeforeListeningIsEnabled() {
        assertChatReady()
        composeRule.onNodeWithText("Settings").performClick()

        composeRule.onNodeWithTag("wake_phrase_disclosure").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("wake_phrase_disclosure").assertTextContains("Android 12+", substring = true)
        composeRule.onNodeWithText("Offline wake phrase").assertIsDisplayed()
        composeRule.onNodeWithTag("wake_phrase_toggle").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Enable microphone listening").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsExposeEncryptedBackupAndRestoreControls() {
        assertChatReady()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Backup and restore").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Backup password (at least 10 characters)")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "The backup is password-encrypted. API keys, Android permissions and plugin consent are never included."
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsExposeLocalPluginPackageImportWithoutBackupPassword() {
        assertChatReady()
        composeRule.onNodeWithText("Settings").performClick()

        composeRule.onNodeWithTag("plugin_package_import")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            "Plugin packages contain metadata only. No code, credentials, Android permissions or consent are imported."
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsCanToggleDarkTheme() {
        assertChatReady()
        composeRule.onNodeWithText("Settings").performClick()
        val themeSwitch = composeRule.onNodeWithTag("dark_mode_switch")
        themeSwitch.assertIsOff()
        themeSwitch.performClick()
        themeSwitch.assertIsOn()
    }

    @Test
    fun settingsSwitchesBetweenSupportedLanguages() {
        assertChatReady()
        composeRule.onNodeWithText("Settings").performClick()

        composeRule.onNodeWithText("Русский").performClick()
        composeRule.onAllNodesWithText("Настройки приложения").get(0).assertIsDisplayed()
        composeRule.onNodeWithText("Язык").assertIsDisplayed()

        composeRule.onNodeWithText("Тоҷикӣ").performClick()
        composeRule.onAllNodesWithText("Танзимоти барнома").get(0).assertIsDisplayed()
        composeRule.onNodeWithText("Забон").assertIsDisplayed()
    }

    @Test
    fun connectedPluginToolsAreAutomaticallyInjectedIntoModelRequests() {
        assertChatReady()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithTag("plugin_consent_android_device_status")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("New").performClick()
        // Creating/loading a conversation is asynchronous; wait for Chat to be
        // composed before interacting with its input field.
        waitForChatScreen()
        composeRule.onNodeWithText("How can I help?").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_input").performTextInput("What device tools are available?")
        composeRule.onNodeWithTag("chat_send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            aiProvider.lastRequest != null
        }

        val tools = aiProvider.lastRequest?.tools.orEmpty()
        assertTrue("Connected tools should be discovered without a per-turn toggle.", tools.isNotEmpty())
        assertTrue(tools.all { it.name.startsWith("plugin_") })
    }
}
