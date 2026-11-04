package com.example.metacheck.data.billing

object PlayPublicKey {
    /**
     * Открытый ключ лицензирования приложения в Google Play (RSA, base64). Им проверяется подпись
     * каждой покупки: подделанная покупка (Lucky Patcher и подобные) Pro не открывает (аудит, п. 26).
     *
     * [ТРЕБУЕТСЯ ВЫ] Play Console → ваше приложение → Монетизация → Настройка монетизации →
     * «Лицензирование» → скопируйте ключ base64 целиком и вставьте сюда. Ключ открытый, хранить его
     * в репозитории безопасно. Релизный AAB с пустым ключом не собирается (release-aab.yml).
     * Пока строка пустая (отладка до появления приложения в Play Console), подпись не проверяется.
     */
    const val BASE64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAh9Y6RT7US0MYw0uEcFIL9tOTDLFawKi4RnvHVkeduFKJp4aEXoyPxVr7uF7XOFt0I9L5Ig1kMukKwMB4ooP35bYapgyt4yj/xd5LK9TTnGuRmFARwtPiQ/6POmZjOWu7+o8vxXpSi8MaUpxBpC6o1Hk6mJEN0TQ8gFhNxBGnQnpsSyWz0mjTYA8/PbJpJHAHaFXj15RtNZ8NRwu1FC8unl4APNHXJszdERB+N+rkrmD+vXn0Xg77VFB3q+GpgmCFQZdP101nvXTQEq3pThvfF7H1MuNO9oj68M+HaahUri4ZqEV7kp3Pexq3eFl3zMOlWfnioSzLc6gqzx/xDGsUuQIDAQAB"
}
