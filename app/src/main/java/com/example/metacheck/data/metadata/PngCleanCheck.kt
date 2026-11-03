package com.example.metacheck.data.metadata

import java.io.InputStream

/**
 * Проверка очищенной копии PNG: в файле только то, что нужно для показа картинки.
 *
 * Список разрешённых блоков (allow-list), а не запрещённых: любой незнакомый блок, в том числе
 * C2PA (caBX) или частный блок программы, считается метаданными.
 *
 * Аудит, п. 21. Цветовой профиль iCCP раньше считался метаданными, и очистка падала на картинках
 * с широкой цветовой гаммой (Display P3: скриншоты iPhone, часть фото HEIC/WebP). Android при
 * сохранении такого Bitmap сам пишет профиль, без него цвета исказились бы. Это заново созданный
 * системой профиль цветового пространства, а не профиль из исходного файла, поэтому сведений о
 * человеке или устройстве в нём нет. Разрешён один iCCP разумного размера.
 */
internal object PngCleanCheck {

    /** Блоки, которые пишет кодировщик PNG в Android: изображение, прозрачность и описание цвета. */
    private val ALLOWED = setOf(
        "IHDR", "PLTE", "IDAT", "IEND", "tRNS",
        "sRGB", "gAMA", "cHRM", "iCCP", "cICP", "sBIT", "pHYs", "bKGD",
    )
    private val SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    private const val MAX_CHUNK = 200_000_000L
    private const val MAX_ICC = 1_000_000L

    fun isClean(input: InputStream): Boolean {
        if (!SIGNATURE.contentEquals(readExactly(input, 8) ?: return false)) return false
        var iccSeen = false
        var first = true
        while (true) {
            val header = readExactly(input, 8) ?: return false
            val length = ((header[0].toLong() and 255) shl 24) or ((header[1].toLong() and 255) shl 16) or
                ((header[2].toLong() and 255) shl 8) or (header[3].toLong() and 255)
            if (length > MAX_CHUNK) return false
            val type = String(header, 4, 4, Charsets.ISO_8859_1)
            if (first && type != "IHDR") return false
            first = false
            if (type !in ALLOWED) return false
            if (type == "iCCP") {
                if (iccSeen || length > MAX_ICC) return false
                iccSeen = true
            }
            if (!skipAll(input, length + 4)) return false // данные + CRC
            // После IEND ничего быть не должно: хвост после картинки — тайник для любых данных.
            if (type == "IEND") return input.read() < 0
        }
    }

    private fun readExactly(input: InputStream, n: Int): ByteArray? {
        val bytes = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(bytes, off, n - off)
            if (r < 0) return null
            off += r
        }
        return bytes
    }

    private fun skipAll(input: InputStream, amount: Long): Boolean {
        var left = amount
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped > 0) left -= skipped
            else if (input.read() < 0) return false else left--
        }
        return true
    }
}
