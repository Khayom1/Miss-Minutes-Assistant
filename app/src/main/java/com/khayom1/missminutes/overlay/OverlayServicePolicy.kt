package com.khayom1.missminutes.overlay

import android.app.Service

internal data class OverlayPosition(val x: Int, val y: Int)

internal data class OverlayStrings(
    val bubbleLabel: String,
    val bubbleDescription: String,
    val channelName: String,
    val channelDescription: String,
    val notificationTitle: String,
    val notificationText: String,
    val stopAction: String
)

internal object OverlayServicePolicy {
    const val RESTART_POLICY: Int = Service.START_NOT_STICKY

    fun shouldStart(action: String?): Boolean =
        action == MissMinutesOverlayService.ACTION_START

    fun shouldStop(action: String?): Boolean =
        action == MissMinutesOverlayService.ACTION_STOP

    fun clampPosition(
        x: Int,
        y: Int,
        screenWidthPixels: Int,
        screenHeightPixels: Int,
        bubbleWidthPixels: Int,
        bubbleHeightPixels: Int
    ): OverlayPosition {
        val maxX = (screenWidthPixels - bubbleWidthPixels).coerceAtLeast(0)
        val maxY = (screenHeightPixels - bubbleHeightPixels).coerceAtLeast(0)
        return OverlayPosition(
            x = x.coerceIn(0, maxX),
            y = y.coerceIn(0, maxY)
        )
    }

    fun strings(language: String?): OverlayStrings = when (language?.lowercase()) {
        "ru" -> OverlayStrings(
            bubbleLabel = "◷  Miss Minutes",
            bubbleDescription = "Открыть помощник Miss Minutes",
            channelName = "Плавающий помощник",
            channelDescription = "Управление плавающей кнопкой Miss Minutes.",
            notificationTitle = "Плавающий помощник Miss Minutes",
            notificationText = "Кнопка активна. Нажмите «Скрыть», чтобы убрать её.",
            stopAction = "Скрыть"
        )
        "tg" -> OverlayStrings(
            bubbleLabel = "◷  Miss Minutes",
            bubbleDescription = "Кушодани ёвари Miss Minutes",
            channelName = "Ёвари шинокунанда",
            channelDescription = "Идоракунии тугмачаи шинокунандаи Miss Minutes.",
            notificationTitle = "Ёвари шинокунандаи Miss Minutes",
            notificationText = "Тугмача фаъол аст. Барои пинҳон кардан «Пинҳон» -ро пахш кунед.",
            stopAction = "Пинҳон"
        )
        else -> OverlayStrings(
            bubbleLabel = "◷  Miss Minutes",
            bubbleDescription = "Open Miss Minutes assistant",
            channelName = "Floating assistant",
            channelDescription = "Controls the user-started Miss Minutes floating shortcut.",
            notificationTitle = "Miss Minutes floating assistant",
            notificationText = "The floating shortcut is active. Tap Hide to remove it.",
            stopAction = "Hide"
        )
    }
}
