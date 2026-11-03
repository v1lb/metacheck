# MetaCheck 2.4 — Metadata Studio

Android package: `dev.metacheck.app` (`.debug` for debug builds). VersionCode 10, VersionName 1.0.0.

## Delivered

- **High-contrast Cyber Grid/Neon Intelligence:** keeps dark/light Material 3 palettes and on-surface text across the interface.
- **Welcome / Language / Theme:** three-step onboarding with animated transitions, RU/EN and theme preview, system defaults on Skip. The preference store is now **Preferences DataStore**, automatically migrating the v2.3 `ui_preferences_v3` SharedPreferences. Preferences are written serially, and theme/language changes flow to Compose.
- **Metadata Viewer:** unchanged built-in searchable EXIF/XMP/PNG/GPS catalog.
- **Metadata Studio:** available on the dashboard and from a freshly analyzed photograph's report. Provides single-photo clean copies, metadata editing, a world-coordinate grid picker, Android date/time pickers, and batch cleaning.
- **Real EXIF editing (JPEG):** GPS deletion or change, date taken/digitized, make/model, artist, copyright, image description, ISO, aperture, and exposure. Null/empty form fields do not alter their original EXIF values. The writer preserves JPEG compressed pixel data but discards standalone standard/extended XMP APP1, Photoshop IPTC APP13, and JPEG comment segments to avoid conflicting values. It does **not** provide editing arbitrary XMP/IPTC namespace fields.
- **Metadata removal:** decode/re-encode into JPEG (original JPEG) or PNG (other decodable formats), without passing along recognized EXIF/XMP/IPTC/GPS metadata chunks. Result is checked for JPEG APP1/APP13/COM or PNG text/eXIf/time/color-profile chunks. The original stays untouched by default. Re-encoding JPEG is lossy; color profiles and C2PA signatures may be lost. Animated images can lose their animation; images over 16 megapixels are downsampled.
- **Optional overwrite of JPEG or PNG:** user confirms and chooses a **separate backup destination** using Android's document picker. The original is replaced only after the backup is saved and its bytes verified. Replacement is read back and hash-verified. Failure triggers a restoration attempt from the backup. Some storage providers cannot guarantee atomic writes; in this case do not use overwrite. Keep the external backup.
- **Batch cleaning Pro:** selection of up to 50 photos, selecting a destination folder, progress and individual failure messages, unique destination names. Pro entitlement checked in both UI and domain use case. Free single-image cleaning and JPEG EXIF editing remain free.
- **Keystore history, Google Play Billing, optional API, JSON/PDF/CSV:** preserved. Billing is real only when the product `pro_lifetime` is configured on Google Play Console and distribution supports purchasing it.
- **No broad media-storage permissions:** uses Android Storage Access Framework for read, new copies, overwrite backup and folder selection. No photo pixels uploaded by the new metadata functionality.

## Compatibility and boundaries

| Operation | File format | Pixel fidelity | Extra notes |
| --- | --- | --- | --- |
| Inspect | existing supported inputs | unchanged | limited to the application's recognized fields |
| Strip to copy | decodable supported inputs | re-encoded | JPEG output for JPEG input, PNG otherwise |
| Edit EXIF to copy | JPEG | JPEG entropy data preserved except edits to container | may remove XMP/IPTC APP markers |
| Strip overwrite | JPEG/PNG | re-encoded | requires readable separate backup and writable source provider |
| Edit EXIF overwrite | JPEG | JPEG entropy data preserved | backup and user confirmation required |
| Batch strip | decodable inputs, up to 50 | re-encoded | Pro; separate output folder; originals unchanged |

The “world map” picker is an offline schematic latitude/longitude grid, **not** a real map with streets. For exact addresses, the user can input coordinates manually.

“Strip all metadata” refers to the common EXIF/XMP/IPTC/GPS blocks in the reconstructed image. Proprietary invisible information embedded within pixels, unrecognized formats, cloud versions, backups, and images that were already published elsewhere cannot be erased by this action. Edited EXIF can retain vendor MakerNote fields with additional proprietary data; to remove those too, use the full re-encode cleaner.

## Verification

Performed locally in this work session:
- Kotlin syntax parser: 82 source/test Kotlin files parse without syntax errors.
- Kotlin compiler: `MetadataPatch.kt` and `JpegSegments.kt` compile with Kotlin JVM compiler.
- Core writer / use-case compiled against lightweight JVM Android API stubs; this checks Kotlin types/flow, **not** Android runtime API behavior.
- Manual logic tests: validation, GPS bounds, invalid date, JPEG XMP/IPTC removal, EXIF preservation passed.
- Included additional JUnit and Android instrumentation tests for metadata validation, JPEG segments, copying, overwriting with backup.

**Not yet verified:** Gradle / Android SDK dependency resolution, full Kotlin+Compose APK compilation, Android instrumented tests on an actual device/emulator, output accuracy across OEM galleries/providers, long-running batch processing, accessibility compliance for every component and every locale. GitHub Actions CI must pass before publishing APK.

## GitHub Actions

Workflow: `.github/workflows/ci.yml` (already present). Triggers on push, PR and manual run. Its job performs unit tests, Android Lint, debug APK assembly and compilation of instrumentation tests. The workflow **does not execute instrumentation tests** because no emulator is configured. Use Android Studio `connectedDebugAndroidTest` with a physical or virtual device to run the new tests.

## Termux upgrade

Download the provided ZIP to `Download`, then use the commands in the assistant response to unzip into a **temporary folder** and copy into your existing checkout. Take a `git` checkpoint before copying.
