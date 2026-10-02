package com.example.hardware

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import com.example.model.NetworkMetrics
import com.example.model.ProcessMetrics
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
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Robust hardware monitor providing real-time FPS, Thermal status, Memory, Network Ping,
 * and Top Processing App with safe fallbacks.
 */
class HardwareMonitor(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var pollingJob: Job? = null
    private var pingJob: Job? = null
    private var processJob: Job? = null

    // State flows
    private val _fpsMetrics = MutableStateFlow(FpsMetrics())
    val fpsMetrics: StateFlow<FpsMetrics> = _fpsMetrics.asStateFlow()

    private val _thermalMetrics = MutableStateFlow(ThermalMetrics())
    val thermalMetrics: StateFlow<ThermalMetrics> = _thermalMetrics.asStateFlow()

    private val _ramMetrics = MutableStateFlow(RamMetrics())
    val ramMetrics: StateFlow<RamMetrics> = _ramMetrics.asStateFlow()

    private val _networkMetrics = MutableStateFlow(NetworkMetrics())
    val networkMetrics: StateFlow<NetworkMetrics> = _networkMetrics.asStateFlow()

    private val _processMetrics = MutableStateFlow(ProcessMetrics())
    val processMetrics: StateFlow<ProcessMetrics> = _processMetrics.asStateFlow()

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val packageManager: PackageManager = context.packageManager

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

        // Background polling for RAM & Thermal
        pollingJob = scope.launch {
            while (isActive && isRunning) {
                updateRamMetrics()
                updateThermalMetrics()
                delay(1000L)
            }
        }

        // Background polling for Network Ping
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning) {
                measureNetworkPing()
                delay(2000L)
            }
        }

        // Background polling for Top Process
        processJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning) {
                detectTopProcess()
                delay(2000L)
            }
        }
    }

    fun stop() {
        isRunning = false
        pollingJob?.cancel()
        pollingJob = null
        pingJob?.cancel()
        pingJob = null
        processJob?.cancel()
        processJob = null

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
                // Ignore
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
            // Fallback
        }
    }

    private fun unregisterBatteryReceiver() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Ignore
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
        val cpuTemp = readCpuTemperatureDirect() ?: estimateCpuTemperature(latestBatteryTempCelsius, currentThermalStatus)

        _thermalMetrics.value = ThermalMetrics(
            statusLevel = currentThermalStatus,
            statusDescription = statusDesc,
            batteryTempCelsius = latestBatteryTempCelsius,
            estimatedCpuTempCelsius = cpuTemp,
            isThrottling = isThrottling
        )
    }

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
                // Ignore
            }
        }
        return null
    }

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
            // Ignore
        }
    }

    /**
     * Measures real socket connection latency (ping in ms) to public DNS servers.
     */
    private fun measureNetworkPing() {
        try {
            val activeNetwork = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)

            if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                _networkMetrics.value = NetworkMetrics(
                    pingMs = 0,
                    networkType = "Offline",
                    isConnected = false
                )
                return
            }

            val netType = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Rede Móvel"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Conectado"
            }

            val endpoints = listOf(
                Pair("8.8.8.8", 53),
                Pair("1.1.1.1", 53)
            )

            var bestPing = -1
            for ((host, port) in endpoints) {
                try {
                    val socket = Socket()
                    val start = System.currentTimeMillis()
                    socket.connect(InetSocketAddress(host, port), 1200)
                    val latency = (System.currentTimeMillis() - start).toInt()
                    socket.close()
                    if (latency in 1..2000) {
                        bestPing = latency
                        break
                    }
                } catch (e: Exception) {
                    // Try next endpoint
                }
            }

            if (bestPing > 0) {
                _networkMetrics.value = NetworkMetrics(
                    pingMs = bestPing,
                    networkType = netType,
                    isConnected = true
                )
            } else {
                _networkMetrics.value = NetworkMetrics(
                    pingMs = 0,
                    networkType = "$netType (Sem resposta)",
                    isConnected = true
                )
            }
        } catch (e: Exception) {
            _networkMetrics.value = NetworkMetrics(
                pingMs = 0,
                networkType = "Desconectado",
                isConnected = false
            )
        }
    }

    /**
     * Determines the top processing or foreground app with fallback.
     */
    private fun detectTopProcess() {
        try {
            var resolvedAppName: String? = null
            var packageName: String = ""

            // Method 1: Check UsageStatsManager if available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                val endTime = System.currentTimeMillis()
                val beginTime = endTime - (1000 * 60 * 2) // last 2 minutes
                val stats = usageStatsManager?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, beginTime, endTime)

                val recent = stats?.filter { it.packageName != context.packageName }
                    ?.maxByOrNull { it.lastTimeUsed }

                if (recent != null && recent.lastTimeUsed > 0) {
                    packageName = recent.packageName
                    resolvedAppName = getAppNameFromPackage(packageName)
                }
            }

            // Method 2: Running App Processes fallback
            if (resolvedAppName == null && activityManager != null) {
                val runningProcesses = activityManager.runningAppProcesses
                val topProc = runningProcesses?.firstOrNull {
                    it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                        it.processName != context.packageName
                } ?: runningProcesses?.firstOrNull { it.processName != context.packageName }

                if (topProc != null) {
                    packageName = topProc.processName
                    resolvedAppName = getAppNameFromPackage(packageName)
                }
            }

            val finalName = resolvedAppName ?: "Meter FPS (Ativo)"
            _processMetrics.value = ProcessMetrics(
                topAppName = finalName,
                packageName = packageName,
                details = "Em primeiro plano / Atividade principal"
            )
        } catch (e: Exception) {
            _processMetrics.value = ProcessMetrics(
                topAppName = "Sistema Android",
                packageName = "android",
                details = "Serviços em execução"
            )
        }
    }

    private fun getAppNameFromPackage(pkg: String): String {
        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(pkg, 0)
            }
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            // Simplify package name e.g. com.dts.freefireth -> Free Fire
            when {
                pkg.contains("freefire", ignoreCase = true) -> "Free Fire"
                pkg.contains("pubg", ignoreCase = true) -> "PUBG Mobile"
                pkg.contains("roblox", ignoreCase = true) -> "Roblox"
                pkg.contains("cod", ignoreCase = true) -> "Call of Duty"
                pkg.contains("chrome", ignoreCase = true) -> "Google Chrome"
                pkg.contains("youtube", ignoreCase = true) -> "YouTube"
                pkg.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
                pkg.contains("instagram", ignoreCase = true) -> "Instagram"
                pkg.contains("tiktok", ignoreCase = true) -> "TikTok"
                pkg.contains("genshin", ignoreCase = true) -> "Genshin Impact"
                else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            }
        }
    }

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
