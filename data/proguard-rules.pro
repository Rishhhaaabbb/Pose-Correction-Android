# MediaPipe Tasks — keep all native bridge classes
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.tasks.** { *; }
-dontwarn com.google.mediapipe.**

# Keep TFLite runtime used internally by MediaPipe
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Serialization — keep @Serializable annotated classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}