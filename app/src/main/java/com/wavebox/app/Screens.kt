@file:OptIn(ExperimentalMaterial3Api::class)

package com.wavebox.app

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Calendar

// ======================= HOME =======================
@Composable
fun HomeScreen(vm: AppViewModel) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greet = when {
        hour < 5 -> "Good night"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 20.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                Text(greet, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("What do you want to hear?", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("Liked", Icons.Default.Favorite, vm.favorites.size.toString(), Modifier.weight(1f)) { vm.openFavorites() }
                QuickTile("Downloads", Icons.Default.Download, vm.downloaded.size.toString(), Modifier.weight(1f)) {
                    vm.closeDetail(); vm.downloadsSubTab = 0; vm.tab = 3
                }
                QuickTile("Playlists", Icons.Default.LibraryMusic, vm.playlists.size.toString(), Modifier.weight(1f)) {
                    vm.closeDetail(); vm.tab = 2
                }
            }
        }
        if (vm.recents.isEmpty() && vm.downloaded.isEmpty() && vm.playlists.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = glass())) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Start listening", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Search for songs, albums and playlists, or paste a JioSaavn link.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { vm.tab = 1 }) {
                            Icon(Icons.Default.Search, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Search")
                        }
                    }
                }
            }
        }
        if (vm.recents.isNotEmpty()) {
            item { SectionTitle("Recently played") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(vm.recents, key = { _, s -> s.id }) { i, s ->
                        SquareCard(s.cover.ifBlank { s.thumb }, s.title, s.artist) { vm.play(vm.recents, i) }
                    }
                }
            }
        }
        if (vm.playlists.isNotEmpty()) {
            item { SectionTitle("Your playlists") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(vm.playlists, key = { it.id }) { p ->
                        SquareCard(p.songs.firstOrNull()?.cover, p.name, "${p.songs.size} songs") { vm.openUserPlaylist(p) }
                    }
                }
            }
        }
        if (vm.downloaded.isNotEmpty()) {
            item { SectionTitle("Downloaded") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(vm.downloaded.take(20), key = { _, s -> s.id }) { i, s ->
                        SquareCard(s.cover.ifBlank { s.thumb }, s.title, s.artist) { vm.play(vm.downloaded, i) }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTile(label: String, icon: ImageVector, count: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = glass())
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SquareCard(image: String?, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = Modifier.width(140.dp), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = glass())
    ) {
        Column(Modifier.padding(8.dp)) {
            Cover(image, Modifier.fillMaxWidth().height(124.dp), 14.dp)
            Spacer(Modifier.height(8.dp))
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ======================= SEARCH =======================
@Composable
fun SearchScreen(vm: AppViewModel) {
    val focus = LocalFocusManager.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            OutlinedTextField(
                value = vm.query, onValueChange = { vm.onQuery(it) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Search or paste a JioSaavn link") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) IconButton(onClick = { vm.onQuery("") }) { Icon(Icons.Default.Close, "Clear") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(); focus.clearFocus() })
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("songs" to "Songs", "albums" to "Albums", "playlists" to "Playlists").forEach { (key, label) ->
                    FilterChip(selected = vm.searchType == key, onClick = { vm.setType(key) }, label = { Text(label) })
                }
            }
        }
        if (vm.searchType == "songs") {
            itemsIndexed(vm.songResults, key = { _, s -> s.id }) { i, s ->
                SongRow(vm, s, onClick = { vm.play(vm.songResults, i) })
            }
        } else {
            items(vm.collResults, key = { it.kind + it.id }) { c -> CollectionRow(c) { vm.openCollection(c) } }
        }
        if (vm.searching) item {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        vm.searchError?.let { e ->
            item { Text(e, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)) }
        }
        if (vm.canLoadMore && !vm.searching) item {
            OutlinedButton(onClick = vm::loadMore, modifier = Modifier.fillMaxWidth()) { Text("Load more") }
        }
        val empty = if (vm.searchType == "songs") vm.songResults.isEmpty() else vm.collResults.isEmpty()
        if (empty && !vm.searching && vm.searchError == null) item {
            EmptyState(
                if (vm.searchType == "songs") "Type to search songs, or paste a song, album or playlist link."
                else "Type a name and press search to find ${vm.searchType}."
            )
        }
    }
}

@Composable
private fun CollectionRow(c: MediaCollection, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = glass())
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(c.thumb.ifBlank { c.image }, Modifier.size(64.dp), 14.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(c.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(c.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ======================= LIBRARY =======================
@Composable
fun LibraryScreen(vm: AppViewModel) {
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<UserPlaylist?>(null) }
    var deleting by remember { mutableStateOf<UserPlaylist?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 20.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { creating = true }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("New playlist")
                }
            }
        }
        item {
            Card(
                onClick = { vm.openFavorites() }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = glass())
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(40.dp).padding(8.dp))
                    Column(Modifier.padding(start = 8.dp)) {
                        Text("Liked songs", fontWeight = FontWeight.SemiBold)
                        Text("${vm.favorites.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (vm.playlists.isEmpty()) item { EmptyState("Create a playlist and add songs from any list using the ⋮ menu.") }
        items(vm.playlists, key = { it.id }) { p ->
            var menu by remember { mutableStateOf(false) }
            Card(
                onClick = { vm.openUserPlaylist(p) }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = glass())
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(p.songs.firstOrNull()?.thumb, Modifier.size(56.dp), 14.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text("${p.songs.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            MenuRow("Rename", Icons.Default.Edit) { menu = false; renaming = p }
                            MenuRow("Delete", Icons.Default.Delete) { menu = false; deleting = p }
                        }
                    }
                }
            }
        }
    }

    if (creating) NameDialog("New playlist", "", "Create", { creating = false }) { vm.createPlaylist(it) }
    renaming?.let { p ->
        NameDialog("Rename playlist", p.name, "Save", { renaming = null }) { vm.renamePlaylist(p.id, it) }
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete playlist?") },
            text = { Text("\"${p.name}\" will be removed. The songs themselves are not deleted.") },
            confirmButton = { TextButton(onClick = { vm.deletePlaylist(p.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

// ======================= DOWNLOADS / ON DEVICE =======================
@Composable
fun DownloadsScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Your music", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 8.dp)
        )
        TabRow(selectedTabIndex = vm.downloadsSubTab, containerColor = Color.Transparent) {
            Tab(selected = vm.downloadsSubTab == 0, onClick = { vm.downloadsSubTab = 0 }, text = { Text("Downloaded") })
            Tab(selected = vm.downloadsSubTab == 1, onClick = { vm.downloadsSubTab = 1 }, text = { Text("On device") })
        }
        if (vm.downloadsSubTab == 0) {
            SongListSection(
                vm, vm.downloaded, "Nothing downloaded yet. Tap the download icon on any song.",
                SortMode.DEFAULT, allowRecent = true,
                extraFor = { s -> listOf(Triple("Delete download", Icons.Default.Delete) { vm.deleteDownloaded(s) }) }
            )
        } else {
            LocalSection(vm)
        }
    }
}

@Composable
private fun LocalSection(vm: AppViewModel) {
    var granted by remember { mutableStateOf(vm.hasAudioAccess()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
    }
    LaunchedEffect(granted) { if (granted) vm.scanLocal() }
    if (!granted) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Allow access to audio files to play the music stored on this phone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { launcher.launch(vm.audioPermission()) }) { Text("Allow access") }
        }
    } else if (vm.localLoading && vm.localSongs.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        SongListSection(vm, vm.localSongs, "No music files found on this device.", SortMode.TITLE, allowRecent = true, extraFor = { emptyList() })
    }
}

@Composable
fun SongListSection(
    vm: AppViewModel,
    songs: List<Song>,
    emptyText: String,
    defaultSort: SortMode,
    allowRecent: Boolean,
    extraFor: (Song) -> List<Triple<String, ImageVector, () -> Unit>>
) {
    var sort by remember { mutableStateOf(defaultSort) }
    val sorted = remember(songs, sort) { songs.applySort(sort) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (sorted.isEmpty()) {
            item { EmptyState(emptyText) }
        } else {
            item {
                ListActions(
                    onPlay = { vm.play(sorted, 0) },
                    onShuffle = { vm.shufflePlay(sorted) },
                    sort = sort, allowRecent = allowRecent, onSort = { sort = it },
                    extras = {}
                )
            }
            itemsIndexed(sorted, key = { _, s -> s.id }) { i, s ->
                SongRow(vm, s, onClick = { vm.play(sorted, i) }, extra = extraFor(s))
            }
        }
    }
}

@Composable
fun ListActions(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    sort: SortMode,
    allowRecent: Boolean,
    onSort: (SortMode) -> Unit,
    extras: @Composable () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(4.dp))
            Text("Play")
        }
        FilledTonalButton(onClick = onShuffle) {
            Icon(Icons.Default.Shuffle, null)
            Spacer(Modifier.width(4.dp))
            Text("Shuffle")
        }
        Spacer(Modifier.weight(1f))
        extras()
        SortButton(sort, allowRecent, onSort)
    }
}

// ======================= DETAIL =======================
@Composable
fun DetailScreen(vm: AppViewModel, d: DetailState) {
    val songs = vm.detailSongs(d)
    var sort by remember(d.kind, d.id) { mutableStateOf(SortMode.DEFAULT) }
    val sorted = remember(songs, sort) { songs.applySort(sort) }
    val canEdit = d.kind == "user"
    val title = vm.detailTitle(d)

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.closeDetail() }) { Icon(Icons.Default.ArrowBack, "Back") }
                    Spacer(Modifier.weight(1f))
                    if (d.kind == "album" || d.kind == "playlist") {
                        IconButton(onClick = { vm.saveDetailAsPlaylist(d.copy(songs = songs)) }, enabled = songs.isNotEmpty()) {
                            Icon(Icons.Default.PlaylistAdd, "Save as my playlist")
                        }
                    }
                }
                Cover(vm.detailImage(d), Modifier.size(180.dp), 26.dp)
                Spacer(Modifier.height(12.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = listOf(d.subtitle, if (songs.isNotEmpty()) "${songs.size} songs" else "").filter { it.isNotBlank() }.distinct().joinToString("  ·  ")
                if (sub.isNotBlank()) Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
            }
        }
        if (d.loading) item {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        d.error?.let { e ->
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(e, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { vm.retryDetail() }) { Text("Retry") }
                }
            }
        }
        if (songs.isNotEmpty()) {
            item {
                ListActions(
                    onPlay = { vm.play(sorted, 0) },
                    onShuffle = { vm.shufflePlay(sorted) },
                    sort = sort, allowRecent = false, onSort = { sort = it },
                    extras = {
                        IconButton(onClick = { vm.downloadAll(sorted) }) { Icon(Icons.Default.Download, "Download all") }
                        IconButton(onClick = { vm.pickFor = sorted }) { Icon(Icons.Default.PlaylistAdd, "Add all to playlist") }
                    }
                )
            }
            itemsIndexed(sorted, key = { _, s -> s.id }) { i, s ->
                SongRow(
                    vm, s, onClick = { vm.play(sorted, i) },
                    extra = if (canEdit) listOf(Triple("Remove from playlist", Icons.Default.Delete) { vm.removeFromPlaylist(d.id, s.id) }) else emptyList()
                )
            }
        } else if (!d.loading && d.error == null) {
            item { EmptyState(if (d.kind == "fav") "Songs you like will show up here." else "This list is empty.") }
        }
    }
}

// ======================= SETTINGS =======================
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setFolder(uri)
    }
    val quals = listOf(96, 160, 320)

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 20.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }

        item {
            SettingsCard("Playback quality", "Streaming quality (kbps). Applies to songs you start next.") {
                Segmented(quals.map { "$it" }, quals.indexOf(vm.playQuality).coerceAtLeast(0)) { vm.updatePlayQuality(quals[it]) }
            }
        }
        item {
            SettingsCard("Download quality", "Bitrate of saved files (kbps).") {
                Segmented(quals.map { "$it" }, quals.indexOf(vm.dlQuality).coerceAtLeast(0)) { vm.updateDlQuality(quals[it]) }
            }
        }
        item {
            SettingsCard("Download format", "M4A keeps the original audio. MP3 is converted on your phone (takes a few seconds per song).") {
                val fmts = listOf("m4a", "mp3")
                Segmented(listOf("M4A", "MP3"), fmts.indexOf(vm.dlFormat).coerceAtLeast(0)) { vm.updateDlFormat(fmts[it]) }
            }
        }
        item {
            SettingsCard("Download location", vm.folderLabel()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { picker.launch(null) }) {
                        Icon(Icons.Default.Folder, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Choose folder")
                    }
                    if (vm.folder != null) TextButton(onClick = { vm.resetFolder() }) { Text("Use default") }
                }
            }
        }
        item {
            SettingsCard("Metadata & cover art", "Write title, artist, album, year, copyright and the song link into each file.") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Embed tags and cover", modifier = Modifier.weight(1f))
                    Switch(checked = vm.embed, onCheckedChange = { vm.updateEmbed(it) })
                }
            }
        }
        item {
            SettingsCard("Appearance", "Soft colours that drift slowly and change with the song.") {
                Segmented(listOf("System", "Light", "Dark"), vm.themeMode.coerceIn(0, 2)) { vm.updateThemeMode(it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Animated gradient", modifier = Modifier.weight(1f))
                    Switch(checked = vm.animateBg, onCheckedChange = { vm.updateAnimateBg(it) })
                }
            }
        }
        item {
            SettingsCard("History", "${vm.recents.size} recently played songs") {
                OutlinedButton(onClick = { vm.clearRecents() }, enabled = vm.recents.isNotEmpty()) { Text("Clear recently played") }
            }
        }
        item {
            SettingsCard("Coming soon", "Synced lyrics from LRCLIB.") {}
        }
    }
}

@Composable
private fun SettingsCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = glass())) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected, onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size)
            ) { Text(label) }
        }
    }
}
