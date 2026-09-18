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
        Log.i(TAG, "init: user=$currentUsername")
    }

    fun isLoggedIn(): Boolean {
        val t = currentToken
        if (t.isNullOrEmpty()) {
            Log.w(TAG, "isLoggedIn=false")
            return false
        }
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

    /**
     * يمسح التوكن الحالي — لكن **بيحفظ** الـ creds للـ auto-login
     */
    fun logout() {
        Log.i(TAG, "logout() — keeping saved creds for auto-login")
        currentUsername = null
        currentToken = null
        SecurePrefs.remove(AuthConfig.PREF_TOKEN)
        SecurePrefs.remove(AuthConfig.PREF_TOKEN_EXP)
        SecurePrefs.remove(AuthConfig.PREF_USERNAME)
        // ↑ متشيلش "saved_user" و "saved_pass"
    }

    /**
     * يمسح كل حاجة — logout نهائي (المستخدم اختار)
     */
    fun logoutComplete() {
        Log.i(TAG, "logoutComplete() — removing all")
        logout()
        SecurePrefs.remove("saved_user")
        SecurePrefs.remove("saved_pass")
    }
}
