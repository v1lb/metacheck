package com.example.metacheck.data.license

object LicenseKey {
    /**
     * Открытый ключ для проверки лицензионных ключей Pro: base64 от X.509 SubjectPublicKeyInfo (ECDSA P-256).
     * Получи его командой `python3 tools/license_tool.py keygen` и вставь сюда.
     * Пока строка пустая, ввод ключей в приложении отключён (поле скрыто). Закрытый ключ в приложение класть нельзя.
     *
     * [ТРЕБУЕТСЯ ВЫ] Если хотите выдавать ключи Pro вручную (партнёрам, тестерам), создайте пару ключей
     * и вставьте открытый ключ сюда. Если нет, оставьте пустым: покупка Pro через Google Play работает отдельно.
     */
    const val PUBLIC_KEY_BASE64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE84nzSRhInK2CpP4c9eaFKRU/2FPI3GraJ8DeyFpDbO1WP2BdT8vgJfGgWLxr/HThgXsfqfE6DiFPE7XD5iboFA=="
}
