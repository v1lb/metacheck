package com.example.metacheck.data.metadata

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.example.metacheck.data.parser.ExifReader
import com.example.metacheck.domain.metadata.AuditFinding
import com.example.metacheck.domain.metadata.AuditResult
import com.example.metacheck.domain.metadata.FolderAuditRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Обход папки через SAF (дерево документов) и чтение EXIF каждого фото.
 * Координаты на Android 10+ читаются через «оригинальный» URI MediaStore, иначе система их скрывает.
 */
class FolderAuditImpl(private val context: Context) : FolderAuditRepository {
    private val resolver = context.contentResolver

    private val imageTypes = setOf("image/jpeg", "image/png", "image/heic", "image/heif", "image/webp")
    private val serialKeys = setOf("exif:BodySerialNumber", "exif:LensSerialNumber")
    private val personKeys = setOf("exif:CameraOwnerName", "exif:Artist", "exif:Copyright")

    override suspend fun scan(tree: String, progress: (Int) -> Unit): AuditResult = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(tree)
        val files = list(treeUri)
        val limited = files.size > FolderAuditRepository.MAX_FILES
        val findings = ArrayList<AuditFinding>()
        var scanned = 0
        for ((uri, name) in files.take(FolderAuditRepository.MAX_FILES)) {
            coroutineContext.ensureActive()
            inspect(uri, name)?.let { findings += it }
            scanned++
            if (scanned % 10 == 0) progress(scanned)
        }
        progress(scanned)
        AuditResult.of(scanned, findings, limited)
    }

    private suspend fun list(treeUri: Uri): List<Pair<Uri, String>> {
        val out = ArrayList<Pair<Uri, String>>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue += DocumentsContract.getTreeDocumentId(treeUri) to 0
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        while (queue.isNotEmpty() && out.size <= FolderAuditRepository.MAX_FILES) {
            coroutineContext.ensureActive()
            val (parent, depth) = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent)
            runCatching {
                resolver.query(children, columns, null, null, null)?.use { c ->
                    while (c.moveToNext() && out.size <= FolderAuditRepository.MAX_FILES) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1).orEmpty()
                        val mime = c.getString(2).orEmpty()
                        if (name.startsWith(".")) continue // .thumbnails, .trashed-…, .pending-…
                        when {
                            mime == DocumentsContract.Document.MIME_TYPE_DIR ->
                                if (depth < FolderAuditRepository.MAX_DEPTH) queue += id to depth + 1
                            mime in imageTypes -> out += DocumentsContract.buildDocumentUriUsingTree(treeUri, id) to name
                        }
                    }
                }
            }
        }
        return out
    }

    private fun inspect(uri: Uri, name: String): AuditFinding? {
        val direct = runCatching { resolver.openInputStream(uri)?.use { ExifReader.read(it) } }.getOrNull()
        val gps = direct?.gps ?: if (Build.VERSION.SDK_INT >= 29) runCatching {
            val media = MediaStore.getMediaUri(context, uri) ?: return@runCatching null
            resolver.openInputStream(MediaStore.setRequireOriginal(media))?.use { ExifReader.read(it) }?.gps
        }.getOrNull() else null
        val keys = direct?.sections.orEmpty().flatMap { s -> s.items.mapNotNull { it.key } }.toSet()
        if (gps == null && direct?.hasData != true) return null
        return AuditFinding(
            uri = uri.toString(),
            name = name,
            location = gps != null,
            serial = keys.any { it in serialKeys },
            person = keys.any { it in personKeys },
        )
    }
}
