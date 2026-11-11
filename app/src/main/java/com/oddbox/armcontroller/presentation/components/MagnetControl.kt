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
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.oddbox.armcontroller.domain.model.MagnetState
import com.oddbox.armcontroller.presentation.theme.ButtonInactive
import com.oddbox.armcontroller.presentation.theme.CardBackground
import com.oddbox.armcontroller.presentation.theme.CardBorder
import com.oddbox.armcontroller.presentation.theme.ConnectedGreen
import com.oddbox.armcontroller.presentation.theme.ElectricBlue
import com.oddbox.armcontroller.presentation.theme.EmergencyRed
import com.oddbox.armcontroller.presentation.theme.EmergencyRedBright
import com.oddbox.armcontroller.presentation.theme.TextDim
import com.oddbox.armcontroller.presentation.theme.TextMuted
import com.oddbox.armcontroller.presentation.theme.TextWhite
import com.oddbox.armcontroller.presentation.theme.WarningAmber

@Composable
fun MagnetControl(
    magnetState: MagnetState,
    isSafetyUnlocked: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSafetyToggle: (Boolean) -> Unit,
    onSetMagnetState: (Boolean) -> Unit
) {
    val isEnergized = magnetState == MagnetState.ON

    // Pulsing transition for warning indicator when magnet is live
    val infiniteTransition = rememberInfiniteTransition(label = "magnet_pulse")
    val warningBgColor by infiniteTransition.animateColor(
        initialValue = EmergencyRed.copy(alpha = 0.25f),
        targetValue = EmergencyRed.copy(alpha = 0.85f),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "warn_pulse"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = BorderStroke(
            width = if (isEnergized) 2.dp else 1.dp,
            color = if (isEnergized) EmergencyRed else CardBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with status indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = "Magnet",
                        tint = if (isEnergized) WarningAmber else ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ELECTROMAGNET (12V)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = TextWhite
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (magnetState) {
                                    MagnetState.ON -> EmergencyRedBright
                                    MagnetState.OFF -> ConnectedGreen
                                    MagnetState.UNKNOWN -> TextDim
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (magnetState) {
                            MagnetState.ON -> "ENERGIZED"
                            MagnetState.OFF -> "DE-ENERGIZED"
                            MagnetState.UNKNOWN -> "STANDBY"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (magnetState) {
                            MagnetState.ON -> EmergencyRedBright
                            MagnetState.OFF -> ConnectedGreen
                            MagnetState.UNKNOWN -> TextDim
                        }
                    )
                }
            }

            // Pulsing Warning Banner when active
            AnimatedVisibility(visible = isEnergized) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = warningBgColor,
                        border = BorderStroke(1.dp, EmergencyRedBright)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Hazard",
                                tint = TextWhite,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "COIL ACTIVE: 12V LOAD CURRENT FLOWING",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Safety Interlock Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ButtonInactive.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isSafetyUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = "Safety Lock",
                        tint = if (isSafetyUnlocked) WarningAmber else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isSafetyUnlocked) "SAFETY INTERLOCK: ARMED" else "SAFETY INTERLOCK: LOCKED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSafetyUnlocked) WarningAmber else TextMuted
                        )
                        Text(
                            text = "Prevents accidental 12V coil power",
                            fontSize = 10.sp,
                            color = TextDim
                        )
                    }
                }

                Switch(
                    checked = isSafetyUnlocked,
                    onCheckedChange = { onSafetyToggle(it) },
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = WarningAmber,
                        checkedTrackColor = WarningAmber.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextDim,
                        uncheckedTrackColor = ButtonInactive
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons: MAGNET ON & MAGNET OFF
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onSetMagnetState(true) },
                    enabled = enabled && isSafetyUnlocked && !isEnergized,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmergencyRed,
                        disabledContainerColor = ButtonInactive.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = "MAGNET ON",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }

                OutlinedButton(
                    onClick = { onSetMagnetState(false) },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = ButtonInactive
                    )
                ) {
                    Text(
                        text = "MAGNET OFF",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }
            }
        }
    }
}

