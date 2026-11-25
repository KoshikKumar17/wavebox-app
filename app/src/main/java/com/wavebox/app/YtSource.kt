package com.wavebox.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class YtSongs(val songs: List<Song>, val next: Page?)
class YtColls(val items: List<MediaCollection>, val next: Page?)
class YtAudio(val url: String, val kbps: Int, val year: String)

/**
 * YouTube / YT Music through NewPipeExtractor, running on the phone (so YouTube sees a normal
 * user IP, not a datacenter). Songs are stored as "yt_<videoId>" and the stream link is looked up
 * only when the song is played or downloaded, because those links expire after a few hours.
 */
object YtSource {
    private val service get() = ServiceList.YouTube
    private val http = OkHttpClient()
    @Volatile private var ready = false

    private val LINK_RE = Regex("""https?://(?:[\w-]+\.)*(?:youtube\.com|youtu\.be)/\S+""", RegexOption.IGNORE_CASE)
    private val ID_RE = Regex("""(?:[?&]v=|youtu\.be/|/shorts/|/embed/)([\w-]{11})""")
    private val LIST_RE = Regex("""[?&]list=([\w-]+)""")
    private val EXPIRE_RE = Regex("""[?&]expire=(\d+)""")

    @Synchronized
    private fun init() {
        if (ready) return
        val cc = Locale.getDefault().country.ifBlank { "IN" }
        NewPipe.init(NpDownloader(http), Localization("en", cc), ContentCountry(cc))
        ready = true
    }

    fun extractLink(text: String): String? =
        LINK_RE.find(text)?.value?.trimEnd('.', ',', ')', ']')?.takeIf { ID_RE.containsMatchIn(it) || LIST_RE.containsMatchIn(it) }

    // ---------------- helpers ----------------
    private fun images(list: List<Image>?): Pair<String, String> {
        val l = list.orEmpty().filter { !it.url.isNullOrBlank() }
        if (l.isEmpty()) return "" to ""
        val big = l.maxByOrNull { it.height } ?: l.last()
        val small = l.filter { it.height in 1..400 }.maxByOrNull { it.height } ?: big
        return big.url to small.url
    }

    private fun cleanArtist(s: String?): String = (s ?: "").removeSuffix(" - Topic").trim()

    private fun yearOf(info: StreamInfo): String =
        runCatching { info.uploadDate?.offsetDateTime()?.year?.toString() }.getOrNull().orEmpty()

    private fun toSong(i: StreamInfoItem, album: String = "", fallbackArtist: String = ""): Song? {
        val vid = ID_RE.find(i.url ?: "")?.groupValues?.get(1) ?: return null
        val (big, small) = images(i.thumbnails)
        return Song(
            id = "yt_$vid",
            title = (i.name ?: "").ifBlank { "Untitled" },
            artist = cleanArtist(i.uploaderName).ifBlank { fallbackArtist }.ifBlank { "Unknown artist" },
            album = album, year = "", copyright = "",
            duration = i.duration.toInt().coerceAtLeast(0),
            url = "https://music.youtube.com/watch?v=$vid",
            cover = big, thumb = small, links = emptyMap(), source = SRC_YT
        )
    }

    private fun toCollection(kind: String, i: PlaylistInfoItem): MediaCollection? {
        val url = i.url ?: return null
        val (big, small) = images(i.thumbnails)
        val n = i.streamCount.toInt().coerceAtLeast(0)
        val by = cleanArtist(i.uploaderName)
        val sub = if (kind == "album") by.ifBlank { "Album" }
        else listOf(by, if (n > 0) "$n songs" else "").filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Playlist" }
        return MediaCollection(
            kind = kind, id = url, title = (i.name ?: "").ifBlank { "Untitled" }, subtitle = sub,
            image = big, thumb = small, url = url, count = n, source = SRC_YT
        )
    }

    // ---------------- search ----------------
    suspend fun searchSongs(q: String, page: Page?): YtSongs = withContext(Dispatchers.IO) {
        init()
        val h = service.searchQHFactory.fromQuery(q, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "")
        val (items, next) =
            if (page == null) SearchInfo.getInfo(service, h).let { it.relatedItems to it.nextPage }
            else SearchInfo.getMoreItems(service, h, page).let { it.items to it.nextPage }
        YtSongs(items.filterIsInstance<StreamInfoItem>().mapNotNull { toSong(it) }, next)
    }

    suspend fun searchCollections(kind: String, q: String, page: Page?): YtColls = withContext(Dispatchers.IO) {
        init()
        val f = if (kind == "album") YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS else YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS
        val h = service.searchQHFactory.fromQuery(q, listOf(f), "")
        val (items, next) =
            if (page == null) SearchInfo.getInfo(service, h).let { it.relatedItems to it.nextPage }
            else SearchInfo.getMoreItems(service, h, page).let { it.items to it.nextPage }
        YtColls(items.filterIsInstance<PlaylistInfoItem>().mapNotNull { toCollection(kind, it) }, next)
    }

    // ---------------- album / playlist / link ----------------
    suspend fun detail(c: MediaCollection): LinkResult.Detail = withContext(Dispatchers.IO) {
        detailOf(c.url, c.kind, c.title)
    }

    private fun detailOf(url: String, kind: String, fallbackTitle: String): LinkResult.Detail {
        init()
        val info = PlaylistInfo.getInfo(service, url)
        val title = (info.name ?: "").ifBlank { fallbackTitle }.ifBlank { "Untitled" }
        val by = cleanArtist(info.uploaderName)
        val album = if (kind == "album") title else ""
        val songs = ArrayList<Song>()
        val seen = HashSet<String>()
        fun add(items: List<StreamInfoItem>) {
            items.forEach { it0 -> toSong(it0, album, by)?.let { s -> if (seen.add(s.id)) songs.add(s) } }
        }
        add(info.relatedItems)
        var next = info.nextPage
        var guard = 0
        while (next != null && guard++ < 6 && songs.size < 300) {
            val pg = PlaylistInfo.getMoreItems(service, url, next)
            add(pg.items)
            next = pg.nextPage
        }
        val (big, small) = images(info.thumbnails)
        val sub = listOf(by, "${songs.size} songs").filter { it.isNotBlank() }.joinToString(" · ")
        val coll = MediaCollection(kind, url, title, sub, big, small, url, songs.size, SRC_YT)
        return LinkResult.Detail(coll, songs)
    }

    suspend fun resolveLink(link: String): LinkResult = withContext(Dispatchers.IO) {
        init()
        val vid = ID_RE.find(link)?.groupValues?.get(1)
        if (vid != null) {
            val info = StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=$vid")
            val (big, small) = images(info.thumbnails)
            LinkResult.Songs(
                listOf(
                    Song(
                        id = "yt_$vid", title = (info.name ?: "").ifBlank { "Untitled" },
                        artist = cleanArtist(info.uploaderName).ifBlank { "Unknown artist" },
                        album = "", year = yearOf(info), copyright = "",
                        duration = info.duration.toInt().coerceAtLeast(0),
                        url = "https://music.youtube.com/watch?v=$vid",
                        cover = big, thumb = small, links = emptyMap(), source = SRC_YT
                    )
                )
            )
        } else {
            val id = LIST_RE.find(link)?.groupValues?.get(1) ?: error("Unsupported YouTube link")
            detailOf("https://www.youtube.com/playlist?list=$id", if (id.startsWith("OLAK5uy")) "album" else "playlist", "")
        }
    }

    // ---------------- stream links (looked up at play / download time) ----------------
    /**
     * Picks the audio stream closest to [target] kbps. [needM4a] = true when the file will be saved
     * as M4A without re-encoding (the M4A tagger needs an MP4 container); for MP3 or playback any
     * stream (AAC or Opus) is fine because Android decodes both.
     * Blocking: call from a background thread.
     */
    fun resolve(videoId: String, target: Int, needM4a: Boolean): YtAudio {
        init()
        val info = StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=$videoId")
        val all = info.audioStreams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !it.content.isNullOrBlank()
        }
        if (all.isEmpty()) error("No playable audio stream found for this song")
        val pool = if (needM4a) all.filter { it.format == MediaFormat.M4A } else all
        if (pool.isEmpty()) error("No M4A stream for this song. Switch the download format to MP3 in Settings.")
        val best = pool.minWithOrNull(compareBy<AudioStream>({ abs(kbps(it) - target) }, { -kbps(it) }))!!
        return YtAudio(best.content, kbps(best), yearOf(info))
    }

    private fun kbps(s: AudioStream): Int = if (s.averageBitrate > 0) s.averageBitrate else 128

    private class Cached(val audio: YtAudio, val until: Long)
    private val cache = ConcurrentHashMap<String, Cached>()

    /** Same as [resolve] for playback, but remembers the link until shortly before it expires (seeks re-open the stream). */
    fun resolveCached(videoId: String, target: Int): YtAudio {
        val key = "$videoId:$target"
        val now = System.currentTimeMillis()
        cache[key]?.let { if (it.until > now) return it.audio }
        val a = resolve(videoId, target, needM4a = false)
        val exp = EXPIRE_RE.find(a.url)?.groupValues?.get(1)?.toLongOrNull()
        cache[key] = Cached(a, if (exp != null) exp * 1000 - 120_000 else now + 20 * 60_000)
        if (cache.size > 40) cache.entries.removeAll { it.value.until <= now }
        return a
    }

    /** Drops remembered links for a song (used to retry after a playback error). */
    fun forget(videoId: String) { cache.keys.removeAll { it.startsWith("$videoId:") } }
}
