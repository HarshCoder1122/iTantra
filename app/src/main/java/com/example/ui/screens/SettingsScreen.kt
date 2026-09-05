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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LanguagePack
import com.example.model.SupportedLanguage
import com.example.model.VerifiedAsset
import com.example.ui.theme.MinimalColorsInstance
import com.example.viewmodel.MissionControlViewModel

/**
 * Minimal Settings & On-Device Neural Model Hub (Linear / Notion / Things 3 aesthetic).
 * Single accent color #6C5CE7, flat 1dp cards, clean segmented theme controls.
 */
@Composable
fun SettingsScreen(
    viewModel: MissionControlViewModel,
    modifier: Modifier = Modifier
) {
    val colors = MinimalColorsInstance
    val uiState by viewModel.uiState.collectAsState()
    val languagePacks: Map<String, LanguagePack> by viewModel.languagePacks.collectAsState()
    val verifiedAssets: Map<String, VerifiedAsset> by viewModel.verifiedAssets.collectAsState()
    val isManifestLoaded: Boolean by viewModel.isManifestLoaded.collectAsState()
    val sttModelInfo by viewModel.sttModelInfo.collectAsState()
    val ttsModelInfo by viewModel.ttsModelInfo.collectAsState()

    val scrollState = rememberScrollState()

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
                text = "Settings",
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "On-device neural models & app preferences",
                fontSize = 13.sp,
                color = colors.textSecondary
            )
        }

        // Section 2: Theme Selector (System / Light / Dark)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Appearance",
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
                    val themes = listOf(
                        Triple("system", "System", Icons.Default.SettingsBrightness),
                        Triple("light", "Light", Icons.Default.LightMode),
                        Triple("dark", "Dark", Icons.Default.DarkMode)
                    )

                    themes.forEach { (mode, label, icon) ->
                        val isSelected = uiState.themeMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colors.accentContainer else Color.Transparent)
                                .clickable { viewModel.setThemeMode(mode) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) colors.accent else colors.textSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
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
        }

        // Section 3: Audio & Transceiver Preferences
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Transceiver preferences",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Switch 1: Max Volume Disaster Override
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Emergency volume override",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Plays disaster sirens at full volume even if device is silenced",
                                fontSize = 13.sp,
                                color = colors.textSecondary
                            )
                        }

                        Switch(
                            checked = uiState.forceMaxVolumeAlerts,
                            onCheckedChange = { viewModel.setForceMaxVolumeAlerts(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.accent,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.outline
                            ),
                            modifier = Modifier.testTag("force_max_vol_switch")
                        )
                    }

                    HorizontalDivider(thickness = 1.dp, color = colors.outline)

                    // Switch 2: Low-Power Idle Listening
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Low-power standby",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Puts microphone buffers to sleep during silence to conserve battery",
                                fontSize = 13.sp,
                                color = colors.textSecondary
                            )
                        }

                        Switch(
                            checked = uiState.isLowPowerListeningEnabled,
                            onCheckedChange = { viewModel.setLowPowerListeningEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.accent,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.outline
                            ),
                            modifier = Modifier.testTag("low_power_switch")
                        )
                    }
                }
            }
        }

        // Section 4: Bundled Neural Models
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "On-device neural models",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )

                Text(
                    text = if (isManifestLoaded) "Manifest verified" else "Loading...",
                    fontSize = 13.sp,
                    color = if (isManifestLoaded) colors.accent else colors.textSecondary
                )
            }

            // Live result of the last "Test" press below — modelInfo only tracks
            // whichever single language is currently loaded (STT/TTS unload the
            // previous language before loading the next, to keep RAM down), so this
            // reads as one shared status line rather than per-row.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = "STT: ${sttModelInfo.name}" +
                            if (sttModelInfo.isLoaded) " · ${sttModelInfo.inferenceLatencyMs}ms" else "",
                        fontSize = 12.sp,
                        color = if (sttModelInfo.isLoaded) colors.accent else colors.textSecondary
                    )
                    Text(
                        text = "TTS: ${ttsModelInfo.name}",
                        fontSize = 12.sp,
                        color = if (ttsModelInfo.isReady) colors.accent else colors.textSecondary
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (languagePacks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Reading model_manifest.json...",
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }
                } else {
                    SupportedLanguage.entries.forEach { lang ->
                        val pack = languagePacks[lang.code]
                        val sttAsset = pack?.stt
                        val ttsAsset = pack?.tts
                        val sttVerified = sttAsset?.let { verifiedAssets[it.modelPath] }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${lang.englishName} (${lang.nativeName})",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = when {
                                            sttAsset == null -> "STT not installed · TTS not installed"
                                            sttVerified == null -> "${sttAsset.name} · verifying..."
                                            else -> "${sttAsset.name} · ${sttVerified.sizeBytes / 1_000_000} MB" +
                                                if (ttsAsset == null) " · TTS not installed" else " · TTS ${ttsAsset.name}"
                                        },
                                        fontSize = 13.sp,
                                        color = colors.textSecondary
                                    )
                                }

                                OutlinedButton(
                                    onClick = { viewModel.runModelBenchmark(lang.code) },
                                    enabled = sttAsset != null,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Test", fontSize = 12.sp, color = colors.textPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 5: App Information Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Walkie-Talkie Mesh",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
                Text(
                    text = "Offline multilingual voice transceiver app for field rescue and disaster alert communications. Operates 100% on-device without cloud or cellular infrastructure.",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            }
        }
    }
}
