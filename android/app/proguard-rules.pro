# FREYA ProGuard/R8 rules.
# minifyEnabled is currently false for both debug and release builds, so these
# rules are dormant. They are kept for future release builds.

# kotlinx.serialization: keep generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class ai.freya.app.** {
    *** Companion;
}
-keepclasseswithmembers class ai.freya.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit + OkHttp.
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions

# Room.
-keep class * extends androidx.room.RoomDatabase
