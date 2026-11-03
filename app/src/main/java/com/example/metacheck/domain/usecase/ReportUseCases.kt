package com.example.metacheck.domain.usecase

import com.example.metacheck.domain.model.ApiResult
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.model.ProRequiredException
import com.example.metacheck.domain.repository.ApiRepository
import com.example.metacheck.domain.repository.EntitlementRepository
import com.example.metacheck.domain.repository.ExportRepository
import com.example.metacheck.domain.text.MetaTexts

/** Экспорт доступен только в Pro. Проверка сделана здесь, а не только в интерфейсе. */
class ExportReportUseCase(
    private val repository: ExportRepository,
    private val entitlements: EntitlementRepository,
) {
    suspend fun json(report: MetaReport, target: String, texts: MetaTexts): Result<Unit> =
        if (entitlements.current().isPro) repository.exportJson(report, target, texts)
        else Result.failure(ProRequiredException())

    suspend fun pdf(report: MetaReport, target: String, texts: MetaTexts): Result<Unit> =
        if (entitlements.current().isPro) repository.exportPdf(report, target, texts)
        else Result.failure(ProRequiredException())
}

class CheckViaApiUseCase(
    private val repository: ApiRepository,
    private val entitlements: EntitlementRepository,
) {
    suspend operator fun invoke(report: MetaReport, texts: MetaTexts): Result<ApiResult> =
        if (entitlements.current().isPro) repository.send(report, texts)
        else Result.failure(ProRequiredException())
}
