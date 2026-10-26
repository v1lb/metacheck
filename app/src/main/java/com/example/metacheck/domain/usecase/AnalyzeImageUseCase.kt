package com.example.metacheck.domain.usecase

import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.repository.ImageRepository

class AnalyzeImageUseCase(private val repository: ImageRepository) {
    suspend operator fun invoke(source: String): Result<MetaReport> = repository.analyze(source)
}
