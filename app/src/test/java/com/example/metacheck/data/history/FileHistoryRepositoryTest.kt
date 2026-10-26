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

    private fun repo(max: Int = 50) =
        FileHistoryRepository(File(tmp.root, "reports"), XorCrypto(), { now++ }, { max })

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
    fun oldReportsArePruned() = runBlocking {
        val repo = repo(max = 3)
        for (i in 1..5) repo.save(sampleReport(name = "f$i.jpg"))
        assertEquals(listOf("f5.jpg", "f4.jpg", "f3.jpg"), repo.list().map { it.fileName })
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
