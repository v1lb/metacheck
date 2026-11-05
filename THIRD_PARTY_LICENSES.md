# Third-party licenses

Versions follow `gradle/libs.versions.toml` (the app shows the same list: About → Open source licenses). The full list including transitive dependencies: `./gradlew :app:dependencies`.

| Component | Version | License |
|---|---|---|
| osmdroid-android | 6.1.20 | Apache License 2.0 |
| AndroidX Core KTX | 1.17.0 | Apache License 2.0 |
| AndroidX Lifecycle (runtime, viewmodel, compose) | 2.9.2 | Apache License 2.0 |
| AndroidX Activity Compose | 1.10.1 | Apache License 2.0 |
| AndroidX AppCompat (app language) | 1.7.1 | Apache License 2.0 |
| AndroidX ExifInterface | 1.4.1 | Apache License 2.0 |
| AndroidX DataStore Preferences | 1.1.7 | Apache License 2.0 |
| Jetpack Compose (BOM) | 2025.08.00 | Apache License 2.0 |
| Kotlin Coroutines | 1.10.2 | Apache License 2.0 |
| OkHttp (and Okio) | 4.12.0 | Apache License 2.0 |
| Google Play Billing Library | 8.0.0 | Android SDK license (Google), see https://developer.android.com/studio/terms |
| Google Play In-App Review | 2.0.1 | Android SDK license (Google), see https://developer.android.com/studio/terms |

Apache License 2.0 text: https://www.apache.org/licenses/LICENSE-2.0

## Map data
Map data © OpenStreetMap contributors, Open Database License 1.0.
Details: https://www.openstreetmap.org/copyright
Map tiles are loaded on user request from MapTiler (© MapTiler) when the build has a key, otherwise from the OpenStreetMap Foundation servers.
