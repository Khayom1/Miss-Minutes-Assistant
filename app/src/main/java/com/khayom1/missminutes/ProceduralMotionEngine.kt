package com.khayom1.missminutes

import kotlin.math.abs
import kotlin.math.sin

/**
 * Frame-rate independent procedural root motion for the current transform-only GLB.
 *
 * This deliberately does not pretend to drive individual bones or facial controls.
 * Those require the replacement weighted rig and a separate rig adapter.
 */
internal data class ProceduralPose(
    val liftMeters: Float,
    val pitchDegrees: Float,
    val yawDegrees: Float,
    val rollDegrees: Float
)

internal object ProceduralMotionEngine {
    fun sample(elapsedMillis: Long, motion: MotionIntent): ProceduralPose {
        val seconds = elapsedMillis.coerceAtLeast(0L) / 1_000f
        val energy = motion.energy.coerceIn(0f, 1f)
        val emphasis = motion.emphasis.coerceIn(0f, 1f)
        val speaking = motion.gesture == "talk"

        val breathing = sin(seconds * 2.0f * Math.PI.toFloat() * 0.32f) * 0.0025f
        val speechPulse = if (speaking) {
            abs(sin(seconds * 2.0f * Math.PI.toFloat() * 3.2f)) * (0.0015f + emphasis * 0.0035f)
        } else {
            0f
        }
        val idleSway = sin(seconds * 2.0f * Math.PI.toFloat() * 0.18f)
        val energySway = (0.15f + energy * 0.55f)

        return ProceduralPose(
            liftMeters = breathing + speechPulse,
            pitchDegrees = (motion.bodyLean.coerceIn(-0.22f, 0.22f) * 18f) +
                idleSway * energySway * 0.45f,
            yawDegrees = (motion.gaze.coerceIn(-0.7f, 0.7f) * 12f) +
                sin(seconds * 2.0f * Math.PI.toFloat() * 0.11f) * 0.35f,
            rollDegrees = idleSway * (0.25f + energy * 0.55f) +
                (if (speaking) sin(seconds * 2.0f * Math.PI.toFloat() * 1.6f) * emphasis * 0.25f else 0f)
        )
    }
}
