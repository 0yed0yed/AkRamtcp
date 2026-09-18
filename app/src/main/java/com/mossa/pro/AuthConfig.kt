package com.mossa.pro

object AuthConfig {
    // عنوان السيرفر — نفس اللي عندك
    const val BASE_URL = "http://38.29.171.32:8090"

    // timeout الـ verify (ثواني)
    const val VERIFY_INTERVAL_SEC = 300          // كل 5 دقايق
    const val TOKEN_REFRESH_SEC = 50 * 60        // كل 50 دقيقة جدّد التوكن

    // Encrypted prefs
    const val PREFS_FILE = "akram_auth_prefs"
    const val PREF_USERNAME = "username"
    const val PREF_TOKEN = "auth_token"
    const val PREF_TOKEN_EXP = "token_expires"
    const val PREF_LAST_VERIFY = "last_verify_ok"
    const val PREF_BIOMETRIC = "biometric_enabled"
    const val PREF_DEVICE_BOUND = "device_bound"
}
