package com.example.metacheck.domain.repository

import com.example.metacheck.domain.model.ApiResult
import com.example.metacheck.domain.model.AppSettings
import com.example.metacheck.domain.model.HistoryEntry
import com.example.metacheck.domain.model.ImageFormat
import com.example.metacheck.domain.model.MetaReport
import kotlinx.coroutines.flow.StateFlow

interface HistoryRepository {
    suspend fun save(report: MetaReport): Result<String>
    suspend fun list(): List<HistoryEntry>
    suspend fun load(id: String): MetaReport?
    suspend fun delete(id: String)
    suspend fun clear()
}

interface ExportRepository {
    suspend fun exportJson(report: MetaReport, target: String): Result<Unit>
    suspend fun exportPdf(report: MetaReport, target: String): Result<Unit>
}

interface ApiRepository {
    suspend fun send(report: MetaReport): Result<ApiResult>
}

interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    fun update(transform: (AppSettings) -> AppSettings)

    /** null или пустая строка удаляют ключ. Ключ хранится зашифрованным (Android Keystore). */
    fun saveApiKey(key: String?)
    fun readApiKey(): String?
}
