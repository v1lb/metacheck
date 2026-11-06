# Building and publishing MetaCheck on Google Play

Русская версия: [DEPLOY.ru.md](DEPLOY.ru.md).

## 0. Before the first build
1. **Confirm the `applicationId`** in `app/build.gradle.kts` (currently `dev.metacheck.app`). [ACTION REQUIRED: the decision is final.]
   Google Play does not accept IDs starting with `com.example`. After the first upload to Play Console
   the ID cannot be changed. The `namespace` (package in the code) does not need to change.
2. You need a recent Android Studio: the project uses AGP 8.13, Gradle 8.14.3, Kotlin 2.2 and compileSdk/targetSdk 36.
   All dependency versions: `gradle/libs.versions.toml`. If Sync asks to upgrade the plugin, use the Upgrade Assistant.
3. Builds go through the Gradle Wrapper (`./gradlew`, `gradlew.bat` on Windows): it downloads Gradle 8.14.3 itself
   and verifies the archive against the SHA-256 in `gradle/wrapper/gradle-wrapper.properties`. No separate Gradle
   install is needed; Android Studio uses the same version. To upgrade:
   `./gradlew wrapper --gradle-version <version> --gradle-distribution-sha256-sum <sha>`.
4. Make sure the tests pass: `./gradlew test`. UI tests (need a phone or an emulator): `./gradlew connectedDebugAndroidTest`.

## 1. Upload key (one time)
```bash
keytool -genkeypair -v -keystore secrets/metacheck-upload.jks \
  -alias metacheck-upload -keyalg RSA -keysize 4096 -validity 10000
```
Keep the `secrets/` folder outside the repository. Make **two copies** of the `.jks` file and the password
in different safe places: without the upload key, updates have to be arranged with Google support.

Copy `keystore.properties.example` to `keystore.properties` and fill in paths and passwords.
The file is in `.gitignore`. For CI, set the environment variables instead
`METACHECK_STORE_FILE`, `METACHECK_STORE_PASSWORD`, `METACHECK_KEY_ALIAS`, `METACHECK_KEY_PASSWORD`.

## 2. Building the AAB

### With GitHub Actions (recommended)
1. Bump `versionCode` and `versionName` in `app/build.gradle.kts` and commit.
2. Create a tag with the same version and push it:
   ```bash
   git tag v2.6.1 && git push origin v2.6.1
   ```
3. The "Release AAB for Google Play" workflow starts by itself: it checks that the tag matches `versionName`,
   runs `release_gate.py`, unit tests and `lintRelease`, checks that the MapTiler key and the Play licensing key
   are set, and only then signs and builds the AAB. After the build it checks the permissions of the final manifest.
   If tests or lint fail, no AAB is built; the reports are in the `MetaCheck-release-reports-*` artifact.
4. Download the `MetaCheck-play-release-v2.6.1` artifact (AAB + `mapping.txt`).

Do not skip the check with `-x lintVitalRelease`: it is the last barrier before Play
(manifest errors, untranslated strings, etc.). `release_gate.py` fails if it finds that flag.

### Locally
```bash
./gradlew clean test lintRelease bundleRelease
```
Output: `app/build/outputs/bundle/release/app-release.aab`.
The R8 mapping file is in `app/build/outputs/mapping/release/mapping.txt`. It is already included in the AAB,
but keep a copy next to every release.

Signature check:
```bash
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```
If `keystore.properties` is not filled in, `bundleRelease` stops with an error: the debug key is not accepted by Play.

## 3. Testing on a device before publishing
Download bundletool (github.com/google/bundletool/releases) and install the AAB the way Play does:
```bash
java -jar bundletool.jar build-apks --bundle=app-release.aab --output=app.apks \
  --ks=secrets/metacheck-upload.jks --ks-key-alias=metacheck-upload --connected-device
java -jar bundletool.jar install-apks --apks=app.apks
```
Always test the release build: R8 can break things that worked in debug.
Check photo selection, all tabs and a PNG with Stable Diffusion metadata.

## 4. Play Console
1. Create the app: name, language, "App", "Free". Category, for example, "Tools".
2. **Play App Signing:** keep it on. Your key becomes the upload key; Google manages the distribution key.
3. Fill in the "App content" sections:
   - **Privacy policy:** a link to a web page is required. Be honest: analysis happens on the device only;
     the network is used only on user action (map, address search, optional report sending).
     Ready texts: `legal/`; the page address: `RELEASE_TODO.md`, item 5.
   - **Data safety:** the app has optional sending of a JSON report to a server the user configured
     (off by default, only on an explicit button press). Describe it in the form and in the privacy policy,
     checking the wording against the Google Play help. Images are not sent; history and keys stay encrypted on the device.
     Map: after the button press, the map provider (MapTiler, OpenStreetMap in builds without a key) receives the IP
     address and tile numbers, which reveal where the point is to within a few hundred metres. Ready answers: `DATA_SAFETY.md`.
   - **Content rating**, **target audience**, **ads** (none), **app access** (no login needed).
4. **Testing → Internal:** create a release, upload the `.aab`, add testers by email, send for review.
5. New personal developer accounts may be required to run closed testing with a group of testers
   before production. Check the current condition in Play Console on the "Production" page.
6. Production: prepare the description, phone screenshots (at least 2), a 512×512 icon and a 1024×500 feature graphic.

## 5. Updates
Every new AAB needs a higher `versionCode` in `app/build.gradle.kts`.
The target API requirement rises every year: new apps and updates currently need API 36;
see the Target API level requirements page in the Google Play help for upcoming deadlines.

## 6. Pro: the Google Play product and giving Pro to other people
**Product.** Play Console → Monetize → Products → One-time products → create a product with the ID
`pro_lifetime` (hard-coded in `PlayBillingGateway.PRODUCT_ID`), set a price and activate it. Purchases only work
in a build uploaded to Play (at least to internal testing), and only for tester accounts or real buyers.

**Purchase signature.** Copy the licensing key (Monetize → Monetization setup → Licensing) into `PlayPublicKey.kt`:
the app uses it to verify the signature of every purchase. A release AAB is not built without it.

**Three ways to give someone Pro**
1. **Google Play promo codes** (simplest, no code). Monetize → Promo codes: create codes for `pro_lifetime`
   and send one to the person; they redeem it in Google Play and get Pro as a normal purchase. See the console
   for the quarterly code limit and conditions.
2. **License key** (works outside Play; suits friends, partners, journalists and testers).
   - One time: `pip install cryptography`, then `python3 tools/license_tool.py keygen --out secrets/license_private.pem`.
     The script prints the public key. Paste it into `LicenseKey.PUBLIC_KEY_BASE64`, rebuild and release the app.
     Keep the private key offline and never commit it.
   - Issue a key: `python3 tools/license_tool.py issue --key secrets/license_private.pem --note "Ivan" --days 365`
     (without `--days` the key never expires). Send the `MC1.…` string; the person pastes it in the Pro window or in settings.
   - Limits: a key cannot be revoked (only expire via `--days`), and it can be forwarded to others.
     Use a short term when handing out keys to individuals.
3. **License testers.** Play Console → Settings → License testing: add an email, and those accounts
   buy Pro for free (to test the purchase flow itself).

**If the purchase code does not compile.** Billing Library 8 changes the API. If the build complains about
`queryProductDetailsAsync` in `PlayBillingGateway`, check the current Play Billing Library 8 documentation.
