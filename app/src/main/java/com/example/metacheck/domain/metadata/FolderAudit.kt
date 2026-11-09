package com.example.metacheck.domain.metadata

/** Что выдаёт одно фото из проверенной папки. */
data class AuditFinding(
    val uri: String,
    val name: String,
    val location: Boolean,
    val serial: Boolean,
    val person: Boolean,
) {
    /** Чем выше, тем раньше фото попадает в очистку. */
    val risk: Int get() = (if (location) 4 else 0) + (if (serial) 2 else 0) + (if (person) 1 else 0)
}

/**
 * Итог проверки папки. [findings] — только фото с метаданными, самые рискованные первыми.
 * [limited]: в папке больше фото, чем проверяется за раз.
 */
data class AuditResult(
    val scanned: Int,
    val findings: List<AuditFinding>,
    val limited: Boolean = false,
) {
    val withLocation: Int get() = findings.count { it.location }
    val withSerial: Int get() = findings.count { it.serial }
    val withPerson: Int get() = findings.count { it.person }
    val otherOnly: Int get() = findings.count { it.risk == 0 }

    companion object {
        fun of(scanned: Int, all: List<AuditFinding>, limited: Boolean) =
            AuditResult(scanned, all.sortedByDescending { it.risk }, limited)
    }
}

/** Проверка всех фото в выбранной папке на устройстве. Ничего не меняет и никуда не отправляет. */
interface FolderAuditRepository {
    suspend fun scan(tree: String, progress: (Int) -> Unit): AuditResult

    companion object {
        const val MAX_FILES = 1000
        const val MAX_DEPTH = 3
    }
}

/** Сколько фото очищать за один запуск пакета. */
object BatchLimits {
    const val FREE_PER_RUN = 5
    const val PRO_PER_RUN = 50

    /** Бесплатно — не больше 5 за раз и не больше остатка дневного лимита. */
    fun perRun(isPro: Boolean, usedToday: Int): Int =
        if (isPro) PRO_PER_RUN else minOf(FREE_PER_RUN, (DailyCleanQuota.FREE_DAILY_LIMIT - usedToday).coerceAtLeast(0))
}
