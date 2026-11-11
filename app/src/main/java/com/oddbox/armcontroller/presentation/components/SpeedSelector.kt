package com.oddbox.armcontroller.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oddbox.armcontroller.domain.model.ArmSpeed
import com.oddbox.armcontroller.presentation.theme.ButtonInactive
import com.oddbox.armcontroller.presentation.theme.CardBackground
import com.oddbox.armcontroller.presentation.theme.CardBorder
import com.oddbox.armcontroller.presentation.theme.DeepBlue
import com.oddbox.armcontroller.presentation.theme.ElectricBlue
import com.oddbox.armcontroller.presentation.theme.TextDim
import com.oddbox.armcontroller.presentation.theme.TextMuted
import com.oddbox.armcontroller.presentation.theme.TextWhite

@Composable
fun SpeedSelector(
    selectedSpeed: ArmSpeed,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSpeedSelected: (ArmSpeed) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MOVEMENT SPEED",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextWhite
                )
                Text(
                    text = "STEP ${selectedSpeed.level}x (${selectedSpeed.command})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ElectricBlue
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ArmSpeed.values().forEach { speed ->
                    val isSelected = speed == selectedSpeed

                    val bgColor by animateColorAsState(
                        targetValue = when {
                            !enabled -> ButtonInactive.copy(alpha = 0.4f)
                            isSelected -> DeepBlue
                            else -> ButtonInactive
                        },
                        label = "speed_bg"
                    )

                    val borderColor by animateColorAsState(
                        targetValue = when {
                            !enabled -> CardBorder.copy(alpha = 0.3f)
                            isSelected -> ElectricBlue
                            else -> CardBorder
                        },
                        label = "speed_border"
                    )

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable(enabled = enabled) {
                                onSpeedSelected(speed)
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = bgColor,
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = speed.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                letterSpacing = 0.5.sp,
                                color = when {
                                    !enabled -> TextDim
                                    isSelected -> TextWhite
                                    else -> TextMuted
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

