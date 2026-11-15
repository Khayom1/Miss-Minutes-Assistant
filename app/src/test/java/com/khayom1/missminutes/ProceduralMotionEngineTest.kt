package com.khayom1.missminutes

import org.junit.Assert.assertTrue
import org.junit.Test

class ProceduralMotionEngineTest {
    @Test
    fun neutralPoseStaysWithinSmallRootMotionBounds() {
        val pose = ProceduralMotionEngine.sample(5_000L, MotionIntent.neutral())
        assertTrue(kotlin.math.abs(pose.liftMeters) <= 0.003f)
        assertTrue(kotlin.math.abs(pose.pitchDegrees) <= 0.5f)
        assertTrue(kotlin.math.abs(pose.yawDegrees) <= 0.5f)
        assertTrue(kotlin.math.abs(pose.rollDegrees) <= 0.3f)
    }

    @Test
    fun bodyLeanAndTwoAxisGazeAffectPoseWithinClampedBounds() {
        val motion = MotionIntent.neutral().copy(
            bodyLean = 0.22f,
            gaze = 0.65f,
            gazeVertical = 0.35f,
            headTilt = 0.55f,
            energy = 1f
        )
        val pose = ProceduralMotionEngine.sample(1_250L, motion)
        assertTrue(pose.pitchDegrees in 3.0f..5.0f)
        assertTrue(pose.eyeYawDegrees in 14.0f..15.0f)
        assertTrue(pose.eyePitchDegrees in 6.0f..7.0f)
        assertTrue(pose.headTiltDegrees in 9.0f..10.0f)
    }

    @Test
    fun speakingAddsBoundedSpeechPulseAndMouthOpening() {
        val speaking = MotionIntent.neutral().copy(gesture = "talk", emphasis = 1f)
        val pose = ProceduralMotionEngine.sample(125L, speaking)
        assertTrue(pose.liftMeters in 0f..0.0051f)
        assertTrue(kotlin.math.abs(pose.rollDegrees) <= 0.3f)
        assertTrue(pose.mouthOpenDegrees == 16f)
    }

    @Test
    fun blinkIsTimeDrivenAndCanBeForcedByAnAutonomousAction() {
        val neutral = MotionIntent.neutral()
        assertTrue(ProceduralMotionEngine.sample(0L, neutral).blinkAmount == 0f)
        assertTrue(ProceduralMotionEngine.sample(50L, neutral).blinkAmount > 0.99f)
        assertTrue(ProceduralMotionEngine.sample(500L, neutral, forceBlink = true).blinkAmount == 1f)
    }

    @Test
    fun gesturesProduceProceduralArmAndHandPoses() {
        val wave = ProceduralMotionEngine.sample(1_000L, MotionIntent.neutral().copy(gesture = "wave"))
        assertTrue(wave.leftArmLiftDegrees > 30f)
        assertTrue(wave.leftElbowBendDegrees > 25f)

        val point = ProceduralMotionEngine.sample(1_000L, MotionIntent.neutral().copy(gesture = "point"))
        assertTrue(point.rightArmLiftDegrees > 20f)
        assertTrue(point.fingerCurlDegrees > 0f)
    }

    @Test
    fun invalidElapsedTimeAndIntentValuesAreClamped() {
        val motion = MotionIntent.neutral().copy(bodyLean = 10f, gaze = -10f, energy = 10f, emphasis = -2f)
        val pose = ProceduralMotionEngine.sample(-10L, motion)
        assertTrue(pose.pitchDegrees in -5f..5f)
        assertTrue(pose.eyeYawDegrees in -15.5f..-15f)
        assertTrue(pose.liftMeters == 0f)
    }
}
