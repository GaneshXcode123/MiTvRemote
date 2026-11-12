package com.mitvremote.app.tv

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Minimal protobuf encoder (only what the Android TV remote protocol needs). */
class PbWriter {
    private val out = ByteArrayOutputStream()

    private fun tag(field: Int, wire: Int) = writeVarint(out, ((field shl 3) or wire).toLong())

    fun int(field: Int, value: Int): PbWriter {
        tag(field, 0)
        writeVarint(out, value.toLong())
        return this
    }

    fun bool(field: Int, value: Boolean): PbWriter = int(field, if (value) 1 else 0)

    fun bytes(field: Int, value: ByteArray): PbWriter {
        tag(field, 2)
        writeVarint(out, value.size.toLong())
        out.write(value)
        return this
    }

    fun str(field: Int, value: String): PbWriter = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun msg(field: Int, build: PbWriter.() -> Unit): PbWriter =
        bytes(field, PbWriter().apply(build).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    companion object {
        fun build(block: PbWriter.() -> Unit): ByteArray = PbWriter().apply(block).toByteArray()
    }
}

class PbField(val number: Int, val wire: Int, val num: Long, val data: ByteArray)

object Pb {
    private val EMPTY = ByteArray(0)

    fun parse(b: ByteArray): List<PbField> {
        val res = ArrayList<PbField>()
        var pos = 0

        fun varint(): Long {
            var shift = 0
            var r = 0L
            while (shift < 64) {
                if (pos >= b.size) throw EOFException("truncated message")
                val x = b[pos++].toInt() and 0xFF
                r = r or ((x and 0x7F).toLong() shl shift)
                if ((x and 0x80) == 0) return r
                shift += 7
            }
            throw IOException("bad varint")
        }

        while (pos < b.size) {
            val key = varint().toInt()
            val field = key ushr 3
            when (key and 7) {
                0 -> res.add(PbField(field, 0, varint(), EMPTY))
                1 -> {
                    pos += 8
                    res.add(PbField(field, 1, 0L, EMPTY))
                }
                2 -> {
                    val len = varint().toInt()
                    if (len < 0 || pos + len > b.size) throw IOException("bad length")
                    res.add(PbField(field, 2, 0L, b.copyOfRange(pos, pos + len)))
                    pos += len
                }
                5 -> {
                    pos += 4
                    res.add(PbField(field, 5, 0L, EMPTY))
                }
                else -> throw IOException("unsupported wire type")
            }
        }
        return res
    }
}

fun List<PbField>.has(n: Int): Boolean = any { it.number == n }

fun List<PbField>.num(n: Int, def: Long = 0L): Long =
    firstOrNull { it.number == n && it.wire == 0 }?.num ?: def

fun List<PbField>.int(n: Int, def: Int = 0): Int = num(n, def.toLong()).toInt()

fun List<PbField>.sub(n: Int): List<PbField>? =
    firstOrNull { it.number == n && it.wire == 2 }?.let { Pb.parse(it.data) }

fun List<PbField>.str(n: Int): String =
    firstOrNull { it.number == n && it.wire == 2 }?.data?.toString(Charsets.UTF_8) ?: ""

fun writeVarint(out: OutputStream, value: Long) {
    var v = value
    while ((v and 0x7FL.inv()) != 0L) {
        out.write(((v and 0x7FL) or 0x80L).toInt())
        v = v ushr 7
    }
    out.write(v.toInt())
}

/** Messages on the wire are prefixed with their length as a varint. */
fun OutputStream.writeFrame(payload: ByteArray) {
    val buf = ByteArrayOutputStream(payload.size + 5)
    writeVarint(buf, payload.size.toLong())
    buf.write(payload)
    write(buf.toByteArray())
    flush()
}

fun InputStream.readFrame(): ByteArray {
    var len = 0L
    var shift = 0
    var done = false
    while (!done) {
        val b = read()
        if (b < 0) throw EOFException("The TV closed the connection")
        len = len or ((b and 0x7F).toLong() shl shift)
        if ((b and 0x80) == 0) {
            done = true
        } else {
            shift += 7
            if (shift > 35) throw IOException("bad frame header")
        }
    }
    if (len > 2_000_000L) throw IOException("frame too large")
    val buf = ByteArray(len.toInt())
    var off = 0
    while (off < buf.size) {
        val r = read(buf, off, buf.size - off)
        if (r < 0) throw EOFException("The TV closed the connection")
        off += r
    }
    return buf
}
