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

    @Test fun mapsSemanticBehaviorToSupportedCharacterClips() {
        assertEquals("Wave_Greeting", MotionIntent.neutral().copy(gesture = "wave").animationName())
        assertEquals("Pointing", MotionIntent.neutral().copy(gesture = "point").animationName())
        assertEquals("Talk_Loop", MotionIntent.neutral().copy(gesture = "talk").animationName())
        assertEquals("Surprised", MotionIntent.neutral().copy(mood = "surprised").animationName())
        assertEquals("Angry_Expression", MotionIntent.neutral().copy(mood = "angry").animationName())
        assertEquals("Cheerful_Bounce", MotionIntent.neutral().copy(mood = "happy").animationName())
        assertEquals("Look_Around", MotionIntent.neutral().copy(mood = "curious").animationName())
        assertEquals("Idle_Breathe", MotionIntent.neutral().animationName())
    }

    @Test fun clampsAnimationSpeed() {
        assertEquals(0.85f, MotionIntent.neutral().copy(energy = -1f).animationSpeed())
        assertEquals(1.2f, MotionIntent.neutral().copy(energy = 2f).animationSpeed())
    }
