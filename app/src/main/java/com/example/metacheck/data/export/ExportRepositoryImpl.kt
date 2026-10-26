package com.example.metacheck.data.export

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.repository.ExportRepository
import com.example.metacheck.domain.runCatchingCancellable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

/** Запись идёт только в URI, который выдал системный диалог сохранения (Scoped Storage, без разрешений). */
class ExportRepositoryImpl(
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ExportRepository {

    override suspend fun exportJson(report: MetaReport, target: String): Result<Unit> =
        write(target) { it.write(ReportJson.toJson(report).toString(2).toByteArray(Charsets.UTF_8)) }

    override suspend fun exportPdf(report: MetaReport, target: String): Result<Unit> =
        write(target) { PdfReportWriter.write(report, it) }

    private suspend fun write(target: String, block: (OutputStream) -> Unit): Result<Unit> = withContext(io) {
        val uri = Uri.parse(target)
        runCatchingCancellable {
            require(uri.scheme == ContentResolver.SCHEME_CONTENT) { "Недопустимая цель сохранения" }
            resolver.openOutputStream(uri, "wt")?.use(block) ?: throw IOException("Не удалось открыть файл")
        }.onFailure {
            // Не оставляем пустой или битый файл.
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
        }
    }
}
