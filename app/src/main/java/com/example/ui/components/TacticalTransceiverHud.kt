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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PeerDevice
import com.example.model.RadioChannelState
import com.example.model.SupportedLanguage
import com.example.model.VadStatus
import com.example.ui.theme.MinimalColorsInstance
import kotlin.math.sin

/**
 * Minimal Audio & Transcript Panel (Linear / Things 3 aesthetic).
 * Restrained audio spectrum with single accent color #6C5CE7, 16dp rounded card, flat 1dp outline.
 */
@Composable
fun TacticalTransceiverHud(
    channelState: RadioChannelState,
    connectedPeer: PeerDevice?,
    selectedLanguage: SupportedLanguage,
    currentTranscript: String,
    incomingCaption: String?,
    isIncomingAlert: Boolean,
    isTtsSpeaking: Boolean,
    audioLevel: Float,
    vadStatus: VadStatus,
    speechProbability: Float = 0f,
    modifier: Modifier = Modifier
) {
    val colors = MinimalColorsInstance
    val barCount = 24
    val smoothedLevel = remember { Animatable(0f) }

    LaunchedEffect(audioLevel) {
        smoothedLevel.animateTo(
            targetValue = audioLevel,
            animationSpec = tween(durationMillis = 50, easing = FastOutLinearInEasing)
        )
    }

    val isVoiceActive = channelState != RadioChannelState.STANDBY || vadStatus == VadStatus.SPEECH_DETECTED || audioLevel > 0.05f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(
                width = 1.dp,
                color = if (isIncomingAlert) colors.error else colors.outline,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status Row: Channel & Peer info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isIncomingAlert) colors.error
                            else if (isVoiceActive) colors.accent
                            else colors.outline
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isIncomingAlert -> "Emergency alert inbound"
                        channelState == RadioChannelState.TRANSMITTING -> "Transmitting..."
                        channelState == RadioChannelState.RECEIVING -> "Receiving audio"
                        channelState == RadioChannelState.LISTENING -> "Listening"
                        else -> "Standby"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isIncomingAlert) colors.error else colors.textPrimary
                )
            }

            Text(
                text = if (connectedPeer != null) connectedPeer.name else "Mesh Channel 1",
                fontSize = 13.sp,
                color = colors.textSecondary
            )
        }

        // Clean Minimal Soundwave Bars (Single Accent Color)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.background)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            val totalWidth = size.width
            val canvasHeight = size.height
            val barWidth = (totalWidth / barCount) * 0.5f
            val spacing = (totalWidth - (barWidth * barCount)) / (barCount - 1)
            val currentLevel = smoothedLevel.value

            for (i in 0 until barCount) {
                val normalizedIndex = i.toFloat() / barCount
                val bellCurve = sin(normalizedIndex * Math.PI).toFloat()

                val barHeightFraction = if (isVoiceActive) {
                    ((currentLevel * 0.85f * bellCurve) + 0.15f).coerceIn(0.15f, 0.95f)
                } else {
                    0.1f
                }

                val calculatedBarHeight = canvasHeight * barHeightFraction
                val x = i * (barWidth + spacing)
                val y = (canvasHeight - calculatedBarHeight) / 2f

                drawRoundRect(
                    color = if (isVoiceActive) colors.accent else colors.outline,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, calculatedBarHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }

        // Subtitle / Transcript Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.background)
                .padding(12.dp)
        ) {
            when {
                isIncomingAlert && incomingCaption != null -> {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = colors.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Distress broadcast",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.error
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = incomingCaption,
                                fontSize = 15.sp,
                                color = colors.textPrimary
                            )
                        }
                    }
                }

                incomingCaption != null || isTtsSpeaking -> {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Incoming translation (${selectedLanguage.nativeName})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.accent
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = incomingCaption ?: "Playing incoming audio...",
                                fontSize = 15.sp,
                                color = colors.textPrimary
                            )
                        }
                    }
                }

                (channelState == RadioChannelState.TRANSMITTING || channelState == RadioChannelState.LISTENING) && currentTranscript.isNotBlank() -> {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Recognized speech",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.accent
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentTranscript,
                                fontSize = 15.sp,
                                color = colors.textPrimary
                            )
                        }
                    }
                }

                else -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Hold button to speak, or toggle voice activation",
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}
