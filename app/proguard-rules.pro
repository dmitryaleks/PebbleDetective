# The logbook is kotlinx.serialization. The library ships consumer rules, but
# the failure mode if they ever fall short is nasty and release-only: the
# index file reads back empty and a child's pebbles silently vanish. These
# make it explicit rather than relying on that.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keep,includedescriptorclasses class com.pebbledetective.**$$serializer { *; }
-keepclassmembers class com.pebbledetective.** {
    *** Companion;
}
-keepclasseswithmembers class com.pebbledetective.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Enum entries are looked up by name when the logbook is parsed.
-keepclassmembers enum com.pebbledetective.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
