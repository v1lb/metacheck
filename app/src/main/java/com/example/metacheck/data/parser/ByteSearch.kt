package com.example.metacheck.data.parser

internal fun String.ascii(): ByteArray = toByteArray(Charsets.ISO_8859_1)

internal fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (i in prefix.indices) if (this[i] != prefix[i]) return false
    return true
}

/** Поиск подпоследовательности в [start, endExclusive). Возвращает -1, если не найдено. */
internal fun ByteArray.indexOfSeq(pattern: ByteArray, start: Int = 0, endExclusive: Int = size): Int {
    if (pattern.isEmpty()) return -1
    val last = minOf(endExclusive, size) - pattern.size
    var i = maxOf(start, 0)
    outer@ while (i <= last) {
        if (this[i] == pattern[0]) {
            for (j in 1 until pattern.size) {
                if (this[i + j] != pattern[j]) {
                    i++
                    continue@outer
                }
            }
            return i
        }
        i++
    }
    return -1
}
