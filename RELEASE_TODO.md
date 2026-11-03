# What is left for you to do before publishing

Русская версия: [RELEASE_TODO.ru.md](RELEASE_TODO.ru.md).

Everything that can be done in code and documents is done. Below is only what needs your involvement.
In the code such places are marked `[ТРЕБУЕТСЯ ВЫ]` ("action required").

## Required
1. **Upload key and GitHub secrets.** Check `gh secret list -R v1lb/metacheck`: you need `METACHECK_KEYSTORE_B64`, `METACHECK_STORE_PASSWORD`, `METACHECK_KEY_PASSWORD`, `METACHECK_KEY_ALIAS`. Keep a copy of `metacheck-upload.jks` and the passwords off the phone. Without your own key no AAB is built (guard in `app/build.gradle.kts`).
2. **Google Play developer account** ($25 one time) with identity verification. For paid features, fill in the payments profile and seller details (in the EU the developer's address and contacts may be shown on the listing).
3. **First AAB build:** Actions → Release AAB for Google Play → Run workflow → download the `.aab` and `mapping.txt`.
4. **The `pro_lifetime` product** in Play Console (one-time purchase, price), tester accounts, testing purchase, refund and restore.
5. **Privacy policy:** enter `https://v1lb.github.io/legal-metacheck/PRIVACY_POLICY_EN.html` in Play Console. The page must open without signing in.
6. **Data safety form** (see `DATA_SAFETY.md`), content rating, target audience, ads answer (none), category, contact email.
7. **Listing assets:** 512×512 PNG icon, 1024×500 feature graphic, at least 2 phone screenshots per language. Texts are in `STORE_LISTING_EN.md`, `STORE_LISTING.md`, release notes in `RELEASE_NOTES.md`.
8. **Testing on real phones** (Android 8, 13, 15+, Russian and English): the R8 release build (map on button press, GPS, cleaning, Pro window), dark and light theme.
9. **Closed testing:** a personal account created after 13 November 2023 needs 12 testers for 14 days, then an application for production access. Check the conditions in Play Console.
10. **`applicationId` `dev.metacheck.app`:** confirm it. It cannot be changed after the first upload.
11. **MapTiler key.** Sign up at maptiler.com (free plan), create a key and, if the dashboard allows it, restrict it to your app.
    Add the `METACHECK_MAPTILER_KEY` secret on GitHub (`gh secret set METACHECK_MAPTILER_KEY -R v1lb/metacheck`). Without it no AAB is built: the Play listing and the policy name MapTiler as the map provider.
12. **Policy website.** The `legal/*.md` texts changed (date 2026-11-03): copy them to the `legal-metacheck` website repository.
13. **Play licensing key.** Play Console → app → Monetize → Monetization setup → "Licensing": copy the base64 key into `PlayPublicKey.kt`. It verifies the signature of Pro purchases; without it no AAB is built.

## Optional
- **Manual Pro keys:** create a key pair (`tools/license_tool.py keygen`) and paste the public key into `LicenseKey.kt`. Otherwise the key field in the app stays hidden.
- **Server-side purchase verification:** the purchase signature is currently verified on the device (`PurchaseSignature`): this stops ready-made purchase fakes, but not a modified APK. Full protection requires your own server and the Google Play Developer API (its credentials must never go into the app).
- **Lawyer and tax advisor:** show them the terms, the policy and the taxation of Pro income once. The terms define the governing law as the developer's country of residence; you can name a specific country.
- **Dependency updates:** before a release, check for updates and compare versions with `LicenseNotices.kt`.
