# MetaCheck 2.3 / Kotlin Jetpack Compose / Version 0.9.0

## Implemented

- First-run screen routing independent of the report interface: Welcome → Language → Theme.
- AnimatedContent crossfade and directional motion between steps; neon/pulse animated logo.
- Russian/English selection with immediate UI recomposition; persistent settings.
- System locale/theme skip action on both selection steps. Supported in-app languages are Russian and English; unsupported system languages fall back to English.
- Live interactive Dark Cyber Grid / Clean Cyber Light previews. Preferences change the full app theme instantly.
- Settings section for changing language and appearance later, or following system selection.
- Material 3 palette for both modes; text colors inherit onSurface/onBackground rather than hard-coded black.
- Existing EXIF/XMP/IPTC/PNG/GPS native viewer, search, category filters, and CSV/JSON/PDF exports retained.
- English query aliases for common metadata tags and groups; raw metadata values remain unmodified.
- Existing local history, encryption, analysis and Google Play Pro flow retained unchanged.
- Unit coverage added for language/theme resolution; metadata search test extended.
- CI checks canonical foreground/background contrast pairs and compiles instrumentation test sources.

## Architecture

- `ui/preferences/UiSelection.kt`: immutable configuration types and pure system-preference resolution.
- `ui/preferences/UiPreferences.kt`: on-device SharedPreferences backing a StateFlow; reads and commits.
- `ui/OnboardingFlow.kt`: self-contained, composable multi-step UI.
- `ui/AppearanceSettings.kt`: configurable language and theme after onboarding.
- `ui/LocalizedText.kt`: app-level localizable material text component and translation table. The raw metadata values aren't translated to preserve forensic fidelity.
- `ui/theme/Theme.kt` and `ui/CyberDesign.kt`: theme-aware Material schemes and visuals.
- `domain/metadata/MetadataCatalog.kt`: pure Kotlin index/filter of extracted metadata, with English search aliases.

## Product and QA limitations

- **This source archive is not a compiled APK.** Full Android Gradle build, lint and instrumentation execution require an Android SDK and emulator/device. CI compiles instrumentation test APKs but does not run them on a device.
- Contrast gate checks named pairs (normal text >=7:1), not an exhaustive WCAG audit across all composited overlays and Android font scaling/device brightness.
- Core image analysis is evidence from metadata; it is **not** a trained pixel-level detector nor a proof of authenticity.
- Google Play Billing purchase availability still requires the Play Console product configuration. No simulated payment approval is included.
- Polish/Italian/system locales fall back to English because only Russian and English translations are bundled. Developer-originated parser messages and third-party OS dialogs may retain original language.
- Prior debug build version may be installed under `.debug` application ID; the app is not a signed Google Play release.
