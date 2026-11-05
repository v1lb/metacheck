package com.example.metacheck.domain.model

enum class ProSource { NONE, PLAY, LICENSE }

/** Параметры лицензионного ключа, выданного вручную. [expiresAt] в секундах Unix-времени, null = бессрочно. */
data class LicenseInfo(val id: String, val expiresAt: Long?, val note: String?)

data class Entitlement(
    val source: ProSource = ProSource.NONE,
    val licenseExpiresAt: Long? = null,
    /** Покупка оформлена, но не оплачена (наличные, перевод, ожидание банка). Pro ещё НЕ даёт. */
    val purchasePending: Boolean = false,
) {
    val isPro: Boolean get() = source != ProSource.NONE

    companion object {
        /** Покупка в Google Play важнее ключа: у неё нет срока. */
        fun resolve(playOwned: Boolean, license: LicenseInfo?, pending: Boolean = false): Entitlement = when {
            playOwned -> Entitlement(ProSource.PLAY)
            license != null -> Entitlement(ProSource.LICENSE, license.expiresAt, purchasePending = pending)
            else -> Entitlement(purchasePending = pending)
        }
    }
}

/** Состояние покупок в Google Play на момент запроса. */
data class PurchaseSnapshot(val owned: Boolean, val pending: Boolean)

/** Итог покупки для показа пользователю. */
enum class PurchaseEvent {
    /** Оплачено, Pro активен. */
    COMPLETED,
    /** Оформлено, ждёт оплаты: Pro включится сам, когда платёж пройдёт. */
    PENDING,
    /** Товар уже куплен (например, после переустановки): восстанавливаем. */
    ALREADY_OWNED,
    CANCELED,
    FAILED,
    /** Покупки изменились без действия пользователя (возврат, другой товар): только перечитать. */
    UPDATED,
    /** Пользователь поддержал разработчика разовым платежом. Pro это не включает. */
    TIP_COMPLETED;

    /** После этих событий нужно перечитать покупки у Play. */
    val needsRefresh: Boolean get() = this != CANCELED && this != FAILED && this != TIP_COMPLETED
}

class ProRequiredException : Exception("Pro feature")

/** Причина отказа ключа Pro. Текст для пользователя подбирает интерфейс по коду. */
enum class LicenseError { NOT_ENABLED, MALFORMED, BAD_SIGNATURE, EXPIRED }

class LicenseException(val code: LicenseError) : Exception(code.name)
