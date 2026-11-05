package com.wavebox.app

import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

const val SONG_LIMIT = 15
const val COLL_LIMIT = 10

data class SearchResult(val songs: List<Song>, val rawCount: Int)
data class CollectionResult(val items: List<MediaCollection>, val rawCount: Int)

sealed class LinkResult {
    class Songs(val songs: List<Song>) : LinkResult()
    class Detail(val info: MediaCollection, val songs: List<Song>) : LinkResult()
}

object Api {
    private const val BASE = "https://jiosaavnsearch-up.koshikkumar17.workers.dev/api"
    private val http = OkHttpClient()
    private val LINK_RE = Regex(
        """https?://(?:[\w-]+\.)*(?:jiosaavn|saavn)\.com/\S*(?:song|album|featured|playlist)/\S+""",
        RegexOption.IGNORE_CASE
    )

    fun extractLink(text: String): String? = LINK_RE.find(text)?.value

    fun linkKind(link: String): String {
        val l = link.lowercase()
        return when {
            "/album/" in l -> "album"
            "/featured/" in l || "/playlist/" in l -> "playlist"
            else -> "song"
        }
    }

    suspend fun searchSongs(query: String, page: Int): SearchResult = withContext(Dispatchers.IO) {
        parseSongs(get("$BASE/search/songs?query=${enc(query)}&page=$page&limit=$SONG_LIMIT"))
    }

    suspend fun searchCollections(kind: String, query: String, page: Int): CollectionResult =
        withContext(Dispatchers.IO) {
            val path = if (kind == "album") "albums" else "playlists"
            val body = get("$BASE/search/$path?query=${enc(query)}&page=$page&limit=$COLL_LIMIT")
            val arr = resultsArray(body)
            val out = ArrayList<MediaCollection>()
            for (i in 0 until arr.length()) toCollection(kind, arr.optJSONObject(i))?.let { out.add(it) }
            CollectionResult(out, arr.length())
        }

    suspend fun detail(kind: String, id: String): LinkResult.Detail = detailFrom(kind, "id=${enc(id)}")

    suspend fun resolveLink(link: String): LinkResult = when (linkKind(link)) {
        "album" -> detailFrom("album", "link=${enc(link)}")
        "playlist" -> detailFrom("playlist", "link=${enc(link)}")
        else -> withContext(Dispatchers.IO) {
            LinkResult.Songs(parseSongs(get("$BASE/songs?link=${enc(link)}")).songs)
        }
    }

    private suspend fun detailFrom(kind: String, param: String): LinkResult.Detail =
        withContext(Dispatchers.IO) {
            val path = if (kind == "album") "albums" else "playlists"
            val extra = if (kind == "playlist") "&limit=100" else ""
            val first = JSONObject(get("$BASE/$path?$param$extra")).optJSONObject("data")
                ?: error("No data")
            val info = toCollection(kind, first) ?: error("Bad data")
            val songs = ArrayList<Song>()
            val seen = HashSet<String>()

            fun addFrom(o: JSONObject): Int {
                val arr = o.optJSONArray("songs") ?: return 0
                var added = 0
                for (i in 0 until arr.length()) {
                    val s = toSong(arr.optJSONObject(i)) ?: continue
                    if (seen.add(s.id)) { songs.add(s); added++ }
                }
                return added
            }

            addFrom(first)
            if (kind == "playlist") {
                var page = 1
                while (songs.size < info.count && page < 10) {
                    val o = JSONObject(get("$BASE/$path?$param&limit=100&page=$page")).optJSONObject("data") ?: break
                    if (addFrom(o) == 0) break
                    page++
                }
            }
            LinkResult.Detail(info, songs)
        }

    // ---------- helpers ----------
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): String {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            return r.body?.string() ?: error("Empty response")
        }
    }

    private fun dec(s: String?): String =
        if (s.isNullOrEmpty()) "" else HtmlCompat.fromHtml(s, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()

    private fun resultsArray(body: String): JSONArray {
        val data = JSONObject(body).opt("data")
        return when (data) {
            is JSONArray -> data
            is JSONObject -> data.optJSONArray("results") ?: JSONArray().put(data)
            else -> JSONArray()
        }
    }

    private fun parseSongs(body: String): SearchResult {
        val arr = resultsArray(body)
        val out = ArrayList<Song>()
        for (i in 0 until arr.length()) toSong(arr.optJSONObject(i))?.let { out.add(it) }
        return SearchResult(out, arr.length())
    }

    private fun pickImg(arr: JSONArray?, quality: String): String {
        if (arr == null || arr.length() == 0) return ""
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("quality") == quality) return o.optString("url")
        }
        return arr.optJSONObject(arr.length() - 1)?.optString("url") ?: ""
    }

    private fun names(arr: JSONArray?): String {
        if (arr == null) return ""
        val list = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val n = arr.optJSONObject(i)?.optString("name") ?: continue
            if (n.isNotBlank()) list.add(n)
        }
        return dec(list.joinToString(", "))
    }

    private fun toCollection(kind: String, o: JSONObject?): MediaCollection? {
        if (o == null) return null
        val id = o.optString("id")
        if (id.isBlank()) return null
        val count = o.optInt("songCount")
        val sub = if (kind == "album") {
            val artists = o.optJSONObject("artists")
            val a = names(artists?.optJSONArray("primary")?.takeIf { it.length() > 0 } ?: artists?.optJSONArray("all"))
            listOf(a, o.optString("year")).filter { it.isNotBlank() }.joinToString(" · ")
        } else {
            if (count > 0) "$count songs" else "Playlist"
        }
        val imgs = o.optJSONArray("image")
        return MediaCollection(
            kind = kind, id = id,
            title = dec(o.optString("name")).ifBlank { "Untitled" },
            subtitle = sub,
            image = pickImg(imgs, "500x500"),
            thumb = pickImg(imgs, "150x150"),
            url = o.optString("url"),
            count = count
        )
    }

    private fun toSong(s: JSONObject?): Song? {
        if (s == null) return null
        val id = s.optString("id")
        if (id.isBlank()) return null
        val dl = s.optJSONArray("downloadUrl") ?: return null
        val links = LinkedHashMap<Int, String>()
        for (i in 0 until dl.length()) {
            val d = dl.optJSONObject(i) ?: continue
            val q = d.optString("quality").filter { it.isDigit() }.toIntOrNull() ?: continue
            val u = d.optString("url")
            if (u.isNotBlank()) links[q] = u
        }
        if (links.isEmpty()) return null
        val artists = s.optJSONObject("artists")
        val primary = artists?.optJSONArray("primary")?.takeIf { it.length() > 0 } ?: artists?.optJSONArray("all")
        val images = s.optJSONArray("image")
        return Song(
            id = id,
            title = dec(s.optString("name")).ifBlank { "Untitled" },
            artist = names(primary).ifBlank { "Unknown artist" },
            album = dec(s.optJSONObject("album")?.optString("name")),
            year = s.optString("year"),
            copyright = dec(s.optString("copyright")),
            duration = s.optInt("duration"),
            url = s.optString("url"),
            cover = pickImg(images, "500x500"),
            thumb = pickImg(images, "150x150"),
            links = links
        )
    }
}
