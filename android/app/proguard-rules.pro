# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /opt/android-sdk/tools/proguard/proguard-android.txt

# Keep LX200 protocol classes
-keep class com.skygoto.app.data.protocol.** { *; }
-keep class com.skygoto.app.domain.model.** { *; }
