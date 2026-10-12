# kotlinx.serialization: keep the generated serializers for the hub's JSON.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.rusty.puff.** {
    *** Companion;
}
-keepclasseswithmembers class com.rusty.puff.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.slf4j.**
