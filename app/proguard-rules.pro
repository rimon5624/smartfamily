# 2.2: the release APK is shrunk and obfuscated with R8 (free), so the app's code is not readable from the APK.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, SourceFile, LineNumberTable
-renamesourcefileattribute SF

# JNI (offline transcription): native methods are looked up by class and method name
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
-keep class com.rimon.smartfamily.smart.LocalWhisper { *; }

# SQLCipher (encrypted database) is called from native code
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**

# kotlinx.serialization: keep generated serializers of the app's classes
-keepclassmembers class com.rimon.smartfamily.** { *** Companion; }
-keepclasseswithmembers class com.rimon.smartfamily.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.rimon.smartfamily.**$$serializer { *; }

# Room entities / DAOs (generated code refers to them by name)
-keep class com.rimon.smartfamily.data.db.** { *; }

# WorkManager workers, widgets, services are created by name
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
-keep class com.rimon.smartfamily.widget.** { *; }

# Price files: FTP client and XML parser
-keep class org.apache.commons.net.** { *; }
-dontwarn org.apache.commons.net.**
-dontwarn org.xmlpull.v1.**
-keep class org.xmlpull.** { *; }

# Tink / crypto, ML Kit, Firebase bring their own rules; quiet optional references
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
