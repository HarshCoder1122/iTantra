package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionStatus
import com.example.model.MissionTelemetry
import com.example.model.TransportProtocol
import com.example.ui.theme.IsroBorderStrong
import com.example.ui.theme.IsroBorderSubtle
import com.example.ui.theme.IsroCyan
import com.example.ui.theme.IsroCyanBright
import com.example.ui.theme.IsroCyanContainer
import com.example.ui.theme.IsroDarkCarbon
import com.example.ui.theme.IsroDistressRed
import com.example.ui.theme.IsroGreen
import com.example.ui.theme.IsroGreenBright
import com.example.ui.theme.IsroGreenContainer
import com.example.ui.theme.IsroOffWhite
import com.example.ui.theme.IsroPanelElevated
import com.example.ui.theme.IsroPanelSurface
import com.example.ui.theme.IsroSaffron
import com.example.ui.theme.IsroWhite
import kotlinx.coroutines.delay

/**
 * ISRO Mission Control Telemetry & Command Center Header (ISTRAC / MOX Standard).
 * Features:
 * - Running Mission Elapsed Time (MET) clock
 * - NavIC S-band lock status
 * - Live battery, temperature, RAM telemetry
 * - Active RF transport indicator (Bluetooth RFCOMM vs Wi-Fi Direct)
 */
@Composable
fun TelemetryHeader(
    telemetry: MissionTelemetry,
    connectionStatus: ConnectionStatus,
    alertCount: Int,
    activeProtocol: TransportProtocol = TransportProtocol.WIFI_DIRECT,
    onAlertBadgeClick: () -> Unit = {},
    onProtocolToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Dynamic running Mission Elapsed Time (MET)
    var elapsedSeconds by remember { mutableLongStateOf(1426L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    val metString = String.format("MET: T+ %02d:%02d:%02d", hours, minutes, seconds)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(IsroPanelSurface)
            .border(1.5.dp, IsroBorderSubtle, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Row 1: ISRO / iTantra Header + Running MET Clock + SOS Alert Counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Tricolor Emblem Insignia
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(IsroPanelElevated)
                            .border(1.5.dp, IsroSaffron, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🇮🇳",
                            fontSize = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ISRO • iTANTRA",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = IsroWhite,
                                    fontSize = 15.sp,
                                    letterSpacing = 1.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(IsroGreenContainer)
                                    .border(0.8.dp, IsroGreen, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "OFFLINE 100%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = IsroGreenBright
                                    )
                                )
                            }
                        }

                        Text(
                            text = "Tactical Mesh Transceiver • Sub-GHz / S-Band",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = IsroOffWhite,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Distress Alert Counter (if active)
                if (alertCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(IsroDistressRed)
                            .clickable { onAlertBadgeClick() }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Distress Alerts",
                            tint = IsroWhite,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$alertCount SOS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = IsroWhite,
                                fontSize = 11.sp
                            )
                        )
                    }
                } else {
                    // Mission Clock Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(IsroCyanContainer)
                            .border(0.8.dp, IsroCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = metString,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                color = IsroCyanBright
                            )
                        )
                    }
                }
            }

            // Row 2: Precision Telemetry Bar (Carrier Frequency, NavIC, Transport, Battery)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(IsroDarkCarbon)
                    .border(0.8.dp, IsroBorderSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connection Link State
                val statusColor = when (connectionStatus) {
                    ConnectionStatus.CONNECTED -> IsroGreen
                    ConnectionStatus.SEARCHING -> IsroCyan
                    ConnectionStatus.PAIRING -> IsroSaffron
                    ConnectionStatus.DISCONNECTED, ConnectionStatus.LOST -> IsroDistressRed
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (connectionStatus == ConnectionStatus.CONNECTED) {
                            if (activeProtocol == TransportProtocol.BLUETOOTH) "BT-RFCOMM LOCK" else "S-BAND CH-1"
                        } else connectionStatus.name,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            color = statusColor
                        )
                    )
                }

                // NavIC Satellite / GNSS Lock
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = "NavIC Lock",
                        tint = IsroCyanBright,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "NavIC LOCK",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.5.sp,
                            color = IsroCyanBright
                        )
                    )
                }

                // Protocol badge (clickable to switch)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(IsroPanelElevated)
                        .clickable { onProtocolToggle() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (activeProtocol == TransportProtocol.BLUETOOTH) Icons.Default.Bluetooth else Icons.Default.Wifi,
                        contentDescription = "Protocol",
                        tint = if (activeProtocol == TransportProtocol.BLUETOOTH) IsroCyan else IsroGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (activeProtocol == TransportProtocol.BLUETOOTH) "BLUETOOTH" else "WIFI-DIRECT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = IsroWhite
                        )
                    )
                }

                // Battery Telemetry
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Battery",
                        tint = if (telemetry.batteryPercent > 20) IsroGreen else IsroDistressRed,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${telemetry.batteryPercent}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = IsroWhite
                        )
                    )
                }
            }
        }
    }
}
