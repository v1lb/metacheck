package com.example.metacheck.data.parser

/**
 * Минимальный декодер CBOR (RFC 8949) для чтения утверждений C2PA. Только чтение и только то,
 * что встречается в манифестах: числа, строки, байты, массивы, словари, теги, простые значения.
 *
 * Данные чужие и могут быть испорчены специально, поэтому:
 *  - длины проверяются по оставшимся байтам до выделения памяти;
 *  - глубина вложенности и общее число элементов ограничены;
 *  - любая ошибка формата даёт [CborException], а не падение.
 *
 * Результат: Long, Double, String, ByteArray, List<Any?>, Map<Any?, Any?>, Boolean, null.
 * Теги отбрасываются (значение под тегом возвращается как есть).
 */
internal class Cbor private constructor(private val data: ByteArray, private var pos: Int, private val end: Int) {

    class CborException(message: String) : RuntimeException(message)

    private var items = 0

    private fun fail(message: String): Nothing = throw CborException(message)

    private fun u8(): Int {
        if (pos >= end) fail("unexpected end")
        return data[pos++].toInt() and 0xFF
    }

    private fun uint(bytes: Int): Long {
        var v = 0L
        repeat(bytes) { v = (v shl 8) or u8().toLong() }
        return v
    }

    /** Аргумент заголовка; -1 означает «неопределённая длина». */
    private fun argument(info: Int): Long = when {
        info < 24 -> info.toLong()
        info == 24 -> uint(1)
        info == 25 -> uint(2)
        info == 26 -> uint(4)
        info == 27 -> uint(8).also { if (it < 0) fail("length overflow") }
        info == 31 -> -1
        else -> fail("reserved additional info $info")
    }

    private fun length(arg: Long): Int {
        if (arg < 0 || arg > end - pos) fail("length $arg exceeds data")
        return arg.toInt()
    }

    private fun value(depth: Int): Any? {
        if (depth > MAX_DEPTH) fail("too deep")
        if (++items > MAX_ITEMS) fail("too many items")
        val head = u8()
        val major = head ushr 5
        val info = head and 0x1F
        return when (major) {
            0 -> argument(info).also { if (info == 31) fail("bad uint") }
            1 -> (-1 - argument(info)).also { if (info == 31) fail("bad nint") }
            2 -> chunks(info, 2)
            3 -> chunks(info, 3)
            4 -> {
                val n = argument(info)
                val list = ArrayList<Any?>()
                if (n < 0) {
                    while (true) {
                        if (pos < end && data[pos] == BREAK) { pos++; break }
                        list += value(depth + 1)
                    }
                } else {
                    // Каждый элемент занимает хотя бы байт: так длину нельзя завысить ради выделения памяти.
                    length(n)
                    repeat(n.toInt()) { list += value(depth + 1) }
                }
                list
            }
            5 -> {
                val n = argument(info)
                val map = LinkedHashMap<Any?, Any?>()
                if (n < 0) {
                    while (true) {
                        if (pos < end && data[pos] == BREAK) { pos++; break }
                        val k = value(depth + 1)
                        map[k] = value(depth + 1)
                    }
                } else {
                    if (n * 2 > end - pos) fail("map too long")
                    repeat(n.toInt()) {
                        val k = value(depth + 1)
                        map[k] = value(depth + 1)
                    }
                }
                map
            }
            6 -> { argument(info); value(depth + 1) }
            else -> when (info) {
                20 -> false
                21 -> true
                22, 23 -> null
                24 -> { u8(); null }
                25 -> halfFloat(uint(2).toInt())
                26 -> java.lang.Float.intBitsToFloat(uint(4).toInt()).toDouble()
                27 -> java.lang.Double.longBitsToDouble(uint(8))
                31 -> fail("unexpected break")
                else -> if (info < 20) null else fail("bad simple value $info")
            }
        }
    }

    /** Строка байтов (2) или текста (3), в том числе из кусков неопределённой длины. */
    private fun chunks(info: Int, major: Int): Any {
        val n = argument(info)
        val bytes = if (n >= 0) {
            val len = length(n)
            data.copyOfRange(pos, pos + len).also { pos += len }
        } else {
            val out = java.io.ByteArrayOutputStream()
            while (true) {
                val head = u8()
                if (head == 0xFF) break
                if (head ushr 5 != major || head and 0x1F == 31) fail("bad chunk")
                val len = length(argument(head and 0x1F))
                out.write(data, pos, len)
                pos += len
            }
            out.toByteArray()
        }
        return if (major == 3) String(bytes, Charsets.UTF_8) else bytes
    }

    private fun halfFloat(bits: Int): Double {
        val exp = (bits shr 10) and 0x1F
        val mant = bits and 0x3FF
        val v = when (exp) {
            0 -> mant * Math.pow(2.0, -24.0)
            31 -> if (mant == 0) Double.POSITIVE_INFINITY else Double.NaN
            else -> (mant + 1024) * Math.pow(2.0, exp - 25.0)
        }
        return if (bits and 0x8000 != 0) -v else v
    }

    companion object {
        private const val MAX_DEPTH = 32
        private const val MAX_ITEMS = 100_000
        private const val BREAK = 0xFF.toByte()

        /** Первый элемент CBOR в [data] с [offset] длиной [length]. */
        fun decode(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Any? {
            require(offset >= 0 && length >= 0 && offset + length <= data.size)
            return Cbor(data, offset, offset + length).value(0)
        }
    }
}
