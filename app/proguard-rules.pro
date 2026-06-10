# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.yhx.notices.** {
    *** Companion;
}
-keepclasseswithmembers class com.yhx.notices.** {
    kotlinx.serialization.KSerializer serializer(...);
}
