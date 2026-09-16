# kotlinx.serialization generates serializers reflectively, so R8 has to be told
# to keep them. Without these the release build compiles and then fails at run
# time on the first decode -- which is the question catalog, at launch.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# The app's own @Serializable types: the generated $$serializer classes, their
# Companion objects, and the serializer() factories.
-keep,includedescriptorclasses class com.rsm.eztrivia.**$$serializer { *; }
-keepclassmembers class com.rsm.eztrivia.** {
    *** Companion;
}
-keepclasseswithmembers class com.rsm.eztrivia.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Enum entries are looked up by name when decoding wire values.
-keepclassmembers enum com.rsm.eztrivia.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Room and WorkManager instantiate these implementations reflectively. With the
# AGP/R8 full-mode release shrinker, the class can survive while its no-arg
# constructor is removed. EZ Trivia v2 crashed before MainActivity because
# WorkDatabase_Impl had no retained constructor. Keep Room database
# implementations and WorkManager InputMergers constructible.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.work.InputMerger { *; }
