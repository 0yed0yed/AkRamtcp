package com.mossa.pro

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object AuthVerifier {
    private const val TAG = "AuthVerifier"

    private var job: Job? = null
    private var scope: CoroutineScope? = null

    var onRevoked: (() -> Unit)? = null

    @Volatile private var running = false

    fun start(context: Context) {
        if (running) return
        running = true

        val appContext = context.applicationContext
        scope = CoroutineScope(Dispatchers.IO)

        job = scope?.launch {
            delay(30_000)   // 30 ثانية قبل أول verify
            val deviceId = DeviceId.get(appContext)

            while (isActive && running) {
                try {
                    val token = AuthManager.currentToken
                    if (token.isNullOrEmpty()) {
                        delay(60_000)
                        continue
                    }

                    val result = ApiClient.verify(token, deviceId)

                    when {
                        result.ok -> Log.i(TAG, "✓ verified")
                        result.errorCode == 401 -> {
                            Log.w(TAG, "401 — account gone")
                            invokeRevoked()
                            break
                        }
                        result.errorCode == 403 -> {
                            Log.w(TAG, "403 — revoked")
                            invokeRevoked()
                            break
                        }
                        else -> Log.w(TAG, "network: ${result.error}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "loop: ${e.message}")
                }

                delay(AuthConfig.VERIFY_INTERVAL_SEC * 1000L)
            }
        }
    }

    private suspend fun invokeRevoked() {
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
