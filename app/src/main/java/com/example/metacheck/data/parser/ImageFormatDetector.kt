package com.example.metacheck.data.parser

import com.example.metacheck.domain.model.ImageFormat

/** Формат определяется по сигнатуре, а не по расширению или MIME, которым нельзя доверять. */
object ImageFormatDetector {

    fun detect(d: ByteArray, len: Int = d.size): ImageFormat {
        val n = minOf(len, d.size)
        fun at(off: Int, s: String): Boolean =
            off + s.length <= n && s.indices.all { d[off + it] == s[it].code.toByte() }

        return when {
            n >= 3 && d[0] == 0xFF.toByte() && d[1] == 0xD8.toByte() && d[2] == 0xFF.toByte() -> ImageFormat.JPEG
            n >= 8 && d.startsWith(PNG_SIGNATURE) -> ImageFormat.PNG
            at(0, "GIF87a") || at(0, "GIF89a") -> ImageFormat.GIF
            at(0, "RIFF") && at(8, "WEBP") -> ImageFormat.WEBP
            at(4, "ftyp") -> when {
                at(8, "avif") || at(8, "avis") -> ImageFormat.AVIF
                at(8, "heic") || at(8, "heix") || at(8, "heim") || at(8, "heis") || at(8, "hevc") -> ImageFormat.HEIC
                // mif1/msf1 — общий бренд HEIF: что внутри (HEVC или AV1), видно по совместимым брендам.
                // Смотрим только первые 32 байта: все вызывающие читают не меньше, и ответ у них одинаковый.
                at(8, "mif1") || at(8, "msf1") ->
                    if ((16..28 step 4).any { at(it, "avif") || at(it, "avis") }) ImageFormat.AVIF else ImageFormat.HEIC
                at(8, "qt  ") -> ImageFormat.MOV
                VIDEO_BRANDS.any { at(8, it) } -> ImageFormat.MP4
                else -> ImageFormat.UNKNOWN
            }
            // Старые файлы QuickTime бывают без ftyp и начинаются сразу с moov/mdat/wide.
            at(4, "moov") || at(4, "mdat") || at(4, "wide") -> ImageFormat.MOV
            else -> ImageFormat.UNKNOWN
        }
    }

    /** Основные бренды ftyp видео с телефонов и камер. Аудио (M4A) и RAW (CR3) сюда не входят. */
    private val VIDEO_BRANDS = listOf(
        "isom", "iso2", "iso3", "iso4", "iso5", "iso6", "mp41", "mp42", "mp71", "avc1", "M4V ", "M4VH", "M4VP",
        "3gp4", "3gp5", "3gp6", "3gp7", "3gp8", "3gp9", "3g2a", "3g2b", "3g2c", "MSNV", "XAVC", "mmp4", "f4v ", "CAEP",
    )
}
