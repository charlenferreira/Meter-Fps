package com.example.model

import androidx.compose.ui.graphics.Color

enum class ThemePreset(
    val id: String,
    val title: String,
    val previewPrimary: Color,
    val previewBg: Color
) {
    BEIGE(
        id = "BEIGE",
        title = "Bege / Areia",
        previewPrimary = Color(0xFFD97706),
        previewBg = Color(0xFFF7F2EB)
    ),
    WHITE(
        id = "WHITE",
        title = "Branco Limpo",
        previewPrimary = Color(0xFF2563EB),
        previewBg = Color(0xFFFFFFFF)
    ),
    AMOLED_BLACK(
        id = "AMOLED_BLACK",
        title = "Todo Preto",
        previewPrimary = Color(0xFFE2E8F0),
        previewBg = Color(0xFF000000)
    ),
    CYBER_BLUE(
        id = "CYBER_BLUE",
        title = "Azul Cyber",
        previewPrimary = Color(0xFF00E5FF),
        previewBg = Color(0xFF090D16)
    ),
    RED(
        id = "RED",
        title = "Tons de Vermelho",
        previewPrimary = Color(0xFFFF1744),
        previewBg = Color(0xFF14080B)
    ),
    GREEN(
        id = "GREEN",
        title = "Tons de Verde",
        previewPrimary = Color(0xFF00E676),
        previewBg = Color(0xFF07140B)
    );

    companion object {
        fun fromId(id: String): ThemePreset =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: BEIGE
    }
}

data class ThemeColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceHighlight: Color,
    val border: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val isLight: Boolean
)

fun ThemePreset.toThemeColors(): ThemeColors = when (this) {
    ThemePreset.BEIGE -> ThemeColors(
        background = Color(0xFFF5EFEB),
        surface = Color(0xFFECE4DC),
        surfaceVariant = Color(0xFFDFD5CA),
        surfaceHighlight = Color(0xFFD3C6B8),
        border = Color(0xFFD3C6B8),
        primary = Color(0xFFB45309),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFFD97706),
        accent = Color(0xFF059669),
        textPrimary = Color(0xFF292524),
        textSecondary = Color(0xFF57534E),
        textMuted = Color(0xFF78716C),
        isLight = true
    )

    ThemePreset.WHITE -> ThemeColors(
        background = Color(0xFFF8FAFC),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF1F5F9),
        surfaceHighlight = Color(0xFFE2E8F0),
        border = Color(0xFFE2E8F0),
        primary = Color(0xFF2563EB),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF0284C7),
        accent = Color(0xFF10B981),
        textPrimary = Color(0xFF0F172A),
        textSecondary = Color(0xFF475569),
        textMuted = Color(0xFF64748B),
        isLight = true
    )

    ThemePreset.AMOLED_BLACK -> ThemeColors(
        background = Color(0xFF000000),
        surface = Color(0xFF0D0D0D),
        surfaceVariant = Color(0xFF171717),
        surfaceHighlight = Color(0xFF262626),
        border = Color(0xFF262626),
        primary = Color(0xFFF8FAFC),
        onPrimary = Color(0xFF000000),
        secondary = Color(0xFFE2E8F0),
        accent = Color(0xFF38BDF8),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFA3A3A3),
        textMuted = Color(0xFF737373),
        isLight = false
    )

    ThemePreset.CYBER_BLUE -> ThemeColors(
        background = Color(0xFF090D16),
        surface = Color(0xFF111827),
        surfaceVariant = Color(0xFF1E293B),
        surfaceHighlight = Color(0xFF334155),
        border = Color(0xFF1E293B),
        primary = Color(0xFF00E5FF),
        onPrimary = Color(0xFF090D16),
        secondary = Color(0xFF00E676),
        accent = Color(0xFFFFAB00),
        textPrimary = Color(0xFFF8FAFC),
        textSecondary = Color(0xFF94A3B8),
        textMuted = Color(0xFF64748B),
        isLight = false
    )

    ThemePreset.RED -> ThemeColors(
        background = Color(0xFF14080B),
        surface = Color(0xFF1F0D12),
        surfaceVariant = Color(0xFF2E131C),
        surfaceHighlight = Color(0xFF4A1A2A),
        border = Color(0xFF3B1522),
        primary = Color(0xFFFF1744),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFFFF5252),
        accent = Color(0xFFFF9100),
        textPrimary = Color(0xFFFFF1F2),
        textSecondary = Color(0xFFFECDD3),
        textMuted = Color(0xFFFB7185),
        isLight = false
    )

    ThemePreset.GREEN -> ThemeColors(
        background = Color(0xFF07140B),
        surface = Color(0xFF0E2214),
        surfaceVariant = Color(0xFF16331F),
        surfaceHighlight = Color(0xFF1E482B),
        border = Color(0xFF1A3B24),
        primary = Color(0xFF00E676),
        onPrimary = Color(0xFF07140B),
        secondary = Color(0xFF69F0AE),
        accent = Color(0xFF00E5FF),
        textPrimary = Color(0xFFECFDF5),
        textSecondary = Color(0xFFA7F3D0),
        textMuted = Color(0xFF6EE7B7),
        isLight = false
    )
}
