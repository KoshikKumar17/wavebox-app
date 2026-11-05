package com.wavebox.app

import java.io.ByteArrayOutputStream

/** Builds an ID3v2.3 tag (UTF-16 text, optional front cover) to put in front of an MP3 stream. */
object Id3 {
    private fun u32(v: Int) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
    private fun syncsafe(v: Int) =
        byteArrayOf(((v shr 21) and 127).toByte(), ((v shr 14) and 127).toByte(), ((v shr 7) and 127).toByte(), (v and 127).toByte())

    private fun utf16(s: String): ByteArray = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + s.toByteArray(Charsets.UTF_16LE)

    private fun frame(id: String, payload: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(id.toByteArray(Charsets.ISO_8859_1)); o.write(u32(payload.size)); o.write(byteArrayOf(0, 0)); o.write(payload)
        return o.toByteArray()
    }

    fun build(info: TagInfo, cover: ByteArray?): ByteArray {
        val f = ByteArrayOutputStream()
        fun text(id: String, v: String) { if (v.isNotBlank()) f.write(frame(id, byteArrayOf(1) + utf16(v))) }
        text("TIT2", info.title); text("TPE1", info.artist); text("TALB", info.album)
        text("TYER", info.year); text("TCOP", info.copyright)
        if (info.comment.isNotBlank()) {
            f.write(frame("COMM", byteArrayOf(1) + "eng".toByteArray() + byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0) + utf16(info.comment)))
        }
        if (cover != null && cover.size > 8) {
            val png = cover[0] == 0x89.toByte() && cover[1] == 'P'.code.toByte()
            val mime = (if (png) "image/png" else "image/jpeg").toByteArray()
            f.write(frame("APIC", byteArrayOf(0) + mime + byteArrayOf(0, 3, 0) + cover))
        }
        val body = f.toByteArray()
        return "ID3".toByteArray() + byteArrayOf(3, 0, 0) + syncsafe(body.size) + body
    }
}
