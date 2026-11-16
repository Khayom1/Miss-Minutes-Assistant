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
    fun launchDisplaysChatComposerAndPreviewNotice() {
        composeRule.onNodeWithText("How can I help?").assertIsDisplayed()
        composeRule.onNodeWithText("Your personal assistant, ready when you are.").assertIsDisplayed()
        composeRule.onNodeWithText("Message Miss Minutes").assertIsDisplayed()
        composeRule.onNodeWithText("UI preview · AI services will be connected in P2.").assertIsDisplayed()
    }

    @Test
    fun bottomNavigationOpensDashboardHistoryAndSettings() {
        composeRule.onNodeWithText("Dashboard").performClick()
        composeRule.onNodeWithText("Your workspace").assertIsDisplayed()
        composeRule.onNodeWithText("AI provider").assertIsDisplayed()

        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithText("Conversation history").assertIsDisplayed()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("App settings").assertIsDisplayed()
    }

    @Test
    fun chatComposerAddsLocalPreviewMessage() {
        composeRule.onNodeWithTag("chat_input").performTextInput("Check the chat composer")
        composeRule.onNodeWithText("Send").performClick()
        composeRule.onNodeWithText("Check the chat composer").assertIsDisplayed()
        composeRule.onNodeWithText("UI preview · AI services will be connected in P2.").assertIsDisplayed()
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
