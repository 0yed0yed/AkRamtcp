package com.mossa.pro

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * يتحقق من التوكن كل N ثانية مع السيرفر.
 * لو السيرفر رفض → onRevoked() يتنادى.
 */
object AuthVerifier {
    private const val TAG = "AuthVerifier"

    private var job: Job? = null
    private var scope: CoroutineScope? = null

    var onRevoked: (() -> Unit)? = null
    var onNetworkError: (() -> Unit)? = null

    @Volatile private var running = false

    fun start(context: Context) {
        if (running) return
        running = true

        val appContext = context.applicationContext
        scope = CoroutineScope(Dispatchers.IO)
        job = scope?.launch {
            val deviceId = DeviceId.get(appContext)

            while (isActive && running) {
                try {
                    val token = AuthManager.currentToken
                    if (token.isNullOrEmpty()) {
                        delay(10_000)
                        continue
                    }

                    val result = ApiClient.verify(token, deviceId)

                    when {
                        result.ok -> {
                            AuthManager.markVerified()
                            Log.i(TAG, "✓ verified")
                        }
                        result.errorCode == 401 -> {
                            // token منتهي — عايز login جديد
                            Log.w(TAG, "token expired")
                            invokeRevoked()
                            break
                        }
                        result.errorCode == 403 -> {
                            // revoke / device mismatch
                            Log.w(TAG, "revoked: ${result.error}")
                            invokeRevoked()
                            break
                        }
                        else -> {
                            // network error — نكمل لحد ما يشتغل
                            Log.w(TAG, "network issue: ${result.error}")
                            onNetworkError?.invoke()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "verify loop: ${e.message}")
                }

                delay(AuthConfig.VERIFY_INTERVAL_SEC * 1000L)
            }
        }
    }

    private suspend fun invokeRevoked() {
        // نرجع للـ main thread
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            onRevoked?.invoke()
        }
    }

    fun stop() {
        running = false
        job?.cancel()
        job = null
        scope = null
    }
}
