# MetaCheck — release acceptance criteria (not a certificate)

Status: Android preview. A green debug build does NOT equal Google Play production readiness.

## Must pass before public paid release

1. GitHub CI: unit tests, Android Lint, Compose instrumented-test compilation, debug APK.
2. `Android emulator tests` workflow: run actual Compose/UI and metadata rewrite tests (not merely compile them).
3. Release: valid non-debug upload-signed AAB, `bundleRelease`, `lintRelease`, R8-protected navigation and JSON/PDF exports.
4. On-device test: Android 8 / 13 / 16 where possible; Russian and English; dark/light; large fonts; screen reader.
5. Storage tests: Google Photos/Files, local providers, inaccessible documents, zero-byte files, corrupt JPEG/PNG, huge images, low disk, cancelled SAF operations and provider read-back failure.
6. Metadata edit + strip: verify source untouched for duplicate operation; verify EXIF/XMP/IPTC/GPS results independently on copies. Ensure failures never present an unverified file as successful.
7. Purchases: register `pro_lifetime` in Play Console and test purchases/refunds/restore/pending/offline/acknowledgment with license testers.
8. High-scale sales: implement a secure backend with Google Play Developer API purchase-token verification, server acknowledgement and revoked-purchase reconciliation. **Current client-only implementation is a release blocker for hardened monetization**. Never place developer API credentials in APK.
9. Privacy policy: publish an externally accessible finalized document with legal entity/contact; accurately disclose optional outgoing JSON metadata and Google Play billing. Resolve placeholders in PRIVACY_POLICY.md.
10. Play Data safety declarations, age rating, store images, store listing, app support address, intellectual property/licensing, crash monitoring consent, test tracks and staged rollout.
11. Real-world beta cohort: at least 20 real users with consent, measure crashes/ANRs, upload errors, user comprehension and willingness to pay. For certain newly created personal Play developer accounts, at least 12 opted-in testers for 14 days of closed testing are required by Play Console.
12. Privacy claims: absence of AI markers is NOT evidence that an image is authentic. Current analysis is metadata-based; pixel-based AI detection and C2PA signature validation are not implemented.

## Current technical limitations

- JPEG metadata editing; cleaning JPEG/PNG re-encodes images (may change quality and dimensions).
- Pixel hidden information, remote copies/backups and unsupported proprietary blocks are outside the strip guarantee.
- No end-to-end instrumentation evidence until emulator workflow succeeds.
- Client-only Pro verification is not secure against a modified client; a server is needed for commercial anti-fraud.
- Debug APK cannot be submitted as Play production release. Publish upload-signed AAB.
