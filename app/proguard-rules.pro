# ProGuard and R8 rules for TripFinance release builds
# Note: R8/ProGuard is for code shrinking and optimization, not a data security layer.
# Data security is enforced by SQLCipher encryption, Android KeyStore, transactions, and SQLite triggers.

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room Database persistence: Keep database class and entity fields/constructors for SQLite mapping
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keepclassmembers class com.example.data.entity.** {
    <fields>;
    <init>(...);
}

# SQLCipher native JNI bridge
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**

# Kotlin Coroutines & Serialization
-keepclassmembers class kotlinx.coroutines.** { *; }

# Compose and AndroidX
-dontwarn androidx.compose.**

