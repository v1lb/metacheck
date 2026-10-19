package com.example.metacheck.data.parser

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Читает не больше [limit] байт. Второй элемент пары — true, если в потоке были ещё данные.
 * Защита от файлов-гигантов и бесконечных потоков.
 */
internal fun readBounded(stream: InputStream, limit: Int): Pair<ByteArray, Boolean> {
    val out = ByteArrayOutputStream(minOf(limit, 64 * 1024))
    val chunk = ByteArray(8192)
    var total = 0
    while (total < limit) {
        val n = stream.read(chunk, 0, minOf(chunk.size, limit - total))
        if (n < 0) break
        out.write(chunk, 0, n)
        total += n
    }
    val truncated = total == limit && stream.read() >= 0
    return out.toByteArray() to truncated
}
