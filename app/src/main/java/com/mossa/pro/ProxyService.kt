package com.mossa.pro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
        const val CHANNEL_ID = "akramtcp"

        @Volatile var isRunning = false
            private set

        var packetListener: ((PacketInfo) -> Unit)? = null

        // مرجع للـ server عشان نقدر نحدّث المفاتيح
        @Volatile private var serverRef: Socks5Server? = null

        fun updateKeys(key: IntArray, iv: IntArray) {
            serverRef?.setKeys(key, iv)
            Log.i(TAG, "Keys updated: key=${key.joinToString(",")}")
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
                CHANNEL_ID, "AkRamtcp", NotificationManager.IMPORTANCE_LOW
            )
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(chan)
        }
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AkRamtcp")
            .setContentText("Sniffer running · port ${Config.PROXY_PORT}")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        startForeground(1, notif)
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }
}
