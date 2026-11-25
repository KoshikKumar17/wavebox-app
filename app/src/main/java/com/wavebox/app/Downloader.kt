package com.wavebox.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

data class Saved(val name: String, val uri: String)

object Downloader {
    private val http = OkHttpClient()

    fun safe(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|]+"), "").replace(Regex("\\s+"), " ").trim().take(80)

    /**
     * Downloads the song, writes tags + cover art (M4A: no re-encode; MP3: MediaCodec + jump3r)
     * and saves it. [folderUri] == null -> Music/Wavebox on internal storage.
     * [format] is "m4a" or "mp3" (re-encoded at [quality] kbps).
     * For YT Music songs [quality] is a target: the closest stream YouTube offers is downloaded.
     */
    suspend fun run(
        ctx: Context,
        song: Song,
        quality: Int,
        format: String,
        folderUri: Uri?,
        embed: Boolean,
        onProgress: (Int) -> Unit
    ): Saved = withContext(Dispatchers.IO) {
        val check = { ensureActive() }
        var tagSong = song
        val srcUrl: String
        val q: Int
        if (song.isYt) {
            // the stream link is looked up now, not stored (YouTube links expire within hours)
            val a = YtSource.resolve(song.ytVideoId, quality, needM4a = format == "m4a")
            srcUrl = a.url
            q = quality
            if (song.year.isBlank() && a.year.isNotBlank()) tagSong = song.copy(year = a.year)
        } else {
            q = song.closest(quality)
            srcUrl = song.urlFor(q)
        }
        val tmpIn = File(ctx.cacheDir, "in_${song.id}.m4a")
        val tmpCover = File(ctx.cacheDir, "cv_${song.id}.jpg")
        val tmpOut = File(ctx.cacheDir, "out_${song.id}.$format")
        try {
            if (song.isYt) fetchChunked(srcUrl, tmpIn, check, onProgress)
            else fetch(srcUrl, tmpIn, check, onProgress)

            var cover: File? = null
            if (embed && song.cover.isNotBlank()) {
                val ok = runCatching { fetch(song.cover, tmpCover, check) {}; tmpCover.length() > 0 }
                    .getOrDefault(false)
                if (ok) cover = tmpCover
            }
            check()
            onProgress(100) // processing

            val coverBytes = cover?.readBytes()
            val tags = TagInfo(tagSong.title, tagSong.artist, tagSong.album, tagSong.year, tagSong.copyright, tagSong.url)
            if (format == "mp3") {
                Mp3Encoder.encode(tmpIn, tmpOut, q, if (embed) Id3.build(tags, coverBytes) else null, check)
            } else if (embed) {
                try {
                    Mp4Tagger.tag(tmpIn, tmpOut, tags, coverBytes)
                } catch (e: Exception) {
                    // tagging is best effort: keep the song, just without tags
                    tmpIn.copyTo(tmpOut, overwrite = true)
                }
            } else tmpIn.copyTo(tmpOut, overwrite = true)
            check()

            val name = "${safe(song.title)} - ${safe(song.artist)}.$format"
            val mime = if (format == "mp3") "audio/mpeg" else "audio/mp4"
            val uri = if (folderUri == null) saveToMediaStore(ctx, name, mime, tmpOut)
            else saveToTree(ctx, folderUri, name, mime, tmpOut)
            Saved(name, uri)
        } finally {
            tmpIn.delete(); tmpCover.delete(); tmpOut.delete()
        }
    }

    /** Android 8-9: no scoped storage. Use the public Music folder if allowed, else the app's own Music folder. */
    @Suppress("DEPRECATION")
    private fun saveLegacy(ctx: Context, name: String, mime: String, file: File): String {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        val base = if (granted) Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        else ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: error("Storage isn't available")
        val dir = File(base, "Wavebox")
        if (!dir.exists() && !dir.mkdirs()) error("Couldn't create the folder")
        val out = File(dir, name)
        file.copyTo(out, overwrite = true)
        MediaScannerConnection.scanFile(ctx, arrayOf(out.path), arrayOf(mime), null)
        return Uri.fromFile(out).toString()
    }

    private fun saveToMediaStore(ctx: Context, name: String, mime: String, file: File): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return saveLegacy(ctx, name, mime, file)
        val r = ctx.contentResolver
        val v = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Wavebox")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = r.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, v) ?: error("Couldn't create the file")
        try {
            r.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: error("Couldn't write the file")
            v.clear()
            v.put(MediaStore.Audio.Media.IS_PENDING, 0)
            r.update(uri, v, null, null)
        } catch (e: Exception) {
            r.delete(uri, null, null)
            throw e
        }
        return uri.toString()
    }

    private fun saveToTree(ctx: Context, tree: Uri, name: String, mime: String, file: File): String {
        val dir = DocumentFile.fromTreeUri(ctx, tree) ?: error("Folder not available")
        if (!dir.canWrite()) error("Can't write to the chosen folder. Pick it again in Settings.")
        dir.findFile(name)?.delete()
        val df = dir.createFile(mime, name) ?: error("Couldn't create the file")
        ctx.contentResolver.openOutputStream(df.uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: error("Couldn't write the file")
        return df.uri.toString()
    }

    private fun fetch(url: String, dest: File, check: () -> Unit, onProgress: (Int) -> Unit) {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("Download failed (HTTP ${r.code})")
            val body = r.body ?: error("Empty response")
            val total = body.contentLength()
            body.byteStream().use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    var got = 0L
                    while (true) {
                        check()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        got += n
                        if (total > 0) onProgress(minOf(99, (got * 100 / total).toInt()))
                    }
                }
            }
        }
    }

    // ---- fast download for YouTube ----
    // YouTube slows a single long request down to roughly playback speed. Asking for the file in
    // small Range pieces over several connections at once avoids that.
    private const val CHUNK = 1L shl 20   // 1 MiB per request
    private const val WORKERS = 6

    private class Piece(val data: ByteArray, val total: Long, val partial: Boolean)

    private fun getRange(url: String, from: Long, to: Long): Piece {
        var last: IOException? = null
        repeat(3) {
            try {
                http.newCall(Request.Builder().url(url).header("Range", "bytes=$from-$to").build()).execute().use { r ->
                    if (!r.isSuccessful) error("Download failed (HTTP ${r.code})")
                    val body = r.body ?: error("Empty response")
                    val partial = r.code == 206
                    val total = if (partial) r.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
                    else body.contentLength()
                    return Piece(body.bytes(), total, partial)
                }
            } catch (e: IOException) { last = e }
        }
        throw last ?: IOException("Download failed")
    }

    private suspend fun fetchChunked(url: String, dest: File, check: () -> Unit, onProgress: (Int) -> Unit) = coroutineScope {
        val first = getRange(url, 0, CHUNK - 1)
        val total = first.total
        if (!first.partial || total <= first.data.size) { // server ignored Range, or tiny file: we already have it all
            dest.writeBytes(first.data)
            return@coroutineScope
        }
        RandomAccessFile(dest, "rw").use { raf ->
            raf.setLength(total)
            val ch = raf.channel
            fun put(data: ByteArray, pos: Long) {
                val b = ByteBuffer.wrap(data)
                var p = pos
                while (b.hasRemaining()) p += ch.write(b, p)
            }
            put(first.data, 0)
            val done = AtomicLong(first.data.size.toLong())
            onProgress(minOf(99, (done.get() * 100 / total).toInt()))
            val gate = Semaphore(WORKERS)
            (first.data.size.toLong() until total step CHUNK).map { start ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        check()
                        val end = minOf(start + CHUNK, total) - 1
                        val c = getRange(url, start, end)
                        if (c.data.size.toLong() != end - start + 1) throw IOException("Incomplete download")
                        put(c.data, start)
                        onProgress(minOf(99, (done.addAndGet(c.data.size.toLong()) * 100 / total).toInt()))
                    }
                }
            }.awaitAll()
        }
    }
}
