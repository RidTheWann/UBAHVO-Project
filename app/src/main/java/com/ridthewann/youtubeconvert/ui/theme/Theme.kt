package com.ridthewann.youtubeconvert.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColors = darkColorScheme(
    primary = Color(0xFFC62832),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFFFC857),
    onSecondary = Color(0xFF272016),
    background = Color(0xFF0D0F13),
    onBackground = Color(0xFFF4F5F7),
    surface = Color(0xFF171A21),
    onSurface = Color(0xFFF4F5F7),
    surfaceVariant = Color(0xFF242833),
    onSurfaceVariant = Color(0xFFB9BECA),
    outline = Color(0xFF555B68),
    outlineVariant = Color(0xFF353A46),
    error = Color(0xFFFF6868),
    errorContainer = Color(0xFF451F26),
    onErrorContainer = Color(0xFFFFDADD)
)

@Composable
fun UbahvoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
