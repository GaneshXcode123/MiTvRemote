package com.mitvremote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A button that reacts instantly on touch-down and can auto-repeat while held. */
@Composable
fun PressButton(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    repeat: Boolean = false,
    onPress: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val bg = if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else color
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .pointerInput(repeat) {
                detectTapGestures(onPress = {
                    pressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    latest()
                    val job = if (repeat) {
                        scope.launch {
                            delay(450)
                            while (true) {
                                latest()
                                delay(90)
                            }
                        }
                    } else null
                    tryAwaitRelease()
                    job?.cancel()
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun IconKey(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    repeat: Boolean = false,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onPress: () -> Unit,
) {
    PressButton(modifier.size(size), CircleShape, color, repeat, onPress) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
fun TextKey(
    label: String,
    modifier: Modifier = Modifier,
    width: Dp = 100.dp,
    height: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onPress: () -> Unit,
) {
    PressButton(modifier.size(width, height), RoundedCornerShape(24.dp), color, false, onPress) {
        Text(label, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
