# kotlinx.serialization: keep the generated serializers for the hub's JSON.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.rusty.puff.** {
    *** Companion;
}
-keepclasseswithmembers class com.rusty.puff.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.slf4j.**
# WorkManager's Room database is created by reflection (R8 full mode dropped its constructor: crash at startup).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
# Glance makes the widget buttons' callbacks by reflection.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
