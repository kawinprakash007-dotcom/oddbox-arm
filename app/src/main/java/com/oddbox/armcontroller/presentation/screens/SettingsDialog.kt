package com.oddbox.armcontroller.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.oddbox.armcontroller.data.ble.BleConstants
import com.oddbox.armcontroller.presentation.theme.ButtonInactive
import com.oddbox.armcontroller.presentation.theme.CardBackground
import com.oddbox.armcontroller.presentation.theme.CardBorder
import com.oddbox.armcontroller.presentation.theme.DeepBlue
import com.oddbox.armcontroller.presentation.theme.ElectricBlue
import com.oddbox.armcontroller.presentation.theme.SurfaceDark
import com.oddbox.armcontroller.presentation.theme.TextDim
import com.oddbox.armcontroller.presentation.theme.TextMuted
import com.oddbox.armcontroller.presentation.theme.TextWhite
import com.oddbox.armcontroller.presentation.theme.WarningAmber

@Composable
fun SettingsDialog(
    heartbeatIntervalMs: Long,
    onHeartbeatChanged: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark,
            border = BorderStroke(1.5.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = ElectricBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "SYSTEM SETTINGS",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = TextWhite
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Heartbeat Refresh Timing
                Text(
                    text = "PRESS-AND-HOLD HEARTBEAT REFRESH",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = TextWhite
                )
                Text(
                    text = "ESP32 firmware watchdog stops movement after 600ms if no refresh is received. App repeats command every ${heartbeatIntervalMs}ms while held.",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Interval: ${heartbeatIntervalMs} ms",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricBlue
                    )
                    Text(
                        text = "Timeout: 600 ms",
                        fontSize = 11.sp,
                        color = TextDim
                    )
                }

                Slider(
                    value = heartbeatIntervalMs.toFloat(),
                    onValueChange = { onHeartbeatChanged(it.toLong()) },
                    valueRange = 100f..400f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricBlue,
                        activeTrackColor = DeepBlue,
                        inactiveTrackColor = ButtonInactive
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // BLE Protocol Profile
                Text(
                    text = "BLE PROTOCOL CONSTANTS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = TextWhite
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = CardBackground,
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        ProtocolRow("NAME", BleConstants.DEVICE_NAME)
                        ProtocolRow("SERVICE", BleConstants.SERVICE_UUID.toString())
                        ProtocolRow("COMMAND", BleConstants.COMMAND_CHAR_UUID.toString())
                        ProtocolRow("STATUS", BleConstants.STATUS_CHAR_UUID.toString())
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Safety Disclaimer Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = WarningAmber.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Safety",
                            tint = WarningAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "SAFETY WARNING: Displayed servo positions are estimates unless confirmed by hardware feedback telemetry. The ESP32 must independently enforce mechanical angle limits.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepBlue)
                ) {
                    Text(
                        text = "SAVE & CLOSE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtocolRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextDim
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = ElectricBlue
        )
    }
}

