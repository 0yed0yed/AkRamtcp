package com.mossa.pro

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

object DeviceId {

    @SuppressLint("HardwareIds")
    fun get(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        val raw = buildString {
            append(androidId)
            append("|")
            append(Build.BRAND)
            append("|")
            append(Build.MODEL)
            append("|")
            append(Build.DEVICE)
            append("|")
            append(Build.MANUFACTURER)
            append("|")
            append(Build.FINGERPRINT)
        }

        return sha256(raw)
    }

    fun model(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray())
        val sb = StringBuilder()
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }
}
