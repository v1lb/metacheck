package com.example.metacheck.domain.model

enum class ProSource { NONE, PLAY, LICENSE }

/** Параметры лицензионного ключа, выданного вручную. [expiresAt] в секундах Unix-времени, null = бессрочно. */
data class LicenseInfo(val id: String, val expiresAt: Long?, val note: String?)

data class Entitlement(
    val source: ProSource = ProSource.NONE,
    val licenseExpiresAt: Long? = null,
) {
    val isPro: Boolean get() = source != ProSource.NONE

    companion object {
        /** Покупка в Google Play важнее ключа: у неё нет срока. */
        fun resolve(playOwned: Boolean, license: LicenseInfo?): Entitlement = when {
            playOwned -> Entitlement(ProSource.PLAY)
            license != null -> Entitlement(ProSource.LICENSE, license.expiresAt)
            else -> Entitlement()
        }
    }
}

class ProRequiredException : Exception("Эта функция доступна в Pro")

/** Сообщение предназначено для показа пользователю. */
class LicenseException(message: String) : Exception(message)
