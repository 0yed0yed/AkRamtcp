package com.mossa.pro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicBoolean

class ProxyService : Service() {

    companion object {
        const val TAG = "AkRamtcp"
        const val ACTION_START = "com.mossa.pro.START"
        const val ACTION_STOP = "com.mossa.pro.STOP"
        const val CHANNEL_ID = "akramtcp_service"
        const val NOTIF_ID = 1

        @Volatile var isRunning = false
            private set

        var packetListener: ((PacketInfo) -> Unit)? = null

        @Volatile private var serverRef: Socks5Server? = null

        fun updateKeys(key: IntArray, iv: IntArray) {
            serverRef?.setKeys(key, iv)
            Log.i(TAG, "Keys updated")
        }
    }

    private var server: Socks5Server? = null
    private val starting = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopServer()
        }
        // ✅ مهم: START_STICKY — يعيد تشغيل نفسه لو اتقتل
        return START_STICKY
    }

    private fun startServer() {
        if (isRunning || !starting.compareAndSet(false, true)) return

        startForegroundNotification()

        server = Socks5Server(Config.PROXY_PORT) { info ->
            if (!PacketTypes.NAMES.containsKey(info.type)) {
                return@Socks5Server
            }

            PacketRegistry.put(info)

            try {
                PacketStore.save(applicationContext, info)
            } catch (e: Exception) {
                Log.e(TAG, "save failed: ${e.message}")
            }

            packetListener?.invoke(info)
        }
        serverRef = server
        server?.start()
        isRunning = true
        starting.set(false)
        Log.i(TAG, "ProxyService started")
    }

    private fun stopServer() {
        server?.stop()
        server = null
        serverRef = null
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID, "AkRamtcp Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Sniffer running"
                setShowBadge(false)
            }
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(chan)
        }

        // Intent لفتح التطبيق عند الضغط على الإشعار
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else
            PendingIntent.FLAG_UPDATE_CURRENT
        val openPending = PendingIntent.getActivity(this, 0, openIntent, pendingFlags)

        // Intent لإيقاف الـ service من الإشعار
        val stopIntent = Intent(this, ProxyService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(this, 1, stopIntent, pendingFlags)

        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AkRamtcp")
            .setContentText("Sniffer running · port ${Config.PROXY_PORT}")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_media_pause, "STOP", stopPending)
            .build()

        startForeground(NOTIF_ID, notif)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // ✅ لما المستخدم يقفل التطبيق من الـ recents
        // بنسيب الـ service شغال
        Log.i(TAG, "onTaskRemoved — keeping service alive")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        // لو المستخدم عمل stop من الـ UI، نوقف
        // بس لو الـ system قتل الـ service، نسيبها ترجع
        if (!isRunning) {
            stopServer()
        }
        super.onDestroy()
    }
}
