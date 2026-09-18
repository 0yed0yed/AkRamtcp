package com.mossa.pro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicBoolean

class ProxyService : Service() {

    companion object {
        const val TAG = "AkRamtcp"
        const val ACTION_START = "com.mossa.pro.START"
        const val ACTION_STOP = "com.mossa.pro.STOP"
        const val CHANNEL_ID = "akramtcp_foreground"
        const val NOTIF_ID = 1001

        @Volatile var isRunning = false
            private set

        var packetListener: ((PacketInfo) -> Unit)? = null

        @Volatile private var serverRef: Socks5Server? = null

        fun updateKeys(key: IntArray, iv: IntArray) {
            serverRef?.setKeys(key, iv)
        }
    }

    private var server: Socks5Server? = null
    private val starting = AtomicBoolean(false)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        // ✅ ضمان: channel + notification قبل أي حاجة
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                // ✅ لازم startForeground أول حاجة
                startForeground(NOTIF_ID, buildNotification())
                startServer()
            }
            ACTION_STOP -> stopServer()
            else -> {
                // لو اتشغل من غير action (boot/watchdog) → default = start
                startForeground(NOTIF_ID, buildNotification())
                startServer()
            }
        }
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val chan = NotificationChannel(
                    CHANNEL_ID,
                    "AkRamtcp Foreground",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Sniffer service"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                mgr.createNotificationChannel(chan)
                Log.i(TAG, "channel created")
            }
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_UPDATE_CURRENT
        val openPending = PendingIntent.getActivity(this, 0, openIntent, flags)

        val stopIntent = Intent(this, ProxyService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(this, 1, stopIntent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AkRamtcp يعمل")
            .setContentText("Sniffer · port ${Config.PROXY_PORT}")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_media_pause, "STOP", stopPending)
            .build()
    }

    private fun startServer() {
        if (isRunning || !starting.compareAndSet(false, true)) {
            Log.w(TAG, "already running or starting")
            return
        }

        acquireWakeLock()

        try {
            server = Socks5Server(Config.PROXY_PORT) { info ->
                if (!PacketTypes.NAMES.containsKey(info.type)) return@Socks5Server

                PacketRegistry.put(info)
                try { PacketStore.save(applicationContext, info) } catch (_: Exception) {}
                packetListener?.invoke(info)
            }
            serverRef = server
            server?.start()
            isRunning = true
            Log.i(TAG, "✓ ProxyService STARTED")
        } catch (e: Exception) {
            Log.e(TAG, "startServer: ${e.message}")
        }

        starting.set(false)
    }

    private fun stopServer() {
        Log.i(TAG, "stopServer")
        server?.stop()
        server = null
        serverRef = null
        isRunning = false
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AkRamtcp::SnifferWakeLock"
            )
            wakeLock?.acquire(24 * 60 * 60 * 1000L)
        } catch (e: Exception) {
            Log.e(TAG, "wakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "⚠ onTaskRemoved — app swiped away")
        // ✅ خدمة الـ foreground هتفضل شغالة — START_STICKY يعيد تشغيلها
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy — service killed")
        releaseWakeLock()
        super.onDestroy()
    }
}
