package com.example.metacheck.ui

import com.example.metacheck.domain.metadata.MetadataPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

/** Регрессия аудита, п. 3: потеря состояния не должна превращать правку EXIF в полную очистку оригинала. */
class PendingWritesTest {

    private class MapStore(val map: HashMap<String, Any?> = HashMap()) : StateStore {
        override fun get(key: String) = map[key]
        override fun set(key: String, value: Any?) { map[key] = value }
    }

    /** Имитация смерти процесса: SavedStateHandle сериализуется в Bundle и восстанавливается. */
    private fun MapStore.afterProcessDeath(): MapStore {
        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { o -> o.writeObject(HashMap(map)) } }.toByteArray()
        @Suppress("UNCHECKED_CAST")
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() } as HashMap<String, Any?>
        return MapStore(restored)
    }

    private val datePatch = MetadataPatch(dateTaken = "2024:05:01 10:00:00")
    private val src = "content://docs/document/1"

    @Test fun editOverwriteSurvivesProcessDeath_andStaysAnEdit() {
        val store = MapStore()
        PendingWrites(store).apply { requestOverwrite(src, OverwriteOp.Edit(datePatch)); assertTrue(confirmOverwrite()) }

        val op = PendingWrites(store.afterProcessDeath()).takeConfirmedOverwrite()

        assertEquals(src, op!!.source)
        assertEquals(OverwriteOp.Edit(datePatch), op.op)
    }

    @Test fun stripOverwriteSurvivesProcessDeath() {
        val store = MapStore()
        PendingWrites(store).apply { requestOverwrite(src, OverwriteOp.Strip); confirmOverwrite() }
        val op = PendingWrites(store.afterProcessDeath()).takeConfirmedOverwrite()!!
        assertTrue(op.op is OverwriteOp.Strip)
        assertTrue(op.op === OverwriteOp.Strip) // readResolve сохраняет синглтон
    }

    @Test fun lostState_neverFallsBackToStrip() {
        // Состояние потеряно полностью: результат SAF пришёл, а операции нет.
        assertNull(PendingWrites(MapStore()).takeConfirmedOverwrite())
        assertNull(PendingWrites(MapStore()).takeEditCopy())
    }

    @Test fun unconfirmedOverwriteIsNotExecuted() {
        val w = PendingWrites(MapStore())
        w.requestOverwrite(src, OverwriteOp.Edit(datePatch))
        assertNull(w.takeConfirmedOverwrite())
        assertNull(w.current)
    }

    @Test fun resultIsConsumedOnce() {
        val w = PendingWrites(MapStore())
        w.requestEditCopy(src, datePatch)
        assertEquals(datePatch, w.takeEditCopy()!!.patch)
        assertNull(w.takeEditCopy())
    }

    @Test fun mismatchedCallbackClearsAndDoesNothing() {
        val w = PendingWrites(MapStore())
        w.requestEditCopy(src, datePatch)
        assertNull(w.takeConfirmedOverwrite())
        assertNull(w.current)
    }

    @Test fun cancelAndConfirmWithoutPending() {
        val w = PendingWrites(MapStore())
        assertFalse(w.confirmOverwrite())
        w.requestOverwrite(src, OverwriteOp.Strip)
        w.cancel()
        assertNull(w.current)
    }

    @Test fun sourceIsCapturedAtRequestTime() {
        val w = PendingWrites(MapStore())
        w.requestOverwrite(src, OverwriteOp.Strip); w.confirmOverwrite()
        // UI мог сменить выбранный файл, операция всё равно относится к исходному.
        assertEquals(src, w.takeConfirmedOverwrite()!!.source)
    }

    @Test fun patchIsSerializable() {
        assertTrue(MetadataPatch() is Serializable)
    }
}
