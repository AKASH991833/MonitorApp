-keep class org.webrtc.** { *; }
-keep class com.google.firebase.** { *; }
-keep class com.familyconnect.app.data.local.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class * extends android.app.Service

# Timber
-keep class timber.log.** { *; }
-dontwarn timber.log.**
