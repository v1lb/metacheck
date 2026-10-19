package com.example.metacheck.domain.repository

import com.example.metacheck.domain.model.MetaReport

interface ImageRepository {
    /** [source] — строка с content:// URI. Домен не зависит от android.net.Uri. */
    suspend fun analyze(source: String): Result<MetaReport>
}
