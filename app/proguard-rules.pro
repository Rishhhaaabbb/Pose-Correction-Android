# Hilt generated components
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-dontwarn dagger.hilt.**

# Room — keep entity and DAO classes
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Ignore optional compile-time and logging binder classes from transitive libs.
-dontwarn javax.lang.model.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Include data module rules
-include ../data/proguard-rules.pro

# Strip Log.d/v/i/e calls in release builds (PII safety)
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static int w(...);
}