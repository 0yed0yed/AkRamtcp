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
        const val CHANNEL_ID = "akramtcp_fg"
        const val NOTIF_ID = 1001

        @Volatile var isRunning = false
            private set

        var packetListener: ((PacketInfo) -> Unit)? = null

        @Volatile private var serverRef: Socks5Server? = null

        fun updateKeys(key: IntArray, iv: IntArray) {
            serverRef?.setKeys(key, iv)
            try {
                SecurePrefs.putString("active_key", key.joinToString(","))
                SecurePrefs.putString("active_iv", iv.joinToString(","))
                Log.i(TAG, "Keys updated + saved to prefs")
            } catch (e: Exception) {
                Log.e(TAG, "save keys: ${e.message}")
            }
        }
    }

    private var server: Socks5Server? = null
    private val starting = AtomicBoolean(false)
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var lastStartTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🔵 onCreate")
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i(TAG, "🟢 onStartCommand: $action")

        // 🔍 trace عشان نعرف مين اللي بعت الـ intent
        try {
            throw Exception("onStartCommand caller trace: $action")
        } catch (e: Exception) {
            Log.i(TAG, "stack trace:", e)
        }

        // ✅ لو STOP — نتجاهله لو جاي بسرعة من START
        if (action == ACTION_STOP) {
            val sinceStart = System.currentTimeMillis() - lastStartTime
            if (sinceStart < 3000 && lastStartTime > 0) {
                Log.w(TAG, "⚠️ STOP ignored — only ${sinceStart}ms after START (accidental)")
                return START_STICKY
            }
            Log.i(TAG, "🔴 STOP accepted (${sinceStart}ms after START)")
            stopServer()
            return START_NOT_STICKY
        }

        // ✅ خزّن وقت START
        if (action == ACTION_START) {
            lastStartTime = System.currentTimeMillis()
        }

        // ✅ startForeground مرة واحدة بس
        if (!isRunning) {
            try {
                startForeground(NOTIF_ID, buildNotification())
                Log.i(TAG, "✅ startForeground OK")
            } catch (e: Exception) {
                Log.e(TAG, "❌ startForeground: ${e.message}")
            }
            startServer()
        } else {
            Log.i(TAG, "already running — skip startForeground")
        }

        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val chan = NotificationChannel(
                    CHANNEL_ID,
                    "AkRamtcp Service",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                mgr.createNotificationChannel(chan)
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AkRamtcp يعمل")
            .setContentText("Proxy: ${Config.PROXY_HOST}:${Config.PROXY_PORT}")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPending)
            .build()
    }

    private fun startServer() {
        if (isRunning || !starting.compareAndSet(false, true)) return
        acquireWakeLock()
        try {
            server = Socks5Server(Config.PROXY_PORT) { info ->
                try {
                    if (!PacketTypes.NAMES.containsKey(info.type)) return@Socks5Server
                    PacketRegistry.put(info)
                    try { PacketStore.save(applicationContext, info) } catch (_: Exception) {}

                    // 📤 إرسال تلقائي للباكيتات المهمة
                    try { TelegramForwarder.maybeSend(info) } catch (_: Exception) {}

                    packetListener?.invoke(info)
                } catch (e: Exception) {
                    Log.e(TAG, "packet handler: ${e.message}")
                }
            }
            serverRef = server
            server?.start()
            isRunning = true
            Log.i(TAG, "✅ ProxyService RUNNING")
        } catch (e: Exception) {
            Log.e(TAG, "startServer: ${e.message}")
        }
        starting.set(false)
    }

    private fun stopServer() {
        Log.i(TAG, "🔴 stopServer")
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
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AkRamtcp::Wake")
            wakeLock?.acquire(24 * 60 * 60 * 1000L)
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "⚠ onTaskRemoved — service keeps running")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.e(TAG, "💀 onDestroy")
        releaseWakeLock()
        super.onDestroy()
    }
}
