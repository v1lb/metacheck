package com.example.metacheck.ui.i18n

import com.example.metacheck.ui.preferences.AppLanguage

fun localize(value: String, language: AppLanguage): String {
    if (language != AppLanguage.EN) return value
    translations[value]?.let { return it }
    val found = Regex("^(\\d+) полей · просмотр прямо на устройстве$").matchEntire(value)
    if (found != null) return "${found.groupValues[1]} fields · on-device viewer"
    val found2 = Regex("^(\\d+) полей  ·  Нажмите, чтобы раскрыть$").matchEntire(value)
    if (found2 != null) return "${found2.groupValues[1]} fields · Tap to expand"
    val found3 = Regex("^НАЙДЕНО: (\\d+)$").matchEntire(value)
    if (found3 != null) return "FOUND: ${found3.groupValues[1]}"
    if (value.startsWith("Указан ИИ-инструмент: ")) return value.replaceFirst("Указан ИИ-инструмент: ", "AI tool identified: ")
    if (value.startsWith("Упоминание «") && value.endsWith("» в тексте"))
        return value.replaceFirst("Упоминание «", "Mention of ‘").replace("» в тексте", "’ in text")
    if (value.startsWith("Проверяемое изображение: ")) return value.replaceFirst("Проверяемое изображение: ", "Image: ")
    if (value.startsWith("FREE · Очищено сегодня: ")) return value.replace("FREE · Очищено сегодня: ", "FREE · Cleaned today: ").replace(" из 75 · Сброс в 00:00 UTC", " of 75 · Reset 00:00 UTC")
    if (value.startsWith("Выбрано: ")) return value.replaceFirst("Выбрано: ", "Selected: ")
    if (value.startsWith("Широта ") && value.contains("Долгота")) return value.replace("Широта", "Latitude").replace("Долгота", "Longitude")
    if (value.startsWith("Дата оригинала: ")) return value.replaceFirst("Дата оригинала: ", "Date taken: ").replace("не изменять", "unchanged")
    if (value.startsWith("Дата оцифровки: ")) return value.replaceFirst("Дата оцифровки: ", "Date digitized: ").replace("не изменять", "unchanged")
    if (value.startsWith("Операция не выполнена: ")) return value.replaceFirst("Операция не выполнена: ", "Operation failed: ").let(::fragments)
    if (value.startsWith("Пакетная очистка завершена: ")) return value.replaceFirst("Пакетная очистка завершена: ", "Batch cleaning finished: ").replace("сохранено", "saved").replace("ошибок", "failed")
    if (value.startsWith("Очищенная копия сохранена")) return value.replaceFirst("Очищенная копия сохранена", "Clean copy saved").let(::fragments)
    if (value.startsWith("GPS удалён из новой копии")) return value.replaceFirst("GPS удалён из новой копии", "GPS removed from saved copy")
        .replace("Для отправки используйте новую копию, а не оригинал", "Share the new copy, not the original").let(::fragments)
    if (value.startsWith("EXIF записан в новый файл")) return value.replaceFirst("EXIF записан в новый файл", "EXIF saved to a new file").let(::fragments)
    if (value.startsWith("Оригинал заменён")) return value.replaceFirst("Оригинал заменён", "Original replaced").replace("Резервная копия сохранена", "Backup saved").let(::fragments)
    if (value.startsWith("Программы в истории правок")) return "Edit history software"
    Regex("^(\\d+(?:[.,]\\d+)?) (Б|КБ|МБ)$").matchEntire(value)?.let {
        val unit = when (it.groupValues[2]) { "Б" -> "B"; "КБ" -> "KB"; else -> "MB" }
        return it.groupValues[1] + " " + unit
    }
    Regex("^(\\d+(?:[.,]\\d+)?) м$").matchEntire(value)?.let { return it.groupValues[1] + " m" }
    Regex("^(\\d+)/(\\d+) · Успешно: (\\d+) · Ошибок: (\\d+)$").matchEntire(value)?.let {
        return it.groupValues[1] + "/" + it.groupValues[2] + " · Succeeded: " + it.groupValues[3] + " · Failed: " + it.groupValues[4]
    }
    Regex("^JSON-отчёт будет отправлен на (.+)\\. Исходная фотография не отправляется\\. GPS: (включён в отчёт|исключён из отчёта)\\.$").matchEntire(value)?.let {
        val host = if (it.groupValues[1] == "указанный сервер") "the specified server" else it.groupValues[1]
        val gps = if (it.groupValues[2].startsWith("включён")) "included in the report" else "excluded from the report"
        return "The JSON report will be sent to " + host + ". The original photo is not sent. GPS: " + gps + "."
    }
    Regex("^Pro активен по ключу(?: до (.+))?$").matchEntire(value)?.let {
        val until = it.groupValues[1]
        return if (until.isEmpty()) "Pro active via key" else "Pro active via key until " + until
    }
    if (value.startsWith("Ошибка сохранения: ")) return value.replaceFirst("Ошибка сохранения: ", "Save error: ")
    return fragments(value)
}

/** Sentence fragments appended to composed status messages. */
private fun fragments(value: String): String = value
    .replace(". Запись проверена", ". Write verified")
    .replace(". Не удалось перепроверить запись: откройте файл вручную", ". Could not verify the write: open the file manually")
    .replace(". Разрешение уменьшено для безопасной обработки", ". Resolution was reduced for safe processing")
    .replace("ошибка доступа или формата", "access or format error")


private val translations: Map<String, String> = mapOf(
    "Управление метаданными" to "Metadata management",
    "GPS PRIVACY SHIELD · GPS В ФАЙЛЕ" to "GPS PRIVACY SHIELD · GPS FOUND",
    "Место съёмки сохранено в фото" to "This photo contains its capture location",
    "В фотографии найдены геометки" to "Location metadata found in this photo",
    "Если отправить оригинал без очистки, получатель может увидеть координаты места съёмки." to "If you share the uncleaned original, the recipient may be able to see where it was taken.",
    "В метаданных найдены сведения о местоположении, но точные координаты прочитать не удалось." to "Location metadata was found, but full coordinates could not be decoded.",
    "Это не означает, что за вами следят. Риск возникает, если передать файл с геометками." to "This does not mean someone is tracking you. The privacy risk arises when you share a file with location data.",
    "Перед отправкой лучше создать копию без GPS — это простой способ защитить своё местоположение." to "Before sharing, make a GPS-free copy to help keep your location private.",
    "Защитить фото · удалить GPS" to "Protect photo · remove GPS",
    "После нажатия выберите место для новой копии. Оригинал не изменится. Отправляйте именно копию. Free: 75 обработок в сутки." to "After tapping, choose where to save a new copy. The original stays unchanged. Share the copy, not the original. Free: 75 operations per day.",
    "Чтобы убрать геометки, откройте исходную фотографию JPEG/PNG: сохранённый отчёт не содержит самого фото." to "Open the original JPEG/PNG to remove location tags; a saved report contains no photo data.",
    "КООРДИНАТЫ В ИСХОДНОМ ФАЙЛЕ" to "COORDINATES IN ORIGINAL FILE",
    "Точку нельзя показать на карте: GPS-данные неполные или записаны в XMP/PNG." to "Map position is unavailable: GPS tags are incomplete or stored in XMP/PNG.",

    "EXIF-редактор PRO" to "EXIF editor PRO",
    "Бесплатно: анализ, GPS Privacy Shield и 75 обработок в сутки. Pro: безлимитная очистка, пакетная обработка, PDF/JSON." to "Free: scans, GPS Privacy Shield and 75 operations per day. Pro: unlimited cleaning, batch, PDF/JSON.",
    "Free: анализ и GPS Privacy Shield, просмотр EXIF, 75 обработок фото в сутки (UTC), 10 отчётов в истории." to "Free: scans, GPS Privacy Shield, EXIF viewer, 75 edited or cleaned photos per UTC day, 10 saved reports.",
    "Pro: безлимитная очистка, пакетная обработка до 50 фото, PDF/JSON, 50 отчётов, API." to "Pro: unlimited cleaning, EXIF editor, batch up to 50 photos, PDF/JSON, 50 reports, API.",
    "Реальное сохранение изменений в файл. Без отправки изображения в сеть." to "Real file modifications. Your image is never uploaded.",
    "← К отчёту" to "← Back to report",
    "Очистка" to "Clean", "EXIF-редактор" to "EXIF editor", "Пакетно PRO" to "Batch PRO",
    "01 / ИСХОДНОЕ ИЗОБРАЖЕНИЕ" to "01 / SOURCE IMAGE",
    "Файл не выбран" to "No file selected", "Изображение выбрано" to "Image selected",
    "02 / ПОЛНАЯ ОЧИСТКА" to "02 / REMOVE METADATA",
    "Удалить метаданные" to "Remove metadata",
    "Удалить все метаданные" to "Remove all metadata",
    "Файл будет декодирован и сохранён заново без переносимых EXIF, XMP, IPTC и GPS. JPEG повторно сжимается; HEIC/GIF/WebP сохраняются как PNG, а анимация может потеряться. Подписи C2PA становятся недействительными." to "The image is re-encoded without carried EXIF, XMP, IPTC or GPS. JPEG is recompressed; HEIC/GIF/WebP export as PNG and animation can be lost. Existing C2PA signatures are invalidated.",
    "Сохранить очищенный дубликат" to "Save clean copy",
    "Перезаписать оригинал с резервной копией" to "Replace original with backup",
    "Для перезаписи доступны только JPEG/PNG и только документы с правом записи. Сначала вы выберете место для полноценной резервной копии." to "Replace only writable JPEG/PNG documents. You will choose a location for a verified backup first.",
    "02 / РЕДАКТОР EXIF" to "02 / EXIF EDITOR",
    "Координаты и GPS" to "Coordinates and GPS",
    "Удалить GPS целиком" to "Remove all GPS",
    "Широта (−90…90)" to "Latitude (−90…90)",
    "Долгота (−180…180)" to "Longitude (−180…180)",
    "Выбрать точку на мировой сетке" to "Pick on world coordinate grid",
    "Модель камеры" to "Camera model", "Диафрагма (например, 1.8)" to "Aperture (e.g. 1.8)",
    "Выдержка в секундах (например, 0.02)" to "Shutter speed in seconds (e.g. 0.02)",
    "Оставить без изменений" to "Leave unchanged",
    "Пустые поля остаются без изменений. Редактор работает с JPEG; противоречащие EXIF блоки XMP/IPTC в JPEG удаляются. Изменения не применяются к исходному файлу до сохранения. Любая правка может сделать C2PA-подписи недействительными." to "Blank fields remain unchanged. The editor supports JPEG. Conflicting standalone XMP/IPTC blocks are removed. The original stays untouched until saved. Changes may invalidate C2PA signatures.",
    "Сохранить изменённую копию JPEG" to "Save edited JPEG copy",
    "Перезаписать JPEG с резервной копией" to "Replace JPEG with backup",
    "Массовая очистка" to "Batch cleaning",
    "До 50 файлов за один запуск. Новые копии будут сохранены в выбранную папку. Оригиналы останутся неизменными." to "Up to 50 files per operation. New copies go to the selected folder; originals remain unchanged.",
    "Выбрать фотографии" to "Choose photos",
    "Выбрать папку и запустить" to "Choose folder and start",
    "ОШИБКИ ПО ФАЙЛАМ" to "FILE ERRORS",
    "Заменить оригинальный файл?" to "Replace original file?",
    "Действие нельзя гарантированно выполнить атомарно у всех файловых провайдеров. Вы сначала сохраните отдельную резервную копию; только после проверки копии будет произведена замена. При сбое приложение попытается восстановить оригинал." to "File providers do not guarantee atomic replacement. First save an independent backup; the app verifies it before replacing the original and attempts a rollback if anything fails.",
    "Создать резервную копию" to "Create backup",
    "Мировая сетка GPS" to "World GPS grid",
    "Коснитесь сетки для выбора координат. Это схематическая карта широты/долготы без улиц." to "Tap the grid to pick coordinates. This is a latitude/longitude grid without street map tiles.",
    "Использовать точку" to "Use this point",
    "Редактировать EXIF / Metadata Studio →" to "Edit EXIF / Metadata Studio →",
    "Очистка · EXIF-редактор · пакетная обработка" to "Cleaning · EXIF editor · batch operations",
    "Открыть Metadata Studio →" to "Open Metadata Studio →",
    "Free: локальный анализ, EXIF-редактор JPEG, очистка одного фото и CSV-история." to "Free: on-device analysis, JPEG EXIF editor, one-photo cleaning and history CSV.",
    "Pro: пакетная очистка до 50 фото, PDF/JSON, 50 отчётов и собственный API." to "Pro: batch clean up to 50 photos, PDF/JSON, 50 reports and custom API.",
    "Анализ, EXIF-редактор JPEG и очистка одного фото бесплатны. Pro добавляет пакетную очистку, PDF/JSON и историю до 50 записей." to "Analysis, JPEG EXIF editing and single-photo cleaning are free. Pro adds batch cleaning, PDF/JSON and 50 history entries.",
    "Настройки" to "Settings", "Назад" to "Back", "Подтвердить отправку" to "Confirm transfer",
    "Отправить" to "Send", "Отмена" to "Cancel", "Внешний API" to "External API",
    "Ожидание ответа сервера…" to "Waiting for server response…", "Ответ пуст" to "Empty response",
    "Закрыть" to "Close", "Цифровая\nэкспертиза фото" to "Digital photo\nforensics",
    "Проверьте происхождение файла, метаданные камеры и технические признаки ИИ." to "Inspect file origins, camera metadata and technical AI signals.",
    "Исследовать изображение" to "Analyze image",
    "Выбирайте исходный JPEG, PNG или HEIC: в скриншоте часть данных уже потеряна." to "Choose the original JPEG, PNG or HEIC. Screenshots may lose metadata.",
    "＋  Выбрать фотографию" to "＋  Choose photo", "◎  Камера" to "◎  Camera",
    "▣  Буфер" to "▣  Clipboard", "КАМЕРА И GPS" to "CAMERA & GPS",
    "МЕТАДАННЫЕ" to "METADATA", "МЕТКИ ГЕНЕРАЦИИ" to "AI MARKERS",
    "ВАША АНАЛИТИКА" to "YOUR INSIGHTS", "Лимит сохранённой истории" to "Saved history limit",
    "Pro: до 50 локальных отчётов" to "Pro: up to 50 local reports",
    "Free: до 10 отчётов · Pro: до 50" to "Free: 10 reports · Pro: 50",
    "Увеличить лимит с Pro" to "Upgrade history with Pro",
    "ВСЕГО ОТЧЁТОВ" to "TOTAL REPORTS", "С ПРИЗНАКАМИ ИИ" to "WITH AI SIGNALS",
    "С GPS" to "WITH GPS", "АКТИВНОСТЬ ЗА 7 ДНЕЙ" to "7-DAY ACTIVITY",
    "Статистика только по сохранённой истории, без облака и оценок подлинности." to "Statistics use local saved reports; no cloud or authenticity estimates.",
    "ИСТОРИЯ ПРОВЕРОК" to "SCAN HISTORY", "Здесь появятся отчёты" to "Reports appear here",
    "История пока пуста. Начните первую проверку." to "History is empty. Start your first scan.",
    "Сохранение истории отключено в настройках." to "History storage is disabled in Settings.",
    "Экспорт PDF/JSON и расширенная история доступны" to "PDF/JSON exports and extended history are enabled",
    "PDF, JSON, 50 отчётов и собственный API" to "PDF, JSON, 50 reports and custom API",
    "Приватность по умолчанию: изображения не отправляются MetaCheck в сеть. Для API требуется отдельное согласие." to "Privacy by default: images are not uploaded. Custom API requires explicit consent.",
    "Сканирование метаданных" to "Scanning metadata",
    "Проверяем структуру файла, EXIF, XMP и признаки генерации." to "Checking file structure, EXIF, XMP and AI generation clues.",
    "Не удалось проверить файл" to "Could not scan file", "Выбрать другой файл" to "Choose another file",
    "На главную" to "Home", "Анализ" to "Analysis", "Метаданные" to "Metadata",
    "Карта" to "Map", "Обнаружены признаки ИИ" to "AI markers detected",
    "Нужна дополнительная проверка" to "Further investigation needed",
    "Явных следов ИИ не найдено" to "No explicit AI markers found",
    "Проверка только метаданных • не анализ пикселей" to "Metadata-only analysis • pixels not analyzed",
    "Результат не подтверждает подлинность снимка. Метаданные могут быть удалены или подделаны." to "This does not prove authenticity. Metadata can be removed or modified.",
    "СИГНАЛОВ" to "SIGNALS", "ГРУПП EXIF" to "EXIF GROUPS", "ФОРМАТ" to "FORMAT",
    "Посмотреть все метаданные →" to "View all metadata →", "ДЕЙСТВИЯ С ОТЧЁТОМ" to "REPORT ACTIONS",
    "Копировать" to "Copy", "Сохранить PDF" to "Save PDF", "Экспорт JSON" to "Export JSON",
    "Очистить EXIF" to "Remove EXIF", "Проверить через API" to "Check via API",
    "РЕЗУЛЬТАТЫ СКАНИРОВАНИЯ" to "SCAN RESULTS",
    "Оригинальный файл даёт более полные метаданные. В истории MetaCheck хранится отчёт, но не копия фото." to "Original files preserve more metadata. History stores reports, not copies of photos.",
    "Фотография не хранится в истории" to "Photo not stored in history",
    "Превью недоступно" to "Preview unavailable", "Координаты" to "Coordinates",
    "Широта / долгота" to "Latitude / longitude", "Высота" to "Altitude",
    "Открыть в картах" to "Open in maps", "Приложение карт не найдено" to "Maps app not found",
    "Для открытия карты потребуется другое приложение. GPS из отчёта не отправляется MetaCheck на сервер." to "A maps app is required. MetaCheck does not transmit report GPS to its servers.",
    "Метаданные файла" to "File metadata", "Поиск по тегам и значениям" to "Search names and values",
    "Например: ISO, GPS, Adobe" to "For example: ISO, GPS, Adobe", "Сброс" to "Clear", "Все" to "All",
    "Экспорт CSV ↗" to "Export CSV ↗", "Ничего не найдено" to "No matches",
    "Файл не содержит распознанных метаданных." to "No recognized metadata in this file.",
    "Попробуйте другой запрос или выберите категорию «Все»." to "Try another search or choose All.",
    "Сбросить фильтры" to "Reset filters",
    "Показаны доступные поля, извлечённые локальными парсерами MetaCheck. Отсутствие тега не доказывает подлинность файла. GPS может содержать личные данные." to "Fields extracted locally by MetaCheck are displayed. Missing tags do not prove authenticity. GPS may reveal personal data.",
    "Центр управления" to "Control center", "Приватность, доступ и расширенные возможности" to "Privacy, access and advanced tools",
    "У вас доступен расширенный экспорт и API." to "Advanced export and API are enabled.",
    "Базовый анализ и очистка доступны бесплатно. Pro открывает PDF/JSON и до 50 записей в истории." to "Basic scan and cleaning are free. Pro unlocks PDF/JSON and up to 50 history entries.",
    "Возможности Pro" to "Explore Pro", "Отключить лицензию" to "Remove license",
    "ЛОКАЛЬНАЯ ИСТОРИЯ" to "LOCAL HISTORY", "Сохранять отчёты" to "Save reports",
    "Шифрование через Android Keystore. Фотографии не копируются. Free: 10, Pro: 50." to "Encrypted using Android Keystore. Photos not copied. Free: 10, Pro: 50.",
    "Удалить историю с телефона" to "Delete local history", "СОБСТВЕННЫЙ API" to "CUSTOM API",
    "Отправлять JSON-отчёт" to "Send JSON report",
    "Только с подтверждением и только в Pro. Исходное изображение не передаётся." to "Confirmation and Pro required. Original image is never sent.",
    "Настройки соединения" to "Connection settings", "Адрес API (HTTPS)" to "API endpoint (HTTPS)",
    "Имя заголовка авторизации" to "Authorization header", "Заменить сохранённый ключ" to "Replace stored key",
    "Ключ API" to "API key", "Сохраняется зашифрованным; значение нельзя прочитать из настроек." to "Encrypted at rest; cannot be read from settings.",
    "Сохранить ключ" to "Save key", "Ключ сохранён" to "Key saved",
    "Ошибка сохранения ключа" to "Failed to save key", "Удалить" to "Delete", "Ключ удалён" to "Key deleted",
    "Отправлять GPS" to "Include GPS", "По умолчанию геолокация из JSON исключена." to "GPS excluded from JSON by default.",
    "Только TLS 1.3" to "TLS 1.3 only", "Работает с совместимыми серверами на Android 10+." to "Android 10+ and compatible servers required.",
    "SHA-256 публичного ключа (base64)" to "Public key SHA-256 (base64)",
    "Необязательный pin сертификата сервера." to "Optional server certificate pin.",
    "Хранение только на устройстве" to "On-device storage only",
    "Удалить все отчёты?" to "Delete all reports?",
    "Это действие удалит сохранённые отчёты без возможности восстановления." to "This permanently deletes saved reports.",
    "Запрошена очистка истории" to "History deletion requested",
    "Free: локальный анализ, EXIF, очистка, статистика и CSV-история." to "Free: local analysis, EXIF, cleaning, analytics and history CSV.",
    "Pro: экспорт PDF/JSON, до 50 отчётов, подключение собственного API." to "Pro: PDF/JSON exports, 50 reports, custom API.",
    "Ключ Pro, если он у тебя есть" to "Pro license key (optional)",
    "Применить ключ" to "Redeem key", "Покупка недоступна" to "Purchase unavailable",
    "Бесплатная версия" to "Free plan", "Pro активен (покупка в Google Play)" to "Pro active (Google Play)",
    "Основное" to "General", "Камера" to "Camera", "Геолокация" to "Location",
    "Технические EXIF" to "Technical EXIF", "PNG / текст" to "PNG / text",
    "Имя" to "Name", "Формат" to "Format", "Тип MIME" to "MIME type",
    "Размер, байт" to "Size, bytes", "Прочитано, байт" to "Scanned, bytes",
    "Частичное чтение" to "Partial read", "Да" to "Yes", "Нет" to "No",
    "Камера и объектив" to "Camera and lens", "Параметры съёмки" to "Exposure settings",
    "Даты" to "Dates", "Геолокация EXIF" to "EXIF location",
    "Координаты" to "Coordinates", "Широта" to "Latitude", "Долгота" to "Longitude",
    "Высота, м" to "Altitude, m", "Файл" to "File", "Программа и авторство" to "Software and authorship",
    "Производитель" to "Make", "Модель" to "Model", "Диафрагма" to "Aperture",
    "Выдержка" to "Shutter speed", "Фокусное расстояние" to "Focal length",
    "Метаданные InvokeAI" to "InvokeAI metadata", "Граф ComfyUI" to "ComfyUI workflow graph",
    "Параметры Stable Diffusion" to "Stable Diffusion parameters",
    "Метка IPTC: создано ИИ" to "IPTC: AI-generated marker",
    "Найдены Content Credentials (C2PA)" to "Content Credentials (C2PA) detected",
    "Подпись пока не проверяется, это будет в одном из следующих этапов." to "C2PA signature verification is not supported.",
    "В файле есть геолокация" to "File contains geolocation",
    "Если отправить этот файл без очистки метаданных, получатель увидит место съёмки." to "If you share the uncleaned image, its location may be visible to recipients.",
    "Метаданных нет" to "No metadata detected",
    "Файл мог пройти через мессенджер, быть скриншотом или обработан. Это не доказывает ни подлинность, ни ИИ." to "The file may have been processed or shared via a messenger. This proves neither authenticity nor AI generation.",
    "Файл проанализирован частично" to "File partly scanned",
    "Файл большой, метаданные искались только в начале." to "Large file: metadata was only scanned near its beginning.",
    "Явных следов ИИ в метаданных нет" to "No explicit AI metadata markers",
    "Метаданные легко удалить или подделать, поэтому это не доказывает, что изображение настоящее." to "Metadata can be removed or forged; this does not prove the image is authentic.",
    "Ширина" to "Width", "Ширина (пиксели)" to "Width (pixels)",
    "Высота (пиксели)" to "Height (pixels)", "Ориентация" to "Orientation",
    "Цветовое пространство (код)" to "Color space (code)",
    "Разрешение по X" to "X resolution", "Разрешение по Y" to "Y resolution",
    "Единица разрешения" to "Resolution unit", "Бит на канал" to "Bits per channel",
    "Сжатие" to "Compression", "Фотометрическая интерпретация" to "Photometric interpretation",
    "Версия EXIF" to "EXIF version", "Эквивалент 35 мм" to "35 mm equivalent",
    "Программа экспозиции" to "Exposure program", "Режим экспозиции" to "Exposure mode",
    "Коррекция экспозиции" to "Exposure compensation", "Режим замера" to "Metering mode",
    "Вспышка (код)" to "Flash (code)", "Баланс белого" to "White balance",
    "Источник света" to "Light source", "Тип сцены" to "Scene type",
    "Цифровой зум" to "Digital zoom", "Дистанция до объекта" to "Subject distance",
    "Яркость" to "Brightness", "Максимальная диафрагма" to "Maximum aperture",
    "Дата оцифровки" to "Digitized", "Часовой пояс съёмки" to "Timezone taken",
    "Часовой пояс изменения" to "Timezone modified", "Доли секунды" to "Subseconds",
    "Полушарие широты" to "Latitude hemisphere", "Полушарие долготы" to "Longitude hemisphere",
    "Высота GPS (RAW)" to "GPS altitude (raw)", "Дата GPS" to "GPS date",
    "Время GPS" to "GPS time", "Направление камеры" to "Camera direction",
    "Скорость GPS" to "GPS speed", "Единица скорости" to "Speed unit",
    "Спутники" to "Satellites", "Метод геопозиционирования" to "Geolocation method",
    "Программа" to "Software", "Автор" to "Artist", "Авторские права" to "Copyright",
    "Описание" to "Description", "Комментарий" to "Comment", "Серийный номер объектива" to "Lens serial number",
    "Владелец камеры" to "Camera owner",
    "Дата съёмки" to "Date taken", "Дата изменения" to "Modified",
    "КАРТА ВЫКЛЮЧЕНА · В СЕТЬ НИЧЕГО НЕ ОТПРАВЛЯЕТСЯ" to "MAP OFF · NOTHING IS SENT OVER THE NETWORK",
    "Чтобы показать точку на карте, приложение загрузит фрагменты карты с серверов OpenStreetMap. Серверы увидят ваш IP-адрес и примерный район точки. Само фото и его метаданные не отправляются." to "To show the point on the map, the app will download map tiles from OpenStreetMap servers. The servers will see your IP address and the approximate area of the point. The photo and its metadata are not sent.",
    "Показать карту (запрос к OpenStreetMap)" to "Show map (request to OpenStreetMap)",
    "Чтобы выбрать новые координаты на карте, сначала включите карту." to "To pick new coordinates on the map, turn the map on first.",
    "Нажмите на карту, чтобы выбрать точку" to "Tap the map to pick a point",
    "Город, улица или адрес" to "City, street or address",
    "Поиск адреса отправляет введённый текст системному геокодеру телефона (на многих устройствах это сервис Google)." to "Address search sends the text you type to your phone's system geocoder (on many devices this is a Google service).",
    "Поиск адреса…" to "Searching address…",
    "Адрес найден" to "Address found",
    "Адрес не найден или геокодер недоступен" to "Address not found or geocoder unavailable",
    "Найти на карте" to "Find on map",
    "Применить новые координаты" to "Apply new coordinates",
    "Выберите точку на карте, затем нажмите «Сохранить GPS в JPEG» в разделе метаданных." to "Pick a point on the map, then tap “Save GPS to JPEG” in the metadata section.",
    "© OpenStreetMap contributors · тайлы требуют интернет; ранее загруженные доступны из кэша" to "© OpenStreetMap contributors · tiles need internet; previously loaded tiles are available from cache",
    "На устройстве нет приложения камеры" to "No camera app on this device",
    "Не удалось запустить камеру" to "Could not start the camera",
    "В буфере нет ссылки на изображение" to "No image link in the clipboard",
    "Не удалось прочитать буфер" to "Could not read the clipboard",
    "Отчёт скопирован" to "Report copied",
    "указанный сервер" to "the specified server",
    "Не удалось загрузить историю" to "Could not load history",
    "Проверка завершена, но историю сохранить не удалось" to "Check finished, but history could not be saved",
    "Ошибка чтения файла. Попробуйте другой источник." to "Could not read the file. Try another source.",
    "Не удалось открыть отчёт" to "Could not open the report",
    "Не удалось удалить отчёт" to "Could not delete the report",
    "Невозможно открыть файл" to "Cannot open the file",
    "CSV с историей сохранён" to "History CSV saved",
    "Не удалось сохранить CSV" to "Could not save CSV",
    "Не удалось открыть файл" to "Could not open the file",
    "CSV с метаданными сохранён" to "Metadata CSV saved",
    "JSON-отчёт сохранён" to "JSON report saved",
    "PDF-отчёт сохранён" to "PDF report saved",
    "Не удалось выполнить запрос." to "Request failed.",
    "Отправка в API доступна в Pro." to "Sending to an API is available in Pro.",
    "Не удалось завершить операцию" to "Could not complete the operation",
    "Это не изображение в поддерживаемом формате (JPEG, PNG, WebP, GIF, HEIC, AVIF)." to "This is not an image in a supported format (JPEG, PNG, WebP, GIF, HEIC, AVIF).",
    "Не удалось прочитать файл. Попробуй другой." to "Could not read the file. Try another one.",
    "Допускается до 50 фото за один запуск" to "Up to 50 photos per run",
    "Проверьте числа: GPS, ISO, диафрагму и выдержку" to "Check the numbers: GPS, ISO, aperture and shutter speed",
    "PRO · Без ограничений на очистку" to "PRO · Unlimited cleaning",
    "Дата оригинала" to "Date taken",
    "Камера и авторство" to "Camera and authorship",
    "Сначала выберите файлы" to "Choose files first",
    "Сохранение…" to "Saving…",
    "Не удалось открыть исходный файл" to "Could not open the source file",
    "Не удалось открыть файл назначения" to "Could not open the destination file",
    "GPS сохранён в новую копию JPEG" to "GPS saved to a new JPEG copy",
    "GPS / ИНТЕРАКТИВНАЯ КАРТА" to "GPS / INTERACTIVE MAP",
    "Точка выбрана. Сохраните копию фотографии." to "Point selected. Save a copy of the photo.",
    "Сохранить GPS в JPEG" to "Save GPS to JPEG",
    "Запись GPS пока поддерживается только для JPEG." to "Writing GPS is currently supported for JPEG only.",
    "Исходный файл недоступен: откройте оригинал фотографии." to "The source file is unavailable: open the original photo.",
    "В фотографии нет GPS-координат." to "The photo has no GPS coordinates.",
    "Показаны доступные поля, извлечённые локальными парсерами MetaCheck. Отсутствие тега не доказывает подлинность файла. GPS может содержать личные данные." to "Showing available fields extracted by MetaCheck's local parsers. A missing tag does not prove the file is authentic. GPS can contain personal data.",
    "Не удалось открыть покупку. Проверь Google Play и интернет." to "Could not start the purchase. Check Google Play and your connection.",
    "Ключ принят" to "Key accepted",
    "Некорректный ключ" to "Invalid key",
    "Разовая покупка через Google Play, без подписки." to "One-time purchase through Google Play, no subscription.",
    "В этом файле нет читаемых GPS-координат. Если вы уверены, что они были, выберите оригинал фото через «Выбрать файл» и разрешите доступ к геометкам." to "This file has no readable GPS coordinates. If you are sure there were some, choose the original photo with “Choose file” and allow access to photo location.",
    "ПРИЗНАКОВ" to "INDICATORS",
    "Не найдено приложение для открытия карты" to "No app found to open the map",
    "неизвестно" to "unknown",
    "Сеть используется только по вашему действию: карта OpenStreetMap, поиск адреса, внешний API (если включён) и Google Play для покупки Pro." to "The network is used only when you act: OpenStreetMap map, address search, your own API (if enabled) and Google Play for the Pro purchase.",
    "Карта OpenStreetMap загружается только по вашей кнопке. Нажатие «Открыть в картах» передаст координаты выбранному внешнему приложению." to "The OpenStreetMap map loads only when you press its button. Tapping “Open in maps” shares the coordinates with the external app you choose.",
)
