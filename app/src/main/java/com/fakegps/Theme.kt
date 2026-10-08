package com.fakegps

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1A73E8), onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E3FC), onPrimaryContainer = Color(0xFF041E49),
    secondaryContainer = Color(0xFFE8F0FE), onSecondaryContainer = Color(0xFF174EA6),
    surface = Color.White, onSurface = Color(0xFF202124),
    surfaceVariant = Color(0xFFF1F3F4), onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFF9AA0A6), outlineVariant = Color(0xFFDADCE0),
    error = Color(0xFFD93025), surfaceTint = Color.Transparent,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8AB4F8), onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF174EA6), onPrimaryContainer = Color(0xFFD2E3FC),
    secondaryContainer = Color(0xFF283C5A), onSecondaryContainer = Color(0xFFD2E3FC),
    surface = Color(0xFF202124), onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF303134), onSurfaceVariant = Color(0xFF9AA0A6),
    outline = Color(0xFF80868B), outlineVariant = Color(0xFF3C4043),
    error = Color(0xFFF28B82), surfaceTint = Color.Transparent,
)

@Composable
fun FakeGpsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
