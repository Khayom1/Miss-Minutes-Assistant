package com.khayom1.missminutes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchDisplaysChatComposerAndSecureProviderNotice() {
        composeRule.onNodeWithText("How can I help?").assertIsDisplayed()
        composeRule.onNodeWithText("Your personal assistant, ready when you are.").assertIsDisplayed()
        composeRule.onNodeWithText("Message Miss Minutes").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Cloud messages are sent through the authenticated backend. The DeepSeek key stays server-side."
        ).assertIsDisplayed()
    }

    @Test
    fun bottomNavigationOpensDashboardHistoryAndSettings() {
        composeRule.onNodeWithText("Dashboard").performClick()
        composeRule.onNodeWithText("Your workspace").assertIsDisplayed()
        composeRule.onNodeWithText("AI provider").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in required").assertIsDisplayed()

        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithText("Conversation history").assertIsDisplayed()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("App settings").assertIsDisplayed()
    }

    @Test
    fun cloudChatRequiresAnAuthenticatedSession() {
        composeRule.onNodeWithTag("chat_input").performTextInput("Check the secure chat composer")
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Check the secure chat composer").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in required").assertIsDisplayed()
    }

    @Test
    fun settingsExposeAuthenticationAndProviderModes() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Email").assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in").assertIsDisplayed()
        composeRule.onNodeWithText("Create account").assertIsDisplayed()
        composeRule.onNodeWithText("Secure cloud").assertIsDisplayed()
        composeRule.onNodeWithText("Local device").assertIsDisplayed()
    }

    @Test
    fun selectingLocalProviderShowsLoopbackOnlyNotice() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Local device").performClick()
        composeRule.onNodeWithText("Chat").performClick()
        composeRule.onNodeWithText(
            "Local messages are sent only to the loopback model server on this device."
        ).assertIsDisplayed()
    }

    @Test
    fun settingsCanToggleDarkTheme() {
        composeRule.onNodeWithText("Settings").performClick()
        val themeSwitch = composeRule.onNodeWithTag("dark_mode_switch")
        themeSwitch.assertIsOff()
        themeSwitch.performClick()
        themeSwitch.assertIsOn()
    }

    @Test
    fun settingsSwitchesBetweenSupportedLanguages() {
        composeRule.onNodeWithText("Settings").performClick()

        composeRule.onNodeWithText("Русский").performClick()
        composeRule.onNodeWithText("Настройки приложения").assertIsDisplayed()
        composeRule.onNodeWithText("Язык").assertIsDisplayed()

        composeRule.onNodeWithText("Тоҷикӣ").performClick()
        composeRule.onNodeWithText("Танзимоти барнома").assertIsDisplayed()
        composeRule.onNodeWithText("Забон").assertIsDisplayed()
    }
}
