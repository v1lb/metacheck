package com.example.metacheck.ui

import com.example.metacheck.domain.metadata.MetadataPatch
import java.io.Serializable

/**
 * Что именно сделать с оригиналом при перезаписи. Режим задаётся явно:
 * раньше «patch == null» означало полную очистку, и потерянный при пересоздании Activity патч
 * незаметно превращал «поправить дату» в «стереть все метаданные и пережать JPEG».
 */
sealed interface OverwriteOp : Serializable {
    data object Strip : OverwriteOp {
        private fun readResolve(): Any = Strip
    }

    data class Edit(val patch: MetadataPatch) : OverwriteOp
}

/**
 * Операция, ожидающая ответа системного диалога SAF. Хранится в SavedStateHandle, поэтому переживает
 * поворот экрана, смену темы/языка и смерть процесса, пока открыт диалог выбора файла.
 * Источник фиксируется в момент запроса, а не читается из UI при возврате.
 */
sealed interface PendingWrite : Serializable {
    val source: String

    data class EditCopy(override val source: String, val patch: MetadataPatch) : PendingWrite

    /** [confirmed] = пользователь подтвердил диалог и выбирает место для резервной копии. */
    data class Overwrite(
        override val source: String,
        val op: OverwriteOp,
        val confirmed: Boolean = false,
    ) : PendingWrite
}

/** Минимальное хранилище: в приложении это SavedStateHandle, в тестах обычная Map. */
interface StateStore {
    operator fun get(key: String): Any?
    operator fun set(key: String, value: Any?)
}

/**
 * Однократная выдача ожидающей операции. Результат SAF без подходящей ожидающей операции
 * НИКОГДА не выполняет действие по умолчанию: вызывающий код показывает «операция прервана».
 */
class PendingWrites(private val store: StateStore) {

    val current: PendingWrite? get() = store[KEY] as? PendingWrite

    fun requestEditCopy(source: String, patch: MetadataPatch) {
        require(source.isNotBlank()) { "No source file selected" }
        store[KEY] = PendingWrite.EditCopy(source, patch)
    }

    fun requestOverwrite(source: String, op: OverwriteOp) {
        require(source.isNotBlank()) { "No source file selected" }
        store[KEY] = PendingWrite.Overwrite(source, op)
    }

    /** Пользователь подтвердил замену. Возвращает false, если подтверждать нечего. */
    fun confirmOverwrite(): Boolean {
        val pending = current as? PendingWrite.Overwrite ?: return false
        store[KEY] = pending.copy(confirmed = true)
        return true
    }

    fun cancel() {
        store[KEY] = null
    }

    /** Забирает операцию «сохранить изменённую копию». Любая другая ожидающая операция сбрасывается. */
    fun takeEditCopy(): PendingWrite.EditCopy? =
        (current as? PendingWrite.EditCopy).also { cancel() }

    /** Забирает подтверждённую перезапись. Неподтверждённая или чужая операция сбрасывается. */
    fun takeConfirmedOverwrite(): PendingWrite.Overwrite? =
        (current as? PendingWrite.Overwrite)?.takeIf { it.confirmed }.also { cancel() }

    companion object {
        const val KEY = "metadata_studio_pending_write"
    }
}
