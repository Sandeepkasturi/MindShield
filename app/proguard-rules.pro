# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# SLF4J logging bindings
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }
-keep class org.slf4j.impl.** { *; }

# Keep SLF4J static logger binder
-keep class org.slf4j.impl.StaticLoggerBinder { *; }
-keep class org.slf4j.impl.StaticMDCBinder { *; }
-keep class org.slf4j.impl.StaticMarkerBinder { *; }

# Keep SLF4J logger factory
-keep class org.slf4j.LoggerFactory { *; }
-keep class org.slf4j.ILoggerFactory { *; }

# Keep SLF4J logger implementations
-keep class org.slf4j.Logger { *; }
-keep class org.slf4j.Marker { *; }
-keep class org.slf4j.MDC { *; }

# Keep Hilt/Dagger generated code and annotations
-keep class dagger.hilt.** { *; }
-keep class dagger.** { *; }
-dontwarn dagger.hilt.internal.**

# Keep Room entities, daos, and database
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Keep Kotlin data classes used for serialization (if any)
-keep class com.example.mindshield.data.model_isolate.** { *; }