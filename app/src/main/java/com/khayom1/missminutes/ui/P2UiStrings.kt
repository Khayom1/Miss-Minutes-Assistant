package com.khayom1.missminutes.ui

internal data class P2UiStrings(
    val account: String,
    val email: String,
    val password: String,
    val signIn: String,
    val createAccount: String,
    val signOut: String,
    val signedInAs: String,
    val signingIn: String,
    val emailConfirmation: String,
    val authInvalid: String,
    val authUnavailable: String,
    val authRateLimited: String,
    val sessionExpired: String,
    val aiRequiresSignIn: String,
    val aiUnavailable: String,
    val aiRateLimited: String,
    val aiInvalidResponse: String,
    val conversationTooLong: String
)

internal fun p2UiStrings(language: String): P2UiStrings = when (language) {
    "Tajik" -> P2UiStrings(
        account = "Ҳисоб",
        email = "Почтаи электронӣ",
        password = "Рамз",
        signIn = "Ворид шудан",
        createAccount = "Сохтани ҳисоб",
        signOut = "Баромадан",
        signedInAs = "Шумо ҳамчун ворид шудед",
        signingIn = "Лутфан интизор шавед…",
        emailConfirmation = "Ҳисоб сохта шуд. Почтаи худро тасдиқ карда, баъд ворид шавед.",
        authInvalid = "Почта ё рамз нодуруст аст.",
        authUnavailable = "Хидмати воридшавӣ дастрас нест. Интернетро санҷед.",
        authRateLimited = "Кӯшишҳо зиёд шуданд. Каме интизор шуда, такрор кунед.",
        sessionExpired = "Сессия анҷом ёфт. Аз нав ворид шавед.",
        aiRequiresSignIn = "Барои истифодаи AI аввал аз танзимот ворид шавед.",
        aiUnavailable = "Хидмати AI ҳоло дастрас ё танзим нашудааст.",
        aiRateLimited = "Лимити дархостҳои AI ба охир расид. Каме интизор шавед.",
        aiInvalidResponse = "Ҷавоби хидмати AI нодуруст аст.",
        conversationTooLong = "Гуфтугӯ хеле дароз шуд. Гуфтугӯи нав оғоз кунед."
    )
    "Russian" -> P2UiStrings(
        account = "Аккаунт",
        email = "Электронная почта",
        password = "Пароль",
        signIn = "Войти",
        createAccount = "Создать аккаунт",
        signOut = "Выйти",
        signedInAs = "Вы вошли как",
        signingIn = "Подождите…",
        emailConfirmation = "Аккаунт создан. Подтвердите почту и войдите.",
        authInvalid = "Неверная почта или пароль.",
        authUnavailable = "Сервис авторизации недоступен. Проверьте соединение.",
        authRateLimited = "Слишком много попыток. Подождите и повторите.",
        sessionExpired = "Сессия истекла. Войдите снова.",
        aiRequiresSignIn = "Войдите в аккаунт в настройках, чтобы использовать ИИ.",
        aiUnavailable = "Сервис ИИ недоступен или ещё не настроен.",
        aiRateLimited = "Достигнут лимит запросов ИИ. Подождите.",
        aiInvalidResponse = "Сервис ИИ вернул некорректный ответ.",
        conversationTooLong = "Диалог слишком длинный. Начните новый диалог."
    )
    else -> P2UiStrings(
        account = "Account",
        email = "Email",
        password = "Password",
        signIn = "Sign in",
        createAccount = "Create account",
        signOut = "Sign out",
        signedInAs = "Signed in as",
        signingIn = "Please wait…",
        emailConfirmation = "Account created. Confirm your email, then sign in.",
        authInvalid = "Email or password is incorrect.",
        authUnavailable = "Authentication service is unavailable. Check your connection.",
        authRateLimited = "Too many attempts. Wait and try again.",
        sessionExpired = "Your session expired. Please sign in again.",
        aiRequiresSignIn = "Sign in under Settings before using AI.",
        aiUnavailable = "AI service is unavailable or not configured yet.",
        aiRateLimited = "AI request limit reached. Wait before trying again.",
        aiInvalidResponse = "AI service returned an invalid response.",
        conversationTooLong = "This conversation is too long. Start a new one."
    )
}
