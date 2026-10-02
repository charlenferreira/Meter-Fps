package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.hardware.HardwareMonitor
import com.example.model.HudConfig
import com.example.service.OverlayLifecycleOwner
import com.example.ui.FloatingHudView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service hosting the floating overlay HUD widget in WindowManager.
 */
class FloatingHudService : Service() {

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var overlayOwner: OverlayLifecycleOwner? = null
    private var hardwareMonitor: HardwareMonitor? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true

        // Ensure notification channel exists
        createNotificationChannel()

        // Start Foreground immediately
        startForegroundNotification()

        // Initialize Hardware Monitoring engine
        hardwareMonitor = HardwareMonitor(this).apply {
            start()
        }

        // Initialize Configuration
        _currentConfig.value = HudConfig.load(this)

        // Show floating HUD overlay view if permission is granted
        if (Settings.canDrawOverlays(this)) {
            setupOverlayWindow()
        } else {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_CONFIG -> {
                _currentConfig.value = HudConfig.load(this)
            }
        }
        return START_STICKY
    }

    private fun setupOverlayWindow() {
        if (composeView != null) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val config = _currentConfig.value
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = config.posX
            y = config.posY
        }
        windowLayoutParams = params

        overlayOwner = OverlayLifecycleOwner().apply {
            onCreate()
            onStart()
        }

        composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setViewTreeLifecycleOwner(overlayOwner)
            setViewTreeSavedStateRegistryOwner(overlayOwner)
            setViewTreeViewModelStoreOwner(overlayOwner)

            setContent {
                val hudConfig by _currentConfig.collectAsState()
                val fps by hardwareMonitor!!.fpsMetrics.collectAsState()
                val thermal by hardwareMonitor!!.thermalMetrics.collectAsState()
                val ram by hardwareMonitor!!.ramMetrics.collectAsState()

                FloatingHudView(
                    config = hudConfig,
                    fpsMetrics = fps,
                    thermalMetrics = thermal,
                    ramMetrics = ram,
                    onDrag = { dx, dy ->
                        windowLayoutParams?.let { lp ->
                            lp.x = (lp.x + dx.toInt()).coerceAtLeast(0)
                            lp.y = (lp.y + dy.toInt()).coerceAtLeast(0)
                            windowManager?.updateViewLayout(this@apply, lp)
                            HudConfig.updatePosition(this@FloatingHudService, lp.x, lp.y)
                        }
                    },
                    onToggleCompact = {
                        val updated = hudConfig.copy(compactMode = !hudConfig.compactMode)
                        _currentConfig.value = updated
                        HudConfig.save(this@FloatingHudService, updated)
                    },
                    onClose = {
                        stopSelf()
                    }
                )
            }
        }

        try {
            windowManager?.addView(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.service_notification_text)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, FloatingHudService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                getString(R.string.service_notification_stop_action),
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        _isRunning.value = false

        hardwareMonitor?.stop()
        hardwareMonitor = null

        serviceScope.cancel()

        overlayOwner?.onDestroy()
        overlayOwner = null

        if (composeView != null && windowManager != null) {
            try {
                windowManager?.removeView(composeView)
            } catch (e: Exception) {
                // View might already be detached
            }
            composeView = null
        }

        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "hud_monitor_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_STOP_SERVICE = "com.example.ACTION_STOP_SERVICE"
        const val ACTION_UPDATE_CONFIG = "com.example.ACTION_UPDATE_CONFIG"

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        private val _currentConfig = MutableStateFlow(HudConfig())
        val currentConfig = _currentConfig.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, FloatingHudService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingHudService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun updateConfig(context: Context, newConfig: HudConfig) {
            _currentConfig.value = newConfig
            HudConfig.save(context, newConfig)
            val intent = Intent(context, FloatingHudService::class.java).apply {
                action = ACTION_UPDATE_CONFIG
            }
            context.startService(intent)
        }
    }
}
