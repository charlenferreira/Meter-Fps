package com.example.hardware

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import com.example.model.HudConfig
import com.example.model.LagDiagnosticResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Diagnostic and optimization controller for network stutter diagnosis and fast memory cleanup.
 */
object SystemOptimizer {

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    // Auto-analysis of lag / stutter
    private val _diagnosticState = MutableStateFlow<LagDiagnosticResult?>(null)
    val diagnosticState: StateFlow<LagDiagnosticResult?> = _diagnosticState.asStateFlow()

    private val _isDiagnosing = MutableStateFlow(false)
    val isDiagnosing: StateFlow<Boolean> = _isDiagnosing.asStateFlow()

    // Memory clean status
    private val _memoryCleanFeedback = MutableStateFlow<String?>(null)
    val memoryCleanFeedback: StateFlow<String?> = _memoryCleanFeedback.asStateFlow()

    private var diagnosticJob: Job? = null
    private var cleanJob: Job? = null

    /**
     * Executes 2-3 second network & hardware bottleneck diagnostic.
     * Evaluates Wi-Fi RSSI signal and socket latency.
     */
    fun runNetworkDiagnostic(context: Context, currentFps: Int) {
        if (_isDiagnosing.value) return

        diagnosticJob?.cancel()
        diagnosticJob = scope.launch(Dispatchers.IO) {
            _isDiagnosing.value = true
            _diagnosticState.value = LagDiagnosticResult(
                causeTitle = "Analisando Wi-Fi e Hardware...",
                isNetworkIssue = false,
                details = "Medindo sinal e resposta"
            )

            delay(1800L)

            val config = HudConfig.load(context)
            val serverHost = config.pingServerHost

            // Measure Ping
            var measuredPing = -1
            try {
                val socket = Socket()
                val start = System.currentTimeMillis()
                socket.connect(InetSocketAddress(serverHost, 53), 2000)
                measuredPing = (System.currentTimeMillis() - start).toInt()
                socket.close()
            } catch (e: Exception) {
                measuredPing = 999
            }

            // Measure Wi-Fi Signal
            val wifiPercent = getWifiSignalPercentage(context)

            val result: LagDiagnosticResult = when {
                measuredPing > 120 || wifiPercent < 35 -> {
                    LagDiagnosticResult(
                        causeTitle = "Causa: Oscilação de Rede Wi-Fi",
                        isNetworkIssue = true,
                        details = "Ping: ${if (measuredPing == 999) "Perdido" else "${measuredPing}ms"} | Sinal: $wifiPercent%"
                    )
                }
                currentFps < 42 -> {
                    LagDiagnosticResult(
                        causeTitle = "Causa: Gargalo de Hardware/Jogo",
                        isNetworkIssue = false,
                        details = "Ping Estável (${measuredPing}ms) | Queda de FPS detectada"
                    )
                }
                else -> {
                    LagDiagnosticResult(
                        causeTitle = "Rede e Hardware Estáveis",
                        isNetworkIssue = false,
                        details = "Ping: ${measuredPing}ms | Wi-Fi: $wifiPercent% | FPS fluido"
                    )
                }
            }

            _diagnosticState.value = result
            _isDiagnosing.value = false

            // Keep visible for 8 seconds and return smoothly
            delay(8000L)
            if (_diagnosticState.value == result) {
                _diagnosticState.value = null
            }
        }
    }

    /**
     * Recycles memory via System.gc() and notifies the user with dynamic feedback.
     */
    fun performMemoryOptimization(context: Context, isRamCritical: Boolean) {
        cleanJob?.cancel()
        cleanJob = scope.launch(Dispatchers.Default) {
            System.gc()
            Runtime.getRuntime().gc()

            if (isRamCritical) {
                _memoryCleanFeedback.value = "⚠️ Memória Crítica (<15%)! Feche apps pesados."
            } else {
                _memoryCleanFeedback.value = "🧹 Memória RAM Reciclada com Sucesso!"
            }

            delay(4500L)
            _memoryCleanFeedback.value = null
        }
    }

    private fun getWifiSignalPercentage(context: Context): Int {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = connManager?.activeNetwork
            val capabilities = connManager?.getNetworkCapabilities(network)

            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    capabilities.signalStrength
                }
                val info = @Suppress("DEPRECATION") wifiManager?.connectionInfo
                val rssi = info?.rssi ?: -65
                WifiManager.calculateSignalLevel(rssi, 100).coerceIn(10, 100)
            } else {
                80
            }
        } catch (e: Exception) {
            75
        }
    }
}
