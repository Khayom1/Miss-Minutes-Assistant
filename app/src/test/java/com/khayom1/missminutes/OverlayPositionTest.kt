package com.khayom1.missminutes

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPositionTest {
    @Test fun keepsOverlayInsideAllFourEdges() {
        assertEquals(OverlayPosition(0, 0), clampOverlayPosition(-30, -20, 400, 800, 100, 120))
        assertEquals(OverlayPosition(300, 680), clampOverlayPosition(900, 900, 400, 800, 100, 120))
    }

    @Test fun preservesPositionWhenAlreadyInsideBounds() {
        assertEquals(OverlayPosition(120, 240), clampOverlayPosition(120, 240, 400, 800, 100, 120))
    }

    @Test fun handlesOverlayLargerThanDisplay() {
        assertEquals(OverlayPosition(0, 0), clampOverlayPosition(50, 60, 100, 100, 140, 160))
    }

    @Test fun handlesZeroSizedDisplayAndOverlay() {
        assertEquals(OverlayPosition(0, 0), clampOverlayPosition(50, 60, 0, 0, 0, 0))
    }
}
