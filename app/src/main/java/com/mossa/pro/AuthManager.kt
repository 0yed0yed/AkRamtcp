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
    }

    fun isLoggedIn(): Boolean {
        val t = currentToken
        val exp = SecurePrefs.getLong(AuthConfig.PREF_TOKEN_EXP, 0L)
        if (t.isNullOrEmpty()) return false
        // لو التوكن لسه صالح (بهامش 5 دقايق)
        val now = System.currentTimeMillis() / 1000
        return exp > (now + 300)
    }

    fun saveSession(username: String, token: String, expiresInSec: Long) {
        currentUsername = username
        currentToken = token
        val exp = (System.currentTimeMillis() / 1000) + expiresInSec

        SecurePrefs.putString(AuthConfig.PREF_USERNAME, username)
        SecurePrefs.putString(AuthConfig.PREF_TOKEN, token)
        SecurePrefs.putLong(AuthConfig.PREF_TOKEN_EXP, exp)
        SecurePrefs.putLong(AuthConfig.PREF_LAST_VERIFY, System.currentTimeMillis() / 1000)
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
        currentUsername = null
        currentToken = null
        SecurePrefs.remove(AuthConfig.PREF_TOKEN)
        SecurePrefs.remove(AuthConfig.PREF_TOKEN_EXP)
        SecurePrefs.remove(AuthConfig.PREF_USERNAME)
        Log.i(TAG, "Logged out")
    }
}
