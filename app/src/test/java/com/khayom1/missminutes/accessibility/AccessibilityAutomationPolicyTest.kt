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
    fun modernAndroidPermissionDialogWordingIsTreatedAsHighRisk() {
        listOf(
            "Allow Miss Minutes to record audio?",
            "Allow Miss Minutes to take pictures and record video?",
            "Allow Miss Minutes to send you notifications?",
            "Allow Miss Minutes to access photos and videos?",
            "Allow Miss Minutes to find nearby devices?",
            "Allow Miss Minutes to manage all files?",
            "Разрешить приложению записывать аудио?",
            "Разрешить отправлять уведомления?",
            "Иҷозат додан барои сабти садо",
            "Иҷозат додан барои ирсоли огоҳиҳо"
        ).forEach { label ->
            assertTrue("Expected sensitive permission wording to be high risk: $label",
                AccessibilityAutomationPolicy.isHighRiskControlLabel(label))
        }
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
        assertTrue(AccessibilityAutomationPolicy.isSensitiveFieldLabel("PIN"))
        assertTrue(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Recovery code"))
        assertTrue(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Код подтверждения"))
        assertTrue(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Рамзи тасдиқ"))
        assertTrue(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Код восстановления"))
        assertTrue(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Калиди API"))
        assertFalse(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Code editor"))
        assertFalse(AccessibilityAutomationPolicy.isSensitiveFieldLabel("Search"))
    }
}
