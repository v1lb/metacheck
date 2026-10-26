package com.example.metacheck

import com.example.metacheck.domain.model.FileInfo
import com.example.metacheck.domain.model.GpsPoint
import com.example.metacheck.domain.model.ImageFormat
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.model.MetaSection
import com.example.metacheck.domain.model.Severity
import com.example.metacheck.domain.model.Signal

fun sampleReport(
    name: String = "photo.jpg",
    ai: Boolean = false,
    gps: Boolean = true,
    prompt: String = "кот \"в шляпе\"\nSteps: 20, Sampler: Euler",
) = MetaReport(
    file = FileInfo(name, "image/jpeg", 1234L, ImageFormat.JPEG, 1234, false),
    exifSections = listOf(MetaSection("Камера и объектив", listOf(MetaItem("Модель", "Pixel 8")))),
    xmpItems = listOf(MetaItem("Инструмент создания", "Editor")),
    pngTextItems = listOf(MetaItem("parameters", prompt)),
    gps = if (gps) GpsPoint(55.75, 37.62, 120.0) else null,
    signals = if (ai) {
        listOf(Signal(Severity.AI_EVIDENCE, "Параметры Stable Diffusion", "детали"))
    } else {
        listOf(Signal(Severity.INFO, "Явных следов ИИ в метаданных нет", "детали"))
    },
)
