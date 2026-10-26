# Сборка и публикация MetaCheck в Google Play

## 0. Перед первой сборкой
1. **Смени `applicationId`** в `app/build.gradle.kts` (сейчас заглушка `dev.metacheck.app`).
   Google Play не принимает идентификаторы, начинающиеся с `com.example`. После первой загрузки
   в Play Console идентификатор изменить нельзя. `namespace` (пакет в коде) трогать не нужно.
2. Нужна свежая Android Studio: проект использует AGP 8.9.1, Gradle 8.11.1 и compileSdk/targetSdk 36.
   Если Sync просит обновить плагин, соглашайся через Upgrade Assistant.
3. В архиве нет `gradlew` и `gradle-wrapper.jar`. Android Studio обходится без них. Для командной строки
   один раз выполни `gradle wrapper --gradle-version 8.11.1` (нужен установленный Gradle), после этого работает `./gradlew`.
4. Проверь, что проходят тесты: `./gradlew test`. UI-тесты (нужен телефон или эмулятор): `./gradlew connectedDebugAndroidTest`.

## 1. Ключ загрузки (делается один раз)
```bash
keytool -genkeypair -v -keystore secrets/metacheck-upload.jks \
  -alias metacheck-upload -keyalg RSA -keysize 4096 -validity 10000
```
Папку `secrets/` держи вне репозитория. Сделай **две копии** файла `.jks` и пароля
в разных надёжных местах: без ключа загрузки обновления придётся согласовывать с поддержкой Google.

Скопируй `keystore.properties.example` в `keystore.properties` и заполни пути и пароли.
Файл в `.gitignore`. Для CI вместо файла задай переменные окружения
`METACHECK_STORE_FILE`, `METACHECK_STORE_PASSWORD`, `METACHECK_KEY_ALIAS`, `METACHECK_KEY_PASSWORD`.

## 2. Сборка AAB
```bash
./gradlew clean test lintRelease bundleRelease
```
Результат: `app/build/outputs/bundle/release/app-release.aab`.
Файл R8-маппинга лежит в `app/build/outputs/mapping/release/mapping.txt`. Он уже включается в AAB,
а копию стоит сохранить рядом с каждым релизом.

Проверка подписи:
```bash
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```
Если `keystore.properties` не заполнен, получится **неподписанный** AAB, и Play его не примет.

## 3. Проверка на устройстве до публикации
Скачай bundletool (github.com/google/bundletool/releases) и установи AAB как это сделает Play:
```bash
java -jar bundletool.jar build-apks --bundle=app-release.aab --output=app.apks \
  --ks=secrets/metacheck-upload.jks --ks-key-alias=metacheck-upload --connected-device
java -jar bundletool.jar install-apks --apks=app.apks
```
Обязательно прогони именно release-сборку: R8 может сломать то, что работало в debug.
Проверь выбор фото, все вкладки и PNG с метаданными Stable Diffusion.

## 4. Play Console
1. Создай приложение: название, язык, «Приложение», «Бесплатное». Категория, например, «Инструменты».
2. **Play App Signing:** оставь включённым. Твой ключ будет ключом загрузки, а ключом распространения управляет Google.
3. Заполни разделы «Настройка приложения»:
   - **Политика конфиденциальности:** нужна ссылка на страницу. Напиши честно: обработка только на устройстве,
     доступа к сети нет.
   - **Безопасность данных (Data safety):** в приложении есть необязательная отправка JSON-отчёта
     на сервер, который пользователь сам указал (по умолчанию выключена, только по явному нажатию кнопки).
     Опиши это в форме и в политике конфиденциальности, формулировки сверяй со справкой Google Play.
     Изображения не отправляются, история и ключ хранятся только на устройстве в зашифрованном виде.
   - **Рейтинг контента**, **целевая аудитория**, **реклама** (нет), **доступ к приложению** (вход не нужен).
4. **Тестирование → Внутреннее:** создай релиз, загрузи `.aab`, добавь тестеров по email, отправь на проверку.
5. Для новых личных аккаунтов разработчика Play может требовать закрытое тестирование с группой тестеров
   перед выходом в production. Актуальное условие смотри в Play Console на странице «Выпуск в production».
6. Production: подготовь описание, скриншоты телефона (минимум 2), значок 512×512 и feature graphic 1024×500.

## 5. Обновления
Каждый новый AAB должен иметь больший `versionCode` в `app/build.gradle.kts`.
Требование к целевому API растёт каждый год: сейчас для новых приложений и обновлений нужен API 36,
ближайшие сроки смотри на странице Target API level requirements в справке Google Play.


## 6. Pro: товар в Google Play и выдача Pro другим людям
**Товар.** Play Console -> Монетизация -> Продукты -> Разовые продукты -> создай товар с идентификатором
`pro_lifetime` (он зашит в `PlayBillingGateway.PRODUCT_ID`), задай цену и активируй. Покупки работают только
в версии, загруженной в Play (хотя бы во внутреннее тестирование), и только для аккаунтов тестировщиков или реальных покупателей.

**Как выдать Pro человеку: три способа**
1. **Промокоды Google Play** (проще всего, без кода). Монетизация -> Промокоды: создаёшь коды для товара `pro_lifetime`
   и отправляешь человеку, он вводит код в Google Play и получает Pro как обычную покупку. Лимит кодов на квартал
   и условия смотри в консоли.
2. **Лицензионный ключ** (работает вне Play, подходит для друзей, партнёров, журналистов и тестеров).
   - Один раз: `pip install cryptography`, затем `python3 tools/license_tool.py keygen --out secrets/license_private.pem`.
     Скрипт напечатает открытый ключ. Вставь его в `LicenseKey.PUBLIC_KEY_BASE64`, пересобери и выпусти приложение.
     Закрытый ключ храни офлайн и не коммить.
   - Выдать ключ: `python3 tools/license_tool.py issue --key secrets/license_private.pem --note "Иван" --days 365`
     (без `--days` ключ бессрочный). Строку вида `MC1.…` отправь человеку: он вставит её в окне Pro или в настройках.
   - Ограничения: ключ нельзя отозвать (только срок `--days`), и человек может переслать его другим.
     Для раздачи по отдельным людям используй короткий срок.
3. **Тестировщики лицензий.** Play Console -> Настройки -> Тестирование лицензий: добавь email, и эти аккаунты
   покупают Pro бесплатно (для проверки самой покупки).

**Если проверка покупки не компилируется.** Библиотека Billing 8 меняет API. Если сборка ругается на
`queryProductDetailsAsync` в `PlayBillingGateway`, сверься с актуальной документацией (Play Billing Library 8).
