package com.khayom1.missminutes

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionIntentTest {
    @Test fun parsesAllowedAnimationAndReply() {
        val result = MotionIntent.parse("""{"reply":"Hello!","animation":"Wave_Greeting","emotion":"happy"}""")
        assertEquals("Hello!", result.reply)
        assertEquals("Wave_Greeting", result.animation)
        assertEquals("happy", result.emotion)
    }

    @Test fun rejectsAnimationNotInBundledContract() {
        val result = MotionIntent.parse("""{"reply":"Hello","animation":"Fly_Away"}""")
        assertEquals("Idle_Breathe", result.animation)
    }

    @Test fun fallsBackWhenModelDoesNotReturnJson() {
        val result = MotionIntent.parse("Hello there")
        assertEquals("Hello there", result.reply)
        assertEquals("Idle_Breathe", result.animation)
    }

    @Test fun infersGreetingGesture() {
        assertEquals("Wave_Greeting", MotionIntent.infer("Hello, welcome back!"))
    }
}
