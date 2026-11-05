package com.wavebox.app

import org.json.JSONObject
import kotlin.math.abs

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val year: String,
    val copyright: String,
    val duration: Int,
    val url: String,
    val cover: String,
    val thumb: String,
    val links: Map<Int, String>,
    val uri: String? = null,   // playable file uri (downloaded / on-device songs)
    val added: Long = 0L
) {
    val qualities: List<Int>
        get() = links.keys.filter { it >= 96 }.sorted().ifEmpty { links.keys.sorted() }

    fun closest(q: Int): Int = qualities.minByOrNull { abs(it - q) } ?: q
    fun urlFor(q: Int): String = links[closest(q)] ?: links.values.firstOrNull() ?: ""

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("artist", artist); put("album", album)
        put("year", year); put("copyright", copyright); put("duration", duration)
        put("url", url); put("cover", cover); put("thumb", thumb)
        put("uri", uri ?: ""); put("added", added)
        val l = JSONObject()
        links.forEach { (k, v) -> l.put(k.toString(), v) }
        put("links", l)
    }

    companion object {
        fun fromJson(o: JSONObject): Song {
            val m = LinkedHashMap<Int, String>()
            val lj = o.optJSONObject("links")
            if (lj != null) {
                val keys = lj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val q = k.toIntOrNull() ?: continue
                    m[q] = lj.optString(k)
                }
            }
            return Song(
                id = o.optString("id"), title = o.optString("title"), artist = o.optString("artist"),
                album = o.optString("album"), year = o.optString("year"),
                copyright = o.optString("copyright"), duration = o.optInt("duration"),
                url = o.optString("url"), cover = o.optString("cover"), thumb = o.optString("thumb"),
                links = m, uri = o.optString("uri").ifBlank { null }, added = o.optLong("added")
            )
        }
    }
}

/** An album or a JioSaavn playlist (search result or detail header). */
data class MediaCollection(
    val kind: String,        // "album" | "playlist"
    val id: String,
    val title: String,
    val subtitle: String,
    val image: String,
    val thumb: String,
    val url: String,
    val count: Int
)

data class UserPlaylist(val id: String, val name: String, val songs: List<Song>)

enum class SortMode(val label: String) {
    DEFAULT("Default order"),
    TITLE("Title (A–Z)"),
    ARTIST("Artist (A–Z)"),
    DURATION("Duration"),
    RECENT("Recently added")
}

fun List<Song>.applySort(mode: SortMode): List<Song> = when (mode) {
    SortMode.DEFAULT -> this
    SortMode.TITLE -> sortedBy { it.title.lowercase() }
    SortMode.ARTIST -> sortedBy { it.artist.lowercase() }
    SortMode.DURATION -> sortedBy { it.duration }
    SortMode.RECENT -> sortedByDescending { it.added }
}
