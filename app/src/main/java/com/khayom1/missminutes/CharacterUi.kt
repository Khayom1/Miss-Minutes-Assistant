package com.khayom1.missminutes

import android.os.SystemClock
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sceneview.SceneView
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicReference

/**
 * Converts the AI's semantic intent to a clip supported by the currently shipped
 * GLB. Clip names are validated by tools/validate-model.mjs in CI.
 *
 * ProceduralRigController applies named node/bone transforms after each authored clip update.
 * Morph-target facial deformation and phoneme-level lip sync remain separate release gates.
 */
internal fun MotionIntent.animationName(): String = when {
    gesture == "talk" -> "Talk_Loop"
    gesture == "wave" -> "Wave_Greeting"
    gesture == "point" -> "Pointing"
    gesture == "shrug" || gesture == "open_hands" || gesture == "present" -> "Wave_Greeting"
    mood == "surprised" -> "Surprised"
    mood == "angry" -> "Angry_Expression"
    mood == "sad" || mood == "sleepy" -> "Sleep_Idle"
    mood == "happy" || mood == "playful" -> "Cheerful_Bounce"
    mood == "curious" || gaze != 0f || gazeVertical != 0f -> "Look_Around"
    else -> "Idle_Breathe"
}

internal fun MotionIntent.animationSpeed(): Float =
    (0.85f + energy.coerceIn(0f, 1f) * 0.35f).coerceIn(0.85f, 1.2f)

internal fun isLoopingCharacterAnimation(name: String): Boolean =
    name in setOf("Idle_Breathe", "Talk_Loop", "Walk_Cycle", "Run_Cycle", "Look_Around")

internal fun characterAnimationDurationMillis(name: String): Long = when (name) {
    "Blink" -> 300L
    "Wave_Greeting" -> 1_850L
    "Pointing" -> 1_250L
    "Laugh" -> 850L
    "Surprised" -> 950L
    "Angry_Expression" -> 1_050L
    "Sleep_Idle" -> 1_550L
    "Glitch_Recovery" -> 700L
    "Peek_Around" -> 1_800L
    "Spin_In_Place" -> 2_000L
    "Nervous_Glance" -> 1_200L
    "Cheerful_Bounce" -> 1_500L
    else -> 1_200L
}

@Composable
fun CharacterScene(
    modifier: Modifier = Modifier,
    motion: MotionIntent = MotionIntent.neutral(),
    modelScale: Float = 1.0f,
    behaviorMode: BehaviorMode? = null
) {
    val resolvedBehaviorMode = behaviorMode ?: if (motion.gesture == "talk") BehaviorMode.SPEAKING else BehaviorMode.IDLE
    var activeAnimation by remember { mutableStateOf(motion.animationName()) }
    val latestMotion = rememberUpdatedState(motion)
    val latestAnimation = rememberUpdatedState(activeAnimation)
    val rigControllerRef = remember { AtomicReference<ProceduralRigController?>(null) }

    LaunchedEffect(motion, resolvedBehaviorMode) {
        val selected = when (resolvedBehaviorMode) {
            BehaviorMode.SPEAKING -> "Talk_Loop"
            BehaviorMode.THINKING, BehaviorMode.LISTENING, BehaviorMode.INTERACTING -> "Look_Around"
            BehaviorMode.HIDDEN, BehaviorMode.IDLE -> motion.animationName()
        }
        activeAnimation = selected
        val startedAt = SystemClock.elapsedRealtime()
        var animationUntil = if (isLoopingCharacterAnimation(selected)) {
            Long.MAX_VALUE
        } else {
            startedAt + characterAnimationDurationMillis(selected)
        }
        val autonomous = AutonomousBehaviorEngine()

        while (true) {
            val now = SystemClock.elapsedRealtime()
            when (resolvedBehaviorMode) {
                BehaviorMode.SPEAKING -> {
                    if (activeAnimation != "Talk_Loop") activeAnimation = "Talk_Loop"
                    animationUntil = Long.MAX_VALUE
                }
                BehaviorMode.THINKING, BehaviorMode.LISTENING, BehaviorMode.INTERACTING -> {
                    if (activeAnimation != "Look_Around") activeAnimation = "Look_Around"
                    animationUntil = Long.MAX_VALUE
                    autonomous.nextAction(now, resolvedBehaviorMode, motion.mood, motion.energy)
                }
                BehaviorMode.HIDDEN -> {
                    if (activeAnimation != "Idle_Breathe") activeAnimation = "Idle_Breathe"
                    animationUntil = Long.MAX_VALUE
                    autonomous.nextAction(now, BehaviorMode.HIDDEN, motion.mood, motion.energy)
                }
                BehaviorMode.IDLE -> {
                    if (now >= animationUntil) {
                        activeAnimation = "Idle_Breathe"
                        animationUntil = Long.MAX_VALUE
                    }
                    if (activeAnimation == "Idle_Breathe") {
                        val action = autonomous.nextAction(now, BehaviorMode.IDLE, motion.mood, motion.energy)
                        if (action != null) {
                            activeAnimation = action.animationName
                            animationUntil = now + action.durationMillis
                        }
                    }
                }
            }

            delay(100L)
        }
    }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    SceneView(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        cameraManipulator = rememberCameraManipulator(),
        onFrame = { frameTimeNanos ->
            val elapsedMillis = (frameTimeNanos / 1_000_000L) % 60_000L
            rigControllerRef.get()?.apply(elapsedMillis, latestMotion.value, latestAnimation.value)
        }
    ) {
        val instance = rememberModelInstance(modelLoader, "models/miss-minutes.glb")
        instance?.let {
            ModelNode(
                modelInstance = it,
                scaleToUnits = modelScale,
                autoAnimate = false,
                animationName = activeAnimation,
                animationLoop = isLoopingCharacterAnimation(activeAnimation),
                animationSpeed = motion.animationSpeed(),
                apply = { rigControllerRef.set(ProceduralRigController(this)) }
            )
            DisposableEffect(it) {
                onDispose { rigControllerRef.set(null) }
            }
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
            progress = { (index + 1).toFloat() / stages.size.toFloat() },
            modifier = Modifier.fillMaxWidth(.82f),
            color = Color(0xFFF28A35),
            trackColor = Color(0xFF382319)
        )
        Spacer(Modifier.height(8.dp))
        Text("TEMPORAL ARCHIVE RESTORATION", color = Color(0xFF8F715C), fontSize = 9.sp, letterSpacing = 1.sp)
    }
}
