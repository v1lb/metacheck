package com.example.metacheck.domain.export

import com.example.metacheck.domain.model.ImageFormat

data class CleanOutput(val mimeType: String, val extension: String)

/** JPEG остаётся JPEG, видео — в своём контейнере, остальные изображения сохраняются в PNG без потерь. */
object CleanFormat {
    fun outputFor(format: ImageFormat): CleanOutput = when (format) {
        ImageFormat.JPEG -> CleanOutput("image/jpeg", "jpg")
        ImageFormat.MP4 -> CleanOutput("video/mp4", "mp4")
        ImageFormat.MOV -> CleanOutput("video/quicktime", "mov")
        else -> CleanOutput("image/png", "png")
    }
}
