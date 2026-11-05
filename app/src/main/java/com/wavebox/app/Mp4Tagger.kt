package com.wavebox.app

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

class TagInfo(
    val title: String, val artist: String, val album: String,
    val year: String, val copyright: String, val comment: String
)

/** Writes iTunes-style tags + cover art into an M4A without re-encoding (pure Kotlin). */
object Mp4Tagger {
    private val L1 = Charsets.ISO_8859_1

    private class Top(val type: String, val start: Long, val size: Long)
    private class Kid(val type: String, val start: Int, val size: Int)

    private fun u32(b: ByteArray, p: Int): Long =
        ((b[p].toLong() and 0xFF) shl 24) or ((b[p + 1].toLong() and 0xFF) shl 16) or
            ((b[p + 2].toLong() and 0xFF) shl 8) or (b[p + 3].toLong() and 0xFF)

    private fun put32(b: ByteArray, p: Int, v: Long) {
        b[p] = (v shr 24).toByte(); b[p + 1] = (v shr 16).toByte()
        b[p + 2] = (v shr 8).toByte(); b[p + 3] = v.toByte()
    }

    private fun u64(b: ByteArray, p: Int): Long = (u32(b, p) shl 32) or u32(b, p + 4)
    private fun put64(b: ByteArray, p: Int, v: Long) { put32(b, p, v ushr 32); put32(b, p + 4, v and 0xFFFFFFFFL) }

    private fun be32(v: Int): ByteArray = ByteArray(4).also { put32(it, 0, v.toLong() and 0xFFFFFFFFL) }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(payload.size + 8)
        out.write(be32(payload.size + 8)); out.write(type.toByteArray(L1)); out.write(payload)
        return out.toByteArray()
    }

    private fun item(type: String, kind: Int, payload: ByteArray): ByteArray {
        val d = ByteArrayOutputStream()
        d.write(be32(kind)); d.write(be32(0)); d.write(payload)
        return box(type, box("data", d.toByteArray()))
    }

    private fun buildUdta(info: TagInfo, cover: ByteArray?): ByteArray {
        val ilst = ByteArrayOutputStream()
        fun text(type: String, v: String) { if (v.isNotBlank()) ilst.write(item(type, 1, v.toByteArray(Charsets.UTF_8))) }
        text("\u00A9nam", info.title)
        text("\u00A9ART", info.artist)
        text("\u00A9alb", info.album)
        text("\u00A9day", info.year)
        text("cprt", info.copyright)
        text("\u00A9cmt", info.comment)
        if (cover != null && cover.size > 8) {
            val png = cover[0] == 0x89.toByte() && cover[1] == 'P'.code.toByte()
            ilst.write(item("covr", if (png) 14 else 13, cover))
        }
        val hdlr = ByteArrayOutputStream()
        hdlr.write(ByteArray(8)); hdlr.write("mdir".toByteArray(L1)); hdlr.write("appl".toByteArray(L1))
        hdlr.write(ByteArray(9))
        val meta = ByteArrayOutputStream()
        meta.write(ByteArray(4)); meta.write(box("hdlr", hdlr.toByteArray())); meta.write(box("ilst", ilst.toByteArray()))
        return box("udta", box("meta", meta.toByteArray()))
    }

    private fun parseTop(raf: RandomAccessFile, len: Long): List<Top> {
        val out = ArrayList<Top>()
        var p = 0L
        val h = ByteArray(16)
        while (p + 8 <= len) {
            raf.seek(p); raf.readFully(h, 0, 8)
            var size = u32(h, 0)
            val type = String(h, 4, 4, L1)
            if (size == 1L) { raf.readFully(h, 8, 8); size = u64(h, 8) }
            else if (size == 0L) size = len - p
            if (size < 8 || p + size > len) break
            out.add(Top(type, p, size)); p += size
        }
        return out
    }

    private fun kids(b: ByteArray, s: Int, e: Int): List<Kid> {
        val out = ArrayList<Kid>()
        var p = s
        while (p + 8 <= e) {
            var size = u32(b, p).toInt()
            if (size == 0) size = e - p
            if (size < 8 || p + size > e) break
            out.add(Kid(String(b, p + 4, 4, L1), p, size)); p += size
        }
        return out
    }

    /** moov sits before mdat -> every chunk offset shifts by [delta]. */
    private fun patchOffsets(b: ByteArray, s: Int, e: Int, delta: Long) {
        for (k in kids(b, s, e)) {
            when (k.type) {
                "trak", "mdia", "minf", "stbl" -> patchOffsets(b, k.start + 8, k.start + k.size, delta)
                "stco" -> {
                    val n = u32(b, k.start + 12).toInt()
                    for (i in 0 until n) { val q = k.start + 16 + 4 * i; put32(b, q, u32(b, q) + delta) }
                }
                "co64" -> {
                    val n = u32(b, k.start + 12).toInt()
                    for (i in 0 until n) { val q = k.start + 16 + 8 * i; put64(b, q, u64(b, q) + delta) }
                }
            }
        }
    }

    private fun copyRange(raf: RandomAccessFile, start: Long, size: Long, out: OutputStream) {
        raf.seek(start)
        val buf = ByteArray(64 * 1024)
        var left = size
        while (left > 0) {
            val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) error("Unexpected end of file")
            out.write(buf, 0, n); left -= n
        }
    }

    fun tag(input: File, output: File, info: TagInfo, cover: ByteArray?) {
        RandomAccessFile(input, "r").use { raf ->
            val tops = parseTop(raf, raf.length())
            val moov = tops.firstOrNull { it.type == "moov" } ?: error("Not a valid M4A (no moov)")
            val mdat = tops.firstOrNull { it.type == "mdat" } ?: error("Not a valid M4A (no mdat)")
            if (moov.size > 64L * 1024 * 1024) error("moov too large")
            val old = ByteArray(moov.size.toInt())
            raf.seek(moov.start); raf.readFully(old)
            if (u32(old, 0) == 1L) error("64-bit moov not supported")

            val body = ByteArrayOutputStream()
            for (k in kids(old, 8, old.size)) if (k.type != "udta") body.write(old, k.start, k.size)
            body.write(buildUdta(info, cover))
            val nm = box("moov", body.toByteArray())
            if (moov.start < mdat.start) patchOffsets(nm, 8, nm.size, (nm.size - old.size).toLong())

            output.outputStream().buffered().use { out ->
                for (t in tops) if (t === moov) out.write(nm) else copyRange(raf, t.start, t.size, out)
            }
        }
    }
}
