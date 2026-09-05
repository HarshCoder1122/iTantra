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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.model.AlertPriority
import com.example.ui.theme.MinimalColorsInstance
import com.example.viewmodel.MissionControlViewModel

/**
 * Minimal Emergency SOS Screen (Linear / Things 3 aesthetic).
 * Restrained, high-clarity emergency broadcast interface.
 */
@Composable
fun AlertDistressScreen(
    viewModel: MissionControlViewModel,
    modifier: Modifier = Modifier
) {
    val colors = MinimalColorsInstance
    val uiState by viewModel.uiState.collectAsState()
    val connectedPeer by viewModel.connectedPeer.collectAsState()
    var selectedPriority by remember { mutableStateOf(AlertPriority.CRITICAL_DISTRESS) }
    var customMessageText by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var alertSentConfirmation by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    val presets = listOf(
        Pair("Cyclone Alert", "चक्रवात चेतावनी: तटीय क्षेत्र तुरंत खाली करें। सुरक्षित आश्रय में जाएं।"),
        Pair("Flash Flood", "बाढ़ चेतावनी: जलस्तर तेजी से बढ़ रहा है। उच्च स्थान की ओर प्रस्थान करें।"),
        Pair("Medical Emergency", "आपातकालीन चिकित्सा: त्वरित बचाव दल और एम्बुलेंस की तत्काल आवश्यकता है।"),
        Pair("Comms Blackout", "आपदा संचार चेतावनी: बैकअप सैटेलाइट टर्मिनल सक्रिय। राहत कार्य जारी रखें।")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Section 1: Screen Header
        Column {
            Text(
                text = "Emergency SOS",
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Broadcast high-priority distress signal over offline mesh",
                fontSize = 13.sp,
                color = colors.textSecondary
            )
        }

        // Section 2: Confirmation Notice if broadcast
        if (alertSentConfirmation) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.accent, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Distress signal broadcasted",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Transmitted to all connected mesh nodes with volume override",
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }

        // Section 3: Priority Selector (Restrained Segmented Style)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Alert priority",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    val priorities = listOf(
                        Pair(AlertPriority.CRITICAL_DISTRESS, "Critical SOS"),
                        Pair(AlertPriority.URGENT, "Urgent"),
                        Pair(AlertPriority.ROUTINE, "Routine")
                    )

                    priorities.forEach { (priority, label) ->
                        val isSelected = selectedPriority == priority
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colors.accentContainer else Color.Transparent)
                                .clickable { selectedPriority = priority }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = if (isSelected) colors.accent else colors.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // Section 4: Quick Emergency Presets
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Preset incident messages",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (label, text) ->
                    val isSelected = customMessageText == text

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) colors.accentContainer else colors.surface)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) colors.accent else colors.outline,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { customMessageText = text }
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = label,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isSelected) colors.accent else colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = text,
                                fontSize = 13.sp,
                                color = colors.textSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Section 5: Custom Message Input
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Custom broadcast message",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )

            OutlinedTextField(
                value = customMessageText,
                onValueChange = { customMessageText = it },
                placeholder = { Text("Enter emergency broadcast text...", color = colors.textSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.outline,
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            )
        }

        // Section 6: Primary CTA Button
        Button(
            onClick = { showConfirmDialog = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.error,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("broadcast_sos_button")
        ) {
            Icon(
                imageVector = Icons.Default.Campaign,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Broadcast Emergency SOS",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    // Confirmation Dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "Confirm Emergency Broadcast",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "This will trigger a high-volume acoustic alert siren on all connected mesh devices.",
                    fontSize = 15.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val messageToBroadcast = customMessageText.ifBlank {
                            "आपातकालीन चेतावनी: तत्काल सहायता आवश्यक है।"
                        }
                        viewModel.broadcastDistressAlert(
                            customMessage = messageToBroadcast,
                            priority = selectedPriority
                        )
                        showConfirmDialog = false
                        alertSentConfirmation = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirm Broadcast", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel", color = colors.textPrimary)
                }
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
