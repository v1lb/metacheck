package com.example.metacheck.ui

/**
 * Уведомления о лицензиях открытого кода. Список должен совпадать с зависимостями в gradle/libs.versions.toml.
 * Названия библиотек и лицензий не переводятся; пояснения (вступление, данные карты) лежат в ресурсах.
 */
object LicenseNotices {
    const val LIBRARIES = """osmdroid 6.1.20: Apache License 2.0
AndroidX (Core KTX 1.17.0, AppCompat 1.7.1, Lifecycle 2.9.2, Activity 1.10.1, ExifInterface 1.4.1, DataStore 1.1.7): Apache License 2.0
Jetpack Compose (BOM 2025.08.00): Apache License 2.0
Kotlin Coroutines 1.10.2: Apache License 2.0
OkHttp 4.12.0 and Okio: Apache License 2.0
Google Play Billing Library 8.0.0: Android Software Development Kit License"""

    const val LICENSE_LINKS = """Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
Android SDK License: https://developer.android.com/studio/terms"""

    const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

    /** Полный текст окна лицензий; переводимые части передаются уже разрешёнными из ресурсов. */
    fun text(intro: String, mapData: String, mapTiles: String): String =
        listOf(intro, LIBRARIES, LICENSE_LINKS, "$mapData\n$OSM_COPYRIGHT_URL\n$mapTiles").joinToString("\n\n")
}
