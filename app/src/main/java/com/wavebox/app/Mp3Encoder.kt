package com.wavebox.app

import android.media.AudioFormat as AFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decodes the downloaded AAC with Android's MediaCodec and encodes it to MP3 with native LAME 3.100 (via LameNative). */
object Mp3Encoder {

    fun encode(input: File, output: File, bitrate: Int, id3: ByteArray?, check: () -> Unit) {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        var enc = 0L
        var channels = 2
        try {
            ex.setDataSource(input.path)
            var track = -1
            for (i in 0 until ex.trackCount) {
                if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
            }
            if (track < 0) error("No audio track found")
            ex.selectTrack(track)
            val inFmt = ex.getTrackFormat(track)
            codec = MediaCodec.createDecoderByType(inFmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(inFmt, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            var float32 = false
            var pending = ByteArray(0)
            var pendingLen = 0
            var mp3Buf = ByteArray(0)
            var shorts = ShortArray(0)

            FileOutputStream(output).buffered().use { out ->
                if (id3 != null) out.write(id3)

                fun feed(pcm: ByteArray, n: Int) {
                    if (enc == 0L) return
                    if (pending.size < pendingLen + n) pending = pending.copyOf(pendingLen + n + 8192)
                    System.arraycopy(pcm, 0, pending, pendingLen, n); pendingLen += n
                    val frameBytes = channels * 2
                    val frames = pendingLen / frameBytes
                    if (frames == 0) return
                    val sampleCount = frames * channels
                    if (shorts.size < sampleCount) shorts = ShortArray(sampleCount)
                    ByteBuffer.wrap(pending, 0, frames * frameBytes).order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer().get(shorts, 0, sampleCount)
                    val need = (frames * 5 / 4) + 7200 // LAME's recommended worst-case size
                    if (mp3Buf.size < need) mp3Buf = ByteArray(need)
                    val w = LameNative.encode(enc, shorts, frames, channels, mp3Buf)
                    if (w < 0) error("MP3 encoder error ($w)")
                    if (w > 0) out.write(mp3Buf, 0, w)
                    val used = frames * frameBytes
                    System.arraycopy(pending, used, pending, 0, pendingLen - used)
                    pendingLen -= used
                }

                while (!outDone) {
                    check()
                    if (!inDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val b = codec.getInputBuffer(i)!!
                            val n = ex.readSampleData(b, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true
                            } else {
                                codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance()
                            }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    if (o >= 0) {
                        if (info.size > 0) {
                            if (enc == 0L) {
                                val f = codec.outputFormat
                                val rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                val ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                if (ch !in 1..2) error("Unsupported channel count: $ch")
                                channels = ch
                                float32 = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                                    f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AFormat.ENCODING_PCM_FLOAT
                                enc = LameNative.init(rate, ch, bitrate, 5)
                                if (enc == 0L) error("Couldn't start the MP3 encoder")
                            }
                            val ob = codec.getOutputBuffer(o)!!
                            ob.position(info.offset); ob.limit(info.offset + info.size)
                            var chunk = ByteArray(info.size)
                            ob.get(chunk)
                            if (float32) chunk = floatToPcm16(chunk)
                            feed(chunk, chunk.size)
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    }
                }
                if (enc != 0L) {
                    if (mp3Buf.size < 7200) mp3Buf = ByteArray(7200)
                    val w = LameNative.flush(enc, mp3Buf)
                    if (w > 0) out.write(mp3Buf, 0, w)
                }
            }
            if (enc == 0L) error("Couldn't decode the audio")
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { ex.release() }
            if (enc != 0L) runCatching { LameNative.close(enc) }
        }
    }

    private fun floatToPcm16(src: ByteArray): ByteArray {
        val fb = java.nio.ByteBuffer.wrap(src).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        val out = ByteArray(fb.remaining() * 2)
        var j = 0
        while (fb.hasRemaining()) {
            val v = (fb.get().coerceIn(-1f, 1f) * 32767f).toInt()
            out[j++] = v.toByte(); out[j++] = (v shr 8).toByte()
        }
        return out
    }
}
