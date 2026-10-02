package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hardware.SystemOptimizer
import com.example.model.FpsMetrics
import com.example.model.HudConfig
import com.example.model.NetworkMetrics
import com.example.model.ProcessMetrics
import com.example.model.RamMetrics
import com.example.model.ThemeColors
import com.example.model.ThermalMetrics
import com.example.network.SpeedTestState

@Composable
fun FloatingHudView(
    config: HudConfig,
    fpsMetrics: FpsMetrics,
    thermalMetrics: ThermalMetrics,
    ramMetrics: RamMetrics,
    networkMetrics: NetworkMetrics,
    processMetrics: ProcessMetrics,
    speedTestState: SpeedTestState = SpeedTestState.Idle,
    onTriggerSpeedTest: () -> Unit = {},
    onDrag: (dx: Float, dy: Float) -> Unit,
    onToggleCompact: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = config.colors

    var isDrawerExpanded by remember { mutableStateOf(false) }

    // Auto-analysis of lag & stutter
    val diagnosticResult by SystemOptimizer.diagnosticState.collectAsState()
    val isDiagnosing by SystemOptimizer.isDiagnosing.collectAsState()

    // Memory clean feedback
    val memoryFeedback by SystemOptimizer.memoryCleanFeedback.collectAsState()

    // Dynamic FPS color indicator
    val fpsColor = when {
        fpsMetrics.fps >= 55 -> if (colors.isLight) Color(0xFF15803D) else Color(0xFF00E676)
        fpsMetrics.fps >= 30 -> if (colors.isLight) Color(0xFFD97706) else Color(0xFFFFAB00)
        else -> if (colors.isLight) Color(0xFFDC2626) else Color(0xFFFF1744)
    }

    // Dynamic Thermal color indicator
    val tempColor = when {
        thermalMetrics.isThrottling || thermalMetrics.statusLevel >= 2 -> if (colors.isLight) Color(0xFFDC2626) else Color(0xFFFF1744)
        thermalMetrics.batteryTempCelsius >= 40f -> if (colors.isLight) Color(0xFFD97706) else Color(0xFFFFAB00)
        else -> colors.primary
    }

    // Dynamic RAM color
    val ramColor = when {
        ramMetrics.usagePercentage >= 85 -> if (colors.isLight) Color(0xFFDC2626) else Color(0xFFFF1744)
        ramMetrics.usagePercentage >= 70 -> if (colors.isLight) Color(0xFFD97706) else Color(0xFFFFAB00)
        else -> colors.secondary
    }

    // Dynamic Ping color
    val pingColor = when {
        !networkMetrics.isConnected || networkMetrics.pingMs == 0 -> colors.textMuted
        networkMetrics.pingMs <= 50 -> if (colors.isLight) Color(0xFF15803D) else Color(0xFF00E676)
        networkMetrics.pingMs <= 110 -> if (colors.isLight) Color(0xFFD97706) else Color(0xFFFFAB00)
        else -> if (colors.isLight) Color(0xFFDC2626) else Color(0xFFFF1744)
    }

    val hudBackground = colors.surface.copy(alpha = config.alphaFactor)
    val hudBorderColor = colors.primary.copy(alpha = (config.alphaFactor * 0.85f).coerceIn(0.2f, 0.95f))

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
        Column {
            if (config.compactMode) {
                CompactHudContent(
                    config = config,
                    colors = colors,
                    fps = fpsMetrics.fps,
                    fpsColor = fpsColor,
                    cpuTemp = thermalMetrics.estimatedCpuTempCelsius,
                    tempColor = tempColor,
                    ramUsage = ramMetrics.usagePercentage,
                    ramColor = ramColor,
                    pingMs = networkMetrics.pingMs,
                    pingColor = pingColor,
                    speedTestState = speedTestState,
                    isDiagnosing = isDiagnosing,
                    diagnosticResult = diagnosticResult,
                    memoryFeedback = memoryFeedback,
                    onTriggerSpeedTest = onTriggerSpeedTest,
                    onTriggerDiagnostic = {
                        SystemOptimizer.runNetworkDiagnostic(context, fpsMetrics.fps)
                    },
                    onTriggerMemoryClean = {
                        SystemOptimizer.performMemoryOptimization(context, ramMetrics.isCritical)
                    },
                    onToggleCompact = onToggleCompact,
                    onClose = onClose
                )
            } else {
                DetailedHudContent(
                    config = config,
                    colors = colors,
                    fpsMetrics = fpsMetrics,
                    fpsColor = fpsColor,
                    thermalMetrics = thermalMetrics,
                    tempColor = tempColor,
                    ramMetrics = ramMetrics,
                    ramColor = ramColor,
                    networkMetrics = networkMetrics,
                    pingColor = pingColor,
                    processMetrics = processMetrics,
                    speedTestState = speedTestState,
                    isDiagnosing = isDiagnosing,
                    diagnosticResult = diagnosticResult,
                    memoryFeedback = memoryFeedback,
                    isDrawerExpanded = isDrawerExpanded,
                    onToggleDrawer = { isDrawerExpanded = !isDrawerExpanded },
                    onTriggerSpeedTest = onTriggerSpeedTest,
                    onTriggerDiagnostic = {
                        SystemOptimizer.runNetworkDiagnostic(context, fpsMetrics.fps)
                    },
                    onTriggerMemoryClean = {
                        SystemOptimizer.performMemoryOptimization(context, ramMetrics.isCritical)
                    },
                    onToggleCompact = onToggleCompact,
                    onClose = onClose
                )
            }

            // Notification Banner for Lag Diagnostic
            AnimatedVisibility(
                visible = diagnosticResult != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                diagnosticResult?.let { diag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (diag.isNetworkIssue) Color(0xFFDC2626).copy(alpha = 0.2f) else colors.primary.copy(alpha = 0.2f))
                            .border(1.dp, if (diag.isNetworkIssue) Color(0xFFDC2626) else colors.primary, RoundedCornerShape(8.dp))
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (diag.isNetworkIssue) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (diag.isNetworkIssue) Color(0xFFDC2626) else colors.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Column {
                            Text(
                                text = diag.causeTitle,
                                color = colors.textPrimary,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp
                            )
                            if (diag.details.isNotBlank()) {
                                Text(
                                    text = diag.details,
                                    color = colors.textSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }

            // Notification Banner for Memory Clean Feedback
            AnimatedVisibility(
                visible = memoryFeedback != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                memoryFeedback?.let { feedback ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.primary.copy(alpha = 0.2f))
                            .border(1.dp, colors.primary, RoundedCornerShape(8.dp))
                            .padding(6.dp)
                    ) {
                        Text(
                            text = feedback,
                            color = colors.textPrimary,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactHudContent(
    config: HudConfig,
    colors: ThemeColors,
    fps: Int,
    fpsColor: Color,
    cpuTemp: Float,
    tempColor: Color,
    ramUsage: Int,
    ramColor: Color,
    pingMs: Int,
    pingColor: Color,
    speedTestState: SpeedTestState,
    isDiagnosing: Boolean,
    diagnosticResult: com.example.model.LagDiagnosticResult?,
    memoryFeedback: String?,
    onTriggerSpeedTest: () -> Unit,
    onTriggerDiagnostic: () -> Unit,
    onTriggerMemoryClean: () -> Unit,
    onToggleCompact: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = "Mover HUD (Arraste livre)",
            tint = colors.textMuted,
            modifier = Modifier.size(18.dp)
        )

        // SpeedTest Flash Result or Progress (takes priority in compact mode when active)
        when (speedTestState) {
            is SpeedTestState.Testing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    color = colors.primary,
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Testando...",
                    color = colors.primary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                DividerSeparator(colors)
            }
            is SpeedTestState.Result -> {
                Text(
                    text = "${speedTestState.pingMs}ms | ${speedTestState.downloadMbps}M",
                    color = colors.primary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                DividerSeparator(colors)
            }
            is SpeedTestState.Error -> {
                Text(
                    text = "Erro Rede",
                    color = Color(0xFFDC2626),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
                DividerSeparator(colors)
            }
            is SpeedTestState.Idle -> {
                // FPS
                if (config.showFps) {
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
                            color = colors.textSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                    DividerSeparator(colors)
                }

                // CPU / Temp
                if (config.showThermal) {
                    Text(
                        text = "${cpuTemp.toInt()}°C",
                        color = tempColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    DividerSeparator(colors)
                }

                // RAM %
                if (config.showRam) {
                    Text(
                        text = "$ramUsage% RAM",
                        color = ramColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Ping (Clickable for network diagnostic)
                if (config.showPing && pingMs > 0) {
                    DividerSeparator(colors)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onTriggerDiagnostic() }
                    ) {
                        if (isDiagnosing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = colors.primary,
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "Toque para Autoanálise de Conexão",
                                tint = pingColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${pingMs}ms",
                            color = pingColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Memory Clean (Vassourinha)
        IconButton(
            onClick = onTriggerMemoryClean,
            modifier = Modifier
                .size(24.dp)
                .testTag("compact_memory_clean_button")
        ) {
            Icon(
                imageVector = Icons.Default.AutoFixHigh,
                contentDescription = "Otimizar Memória RAM",
                tint = colors.primary,
                modifier = Modifier.size(15.dp)
            )
        }

        // Speedtest Flash Trigger Button
        IconButton(
            onClick = onTriggerSpeedTest,
            modifier = Modifier
                .size(24.dp)
                .testTag("compact_speedtest_flash_button")
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = "Speedtest Flash (Mini teste rápido)",
                tint = if (speedTestState is SpeedTestState.Testing) colors.primary else colors.secondary,
                modifier = Modifier.size(16.dp)
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
                contentDescription = "Expandir HUD Detalhado",
                tint = colors.primary,
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
                tint = if (colors.isLight) Color(0xFFDC2626) else Color(0xFFFF1744),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun DetailedHudContent(
    config: HudConfig,
    colors: ThemeColors,
    fpsMetrics: FpsMetrics,
    fpsColor: Color,
    thermalMetrics: ThermalMetrics,
    tempColor: Color,
    ramMetrics: RamMetrics,
    ramColor: Color,
    networkMetrics: NetworkMetrics,
    pingColor: Color,
    processMetrics: ProcessMetrics,
    speedTestState: SpeedTestState,
    isDiagnosing: Boolean,
    diagnosticResult: com.example.model.LagDiagnosticResult?,
    memoryFeedback: String?,
    isDrawerExpanded: Boolean,
    onToggleDrawer: () -> Unit,
    onTriggerSpeedTest: () -> Unit,
    onTriggerDiagnostic: () -> Unit,
    onTriggerMemoryClean: () -> Unit,
    onToggleCompact: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(min = 205.dp, max = 250.dp)
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
                    contentDescription = "Mover HUD (Arraste livre pela tela)",
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "METER FPS",
                    color = colors.primary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Memory Clean (Vassourinha)
                IconButton(
                    onClick = onTriggerMemoryClean,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("detailed_memory_clean_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = "Limpeza Rápida de Memória",
                        tint = colors.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Speedtest Flash Button
                IconButton(
                    onClick = onTriggerSpeedTest,
                    enabled = speedTestState !is SpeedTestState.Testing,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("speedtest_flash_button")
                ) {
                    if (speedTestState is SpeedTestState.Testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            color = colors.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Speedtest Flash (Medir Vazão)",
                            tint = colors.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Expandable Drawer Arrow Button
                IconButton(
                    onClick = onToggleDrawer,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("drawer_arrow_button")
                ) {
                    Icon(
                        imageVector = if (isDrawerExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isDrawerExpanded) "Recolher Diagnóstico" else "Expandir Diagnóstico de Recursos",
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onToggleCompact,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("compact_hud_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.UnfoldLess,
                        contentDescription = "Alternar Modo Compacto",
                        tint = colors.textSecondary,
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
                        tint = if (colors.isLight) Color(0xFFDC2626) else Color(0xFFFF1744),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Speedtest Flash Live Status / Result Banner
        when (speedTestState) {
            is SpeedTestState.Testing -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.primary.copy(alpha = 0.15f))
                        .border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = colors.primary,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Testando rede...",
                        color = colors.primary,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            is SpeedTestState.Result -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.primary.copy(alpha = 0.18f))
                        .border(1.dp, colors.primary, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Ping: ${speedTestState.pingMs} ms | Down: ${speedTestState.downloadMbps} Mbps",
                        color = colors.textPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            is SpeedTestState.Error -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFDC2626).copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "⚡ Erro de Rede",
                        color = Color(0xFFDC2626),
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            is SpeedTestState.Idle -> {
                // Idle
            }
        }

        // FPS Metric Row
        if (config.showFps) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Taxa de Quadros por Segundo (FPS)",
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
                            color = colors.textMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (config.showRefreshRate) {
                        Text(
                            text = "${fpsMetrics.refreshRate.toInt()}Hz",
                            color = colors.primary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = "${fpsMetrics.frameTimeMs}ms",
                        color = colors.textSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
        }

        // Ping Row (Clickable to trigger Wi-Fi Auto-Diagnostic)
        if (config.showPing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.6f))
                    .clickable { onTriggerDiagnostic() }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isDiagnosing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = colors.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "Toque para Autoanálise de Conexão",
                            tint = pingColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = if (networkMetrics.pingMs > 0) "${networkMetrics.pingMs} ms" else "Calculando...",
                        color = pingColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = networkMetrics.networkType,
                        color = colors.textSecondary,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "(Diag)",
                        color = colors.primary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
        }

        // Thermal Metric Row
        if (config.showThermal) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Thermostat,
                        contentDescription = "Sensor de Temperatura e Thermal Throttling",
                        tint = tempColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "${thermalMetrics.estimatedCpuTempCelsius}°C",
                            color = tempColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "EST. CPU",
                            color = colors.textMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${thermalMetrics.batteryTempCelsius}°C Bat",
                        color = colors.textPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Text(
                        text = thermalMetrics.statusDescription,
                        color = if (thermalMetrics.isThrottling) Color(0xFFDC2626) else colors.textMuted,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 9.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
        }

        // RAM Metric Row
        if (config.showRam) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
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
                            contentDescription = "Consumo de Memória RAM",
                            tint = ramColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "RAM ${ramMetrics.usagePercentage}%",
                            color = colors.textPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f/%.1f GB", ramMetrics.usedGb, ramMetrics.totalGb),
                        color = colors.textSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                LinearProgressIndicator(
                    progress = { (ramMetrics.usagePercentage / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = ramColor,
                    trackColor = colors.background
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
        }

        // Expandable Resource Diagnostic Drawer
        AnimatedVisibility(
            visible = isDrawerExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceHighlight.copy(alpha = 0.5f))
                    .border(1.dp, colors.primary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "DIAGNÓSTICO AVANÇADO",
                        color = colors.primary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    Text(
                        text = if (thermalMetrics.isThrottling) "⚠️ THROTTLING" else "✓ NORMAL",
                        color = if (thermalMetrics.isThrottling) Color(0xFFDC2626) else if (colors.isLight) Color(0xFF15803D) else Color(0xFF00E676),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }

                // App in Focus
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Whatshot,
                        contentDescription = "Jogo ou Aplicativo em Execução",
                        tint = colors.secondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "App: ${processMetrics.topAppName}",
                        color = colors.textPrimary,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }

                // Thermal Throttling Diagnosis
                Text(
                    text = if (thermalMetrics.isThrottling) {
                        "Alerta: Redução térmica ativada! FPS pode oscilar."
                    } else {
                        "Térmico: Sistema operando com temperatura segura."
                    },
                    color = if (thermalMetrics.isThrottling) Color(0xFFDC2626) else colors.textSecondary,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 9.5.sp
                )

                // Memory Breakdown (MB/GB)
                Text(
                    text = "Livre: ${ramMetrics.availMb} MB / Total: ${ramMetrics.totalMb} MB",
                    color = colors.textSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp
                )
            }
        }
    }
}

@Composable
private fun DividerSeparator(colors: ThemeColors) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(14.dp)
            .background(colors.border)
    )
}
