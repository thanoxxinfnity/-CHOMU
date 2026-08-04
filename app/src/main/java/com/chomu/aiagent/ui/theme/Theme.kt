package com.chomu.aiagent.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary         = DarkPrimary,
    onPrimary       = DarkOnPrimary,
    secondary       = DarkSecondary,
    tertiary        = DarkTertiary,
    background      = DarkBackground,
    surface         = DarkSurface,
    surfaceVariant  = DarkSurfaceVariant,
    onSurface       = DarkOnSurface,
    onSurfaceVariant= DarkOnSurface.copy(alpha = 0.7f),
    outline         = DarkOutline,
    error           = DarkError
)

private val LightColorScheme = lightColorScheme(
    primary         = LightPrimary,
    onPrimary       = LightOnPrimary,
    secondary       = LightSecondary,
    tertiary        = LightTertiary,
    background      = LightBackground,
    surface         = LightSurface,
    surfaceVariant  = LightSurfaceVariant,
    onSurface       = LightOnSurface,
    onSurfaceVariant= LightOnSurface.copy(alpha = 0.7f),
    outline         = LightOutline,
    error           = LightError
)

@Composable
fun ChomuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.hashCode()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
