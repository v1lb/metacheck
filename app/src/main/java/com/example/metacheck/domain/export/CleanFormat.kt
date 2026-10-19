package com.example.metacheck.domain.export

import com.example.metacheck.domain.model.ImageFormat

data class CleanOutput(val mimeType: String, val extension: String)

/** JPEG остаётся JPEG, всё остальное сохраняется в PNG без потерь. */
object CleanFormat {
    fun outputFor(format: ImageFormat): CleanOutput =
        if (format == ImageFormat.JPEG) CleanOutput("image/jpeg", "jpg") else CleanOutput("image/png", "png")
}
