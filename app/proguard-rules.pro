# ============================================
# AkRamtcp — ProGuard Rules (Heavy Obfuscation)
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

# ===== Native methods =====
-keepclasseswithmembernames class * {
    native <methods>;
}

# ===== Data classes بتاعة Gson =====
-keep class com.mossa.pro.PacketInfo { *; }
-keep class com.mossa.pro.LoginResponse { *; }
-keep class com.mossa.pro.UsersListResponse { *; }
-keep class com.mossa.pro.User { *; }
-keep class com.mossa.pro.GenericResponse { *; }
-keep class com.mossa.pro.CreateUserResponse { *; }
-keep class com.mossa.pro.LogEntry { *; }
-keep class com.mossa.pro.LogsResponse { *; }

# ===== Gson general =====
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ===== OkHttp =====
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ===== Kotlin =====
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ===== Coroutines =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ===== AndroidX =====
-dontwarn androidx.**

# ============================================
# OBFUSCATION — التشويش
# ============================================

# إعادة تعبئة الكلاسات
-repackageclasses ''

# السماح بتعديل الوصول
-allowaccessmodification

# إخفاء اسم الملف المصدر
-renamesourcefileattribute SourceFile

# الاحتفاظ برقم السطر للـ crash reports
-keepattributes SourceFile,LineNumberTable

# ============================================
# إزالة الـ Logs في الإصدار النهائي
# ============================================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
    public static *** println(...);
}

# إزالة Log من الـ Kotlin
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkReturnedValueIsNotNull(...);
    public static void checkFieldIsNotNull(...);
    public static void checkParameterIsNotNull(...);
}

# ============================================
# تحسينات إضافية
# ============================================
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5

# إخفاء الأسماء الداخلية
-adaptclassstrings
-adaptresourcefilecontents **.xml

# ===== View Binding =====
-keep class com.mossa.pro.databinding.** { *; }
-keepclassmembers class com.mossa.pro.databinding.** {
    <init>(...);
    public static *** inflate(...);
    public static *** bind(...);
}

# ===== BuildConfig =====
-keep class com.mossa.pro.BuildConfig { *; }

# ===== R class =====
-keep class com.mossa.pro.R { *; }
-keep class com.mossa.pro.R$* { *; }
