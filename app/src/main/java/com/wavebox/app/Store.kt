package com.wavebox.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("wavebox_data", Context.MODE_PRIVATE)

    fun loadSongs(key: String): List<Song> = runCatching {
        val a = JSONArray(sp.getString(key, "[]"))
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Song.fromJson(it) } }
    }.getOrDefault(emptyList())

    fun saveSongs(key: String, list: List<Song>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        sp.edit().putString(key, a.toString()).apply()
    }

    fun loadPlaylists(): List<UserPlaylist> = runCatching {
        val a = JSONArray(sp.getString("playlists", "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val sa = o.optJSONArray("songs") ?: JSONArray()
            val songs = (0 until sa.length()).mapNotNull { j -> sa.optJSONObject(j)?.let { Song.fromJson(it) } }
            UserPlaylist(o.optString("id"), o.optString("name"), songs)
        }
    }.getOrDefault(emptyList())

    fun savePlaylists(list: List<UserPlaylist>) {
        val a = JSONArray()
        list.forEach { p ->
            val sa = JSONArray()
            p.songs.forEach { sa.put(it.toJson()) }
            a.put(JSONObject().put("id", p.id).put("name", p.name).put("songs", sa))
        }
        sp.edit().putString("playlists", a.toString()).apply()
    }
}
