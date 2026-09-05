package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RadioChannelState
import com.example.ui.theme.MinimalColorsInstance

/**
 * Minimal Push-to-Talk Hero Action (Linear / Arc style).
 * Clean, tactile, single focal point per screen.
 * Accent color #6C5CE7 activated cleanly during speech transmission.
 */
@Composable
fun PttButtonWithRings(
    channelState: RadioChannelState,
    isPttMode: Boolean,
    onPressed: () -> Unit,
    onReleased: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MinimalColorsInstance
    val haptic = LocalHapticFeedback.current
    var isPressedState by remember { mutableStateOf(false) }

    val isTransmitting = channelState == RadioChannelState.TRANSMITTING || (isPttMode && isPressedState)
    val isListening = channelState == RadioChannelState.LISTENING
    val isReceiving = channelState == RadioChannelState.RECEIVING

    val isActive = isTransmitting || isListening || isReceiving

    // Gentle 150ms press scale
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressedState) 0.95f else 1.0f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "PttScale"
    )

    // Smooth color state transitions
    val containerColor by animateColorAsState(
        targetValue = when {
            isTransmitting -> colors.accent
            isListening -> colors.accentContainer
            isReceiving -> colors.accentContainer
            else -> colors.surface
        },
        animationSpec = tween(200),
        label = "PttContainer"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isTransmitting -> Color.White
            isListening -> colors.accent
            isReceiving -> colors.accent
            else -> colors.textPrimary
        },
        animationSpec = tween(200),
        label = "PttContent"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isTransmitting -> colors.accent
            isActive -> colors.accent
            else -> colors.outline
        },
        animationSpec = tween(200),
        label = "PttBorder"
    )

    Box(
        modifier = modifier
            .size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        // Main Tactile Button
        Box(
            modifier = Modifier
                .size(144.dp)
                .scale(buttonScale)
                .clip(CircleShape)
                .background(containerColor)
                .border(
                    width = if (isActive) 1.5.dp else 1.dp,
                    color = borderColor,
                    shape = CircleShape
                )
                .pointerInput(isPttMode) {
                    if (isPttMode) {
                        detectTapGestures(
                            onPress = {
                                isPressedState = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPressed()
                                val released = tryAwaitRelease()
                                isPressedState = false
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onReleased()
                            }
                        )
                    } else {
                        detectTapGestures(
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isListening) onReleased() else onPressed()
                            }
                        )
                    }
                }
                .testTag("ptt_button_main"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = when {
                        isReceiving -> Icons.Default.VolumeUp
                        isListening -> Icons.Default.GraphicEq
                        else -> Icons.Default.Mic
                    },
                    contentDescription = "Push to talk",
                    tint = contentColor,
                    modifier = Modifier.size(36.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when {
                        isTransmitting -> "Transmitting"
                        isReceiving -> "Receiving"
                        isListening -> "Listening"
                        isPttMode -> "Hold to speak"
                        else -> "Tap to speak"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor
                )
            }
        }
    }
}
