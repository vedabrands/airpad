package com.airpad.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.airpad.app.MainActivity

/**
 * Android Foreground Service managing PowerManager WakeLock and WifiManager WifiLock.
 * Ensures uninterrupted low-latency UDP streams, sensor processing, and gamepad inputs
 * when the app is backgrounded or when the phone screen is turned off.
 */
class AirPadService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    companion object {
        const val CHANNEL_ID = "airpad_controller_channel"
        const val CHANNEL_NAME = "AirPad Active Controller"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.airpad.app.action.START_SERVICE"
        const val ACTION_STOP = "com.airpad.app.action.STOP_SERVICE"
        const val ACTION_UPDATE_STATUS = "com.airpad.app.action.UPDATE_STATUS"
        const val ACTION_DISCONNECT_REQUEST = "com.airpad.app.action.DISCONNECT_REQUEST"

        const val EXTRA_HOST_NAME = "extra_host_name"
        const val EXTRA_STATUS_TEXT = "extra_status_text"

        fun startService(context: Context, hostName: String) {
            val intent = Intent(context, AirPadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_HOST_NAME, hostName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateStatus(context: Context, statusText: String) {
            val intent = Intent(context, AirPadService::class.java).apply {
                action = ACTION_UPDATE_STATUS
                putExtra(EXTRA_STATUS_TEXT, statusText)
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AirPadService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquirePowerLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val hostName = intent.getStringExtra(EXTRA_HOST_NAME) ?: "Desktop PC"
                val notification = buildNotification("Connected to $hostName", "Active air-mouse & gamepad stream")
                startForeground(NOTIFICATION_ID, notification)
            }

            ACTION_UPDATE_STATUS -> {
                val statusText = intent.getStringExtra(EXTRA_STATUS_TEXT) ?: "Active Connection"
                val notification = buildNotification("AirPad Controller", statusText)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }

            ACTION_STOP -> {
                releasePowerLocks()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun buildNotification(title: String, content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_dialer)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing Wi-Fi controller connection state and sensor stream activity."
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION")
    private fun acquirePowerLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AirPad::ControllerStreamWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // Max 12 hours safety timeout
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiLockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }

            wifiLock = wifiManager.createWifiLock(wifiLockMode, "AirPad::WifiLowLatencyLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releasePowerLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null

            wifiLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePowerLocks()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
