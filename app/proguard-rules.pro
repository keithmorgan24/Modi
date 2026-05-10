# Modi Production Proguard Rules

# PENDO: Shield Kotlin Serialization
-keepattributes *Annotation*, EnclosingMethod, InnerClasses, Signature
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName *;
}
-keep class kotlinx.serialization.json.** { *; }

# PENDO: Protect Supabase & Ktor (Networking)
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-keep class okhttp3.** { *; }
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# PENDO: Protect Cloudinary & Coil (Images)
-keep class com.cloudinary.** { *; }
-keep class coil.** { *; }

# PENDO: Protect Google Maps
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.android.libraries.maps.** { *; }

# General safety
-dontwarn io.github.jan.supabase.**
-dontwarn io.ktor.**
-dontwarn kotlinx.serialization.**
