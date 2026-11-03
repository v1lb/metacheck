# MetaCheck

English version: [README.md](README.md).

Android-приложение (Kotlin, Jetpack Compose): показывает метаданные изображений (EXIF, GPS, XMP, IPTC,
текстовые блоки PNG, Content Credentials / C2PA), ищет в них следы ИИ-генерации и сохраняет копии
с очищенными или исправленными метаданными. Анализ идёт на телефоне, изображения в сеть не уходят.

Версия 2.6.0 · minSdk 26 · targetSdk 36 · applicationId `dev.metacheck.app`.

## Документы
| Файл | Для чего |
|---|---|
| [`RELEASE_TODO.ru.md`](RELEASE_TODO.ru.md) | что осталось сделать вам перед публикацией |
| [`DEPLOY.ru.md`](DEPLOY.ru.md) | ключ загрузки, сборка AAB, секреты CI, лицензионные ключи Pro |
| [`PLAY_RELEASE_CHECKLIST.md`](PLAY_RELEASE_CHECKLIST.md) | критерии готовности к релизу |
| [`legal/`](legal/) | политика конфиденциальности и условия (RU/EN), исходники страниц сайта |
| [`DATA_SAFETY.ru.md`](DATA_SAFETY.ru.md) | ответы для формы Data safety |
| [`STORE_LISTING.md`](STORE_LISTING.md), [`STORE_LISTING_EN.md`](STORE_LISTING_EN.md) | тексты карточки Google Play |
| [`RELEASE_NOTES.md`](RELEASE_NOTES.md) | заметки о версии для Play |
| [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md) | лицензии библиотек |
| [`docs/`](docs/) | стратегия продукта и архив заметок прежних версий (могут не совпадать с текущим кодом) |

## Сборка
- Android Studio: File → Open → папка проекта, дождаться Gradle Sync.
- Командная строка: `./gradlew test` (JVM-тесты), `./gradlew assembleDebug`, UI-тесты на устройстве:
  `./gradlew connectedDebugAndroidTest`. Версию Gradle (8.14.3) задаёт Gradle Wrapper и проверяет по SHA-256.
- APK для своего телефона: GitHub → Actions → **Build installable APK** → Run workflow → артефакт
  `MetaCheck-installable-apk`. Без своего ключа он подписан debug-ключом; если стоит версия с другой подписью,
  сначала удалите её.
- AAB для Google Play: workflow **Release AAB for Google Play** (нужны свой ключ загрузки, ключ MapTiler
  и ключ лицензирования Play, см. `RELEASE_TODO.ru.md`).
- Статическая проверка перед релизом: `python3 tools/release_gate.py`.

## Структура
```
com.example.metacheck
├─ MetaCheckApplication, AppContainer   ручной DI
├─ domain/        чистый Kotlin: модели, SignalEngine (признаки ИИ), каталог метаданных, use case, экспорт
├─ data/
│  ├─ parser/     EXIF, XMP, PNG, C2PA (JUMBF + CBOR), определение формата по сигнатуре
│  ├─ metadata/   очистка и правка копий, JPEG-сегменты, проверка результата, дневной лимит
│  ├─ billing/, entitlement/, license/   покупка Pro, проверка подписи, офлайн-ключи
│  ├─ crypto/     AES-GCM с ключами в Android Keystore
│  └─ history/, settings/, export/, api/
└─ ui/            Compose: экраны, ViewModel, тексты из ресурсов
```

## Возможности
- **Free:** анализ, просмотр всех полей, GPS Privacy Shield, очистка и правка до 75 фото в сутки (UTC),
  в истории видны 10 последних отчётов.
- **Pro** (разовая покупка `pro_lifetime` или лицензионный ключ): без дневного лимита, пакетная очистка
  до 50 фото, экспорт PDF/JSON, 50 отчётов в истории, отправка отчёта в свой API.
- Признаки ИИ только по метаданным: C2PA (манифест читается, подпись не проверяется), IPTC
  DigitalSourceType, параметры генерации (A1111, ComfyUI, NovelAI, Fooocus), около 70 генераторов.
  Отсутствие меток ничего не доказывает: мессенджеры и соцсети их стирают.
- Очистка перекодирует пиксели: JPEG остаётся JPEG (q=95), остальное сохраняется в PNG, ориентация
  применяется к пикселям, изображения больше 16 Мп уменьшаются, анимация GIF теряется.
  Цветовой профиль широкой гаммы (Display P3) Android записывает заново.
- Удаление GPS из JPEG без перекодирования; правка тегов только JPEG EXIF; перезапись оригинала только
  после проверенной внешней резервной копии.

## Приватность и безопасность
- Разрешения: `INTERNET` (карта по кнопке, необязательный API), `ACCESS_MEDIA_LOCATION` (GPS из фото),
  от библиотек — состояние сети и покупки Play. Разрешения на хранилище вырезаются;
  итоговый набор в собранном APK проверяет `tools/check_apk_permissions.py`.
- Файлы только через системные диалоги и `content://`; читается не больше 16 МБ; блоки PNG, CBOR и XMP
  разбираются с лимитами (без XML-парсера, без XXE).
- Карта (osmdroid; тайлы MapTiler, в сборках без ключа OpenStreetMap) загружается только по кнопке.
- История, ключ API и лицензия зашифрованы ключами Android Keystore; бэкап отключён.
- API: только HTTPS, без редиректов, TLS 1.2+, необязательный пин; GPS по умолчанию не отправляется.
- Покупки Pro проверяются по подписи Google Play на устройстве; от взлома самого APK защищает только сервер.

## Языки
Интерфейс на английском (по умолчанию) и русском. Строки генерируются из `tools/i18n`
(как добавить строку или язык: [`tools/i18n/README.ru.md`](tools/i18n/README.ru.md)). Язык выбирается в приложении
или, на Android 13+, в системных настройках.

## Если GPS не показывается
Android 10+ скрывает геометки в фото, пока приложению не выдано разрешение «Доступ к местоположению в фото».
Выберите файл через «Выбрать файл» и нажмите «Разрешить». Фото из мессенджеров обычно уже без GPS — это нормально.
