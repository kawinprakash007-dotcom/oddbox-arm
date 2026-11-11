package com.oddbox.armcontroller.presentation.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oddbox.armcontroller.domain.model.ArmAxis
import com.oddbox.armcontroller.domain.model.ArmDirection
import com.oddbox.armcontroller.presentation.theme.CardBackground
import com.oddbox.armcontroller.presentation.theme.CardBorder
import com.oddbox.armcontroller.presentation.theme.ConnectedGreen
import com.oddbox.armcontroller.presentation.theme.ElectricBlue
import com.oddbox.armcontroller.presentation.theme.GaugeTrack
import com.oddbox.armcontroller.presentation.theme.TextDim
import com.oddbox.armcontroller.presentation.theme.TextMuted
import com.oddbox.armcontroller.presentation.theme.TextWhite
import com.oddbox.armcontroller.presentation.theme.WarningAmber

@Composable
fun ServoStatusCard(
    axis: ArmAxis,
    servoModel: String,
    currentAngle: Int?,
    isConfirmed: Boolean,
    primaryDirection: ArmDirection,
    secondaryDirection: ArmDirection,
    primaryIcon: ImageVector,
    secondaryIcon: ImageVector,
    activeDirection: ArmDirection?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onDirectionPress: (ArmDirection) -> Unit,
    onDirectionRelease: (ArmDirection) -> Unit
) {
    val angleProgress = ((currentAngle ?: 90) - axis.minAngle).toFloat() /
            (axis.maxAngle - axis.minAngle).coerceAtLeast(1)
    val animatedProgress by animateFloatAsState(
        targetValue = angleProgress.coerceIn(0f, 1f),
        label = "angle_gauge"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: Axis name + Servo type + Telemetry status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = axis.displayName.uppercase(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "[$servoModel]",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ElectricBlue
                        )
                    }
                    Text(
                        text = "Range: ${axis.minAngle}° – ${axis.maxAngle}°",
                        fontSize = 11.sp,
                        color = TextDim
                    )
                }

                // Angle readout
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (currentAngle != null) "$currentAngle°" else "--°",
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isConfirmed) ElectricBlue else TextMuted
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isConfirmed) ConnectedGreen else WarningAmber)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isConfirmed) "CONFIRMED" else "ESTIMATE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = if (isConfirmed) ConnectedGreen else WarningAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Visual Angle Gauge Bar
            Column {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = ElectricBlue,
                    trackColor = GaugeTrack
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "${axis.minAngle}°", fontSize = 10.sp, color = TextDim)
                    Text(text = "${(axis.minAngle + axis.maxAngle) / 2}°", fontSize = 10.sp, color = TextDim)
                    Text(text = "${axis.maxAngle}°", fontSize = 10.sp, color = TextDim)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Direction Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DirectionButton(
                    text = primaryDirection.label,
                    icon = primaryIcon,
                    enabled = enabled,
                    isExternalActive = activeDirection == primaryDirection,
                    modifier = Modifier.weight(1f),
                    onPress = { onDirectionPress(primaryDirection) },
                    onRelease = { onDirectionRelease(primaryDirection) }
                )

                DirectionButton(
                    text = secondaryDirection.label,
                    icon = secondaryIcon,
                    enabled = enabled,
                    isExternalActive = activeDirection == secondaryDirection,
                    modifier = Modifier.weight(1f),
                    onPress = { onDirectionPress(secondaryDirection) },
                    onRelease = { onDirectionRelease(secondaryDirection) }
                )
            }
        }
    }
}

