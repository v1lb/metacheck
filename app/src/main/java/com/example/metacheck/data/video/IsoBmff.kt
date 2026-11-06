package com.example.metacheck.data.video

import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Произвольный доступ к байтам файла: видео целиком в память не читается. */
interface ByteSource {
    val size: Long

    /** Ровно [length] байт с позиции [position], иначе EOFException. */
    fun readFully(position: Long, dst: ByteArray, offset: Int = 0, length: Int = dst.size)

    fun bytes(position: Long, length: Int): ByteArray = ByteArray(length).also { readFully(position, it) }
}

class ArrayByteSource(private val data: ByteArray) : ByteSource {
    override val size: Long get() = data.size.toLong()

    override fun readFully(position: Long, dst: ByteArray, offset: Int, length: Int) {
        if (position < 0 || length < 0 || position + length > data.size) throw EOFException()
        System.arraycopy(data, position.toInt(), dst, offset, length)
    }
}

class ChannelByteSource(private val channel: FileChannel) : ByteSource {
    override val size: Long = channel.size()

    override fun readFully(position: Long, dst: ByteArray, offset: Int, length: Int) {
        if (position < 0 || length < 0 || position + length > size) throw EOFException()
        val buffer = ByteBuffer.wrap(dst, offset, length)
        var at = position
        while (buffer.hasRemaining()) {
            val n = channel.read(buffer, at)
            if (n < 0) throw EOFException()
            at += n
        }
    }
}

/** Структура файла не та, что ожидается: очистку без пережатия делать нельзя. */
class Mp4FormatException(message: String) : Exception(message)

/** Бокс ISO BMFF: [start] — начало заголовка, [end] — конец бокса (не включительно). */
data class BoxRef(val type: String, val start: Long, val header: Int, val end: Long) {
    val payloadStart: Long get() = start + header
    val payloadSize: Long get() = end - payloadStart
    val size: Long get() = end - start
}

object IsoBmff {
    private const val MAX_BOXES = 200_000

    /**
     * Боксы в диапазоне [from, to). [lenient]: на испорченном заголовке список обрывается, а не падает.
     * Возвращает пару: боксы и позицию, до которой структура разобрана (остаток — нераспознанные байты).
     */
    fun list(src: ByteSource, from: Long, to: Long, lenient: Boolean = false): Pair<List<BoxRef>, Long> {
        val boxes = ArrayList<BoxRef>()
        var pos = from
        val head = ByteArray(16)
        while (pos < to) {
            if (to - pos < 8) {
                if (lenient) break else throw Mp4FormatException("trailing bytes")
            }
            src.readFully(pos, head, 0, 8)
            var size = u32(head, 0)
            val type = fourcc(head, 4)
            var header = 8
            when (size) {
                1L -> {
                    if (to - pos < 16) { if (lenient) break else throw Mp4FormatException("short largesize") }
                    src.readFully(pos + 8, head, 8, 8)
                    size = u64(head, 8)
                    header = 16
                }
                0L -> size = to - pos
            }
            if (size < header || size > to - pos) {
                if (lenient) break else throw Mp4FormatException("bad box size: $type")
            }
            boxes += BoxRef(type, pos, header, pos + size)
            if (boxes.size > MAX_BOXES) throw Mp4FormatException("too many boxes")
            pos += size
        }
        return boxes to pos
    }

    fun children(src: ByteSource, box: BoxRef, skip: Int = 0): List<BoxRef> =
        list(src, box.payloadStart + skip, box.end).first

    fun fourcc(d: ByteArray, i: Int): String = String(d, i, 4, Charsets.ISO_8859_1)

    fun u16(d: ByteArray, i: Int): Int = ((d[i].toInt() and 0xFF) shl 8) or (d[i + 1].toInt() and 0xFF)

    fun u32(d: ByteArray, i: Int): Long =
        ((d[i].toLong() and 0xFF) shl 24) or ((d[i + 1].toLong() and 0xFF) shl 16) or
            ((d[i + 2].toLong() and 0xFF) shl 8) or (d[i + 3].toLong() and 0xFF)

    fun s32(d: ByteArray, i: Int): Int = u32(d, i).toInt()

    fun u64(d: ByteArray, i: Int): Long = (u32(d, i) shl 32) or u32(d, i + 4)

    fun putU32(d: ByteArray, i: Int, v: Long) {
        d[i] = (v ushr 24).toByte(); d[i + 1] = (v ushr 16).toByte(); d[i + 2] = (v ushr 8).toByte(); d[i + 3] = v.toByte()
    }

    fun putU64(d: ByteArray, i: Int, v: Long) {
        putU32(d, i, v ushr 32); putU32(d, i + 4, v and 0xFFFFFFFFL)
    }

    /** UUID бокса XMP (Adobe) и C2PA. */
    val XMP_UUID: ByteArray = hex("BE7ACFCB97A942E89C71999491E3AFAC")
    val C2PA_UUID: ByteArray = hex("D8FEC3D61B0E483C92975828877EC481")

    fun startsWith(d: ByteArray, prefix: ByteArray, at: Int = 0): Boolean =
        at + prefix.size <= d.size && prefix.indices.all { d[at + it] == prefix[it] }

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
