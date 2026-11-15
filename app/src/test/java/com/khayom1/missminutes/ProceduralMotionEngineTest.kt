package com.khayom1.missminutes

import org.junit.Assert.assertTrue
import org.junit.Test

class ProceduralMotionEngineTest {
    @Test
    fun neutralPoseStaysWithinSmallRootMotionBounds() {
        val pose = ProceduralMotionEngine.sample(5_000L, MotionIntent.neutral())
        assertTrue(kotlin.math.abs(pose.liftMeters) <= 0.003f)
        assertTrue(kotlin.math.abs(pose.pitchDegrees) <= 0.5f)
        assertTrue(kotlin.math.abs(pose.yawDegrees) <= 0.4f)
        assertTrue(kotlin.math.abs(pose.rollDegrees) <= 0.9f)
    }

    @Test
    fun bodyLeanAndGazeAffectRootPoseWithinClampedBounds() {
        val motion = MotionIntent.neutral().copy(bodyLean = 0.22f, gaze = 0.65f, energy = 1f)
        val pose = ProceduralMotionEngine.sample(1_250L, motion)
        assertTrue(pose.pitchDegrees in 3.0f..5.0f)
        assertTrue(pose.yawDegrees in 7.0f..8.5f)
    }

    @Test
    fun speakingAddsBoundedSpeechPulse() {
        val speaking = MotionIntent.neutral().copy(gesture = "talk", emphasis = 1f)
        val pose = ProceduralMotionEngine.sample(125L, speaking)
        assertTrue(pose.liftMeters in 0f..0.0051f)
        assertTrue(kotlin.math.abs(pose.rollDegrees) <= 0.3f)
    }

    @Test
    fun invalidElapsedTimeAndIntentValuesAreClamped() {
        val motion = MotionIntent.neutral().copy(bodyLean = 10f, gaze = -10f, energy = 10f, emphasis = -2f)
        val pose = ProceduralMotionEngine.sample(-10L, motion)
        assertTrue(pose.pitchDegrees in -5f..5f)
        assertTrue(pose.yawDegrees in -9f..-7f)
        assertTrue(pose.liftMeters == 0f)
    }
}
