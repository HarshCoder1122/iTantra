package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionStatus
import com.example.model.SupportedLanguage
import com.example.model.TransportProtocol
import com.example.ui.components.LanguageSelectionSheet
import com.example.ui.components.PttButtonWithRings
import com.example.ui.components.TacticalTransceiverHud
import com.example.ui.theme.MinimalColorsInstance
import com.example.viewmodel.MissionControlViewModel

/**
 * Minimal Walkie-Talkie Screen (Linear / Things 3 / Notion aesthetic).
 * - Single accent color #6C5CE7
 * - Screen horizontal margin 20dp, 8dp base unit grid, 24dp vertical section spacing
 * - One clear focal point (Hero PTT button)
 * - 28sp Medium title, 13sp caption, max 2 weights
 */
@Composable
fun MissionControlScreen(
    viewModel: MissionControlViewModel,
    onNavigateToAlerts: () -> Unit,
    onNavigateToPairing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MinimalColorsInstance
    val uiState by viewModel.uiState.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val vadStatus by viewModel.vadStatus.collectAsState()
    val speechProbability by viewModel.speechProbability.collectAsState()
    val activeProtocol by viewModel.activeProtocol.collectAsState()
    val connectedPeer by viewModel.connectedPeer.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsState()

    var showLanguageSheet by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Section 1: Screen Title & Connectivity Meta
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Walkie-Talkie",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (connectionStatus == ConnectionStatus.CONNECTED) colors.accent
                                else colors.outline
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (connectedPeer != null) {
                            "Mesh connected · ${connectedPeer?.name}"
                        } else {
                            "Mesh standby · ${if (activeProtocol == TransportProtocol.BLUETOOTH) "Bluetooth" else "Wi-Fi Direct"}"
                        },
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
            }

            // Language Selector Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                    .clickable { showLanguageSheet = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("lang_selector_pill"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = uiState.selectedLanguage.nativeName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select language",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Section 2: Segmented Mode Selector (Push to Talk vs Voice Activation)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                val isPtt = uiState.isPttActive

                // Push to Talk Option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isPtt) colors.accentContainer else Color.Transparent)
                        .clickable { viewModel.togglePttMode(true) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isPtt) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Push to talk",
                            fontSize = 13.sp,
                            fontWeight = if (isPtt) FontWeight.Medium else FontWeight.Normal,
                            color = if (isPtt) colors.accent else colors.textSecondary
                        )
                    }
                }

                // Voice Activated (VOX) Option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!isPtt) colors.accentContainer else Color.Transparent)
                        .clickable { viewModel.togglePttMode(false) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = if (!isPtt) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Voice activated",
                            fontSize = 13.sp,
                            fontWeight = if (!isPtt) FontWeight.Medium else FontWeight.Normal,
                            color = if (!isPtt) colors.accent else colors.textSecondary
                        )
                    }
                }
            }
        }

        // Section 3: The Hero Focal Point (Push-To-Talk Button)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            PttButtonWithRings(
                channelState = uiState.channelState,
                isPttMode = uiState.isPttActive,
                onPressed = { viewModel.onPttPressed() },
                onReleased = { viewModel.onPttReleased() }
            )
        }

        // Section 4: Live Audio & Transcript Card
        TacticalTransceiverHud(
            channelState = uiState.channelState,
            connectedPeer = connectedPeer,
            selectedLanguage = uiState.selectedLanguage,
            currentTranscript = uiState.currentTranscript,
            incomingCaption = uiState.activeIncomingCaption,
            isIncomingAlert = uiState.activeIncomingIsAlert,
            isTtsSpeaking = isTtsSpeaking,
            audioLevel = audioLevel,
            vadStatus = vadStatus,
            speechProbability = speechProbability
        )

        // Section 5: Secondary Actions (Outlined / Subdued)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.testTtsAudio() },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Test voice",
                    fontSize = 13.sp,
                    color = colors.textPrimary
                )
            }

            OutlinedButton(
                onClick = onNavigateToPairing,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CellTower,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Mesh peers",
                    fontSize = 13.sp,
                    color = colors.textPrimary
                )
            }

            OutlinedButton(
                onClick = onNavigateToAlerts,
                modifier = Modifier
                    .weight(0.8f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "SOS",
                    fontSize = 13.sp,
                    color = colors.error
                )
            }
        }
    }

    if (showLanguageSheet) {
        LanguageSelectionSheet(
            selectedLanguage = uiState.selectedLanguage,
            onLanguageSelected = { lang ->
                viewModel.setSelectedLanguage(lang)
                showLanguageSheet = false
            },
            onDismiss = { showLanguageSheet = false },
            onPreviewAudio = { lang -> viewModel.testTtsAudio(lang.sampleAlertPhrase) }
        )
    }
}
