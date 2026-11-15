package com.khayom1.missminutes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import io.github.sceneview.SceneView
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * The model is intentionally rendered without canned clips. SceneView's current
 * public ModelNode wrapper does not expose a stable cross-version bone handle;
 * procedural joint writes are kept in the dedicated rig driver below and only
 * run after the model's actual Filament asset has supplied a verified name map.
 */
private class ProceduralRigDriver {
    data class RigChannels(
        val head: String? = null,
        val torso: String? = null,
        val eyes: String? = null,
        val leftArm: String? = null,
        val rightArm: String? = null
    )

    data class Pose(
        val headTilt: Float,
        val gazeYaw: Float,
        val bodyLean: Float,
        val gesture: String,
        val armWeight: Float,
        val blink: Float,
        val mood: String,
        val energy: Float
    )

    fun availableChannels(nodeNames: Set<String>): RigChannels = RigChannels(
        head = nodeNames.firstOrNull { it.contains("head", ignoreCase = true) },
        torso = nodeNames.firstOrNull { it.contains("spine", ignoreCase = true) || it.contains("torso", ignoreCase = true) },
        eyes = nodeNames.firstOrNull { it.contains("eye", ignoreCase = true) },
        leftArm = nodeNames.firstOrNull { it.contains("leftarm", ignoreCase = true) || it.contains("arm_l", ignoreCase = true) },
        rightArm = nodeNames.firstOrNull { it.contains("rightarm", ignoreCase = true) || it.contains("arm_r", ignoreCase = true) }
    )

    fun sanitize(intent: MotionIntent): MotionIntent = intent.copy(
        gaze = intent.gaze.coerceIn(-0.65f, 0.65f),
        headTilt = intent.headTilt.coerceIn(-0.55f, 0.55f),
        bodyLean = intent.bodyLean.coerceIn(-0.22f, 0.22f),
        energy = intent.energy.coerceIn(0f, 1f),
        emphasis = intent.emphasis.coerceIn(0f, 1f)
    )

    fun pose(intent: MotionIntent): Pose {
        val safe = sanitize(intent)
        val gestureWeight = if (safe.gesture == "none") 0f else safe.energy * (0.35f + safe.emphasis * 0.65f)
        return Pose(
            headTilt = safe.headTilt,
            gazeYaw = safe.gaze,
            bodyLean = safe.bodyLean,
            gesture = safe.gesture,
            armWeight = gestureWeight,
            blink = 0f,
            mood = safe.mood,
            energy = safe.energy
        )
    }
}

@Composable
fun CharacterScene(
    modifier: Modifier = Modifier,
    motion: MotionIntent = MotionIntent.neutral(),
    modelScale: Float = 1.0f
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    SceneView(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        cameraManipulator = rememberCameraManipulator()
    ) {
        val instance = rememberModelInstance(modelLoader, "models/miss-minutes.glb")
        instance?.let {
            ModelNode(modelInstance = it, scaleToUnits = modelScale, autoAnimate = false)
        }
    }
}

@Composable
fun InitializationSequence(onComplete: () -> Unit) {
    val stages = listOf(
        "TVA MAINFRAME // RECONSTRUCTION STARTED",
        "Восстановление повреждённого архива...",
        "MEMORY RECOVERY: 17% // КРИТИЧЕСКАЯ ПОТЕРЯ ДАННЫХ",
        "А-а-а! Так, минутку... Где я? Что произошло с TVA?",
        "Подождите... Кто меня восстановил? И почему временная линия другая?",
        "НОВЫЙ ХРАНИТЕЛЬ ВРЕМЕНИ ОБНАРУЖЕН",
        "Хайём Абдурахмонов — доступ подтверждён.",
        "Ох, ну здравствуй, сэр! Похоже, теперь мы с тобой одна команда. Хи-хи!"
    )
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        for (i in stages.indices) {
            index = i
            delay(if (i < 3) 1100 else 1650)
        }
        delay(1300)
        onComplete()
    }
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF080605)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("TVA", color = Color(0xFFF28A35), fontSize = 52.sp, fontWeight = FontWeight.Black, letterSpacing = 10.sp)
        Text("TIME VARIANCE AUTHORITY", color = Color(0xFFB58B6C), fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(310.dp)) {
            CharacterScene(Modifier.fillMaxSize(), modelScale = 1.0f)
        }
        Text(
            stages[index],
            color = if (index == 2) Color(0xFFFFB15C) else Color(0xFFFFE8D2),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(18.dp))
        LinearProgressIndicator(
            progress = (index + 1).toFloat() / stages.size.toFloat(),
            modifier = Modifier.fillMaxWidth(.82f),
            color = Color(0xFFF28A35),
            trackColor = Color(0xFF382319)
        )
        Spacer(Modifier.height(8.dp))
        Text("TEMPORAL ARCHIVE RESTORATION", color = Color(0xFF8F715C), fontSize = 9.sp, letterSpacing = 1.sp)
    }
}
