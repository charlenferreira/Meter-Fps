package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.model.ThemePreset
import com.example.model.toThemeColors

@Composable
fun MyApplicationTheme(
    themePreset: ThemePreset = ThemePreset.BEIGE,
    content: @Composable () -> Unit
) {
    val themeColors = themePreset.toThemeColors()

    val colorScheme: ColorScheme = if (themeColors.isLight) {
        lightColorScheme(
            primary = themeColors.primary,
            onPrimary = themeColors.onPrimary,
            primaryContainer = themeColors.surfaceVariant,
            onPrimaryContainer = themeColors.textPrimary,
            secondary = themeColors.secondary,
            onSecondary = themeColors.onPrimary,
            secondaryContainer = themeColors.surfaceHighlight,
            onSecondaryContainer = themeColors.textPrimary,
            background = themeColors.background,
            onBackground = themeColors.textPrimary,
            surface = themeColors.surface,
            onSurface = themeColors.textPrimary,
            surfaceVariant = themeColors.surfaceVariant,
            onSurfaceVariant = themeColors.textSecondary,
            outline = themeColors.border
        )
    } else {
        darkColorScheme(
            primary = themeColors.primary,
            onPrimary = themeColors.onPrimary,
            primaryContainer = themeColors.surfaceVariant,
            onPrimaryContainer = themeColors.textPrimary,
            secondary = themeColors.secondary,
            onSecondary = themeColors.onPrimary,
            secondaryContainer = themeColors.surfaceHighlight,
            onSecondaryContainer = themeColors.textPrimary,
            background = themeColors.background,
            onBackground = themeColors.textPrimary,
            surface = themeColors.surface,
            onSurface = themeColors.textPrimary,
            surfaceVariant = themeColors.surfaceVariant,
            onSurfaceVariant = themeColors.textSecondary,
            outline = themeColors.border
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = themeColors.background.toArgb()
                window.navigationBarColor = themeColors.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = themeColors.isLight
                controller.isAppearanceLightNavigationBars = themeColors.isLight
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
