package com.mossa.pro

import android.content.Context
import android.util.Log

object AuthVerifier {
    private const val TAG = "AuthVerifier"

    var onRevoked: (() -> Unit)? = null
    var onNetworkError: (() -> Unit)? = null

    fun start(context: Context) {
        Log.i(TAG, "verifier disabled (debug mode)")
        // مؤقتاً معطل — عشان نتأكد إنه مش هو السبب
    }

    fun stop() {
        Log.i(TAG, "stop")
    }
}
