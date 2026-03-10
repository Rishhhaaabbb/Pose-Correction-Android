# Hilt generated components
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-dontwarn dagger.hilt.**

# Room — keep entity and DAO classes
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Include data module rules