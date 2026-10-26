package com.example.metacheck.data.metadata

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Streaming JPEG APP-segment editor. Preserves DCT bytes and the EXIF APP1 marker,
 * but drops separate XMP APP1, Photoshop APP13/IPTC and JPEG comments so stale
 * coordinate/copyright data cannot contradict the newly written EXIF fields.
 */
internal object JpegSegments {
    private val xmpStandard = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)
    private val xmpExtended = "http://ns.adobe.com/xmp/extension/\u0000".toByteArray(Charsets.US_ASCII)
    private val photoshop = "Photoshop 3.0\u0000".toByteArray(Charsets.US_ASCII)

    fun removeContradictorySegments(source: File, destination: File) {
        BufferedInputStream(FileInputStream(source)).use { input ->
            BufferedOutputStream(FileOutputStream(destination)).use { output ->
                if (input.read() != 0xFF || input.read() != 0xD8) throw IOException("Invalid JPEG SOI")
                output.write(0xFF); output.write(0xD8)
                while (true) {
                    if (input.read() != 0xFF) throw IOException("Invalid JPEG marker")
                    var marker = input.read()
                    while (marker == 0xFF) marker = input.read()
                    if (marker < 0) throw IOException("Truncated JPEG")
                    if (marker == 0xD9) { output.write(0xFF); output.write(marker); return }
                    if (marker == 0x01 || marker in 0xD0..0xD7) {
                        output.write(0xFF); output.write(marker); continue
                    }
                    val high = input.read(); val low = input.read()
                    if (high < 0 || low < 0) throw IOException("Truncated segment length")
                    val segmentLength = (high shl 8) or low
                    if (segmentLength < 2) throw IOException("Invalid segment length")
                    val data = ByteArray(segmentLength - 2)
                    readCompletely(input, data)
                    val isXmp = marker == 0xE1 &&
                        (data.startsWithBytes(xmpStandard) || data.startsWithBytes(xmpExtended))
                    val isIptc = marker == 0xED && data.startsWithBytes(photoshop)
                    val isComment = marker == 0xFE
                    if (!(isXmp || isIptc || isComment)) {
                        output.write(0xFF); output.write(marker)
                        output.write(high); output.write(low); output.write(data)
                    }
                    if (marker == 0xDA) {
                        input.copyTo(output, bufferSize = 32 * 1024)
                        return
                    }
                }
            }
        }
    }

    /** Fresh Bitmap.compress output must not contain an EXIF, XMP, IPTC or COM segment. */
    fun isStrippedJpeg(source: File): Boolean {
        BufferedInputStream(FileInputStream(source)).use { input ->
            if (input.read() != 0xFF || input.read() != 0xD8) return false
            while (true) {
                if (input.read() != 0xFF) return false
                var marker = input.read()
                while (marker == 0xFF) marker = input.read()
                if (marker == 0xDA || marker == 0xD9) return true
                if (marker < 0) return false
                if (marker == 0x01 || marker in 0xD0..0xD7) continue
                val a = input.read(); val b = input.read()
                if (a < 0 || b < 0) return false
                val length = (a shl 8) or b
                if (length < 2) return false
                // APP1/APP13 and COM must not occur in a completely cleaned image.
                if (marker == 0xE1 || marker == 0xED || marker == 0xFE) return false
                if (!skipExactly(input, (length - 2).toLong())) return false
            }
        }
    }

    private fun ByteArray.startsWithBytes(prefix: ByteArray): Boolean {
        if (prefix.size > size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }

    private fun readCompletely(input: InputStream, bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val count = input.read(bytes, offset, bytes.size - offset)
            if (count <= 0) throw IOException("Truncated JPEG segment")
            offset += count
        }
    }

    private fun skipExactly(input: InputStream, count: Long): Boolean {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) remaining -= skipped
            else if (input.read() < 0) return false else remaining--
        }
        return true
    }
}
