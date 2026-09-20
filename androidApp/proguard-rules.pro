# App-specific R8 rules. Library rules (kotlinx.serialization, Ktor/OkHttp, SQLDelight, Koin,
# Compose) ship as consumer rules inside their artifacts and are applied automatically.

# Keep source file names and line numbers so release stack traces stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Suppress warnings for optional JVM-only classes referenced by OkHttp/Ktor on Android.
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
