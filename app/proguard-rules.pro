# 余额面板 ProGuard 规则（当前 release 未开启混淆，规则保留备用）

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
