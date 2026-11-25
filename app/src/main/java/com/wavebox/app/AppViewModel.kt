package com.wavebox.app

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import org.schabi.newpipe.extractor.Page
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class DetailState(
    val kind: String,          // album | playlist | user | fav
    val id: String,
    val title: String,
    val subtitle: String,
    val image: String,
    val url: String,
    val songs: List<Song> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val source: String = SRC_SAAVN
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    val prefs = Prefs(ctx)
    private val store = Store(ctx)

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    internal fun say(m: String) { messages.tryEmit(m) }

    // ---------------- navigation ----------------
    var tab by mutableIntStateOf(0)
    var downloadsSubTab by mutableIntStateOf(0)

    // ---------------- settings ----------------
    var playQuality by mutableIntStateOf(prefs.playQuality)
        private set
    var dlQuality by mutableIntStateOf(prefs.dlQuality)
        private set
    var ytPlayQuality by mutableIntStateOf(prefs.ytPlayQuality)
        private set
    var ytDlQuality by mutableIntStateOf(prefs.ytDlQuality)
        private set
    var folder by mutableStateOf(prefs.folder)
        private set
    var embed by mutableStateOf(prefs.embed)
        private set
    var animateBg by mutableStateOf(prefs.animateBg)
        private set
    var themeMode by mutableIntStateOf(prefs.themeMode)
        private set
    var gradientStyle by mutableIntStateOf(prefs.gradient)
        private set
    var dlFormat by mutableStateOf(prefs.dlFormat)
        private set

    fun updatePlayQuality(q: Int) { playQuality = q; prefs.playQuality = q }
    fun updateDlQuality(q: Int) { dlQuality = q; prefs.dlQuality = q }
    fun updateYtPlayQuality(q: Int) { ytPlayQuality = q; prefs.ytPlayQuality = q }
    fun updateYtDlQuality(q: Int) { ytDlQuality = q; prefs.ytDlQuality = q }
    fun updateEmbed(v: Boolean) { embed = v; prefs.embed = v }
    fun updateAnimateBg(v: Boolean) { animateBg = v; prefs.animateBg = v }
    fun updateThemeMode(m: Int) { themeMode = m; prefs.themeMode = m }
    fun updateGradient(g: Int) { gradientStyle = g; prefs.gradient = g }
    fun updateDlFormat(f: String) { dlFormat = f; prefs.dlFormat = f }

    fun setFolder(uri: Uri) {
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        folder = uri.toString()
        prefs.folder = folder
    }

    fun resetFolder() { folder = null; prefs.folder = null }

    fun folderLabel(): String {
        val f = folder ?: return "Internal storage › Music › Wavebox"
        val seg = Uri.parse(f).lastPathSegment?.substringAfter(':')
        return if (seg.isNullOrBlank()) "Custom folder" else "Custom › $seg"
    }

    // ---------------- library data ----------------
    var favorites by mutableStateOf(store.loadSongs("fav"))
        private set
    var recents by mutableStateOf(store.loadSongs("recent"))
        private set
    var downloaded by mutableStateOf(store.loadSongs("downloaded"))
        private set
    var playlists by mutableStateOf(store.loadPlaylists())
        private set
    var localSongs by mutableStateOf<List<Song>>(emptyList())
        private set
    var localLoading by mutableStateOf(false)
        private set

    private val downloadedIds by derivedStateOf { downloaded.map { it.id }.toSet() }
    private val favIds by derivedStateOf { favorites.map { it.id }.toSet() }
    fun isDownloaded(id: String) = id in downloadedIds
    fun isFav(id: String) = id in favIds

    fun toggleFav(s: Song) {
        favorites = if (isFav(s.id)) favorites.filter { it.id != s.id } else listOf(s) + favorites
        store.saveSongs("fav", favorites)
    }

    private fun addRecent(s: Song) {
        recents = (listOf(s) + recents.filter { it.id != s.id }).take(30)
        store.saveSongs("recent", recents)
    }

    fun clearRecents() { recents = emptyList(); store.saveSongs("recent", recents) }

    // ---------------- user playlists ----------------
    var pickFor by mutableStateOf<List<Song>?>(null)

    private fun persistPlaylists() = store.savePlaylists(playlists)

    fun createPlaylist(name: String, songs: List<Song> = emptyList()): String {
        val id = "pl_" + System.currentTimeMillis()
        playlists = playlists + UserPlaylist(id, name.trim().ifBlank { "My playlist" }, songs.distinctBy { it.id })
        persistPlaylists()
        return id
    }

    fun addToPlaylist(pid: String, songs: List<Song>) {
        playlists = playlists.map { p ->
            if (p.id == pid) p.copy(songs = (p.songs + songs).distinctBy { it.id }) else p
        }
        persistPlaylists()
        say("Added to playlist")
    }

    fun removeFromPlaylist(pid: String, songId: String) {
        playlists = playlists.map { p -> if (p.id == pid) p.copy(songs = p.songs.filter { it.id != songId }) else p }
        persistPlaylists()
    }

    fun renamePlaylist(pid: String, name: String) {
        playlists = playlists.map { p -> if (p.id == pid) p.copy(name = name.trim().ifBlank { p.name }) else p }
        persistPlaylists()
    }

    fun deletePlaylist(pid: String) {
        if (detail?.kind == "user" && detail?.id == pid) closeDetail()
        playlists = playlists.filter { it.id != pid }
        persistPlaylists()
    }

    fun saveDetailAsPlaylist(d: DetailState) {
        createPlaylist(d.title, d.songs)
        say("Saved to your playlists")
    }

    // ---------------- search ----------------
    var query by mutableStateOf("")
    var searchType by mutableStateOf("songs")
        private set
    /** all | yt | saavn */
    var searchSource by mutableStateOf(SRC_ALL)
        private set
    var songResults by mutableStateOf<List<Song>>(emptyList())
        private set
    var collResults by mutableStateOf<List<MediaCollection>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    var canLoadMore by mutableStateOf(false)
        private set
    private var saavnPage = 1
    private var saavnMore = false
    private var ytNext: Page? = null
    private var ytMore = false
    private var rid = 0
    private var activeQuery = ""
    private var activeType = "songs"
    private var activeSource = SRC_ALL
    private var debounceJob: Job? = null

    fun setType(t: String) { searchType = t; searchError = null; canLoadMore = false }

    fun setSource(s: String) {
        if (s == searchSource) return
        searchSource = s
        searchError = null
        if (query.trim().length >= 2) search()
    }

    private fun linkOf(t: String): String? = Api.extractLink(t) ?: YtSource.extractLink(t)

    fun onQuery(q: String) {
        query = q
        debounceJob?.cancel()
        val t = q.trim()
        if (t.length >= 2 && (searchType == "songs" || linkOf(t) != null)) {
            debounceJob = viewModelScope.launch { delay(650); search() }
        }
    }

    private class Part(
        val songs: List<Song> = emptyList(), val colls: List<MediaCollection> = emptyList(),
        val more: Boolean = false, val next: Page? = null
    )

    private class Batch(val songs: List<Song>, val colls: List<MediaCollection>)

    private fun <T> interleave(a: List<T>, b: List<T>): List<T> {
        val out = ArrayList<T>(a.size + b.size)
        for (i in 0 until maxOf(a.size, b.size)) {
            if (i < a.size) out.add(a[i])
            if (i < b.size) out.add(b[i])
        }
        return out
    }

    /** Fetches one page from every selected source in parallel and merges them (YT Music first). */
    private suspend fun fetchBatch(q: String, type: String, src: String, first: Boolean): Batch = coroutineScope {
        val useY = src != SRC_SAAVN && (first || ytMore)
        val useS = src != SRC_YT && (first || saavnMore)
        val kind = if (type == "albums") "album" else "playlist"
        val sPage = saavnPage
        val yPage = ytNext

        val yj = if (!useY) null else async {
            runCatching {
                if (type == "songs") YtSource.searchSongs(q, yPage).let { Part(songs = it.songs, next = it.next) }
                else YtSource.searchCollections(kind, q, yPage).let { Part(colls = it.items, next = it.next) }
            }
        }
        val sj = if (!useS) null else async {
            runCatching {
                if (type == "songs") Api.searchSongs(q, sPage).let { Part(songs = it.songs, more = it.rawCount >= SONG_LIMIT) }
                else Api.searchCollections(kind, q, sPage).let { Part(colls = it.items, more = it.rawCount >= COLL_LIMIT) }
            }
        }
        val yr = yj?.await()
        val sr = sj?.await()

        val ran = listOfNotNull(yr, sr)
        if (ran.isNotEmpty() && ran.all { it.isFailure }) throw ran.first().exceptionOrNull() ?: IllegalStateException("Search failed")
        if (first) {
            if (yr?.isFailure == true) say("YT Music couldn't be reached")
            if (sr?.isFailure == true) say("JioSaavn couldn't be reached")
        }
        if (yj != null) {
            val p = yr?.getOrNull()
            ytNext = p?.next
            ytMore = p?.next != null
        }
        if (sj != null) {
            val p = sr?.getOrNull()
            saavnMore = p?.more == true
            if (p != null) saavnPage++
        }
        val y = yr?.getOrNull()
        val s = sr?.getOrNull()
        Batch(
            interleave(y?.songs.orEmpty(), s?.songs.orEmpty()),
            interleave(y?.colls.orEmpty(), s?.colls.orEmpty())
        )
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        debounceJob?.cancel()
        val link = linkOf(q)
        val my = ++rid
        searching = true
        searchError = null
        songResults = emptyList()
        collResults = emptyList()
        canLoadMore = false
        saavnPage = 1; saavnMore = false
        ytNext = null; ytMore = false
        activeQuery = q
        activeType = searchType
        activeSource = searchSource
        viewModelScope.launch {
            try {
                if (link != null) {
                    val r = if (Api.extractLink(q) != null) Api.resolveLink(link) else YtSource.resolveLink(link)
                    when (r) {
                        is LinkResult.Songs -> if (my == rid) {
                            searchType = "songs"
                            songResults = r.songs
                            if (r.songs.isEmpty()) searchError = "Nothing found for this link"
                        }
                        is LinkResult.Detail -> if (my == rid) showDetail(r.info, r.songs)
                    }
                } else {
                    val b = fetchBatch(q, activeType, activeSource, first = true)
                    if (my == rid) {
                        songResults = b.songs
                        collResults = b.colls
                        canLoadMore = saavnMore || ytMore
                        if (activeType == "songs" && b.songs.isEmpty()) searchError = "No songs found"
                        if (activeType != "songs" && b.colls.isEmpty()) searchError = "Nothing found"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (my == rid) searchError = "Couldn't load results. Check your connection."
            } finally {
                if (my == rid) searching = false
            }
        }
    }

    fun loadMore() {
        if (searching || !canLoadMore) return
        val my = rid
        searching = true
        viewModelScope.launch {
            try {
                val b = fetchBatch(activeQuery, activeType, activeSource, first = false)
                if (my == rid) {
                    if (activeType == "songs") {
                        val seen = songResults.map { it.id }.toSet()
                        songResults = songResults + b.songs.filter { it.id !in seen }
                    } else {
                        val seen = collResults.map { it.source + it.id }.toSet()
                        collResults = collResults + b.colls.filter { (it.source + it.id) !in seen }
                    }
                    canLoadMore = saavnMore || ytMore
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say("Couldn't load more")
            } finally {
                if (my == rid) searching = false
            }
        }
    }

    fun handleIntent(i: Intent?) {
        val text = when (i?.action) {
            Intent.ACTION_VIEW -> i?.dataString
            Intent.ACTION_SEND -> i?.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        val link = linkOf(text) ?: return
        tab = 1
        query = link
        search()
    }

    // ---------------- detail (album / playlist / user playlist / liked) ----------------
    var detail by mutableStateOf<DetailState?>(null)
        private set
    private var detailRid = 0

    private fun showDetail(info: MediaCollection, songs: List<Song>) {
        detailRid++
        detail = DetailState(info.kind, info.id, info.title, info.subtitle, info.image, info.url, songs, source = info.source)
    }

    fun openCollection(c: MediaCollection) {
        val my = ++detailRid
        detail = DetailState(c.kind, c.id, c.title, c.subtitle, c.image, c.url, loading = true, source = c.source)
        viewModelScope.launch {
            try {
                val r = if (c.source == SRC_YT) YtSource.detail(c) else Api.detail(c.kind, c.id)
                if (my == detailRid) {
                    detail = detail?.copy(
                        title = r.info.title.ifBlank { c.title },
                        subtitle = r.info.subtitle.ifBlank { c.subtitle },
                        image = r.info.image.ifBlank { c.image },
                        songs = r.songs, loading = false, error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (my == detailRid) detail = detail?.copy(loading = false, error = "Couldn't load. Check your connection.")
            }
        }
    }

    fun retryDetail() {
        detail?.let { d -> openCollection(MediaCollection(d.kind, d.id, d.title, d.subtitle, d.image, d.image, d.url, 0, d.source)) }
    }

    fun openUserPlaylist(p: UserPlaylist) { detailRid++; detail = DetailState("user", p.id, p.name, "", "", "") }
    fun openFavorites() { detailRid++; detail = DetailState("fav", "fav", "Liked songs", "", "", "") }
    fun closeDetail() { detailRid++; detail = null }

    fun detailSongs(d: DetailState): List<Song> = when (d.kind) {
        "user" -> playlists.firstOrNull { it.id == d.id }?.songs ?: emptyList()
        "fav" -> favorites
        else -> d.songs
    }

    fun detailTitle(d: DetailState): String =
        if (d.kind == "user") playlists.firstOrNull { it.id == d.id }?.name ?: d.title else d.title

    fun detailImage(d: DetailState): String =
        if (d.kind == "user" || d.kind == "fav") detailSongs(d).firstOrNull()?.cover ?: "" else d.image

    // ---------------- playback ----------------
    private var controller: MediaController? = null
    private val songMap = HashMap<String, Song>()

    var nowId by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var shuffleOn by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var queueIds by mutableStateOf<List<String>>(emptyList())
        private set
    var sleepMinutes by mutableIntStateOf(0)
        private set
    private var sleepJob: Job? = null

    val nowSong: Song? get() = nowId?.let { songMap[it] }
    fun songById(id: String): Song? = songMap[id]

    private fun item(s: Song): MediaItem {
        songMap[s.id] = s
        val uri = s.uri ?: if (s.isYt) "ytm://${s.ytVideoId}" else s.urlFor(playQuality)
        val meta = MediaMetadata.Builder()
            .setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album)
            .setArtworkUri(if (s.cover.isNotBlank()) Uri.parse(s.cover) else null)
            .build()
        return MediaItem.Builder().setMediaId(s.id).setUri(uri).setMediaMetadata(meta).build()
    }

    private fun refreshQueue() {
        val c = controller ?: return
        queueIds = List(c.mediaItemCount) { c.getMediaItemAt(it).mediaId }
    }

    /** Plays [list] starting at [index]. [shuffle] = null keeps the current shuffle setting. */
    fun play(list: List<Song>, index: Int, shuffle: Boolean? = null) {
        val c = controller ?: return
        if (list.isEmpty()) return
        shuffle?.let { c.shuffleModeEnabled = it }
        val start = if (shuffle == true) list.indices.random() else index.coerceIn(0, list.size - 1)
        c.setMediaItems(list.map { item(it) }, start, 0L)
        c.prepare()
        c.play()
    }

    fun shufflePlay(list: List<Song>) = play(list, 0, shuffle = true)

    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun prev() { controller?.seekToPrevious() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun pos(): Long = controller?.currentPosition ?: 0L
    fun dur(): Long {
        val d = controller?.duration ?: C.TIME_UNSET
        return if (d > 0) d else (nowSong?.duration ?: 0) * 1000L
    }

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun playNext(s: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { play(listOf(s), 0); return }
        c.addMediaItem(c.currentMediaItemIndex + 1, item(s))
        // with shuffle on, the player puts the new song at a random spot in its shuffled order
        say(if (c.shuffleModeEnabled) "Added to queue (shuffle is on)" else "Will play next")
    }

    fun addToQueue(s: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { play(listOf(s), 0); return }
        c.addMediaItem(item(s))
        say("Added to queue")
    }

    fun jumpTo(index: Int) {
        controller?.let { it.seekTo(index, 0L); it.play() }
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (c.mediaItemCount > 1) c.removeMediaItem(index)
    }

    fun setSleep(min: Int) {
        sleepJob?.cancel()
        sleepMinutes = min
        if (min > 0) {
            sleepJob = viewModelScope.launch {
                delay(min * 60_000L)
                controller?.pause()
                sleepMinutes = 0
                say("Sleep timer: playback paused")
            }
        }
    }

    private var errorSkips = 0
    private var ytRetryId: String? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            if (playing) { errorSkips = 0; ytRetryId = null }
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            nowId = mediaItem?.mediaId
            // a repeat-one loop is the same song again, not a new "recent"
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                mediaItem?.mediaId?.let { songMap[it] }?.let { addRecent(it) }
            }
        }
        override fun onTimelineChanged(timeline: Timeline, reason: Int) { refreshQueue() }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { shuffleOn = shuffleModeEnabled }
        override fun onRepeatModeChanged(mode: Int) { repeatMode = mode }
        override fun onPlayerError(error: PlaybackException) {
            val c = controller
            val curId = c?.currentMediaItem?.mediaId
            val cur = curId?.let { songMap[it] }
            if (c != null && cur != null && cur.isYt && cur.uri == null && ytRetryId != cur.id) {
                // the stream link may have expired: forget it and try again once with a fresh one
                ytRetryId = cur.id
                YtSource.forget(cur.ytVideoId)
                c.prepare(); c.play()
                return
            }
            if (c != null && c.hasNextMediaItem() && errorSkips < 3) {
                errorSkips++
                say("Couldn't play this song. Skipping to the next one.")
                c.seekToNextMediaItem(); c.prepare(); c.play()
            } else {
                say("Couldn't play this song. Check your connection or the file.")
            }
        }
    }

    // ---------------- downloads ----------------
    // Downloads live in an app-wide scope (not the ViewModel) so they keep running when the
    // screen is closed; DownloadService keeps the process alive while any download is active.
    private companion object {
        val dlScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val dlState = MutableStateFlow<Map<String, Int>>(emptyMap())
        val jobs = HashMap<String, Job>()
        /** ids cancelled in bulk: no per-song "Download cancelled" message */
        val quiet = HashSet<String>()
        val gate = Semaphore(2)
        @Volatile var live: AppViewModel? = null

        fun commitDownloaded(st: Store, rec: Song) {
            val v = live
            val cur = v?.downloaded ?: st.loadSongs("downloaded")
            val merged = listOf(rec) + cur.filter { it.id != rec.id }
            st.saveSongs("downloaded", merged)
            v?.downloaded = merged
        }
    }

    val dlProgress: StateFlow<Map<String, Int>> = dlState

    private fun folderUri(): Uri? = folder?.let { Uri.parse(it) }

    fun download(s: Song) {
        if (!s.remote) { say("This song is already on your device"); return }
        if (isDownloaded(s.id)) { say("Already downloaded"); return }
        if (jobs.containsKey(s.id)) return
        val quality = if (s.isYt) ytDlQuality else dlQuality
        val format = dlFormat
        val target = folderUri()
        val withTags = embed
        val appCtx = ctx
        val st = store
        dlState.update { it + (s.id to 0) }
        val job = dlScope.launch {
            try {
                gate.withPermit {
                    val r = Downloader.run(appCtx, s, quality, format, target, withTags) { p ->
                        dlState.update { m -> m + (s.id to p) }
                    }
                    commitDownloaded(st, s.copy(uri = r.uri, added = System.currentTimeMillis()))
                    live?.say("Saved: ${r.name}")
                }
            } catch (e: CancellationException) {
                if (!quiet.remove(s.id)) live?.say("Download cancelled")
                throw e
            } catch (e: Exception) {
                live?.say(e.message ?: "Download failed")
            } finally {
                dlState.update { it - s.id }
                jobs.remove(s.id)
                DownloadService.sync(appCtx, jobs.size)
            }
        }
        if (job.isCompleted) return
        jobs[s.id] = job
        DownloadService.sync(appCtx, jobs.size)
    }

    fun downloadAll(list: List<Song>) {
        val todo = list.filter { !isDownloaded(it.id) && it.remote }
        if (todo.isEmpty()) { say("Everything is already downloaded"); return }
        val fresh = todo.count { !jobs.containsKey(it.id) }
        if (fresh == 0) { say("Already downloading"); return }
        say(if (fresh == 1) "Downloading 1 song" else "Downloading $fresh songs")
        todo.forEach { download(it) }
    }

    fun cancelDownload(id: String) { jobs[id]?.cancel() }

    /** Cancels every running or waiting download among [list] (songs already saved stay saved). */
    fun cancelDownloads(list: List<Song>) {
        val ids = list.map { it.id }.toSet()
        val hit = jobs.keys.filter { it in ids }
        if (hit.isEmpty()) return
        quiet.addAll(hit)
        hit.forEach { jobs[it]?.cancel() }
        say(if (hit.size == 1) "Cancelled 1 download" else "Cancelled ${hit.size} downloads")
    }

    fun deleteDownloaded(s: Song) {
        downloaded = downloaded.filter { it.id != s.id }
        store.saveSongs("downloaded", downloaded)
        val u = s.uri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(u)
                if (uri.scheme == "file") java.io.File(uri.path ?: return@runCatching).delete()
                else if (uri.authority == "media") ctx.contentResolver.delete(uri, null, null)
                else DocumentFile.fromSingleUri(ctx, uri)?.delete()
            }
        }
    }

    // ---------------- on-device songs ----------------
    fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasAudioAccess(): Boolean =
        ContextCompat.checkSelfPermission(ctx, audioPermission()) == PackageManager.PERMISSION_GRANTED

    fun scanLocal() {
        if (localLoading) return
        localLoading = true
        viewModelScope.launch {
            try {
                localSongs = withContext(Dispatchers.IO) { queryLocal() }
            } catch (e: Exception) {
                say("Couldn't read songs on this device")
            } finally {
                localLoading = false
            }
        }
    }

    private fun queryLocal(): List<Song> {
        val out = ArrayList<Song>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val art = Uri.parse("content://media/external/audio/albumart")
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.YEAR
        )
        val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000"
        ctx.contentResolver.query(base, proj, sel, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iYear = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val cover = ContentUris.withAppendedId(art, c.getLong(iAlbumId)).toString()
                val artist = c.getString(iArtist)
                out.add(
                    Song(
                        id = "local:$id",
                        title = c.getString(iTitle) ?: "Unknown",
                        artist = if (artist.isNullOrBlank() || artist == "<unknown>") "Unknown artist" else artist,
                        album = c.getString(iAlbum) ?: "",
                        year = c.getInt(iYear).let { if (it > 0) it.toString() else "" },
                        copyright = "", duration = (c.getLong(iDur) / 1000).toInt(),
                        url = "", cover = cover, thumb = cover, links = emptyMap(),
                        uri = ContentUris.withAppendedId(base, id).toString(),
                        added = c.getLong(iAdded) * 1000,
                        source = SRC_LOCAL
                    )
                )
            }
        }
        return out
    }

    /** Drops "Downloaded" entries whose file was deleted outside the app (only when we can see all audio, so it can't be wrong). */
    private fun pruneDownloaded() {
        if (!hasAudioAccess() || downloaded.isEmpty()) return
        val snapshot = downloaded
        viewModelScope.launch {
            val gone = withContext(Dispatchers.IO) {
                snapshot.filter { sg ->
                    val u = sg.uri?.let { Uri.parse(it) } ?: return@filter false
                    if (u.authority != "media") return@filter false
                    runCatching {
                        ctx.contentResolver.query(u, arrayOf(MediaStore.Audio.Media._ID), null, null, null)
                            ?.use { it.count == 0 } ?: false
                    }.getOrDefault(false)
                }.map { it.id }.toSet()
            }
            if (gone.isNotEmpty()) {
                downloaded = downloaded.filter { it.id !in gone }
                store.saveSongs("downloaded", downloaded)
            }
        }
    }

    // ---------------- init ----------------
    init {
        live = this
        pruneDownloaded()
        val token = SessionToken(ctx, ComponentName(ctx, PlayerService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                shuffleOn = c.shuffleModeEnabled
                repeatMode = c.repeatMode
                nowId = c.currentMediaItem?.mediaId
                isPlaying = c.isPlaying
                refreshQueue()
            } catch (e: Exception) {
                say("Couldn't start the player")
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    override fun onCleared() {
        if (live === this) live = null
        controller?.release()
        super.onCleared()
    }
}
