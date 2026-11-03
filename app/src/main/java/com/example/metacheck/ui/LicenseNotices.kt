package com.example.metacheck.ui

/** Уведомления о лицензиях открытого кода. Список должен совпадать с зависимостями в app/build.gradle.kts. */
object LicenseNotices {
    const val TEXT = """Open-source software used in MetaCheck

osmdroid 6.1.20: Apache License 2.0
AndroidX (Core KTX 1.17.0, Lifecycle 2.9.2, Activity 1.10.1, ExifInterface 1.4.1, DataStore 1.1.7): Apache License 2.0
Jetpack Compose (BOM 2025.08.00): Apache License 2.0
Kotlin Coroutines 1.10.2: Apache License 2.0
OkHttp 4.12.0 and Okio: Apache License 2.0
Google Play Billing Library 8.0.0: Android Software Development Kit License

Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
Android SDK License: https://developer.android.com/studio/terms

Map data (c) OpenStreetMap contributors, licensed under the Open Database License (ODbL) 1.0.
https://www.openstreetmap.org/copyright
Map tiles are served by MapTiler (© MapTiler) when configured, otherwise by OpenStreetMap Foundation servers."""
}
