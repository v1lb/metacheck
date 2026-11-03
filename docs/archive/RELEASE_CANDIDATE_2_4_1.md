# MetaCheck 1.0.1-rc1 — audited release hardening (not yet Play Production Ready)

## Code changes from the supplied MetaCheck 2.4 archive

- **Fail closed after saving:** both `stripCopy` and `editCopy` now throw when the SAF provider's saved file cannot be read back and validated by SHA-256. No more success messages with `verified=false` for a new copy.
- **Testable integrity utility:** `WriteIntegrity.matches` checks staged vs saved bytes; JVM regression tests cover identical, truncated, modified and empty files.
- **Honest scan metric:** the number labeled `ПРИЗНАКОВ` excludes `Severity.INFO` notes. Absence of AI markers is not represented as a positive detection count.
- **Billing responsiveness:** service connection, owned-purchase query and product-price query stop waiting after 15 seconds. A timeout is *unknown*, not evidence that the user never purchased.
- **Real instrumentation workflow:** a separate manually runnable / pull-request Android-emulator GitHub Actions job executes `connectedDebugAndroidTest`, rather than only compiling the tests. Filesystem operations are tested against real Android APIs.
- **Release preflight:** `python3 tools/release_gate.py` checks API target, package, manifest backup/network settings, broad permission absence and build/test workflow presence. CI runs it; it does not claim to perform vulnerability analysis.
- **Quality criteria:** `PLAY_RELEASE_CHECKLIST.md` and `PRODUCT_STRATEGY.md` define launch blockers and measurable product goals.

## Checks in this environment

- SHA-256 verification utility compiled using Kotlin JVM compiler and passed runnable equal/modified/truncated checks.
- `Models.kt` compiled via Kotlin compiler; unit tests added for actionability count.
- Python static release gate passed.
- Contrast script passed its 10 defined color pairs (not a full accessibility audit).
- ZIP validity, packaging and absence of private keys checked.

## NOT proven

No Android SDK / Gradle available here for full APK/AAB build; no emulator executed. Kotlin/Compose and Billing API resolution require GitHub Actions. No backend purchase verification, signed store bundle, real device test, third-party security audit, hosted privacy policy, Play Console setup or distribution completed. Neither scalability nor million-user adoption is verified.
