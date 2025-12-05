# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signature for reflection and serialization
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ================================
# iText PDF Library Rules
# ================================

# Don't warn about optional dependencies that iText can work without
-dontwarn com.itextpdf.bouncycastle.**
-dontwarn com.itextpdf.bouncycastlefips.**
-dontwarn org.slf4j.**
-dontwarn javax.xml.crypto.**

# Sharpen - iText's C# to Java conversion tool (not needed at runtime)
-dontwarn sharpen.**

# Jackson JSON library - optional iText dependency
-dontwarn com.fasterxml.jackson.**

# Java AWT - desktop graphics library not available on Android
-dontwarn java.awt.**
-dontwarn javax.imageio.**

# Keep all iText classes - they use reflection extensively
-keep class com.itextpdf.** { *; }
-keepclassmembers class com.itextpdf.** { *; }

# Keep iText factory classes that are loaded dynamically
-keep class * implements com.itextpdf.commons.bouncycastle.IBouncyCastleFactory { *; }

# ================================
# Kotlinx Serialization Rules
# ================================

# Keep all @Serializable classes and their members
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @Serializable and @Polymorphic annotations
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all serializable data classes
-keep @kotlinx.serialization.Serializable class ** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep custom serializers
-keep class * implements kotlinx.serialization.KSerializer { *; }

# ================================
# App-Specific Data Classes
# ================================

# Keep all data models that are serialized/deserialized
-keep class com.github.bfollon.farmaciasdeguardiaensegovia.data.** { *; }
-keepclassmembers class com.github.bfollon.farmaciasdeguardiaensegovia.data.** { *; }

# ================================
# PDF Parser Strategy Classes
# ================================

# Keep all PDF parser implementations - loaded dynamically via strategy pattern
-keep class com.github.bfollon.farmaciasdeguardiaensegovia.services.pdfparsing.** { *; }
-keepclassmembers class com.github.bfollon.farmaciasdeguardiaensegovia.services.pdfparsing.** { *; }

# ================================
# Location Services (Google Play Services)
# ================================

# Keep Google Play Services Location classes
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# ================================
# OkHttp and Okio
# ================================

# OkHttp platform used only on JVM and when Conscrypt and other security providers are available
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ================================
# Jetpack Compose
# ================================

# Keep Compose classes that use reflection
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# ================================
# Kotlin Reflection
# ================================

# Keep Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }

# ================================
# General Rules
# ================================

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}