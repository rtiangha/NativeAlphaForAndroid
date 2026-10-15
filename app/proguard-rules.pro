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

# Rules for AdblockAndroid
-keep class io.github.edsuns.adfilter.** { *; }
# ---------------------------------------------------------------------------
# Readable stack traces for release builds (use with build/outputs/mapping/.../mapping.txt)
# ---------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Gson
# ---------------------------------------------------------------------------
# Generic type information is needed for `new TypeToken<ArrayList<WebApp>>() {}` (DataManager).
-keepattributes Signature,*Annotation*
-keep class * extends com.google.gson.reflect.TypeToken

# App settings are persisted with Gson, which uses field names as JSON keys
# (DataManager, WebAppDeserializer, GlobalSettingsDeserializer, DataVersionConverter).
# Field names in the model classes must therefore not be renamed or removed.
-keepclassmembers class com.cylonid.nativealpha.model.** {
    <fields>;
}
