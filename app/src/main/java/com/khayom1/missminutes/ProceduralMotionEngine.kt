package com.khayom1.missminutes

import kotlin.math.abs
import kotlin.math.sin

/**
 * Frame-rate independent procedural pose sampled by the Filament render callback.
 * It produces semantic channels; ProceduralRigController maps them to whichever
 * named controls exist in the loaded GLB.
 */
internal data class ProceduralPose(
    val liftMeters: Float,
    val pitchDegrees: Float,
    val yawDegrees: Float,
    val rollDegrees: Float,
    val headTiltDegrees: Float,
    val eyeYawDegrees: Float,
    val eyePitchDegrees: Float,
    val browLiftDegrees: Float,
    val mouthOpenDegrees: Float,
    val smileDegrees: Float,
    val frownDegrees: Float,
    val blinkAmount: Float,
    val leftArmLiftDegrees: Float,
    val rightArmLiftDegrees: Float,
    val leftElbowBendDegrees: Float,
    val rightElbowBendDegrees: Float,
    val fingerCurlDegrees: Float
)

internal object ProceduralMotionEngine {
    fun sample(
        elapsedMillis: Long,
        motion: MotionIntent,
        forceBlink: Boolean = false
    ): ProceduralPose {
        val seconds = (elapsedMillis.coerceAtLeast(0L) % 60_000L) / 1_000f
        val energy = motion.energy.coerceIn(0f, 1f)
        val emphasis = motion.emphasis.coerceIn(0f, 1f)
        val speaking = motion.gesture == "talk"

        val breathing = sin(seconds * 2.0f * Math.PI.toFloat() * 0.32f) * 0.0025f
        val speechPulse = if (speaking) {
            abs(sin(seconds * 2.0f * Math.PI.toFloat() * 3.2f)) * (0.0015f + emphasis * 0.0035f)
        } else 0f
        val idleSway = sin(seconds * 2.0f * Math.PI.toFloat() * 0.18f)
        val energySway = 0.15f + energy * 0.55f
        val blinkPhase = seconds % 4.8f
        val periodicBlink = if (blinkPhase < 0.10f) {
            sin((blinkPhase / 0.10f) * Math.PI.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val browLift = when (motion.mood) {
            "surprised" -> 8f
            "happy", "playful", "curious" -> 4f
            "sad" -> -3f
            "angry" -> -5f
            else -> 0f
        }
        val mouthOpen = when {
            speaking -> 6f + emphasis * 10f
            motion.mood == "surprised" -> 13f
            else -> 0f
        }
        val smile = if (motion.mood == "happy" || motion.mood == "playful") 7f else 0f
        val frown = if (motion.mood == "sad" || motion.mood == "angry") 7f else 0f

        val leftArm: Float
        val rightArm: Float
        val leftElbow: Float
        val rightElbow: Float
        val fingerCurl: Float
        when (motion.gesture) {
            "wave" -> {
                leftArm = 36f; rightArm = 0f
                leftElbow = 32f; rightElbow = 0f
                fingerCurl = 8f
            }
            "point" -> {
                leftArm = 0f; rightArm = 28f
                leftElbow = 16f; rightElbow = 14f
                fingerCurl = 22f
            }
            "shrug" -> {
                leftArm = 22f; rightArm = 22f
                leftElbow = 24f; rightElbow = 24f
                fingerCurl = 10f
            }
            "open_hands" -> {
                leftArm = 12f; rightArm = 12f
                leftElbow = 12f; rightElbow = 12f
                fingerCurl = 0f
            }
            "hands_together" -> {
                leftArm = 16f; rightArm = 16f
                leftElbow = 34f; rightElbow = 34f
                fingerCurl = 14f
            }
            "present" -> {
                leftArm = 20f; rightArm = 20f
                leftElbow = 18f; rightElbow = 18f
                fingerCurl = 4f
            }
            else -> {
                leftArm = 0f; rightArm = 0f
                leftElbow = 0f; rightElbow = 0f
                fingerCurl = 6f
            }
        }

        val pitch = motion.bodyLean.coerceIn(-0.22f, 0.22f) * 18f +
            idleSway * energySway * 0.45f
        val yaw = idleSway * 0.35f
        val roll = idleSway * (0.25f + energy * 0.55f) +
            if (speaking) sin(seconds * 2.0f * Math.PI.toFloat() * 1.6f) * emphasis * 0.25f else 0f

        return ProceduralPose(
            liftMeters = (breathing + speechPulse).coerceIn(-0.006f, 0.006f),
            pitchDegrees = pitch.coerceIn(-5f, 5f),
            yawDegrees = yaw.coerceIn(-0.5f, 0.5f),
            rollDegrees = roll.coerceIn(-0.3f, 0.3f),
            headTiltDegrees = motion.headTilt.coerceIn(-0.55f, 0.55f) * 18f,
            eyeYawDegrees = motion.gaze.coerceIn(-0.7f, 0.7f) * 22f,
            eyePitchDegrees = motion.gazeVertical.coerceIn(-0.4f, 0.4f) * 18f,
            browLiftDegrees = browLift,
            mouthOpenDegrees = mouthOpen,
            smileDegrees = smile,
            frownDegrees = frown,
            blinkAmount = if (forceBlink) 1f else periodicBlink,
            leftArmLiftDegrees = leftArm,
            rightArmLiftDegrees = rightArm,
            leftElbowBendDegrees = leftElbow,
            rightElbowBendDegrees = rightElbow,
            fingerCurlDegrees = fingerCurl
        )
    }
}
