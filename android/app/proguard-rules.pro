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
