# Keep kotlinx.serialization generated serializers used by the core module.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.englishteacher.** {
    kotlinx.serialization.KSerializer serializer(...);
}
