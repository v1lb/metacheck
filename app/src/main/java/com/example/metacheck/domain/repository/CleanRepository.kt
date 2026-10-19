package com.example.metacheck.domain.repository

import com.example.metacheck.domain.model.CleanResult
import com.example.metacheck.domain.model.ImageFormat

interface CleanRepository {
    suspend fun clean(
        source: String,
        target: String,
        format: ImageFormat
    ): Result<CleanResult>
}
