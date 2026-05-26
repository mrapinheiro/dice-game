package com.example.diceroller.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DiceAnimationState {
    val rotation = Animatable(0f)
    val rotationX = Animatable(0f)
    val rotationY = Animatable(0f)
    val scale = Animatable(1f)
    val yOffset = Animatable(0f)
    val shadowBlur = Animatable(8f)
    var displayNumber by mutableIntStateOf(0)  // 0 = no highlight before first roll
    var isLanded by mutableStateOf(false)      // only highlight when die has landed

    suspend fun animateRoll(finalValue: Int) = coroutineScope {
        isLanded = false

        // Phase 1: Throw (~300ms) — lift off table
        launch { scale.animateTo(1.15f, tween(300)) }
        launch { yOffset.animateTo(-40f, tween(300)) }
        launch { shadowBlur.animateTo(20f, tween(300)) }
        launch { rotationX.animateTo(rotationX.value + 45f, tween(300)) }
        delay(300)

        // Phase 2: Tumble (~800ms) — rapid 3D spin
        val tumbleZ = launch {
            rotation.animateTo(rotation.value + 720f, tween(800, easing = LinearEasing))
        }
        val tumbleX = launch {
            rotationX.animateTo(rotationX.value + 540f, tween(800, easing = LinearEasing))
        }
        val tumbleY = launch {
            rotationY.animateTo(rotationY.value + 360f, tween(800, easing = LinearEasing))
        }
        tumbleZ.join()
        tumbleX.join()
        tumbleY.join()

        // Set final number so the landed highlight shows the rolled face
        displayNumber = finalValue

        // Phase 3: Land (~500ms) — settle to show rolled face toward camera.
        // Normalize accumulated angles into [0, 360) then animate forward to preserve spin direction.
        val (targetRx, targetRy) = getTargetRotationForFace(finalValue)
        val normX = ((rotationX.value % 360f) + 360f) % 360f
        val normY = ((rotationY.value % 360f) + 360f) % 360f
        val normZ = ((rotation.value % 360f) + 360f) % 360f
        rotationX.snapTo(normX)
        rotationY.snapTo(normY)
        rotation.snapTo(normZ)
        val fwdTargetX = if (targetRx >= normX) targetRx else targetRx + 360f
        val fwdTargetY = if (targetRy >= normY) targetRy else targetRy + 360f
        val fwdTargetZ = 360f // continue forward to visual 0°

        launch {
            scale.animateTo(1.08f, tween(100))
            scale.animateTo(0.95f, tween(100))
            scale.animateTo(1f, tween(300))
        }
        launch {
            yOffset.animateTo(5f, tween(100))
            yOffset.animateTo(0f, tween(400))
        }
        launch {
            shadowBlur.animateTo(4f, tween(100))
            shadowBlur.animateTo(8f, tween(400))
        }
        launch { rotationX.animateTo(fwdTargetX, tween(500, easing = FastOutSlowInEasing)) }
        launch { rotationY.animateTo(fwdTargetY, tween(500, easing = FastOutSlowInEasing)) }
        launch { rotation.animateTo(fwdTargetZ, tween(500, easing = FastOutSlowInEasing)) }
        delay(500)

        // Highlight only after the die has fully landed
        isLanded = true
    }

    suspend fun flashRed() {
        scale.animateTo(1.1f, tween(100))
        scale.animateTo(1f, tween(100))
    }
}
