package com.example.model

/**
 * Real-time hardware performance metrics for FPS, Thermal, Memory, Ping, and Top Process.
 */
data class FpsMetrics(
    val fps: Int = 60,
    val frameTimeMs: Float = 16.6f,
    val refreshRate: Float = 60.0f
)

data class ThermalMetrics(
    val statusLevel: Int = 0, // PowerManager.THERMAL_STATUS_NONE = 0
    val statusDescription: String = "Normal",
    val batteryTempCelsius: Float = 32.0f,
    val estimatedCpuTempCelsius: Float = 36.5f,
    val isThrottling: Boolean = false
)

data class RamMetrics(
    val usedBytes: Long = 0L,
    val totalBytes: Long = 1L,
    val availBytes: Long = 0L,
    val usagePercentage: Int = 0
) {
    val usedGb: Float get() = usedBytes / (1024f * 1024f * 1024f)
    val totalGb: Float get() = totalBytes / (1024f * 1024f * 1024f)
    val availGb: Float get() = availBytes / (1024f * 1024f * 1024f)
    val usedMb: Long get() = usedBytes / (1024L * 1024L)
    val totalMb: Long get() = totalBytes / (1024L * 1024L)
    val availMb: Long get() = availBytes / (1024L * 1024L)
    val isCritical: Boolean get() = (totalBytes > 0L && (availBytes.toFloat() / totalBytes.toFloat()) < 0.15f)
}

data class NetworkMetrics(
    val pingMs: Int = 0,
    val networkType: String = "Verificando...",
    val isConnected: Boolean = true,
    val wifiSignalPercent: Int = 85
)

data class ProcessMetrics(
    val topAppName: String = "Identificando...",
    val packageName: String = "",
    val details: String = "Processamento em primeiro plano"
)

data class LagDiagnosticResult(
    val causeTitle: String,
    val isNetworkIssue: Boolean,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class DeviceHardwareInfo(
    val manufacturer: String = "",
    val model: String = "",
    val androidVersion: String = "",
    val apiLevel: Int = 0,
    val displayResolution: String = "",
    val defaultRefreshRate: Float = 60f,
    val totalRamGb: Float = 0f
)
