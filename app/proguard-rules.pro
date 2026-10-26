# R8 в режиме full: обфускация, оптимизация и удаление мёртвого кода.
# Рефлексии и JNI в проекте нет, поэтому keep-правил почти не нужно.

# Номера строк нужны для расшифровки крашей в Play Console (загрузи mapping.txt вместе с AAB).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Отладочные логи не попадают в release.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Если в Kotlin останутся проверки, их тоже убираем из release.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(java.lang.Object, java.lang.String);
    public static void checkNotNullParameter(java.lang.Object, java.lang.String);
}

# OkHttp: необязательные TLS-провайдеры отсутствуют на Android, предупреждения не нужны.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
