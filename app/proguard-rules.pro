# PENDO: Critical Optimization Fix
# Optimization sometimes breaks Coroutine state machines and Flow logic.
-dontoptimize

# Keep App and Initialization entry points
-keep class com.keith.modi.ModiApplication { *; }
-keep class com.keith.modi.Supabase { *; }
-keep class com.keith.modi.SecureSessionManager { *; }

# Kotlinx Datetime
-keep class kotlinx.datetime.** { *; }

# OkHttp / Okio
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep interface okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Ktor 3.x specific
-keep class io.ktor.** { *; }
-keep class io.netty.** { *; }
-dontwarn io.netty.**
-dontwarn io.ktor.**

# Cloudinary
-dontwarn com.cloudinary.android.download.glide.**
-dontwarn com.cloudinary.android.download.picasso.**

# Ktor / OkHttp / Okio
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.conscrypt.**

# Supabase
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep class kotlinx.serialization.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    *** Companion;
    *** $serializer;
}

# Keep all models and their members
-keep class com.keith.modi.models.** { *; }
-keepclassmembers class com.keith.modi.models.** { *; }

# Keep BuildConfig to ensure API keys are accessible
-keep class com.keith.modi.BuildConfig { *; }

# Compose and AndroidX
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }

# EncryptedSharedPreferences / Security Crypto
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Coil
-keep class coil.** { *; }

# Zxing (QR Code)
-keep class com.google.zxing.** { *; }

# Osmdroid (Maps)
-keep class org.osmdroid.** { *; }

# PENDO: General safety for Coroutines and Reflection
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-keep class kotlinx.coroutines.** { *; }
