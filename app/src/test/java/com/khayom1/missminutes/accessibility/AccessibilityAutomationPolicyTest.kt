package com.khayom1.missminutes.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityAutomationPolicyTest {
    @Test
    fun ordinaryMessagingControlsAreNotTreatedAsDangerous() {
        assertFalse(AccessibilityAutomationPolicy.isHighRiskControlLabel("Send"))
        assertFalse(AccessibilityAutomationPolicy.isHighRiskControlLabel("Post comment"))
        assertFalse(AccessibilityAutomationPolicy.isHighRiskControlLabel("Reply"))
    }

    @Test
    fun destructiveAndFinancialControlsRequireAdditionalConfirmation() {
        assertTrue(AccessibilityAutomationPolicy.isHighRiskControlLabel("Delete account"))
        assertTrue(AccessibilityAutomationPolicy.isHighRiskControlLabel("Pay now"))
        assertTrue(AccessibilityAutomationPolicy.isHighRiskControlLabel("Transfer money"))
        assertTrue(AccessibilityAutomationPolicy.isHighRiskControlLabel("Оплатить"))
        assertTrue(AccessibilityAutomationPolicy.isHighRiskControlLabel("Нест кардан"))
        assertTrue(AccessibilityAutomationPolicy.isHighRiskControlLabel("Allow access to contacts"))
        assertTrue(AccessibilityAutomationPolicy.isHighRiskControlLabel("Разрешить доступ"))
        assertTrue(AccessibilityAutomationPolicy.isHighRiskControlLabel("дастрасӣ ба камера"))
    }

    @Test
    fun labelMatchingIsCaseAndWhitespaceInsensitive() {
        assertTrue(AccessibilityAutomationPolicy.normalize("  POST   COMMENT ") == "post comment")
    }

    @Test
    fun dangerousActionNeedsAnExplicitSecondConfirmationForTheSameLabel() {
        assertTrue(
            AccessibilityAutomationPolicy.isExplicitDangerousActionConfirmation(
                "I confirm Delete account",
                "Delete account"
            )
        )
        assertTrue(
            AccessibilityAutomationPolicy.isExplicitDangerousActionConfirmation(
                "Тасдиқ мекунам",
                "Нест кардан"
            )
        )
        assertFalse(
            AccessibilityAutomationPolicy.isExplicitDangerousActionConfirmation(
                "Please delete the account",
                "Delete account"
            )
        )
    }
    @Test
    fun authenticationAndCredentialFieldsAreIdentifiedAcrossSupportedLanguages() {
        assertTrue(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Verification code"))
        assertTrue(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Password"))
        assertTrue(AccessibilityAutomationPolicy.isSensitiveFieldLabel("CVV"))
        assertTrue(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Код подтверждения"))
        assertTrue(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Рамзи тасдиқ"))
        assertTrue(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Калиди API"))
        assertFalse(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Code editor"))
        assertFalse(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Search"))
    }
}
 {
        assertTrue(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Verification code"))
        assertTrue(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Password"))
        assertTrue(AccessibilityAutomationPolicy.isSensitiveFieldLabel("CVV"))
        assertTrue(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Код подтверждения"))
        assertTrue(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Рамзи тасдиқ"))
        assertTrue(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Калиди API"))
        assertFalse(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Code editor"))
        assertFalse(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Search"))
    }
