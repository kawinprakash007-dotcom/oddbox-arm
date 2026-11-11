package com.oddbox.armcontroller.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oddbox.armcontroller.data.model.BleConnectionState
import com.oddbox.armcontroller.presentation.theme.ButtonInactive
import com.oddbox.armcontroller.presentation.theme.CardBackground
import com.oddbox.armcontroller.presentation.theme.CardBorder
import com.oddbox.armcontroller.presentation.theme.ConnectedGreen
import com.oddbox.armcontroller.presentation.theme.ConnectedGreenGlow
import com.oddbox.armcontroller.presentation.theme.DeepBlue
import com.oddbox.armcontroller.presentation.theme.ElectricBlue
import com.oddbox.armcontroller.presentation.theme.EmergencyRed
import com.oddbox.armcontroller.presentation.theme.TextDim
import com.oddbox.armcontroller.presentation.theme.TextMuted
import com.oddbox.armcontroller.presentation.theme.TextWhite
import com.oddbox.armcontroller.presentation.theme.WarningAmber

@Composable
fun ConnectionBadge(
    connectionState: BleConnectionState,
    deviceName: String?,
    deviceAddress: String?,
    isScanning: Boolean,
    modifier: Modifier = Modifier,
    onScanClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")
    val pulseGreen by infiniteTransition.animateColor(
        initialValue = ConnectedGreen,
        targetValue = ConnectedGreenGlow,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_green"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = BorderStroke(
            1.dp,
            if (connectionState == BleConnectionState.CONNECTED) ConnectedGreen else CardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Status Dot + Connection Text + Device details
            Row(verticalAlignment = Alignment.CenterVertically) {
                // LED indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            when (connectionState) {
                                BleConnectionState.CONNECTED -> pulseGreen
                                BleConnectionState.CONNECTING -> ElectricBlue
                                BleConnectionState.SCANNING -> WarningAmber
                                BleConnectionState.DISCONNECTING -> EmergencyRed
                                BleConnectionState.DISCONNECTED -> TextDim
                            }
                        )
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = when (connectionState) {
                            BleConnectionState.CONNECTED -> "CONNECTED: ${deviceName ?: "ODDBOX_ARM"}"
                            BleConnectionState.CONNECTING -> "CONNECTING TO ARM…"
                            BleConnectionState.SCANNING -> "SCANNING FOR ODDBOX_ARM…"
                            BleConnectionState.DISCONNECTING -> "DISCONNECTING…"
                            BleConnectionState.DISCONNECTED -> "DISCONNECTED"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = when (connectionState) {
                            BleConnectionState.CONNECTED -> ConnectedGreen
                            BleConnectionState.CONNECTING -> ElectricBlue
                            BleConnectionState.SCANNING -> WarningAmber
                            BleConnectionState.DISCONNECTING -> EmergencyRed
                            BleConnectionState.DISCONNECTED -> TextMuted
                        }
                    )

                    if (!deviceAddress.isNullOrBlank()) {
                        Text(
                            text = deviceAddress,
                            fontSize = 10.sp,
                            color = TextDim
                        )
                    } else {
                        Text(
                            text = "ESP32 BLE Direct Link",
                            fontSize = 10.sp,
                            color = TextDim
                        )
                    }
                }
            }

            // Right: Connect/Disconnect/Scan Action Button
            when (connectionState) {
                BleConnectionState.CONNECTED -> {
                    OutlinedButton(
                        onClick = onDisconnectClick,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = ButtonInactive
                        ),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = "Disconnect",
                            tint = EmergencyRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DISCONNECT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                    }
                }

                BleConnectionState.CONNECTING, BleConnectionState.DISCONNECTING -> {
                    OutlinedButton(
                        onClick = onDisconnectClick,
                        enabled = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text(
                            text = "CANCEL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                    }
                }

                BleConnectionState.DISCONNECTED, BleConnectionState.SCANNING -> {
                    Button(
                        onClick = onScanClick,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeepBlue
                        ),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(
                            imageVector = if (isScanning) Icons.AutoMirrored.Filled.BluetoothSearching else Icons.Default.Bluetooth,
                            contentDescription = "Scan",
                            tint = TextWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isScanning) "SCANNING…" else "CONNECT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                    }
                }
            }
        }
    }
}

