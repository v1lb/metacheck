package com.example.metacheck.ui

/** Уведомления о лицензиях открытого кода. Список должен совпадать с зависимостями в app/build.gradle.kts. */
object LicenseNotices {
    const val TEXT = """Open-source software used in MetaCheck

osmdroid 6.1.20: Apache License 2.0
AndroidX (Core KTX 1.13.1, Lifecycle 2.8.6, Activity 1.9.2, ExifInterface 1.3.7, DataStore 1.1.1): Apache License 2.0
Jetpack Compose (BOM 2024.09.03): Apache License 2.0
Kotlin Coroutines 1.8.1: Apache License 2.0
OkHttp 4.12.0 and Okio: Apache License 2.0
Google Play Billing Library 8.0.0: Android Software Development Kit License

Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
Android SDK License: https://developer.android.com/studio/terms

Map data (c) OpenStreetMap contributors, licensed under the Open Database License (ODbL) 1.0.
https://www.openstreetmap.org/copyright
Map tiles are served by OpenStreetMap Foundation servers."""
}
