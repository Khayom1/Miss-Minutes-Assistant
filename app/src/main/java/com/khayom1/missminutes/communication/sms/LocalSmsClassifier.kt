package com.khayom1.missminutes.communication.sms

/**
 * Privacy-safe classification result. It intentionally contains no message body,
 * OTP value, sender display name, or credential-bearing fragment.
 */
data class SmsClassification(
    val category: SmsCategory,
    val priority: SmsPriority,
    val confidence: Int,
    val reasons: Set<SmsReason>
) {
    init {
        require(confidence in 0..100)
    }

    val needsImmediateAttention: Boolean
        get() = priority == SmsPriority.CRITICAL || priority == SmsPriority.HIGH
}

enum class SmsCategory {
    AUTHENTICATION_CODE,
    BANK_TRANSACTION,
    SECURITY_ALERT,
    EMERGENCY,
    PERSONAL,
    PROMOTIONAL,
    SUSPECTED_SPAM,
    GENERAL
}

enum class SmsPriority {
    CRITICAL,
    HIGH,
    NORMAL,
    LOW,
    JUNK
}

enum class SmsReason {
    VERIFICATION_LANGUAGE_AND_CODE,
    FINANCIAL_ACTIVITY,
    ACCOUNT_SECURITY_SIGNAL,
    URGENT_LANGUAGE,
    USER_TRUSTED_SENDER,
    MARKETING_LANGUAGE,
    SCAM_LANGUAGE,
    INSUFFICIENT_SIGNAL
}

/**
 * Deterministic, on-device first-pass classifier.
 *
 * This class never performs network access, writes logs, stores the SMS body,
 * opens links, or triggers a reply. Contact trust is provided by a local,
 * user-controlled resolver.
 */
class LocalSmsClassifier(
    private val isUserTrustedSender: (String) -> Boolean = { false }
) {
    fun classify(senderAddress: String, body: String): SmsClassification {
        val text = normalize(body)
        val trustedSender = senderAddress.isNotBlank() && isUserTrustedSender(senderAddress)

        val hasVerificationSignal = containsAny(
            text,
            "otp", "verification", "verify", "verification code",
            "security code", "one time", "код", "подтвержден",
            "подтверждение", "рамз", "тасдиқ", "тасдик", "санҷиш"
        )
        val hasNumericCode = NUMERIC_CODE.containsMatchIn(text)
        if (hasVerificationSignal && hasNumericCode) {
            return result(
                SmsCategory.AUTHENTICATION_CODE,
                SmsPriority.HIGH,
                98,
                SmsReason.VERIFICATION_LANGUAGE_AND_CODE
            )
        }

        val hasBankContext = containsAny(
            text,
            "bank", "банк", "карта", "card", "ҳисоб", "хисоб",
            "счёт", "счет", "balance", "баланс", "сомон", "tjs"
        )
        val hasFinancialActivity = containsAny(
            text,
            "transaction", "транзакц", "пардохт", "платёж", "платеж",
            "payment", "перевод", "списан", "сняти", "зачислен",
            "поступлен", "withdraw", "deposit", "purchase", "харид",
            "харҷ", "расход", "дебет", "кредит"
        )
        if (hasBankContext && hasFinancialActivity) {
            val outgoing = containsAny(
                text, "списан", "сняти", "withdraw", "purchase", "харид",
                "харҷ", "расход", "дебет"
            )
            return result(
                SmsCategory.BANK_TRANSACTION,
                if (outgoing) SmsPriority.HIGH else SmsPriority.HIGH,
                94,
                SmsReason.FINANCIAL_ACTIVITY
            )
        }

        val hasSecurityContext = containsAny(
            text,
            "security", "безопасност", "амният", "аккаунт", "account",
            "профил", "profile", "login", "вход", "ворид", "парол",
            "password", "подозр", "suspicious", "unauthorized",
            "блокирован", "заблокирован", "blocked"
        )
        if (hasSecurityContext) {
            return result(
                SmsCategory.SECURITY_ALERT,
                SmsPriority.HIGH,
                90,
                SmsReason.ACCOUNT_SECURITY_SIGNAL
            )
        }

        if (containsAny(
                text,
                "urgent", "emergency", "срочно", "экстренно",
                "таъҷилӣ", "таъчили", "фавран", "хатар", "опасност"
            )
        ) {
            return result(
                SmsCategory.EMERGENCY,
                SmsPriority.CRITICAL,
                92,
                SmsReason.URGENT_LANGUAGE
            )
        }

        if (containsAny(
                text,
                "вы выиграли", "поздравляем, вы выиграли", "winner",
                "you won", "claim your prize", "ғолиб шудед",
                "бурдед", "мукофот гирифтед", "получите приз",
                "выигрыш", "переведите деньги для получения"
            )
        ) {
            return result(
                SmsCategory.SUSPECTED_SPAM,
                SmsPriority.JUNK,
                88,
                SmsReason.SCAM_LANGUAGE
            )
        }

        if (containsAny(
                text,
                "скидка", "акция", "реклама", "рассылка", "promo",
                "promotion", "discount", "sale", "subscribe",
                "объявление", "подарок от магазина"
            )
        ) {
            return result(
                SmsCategory.PROMOTIONAL,
                SmsPriority.LOW,
                82,
                SmsReason.MARKETING_LANGUAGE
            )
        }

        if (trustedSender) {
            return result(
                SmsCategory.PERSONAL,
                SmsPriority.HIGH,
                85,
                SmsReason.USER_TRUSTED_SENDER
            )
        }

        return result(
            SmsCategory.GENERAL,
            SmsPriority.NORMAL,
            55,
            SmsReason.INSUFFICIENT_SIGNAL
        )
    }

    private fun result(
        category: SmsCategory,
        priority: SmsPriority,
        confidence: Int,
        reason: SmsReason
    ) = SmsClassification(category, priority, confidence, setOf(reason))

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(WHITESPACE, " ")
            .trim()

    private fun containsAny(text: String, vararg signals: String): Boolean =
        signals.any { signal -> text.contains(signal) }

    private companion object {
        val NUMERIC_CODE = Regex("""(?<!\d)\d{4,8}(?!\d)""")
        val WHITESPACE = Regex("""\s+""")
    }
}
