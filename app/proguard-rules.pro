# SPDX-License-Identifier: Apache-2.0
# =============================================================================
# R8/ProGuard rules for the release build of Glyph Dialer (:app).
# Applied on top of proguard-android-optimize.txt (see build.gradle.kts).
# =============================================================================

# Keep source file + line numbers for readable crash reports; hide the original
# source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# Needed by reflection-based libraries (Room/serialization/Hilt generics).
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,RuntimeVisible*Annotations

# -----------------------------------------------------------------------------
# Hilt / Dagger
# -----------------------------------------------------------------------------
# Hilt ships its own consumer rules, but keep generated components/entry points and
# the Application defensively.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.* { *; }
-keep,allowobfuscation @dagger.hilt.android.AndroidEntryPoint class *
-keep class com.glyphdialer.GlyphDialerApp { *; }
# HiltWorkerFactory-injected workers.
-keep class * extends androidx.work.ListenableWorker { *; }

# -----------------------------------------------------------------------------
# Room
# -----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# -----------------------------------------------------------------------------
# kotlinx.serialization
# -----------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
# Keep generated serializers for @Serializable classes (used by the webrtc signaling
# DTOs + any serialized settings/transcript models).
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers,allowshrinking class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep,includedescriptorclasses class kotlinx.serialization.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    static **$Companion Companion;
    static *** serializer(...);
}
-dontwarn kotlinx.serialization.**

# -----------------------------------------------------------------------------
# WebRTC (org.webrtc.** — native JNI bindings must not be renamed/stripped)
# -----------------------------------------------------------------------------
-keep class org.webrtc.** { *; }
-keep interface org.webrtc.** { *; }
-dontwarn org.webrtc.**
# Stream WebRTC wrapper.
-keep class io.getstream.webrtc.** { *; }
-dontwarn io.getstream.webrtc.**

# -----------------------------------------------------------------------------
# Nothing Glyph GDK / Matrix SDK (com.nothing.ketchum.**)
# Loaded reflectively/by the OEM SDK; absent on non-Nothing devices (§9/§17).
# -----------------------------------------------------------------------------
-keep class com.nothing.ketchum.** { *; }
-keep interface com.nothing.ketchum.** { *; }
-dontwarn com.nothing.ketchum.**

# -----------------------------------------------------------------------------
# Ktor (webrtc signaling client) + Coroutines + OkHttp
# -----------------------------------------------------------------------------
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-dontwarn kotlinx.coroutines.**
-dontwarn okhttp3.**
-dontwarn okio.**

# -----------------------------------------------------------------------------
# Compose + Timber are largely covered by their own/AGP rules; nothing extra needed.
# -----------------------------------------------------------------------------

# SLF4J (used by some WebRTC/third-party libs)
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }
