package com.example.metacheck.domain

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

    @Test fun freeEditionCanWriteOneFilePerDay() = runBlocking {
        val store = FakeStore(); val quota = Quota()
        val use = MetadataStudioUseCase(store, EntitlementFake(), quota)
        assertTrue(use.strip("a", "b").isSuccess)
        assertTrue(use.wipeGps("a", "c").exceptionOrNull() is DailyLimitReachedException)
        assertEquals(1, quota.count)
        assertEquals(1, store.writes)
    }

    @Test fun failedWriteDoesNotConsumeQuota() = runBlocking {
        val store = FakeStore().apply { fail = true }; val quota = Quota()
        val use = MetadataStudioUseCase(store, EntitlementFake(), quota)
        assertTrue(use.wipeGps("a", "b").isFailure)
        assertEquals(0, quota.count)
    }

    @Test fun freeUserCannotEditExif() = runBlocking {
        val store = FakeStore()
        val use = MetadataStudioUseCase(store, EntitlementFake(), Quota())
        assertTrue(use.edit("a", "b", MetadataPatch(model = "Test"))
            .exceptionOrNull() is ProRequiredException)
        assertEquals(0, store.writes)
    }

    @Test fun proUnlimitedWritesAndEdits() = runBlocking {
        val store = FakeStore(); val quota = Quota()
        val use = MetadataStudioUseCase(store, EntitlementFake(Entitlement(ProSource.PLAY)), quota)
        repeat(3) { assertTrue(use.strip("a", "b").isSuccess) }
        assertTrue(use.edit("a", "b", MetadataPatch(make = "Nikon")).isSuccess)
        assertEquals(4, store.writes)
        assertEquals(0, quota.count)
    }

    @Test fun concurrentFreeRequestsCannotBothSucceed() = runBlocking {
        val store = FakeStore(); val quota = Quota()
        val use = MetadataStudioUseCase(store, EntitlementFake(), quota)
        val first = async { use.strip("a", "b") }
        val second = async { use.wipeGps("a", "c") }
        assertEquals(1, listOf(first.await(), second.await()).count { it.isSuccess })
        assertEquals(1, store.writes)
    }
}
