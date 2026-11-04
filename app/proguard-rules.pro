# Momo Subtitle ProGuard rules.
# R8 is disabled for Milestone 0.1; these rules must be validated before enabling it
# (docs/RELEASE_CHECKLIST.md).

# JNI resolves native methods by exact name on this class.
-keep class com.momosubtitle.core.speech.WhisperJni { *; }

# kotlinx-serialization generated serializers.
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers class com.momosubtitle.** {
    *** Companion;
}
-keepclasseswithmembers class com.momosubtitle.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.momosubtitle.**$$serializer { *; }
