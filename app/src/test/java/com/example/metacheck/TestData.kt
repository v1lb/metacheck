package com.example.metacheck

import com.example.metacheck.domain.model.FileInfo
import com.example.metacheck.domain.model.GpsPoint
import com.example.metacheck.domain.model.ImageFormat
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.model.MetaSection
import com.example.metacheck.domain.model.SectionKey
import com.example.metacheck.domain.model.Severity
import com.example.metacheck.domain.model.Signal
import com.example.metacheck.domain.model.SignalKind

/** Отчёт в том виде, в каком его строят парсеры: машинные ключи вместо подписей. */
fun sampleReport(
    name: String = "photo.jpg",
    ai: Boolean = false,
    gps: Boolean = true,
    prompt: String = "кот \"в шляпе\"\nSteps: 20, Sampler: Euler",
) = MetaReport(
    file = FileInfo(name, "image/jpeg", 1234L, ImageFormat.JPEG, 1234, false),
    exifSections = listOf(
        MetaSection("", listOf(MetaItem("Model", "Pixel 8", key = "exif:Model")), key = SectionKey.CAMERA),
    ),
    xmpItems = listOf(MetaItem("CreatorTool", "Editor", key = "xmp:xmp:CreatorTool")),
    pngTextItems = listOf(MetaItem("parameters", prompt)),
    gps = if (gps) GpsPoint(55.75, 37.62, 120.0) else null,
    signals = if (ai) {
        listOf(Signal(Severity.AI_EVIDENCE, kind = SignalKind.SD_PARAMETERS))
    } else {
        listOf(Signal(Severity.INFO, kind = SignalKind.NO_AI_TRACES))
    },
)
