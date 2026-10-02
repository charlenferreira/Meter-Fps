package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FpsMetrics
import com.example.model.HudConfig
import com.example.model.RamMetrics
import com.example.model.ThermalMetrics
import com.example.ui.theme.HudAmber
import com.example.ui.theme.HudCrimson
import com.example.ui.theme.HudCyan
import com.example.ui.theme.HudCyanLight
import com.example.ui.theme.HudDarkBg
import com.example.ui.theme.HudEmerald
import com.example.ui.theme.HudSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun FloatingHudView(
    config: HudConfig,
    fpsMetrics: FpsMetrics,
    thermalMetrics: ThermalMetrics,
    ramMetrics: RamMetrics,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onToggleCompact: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Dynamic FPS color indicator
    val fpsColor = when {
        fpsMetrics.fps >= 55 -> HudEmerald
        fpsMetrics.fps >= 30 -> HudAmber
        else -> HudCrimson
    }

    // Dynamic Thermal color indicator
    val tempColor = when {
        thermalMetrics.isThrottling || thermalMetrics.statusLevel >= 2 -> HudCrimson
        thermalMetrics.batteryTempCelsius >= 40f -> HudAmber
        else -> HudCyan
    }

    // Dynamic RAM color
    val ramColor = when {
        ramMetrics.usagePercentage >= 85 -> HudCrimson
        ramMetrics.usagePercentage >= 70 -> HudAmber
        else -> HudCyanLight
    }

    val hudBackground = HudDarkBg.copy(alpha = config.alphaFactor)
    val hudBorderColor = HudCyan.copy(alpha = (config.alphaFactor * 0.7f).coerceIn(0.2f, 0.9f))

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = config.scaleFactor
                scaleY = config.scaleFactor
            }
            .clip(RoundedCornerShape(16.dp))
            .background(hudBackground)
            .border(1.5.dp, hudBorderColor, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            }
            .padding(8.dp)
            .testTag("floating_hud_container")
    ) {
        if (config.compactMode) {
            // Compact pill view (ideal for in-game)
            CompactHudContent(
                fps = fpsMetrics.fps,
                fpsColor = fpsColor,
                cpuTemp = thermalMetrics.estimatedCpuTempCelsius,
                tempColor = tempColor,
                ramUsage = ramMetrics.usagePercentage,
                ramColor = ramColor,
                onToggleCompact = onToggleCompact,
                onClose = onClose
            )
        } else {
            // Detailed dashboard view
            DetailedHudContent(
                config = config,
                fpsMetrics = fpsMetrics,
                fpsColor = fpsColor,
                thermalMetrics = thermalMetrics,
                tempColor = tempColor,
                ramMetrics = ramMetrics,
                ramColor = ramColor,
                onToggleCompact = onToggleCompact,
                onClose = onClose
            )
        }
    }
}

@Composable
private fun CompactHudContent(
    fps: Int,
    fpsColor: Color,
    cpuTemp: Float,
    tempColor: Color,
    ramUsage: Int,
    ramColor: Color,
    onToggleCompact: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = "Mover HUD",
            tint = TextMuted,
            modifier = Modifier.size(18.dp)
        )

        // FPS
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$fps",
                color = fpsColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = " FPS",
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(14.dp)
                .background(HudSurfaceVariant)
        )

        // CPU / Temp
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${cpuTemp.toInt()}°C",
                color = tempColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(14.dp)
                .background(HudSurfaceVariant)
        )

        // RAM %
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$ramUsage%",
                color = ramColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = " RAM",
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }

        // Expand button
        IconButton(
            onClick = onToggleCompact,
            modifier = Modifier
                .size(24.dp)
                .testTag("expand_hud_button")
        ) {
            Icon(
                imageVector = Icons.Default.OpenInFull,
                contentDescription = "Expandir HUD",
                tint = HudCyan,
                modifier = Modifier.size(14.dp)
            )
        }

        // Close button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(24.dp)
                .testTag("close_hud_button")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Fechar HUD",
                tint = HudCrimson,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun DetailedHudContent(
    config: HudConfig,
    fpsMetrics: FpsMetrics,
    fpsColor: Color,
    thermalMetrics: ThermalMetrics,
    tempColor: Color,
    ramMetrics: RamMetrics,
    ramColor: Color,
    onToggleCompact: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(min = 180.dp, max = 220.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        // Drag header & Action controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Mover HUD",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "HUD MONITOR",
                    color = HudCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = onToggleCompact,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("compact_hud_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.UnfoldLess,
                        contentDescription = "Modo Compacto",
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("close_hud_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar HUD",
                        tint = HudCrimson,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // FPS Metric Row
        if (config.showFps) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(HudSurfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "FPS",
                        tint = fpsColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "${fpsMetrics.fps}",
                            color = fpsColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            lineHeight = 24.sp
                        )
                        Text(
                            text = "FPS REAL",
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (config.showRefreshRate) {
                        Text(
                            text = "${fpsMetrics.refreshRate.toInt()}Hz",
                            color = HudCyanLight,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = "${fpsMetrics.frameTimeMs}ms",
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Thermal Metric Row
        if (config.showThermal) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(HudSurfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Thermostat,
                        contentDescription = "Térmico",
                        tint = tempColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "${thermalMetrics.estimatedCpuTempCelsius}°C",
                            color = tempColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "EST. CPU",
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${thermalMetrics.batteryTempCelsius}°C",
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (thermalMetrics.isThrottling) HudCrimson else HudEmerald)
                        )
                        Text(
                            text = thermalMetrics.statusDescription,
                            color = if (thermalMetrics.isThrottling) HudCrimson else TextMuted,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 9.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // RAM Metric Row
        if (config.showRam) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(HudSurfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "RAM",
                            tint = ramColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "RAM ${ramMetrics.usagePercentage}%",
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f/%.1f GB", ramMetrics.usedGb, ramMetrics.totalGb),
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = { (ramMetrics.usagePercentage / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = ramColor,
                    trackColor = HudDarkBg
                )
            }
        }
    }
}
