package com.example.metacheck.data.license

object LicenseKey {
    /**
     * Открытый ключ для проверки лицензионных ключей Pro: base64 от X.509 SubjectPublicKeyInfo (ECDSA P-256).
     * Получи его командой `python3 tools/license_tool.py keygen` и вставь сюда.
     * Пока строка пустая, ввод ключей в приложении отключён. Закрытый ключ в приложение класть нельзя.
     */
    const val PUBLIC_KEY_BASE64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEvqvPFlaY83gbqjG9pToWt9r/S4R6ToGWtXaORbP1t/pSmtBkVSRCbVCOCIdU8Ucss3E8WQhLDq8pt33N4AyxWg=="
}
