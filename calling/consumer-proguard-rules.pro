
## AVS and WebRTC things.
-keep class org.webrtc.** { *; }
-keep class com.waz.call.FlowManager { *; }
-keep class com.waz.avs.VideoRenderer { *; }
-keep class com.waz.call.CaptureDevice { *; }
-keep class com.waz.media.manager.** { *; }
-keep class com.waz.service.call.** { *; }

# Avs SoundLink
-keep class com.waz.soundlink.SoundLinkAPI { *; }
## JNA classes for calling v3
-keep class * extends com.sun.jna.** { *; }
-keep class com.sun.jna.** { *; }
