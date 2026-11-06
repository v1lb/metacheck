package com.example.metacheck.domain

import android.app.Activity
import com.example.metacheck.domain.metadata.BatchProgress
import com.example.metacheck.domain.metadata.DailyCleanQuota
import com.example.metacheck.domain.metadata.MetadataPatch
import com.example.metacheck.domain.metadata.MetadataRewriteRepository
import com.example.metacheck.domain.metadata.MetadataStudioUseCase
import com.example.metacheck.domain.metadata.MetadataWriteResult
import com.example.metacheck.domain.model.Entitlement
import com.example.metacheck.domain.model.LicenseInfo
import com.example.metacheck.domain.model.ProRequiredException
import com.example.metacheck.domain.model.ProSource
import com.example.metacheck.domain.repository.EntitlementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Очистка видео — только Pro и не расходует дневной лимит фото. */
class VideoCleanGateTest {
    private class Quota : DailyCleanQuota {
        var count = 0
        override fun usedToday() = count
        override fun recordSuccess(): Boolean { count++; return true }
    }

    private class Entitlements(pro: Boolean) : EntitlementRepository {
        override val entitlement: StateFlow<Entitlement> =
            MutableStateFlow(if (pro) Entitlement(ProSource.PLAY) else Entitlement())
        override suspend fun refresh() {}
        override suspend fun priceText(): String? = null
        override fun launchPurchase(activity: Activity): Boolean = false
        override suspend fun redeem(token: String): Result<LicenseInfo> = Result.failure(Exception("test"))
        override fun removeLicense() {}
    }

    private class Store : MetadataRewriteRepository {
        var photo = 0
        var video = 0
        private fun ok() = MetadataWriteResult(1, false, "JPEG", true)
        override suspend fun stripCopy(source: String, destination: String) = ok().also { photo++ }
        override suspend fun wipeGpsCopy(source: String, destination: String) = ok().also { photo++ }
        override suspend fun editCopy(source: String, destination: String, patch: MetadataPatch) = ok()
        override suspend fun overwriteWithBackup(source: String, backupDestination: String, patch: MetadataPatch?) = ok()
        override suspend fun stripBatch(sources: List<String>, destinationTree: String, progress: (BatchProgress) -> Unit) = BatchProgress()
        override suspend fun isVideo(source: String) = source.endsWith(".mp4")
        override suspend fun stripVideoCopy(source: String, destination: String) =
            MetadataWriteResult(1, false, "MP4", true).also { video++ }
    }

    @Test fun freeUserGetsProRequiredForVideo() = runBlocking {
        val store = Store(); val quota = Quota()
        val use = MetadataStudioUseCase(store, Entitlements(pro = false), quota)
        assertTrue(use.strip("clip.mp4", "out").exceptionOrNull() is ProRequiredException)
        assertTrue(use.wipeGps("clip.mp4", "out").exceptionOrNull() is ProRequiredException)
        assertEquals(0, store.video)
        assertEquals(0, quota.count)
        // Фото по-прежнему чистятся бесплатно.
        assertTrue(use.strip("photo.jpg", "out").isSuccess)
        assertEquals(1, store.photo)
    }

    @Test fun proUserCleansVideoWithoutQuota() = runBlocking {
        val store = Store(); val quota = Quota()
        val use = MetadataStudioUseCase(store, Entitlements(pro = true), quota)
        repeat(DailyCleanQuota.FREE_DAILY_LIMIT + 2) { assertTrue(use.strip("clip.mp4", "out").isSuccess) }
        assertTrue(use.wipeGps("clip.mp4", "out").isSuccess)
        assertEquals(DailyCleanQuota.FREE_DAILY_LIMIT + 3, store.video)
        assertEquals(0, store.photo)
        assertEquals(0, quota.count)
    }
}
