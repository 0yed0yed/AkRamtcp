# ============================================
# AkRamtcp — ProGuard Rules (Aggressive)
# ============================================

# ===== Activities — لازم يفضلوا لأنهم في Manifest =====
-keep class com.mossa.pro.MainActivity { *; }
-keep class com.mossa.pro.LoginActivity { *; }
-keep class com.mossa.pro.HexActivity { *; }
-keep class com.mossa.pro.PacketDetailActivity { *; }

# ===== Services =====
-keep class com.mossa.pro.ProxyService { *; }
-keep class com.mossa.pro.FloatingWindowService { *; }

# ===== Broadcast Receivers =====
-keep class com.mossa.pro.BootReceiver { *; }

# ===== Native =====
-keepclasseswithmembernames class * {
    native <methods>;
}

# ===== Gson — بس Data classes =====
-keep class com.mossa.pro.PacketInfo { *; }
-keep class com.mossa.pro.LoginResponse { *; }
-keep class com.mossa.pro.UsersListResponse { *; }
-keep class com.mossa.pro.User { *; }
-keep class com.mossa.pro.GenericResponse { *; }
-keep class com.mossa.pro.CreateUserResponse { *; }
-keep class com.mossa.pro.LogEntry { *; }
-keep class com.mossa.pro.LogsResponse { *; }

# ===== Gson reflection =====
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ===== OkHttp =====
-dontwarn okhttp3.**
-dontwarn okio.**

# ===== Kotlin =====
-dontwarn kotlin.**
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ===== View Binding =====
-keep class com.mossa.pro.databinding.** { *; }

# ===== BuildConfig =====
-keep class com.mossa.pro.BuildConfig { *; }

# ============================================
# OBFUSCATION — التشويش الأقصى
# ============================================

# إعادة تعبئة الكلاسات — بس مش في الباكدج الرئيسي
-repackageclasses 'x'

# السماح بتعديل الوصول
-allowaccessmodification

# إخفاء اسم الملف المصدر
-renamesourcefileattribute X

# الاحتفاظ بمعلومات الـ crash
-keepattributes SourceFile,LineNumberTable

# ============================================
# حذف الـ Logs
# ============================================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ============================================
# تحسينات
# ============================================
-optimizationpasses 5
-adaptclassstrings
