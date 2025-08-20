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


-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepattributes InnerClasses # Needed for `getDeclaredClasses`.

# Kotlin serialization looks up the generated serializer classes through a function on companion
# objects. The companions are looked up reflectively so we need to explicitly keep these functions.
-keepclasseswithmembers class **.*$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
# If a companion has the serializer function, keep the companion field on the original type so that
# the reflective lookup succeeds.
-if class **.*$Companion {
  kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class <1>.<2> {
  <1>.<2>$Companion Companion;
}

# protobuf
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# Ktor
-dontwarn io.ktor.client.network.sockets.SocketTimeoutException
-dontwarn io.ktor.client.network.sockets.TimeoutExceptionsCommonKt
-dontwarn io.ktor.client.plugins.HttpTimeout$HttpTimeoutCapabilityConfiguration
-dontwarn io.ktor.client.plugins.HttpTimeout$Plugin
-dontwarn io.ktor.client.plugins.HttpTimeout
-dontwarn io.ktor.util.InternalAPI
-dontwarn io.ktor.utils.io.ByteReadChannelJVMKt
-dontwarn io.ktor.utils.io.CoroutinesKt
-dontwarn io.ktor.utils.io.core.ByteBuffersKt
-dontwarn io.ktor.utils.io.core.BytePacketBuilder
-dontwarn io.ktor.utils.io.core.ByteReadPacket$Companion
-dontwarn io.ktor.utils.io.core.ByteReadPacket
-dontwarn io.ktor.utils.io.core.CloseableJVMKt
-dontwarn io.ktor.utils.io.core.Input
-dontwarn io.ktor.utils.io.core.InputArraysKt
-dontwarn io.ktor.utils.io.core.InputPrimitivesKt
-dontwarn io.ktor.utils.io.core.Output
-dontwarn io.ktor.utils.io.core.OutputPrimitivesKt
-dontwarn io.ktor.utils.io.core.PreviewKt
-dontwarn okhttp3.internal.Util
