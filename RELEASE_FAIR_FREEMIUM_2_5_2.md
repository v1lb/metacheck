# MetaCheck 2.5.2 — Fair Freemium source patch

## Included
- Free: 75 successful single-photo operations per UTC day (strip, GPS wipe, EXIF editing, overwrite with backup).
- Pro: unlimited operations and batch processing, enforced in `MetadataStudioUseCase`.
- Android Keystore AES-256-GCM authenticated quota record and encrypted Play ownership cache.
- Free EXIF editor tab, existing GPS manual/map editor, existing three-step animated onboarding and system-language/theme skip.
- Updated domain quota tests.

## Security limitations
No offline-only counter can survive Android app data clearing, uninstall/reinstall, rooted devices, device clock manipulation with certainty, or an instrumented/repacked APK. Encrypted local storage prevents casual plaintext modification, **not** entitlement forgery by a sufficiently privileged attacker. Date rollback is resisted only relative to the highest UTC day still stored. Play purchase must be periodically revalidated; strong enforcement requires backend ledger with trusted time, Play Developer API verification, and Play Integrity attestation. Do not market this as tamper-proof.

## Build validation
This archive was patched and statically inspected, but Android Gradle compilation was not run in this environment. CI must pass before shipping.

## Install from Termux
```
cd ~
termux-setup-storage
pkg install -y unzip git
unzip -o ~/storage/downloads/MetaCheck_2_5_2_Fair_Freemium_Source.zip -d ~/metacheck-import
cd ~/metacheck
cp -a ~/metacheck-import/MetaCheck/. ./
git add .
git commit -m "MetaCheck 2.5.2 fair freemium quota and free editor"
git push origin main
```
Check GitHub Actions and run automated tests. Do not overwrite uncommitted local work; commit or stash it first.
