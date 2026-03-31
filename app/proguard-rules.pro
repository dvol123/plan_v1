# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in Android SDK tools.
# For more details, see
#   https://developer.android.com/build/shrink-code

# ================================
# Project-specific rules
# ================================

# Keep Room entities
-keep class com.plan.app.data.local.entity.** { *; }

# Keep data classes for JSON serialization
-keep class com.plan.app.domain.model.** { *; }

# Keep export data classes
-keep class com.plan.app.domain.manager.**Data { *; }

# ================================
# Room Database
# ================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Room DAO interfaces
-keep interface * extends androidx.room.Dao
-keepclassmembers class * extends androidx.room.Dao {
    <methods>;
}

# ================================
# Hilt Dependency Injection
# ================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep,allowobfuscation,allowshrinking class com.plan.app.di.** { *; }
-keep,allowobfuscation,allowshrinking class com.plan.app.PlanApplication { *; }

# Hilt generated classes
-keep,allowobfuscation,allowshrinking class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ================================
# Gson JSON Serialization
# ================================
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep fields used in JSON
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ================================
# Kotlin & Coroutines
# ================================
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.CoroutineScope {
    public <methods>;
}

# ================================
# Compose UI
# ================================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Compose compiler
-keepclassmembers class androidx.compose.runtime.** { *; }

# ================================
# Coil Image Loading
# ================================
-keep class coil.** { *; }
-keep interface coil.** { *; }
-dontwarn coil.**

# ================================
# ExoPlayer (Media3)
# ================================
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ================================
# Navigation
# ================================
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# ================================
# Lifecycle & ViewModel
# ================================
-keep class androidx.lifecycle.** { *; }
-keepclassmembers class androidx.lifecycle.** { *; }

# ================================
# DataStore
# ================================
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ================================
# CameraX
# ================================
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ================================
# General Android
# ================================
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom views
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ================================
# Optimization settings
# ================================
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Optimization: remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}
