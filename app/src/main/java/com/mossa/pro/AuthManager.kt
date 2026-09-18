package com.mossa.pro

import android.content.Context
import android.util.Log

object AuthManager {
    private const val TAG = "AuthManager"

    @Volatile var currentUsername: String? = null
    @Volatile var currentToken: String? = null

    fun init(context: Context) {
        SecurePrefs.init(context)
        currentUsername = SecurePrefs.getString(AuthConfig.PREF_USERNAME)
        currentToken = SecurePrefs.getString(AuthConfig.PREF_TOKEN)
        Log.i(TAG, "init: user=$currentUsername token=${currentToken?.take(20)}")
    }

    fun isLoggedIn(): Boolean {
        val t = currentToken
        if (t.isNullOrEmpty()) {
            Log.w(TAG, "isLoggedIn=false (no token)")
            return false
        }
        Log.i(TAG, "isLoggedIn=true")
        return true
    }

    fun saveSession(username: String, token: String, expiresInSec: Long) {
        currentUsername = username
        currentToken = token
        val exp = (System.currentTimeMillis() / 1000) + expiresInSec

        SecurePrefs.putString(AuthConfig.PREF_USERNAME, username)
        SecurePrefs.putString(AuthConfig.PREF_TOKEN, token)
        SecurePrefs.putLong(AuthConfig.PREF_TOKEN_EXP, exp)
        SecurePrefs.putLong(AuthConfig.PREF_LAST_VERIFY, System.currentTimeMillis() / 1000)

        // تحقق فوري إن الحفظ شغال
        val checkUser = SecurePrefs.getString(AuthConfig.PREF_USERNAME)
        val checkToken = SecurePrefs.getString(AuthConfig.PREF_TOKEN)
        Log.i(TAG, "saved. verify: user=$checkUser token=${checkToken?.take(20)}")
    }

    fun updateToken(token: String, expiresInSec: Long) {
        currentToken = token
        val exp = (System.currentTimeMillis() / 1000) + expiresInSec
        SecurePrefs.putString(AuthConfig.PREF_TOKEN, token)
        SecurePrefs.putLong(AuthConfig.PREF_TOKEN_EXP, exp)
    }

    fun markVerified() {
        SecurePrefs.putLong(AuthConfig.PREF_LAST_VERIFY, System.currentTimeMillis() / 1000)
    }

    fun lastVerifyAgoSec(): Long {
        val last = SecurePrefs.getLong(AuthConfig.PREF_LAST_VERIFY, 0L)
        if (last == 0L) return Long.MAX_VALUE
        return (System.currentTimeMillis() / 1000) - last
    }

    fun logout() {
        Log.i(TAG, "logout() called")
        currentUsername = null
        currentToken = null
        SecurePrefs.remove(AuthConfig.PREF_TOKEN)
        SecurePrefs.remove(AuthConfig.PREF_TOKEN_EXP)
        SecurePrefs.remove(AuthConfig.PREF_USERNAME)
    }
}
