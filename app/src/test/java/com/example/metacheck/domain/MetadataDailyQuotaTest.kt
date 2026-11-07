package com.example.metacheck.domain

import com.example.metacheck.domain.metadata.DailyCleanQuota

import android.app.Activity
import com.example.metacheck.domain.metadata.*
import com.example.metacheck.domain.model.*
import com.example.metacheck.domain.repository.EntitlementRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.*
import org.junit.Test

class MetadataDailyQuotaTest {
    private class Quota : DailyCleanQuota {
        var count = 0
        override fun usedToday() = count
        override fun recordSuccess(): Boolean { count++; return true }
    }

    private class EntitlementFake(initial: Entitlement = Entitlement()) : EntitlementRepository {
        override val entitlement: StateFlow<Entitlement> = MutableStateFlow(initial)
        override suspend fun refresh() {}
        override suspend fun priceText(): String? = null
        override fun launchPurchase(activity: Activity): Boolean = false
        override suspend fun redeem(token: String): Result<LicenseInfo> = Result.failure(Exception("test"))
        override fun removeLicense() {}
    }

    private class FakeStore : MetadataRewriteRepository {
        var writes = 0
        var fail = false
        override suspend fun stripCopy(source: String, destination: String): MetadataWriteResult = write()
        override suspend fun wipeGpsCopy(source: String, destination: String): MetadataWriteResult = write()
        override suspend fun editCopy(source: String, destination: String, patch: MetadataPatch): MetadataWriteResult = write()
        override suspend fun overwriteWithBackup(source: String, backupDestination: String, patch: MetadataPatch?): MetadataWriteResult = write()
        override suspend fun stripBatch(sources: List<String>, destinationTree: String,
            progress: (BatchProgress) -> Unit): BatchProgress = BatchProgress(total = sources.size, succeeded = sources.size)
        private fun write(): MetadataWriteResult {
            if (fail) throw IllegalStateException("disk full")
            writes++
            return MetadataWriteResult(10L, false, "JPEG", true)
        }
    }

    @Test fun freeEditionAllowsDailyLimitThenBlocks() = runBlocking {
        val store = FakeStore(); val quota = Quota()
        val use = MetadataStudioUseCase(store, EntitlementFake(), quota)
        repeat(DailyCleanQuota.FREE_DAILY_LIMIT) { assertTrue(use.strip("a", "b").isSuccess) }
        assertTrue(use.wipeGps("a", "c").exceptionOrNull() is DailyLimitReachedException)
        assertEquals(DailyCleanQuota.FREE_DAILY_LIMIT, quota.count)
        assertEquals(DailyCleanQuota.FREE_DAILY_LIMIT, store.writes)
    }

    @Test fun failedWriteDoesNotConsumeQuota() = runBlocking {
        val store = FakeStore().apply { fail = true }; val quota = Quota()
        val use = MetadataStudioUseCase(store, EntitlementFake(), quota)
        assertTrue(use.wipeGps("a", "b").isFailure)
        assertEquals(0, quota.count)
    }

    @Test fun freeUserCanEditExifAndConsumesQuota() = runBlocking {
        val store = FakeStore(); val quota = Quota()
        val use = MetadataStudioUseCase(store, EntitlementFake(), quota)
        assertTrue(use.edit("a", "b", MetadataPatch(model = "Test")).isSuccess)
        assertEquals(1, store.writes)
        assertEquals(1, quota.count)
    }

    @Test fun freeUserBatchesUpToFivePerRunWithinDailyLimit() = runBlocking {
        val store = FakeStore(); val quota = Quota()
        val use = MetadataStudioUseCase(store, EntitlementFake(), quota)
        assertTrue(use.batch(List(6) { "f$it" }, "tree") {}.exceptionOrNull() is ProRequiredException)
        assertTrue(use.batch(List(5) { "f$it" }, "tree") {}.isSuccess)
        assertEquals(5, quota.count)
        assertTrue(use.batch(List(5) { "g$it" }, "tree") {}.isSuccess)
        assertTrue(use.batch(listOf("h"), "tree") {}.exceptionOrNull() is DailyLimitReachedException)
        assertEquals(DailyCleanQuota.FREE_DAILY_LIMIT, quota.count)
    }

    @Test fun batchLimitsPerRun() {
        assertEquals(5, BatchLimits.perRun(isPro = false, usedToday = 0))
        assertEquals(2, BatchLimits.perRun(isPro = false, usedToday = 8))
        assertEquals(0, BatchLimits.perRun(isPro = false, usedToday = 12))
        assertEquals(50, BatchLimits.perRun(isPro = true, usedToday = 99))
    }

    @Test fun auditPutsRiskiestFirst() {
        val r = AuditResult.of(3, listOf(
            AuditFinding("a", "a", location = false, serial = false, person = false),
            AuditFinding("b", "b", location = true, serial = false, person = false),
            AuditFinding("c", "c", location = false, serial = true, person = true),
        ), limited = false)
        assertEquals(listOf("b", "c", "a"), r.findings.map { it.uri })
        assertEquals(1, r.withLocation); assertEquals(1, r.otherOnly)
    }

    @Test fun proUnlimitedWritesAndEdits() = runBlocking {
        val store = FakeStore(); val quota = Quota()
        val use = MetadataStudioUseCase(store, EntitlementFake(Entitlement(ProSource.PLAY)), quota)
        repeat(3) { assertTrue(use.strip("a", "b").isSuccess) }
        assertTrue(use.edit("a", "b", MetadataPatch(make = "Nikon")).isSuccess)
        assertEquals(4, store.writes)
        assertEquals(0, quota.count)
    }

    @Test fun counterFailureDoesNotFailSuccessfulWrite() = runBlocking {
        val store = FakeStore()
        val brokenCounter = object : DailyCleanQuota {
            override fun usedToday() = 0
            override fun recordSuccess() = false
        }
        val use = MetadataStudioUseCase(store, EntitlementFake(), brokenCounter)
        assertTrue(use.strip("a", "b").isSuccess)
        assertEquals(1, store.writes)
    }

    @Test fun concurrentFreeRequestsRespectDailyLimit() = runBlocking {
        val store = FakeStore(); val quota = Quota()
        val use = MetadataStudioUseCase(store, EntitlementFake(), quota)
        repeat(DailyCleanQuota.FREE_DAILY_LIMIT - 1) { assertTrue(use.strip("a", "b").isSuccess) }
        val first = async { use.strip("a", "b") }
        val second = async { use.wipeGps("a", "c") }
        assertEquals(1, listOf(first.await(), second.await()).count { it.isSuccess })
        assertEquals(DailyCleanQuota.FREE_DAILY_LIMIT, store.writes)
    }
}
