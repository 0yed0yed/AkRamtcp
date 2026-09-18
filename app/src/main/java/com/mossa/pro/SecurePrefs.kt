package com.mossa.pro

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            prefs = EncryptedSharedPreferences.create(
                context,
                AuthConfig.PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // fallback: prefs عادية (لو الـ keystore فشل)
            prefs = context.getSharedPreferences(AuthConfig.PREFS_FILE, Context.MODE_PRIVATE)
        }
    }

    fun putString(key: String, value: String?) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    fun getString(key: String, default: String? = null): String? {
        return prefs?.getString(key, default)
    }

    fun putLong(key: String, value: Long) {
        prefs?.edit()?.putLong(key, value)?.apply()
    }

    fun getLong(key: String, default: Long = 0L): Long {
        return prefs?.getLong(key, default) ?: default
    }

    fun putBool(key: String, value: Boolean) {
        prefs?.edit()?.putBoolean(key, value)?.apply()
    }

    fun getBool(key: String, default: Boolean = false): Boolean {
        return prefs?.getBoolean(key, default) ?: default
    }

    fun remove(key: String) {
        prefs?.edit()?.remove(key)?.apply()
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}
