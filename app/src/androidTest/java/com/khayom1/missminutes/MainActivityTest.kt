package com.khayom1.missminutes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khayom1.missminutes.ai.AiCompletion
import com.khayom1.missminutes.ai.AiMessage
import com.khayom1.missminutes.ai.AiProvider
import com.khayom1.missminutes.ai.AiRequest
import com.khayom1.missminutes.ai.AuthFlowResult
import com.khayom1.missminutes.ai.SessionManager
import com.khayom1.missminutes.ai.SessionStore
import com.khayom1.missminutes.ai.SupabaseAuthRepository
import com.khayom1.missminutes.ai.SupabaseAuthSession
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var sessionManager: SessionManager
    private lateinit var aiProvider: FakeAiProvider

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
            userId = "test-user-id",
            email = email
        )
    }

    private class FakeAiProvider : AiProvider {
        var lastRequest: AiRequest? = null

        override suspend fun complete(request: AiRequest): AiCompletion {
            lastRequest = request
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
        composeRule.setContent {
            com.khayom1.missminutes.ui.MissMinutesApp(
                sessionManager = sessionManager,
                aiProvider = aiProvider
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Sign in to Miss Minutes")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun signIn() {
        composeRule.onNodeWithTag("auth_email").performTextInput("user@example.test")
        composeRule.onNodeWithTag("auth_password").performTextInput("correct-horse-battery")
        composeRule.onNodeWithTag("auth_submit").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("How can I help?")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun authenticationScreenAcceptsSignInBeforeOpeningAssistant() {
        composeRule.onNodeWithText("Sign in to Miss Minutes").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_submit").assertIsDisplayed()

        signIn()

        composeRule.onNodeWithText("How can I help?").assertIsDisplayed()
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
        composeRule.onNodeWithText("Mock AI response").assertIsDisplayed()
        assert(aiProvider.lastRequest?.messages?.last() == AiMessage(
            com.khayom1.missminutes.ai.AiRole.USER,
            "Check the authenticated chat"
        ))
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
