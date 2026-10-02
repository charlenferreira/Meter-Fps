package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val HudColorScheme = darkColorScheme(
    primary = HudCyan,
    onPrimary = HudDarkBg,
    primaryContainer = HudSurfaceVariant,
    onPrimaryContainer = HudCyanLight,
    secondary = HudEmerald,
    onSecondary = HudDarkBg,
    secondaryContainer = HudSurfaceHighlight,
    onSecondaryContainer = HudEmerald,
    tertiary = HudAmber,
    onTertiary = HudDarkBg,
    background = HudDarkBg,
    onBackground = TextPrimary,
    surface = HudSurface,
    onSurface = TextPrimary,
    surfaceVariant = HudSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = HudBorder,
    error = HudCrimson,
    onError = TextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = HudDarkBg.toArgb()
                window.navigationBarColor = HudDarkBg.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = HudColorScheme,
        typography = Typography,
        content = content
    )
}
