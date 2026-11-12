package com.mitvremote.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MiOrange = Color(0xFFFF6900)

@Composable
fun MiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MiOrange,
            onPrimary = Color.White,
            secondary = Color(0xFF8AB4F8),
            background = Color(0xFF111114),
            surface = Color(0xFF1B1B20),
            surfaceVariant = Color(0xFF2A2A31),
            onBackground = Color(0xFFEDEDED),
            onSurface = Color(0xFFEDEDED),
            onSurfaceVariant = Color(0xFFC4C4CE),
            error = Color(0xFFFF6B5E),
        ),
        content = content,
    )
}
