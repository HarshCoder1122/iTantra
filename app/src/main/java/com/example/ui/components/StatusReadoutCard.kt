package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PeerDevice
import com.example.model.RadioChannelState
import com.example.model.SupportedLanguage
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

/**
 * ISRO Mission Control Transponder HUD Readout Card.
 * Displays real-time RF carrier state, language routing (e.g. Hindi ➔ Marathi),
 * incoming voice transcription with original audio tag, and live STT preview.
 */
@Composable
fun StatusReadoutCard(
    channelState: RadioChannelState,
    connectedPeer: PeerDevice?,
    selectedLanguage: SupportedLanguage,
    currentTranscript: String,
    incomingCaption: String?,
    isIncomingAlert: Boolean,
    isTtsSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val stateColor = when (channelState) {
        RadioChannelState.TRANSMITTING -> IsroSaffron
        RadioChannelState.RECEIVING -> if (isIncomingAlert) IsroDistressRed else IsroCyanBright
        RadioChannelState.LISTENING -> IsroCyan
        RadioChannelState.STANDBY -> IsroGreen
    }

    val stateLabel = when (channelState) {
        RadioChannelState.TRANSMITTING -> "TX: TRANSMITTING VOICE PACKET..."
        RadioChannelState.RECEIVING -> if (isIncomingAlert) "RX: INCOMING SOS DISTRESS BEACON" else "RX: PLAYING INBOUND VOICE NOTE"
        RadioChannelState.LISTENING -> "STANDBY: SILERO-VAD LISTENING..."
        RadioChannelState.STANDBY -> "CARRIER READY • PTT ACTIVE"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(IsroPanelSurface)
            .border(
                width = 1.5.dp,
                color = if (isIncomingAlert) IsroDistressRed else IsroBorderSubtle,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header: Status badge & Language Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(stateColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stateLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = stateColor
                        )
                    )
                }

                // Active Language Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(IsroDarkCarbon)
                        .border(0.8.dp, IsroCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${selectedLanguage.englishName.uppercase()} (${selectedLanguage.nativeName})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = IsroCyanBright
                        )
                    )
                }
            }

            // Subtitle: Connected Transceiver Peer & Link Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (connectedPeer != null) "LINK: ${connectedPeer.name} [${connectedPeer.address}]" else "LINK: LOCAL MESH BROADCAST (CH-1)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = IsroOffWhite,
                        fontSize = 11.sp
                    )
                )

                Text(
                    text = if (connectedPeer != null) "${connectedPeer.protocol.name} • ${connectedPeer.signalStrengthDbm} dBm" else "DIRECT AD-HOC",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = IsroCyan,
                        fontSize = 10.sp
                    )
                )
            }

            // Live Incoming Voice Caption Box (when receiving TTS or alert)
            AnimatedVisibility(
                visible = incomingCaption != null || isTtsSpeaking,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isIncomingAlert) IsroDistressRed.copy(alpha = 0.18f)
                            else IsroDarkCarbon
                        )
                        .border(
                            1.dp,
                            if (isIncomingAlert) IsroDistressRed else IsroCyan.copy(alpha = 0.6f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isIncomingAlert) Icons.Default.Campaign else Icons.Default.VolumeUp,
                            contentDescription = "Voice Speaker",
                            tint = if (isIncomingAlert) IsroDistressRed else IsroCyanBright,
                            modifier = Modifier.size(20.dp)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = if (isIncomingAlert) "INCOMING EMERGENCY ALERT" else "INCOMING VOICE (ON-DEVICE TRANSLATED)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = if (isIncomingAlert) IsroDistressRed else IsroCyan
                                )
                            )

                            Text(
                                text = incomingCaption ?: "Receiving audio stream...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    color = IsroWhite
                                )
                            )
                        }
                    }
                }
            }

            // Live Transcribing Preview Box (When user is pressing PTT or speaking)
            AnimatedVisibility(
                visible = (channelState == RadioChannelState.TRANSMITTING || channelState == RadioChannelState.LISTENING) && currentTranscript.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(IsroDarkCarbon)
                        .border(1.dp, IsroGreen.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Microphone",
                            tint = IsroGreenBright,
                            modifier = Modifier.size(18.dp)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "LIVE STT (INDIC-CTC NEURAL):",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    color = IsroGreen
                                )
                            )

                            Text(
                                text = currentTranscript,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = IsroWhite
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
