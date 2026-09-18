package com.mossa.pro

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

object SecurePrefs {
    private const val TAG = "SecurePrefs"
    private var prefs: SharedPreferences? = null
    private val AES_KEY = "akram_secure_2026_key_v1".toByteArray()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(AuthConfig.PREFS_FILE, Context.MODE_PRIVATE)
        Log.i(TAG, "prefs ready")
    }

    private fun getPrefs(): SharedPreferences? {
        if (prefs == null) {
            Log.e(TAG, "prefs not initialized!")
        }
        return prefs
    }

    // ===== تشفير AES-CBC بسيط =====
    private fun deriveKey(): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(AES_KEY)   // 32 bytes
    }

    private fun encrypt(plain: String): String {
        return try {
            val key = SecretKeySpec(deriveKey().copyOf(16), "AES")  // 128-bit
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val enc = cipher.doFinal(plain.toByteArray())
            val combined = iv + enc
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "encrypt failed: ${e.message}")
            plain  // fallback
        }
    }

    private fun decrypt(cipherText: String): String? {
        return try {
            val combined = Base64.decode(cipherText, Base64.NO_WRAP)
            if (combined.size < 17) return cipherText
            val iv = combined.copyOfRange(0, 16)
            val enc = combined.copyOfRange(16, combined.size)
            val key = SecretKeySpec(deriveKey().copyOf(16), "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            String(cipher.doFinal(enc))
        } catch (e: Exception) {
            Log.e(TAG, "decrypt failed: ${e.message}")
            null
        }
    }

    // ===== API =====
    fun putString(key: String, value: String?) {
        val p = getPrefs() ?: return
        if (value == null) {
            p.edit().remove(key).apply()
        } else {
            p.edit().putString(key, encrypt(value)).apply()
            Log.i(TAG, "putString $key (encrypted)")
        }
    }

    fun getString(key: String, default: String? = null): String? {
        val p = getPrefs() ?: return default
        val enc = p.getString(key, null) ?: return default
        return decrypt(enc) ?: default
    }

    fun putLong(key: String, value: Long) {
        getPrefs()?.edit()?.putLong(key, value)?.apply()
    }

    fun getLong(key: String, default: Long = 0L): Long {
        return getPrefs()?.getLong(key, default) ?: default
    }

    fun putBool(key: String, value: Boolean) {
        getPrefs()?.edit()?.putBoolean(key, value)?.apply()
    }

    fun getBool(key: String, default: Boolean = false): Boolean {
        return getPrefs()?.getBoolean(key, default) ?: default
    }

    fun remove(key: String) {
        getPrefs()?.edit()?.remove(key)?.apply()
    }

    fun clear() {
        getPrefs()?.edit()?.clear()?.apply()
    }
}
