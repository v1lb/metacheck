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
                at(8, "heic") || at(8, "heix") || at(8, "heim") || at(8, "heis") ||
                    at(8, "hevc") || at(8, "mif1") || at(8, "msf1") -> ImageFormat.HEIC
                else -> ImageFormat.UNKNOWN
            }
            else -> ImageFormat.UNKNOWN
        }
    }
}
