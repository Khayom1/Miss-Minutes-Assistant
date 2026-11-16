package com.khayom1.missminutes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun realMainActivityStartsAtSupabaseSignInGate() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Sign in to Miss Minutes")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Sign in to Miss Minutes").assertIsDisplayed()
    }
}
