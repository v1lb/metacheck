package com.example.metacheck

import android.app.Application
import android.content.Context
import com.example.metacheck.data.ImageRepositoryImpl
import com.example.metacheck.data.UriAccess
import com.example.metacheck.data.api.ApiRepositoryImpl
import com.example.metacheck.data.billing.PlayBillingGateway
import com.example.metacheck.data.metadata.MetadataRewriteRepositoryImpl
import com.example.metacheck.data.metadata.PreferencesDailyCleanQuota
import com.example.metacheck.domain.metadata.MetadataStudioUseCase
import com.example.metacheck.data.crypto.Crypto
import com.example.metacheck.data.crypto.KeystoreCrypto
import com.example.metacheck.data.entitlement.EntitlementRepositoryImpl
import com.example.metacheck.data.export.ExportRepositoryImpl
import com.example.metacheck.data.history.FileHistoryRepository
import com.example.metacheck.data.license.LicenseKey
import com.example.metacheck.data.license.LicenseVerifier
import com.example.metacheck.data.settings.SettingsRepositoryImpl
import com.example.metacheck.domain.repository.EntitlementRepository
import com.example.metacheck.domain.repository.HistoryRepository
import com.example.metacheck.domain.repository.ImageRepository
import com.example.metacheck.domain.repository.SettingsRepository
import com.example.metacheck.domain.usecase.AnalyzeImageUseCase
import com.example.metacheck.domain.usecase.CheckViaApiUseCase
import com.example.metacheck.domain.usecase.ExportReportUseCase
import com.example.metacheck.ui.preferences.UiPreferences
import java.io.File

/** Ручной DI: для проекта такого размера Hilt не нужен. */
class AppContainer(app: Application) {
    val contentResolver = app.contentResolver
    val uriAccess = UriAccess(app.contentResolver)
    val uiPreferences = UiPreferences(app)
    private val crypto: Crypto = KeystoreCrypto()
    private val imageRepository: ImageRepository = ImageRepositoryImpl(app.contentResolver)

    val settings: SettingsRepository =
        SettingsRepositoryImpl(app.getSharedPreferences("settings", Context.MODE_PRIVATE), crypto)

    val entitlements: EntitlementRepository = EntitlementRepositoryImpl(
        prefs = app.getSharedPreferences("entitlement", Context.MODE_PRIVATE),
        crypto = crypto,
        verifier = LicenseVerifier(LicenseKey.PUBLIC_KEY_BASE64),
        billing = PlayBillingGateway(app),
    )

    /**
     * Free показывает 10 последних отчётов, Pro — 50. Хранится всегда до 50: тариф только скрывает лишнее.
     * current() дожидается чтения статуса Pro из кэша, поэтому сразу после запуска Pro не считается Free.
     */
    val history: HistoryRepository = FileHistoryRepository(
        dir = File(app.filesDir, "reports"),
        crypto = crypto,
        visibleLimit = { if (entitlements.current().isPro) FileHistoryRepository.STORAGE_LIMIT else FREE_HISTORY_LIMIT },
    )

    val analyzeImage = AnalyzeImageUseCase(imageRepository)
    val exportReport = ExportReportUseCase(ExportRepositoryImpl(app.contentResolver), entitlements)
    val metadataStudio = MetadataStudioUseCase(MetadataRewriteRepositoryImpl(app), entitlements, PreferencesDailyCleanQuota(app, crypto))
    val checkViaApi = CheckViaApiUseCase(ApiRepositoryImpl(settings), entitlements)

    companion object {
        /** Сколько отчётов истории видно без Pro. Совпадает с текстами dash_history_limit_free и pro_free_*. */
        const val FREE_HISTORY_LIMIT = 10
    }
}
