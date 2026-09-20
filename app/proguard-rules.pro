# ProGuard and R8 rules for TripFinance release builds

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room Database persistence
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class com.example.data.entity.** { *; }
-keep class com.example.data.dao.** { *; }
-keep class com.example.data.db.** { *; }

# Domain models, data transfer objects, and financial logic
-keep class com.example.domain.model.** { *; }
-keep class com.example.ui.viewmodel.** { *; }

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

# Compose and AndroidX
-dontwarn androidx.compose.**
