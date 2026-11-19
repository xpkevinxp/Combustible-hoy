# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class pe.combustiblehoy.**$$serializer { *; }
-keepclassmembers class pe.combustiblehoy.** {
    *** Companion;
}
-keepclasseswithmembers class pe.combustiblehoy.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Modelos del módulo core (JSON del pipeline) y enums usados por nombre.
-keep class pe.combustiblehoy.core.** { *; }
-keepclassmembers enum pe.combustiblehoy.** { *; }

# Workers instanciados por reflexión por WorkManager.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
