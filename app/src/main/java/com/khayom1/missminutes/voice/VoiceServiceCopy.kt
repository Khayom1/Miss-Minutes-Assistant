package com.khayom1.missminutes.voice

internal data class VoiceServiceCopy(
    val channelName: String,
    val channelDescription: String,
    val android12Required: String,
    val recognizerUnavailable: String,
    val permissionsRequired: String,
    val listeningState: String,
    val listeningTitle: String,
    val listeningText: String,
    val stopAction: String,
    val wakeHeard: String,
    val stoppedWithError: String,
    val stopped: String
)

internal object VoiceServiceCopyProvider {
    fun forLanguage(language: String): VoiceServiceCopy = when (language) {
        "ru" -> VoiceServiceCopy(
            channelName = "Фраза активации Miss Minutes",
            channelDescription = "Показывается, пока пользователь включил офлайн-распознавание фразы.",
            android12Required = "Для офлайн-распознавания речи требуется Android 12 или новее.",
            recognizerUnavailable = "Офлайн-распознавание речи недоступно. Прослушивание не запущено.",
            permissionsRequired = "Необходимы разрешения на микрофон и уведомления.",
            listeningState = "Офлайн-прослушивание активно.",
            listeningTitle = "Miss Minutes слушает",
            listeningText = "Офлайн-фраза активации: «Miss Minutes»",
            stopAction = "Остановить прослушивание",
            wakeHeard = "Miss Minutes услышала вас. Нажмите, чтобы открыть помощника.",
            stoppedWithError = "Прослушивание остановлено. Проверьте офлайн-распознавание речи и разрешения.",
            stopped = "Офлайн-прослушивание фразы выключено."
        )
        "tg" -> VoiceServiceCopy(
            channelName = "Ибораи бедоркунии Miss Minutes",
            channelDescription = "Ҳангоми фаъол будани шинохти офлайнии ибора нишон дода мешавад.",
            android12Required = "Барои шинохти офлайнии овоз Android 12 ё навтар лозим аст.",
            recognizerUnavailable = "Шинохти офлайнии овоз дастрас нест. Гӯшкунӣ оғоз нашуд.",
            permissionsRequired = "Иҷозати микрофон ва огоҳиномаҳо зарур аст.",
            listeningState = "Гӯшкунии офлайн фаъол аст.",
            listeningTitle = "Miss Minutes гӯш мекунад",
            listeningText = "Ибораи бедоркунӣ: «Miss Minutes»",
            stopAction = "Қатъ кардани гӯшкунӣ",
            wakeHeard = "Miss Minutes шуморо шунид. Барои кушодани ёвар пахш кунед.",
            stoppedWithError = "Гӯшкунӣ қатъ шуд. Шинохти офлайн ва иҷозатҳоро санҷед.",
            stopped = "Гӯшкунии офлайн хомӯш аст."
        )
        else -> VoiceServiceCopy(
            channelName = "Miss Minutes wake phrase",
            channelDescription = "Visible while the user-enabled offline wake phrase listener is active.",
            android12Required = "On-device speech recognition requires Android 12 or later.",
            recognizerUnavailable = "On-device speech recognition is unavailable. Wake phrase listening was not started.",
            permissionsRequired = "Microphone and notification permissions are required.",
            listeningState = "Offline wake phrase listening is active.",
            listeningTitle = "Miss Minutes is listening",
            listeningText = "Offline wake phrase: “Miss Minutes”",
            stopAction = "Stop listening",
            wakeHeard = "Miss Minutes heard you. Tap to open the assistant.",
            stoppedWithError = "Wake phrase listening stopped. Check on-device speech recognition and permissions.",
            stopped = "Offline wake phrase listening is off."
        )
    }
}

internal object WakeWordServicePolicy {
    fun shouldStart(action: String?): Boolean =
        action == MissMinutesWakeWordService.ACTION_START

    fun shouldStop(action: String?): Boolean =
        action == MissMinutesWakeWordService.ACTION_STOP
}
