package com.example.metacheck.domain.usecase

import com.example.metacheck.domain.model.ApiResult
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.model.ProRequiredException
import com.example.metacheck.domain.repository.ApiRepository
import com.example.metacheck.domain.repository.EntitlementRepository
import com.example.metacheck.domain.repository.ExportRepository

/** Экспорт доступен только в Pro. Проверка сделана здесь, а не только в интерфейсе. */
class ExportReportUseCase(
    private val repository: ExportRepository,
    private val entitlements: EntitlementRepository,
) {
    suspend fun json(report: MetaReport, target: String): Result<Unit> =
        if (entitlements.entitlement.value.isPro) repository.exportJson(report, target)
        else Result.failure(ProRequiredException())

    suspend fun pdf(report: MetaReport, target: String): Result<Unit> =
        if (entitlements.entitlement.value.isPro) repository.exportPdf(report, target)
        else Result.failure(ProRequiredException())
}

class CheckViaApiUseCase(
    private val repository: ApiRepository,
    private val entitlements: EntitlementRepository,
) {
    suspend operator fun invoke(report: MetaReport): Result<ApiResult> =
        if (entitlements.entitlement.value.isPro) repository.send(report)
        else Result.failure(ProRequiredException())
}
