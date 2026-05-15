# Cloudinary uses Glide and Picasso optionally.
# Since we are using Coil, we can tell R8 to ignore these missing classes.
-dontwarn com.cloudinary.android.download.glide.**
-dontwarn com.cloudinary.android.download.picasso.**

# Ktor/Supabase - Ignore missing Java Management classes (not available on Android)
-dontwarn java.lang.management.**
-dontwarn javax.management.**

# Ensure Supabase/Ktor classes are handled correctly
-keepattributes Signature, InnerClasses, AnnotationDefault, EnclosingMethod
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }

# Serialization
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
