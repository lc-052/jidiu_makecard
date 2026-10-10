package com.yike.ai.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.yike.ai.data.repository.PreferencesState

val DarkColors =
    darkColorScheme(
        primary = Color(0xFFA99BFF),
        onPrimary = Color(0xFF201344),
        secondary = Color(0xFF38BDF8),
        background = Color(0xFF151827),
        surface = Color(0xFF151827),
        surfaceVariant = Color(0xFF252A40),
        onBackground = Color(0xFFF0F0FA),
        onSurface = Color(0xFFF0F0FA),
        onSurfaceVariant = Color(0xFFC6C8DF),
    )
val LightColors =
    lightColorScheme(
        primary = Color(0xFF6C5CE7),
        secondary = Color(0xFF087DAF),
        background = Color(0xFFF4F6FF),
        surface = Color(0xFFF4F6FF),
        surfaceVariant = Color(0xFFE6E9FA),
        onBackground = Color(0xFF20243A),
        onSurface = Color(0xFF20243A),
    )

@Composable
fun YikeTheme(p: PreferencesState, content: @Composable () -> Unit) {
    val dark =
        when (p.theme) {
            "LIGHT" -> false
            "SYSTEM" -> isSystemInDarkTheme()
            else -> true
        }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * p.fontScale)
    ) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}
