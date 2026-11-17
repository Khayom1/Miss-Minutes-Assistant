package com.khayom1.missminutes.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayServicePolicyTest {
    @Test
    fun onlyExplicitStartActionCanStartTheOverlay() {
        assertTrue(OverlayServicePolicy.shouldStart(MissMinutesOverlayService.ACTION_START))
        assertFalse(OverlayServicePolicy.shouldStart(null))
        assertFalse(OverlayServicePolicy.shouldStart(MissMinutesOverlayService.ACTION_STOP))
        assertFalse(OverlayServicePolicy.shouldStart("unknown.action"))
    }

    @Test
    fun stopActionIsDistinctFromStartAction() {
        assertTrue(OverlayServicePolicy.shouldStop(MissMinutesOverlayService.ACTION_STOP))
        assertFalse(OverlayServicePolicy.shouldStop(MissMinutesOverlayService.ACTION_START))
        assertFalse(OverlayServicePolicy.shouldStop(null))
    }

    @Test
    fun dragPositionIsClampedToVisibleDisplayBounds() {
        assertEquals(
            OverlayPosition(0, 0),
            OverlayServicePolicy.clampPosition(-30, -10, 300, 600, 80, 100)
        )
        assertEquals(
            OverlayPosition(220, 500),
            OverlayServicePolicy.clampPosition(500, 900, 300, 600, 80, 100)
        )
        assertEquals(
            OverlayPosition(0, 0),
            OverlayServicePolicy.clampPosition(10, 20, 60, 60, 90, 90)
        )
    }

    @Test
    fun overlayLabelsAreLocalizedForEverySupportedLanguage() {
        val english = OverlayServicePolicy.strings("en")
        val russian = OverlayServicePolicy.strings("ru")
        val tajik = OverlayServicePolicy.strings("tg")

        assertEquals("Hide", english.stopAction)
        assertEquals("Скрыть", russian.stopAction)
        assertEquals("Пинҳон", tajik.stopAction)
        assertTrue(english.notificationText.contains("Tap Hide"))
        assertTrue(russian.notificationText.contains("Нажмите"))
        assertTrue(tajik.notificationText.contains("пахш"))
    }

    @Test
    fun unknownLanguageFallsBackToEnglish() {
        assertEquals(
            OverlayServicePolicy.strings("en"),
            OverlayServicePolicy.strings("xx")
        )
        assertEquals(
            OverlayServicePolicy.strings("en"),
            OverlayServicePolicy.strings(null)
        )
    }

    @Test
    fun serviceUsesNonStickyRestartPolicy() {
        assertEquals(android.app.Service.START_NOT_STICKY, OverlayServicePolicy.RESTART_POLICY)
    }
}
