# Тексты интерфейса / UI strings

Все тексты MetaCheck лежат в ресурсах Android: `app/src/main/res/values/strings.xml` (английский,
язык по умолчанию) и `values-ru/strings.xml`. Эти файлы **генерируются** из `tools/i18n/*.py`,
вручную их не правят: CI проверяет `python3 tools/i18n/gen.py --check`.

## Изменить или добавить строку

1. Найдите нужный `master_*.py` (файлы разбиты по экранам) и добавьте запись `(key, en, ru)`.
   Аргументы только позиционные: `%1$s`, `%2$d`. Числа с окончаниями («5 полей») — через plurals:
   `{"one": ..., "other": ...}` для EN и `{"one", "few", "many", "other"}` для RU.
2. `python3 tools/i18n/gen.py`
3. В коде: `stringResource(R.string.key)` в Compose, `UiText.Res(R.string.key)` в ViewModel.

Правила: пишем на «вы»; декоративные метки-«брови» (`FORENSIC REPORT / ON-DEVICE`) одинаковы в обоих
языках; числа в значениях метаданных форматируются с точкой (Locale.US), переводятся только слова.
Кириллица в строковых литералах Kotlin запрещена тестом `StringResourcesTest`
(исключение — `LegacyLabels.kt`, это данные для чтения старых отчётов).

## Добавить язык (например, немецкий)

1. Создайте `tools/i18n/lang_de.py` со словарём `T = {"key": "Text", ...}` для всех переводимых ключей
   (генератор перечислит недостающие). Формы plurals для языка заданы в `PLURAL_FORMS` в `gen.py`.
2. В `master_14_settings.py` добавьте самоназвание: `("language_name_de", "Deutsch", None)`.
3. В `AppLanguage` (`ui/preferences/UiSelection.kt`) добавьте `DE("de")`,
   в `AppLocales.nameRes` — `AppLanguage.DE -> R.string.language_name_de`.
4. `python3 tools/i18n/gen.py`. Список языков для системных настроек Android 13+
   (`generateLocaleConfig`) обновится сам при сборке.
5. Политика конфиденциальности и условия: адреса `legal_privacy_url` / `legal_terms_url` тоже
   переводимые строки; для нового языка укажите страницу на этом языке или английскую.

## Язык приложения

Выбор хранит не приложение, а AppCompat (`AppCompatDelegate.setApplicationLocales`): на Android 13+
это системный per-app language (Настройки → Приложения → MetaCheck → Язык), на 8–12 AppCompat сохраняет
выбор сам (`autoStoreLocales` в манифесте). Language split в AAB выключен: иначе Google Play поставил бы
только язык устройства и выбор другого языка внутри приложения не работал бы.
