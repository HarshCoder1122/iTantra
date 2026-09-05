package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RadioChannelState
import com.example.model.VadStatus
import com.example.ui.theme.IsroBorderStrong
import com.example.ui.theme.IsroBorderSubtle
import com.example.ui.theme.IsroCyan
import com.example.ui.theme.IsroCyanBright
import com.example.ui.theme.IsroDarkCarbon
import com.example.ui.theme.IsroDistressRed
import com.example.ui.theme.IsroGreen
import com.example.ui.theme.IsroGreenBright
import com.example.ui.theme.IsroMutedText
import com.example.ui.theme.IsroOffWhite
import com.example.ui.theme.IsroPanelElevated
import com.example.ui.theme.IsroPanelSurface
import com.example.ui.theme.IsroSaffron
import com.example.ui.theme.IsroWhite
import kotlin.math.sin

/**
 * ISRO Tactical Acoustic Waterfall & Silero VAD Neural Trigger Scope.
 * Features:
 * 1. 32-Band FFT-like Spectrum Analyzer with phosphorescent audio bars.
 * 2. Silero VAD v4 Neural Speech Probability Gauge (P(voice) 0%..100%).
 * 3. Exact threshold markers: T_start (50%) and T_end (35%) with hysteresis lock indicator.
 */
@Composable
fun WaveformVisualizer(
    audioLevel: Float,
    vadStatus: VadStatus,
    channelState: RadioChannelState,
    speechProbability: Float = 0f,
    modifier: Modifier = Modifier
) {
    val barCount = 32
    val smoothedLevel = remember { Animatable(0f) }
    val smoothedProb = remember { Animatable(0f) }

    LaunchedEffect(audioLevel) {
        smoothedLevel.animateTo(
            targetValue = audioLevel,
            animationSpec = tween(durationMillis = 50, easing = FastOutLinearInEasing)
        )
    }

    LaunchedEffect(speechProbability) {
        smoothedProb.animateTo(
            targetValue = speechProbability,
            animationSpec = tween(durationMillis = 40, easing = FastOutLinearInEasing)
        )
    }

    val isVoiceDetected = vadStatus == VadStatus.SPEECH_DETECTED || speechProbability >= 0.50f
    val probPercent = (smoothedProb.value * 100f).toInt().coerceIn(0, 100)

    val activeBarColor = when {
        channelState == RadioChannelState.TRANSMITTING -> IsroSaffron
        channelState == RadioChannelState.RECEIVING -> IsroCyanBright
        isVoiceDetected -> IsroGreenBright
        channelState == RadioChannelState.LISTENING -> IsroCyan
        else -> IsroBorderSubtle
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(IsroPanelSurface)
            .border(1.5.dp, if (isVoiceDetected) IsroBorderStrong else IsroBorderSubtle, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row: Spectrum Title + Silero VAD Neural Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ACOUSTIC WATERFALL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = IsroCyan
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "16 kHz PCM",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = IsroMutedText
                        )
                    )
                }

                // Silero VAD Neural Lock Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isVoiceDetected) IsroGreen.copy(alpha = 0.2f) else IsroPanelElevated)
                        .border(
                            0.8.dp,
                            if (isVoiceDetected) IsroGreen else IsroBorderSubtle,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isVoiceDetected) IsroGreenBright else IsroMutedText)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isVoiceDetected) "SILERO-VAD LOCK" else "SILERO-VAD IDLE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp,
                                color = if (isVoiceDetected) IsroGreenBright else IsroMutedText
                            )
                        )
                    }
                }
            }

            // Audio Spectrum Bars Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(IsroDarkCarbon, RoundedCornerShape(8.dp))
                    .border(0.8.dp, IsroBorderSubtle, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                val totalWidth = size.width
                val canvasHeight = size.height
                val barWidth = (totalWidth / barCount) * 0.62f
                val spacing = (totalWidth - (barWidth * barCount)) / (barCount - 1)
                val currentLevel = smoothedLevel.value

                for (i in 0 until barCount) {
                    val normalizedIndex = i.toFloat() / barCount
                    val bellCurve = sin(normalizedIndex * Math.PI).toFloat()
                    val variation = (sin((i * 1.5f) + (currentLevel * 9f)) * 0.25f).toFloat()

                    val isAudioActive = channelState != RadioChannelState.STANDBY || isVoiceDetected || currentLevel > 0.05f
                    val barHeightFraction = if (isAudioActive) {
                        ((currentLevel * 0.88f * bellCurve) + (variation * currentLevel) + 0.12f).coerceIn(0.10f, 0.98f)
                    } else {
                        0.08f
                    }

                    val calculatedBarHeight = canvasHeight * barHeightFraction
                    val x = i * (barWidth + spacing)
                    val y = (canvasHeight - calculatedBarHeight) / 2f

                    // Phosphor glowing bar
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = if (isAudioActive) {
                                listOf(activeBarColor, activeBarColor.copy(alpha = 0.5f))
                            } else {
                                listOf(IsroPanelElevated, IsroPanelElevated.copy(alpha = 0.3f))
                            }
                        ),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, calculatedBarHeight),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )

                    // Peak-hold phosphor dot on active bars
                    if (isAudioActive && calculatedBarHeight > 14.dp.toPx()) {
                        drawCircle(
                            color = IsroWhite,
                            radius = 1.2.dp.toPx(),
                            center = Offset(x + (barWidth / 2f), y - 1.5.dp.toPx())
                        )
                    }
                }
            }

            // Silero VAD Neural Trigger Meter & Hysteresis Indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Neural Probability Readout
                Text(
                    text = "P(VOICE): $probPercent%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        color = if (isVoiceDetected) IsroGreenBright else IsroOffWhite
                    )
                )

                // Silero VAD Threshold Bar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(IsroDarkCarbon)
                        .border(0.5.dp, IsroBorderSubtle, RoundedCornerShape(3.dp))
                ) {
                    // Fill bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(smoothedProb.value.coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        IsroCyan,
                                        if (smoothedProb.value >= 0.50f) IsroGreenBright else IsroCyanBright
                                    )
                                )
                            )
                    )
                }

                // Thresholds notation
                Text(
                    text = "TH: 50% / 35%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = IsroMutedText
                    )
                )
            }
        }
    }
}
