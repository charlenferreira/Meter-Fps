package com.example.model

import android.content.Context
import android.content.SharedPreferences

data class HudConfig(
    val scalePercent: Int = 100, // 75% to 150%
    val opacityPercent: Int = 90, // 30% to 100%
    val showFps: Boolean = true,
    val showRefreshRate: Boolean = true,
    val showThermal: Boolean = true,
    val showRam: Boolean = true,
    val compactMode: Boolean = false,
    val posX: Int = 50,
    val posY: Int = 120
) {
    val scaleFactor: Float get() = scalePercent / 100f
    val alphaFactor: Float get() = opacityPercent / 100f

    companion object {
        private const val PREFS_NAME = "hud_monitor_settings"
        private const val KEY_SCALE = "scale_percent"
        private const val KEY_OPACITY = "opacity_percent"
        private const val KEY_SHOW_FPS = "show_fps"
        private const val KEY_SHOW_REFRESH = "show_refresh"
        private const val KEY_SHOW_THERMAL = "show_thermal"
        private const val KEY_SHOW_RAM = "show_ram"
        private const val KEY_COMPACT = "compact_mode"
        private const val KEY_POS_X = "pos_x"
        private const val KEY_POS_Y = "pos_y"

        fun load(context: Context): HudConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return HudConfig(
                scalePercent = prefs.getInt(KEY_SCALE, 100).coerceIn(70, 150),
                opacityPercent = prefs.getInt(KEY_OPACITY, 90).coerceIn(30, 100),
                showFps = prefs.getBoolean(KEY_SHOW_FPS, true),
                showRefreshRate = prefs.getBoolean(KEY_SHOW_REFRESH, true),
                showThermal = prefs.getBoolean(KEY_SHOW_THERMAL, true),
                showRam = prefs.getBoolean(KEY_SHOW_RAM, true),
                compactMode = prefs.getBoolean(KEY_COMPACT, false),
                posX = prefs.getInt(KEY_POS_X, 60),
                posY = prefs.getInt(KEY_POS_Y, 140)
            )
        }

        fun save(context: Context, config: HudConfig) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt(KEY_SCALE, config.scalePercent)
                .putInt(KEY_OPACITY, config.opacityPercent)
                .putBoolean(KEY_SHOW_FPS, config.showFps)
                .putBoolean(KEY_SHOW_REFRESH, config.showRefreshRate)
                .putBoolean(KEY_SHOW_THERMAL, config.showThermal)
                .putBoolean(KEY_SHOW_RAM, config.showRam)
                .putBoolean(KEY_COMPACT, config.compactMode)
                .putInt(KEY_POS_X, config.posX)
                .putInt(KEY_POS_Y, config.posY)
                .apply()
        }

        fun updatePosition(context: Context, x: Int, y: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt(KEY_POS_X, x)
                .putInt(KEY_POS_Y, y)
                .apply()
        }
    }
}
