package com.example.metacheck.data.parser

import java.io.InputStream

/**
 * Читает не больше [limit] байт. Второй элемент пары — true, если в потоке были ещё данные.
 * Защита от файлов-гигантов и бесконечных потоков.
 *
 * Аудит, п. 12: раньше ByteArrayOutputStream рос удвоением до 16 МБ, а toByteArray() делал ещё одну
 * полную копию: пик 32–48 МБ на один анализ. Теперь, если размер файла известен ([expectedSize]),
 * буфер выделяется сразу нужного размера и копия не нужна. Если размер неизвестен, рост идёт
 * в 1,5 раза и итоговая копия делается только при необходимости.
 */
internal fun readBounded(stream: InputStream, limit: Int, expectedSize: Long = -1L): Pair<ByteArray, Boolean> {
    require(limit >= 0)
    val initial = when {
        expectedSize in 0..limit.toLong() -> expectedSize.toInt()
        expectedSize > limit -> limit
        else -> minOf(limit, 64 * 1024)
    }
    var buf = ByteArray(initial)
    var total = 0
    while (total < limit) {
        if (total == buf.size) {
            // Файл больше заявленного или размер неизвестен: растим буфер, но не выше лимита.
            val grown = minOf(limit.toLong(), maxOf(buf.size + 8192L, buf.size * 3L / 2)).toInt()
            buf = buf.copyOf(grown)
        }
        val n = stream.read(buf, total, minOf(buf.size - total, 64 * 1024))
        if (n < 0) break
        total += n
    }
    val truncated = total == limit && stream.read() >= 0
    return (if (total == buf.size) buf else buf.copyOf(total)) to truncated
}
