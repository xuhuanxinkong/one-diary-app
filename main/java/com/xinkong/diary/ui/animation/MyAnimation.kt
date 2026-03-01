package com.xinkong.diary.ui.animation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun Modifier.pressScaleEffect(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    scaleFactor: Float = 0.85f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleFactor else 1f,
        animationSpec = if (isPressed) {
            tween(80)
        } else {
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium
            )
        },
        label = "press_scale"
    )

    this.then(Modifier.scale(scale))
}

@Composable
fun Modifier.toggleRotateEffect(
    isRotated: Boolean,
    targetAngle: Float = 180f,
    durationMillis: Int = 300
): Modifier = composed {
    val angle by animateFloatAsState(
        targetValue = if (isRotated) targetAngle else 0f,
        animationSpec = tween(durationMillis),
        label = "toggle_rotate"
    )

    // 应用旋转
    this.graphicsLayer {
        rotationX = angle
    }
}