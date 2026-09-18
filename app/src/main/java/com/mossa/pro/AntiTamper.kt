package com.mossa.pro

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.util.Log
import java.io.File
import java.security.MessageDigest

object AntiTamper {
    private const val TAG = "AntiTamper"

    /**
     * يتحقق إن التطبيق مش معدّل ولا بيتم debug.
     * @return null لو OK، أو رسالة الخطأ
     */
    fun check(context: Context): String? {
        // 1. Debugger check
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
            return "Debugger detected"
        }

        // 2. Root check (soft — warning بس)
        if (isRooted()) {
            Log.w(TAG, "Device appears rooted")
            // مش بنرفض — بس نلوج
        }

        // 3. Signature check — مش بنتحقق من قيمة محددة (لأن debug key)
        // في الإصدار النهائي، هنقارن بالـ hash بتاع التوقيع الحقيقي
        // دلوقتي بنسيبها كـ placeholder

        return null
    }

    private fun isRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/magisk/.core/bin/su"
        )
        paths.forEach {
            try {
                if (File(it).exists()) return true
            } catch (_: Exception) {}
        }
        return false
    }

    fun getSignatureHash(context: Context): String? {
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }
            val sig = signatures?.firstOrNull() ?: return null
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(sig.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
