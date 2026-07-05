# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# --- Protocol Buffers (generated lite messages used for the Android TV Remote Protocol) ---
# The lite runtime relies on reflection-free codegen, but we still keep the generated
# message classes intact to avoid subtle (de)serialization bugs after shrinking.
-keep class com.batin.tvremote.proto.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# --- Hilt / Dagger ---
-dontwarn com.google.errorprone.annotations.**

# --- Kotlin coroutines ---
-dontwarn kotlinx.coroutines.**

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep line numbers for readable stack traces in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
