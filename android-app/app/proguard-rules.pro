# SafePath AI — Production ProGuard & R8 Optimization Rules

# Keep all data models used with Room and Gson
-keep class com.safepath.ai.models.** { *; }
-keep class com.safepath.ai.network.dto.** { *; }

# Retrofit service interfaces and methods
-keep,allowobfuscation interface com.safepath.ai.network.** { *; }
-keepattributes *Annotation*
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Gson
-keepattributes Signature
-keepclassmembers enum * { *; }
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }

# Room Database & SQLite
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}

# AndroidX Navigation & Jetpack Lifecycle
-keepnames class androidx.navigation.fragment.NavHostFragment
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Google Play Services Location
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.location.**

# MapLibre Native SDK
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**

# AndroidX Security Crypto (EncryptedSharedPreferences)
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
