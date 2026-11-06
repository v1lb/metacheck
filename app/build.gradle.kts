import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Секреты подписи лежат в keystore.properties (в .gitignore) или в переменных окружения CI.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.example.metacheck"
    compileSdk = 36

    defaultConfig {
        // Постоянный идентификатор приложения в Google Play: после первой загрузки его нельзя изменить.
        applicationId = "dev.metacheck.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 20602
        versionName = "2.6.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Ключ MapTiler для карты (аудит, п. 10). Пусто -> тайлы OpenStreetMap по Tile Usage Policy.
        // Задаётся в ~/.gradle/gradle.properties (METACHECK_MAPTILER_KEY=...) или секретом CI, не в репозитории.
        val mapTilerKey = (project.findProperty("METACHECK_MAPTILER_KEY") as String?)
            ?: System.getenv("METACHECK_MAPTILER_KEY")
            ?: ""
        resValue("string", "maptiler_key", mapTilerKey)
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("storeFile", "METACHECK_STORE_FILE")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("storePassword", "METACHECK_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "METACHECK_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "METACHECK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // ВАЖНО: release-APK без подписи Android НЕ устанавливает («Приложение не установлено»).
            // Есть свой ключ (keystore.properties / переменные окружения) -> подписываем им (для Google Play).
            // Нет ключа -> подписываем стандартным debug-ключом, чтобы APK можно было поставить на телефон.
            // [ТРЕБУЕТСЯ ВЫ] Для Google Play нужен собственный ключ загрузки (см. RELEASE_TODO.md).
            // AAB без своего ключа не собирается: см. защиту в конце файла.
            signingConfig = if (signingValue("storeFile", "METACHECK_STORE_FILE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // Языки приложения для системных настроек Android 13+ (Приложения → MetaCheck → Язык).
    // Список берётся из res/values-* , язык res/values задан в res/resources.properties.
    androidResources {
        generateLocaleConfig = true
    }

    bundle {
        // Язык выбирается внутри приложения, поэтому все языки должны быть в установленном APK.
        // Со split Google Play ставит только язык устройства: выбор «Русский» на английском телефоне
        // молча показывал бы английский текст. Строки весят килобайты, разбиение не нужно.
        language { enableSplit = false }
        density { enableSplit = true }
        abi { enableSplit = true }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Kotlin 2.2+: блок kotlinOptions в android {} удалён (ошибка сборки), настройки компилятора здесь.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Версии зависимостей: gradle/libs.versions.toml.
dependencies {
    implementation(libs.osmdroid)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.billing)
    implementation(libs.play.review)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)

    testImplementation(libs.junit)
    // Настоящий org.json для JVM-тестов (в android.jar только заглушки).
    testImplementation(libs.org.json)
}

// Защита от случайной публикации: AAB для Google Play нельзя собрать с debug-ключом.
// [ТРЕБУЕТСЯ ВЫ] Создайте ключ загрузки и секреты CI (METACHECK_*), см. RELEASE_TODO.md.
// Для установки на свой телефон используйте assembleRelease, ему собственный ключ не нужен.
gradle.taskGraph.whenReady {
    val needsRealKey = allTasks.any { it.name == "bundleRelease" }
    if (needsRealKey && signingValue("storeFile", "METACHECK_STORE_FILE") == null) {
        throw GradleException(
            "bundleRelease требует собственный ключ загрузки (keystore.properties или METACHECK_STORE_FILE). " +
                "Debug-ключ для Google Play не подходит. См. RELEASE_TODO.md.",
        )
    }
}
