package com.example.metacheck.data.parser

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

object PngTestUtil {
    private val L1 = Charsets.ISO_8859_1

    fun chunk(type: String, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)
        val typeBytes = type.toByteArray(L1)
        dos.writeInt(body.size)
        dos.write(typeBytes)
        dos.write(body)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(body)
        dos.writeInt(crc.value.toInt())
        return out.toByteArray()
    }

    fun png(vararg chunks: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(PNG_SIGNATURE)
        chunks.forEach { out.write(it) }
        out.write(chunk("IEND", ByteArray(0)))
        return out.toByteArray()
    }

    fun text(keyword: String, text: String): ByteArray =
        chunk("tEXt", keyword.toByteArray(L1) + byteArrayOf(0) + text.toByteArray(L1))

    fun ztxt(keyword: String, raw: ByteArray): ByteArray =
        chunk("zTXt", keyword.toByteArray(L1) + byteArrayOf(0, 0) + deflate(raw))

    fun itxt(keyword: String, text: String): ByteArray =
        chunk(
            "iTXt",
            keyword.toByteArray(L1) + byteArrayOf(0, 0, 0, 0, 0) + text.toByteArray(Charsets.UTF_8),
        )

    fun deflate(raw: ByteArray): ByteArray {
        val d = Deflater()
        d.setInput(raw)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!d.finished()) {
            val n = d.deflate(buf)
            out.write(buf, 0, n)
        }
        d.end()
        return out.toByteArray()
    }
}
