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
        return !t.isNullOrEmpty()
    }

    fun saveSession(username: String, token: String, expiresInSec: Long, accountExpiresAt: Long = 0L) {
        currentUsername = username
        currentToken = token

        val tokenExp = (System.currentTimeMillis() / 1000) + expiresInSec
        SecurePrefs.putString(AuthConfig.PREF_USERNAME, username)
        SecurePrefs.putString(AuthConfig.PREF_TOKEN, token)
        SecurePrefs.putLong(AuthConfig.PREF_TOKEN_EXP, tokenExp)
        SecurePrefs.putLong(AuthConfig.PREF_LAST_VERIFY, System.currentTimeMillis() / 1000)

        if (accountExpiresAt > 0) {
            SecurePrefs.putLong("account_expires_at", accountExpiresAt)
        }
    }

    fun getAccountExpiry(): Long {
        return SecurePrefs.getLong("account_expires_at", 0L)
    }

    fun getTokenExpiry(): Long {
        return SecurePrefs.getLong(AuthConfig.PREF_TOKEN_EXP, 0L)
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

    fun logout() {
        currentUsername = null
        currentToken = null
        SecurePrefs.remove(AuthConfig.PREF_TOKEN)
        SecurePrefs.remove(AuthConfig.PREF_TOKEN_EXP)
        SecurePrefs.remove(AuthConfig.PREF_USERNAME)
    }

    fun logoutComplete() {
        logout()
        SecurePrefs.remove("saved_user")
        SecurePrefs.remove("saved_pass")
        SecurePrefs.remove("account_expires_at")
    }
}
