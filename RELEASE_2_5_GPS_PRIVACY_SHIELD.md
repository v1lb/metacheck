# MetaCheck 2.5 — GPS Privacy Shield (Android release candidate)

## Scope and release status

This archive is the complete Android/Kotlin/Compose source project, **not a signed Play Store AAB or a verified APK**. The local environment has no Android SDK or Gradle, so Android compilation and instrumented tests require GitHub Actions. Do not call it Play-ready until all gates below pass.

## New GPS Privacy Shield

- Conditional red warning in the report, only when EXIF has a readable GPS point or a recognized EXIF GPS group.
- Displays exact EXIF latitude/longitude to six decimals. This is the metadata's *claimed* location, not independently verified location or evidence of a real-world event.
- Offline map: simplified Natural Earth world polygons (public domain) in `app/src/main/assets/naturalearth_world_outline.txt`; point mapped using an equirectangular geographic projection. Country/coastline outlines are approximate, no streets or geocoding and **no coordinates sent to a mapping server**.
- Wipe GPS saves a **new copy** through Android's system document picker. Source unchanged, no cloud transfer.
- JPEG: GPS IFD tags are nulled with AndroidX ExifInterface, including altitude, timestamp and auxiliary fields. XMP, Photoshop APP13 / IPTC and JPEG comments are dropped as they might contain duplicate location. JPEG pixels are not recompressed in this operation.
- PNG: as the library does not support reliable isolated GPS rewriting in all PNG variants, performs complete metadata removal through image re-encoding. This can change file size and animated formats are unsupported by this route.
- Writes are verified via read-back SHA-256, and JPEG GPS tags checked again. GPS may be reintroduced if the saved photo is later opened in an app that modifies metadata.

## Editor and metadata cleaning

- Existing JPEG EXIF editor remains: coordinates, date/time via date picker, camera, author, ISO, F-number, exposure; manual map-grid GPS editing; verification of written tags; optional overwrite after verified backup.
- Existing full-strip engine remains: JPEG/PNG derived copies. JPEG full strip recompresses and may reduce resolution for very large files.
- Batch mode remains Pro (50 documents per run). Always creates new documents in SAF tree and verifies output.
- Existing local metadata viewer, encrypted history, onboarding language/theme and Play Billing integrations remain.

## Free / Pro business rules

- **Free**: analysis, metadata viewing, one successful privacy write (full strip, GPS wipe or overwrite with full strip) per UTC date; last 10 saved reports.
- **Pro**: unlimited single-file writes, EXIF editor, batch cleaning, extended reports/export/API and 50 history slots (existing Google Play Billing/lifetime key mechanism).
- Counter is in local Android private SharedPreferences, transactionally incremented only when the repository reports write success. An in-process Mutex prevents two concurrent writes bypassing the daily check.
- **Not tamper-proof**: reinstalling/clearing app data or changing device time may reset or bypass the quota. Strict commercial enforcement needs a server-side trusted clock, validated Play purchases, and an account or non-identifying device-bound entitlement policy. Billing currently cannot be considered anti-fraud production hardened.

## Source changes

- `domain/metadata/GpsPrivacy.kt` — detection, valid coordinates, correct longitude wrap and projection.
- `ui/GpsPrivacyShield.kt` — offline map, privacy warning and CTA.
- `data/metadata/MetadataRewriteRepositoryImpl.kt` — verified GPS-only JPEG wipe/PNG full-strip fallback.
- `domain/metadata/MetadataOperations.kt` — centralized Free/Pro checks and quota lock.
- `data/metadata/PreferencesDailyCleanQuota.kt` — persistent UTC-day counter.
- `ui/ResultView.kt`, `ui/MainScreen.kt`, `ui/MetadataStudioScreen.kt`, `ui/MetadataStudioViewModel.kt` — UI wiring, use case routing, quota display.
- `ui/ProUi.kt`, `ui/SettingsScreen.kt`, `ui/i18n/Localization.kt` — marketing clarity and RU/EN strings.
- Removed alternate cleaning writer `data/clean/CleanRepositoryImpl.kt` (could bypass daily cap).
- Added JVM tests `GpsPrivacyTest.kt`, `MetadataDailyQuotaTest.kt`; emulator tests for GPS-write, alert.

## Mandatory release gates

1. **Actions / CI green:** `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`. Any compiler error is a blocker.
2. **Actions / Android emulator tests green:** `connectedDebugAndroidTest` incl. new GPS red alert and JPEG GPS-wipe tests.
3. Physical Android QA: Samsung / Pixel / Xiaomi; JPEG/PNG and content providers: Files, Photos, cloud documents, read-only shares. Check orientation, high resolution, empty GPS, malformed EXIF, GPS in XMP/IPTC, actual saved file, and never overwrite an irreplaceable source.
4. Accessibility QA with TalkBack, font scaling 200%, sunlight/contrast and touch targets on dark **and** light themes. Static canonical-palette checks do not certify WCAG AAA across all states.
5. Play Console: sign/review AAB, data safety, privacy-policy URL, policy review for photo metadata removal; **server-side verification of Pro purchases** is strongly recommended.
6. Local file names, thumbnails and image *pixels* may still reveal location independently of EXIF. No cleaner can universally guarantee total anonymity in every file or platform.

## Update repository from Android / Termux

Download the ZIP, inspect `git status`, preserve local work, then:

```bash
cd ~/metacheck
git status --short
git pull --ff-only origin main
git branch backup-before-gps-privacy-shield
mkdir -p ~/metacheck-import-25
unzip -o ~/storage/downloads/MetaCheck_2_5_GPS_Privacy_Shield_Source.zip -d ~/metacheck-import-25
cp -a ~/metacheck-import-25/MetaCheck/. ~/metacheck/
git rm --ignore-unmatch app/src/main/java/com/example/metacheck/data/clean/CleanRepositoryImpl.kt
git add -A
git commit -m "MetaCheck 2.5 GPS Privacy Shield"
git push origin main
```

Stop before `git pull` if `git status --short` shows local modifications, to avoid overwriting your work.
