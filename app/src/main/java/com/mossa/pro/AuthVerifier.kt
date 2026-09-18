package com.mossa.pro

import android.content.Context
import android.util.Log

object AuthVerifier {
    private const val TAG = "AuthVerifier"

    var onRevoked: (() -> Unit)? = null
    var onNetworkError: (() -> Unit)? = null

    fun start(context: Context) {
        Log.i(TAG, "▶ verifier DISABLED (testing)")
    }

    fun stop() {
        Log.i(TAG, "■ verifier stop")
    }
}
