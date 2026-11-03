package com.example.metacheck.data.history

import com.example.metacheck.data.crypto.Crypto
import com.example.metacheck.sampleReport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Простая обратимая «шифровка» вместо Android Keystore, которого на JVM нет. */
private class XorCrypto : Crypto {
    override fun encrypt(plain: ByteArray) = ByteArray(plain.size) { (plain[it].toInt() xor 0x5A).toByte() }
    override fun decrypt(blob: ByteArray) = encrypt(blob)
}

class FileHistoryRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1_700_000_000_000L

    private fun repo(visible: () -> Int = { FileHistoryRepository.STORAGE_LIMIT }) =
        FileHistoryRepository(File(tmp.root, "reports"), XorCrypto(), { now++ }, { visible() })

    private fun storedFiles() = File(tmp.root, "reports").listFiles { f -> f.name.endsWith(".rep") }!!.size

    @Test
    fun saveListAndLoadRoundTrip() = runBlocking {
        val repo = repo()
        val report = sampleReport(name = "a.jpg", ai = true)
        val id = repo.save(report).getOrThrow()

        val entries = repo.list()
        assertEquals(1, entries.size)
        assertEquals("a.jpg", entries[0].fileName)
        assertTrue(entries[0].hasAiEvidence)
        assertTrue(entries[0].hasGps)
        assertEquals(report, repo.load(id))
    }

    @Test
    fun newestComesFirst() = runBlocking {
        val repo = repo()
        repo.save(sampleReport(name = "first.jpg"))
        repo.save(sampleReport(name = "second.jpg"))
        repo.save(sampleReport(name = "third.jpg"))
        assertEquals(listOf("third.jpg", "second.jpg", "first.jpg"), repo.list().map { it.fileName })
    }

    @Test
    fun visibleLimitShowsNewestOnly() = runBlocking {
        val repo = repo(visible = { 3 })
        for (i in 1..5) repo.save(sampleReport(name = "f$i.jpg"))
        assertEquals(listOf("f5.jpg", "f4.jpg", "f3.jpg"), repo.list().map { it.fileName })
    }

    /** Аудит, п. 18: статус Free в первые секунды после запуска не должен стирать отчёты Pro. */
    @Test
    fun lowerVisibleLimitNeverDeletesReports() = runBlocking {
        var limit = FileHistoryRepository.STORAGE_LIMIT
        val repo = repo(visible = { limit })
        for (i in 1..30) repo.save(sampleReport(name = "f$i.jpg"))
        limit = 10 // временно «Free»
        repo.save(sampleReport(name = "f31.jpg"))
        assertEquals(10, repo.list().size)
        assertEquals(31, storedFiles())
        limit = FileHistoryRepository.STORAGE_LIMIT // статус Pro прочитан
        assertEquals(31, repo.list().size)
        assertEquals("f1.jpg", repo.list().last().fileName)
    }

    @Test
    fun storageLimitPrunesOldestOnDisk() = runBlocking {
        val repo = repo()
        val total = FileHistoryRepository.STORAGE_LIMIT + 3
        for (i in 1..total) repo.save(sampleReport(name = "f$i.jpg"))
        assertEquals(FileHistoryRepository.STORAGE_LIMIT, storedFiles())
        assertEquals("f$total.jpg", repo.list().first().fileName)
        assertEquals("f4.jpg", repo.list().last().fileName)
    }

    @Test
    fun corruptedReportDoesNotShrinkVisibleList() = runBlocking {
        val repo = repo(visible = { 2 })
        repo.save(sampleReport(name = "old.jpg"))
        repo.save(sampleReport(name = "mid.jpg"))
        val id = repo.save(sampleReport(name = "new.jpg")).getOrThrow()
        File(tmp.root, "reports/$id.rep").writeBytes(ByteArray(40) { 7 })
        assertEquals(listOf("mid.jpg", "old.jpg"), repo.list().map { it.fileName })
    }

    @Test
    fun clearRemovesHiddenReportsToo() = runBlocking {
        val repo = repo(visible = { 1 })
        for (i in 1..4) repo.save(sampleReport(name = "f$i.jpg"))
        repo.clear()
        assertEquals(0, storedFiles())
    }

    @Test
    fun filesOnDiskAreNotPlaintext() = runBlocking {
        val repo = repo()
        repo.save(sampleReport(name = "secret-name.jpg"))
        val files = File(tmp.root, "reports").listFiles()!!
        assertTrue(files.isNotEmpty())
        files.forEach { assertFalse(String(it.readBytes(), Charsets.ISO_8859_1).contains("secret-name")) }
    }

    @Test
    fun pathTraversalIsRejected() = runBlocking {
        val repo = repo()
        val victim = File(tmp.root, "victim.rep").apply { writeText("keep me") }
        assertNull(repo.load("../victim"))
        repo.delete("../victim")
        assertTrue(victim.exists())
        assertNull(repo.load("not-an-id"))
    }

    @Test
    fun corruptedFileIsSkipped() = runBlocking {
        val repo = repo()
        val id = repo.save(sampleReport()).getOrThrow()
        File(tmp.root, "reports/$id.rep").writeBytes(ByteArray(40) { 7 })
        assertTrue(repo.list().isEmpty())
        assertNull(repo.load(id))
    }

    @Test
    fun deleteAndClear() = runBlocking {
        val repo = repo()
        val id = repo.save(sampleReport(name = "a.jpg")).getOrThrow()
        repo.save(sampleReport(name = "b.jpg"))
        repo.delete(id)
        assertEquals(listOf("b.jpg"), repo.list().map { it.fileName })
        assertNotNull(repo.list().firstOrNull())
        repo.clear()
        assertTrue(repo.list().isEmpty())
    }
}
