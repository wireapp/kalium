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

# Keep all classes that extend UserSessionWorker for scheduled workers
-keep class * extends com.wire.kalium.logic.sync.DefaultWorker {
   <init>(...);
}

# WrapperWorkerFactory also reads worker class names from persisted WorkManager input data.
# Keep worker names stable across minified upgrades.
# See docs/minification-workmanager-compat.md in wire-android repository.
-keepnames class com.wire.kalium.logic.sync.PendingMessagesSenderWorker
-keepnames class com.wire.kalium.logic.sync.periodic.UserConfigSyncWorker
-keepnames class com.wire.kalium.logic.sync.periodic.UpdateApiVersionsWorker
-keepnames class com.wire.kalium.logic.sync.receiver.asset.AudioNormalizedLoudnessWorker

# protobuf
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
