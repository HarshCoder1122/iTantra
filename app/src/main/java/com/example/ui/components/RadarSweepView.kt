package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MinimalColorsInstance
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimal RF discovery sweep indicator.
 */
@Composable
fun RadarSweepView(
    isSearching: Boolean = true,
    accentColor: Color = MinimalColorsInstance.accent,
    modifier: Modifier = Modifier
) {
    val colors = MinimalColorsInstance
    val transition = rememberInfiniteTransition(label = "RadarSweep")
    val angleDegrees by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAngle"
    )

    Canvas(modifier = modifier.size(160.dp)) {
        val centerPoint = center
        val radius = size.minDimension / 2f

        // Concentric distance rings
        val rings = listOf(0.33f, 0.66f, 1.0f)
        rings.forEach { ratio ->
            drawCircle(
                color = colors.outline,
                radius = radius * ratio,
                center = centerPoint,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Crosshairs
        drawLine(
            color = colors.outline.copy(alpha = 0.6f),
            start = Offset(centerPoint.x - radius, centerPoint.y),
            end = Offset(centerPoint.x + radius, centerPoint.y),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = colors.outline.copy(alpha = 0.6f),
            start = Offset(centerPoint.x, centerPoint.y - radius),
            end = Offset(centerPoint.x, centerPoint.y + radius),
            strokeWidth = 1.dp.toPx()
        )

        // Rotating sweep arm
        if (isSearching) {
            val sweepRad = Math.toRadians(angleDegrees.toDouble())
            val targetX = centerPoint.x + (radius * cos(sweepRad)).toFloat()
            val targetY = centerPoint.y + (radius * sin(sweepRad)).toFloat()

            drawLine(
                color = accentColor.copy(alpha = 0.8f),
                start = centerPoint,
                end = Offset(targetX, targetY),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}
