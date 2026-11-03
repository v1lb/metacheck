# MetaCheck

Русская версия: [README.ru.md](README.ru.md).

An Android app (Kotlin, Jetpack Compose) that shows image metadata (EXIF, GPS, XMP, IPTC, PNG text chunks,
Content Credentials / C2PA), looks for traces of AI generation in it, and saves copies with cleaned or
corrected metadata. Analysis runs on the phone; images are never uploaded.

Version 2.6.0 · minSdk 26 · targetSdk 36 · applicationId `dev.metacheck.app`.

## Documents
| File | Purpose |
|---|---|
| [`RELEASE_TODO.md`](RELEASE_TODO.md) | what is left for you to do before publishing |
| [`DEPLOY.md`](DEPLOY.md) | upload key, building the AAB, CI secrets, Pro license keys |
| [`PLAY_RELEASE_CHECKLIST.md`](PLAY_RELEASE_CHECKLIST.md) | release acceptance criteria |
| [`legal/`](legal/) | privacy policy and terms (RU/EN), sources of the website pages |
| [`DATA_SAFETY.md`](DATA_SAFETY.md) | answers for the Data safety form |
| [`STORE_LISTING_EN.md`](STORE_LISTING_EN.md), [`STORE_LISTING.md`](STORE_LISTING.md) | Google Play listing texts (English, Russian) |
| [`RELEASE_NOTES.md`](RELEASE_NOTES.md) | release notes for Play (ru-RU, en-US) |
| [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md) | library licenses |
| [`docs/`](docs/) | product strategy and an archive of notes from earlier versions (may not match the current code) |

Most guides also exist in Russian as `*.ru.md` next to the English file.

## Building
- Android Studio: File → Open → project folder, wait for Gradle Sync.
- Command line: `./gradlew test` (JVM tests), `./gradlew assembleDebug`, UI tests on a device:
  `./gradlew connectedDebugAndroidTest`. The Gradle version (8.14.3) is pinned by the Gradle Wrapper and verified by SHA-256.
- APK for your own phone: GitHub → Actions → **Build installable APK** → Run workflow → artifact
  `MetaCheck-installable-apk`. Without your own key it is signed with the debug key; if a build with a
  different signature is installed, uninstall it first.
- AAB for Google Play: the **Release AAB for Google Play** workflow (needs your upload key, a MapTiler key
  and the Play licensing key, see `RELEASE_TODO.md`).
- Static pre-release check: `python3 tools/release_gate.py`.

## Structure
```
com.example.metacheck
├─ MetaCheckApplication, AppContainer   manual DI
├─ domain/        pure Kotlin: models, SignalEngine (AI signals), metadata catalog, use cases, export
├─ data/
│  ├─ parser/     EXIF, XMP, PNG, C2PA (JUMBF + CBOR), format detection by file signature
│  ├─ metadata/   cleaning and editing copies, JPEG segments, result verification, daily limit
│  ├─ billing/, entitlement/, license/   Pro purchase, signature check, offline keys
│  ├─ crypto/     AES-GCM with keys in Android Keystore
│  └─ history/, settings/, export/, api/
└─ ui/            Compose: screens, ViewModels, texts from resources
```

## Features
- **Free:** analysis, all fields viewer, GPS Privacy Shield, cleaning and editing up to 75 photos per day (UTC),
  the last 10 reports visible in history.
- **Pro** (one-time purchase `pro_lifetime` or a license key): no daily limit, batch cleaning of up to
  50 photos, PDF/JSON export, 50 reports in history, sending a report to your own API.
- AI signals come from metadata only: C2PA (the manifest is read, the signature is not verified), IPTC
  DigitalSourceType, generation parameters (A1111, ComfyUI, NovelAI, Fooocus), about 70 generator names.
  Missing labels prove nothing: messengers and social networks strip them.
- Cleaning re-encodes pixels: JPEG stays JPEG (q=95), everything else is saved as PNG, orientation is
  applied to the pixels, images above 16 MP are downscaled, GIF animation is lost.
  A wide-gamut color profile (Display P3) is written anew by Android.
- Privacy score (0–100) with the actual exposed values, and "Send without metadata": a second Share-menu entry
  that cleans the photo and passes it on in one step.
- GPS removal from JPEG without re-encoding; tag editing for JPEG EXIF only; overwriting the original only
  after a verified external backup.

## Privacy and security
- Permissions: `INTERNET` (map on button press, optional API), `ACCESS_MEDIA_LOCATION` (GPS from photos),
  plus network state and Play billing from libraries. Storage permissions are stripped;
  the final set in the built APK is checked by `tools/check_apk_permissions.py`.
- Files only through system dialogs and `content://`; at most 16 MB is read; PNG, CBOR and XMP blocks
  are parsed with limits (no XML parser, no XXE).
- The map (osmdroid; MapTiler tiles, OpenStreetMap in builds without a key) loads only on button press.
- History, API key and license are encrypted with Android Keystore keys; backup is disabled.
- API: HTTPS only, no redirects, TLS 1.2+, optional pin; GPS is not sent by default.
- Pro purchases are verified by the Google Play signature on the device; only a server protects against a modified APK.

## Languages
The UI is in English (default) and Russian. Strings are generated from `tools/i18n`
(how to add a string or a language: [`tools/i18n/README.md`](tools/i18n/README.md)). The language is chosen in the app
or, on Android 13+, in the system app settings.

## If GPS does not show up
Android 10+ hides photo location until the app is granted "Access to media location".
Choose the file with "Choose file" and tap "Allow". Photos received through messengers usually have no GPS — that is normal.
