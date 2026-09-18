package com.mossa.pro

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class FloatingWindowService : Service() {

    companion object {
        const val TAG = "AkRamtcp-Float"
        const val ACTION_SHOW = "com.mossa.pro.FLOAT_SHOW"
        const val ACTION_HIDE = "com.mossa.pro.FLOAT_HIDE"

        @Volatile var isVisible = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val counter = AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> show()
            ACTION_HIDE -> hide()
        }
        return START_NOT_STICKY
    }

    private fun show() {
        if (isVisible) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_window, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 300
        }

        // Counter listener
        ProxyService.packetListener = { info ->
            counter.incrementAndGet()
            floatingView?.post {
                val countView = floatingView?.findViewById<TextView>(R.id.fCount)
                val lastView = floatingView?.findViewById<TextView>(R.id.fLast)
                countView?.text = counter.get().toString()
                if (PacketTypes.NAMES.containsKey(info.type)) {
                    lastView?.text = "${info.type} · ${info.direction.take(1)}"
                }
            }
        }

        // Close btn
        floatingView?.findViewById<View>(R.id.fClose)?.setOnClickListener {
            hide()
        }

        // Drag
        setupDrag()

        try {
            windowManager?.addView(floatingView, params)
            isVisible = true
            updateStatus()
        } catch (e: Exception) {
            Log.e(TAG, "addView: ${e.message}")
        }
    }

    private fun setupDrag() {
        val root = floatingView?.findViewById<View>(R.id.floatRoot) ?: return
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params?.x = initialX + (event.rawX - touchX).toInt()
                    params?.y = initialY + (event.rawY - touchY).toInt()
                    floatingView?.let {
                        try { windowManager?.updateViewLayout(it, params) } catch (_: Exception) {}
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun updateStatus() {
        floatingView?.post {
            val dot = floatingView?.findViewById<View>(R.id.fDot)
            dot?.background = ContextCompat.getDrawable(
                this,
                if (ProxyService.isRunning) R.drawable.dot_green else R.drawable.dot_red
            )
        }
    }

    private fun hide() {
        try {
            floatingView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        floatingView = null
        isVisible = false
        stopSelf()
    }

    override fun onDestroy() {
        hide()
        super.onDestroy()
    }
}
