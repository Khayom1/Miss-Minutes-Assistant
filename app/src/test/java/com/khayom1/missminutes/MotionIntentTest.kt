package com.khayom1.missminutes

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionIntentTest {
    @Test fun parsesSemanticMotionIntent() {
        val result = MotionIntent.parse("""{"reply":"Hello","mood":"happy","gaze":"left","head_tilt":0.2,"body_lean":0.1,"gesture":"wave","energy":0.8,"emphasis":0.6}""")
        assertEquals("Hello", result.reply)
        assertEquals("happy", result.mood)
        assertEquals(-0.65f, result.gaze)
        assertEquals("wave", result.gesture)
        assertEquals(0.2f, result.headTilt)
    }

    @Test fun clampsNumericParameters() {
        val result = MotionIntent.parse("""{"reply":"hi","head_tilt":9,"body_lean":-9,"energy":3,"emphasis":-1}""")
        assertEquals(0.55f, result.headTilt)
        assertEquals(-0.22f, result.bodyLean)
        assertEquals(1f, result.energy)
        assertEquals(0f, result.emphasis)
    }

    @Test fun rejectsUnknownSemanticEnums() {
        val result = MotionIntent.parse("""{"reply":"hi","mood":"teleport","gesture":"fly"}""")
        assertEquals("neutral", result.mood)
        assertEquals("none", result.gesture)
    }
}
