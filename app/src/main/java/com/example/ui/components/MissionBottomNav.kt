package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MinimalColorsInstance

enum class MissionDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    CONTROL("control", "Walkie", Icons.Default.Mic),
    COMM_LINK("comm_link", "Mesh", Icons.Default.CellTower),
    VOICE_LOG("voice_log", "Messages", Icons.Default.ChatBubbleOutline),
    DISTRESS("distress", "SOS", Icons.Default.WarningAmber),
    SETTINGS("settings", "Settings", Icons.Default.Settings)
}

/**
 * Minimal Navigation Bar (Linear / Things 3 design language).
 * Single accent color #6C5CE7, flat surface, 1dp subtle border, no heavy drop shadows.
 */
@Composable
fun MissionBottomNav(
    currentDestination: MissionDestination,
    onDestinationSelected: (MissionDestination) -> Unit,
    alertCount: Int,
    modifier: Modifier = Modifier
) {
    val colors = MinimalColorsInstance

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
    ) {
        HorizontalDivider(thickness = 1.dp, color = colors.outline)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MissionDestination.entries.forEach { destination ->
                val isSelected = destination == currentDestination
                val isAlertTab = destination == MissionDestination.DISTRESS

                val targetColor = when {
                    isSelected -> colors.accent
                    else -> colors.textSecondary
                }

                val iconColor by animateColorAsState(
                    targetValue = targetColor,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "NavColor"
                )

                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onDestinationSelected(destination) }
                        .padding(vertical = 4.dp)
                        .testTag("nav_tab_${destination.route}")
                ) {
                    if (isAlertTab && alertCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = colors.error,
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        text = "$alertCount",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.title,
                                tint = iconColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.title,
                            tint = iconColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = destination.title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = iconColor
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Minimal 4dp active dot indicator
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 4.dp else 0.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) colors.accent else Color.Transparent)
                    )
                }
            }
        }
    }
}
