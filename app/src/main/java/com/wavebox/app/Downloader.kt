package com.wavebox.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class Saved(val name: String, val uri: String)

object Downloader {
    private val http = OkHttpClient()

    fun safe(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|]+"), "").replace(Regex("\\s+"), " ").trim().take(80)

    /**
     * Downloads the song, writes tags + cover art (M4A: no re-encode; MP3: MediaCodec + jump3r)
     * and saves it. [folderUri] == null -> Music/Wavebox on internal storage.
     * [format] is "m4a" or "mp3" (re-encoded at [quality] kbps).
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
        val q = song.closest(quality)
        val tmpIn = File(ctx.cacheDir, "in_${song.id}.m4a")
        val tmpCover = File(ctx.cacheDir, "cv_${song.id}.jpg")
        val tmpOut = File(ctx.cacheDir, "out_${song.id}.$format")
        try {
            fetch(song.urlFor(q), tmpIn, check, onProgress)

            var cover: File? = null
            if (embed && song.cover.isNotBlank()) {
                val ok = runCatching { fetch(song.cover, tmpCover, check) {}; tmpCover.length() > 0 }
                    .getOrDefault(false)
                if (ok) cover = tmpCover
            }
            check()
            onProgress(100) // processing

            val coverBytes = cover?.readBytes()
            val tags = TagInfo(song.title, song.artist, song.album, song.year, song.copyright, song.url)
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

    private fun saveToMediaStore(ctx: Context, name: String, mime: String, file: File): String {
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
}
