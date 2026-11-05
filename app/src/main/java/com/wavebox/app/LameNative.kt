package com.wavebox.app

/** Thin JNI wrapper around LAME 3.100 (see app/src/main/cpp). */
object LameNative {
    init { System.loadLibrary("wavelame") }

    /** Returns a native handle, or 0 on failure. quality: 0 = best/slowest ... 9 = worst/fastest. */
    @JvmStatic external fun init(sampleRate: Int, channels: Int, bitrateKbps: Int, quality: Int): Long

    /** [pcm] is interleaved 16-bit samples, [frames] = samples per channel. Returns MP3 bytes written to [out], or < 0 on error. */
    @JvmStatic external fun encode(handle: Long, pcm: ShortArray, frames: Int, channels: Int, out: ByteArray): Int

    @JvmStatic external fun flush(handle: Long, out: ByteArray): Int

    @JvmStatic external fun close(handle: Long)
}
