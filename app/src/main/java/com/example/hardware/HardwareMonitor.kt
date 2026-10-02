package com.example.hardware

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.WindowManager
import com.example.model.DeviceHardwareInfo
import com.example.model.FpsMetrics
import com.example.model.RamMetrics
import com.example.model.ThermalMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Robust hardware monitor providing real-time FPS, Thermal status, and Memory metrics
 * with safe fallbacks across all Android versions.
 */
class HardwareMonitor(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var pollingJob: Job? = null

    // State flows
    private val _fpsMetrics = MutableStateFlow(FpsMetrics())
    val fpsMetrics: StateFlow<FpsMetrics> = _fpsMetrics.asStateFlow()

    private val _thermalMetrics = MutableStateFlow(ThermalMetrics())
    val thermalMetrics: StateFlow<ThermalMetrics> = _thermalMetrics.asStateFlow()

    private val _ramMetrics = MutableStateFlow(RamMetrics())
    val ramMetrics: StateFlow<RamMetrics> = _ramMetrics.asStateFlow()

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    private var isRunning = false
    private var currentRefreshRate = 60.0f

    // FPS calculation variables (Choreographer)
    private var frameCount = 0
    private var lastFpsTimestampNanos = 0L
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return

            if (lastFpsTimestampNanos == 0L) {
                lastFpsTimestampNanos = frameTimeNanos
            }

            val elapsedNanos = frameTimeNanos - lastFpsTimestampNanos
            frameCount++

            // Calculate FPS every 1000ms
            if (elapsedNanos >= 1_000_000_000L) {
                val measuredFps = ((frameCount * 1_000_000_000.0) / elapsedNanos).toInt()
                val safeFps = measuredFps.coerceIn(1, (currentRefreshRate * 1.5).toInt().coerceAtLeast(144))
                val frameTime = if (safeFps > 0) 1000f / safeFps else 16.6f

                _fpsMetrics.value = FpsMetrics(
                    fps = safeFps,
                    frameTimeMs = String.format(java.util.Locale.US, "%.1f", frameTime).toFloatOrNull() ?: 16.6f,
                    refreshRate = currentRefreshRate
                )

                frameCount = 0
                lastFpsTimestampNanos = frameTimeNanos
            }

            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // Battery Broadcast Receiver
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                updateBatteryAndThermalMetrics(intent)
            }
        }
    }

    // Thermal listener for Android 10+ (API 29+)
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var currentThermalStatus = 0

    fun start() {
        if (isRunning) return
        isRunning = true

        updateDisplayRefreshRate()
        registerThermalListener()
        registerBatteryReceiver()

        // Start Choreographer on Main Thread
        mainHandler.post {
            lastFpsTimestampNanos = 0L
            frameCount = 0
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }

        // Start background polling for RAM & periodic hardware refresh
        pollingJob = scope.launch {
            while (isActive && isRunning) {
                updateRamMetrics()
                updateThermalMetrics()
                delay(1000L)
            }
        }
    }

    fun stop() {
        isRunning = false
        pollingJob?.cancel()
        pollingJob = null

        mainHandler.post {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }

        unregisterBatteryReceiver()
        unregisterThermalListener()
    }

    private fun updateDisplayRefreshRate() {
        try {
            val rate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.refreshRate ?: 60f
            } else {
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay?.refreshRate ?: 60f
            }
            currentRefreshRate = if (rate > 0f) rate else 60.0f
        } catch (e: Exception) {
            currentRefreshRate = 60.0f
        }
    }

    private fun registerThermalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            try {
                thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
                    currentThermalStatus = status
                    updateThermalMetrics()
                }
                powerManager.addThermalStatusListener(context.mainExecutor, thermalListener!!)
                currentThermalStatus = powerManager.currentThermalStatus
            } catch (e: Exception) {
                currentThermalStatus = 0
            }
        }
    }

    private fun unregisterThermalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null && thermalListener != null) {
            try {
                powerManager.removeThermalStatusListener(thermalListener!!)
            } catch (e: Exception) {
                // Ignore during teardown
            }
            thermalListener = null
        }
    }

    private fun registerBatteryReceiver() {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val stickyIntent = context.registerReceiver(batteryReceiver, filter)
            if (stickyIntent != null) {
                updateBatteryAndThermalMetrics(stickyIntent)
            }
        } catch (e: Exception) {
            // Fallback default
        }
    }

    private fun unregisterBatteryReceiver() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }

    private var latestBatteryTempCelsius = 31.0f

    private fun updateBatteryAndThermalMetrics(batteryIntent: Intent) {
        val rawTemp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 310)
        latestBatteryTempCelsius = if (rawTemp > 0) rawTemp / 10.0f else 31.0f
        updateThermalMetrics()
    }

    private fun updateThermalMetrics() {
        val statusDesc = when (currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "Normal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Leve"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderado"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severo"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Crítico"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergência"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Desligamento"
            else -> "Normal"
        }

        val isThrottling = currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE

        // Read direct CPU temperature if permitted by hardware/SELinux; fallback cleanly if restricted
        val cpuTemp = readCpuTemperatureDirect() ?: estimateCpuTemperature(latestBatteryTempCelsius, currentThermalStatus)

        _thermalMetrics.value = ThermalMetrics(
            statusLevel = currentThermalStatus,
            statusDescription = statusDesc,
            batteryTempCelsius = latestBatteryTempCelsius,
            estimatedCpuTempCelsius = cpuTemp,
            isThrottling = isThrottling
        )
    }

    /**
     * Attempts to read CPU temperature directly from known Linux thermal sysfs zones.
     * Wrapped with try-catch so it never fails or throws exceptions when restricted.
     */
    private fun readCpuTemperatureDirect(): Float? {
        val thermalPaths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp"
        )

        for (path in thermalPaths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val content = file.readText().trim()
                    val rawVal = content.toDoubleOrNull() ?: continue
                    val temp = if (rawVal > 1000.0) (rawVal / 1000.0).toFloat() else rawVal.toFloat()
                    if (temp in 20.0f..110.0f) {
                        return String.format(java.util.Locale.US, "%.1f", temp).toFloatOrNull() ?: temp
                    }
                }
            } catch (e: Exception) {
                // Ignore and proceed to fallback
            }
        }
        return null
    }

    /**
     * Reliable official API fallback combining battery temperature and PowerManager thermal status.
     * Guaranteed to never return null.
     */
    private fun estimateCpuTemperature(batteryTemp: Float, thermalStatus: Int): Float {
        val thermalOffset = when (thermalStatus) {
            PowerManager.THERMAL_STATUS_LIGHT -> 5.5f
            PowerManager.THERMAL_STATUS_MODERATE -> 10.0f
            PowerManager.THERMAL_STATUS_SEVERE -> 15.0f
            PowerManager.THERMAL_STATUS_CRITICAL -> 20.0f
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> 25.0f
            else -> 4.0f
        }
        val estimated = batteryTemp + thermalOffset
        return String.format(java.util.Locale.US, "%.1f", estimated).toFloatOrNull() ?: estimated
    }

    private fun updateRamMetrics() {
        if (activityManager == null) return
        try {
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)

            val total = memInfo.totalMem
            val avail = memInfo.availMem
            val used = (total - avail).coerceAtLeast(0L)
            val percent = if (total > 0L) {
                ((used.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
            } else 0

            _ramMetrics.value = RamMetrics(
                usedBytes = used,
                totalBytes = total,
                availBytes = avail,
                usagePercentage = percent
            )
        } catch (e: Exception) {
            // Maintain existing metrics on exception
        }
    }

    /**
     * Retrieves static device hardware specs for the dashboard.
     */
    fun getDeviceHardwareInfo(): DeviceHardwareInfo {
        var totalRamGb = 0f
        try {
            val memInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memInfo)
            totalRamGb = memInfo.totalMem / (1024f * 1024f * 1024f)
        } catch (e: Exception) {
            // Ignore
        }

        var resolution = "Desconhecida"
        try {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getRealMetrics(metrics)
            resolution = "${metrics.widthPixels} x ${metrics.heightPixels} px (${metrics.densityDpi} dpi)"
        } catch (e: Exception) {
            // Ignore
        }

        return DeviceHardwareInfo(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            androidVersion = "Android ${Build.VERSION.RELEASE}",
            apiLevel = Build.VERSION.SDK_INT,
            displayResolution = resolution,
            defaultRefreshRate = currentRefreshRate,
            totalRamGb = totalRamGb
        )
    }
}
