package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.BuildConfig
import com.example.hardware.HardwareMonitor
import com.example.model.DeviceHardwareInfo
import com.example.model.FpsMetrics
import com.example.model.HudConfig
import com.example.model.RamMetrics
import com.example.model.ThermalMetrics
import com.example.ui.FloatingHudView
import com.example.updater.GitHubUpdateChecker
import com.example.updater.UpdateState
import kotlinx.coroutines.launch
import com.example.ui.theme.HudAmber
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudCrimson
import com.example.ui.theme.HudCyan
import com.example.ui.theme.HudCyanLight
import com.example.ui.theme.HudDarkBg
import com.example.ui.theme.HudEmerald
import com.example.ui.theme.HudSurface
import com.example.ui.theme.HudSurfaceHighlight
import com.example.ui.theme.HudSurfaceVariant
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    private lateinit var hardwareMonitor: HardwareMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hardwareMonitor = HardwareMonitor(this)

        setContent {
            MyApplicationTheme {
                HudDashboardScreen(hardwareMonitor = hardwareMonitor)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hardwareMonitor.start()
    }

    override fun onPause() {
        super.onPause()
        hardwareMonitor.stop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HudDashboardScreen(
    hardwareMonitor: HardwareMonitor
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State observers
    val isHudRunning by FloatingHudService.isRunning.collectAsState()
    val liveFps by hardwareMonitor.fpsMetrics.collectAsState()
    val liveThermal by hardwareMonitor.thermalMetrics.collectAsState()
    val liveRam by hardwareMonitor.ramMetrics.collectAsState()
    val updateState by GitHubUpdateChecker.updateState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var config by remember { mutableStateOf(HudConfig.load(context)) }
    val hardwareInfo = remember { hardwareMonitor.getDeviceHardwareInfo() }

    // Auto-check GitHub releases on launch
    LaunchedEffect(Unit) {
        GitHubUpdateChecker.checkForUpdates()
    }

    // Check overlay permission whenever app is resumed
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Notification permission launcher for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isHudRunning) HudEmerald else TextMuted)
                        )
                        Text(
                            text = "METER FPS",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            letterSpacing = 1.sp,
                            color = HudCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HudDarkBg,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = HudDarkBg
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Required Warning Banner
            if (!hasOverlayPermission) {
                item {
                    OverlayPermissionBanner(
                        onGrantClicked = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // HUD Master Control Switch Card
            item {
                HudMasterControlCard(
                    isHudActive = isHudRunning,
                    hasPermission = hasOverlayPermission,
                    onToggleService = { activate ->
                        if (activate) {
                            if (hasOverlayPermission) {
                                FloatingHudService.start(context)
                            } else {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        } else {
                            FloatingHudService.stop(context)
                        }
                    }
                )
            }

            // GitHub Update Checker Card
            item {
                GitHubUpdateCard(
                    updateState = updateState,
                    onCheckAgain = {
                        coroutineScope.launch {
                            GitHubUpdateChecker.checkForUpdates()
                        }
                    },
                    onOpenUrl = { url ->
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
            }

            // Real-Time Hardware Performance Telemetry Grid
            item {
                Text(
                    text = "TELEMETRIA EM TEMPO REAL",
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        FpsMetricCard(fps = liveFps)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        ThermalMetricCard(thermal = liveThermal)
                    }
                }
            }

            // RAM Memory Card
            item {
                RamMetricCard(ram = liveRam)
            }

            // HUD Customization Card
            item {
                HudCustomizationCard(
                    config = config,
                    onConfigChanged = { updatedConfig ->
                        config = updatedConfig
                        FloatingHudService.updateConfig(context, updatedConfig)
                    }
                )
            }

            // Live HUD Preview
            item {
                HudPreviewCard(
                    config = config,
                    fps = liveFps,
                    thermal = liveThermal,
                    ram = liveRam
                )
            }

            // Device Specs
            item {
                DeviceSpecsCard(hardwareInfo = hardwareInfo)
            }
        }
    }
}

@Composable
fun OverlayPermissionBanner(onGrantClicked: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("overlay_permission_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HudAmber.copy(alpha = 0.12f)),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(HudAmber))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = HudAmber,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.overlay_permission_title),
                    color = HudAmber,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Text(
                text = stringResource(R.string.overlay_permission_desc),
                color = TextPrimary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Button(
                onClick = onGrantClicked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = HudAmber,
                    contentColor = HudDarkBg
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grant_overlay_permission_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.grant_permission_button),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}

@Composable
fun HudMasterControlCard(
    isHudActive: Boolean,
    hasPermission: Boolean,
    onToggleService: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hud_master_control_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHudActive) HudCyan.copy(alpha = 0.08f) else HudSurface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(if (isHudActive) HudCyan else HudBorder)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isHudActive) HudCyan.copy(alpha = 0.2f) else HudSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = if (isHudActive) HudCyan else TextMuted,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column {
                    Text(
                        text = if (isHudActive) "HUD FLUTUANTE ATIVO" else "HUD FLUTUANTE PARADO",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isHudActive) HudCyan else TextPrimary
                    )
                    Text(
                        text = if (isHudActive) "Exibindo overlay sobre outros apps" else "Ative para monitorar jogos e apps",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Switch(
                checked = isHudActive,
                onCheckedChange = onToggleService,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = HudDarkBg,
                    checkedTrackColor = HudCyan,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = HudSurfaceVariant
                ),
                modifier = Modifier.testTag("toggle_hud_service_switch")
            )
        }
    }
}

@Composable
fun FpsMetricCard(fps: FpsMetrics) {
    val fpsColor = when {
        fps.fps >= 55 -> HudEmerald
        fps.fps >= 30 -> HudAmber
        else -> HudCrimson
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(HudBorder))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = fpsColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "TAXA DE QUADROS",
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${fps.fps}",
                    color = fpsColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    lineHeight = 34.sp
                )
                Text(
                    text = " FPS",
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Tela: ${fps.refreshRate.toInt()}Hz",
                    color = HudCyanLight,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
                Text(
                    text = "${fps.frameTimeMs}ms",
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun ThermalMetricCard(thermal: ThermalMetrics) {
    val tempColor = when {
        thermal.isThrottling || thermal.statusLevel >= 2 -> HudCrimson
        thermal.batteryTempCelsius >= 40f -> HudAmber
        else -> HudCyan
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(HudBorder))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Thermostat,
                    contentDescription = null,
                    tint = tempColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "TEMPERATURA",
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${thermal.estimatedCpuTempCelsius}",
                    color = tempColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    lineHeight = 34.sp
                )
                Text(
                    text = "°C",
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bat: ${thermal.batteryTempCelsius}°C",
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
                Text(
                    text = thermal.statusDescription,
                    color = if (thermal.isThrottling) HudCrimson else HudEmerald,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun RamMetricCard(ram: RamMetrics) {
    val ramColor = when {
        ram.usagePercentage >= 85 -> HudCrimson
        ram.usagePercentage >= 70 -> HudAmber
        else -> HudCyan
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(HudBorder))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = ramColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "MEMÓRIA RAM EM USO",
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = "${ram.usagePercentage}%",
                    color = ramColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            LinearProgressIndicator(
                progress = { (ram.usagePercentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = ramColor,
                trackColor = HudDarkBg
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format(java.util.Locale.US, "Usada: %.2f GB", ram.usedGb),
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                Text(
                    text = String.format(java.util.Locale.US, "Livre: %.2f GB", ram.availGb),
                    color = HudEmerald,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                Text(
                    text = String.format(java.util.Locale.US, "Total: %.1f GB", ram.totalGb),
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun HudCustomizationCard(
    config: HudConfig,
    onConfigChanged: (HudConfig) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(HudBorder))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = HudCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "AJUSTES DO WIDGET FLUTUANTE",
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
            }

            // Scale Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Tamanho do HUD",
                            color = TextSecondary,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        text = "${config.scalePercent}%",
                        color = HudCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Slider(
                    value = config.scalePercent.toFloat(),
                    onValueChange = { onConfigChanged(config.copy(scalePercent = it.toInt())) },
                    valueRange = 70f..150f,
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = HudCyan,
                        activeTrackColor = HudCyan,
                        inactiveTrackColor = HudSurfaceVariant
                    ),
                    modifier = Modifier.testTag("scale_slider")
                )
            }

            // Opacity Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Opacity,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Transparência do Fundo",
                            color = TextSecondary,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        text = "${config.opacityPercent}%",
                        color = HudCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Slider(
                    value = config.opacityPercent.toFloat(),
                    onValueChange = { onConfigChanged(config.copy(opacityPercent = it.toInt())) },
                    valueRange = 30f..100f,
                    steps = 13,
                    colors = SliderDefaults.colors(
                        thumbColor = HudCyan,
                        activeTrackColor = HudCyan,
                        inactiveTrackColor = HudSurfaceVariant
                    ),
                    modifier = Modifier.testTag("opacity_slider")
                )
            }

            // Toggle Switches for metrics
            SettingToggleItem(
                label = "Exibir FPS & Taxa de Atualização",
                checked = config.showFps,
                onCheckedChange = { onConfigChanged(config.copy(showFps = it)) },
                testTag = "toggle_show_fps"
            )

            SettingToggleItem(
                label = "Exibir Monitor Térmico / CPU",
                checked = config.showThermal,
                onCheckedChange = { onConfigChanged(config.copy(showThermal = it)) },
                testTag = "toggle_show_thermal"
            )

            SettingToggleItem(
                label = "Exibir Barra de Memória RAM",
                checked = config.showRam,
                onCheckedChange = { onConfigChanged(config.copy(showRam = it)) },
                testTag = "toggle_show_ram"
            )

            SettingToggleItem(
                label = "Modo Compacto por Padrão",
                checked = config.compactMode,
                onCheckedChange = { onConfigChanged(config.copy(compactMode = it)) },
                testTag = "toggle_compact_mode"
            )
        }
    }
}

@Composable
fun SettingToggleItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontFamily = FontFamily.SansSerif,
            fontSize = 13.sp
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = HudDarkBg,
                checkedTrackColor = HudCyan,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = HudSurfaceVariant
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
fun HudPreviewCard(
    config: HudConfig,
    fps: FpsMetrics,
    thermal: ThermalMetrics,
    ram: RamMetrics
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(HudBorder))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = HudCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "PRÉ-VISUALIZAÇÃO AO VIVO DO WIDGET",
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HudDarkBg)
                    .border(1.dp, HudSurfaceHighlight, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                FloatingHudView(
                    config = config,
                    fpsMetrics = fps,
                    thermalMetrics = thermal,
                    ramMetrics = ram,
                    onDrag = { _, _ -> /* Preview is stationary */ },
                    onToggleCompact = { /* Preview toggle */ },
                    onClose = { /* Preview close */ }
                )
            }
        }
    }
}

@Composable
fun DeviceSpecsCard(hardwareInfo: DeviceHardwareInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(HudBorder))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhonelinkSetup,
                    contentDescription = null,
                    tint = HudCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "INFORMAÇÕES DO DISPOSITIVO",
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
            }

            SpecRow(label = "Aparelho", value = "${hardwareInfo.manufacturer} ${hardwareInfo.model}")
            SpecRow(label = "Sistema", value = "${hardwareInfo.androidVersion} (API ${hardwareInfo.apiLevel})")
            SpecRow(label = "Resolução", value = hardwareInfo.displayResolution)
            SpecRow(label = "Taxa Máxima", value = "${hardwareInfo.defaultRefreshRate.toInt()} Hz")
            SpecRow(label = "RAM Total Instalada", value = String.format(java.util.Locale.US, "%.1f GB", hardwareInfo.totalRamGb))
        }
    }
}

@Composable
fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontFamily = FontFamily.SansSerif,
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp
        )
    }
}

@Composable
fun GitHubUpdateCard(
    updateState: UpdateState,
    onCheckAgain: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("github_update_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(HudBorder))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = HudCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "REPOSITÓRIO & ATUALIZAÇÕES",
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    color = HudCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            when (updateState) {
                is UpdateState.Checking -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(HudSurfaceVariant)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = HudCyan,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Consultando releases oficiais no GitHub...",
                            color = TextSecondary,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp
                        )
                    }
                }

                is UpdateState.UpdateAvailable -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(HudCyan.copy(alpha = 0.12f))
                            .border(1.dp, HudCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = HudCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "NOVA VERSÃO DISPONÍVEL: ${updateState.tagName}",
                                color = HudCyan,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        if (updateState.releaseNotes.isNotBlank()) {
                            Text(
                                text = updateState.releaseNotes,
                                color = TextPrimary,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                maxLines = 4
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (updateState.apkDownloadUrl != null) {
                                Button(
                                    onClick = { onOpenUrl(updateState.apkDownloadUrl) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = HudCyan,
                                        contentColor = HudDarkBg
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("download_update_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Baixar APK",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { onOpenUrl(updateState.releaseHtmlUrl) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("view_release_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = null,
                                    tint = HudCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Ver Release",
                                    color = HudCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                is UpdateState.UpToDate -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(HudSurfaceVariant)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = HudEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Aplicativo atualizado (v${updateState.version})",
                                color = TextPrimary,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = onCheckAgain,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HudSurfaceHighlight,
                                contentColor = HudCyan
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("check_updates_again_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Checar",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                is UpdateState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(HudSurfaceVariant)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = HudAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = updateState.message,
                                color = TextSecondary,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = onCheckAgain,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HudCyan,
                                    contentColor = HudDarkBg
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Tentar Novamente",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                is UpdateState.Idle -> {
                    Button(
                        onClick = onCheckAgain,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HudCyan,
                            contentColor = HudDarkBg
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("check_updates_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Checar Atualizações no GitHub",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // GitHub repository link
            OutlinedButton(
                onClick = { onOpenUrl(GitHubUpdateChecker.GITHUB_REPO_URL) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_github_repo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Acessar Repositório Oficial no GitHub",
                    color = TextSecondary,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp
                )
            }
        }
    }
}
