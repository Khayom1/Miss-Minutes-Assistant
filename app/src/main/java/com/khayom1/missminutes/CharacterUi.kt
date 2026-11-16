package com.khayom1.missminutes

import android.app.ActivityManager
import android.content.Context
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun CharacterScene(modifier: Modifier = Modifier, animation: String = "Idle_Breathe") {
    val context = LocalContext.current
    val supportsOpenGles3 = remember(context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        (activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0) >= 0x00030000
    }
    if (!supportsOpenGles3) {
        val language = remember(context) {
            AppLocalization.normalize(
                context.getSharedPreferences("tva_session", Context.MODE_PRIVATE).getString("app_language", "ru")
            )
        }
        Box(modifier.background(Color(0xFF21150F)), contentAlignment = Alignment.Center) {
            Text(
                AppLocalization.text(language, "3d_unavailable"),
                color = Color(0xFFFFE8D2),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val loops = setOf("Idle_Breathe", "Talk_Loop", "Walk_Cycle", "Run_Cycle", "Look_Around")
    SceneView(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        cameraManipulator = rememberCameraManipulator()
    ) {
        val instance = rememberModelInstance(modelLoader, "models/miss-minutes.glb")
        instance?.let {
            ModelNode(
                modelInstance = it,
                scaleToUnits = 1.35f,
                autoAnimate = false,
                animationName = animation,
                animationLoop = animation in loops,
                animationSpeed = 1.0f
            )
        }
    }
}

@Composable
fun InitializationSequence(languageCode: String = "ru", onComplete: () -> Unit) {
    val stages = (0..7).map { AppLocalization.text(languageCode, "stage$it") }
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
        Text(AppLocalization.text(languageCode, "authority"), color = Color(0xFFB58B6C), fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(310.dp)) {
            CharacterScene(Modifier.fillMaxSize(), if (index in 3..4) "Glitch_Recovery" else "Idle_Breathe")
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
        Text(AppLocalization.text(languageCode, "temporal_archive"), color = Color(0xFF8F715C), fontSize = 9.sp, letterSpacing = 1.sp)
    }
}
