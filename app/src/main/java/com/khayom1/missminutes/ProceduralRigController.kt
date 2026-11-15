package com.khayom1.missminutes

import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node

/**
 * Drives the named transform controls after SceneView has applied its authored clip for the frame.
 * It supports the shipped transform rig and the available joints in the supplied advanced skeleton.
 * Missing controls are ignored; the release validator remains responsible for rejecting an
 * incomplete production rig.
 */
internal class ProceduralRigController(
    private val modelNode: ModelNode
) {
    private val namedNodes: Map<String, Node> = modelNode.nodes.mapNotNull { node ->
        modelNode.model.getName(node.entity)?.lowercase()?.let { it to node }
    }.toMap()

    private val restRotations = modelNode.nodes.associate { it.entity to it.rotation }
    private val restScales = modelNode.nodes.associate { it.entity to it.scale }
    private val rootPosition = modelNode.position
    private val rootRotation = modelNode.rotation

    fun apply(elapsedMillis: Long, sourceMotion: MotionIntent, animationName: String) {
        val motion = when (animationName) {
            "Nervous_Glance" -> sourceMotion.copy(
                gaze = if ((elapsedMillis / 360L) % 2L == 0L) -0.5f else 0.5f,
                gazeVertical = 0.05f
            )
            "Peek_Around" -> sourceMotion.copy(
                bodyLean = if ((elapsedMillis / 900L) % 2L == 0L) -0.12f else 0.12f,
                headTilt = 0.22f
            )
            else -> sourceMotion
        }
        val pose = ProceduralMotionEngine.sample(
            elapsedMillis = elapsedMillis,
            motion = motion,
            forceBlink = animationName == "Blink"
        )

        modelNode.position = Position(
            x = rootPosition.x,
            y = rootPosition.y + pose.liftMeters,
            z = rootPosition.z
        )
        modelNode.rotation = Rotation(
            x = rootRotation.x + pose.pitchDegrees,
            y = rootRotation.y + pose.yawDegrees,
            z = rootRotation.z + pose.rollDegrees
        )

        // Eyes and pupils: the advanced rig exposes weighted pupil joints and separate eye groups.
        setRotation(listOf("eye_l", "eye_r"), z = pose.headTiltDegrees * 0.35f)
        setRotation(
            listOf("pupil_l", "pupil_r", "eye_l_pupil_ctrl", "eye_r_pupil_ctrl"),
            x = pose.eyePitchDegrees,
            y = pose.eyeYawDegrees
        )
        setRotation(
            listOf("eye_l_ctrl", "eye_r_ctrl"),
            x = pose.eyePitchDegrees,
            y = pose.eyeYawDegrees,
            z = pose.headTiltDegrees
        )
        val eyeScaleY = (1f - pose.blinkAmount * 0.92f).coerceIn(0.08f, 1f)
        setScale(listOf("eye_l", "eye_r", "eye_l_ctrl", "eye_r_ctrl"), y = eyeScaleY)

        // Facial controls in the shipped model and the upper/lower lip joints in the advanced rig.
        setRotation(listOf("brow_l_ctrl", "brow_r_ctrl"), x = pose.browLiftDegrees)
        setRotation(listOf("mouth_talk_ctrl"), x = pose.mouthOpenDegrees)
        setRotation(listOf("mouth_smile_ctrl"), z = pose.smileDegrees)
        setRotation(listOf("mouth_frown_ctrl"), z = -pose.frownDegrees)
        setRotation(listOf("mouth_surprise_ctrl"), x = pose.mouthOpenDegrees)
        setRotation(listOf("top_lip_m"), y = -pose.mouthOpenDegrees * 0.35f)
        setRotation(listOf("bottom_lip_m"), y = pose.mouthOpenDegrees * 0.55f)
        setRotation(listOf("tongue_base"), x = pose.mouthOpenDegrees * 0.12f)
        setRotation(listOf("tongue_tip"), x = pose.mouthOpenDegrees * 0.08f)

        // Five-digit hands are driven where the corresponding joint exists. The source rig
        // currently has no ring-finger joints; those controls intentionally remain absent.
        setRotation(listOf("arm_l"), z = -pose.leftArmLiftDegrees)
        setRotation(listOf("arm_r"), z = pose.rightArmLiftDegrees)
        setRotation(listOf("forearm_l"), z = pose.leftElbowBendDegrees)
        setRotation(listOf("forearm_r"), z = -pose.rightElbowBendDegrees)
        val waveWrist = if (motion.gesture == "wave") {
            kotlin.math.sin(elapsedMillis / 140f) * 12f
        } else 0f
        setRotation(listOf("hand_l"), z = waveWrist)
        setRotation(listOf("hand_r"), z = 0f)

        for (side in listOf("l", "r")) {
            for (finger in listOf("thumb", "index", "middle", "pinky")) {
                val curl = if (motion.gesture == "point" && side == "r" && finger == "index") {
                    0f
                } else pose.fingerCurlDegrees
                setRotation(listOf("finger_${finger}_${side}"), z = curl)
                setRotation(listOf("finger_tip_${finger}_${side}"), z = curl * 0.55f)
            }
        }

        // Filament needs fresh bone matrices after direct TransformManager-backed node updates.
        if (modelNode.skinCount > 0) modelNode.animator.updateBoneMatrices()
    }

    private fun setRotation(
        names: List<String>,
        x: Float = 0f,
        y: Float = 0f,
        z: Float = 0f
    ) {
        names.forEach { name ->
            val node = namedNodes[name] ?: return@forEach
            val base = restRotations[node.entity] ?: node.rotation
            node.rotation = Rotation(base.x + x, base.y + y, base.z + z)
        }
    }

    private fun setScale(
        names: List<String>,
        x: Float = 1f,
        y: Float = 1f,
        z: Float = 1f
    ) {
        names.forEach { name ->
            val node = namedNodes[name] ?: return@forEach
            val base = restScales[node.entity] ?: node.scale
            node.scale = Scale(base.x * x, base.y * y, base.z * z)
        }
    }
}
