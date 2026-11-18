package com.khayom1.missminutes.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khayom1.missminutes.ai.AiToolApprovalPrompt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiToolApprovalDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val prompt = AiToolApprovalPrompt(
        toolName = "calendar_create_event",
        pluginName = "Calendar",
        description = "Create a calendar event.",
        argumentNames = listOf("date", "title")
    )

    @Test
    fun showsActionDescriptionAndFieldNamesWithoutArgumentValues() {
        composeRule.setContent {
            MaterialTheme {
                AiToolApprovalDialog(prompt, AppLanguage.English, {}, {})
            }
        }

        composeRule.onNodeWithText("Calendar").assertIsDisplayed()
        composeRule.onNodeWithText("calendar_create_event").assertIsDisplayed()
        composeRule.onNodeWithText("Create a calendar event.").assertIsDisplayed()
        composeRule.onNodeWithText("Requested fields: date, title").assertIsDisplayed()
        assertFalse(composeRule.onAllNodesWithText("private event").fetchSemanticsNodes().isNotEmpty())
        assertFalse(composeRule.onAllNodesWithText("secret-value").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun approveAndRejectButtonsInvokeOnlyTheirOwnCallbacks() {
        var approved = false
        var rejected = false
        composeRule.setContent {
            MaterialTheme {
                AiToolApprovalDialog(
                    prompt,
                    AppLanguage.English,
                    onApprove = { approved = true },
                    onReject = { rejected = true }
                )
            }
        }

        composeRule.onNodeWithTag("approve_plugin_tool").performClick()
        assertTrue(approved)
        assertFalse(rejected)
    }

    @Test
    fun rejectButtonInvokesRejectionWithoutApproval() {
        var approved = false
        var rejected = false
        composeRule.setContent {
            MaterialTheme {
                AiToolApprovalDialog(
                    prompt,
                    AppLanguage.English,
                    onApprove = { approved = true },
                    onReject = { rejected = true }
                )
            }
        }

        composeRule.onNodeWithTag("reject_plugin_tool").performClick()
        assertTrue(rejected)
        assertFalse(approved)
    }

    @Test
    fun dialogLabelsFollowTheSelectedAppLanguage() {
        val language = mutableStateOf(AppLanguage.Tajik)
        composeRule.setContent {
            MaterialTheme {
                AiToolApprovalDialog(prompt, language.value, {}, {})
            }
        }

        composeRule.onNodeWithText("Иҷозат додан").assertIsDisplayed()
        composeRule.runOnIdle { language.value = AppLanguage.Russian }
        composeRule.onNodeWithText("Разрешить").assertIsDisplayed()
        composeRule.runOnIdle { language.value = AppLanguage.English }
        composeRule.onNodeWithText("Approve once").assertIsDisplayed()
    }
}
