package com.khayom1.missminutes.communication.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSmsClassifierTest {
    @Test
    fun detectsVerificationCodesWithoutReturningTheCode() {
        val result = LocalSmsClassifier().classify(
            senderAddress = "BANK",
            body = "Your verification code is 481205. Do not share it."
        )

        assertEquals(SmsCategory.AUTHENTICATION_CODE, result.category)
        assertEquals(SmsPriority.HIGH, result.priority)
        assertTrue(result.needsImmediateAttention)
        assertFalse(result.toString().contains("481205"))
    }

    @Test
    fun recognizesTajikUrgency() {
        val result = LocalSmsClassifier().classify(
            senderAddress = "+992900000000",
            body = "Ин паём таъҷилӣ аст, фавран занг занед."
        )

        assertEquals(SmsCategory.EMERGENCY, result.category)
        assertEquals(SmsPriority.CRITICAL, result.priority)
    }

    @Test
    fun recognizesRussianBankTransactions() {
        val result = LocalSmsClassifier().classify(
            senderAddress = "BANK",
            body = "По карте списано 250 сомони. Остаток баланса доступен в приложении."
        )

        assertEquals(SmsCategory.BANK_TRANSACTION, result.category)
        assertEquals(SmsPriority.HIGH, result.priority)
    }

    @Test
    fun recognizesAccountSecurityWarnings() {
        val result = LocalSmsClassifier().classify(
            senderAddress = "Service",
            body = "Подозрительный вход в аккаунт. Если это не вы, смените пароль."
        )

        assertEquals(SmsCategory.SECURITY_ALERT, result.category)
        assertEquals(SmsPriority.HIGH, result.priority)
    }

    @Test
    fun recognizesPromotionsAndScamLanguage() {
        val classifier = LocalSmsClassifier()
        val promotion = classifier.classify("SHOP", "Большая скидка 50% только сегодня.")
        val scam = classifier.classify("UNKNOWN", "Поздравляем, вы выиграли приз! Claim your prize.")

        assertEquals(SmsCategory.PROMOTIONAL, promotion.category)
        assertEquals(SmsPriority.LOW, promotion.priority)
        assertEquals(SmsCategory.SUSPECTED_SPAM, scam.category)
        assertEquals(SmsPriority.JUNK, scam.priority)
    }

    @Test
    fun trustedContactImportanceComesFromUserPolicy() {
        val classifier = LocalSmsClassifier(isUserTrustedSender = { it == "+992900000001" })
        val trusted = classifier.classify("+992900000001", "Салом, чӣ хабар?")
        val unknown = classifier.classify("+992900000002", "Салом, чӣ хабар?")

        assertEquals(SmsCategory.PERSONAL, trusted.category)
        assertEquals(SmsPriority.HIGH, trusted.priority)
        assertEquals(SmsCategory.GENERAL, unknown.category)
        assertEquals(SmsPriority.NORMAL, unknown.priority)
    }

    @Test
    fun ambiguousMessageRemainsVisibleAsNormal() {
        val result = LocalSmsClassifier().classify("UNKNOWN", "Паёми оддӣ бе калимаи хавфнок.")

        assertEquals(SmsCategory.GENERAL, result.category)
        assertEquals(SmsPriority.NORMAL, result.priority)
        assertEquals(55, result.confidence)
    }
}
