package com.khayom1.missminutes

import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterBehaviorModeTest {
    @Test
    fun directInteractionTakesPriorityOverPassiveAndSpeechStates() {
        assertEquals(
            BehaviorMode.INTERACTING,
            resolveBehaviorMode(isListening = true, isThinking = true, isSpeaking = true, isInteracting = true)
        )
    }

    @Test
    fun speechTakesPriorityOverThinkingAndListening() {
        assertEquals(
            BehaviorMode.SPEAKING,
            resolveBehaviorMode(isListening = true, isThinking = true, isSpeaking = true)
        )
    }

    @Test
    fun thinkingTakesPriorityOverListening() {
        assertEquals(
            BehaviorMode.THINKING,
            resolveBehaviorMode(isListening = true, isThinking = true, isSpeaking = false)
        )
    }

    @Test
    fun listeningAndIdleResolveExplicitly() {
        assertEquals(
            BehaviorMode.LISTENING,
            resolveBehaviorMode(isListening = true, isThinking = false, isSpeaking = false)
        )
        assertEquals(
            BehaviorMode.IDLE,
            resolveBehaviorMode(isListening = false, isThinking = false, isSpeaking = false)
        )
    }
}
