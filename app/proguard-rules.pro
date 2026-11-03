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

# Проверки Kotlin на null (Intrinsics.checkNotNull*) НЕ вырезаем (аудит, п. 23).
# Без них null из Java/Android API тихо уходит дальше и роняет приложение в случайном месте,
# а отчёт о сбое в Play Console указывает не туда. Выигрыш в размере — единицы килобайт.

# OkHttp: необязательные TLS-провайдеры отсутствуют на Android, предупреждения не нужны.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# osmdroid (карта OpenStreetMap): подбирает тайловые источники и обработчики по именам классов.
# Без этих правил R8 может обрезать нужные классы, и в release-сборке карта останется пустой.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Ожидающие операции студии (PendingWrite, OverwriteOp, MetadataPatch) хранятся в SavedStateHandle через
# java.io.Serializable и должны пережить смерть процесса. readResolve нужен объекту-одиночке OverwriteOp.Strip.
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
