# UI strings

Русская версия: [README.ru.md](README.ru.md).

All MetaCheck texts live in Android resources: `app/src/main/res/values/strings.xml` (English,
the default language) and `values-ru/strings.xml`. These files are **generated** from `tools/i18n/*.py`
and are never edited by hand: CI runs `python3 tools/i18n/gen.py --check`.

## Changing or adding a string

1. Find the right `master_*.py` (files are split by screen) and add an entry `(key, en, ru)`.
   Arguments are positional only: `%1$s`, `%2$d`. Numbers with word endings ("5 fields") use plurals:
   `{"one": ..., "other": ...}` for EN and `{"one", "few", "many", "other"}` for RU.
2. `python3 tools/i18n/gen.py`
3. In code: `stringResource(R.string.key)` in Compose, `UiText.Res(R.string.key)` in a ViewModel.

Rules: Russian texts address the user formally ("вы"); decorative eyebrow labels (`FORENSIC REPORT / ON-DEVICE`)
are the same in both languages; numbers in metadata values are formatted with a dot (Locale.US), only words are translated.
Cyrillic in Kotlin string literals is forbidden by `StringResourcesTest`
(the exception is `LegacyLabels.kt`, data for reading old reports).

## Adding a language (for example, German)

1. Create `tools/i18n/lang_de.py` with a dictionary `T = {"key": "Text", ...}` for every translatable key
   (the generator lists the missing ones). Plural forms per language are defined in `PLURAL_FORMS` in `gen.py`.
2. In `master_14_settings.py` add the native name: `("language_name_de", "Deutsch", None)`.
3. In `AppLanguage` (`ui/preferences/UiSelection.kt`) add `DE("de")`,
   and in `AppLocales.nameRes` add `AppLanguage.DE -> R.string.language_name_de`.
4. `python3 tools/i18n/gen.py`. The language list for Android 13+ system settings
   (`generateLocaleConfig`) updates itself during the build.
5. Privacy policy and terms: the `legal_privacy_url` / `legal_terms_url` addresses are translatable strings too;
   for a new language point them to a page in that language or to the English one.

## App language

The choice is stored by AppCompat, not by the app (`AppCompatDelegate.setApplicationLocales`): on Android 13+
it is the system per-app language (Settings → Apps → MetaCheck → Language); on 8–12 AppCompat stores the choice
itself (`autoStoreLocales` in the manifest). The language split in the AAB is off: otherwise Google Play would
install only the device language and choosing another language inside the app would not work.
