package com.oddbox.armcontroller.presentation.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oddbox.armcontroller.presentation.theme.ButtonActive
import com.oddbox.armcontroller.presentation.theme.ButtonInactive
import com.oddbox.armcontroller.presentation.theme.CardBorder
import com.oddbox.armcontroller.presentation.theme.ElectricBlue
import com.oddbox.armcontroller.presentation.theme.TextDim
import com.oddbox.armcontroller.presentation.theme.TextMuted
import com.oddbox.armcontroller.presentation.theme.TextWhite

@Composable
fun DirectionButton(
    text: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isExternalActive: Boolean = false,
    modifier: Modifier = Modifier,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    val view = LocalView.current
    var isPressedLocally by remember { mutableStateOf(false) }
    val isPressed = (isPressedLocally || isExternalActive) && enabled

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = tween(durationMillis = 80),
        label = "btn_scale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> ButtonInactive.copy(alpha = 0.4f)
            isPressed -> ButtonActive
            else -> ButtonInactive
        },
        animationSpec = tween(durationMillis = 100),
        label = "btn_bg"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> CardBorder.copy(alpha = 0.3f)
            isPressed -> ElectricBlue
            else -> CardBorder
        },
        animationSpec = tween(durationMillis = 100),
        label = "btn_border"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .defaultMinSize(minHeight = 54.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isPressedLocally = true
                        try {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        } catch (_: Exception) {}
                        onPress()

                        val upOrCancel = waitForUpOrCancellation()
                        isPressedLocally = false
                        try {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
                        } catch (_: Exception) {}
                        onRelease()
                    }
                }
            },
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(1.5.dp, borderColor),
        shadowElevation = if (isPressed) 0.dp else 4.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = text,
                        tint = when {
                            !enabled -> TextDim
                            isPressed -> TextWhite
                            else -> ElectricBlue
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = when {
                        !enabled -> TextDim
                        isPressed -> TextWhite
                        else -> TextWhite
                    }
                )
            }
        }
    }
}

