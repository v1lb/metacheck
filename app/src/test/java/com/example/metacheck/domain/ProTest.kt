package com.example.metacheck.domain

import android.app.Activity
import com.example.metacheck.domain.model.ApiResult
import com.example.metacheck.domain.model.Entitlement
import com.example.metacheck.domain.model.LicenseInfo
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.model.ProRequiredException
import com.example.metacheck.domain.model.ProSource
import com.example.metacheck.domain.repository.ApiRepository
import com.example.metacheck.domain.repository.EntitlementRepository
import com.example.metacheck.domain.repository.ExportRepository
import com.example.metacheck.domain.usecase.CheckViaApiUseCase
import com.example.metacheck.domain.usecase.ExportReportUseCase
import com.example.metacheck.sampleReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeEntitlements(pro: Boolean) : EntitlementRepository {
    private val state = MutableStateFlow(if (pro) Entitlement(ProSource.PLAY) else Entitlement())
    override val entitlement: StateFlow<Entitlement> = state
    override suspend fun refresh() {}
    override suspend fun priceText(): String? = null
    override fun launchPurchase(activity: Activity) = false
    override suspend fun redeem(token: String): Result<LicenseInfo> = Result.failure(UnsupportedOperationException())
    override fun removeLicense() {}
}

private class CountingExport : ExportRepository {
    var calls = 0
    override suspend fun exportJson(report: MetaReport, target: String): Result<Unit> { calls++; return Result.success(Unit) }
    override suspend fun exportPdf(report: MetaReport, target: String): Result<Unit> { calls++; return Result.success(Unit) }
}

private class CountingApi : ApiRepository {
    var calls = 0
    override suspend fun send(report: MetaReport): Result<ApiResult> { calls++; return Result.success(ApiResult(200, "ok")) }
}

class ProTest {

    @Test
    fun exportIsBlockedWithoutPro() = runBlocking {
        val repo = CountingExport()
        val useCase = ExportReportUseCase(repo, FakeEntitlements(pro = false))
        assertTrue(useCase.json(sampleReport(), "content://x").exceptionOrNull() is ProRequiredException)
        assertTrue(useCase.pdf(sampleReport(), "content://x").exceptionOrNull() is ProRequiredException)
        assertEquals(0, repo.calls)
    }

    @Test
    fun exportWorksWithPro() = runBlocking {
        val repo = CountingExport()
        val useCase = ExportReportUseCase(repo, FakeEntitlements(pro = true))
        assertTrue(useCase.json(sampleReport(), "content://x").isSuccess)
        assertTrue(useCase.pdf(sampleReport(), "content://x").isSuccess)
        assertEquals(2, repo.calls)
    }

    @Test
    fun apiIsBlockedWithoutPro() = runBlocking {
        val repo = CountingApi()
        val result = CheckViaApiUseCase(repo, FakeEntitlements(pro = false))(sampleReport())
        assertTrue(result.exceptionOrNull() is ProRequiredException)
        assertEquals(0, repo.calls)
    }

    @Test
    fun apiWorksWithPro() = runBlocking {
        val repo = CountingApi()
        val result = CheckViaApiUseCase(repo, FakeEntitlements(pro = true))(sampleReport())
        assertEquals(200, result.getOrThrow().statusCode)
        assertEquals(1, repo.calls)
    }

    @Test
    fun entitlementResolution() {
        val license = LicenseInfo("id", 5000L, null)
        assertFalse(Entitlement.resolve(false, null).isPro)
        assertEquals(ProSource.PLAY, Entitlement.resolve(true, null).source)
        assertEquals(ProSource.PLAY, Entitlement.resolve(true, license).source)
        val byLicense = Entitlement.resolve(false, license)
        assertEquals(ProSource.LICENSE, byLicense.source)
        assertEquals(5000L, byLicense.licenseExpiresAt)
        assertNull(Entitlement.resolve(false, null).licenseExpiresAt)
    }
}
