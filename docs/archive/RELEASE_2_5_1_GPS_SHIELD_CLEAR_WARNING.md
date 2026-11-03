# MetaCheck 2.5.1 — GPS Privacy Shield: clear warning and strong action

## Why this update exists

GPS metadata is a privacy consideration, not proof of tracking or an existing leak. The previous title “УТЕЧКА ГЕОЛОКАЦИИ” (“LOCATION DATA EXPOSED”) overstated what a local EXIF check can establish. The updated copy now correctly explains the conditional risk of sharing an original image with metadata intact.

## User flow

1. On a report with detected GPS markers, show a prominent red/pink-accented warning with clear white copy.
2. Explain what the recipient **might** be able to see upon receiving the original image; do not imply the image has been transmitted.
3. Offer a bright cyan action **«Защитить фото · удалить GPS»** *before* the map so the choice is immediately visible on smaller phones.
4. Explain that the original stays untouched and the user must share the saved copy, not the original.
5. Show decoded coordinates on an entirely offline map if present.
6. When cleaning cannot be started, tell the user to open the original JPEG/PNG rather than claiming it is always a missing original.

## Safety and scope

- Keeps the existing save-via-Android-document-picker and GPS removal code; no change to billing, quota or file parsers.
- For JPEG and PNG, the current repository saves a new copy and verifies written bytes; the JPEG path checks EXIF GPS tags. No original modification.
- GPS metadata removal does not guarantee anonymity: landmarks and text within image pixels can expose the scene. Other formats require existing supported flows.
- Russian and English translations are provided.
- Android UI tests were updated to check the new warning and the availability of the primary action.

## Verification

- Source-level/static checks and archive integrity: performed by the packaging script.
- Android Gradle compile, device instrumentation, Play Billing and end-to-end GPS deletion: **not verified in this environment**. Run GitHub Actions and verify on a real device before publishing.
