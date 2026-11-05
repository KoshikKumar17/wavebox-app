@file:OptIn(ExperimentalMaterial3Api::class)

package com.wavebox.app

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

fun fmtSec(s: Int): String = "%d:%02d".format(s / 60, s % 60)
fun fmtMs(ms: Long): String = fmtSec((ms / 1000).toInt())

fun shareSong(ctx: Context, s: Song) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, s.title)
        putExtra(Intent.EXTRA_TEXT, s.url.ifBlank { "${s.title} - ${s.artist}" })
    }
    ctx.startActivity(Intent.createChooser(i, "Share song"))
}

@Composable
fun glass(alpha: Float = 0.62f): Color = MaterialTheme.colorScheme.surface.copy(alpha = alpha)

@Composable
fun Cover(url: String?, modifier: Modifier = Modifier, radius: Dp = 14.dp) {
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.45f))
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Ticks the playback position/duration twice a second. */
@Composable
fun rememberProgress(vm: AppViewModel): State<Pair<Long, Long>> {
    val st = remember { mutableStateOf(0L to 0L) }
    androidx.compose.runtime.LaunchedEffect(vm.nowId) {
        while (true) {
            st.value = vm.pos() to vm.dur()
            delay(400)
        }
    }
    return st
}

@Composable
fun MenuRow(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

@Composable
fun SongRow(
    vm: AppViewModel,
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    extra: List<Triple<String, ImageVector, () -> Unit>> = emptyList()
) {
    val ctx = LocalContext.current
    val progress by vm.dlProgress.collectAsState()
    val p = progress[song.id]
    val active = vm.nowId == song.id
    var menu by remember { mutableStateOf(false) }
    val fav = vm.isFav(song.id)

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) else glass()
        )
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(song.thumb, Modifier.size(52.dp), 12.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
                )
                Text(
                    song.artist + if (song.duration > 0) "  ·  " + fmtSec(song.duration) else "",
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (active && vm.isPlaying) {
                Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            if (song.links.isNotEmpty()) {
                when {
                    p != null -> IconButton(onClick = { vm.cancelDownload(song.id) }, modifier = Modifier.size(40.dp)) {
                        if (p >= 100) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else CircularProgressIndicator(progress = { p / 100f }, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    vm.isDownloaded(song.id) -> Icon(
                        Icons.Default.DownloadDone, "Downloaded",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 9.dp).size(22.dp)
                    )
                    else -> IconButton(onClick = { vm.download(song) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Download, "Download")
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuRow("Play next", Icons.Default.QueueMusic) { menu = false; vm.playNext(song) }
                    MenuRow("Add to queue", Icons.Default.QueueMusic) { menu = false; vm.addToQueue(song) }
                    MenuRow("Add to playlist", Icons.Default.PlaylistAdd) { menu = false; vm.pickFor = listOf(song) }
                    MenuRow(if (fav) "Remove from liked" else "Like", if (fav) Icons.Default.FavoriteBorder else Icons.Default.Favorite) {
                        menu = false; vm.toggleFav(song)
                    }
                    if (song.url.isNotBlank()) MenuRow("Share link", Icons.Default.Share) { menu = false; shareSong(ctx, song) }
                    extra.forEach { (label, icon, action) -> MenuRow(label, icon) { menu = false; action() } }
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(vm: AppViewModel, onOpen: () -> Unit) {
    val s = vm.nowSong ?: return
    val prog by rememberProgress(vm)
    val (pos, dur) = prog
    val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = glass(0.88f))
    ) {
        Column {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(s.thumb, Modifier.size(46.dp), 12.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = vm::prev, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.SkipPrevious, "Previous") }
                FilledIconButton(onClick = vm::toggle, modifier = Modifier.size(44.dp)) {
                    Icon(if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (vm.isPlaying) "Pause" else "Play")
                }
                IconButton(onClick = vm::next, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.SkipNext, "Next") }
            }
            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(2.dp))
        }
    }
}

@Composable
fun SortButton(mode: SortMode, allowRecent: Boolean, onChange: (SortMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.Sort, "Sort") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortMode.entries.filter { allowRecent || it != SortMode.RECENT }.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.label + if (m == mode) "   ✓" else "") },
                    onClick = { onChange(m); open = false }
                )
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        Spacer(Modifier.height(10.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Name") }, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun PlaylistPickerDialog(vm: AppViewModel, songs: List<Song>, onDismiss: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog(
            title = "New playlist", initial = "", confirm = "Create",
            onDismiss = { creating = false },
            onConfirm = { n ->
                val id = vm.createPlaylist(n, songs)
                vm.addToPlaylist(id, songs)
                onDismiss()
            }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("New playlist")
                }
                if (vm.playlists.isEmpty()) {
                    Text("You have no playlists yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                vm.playlists.forEach { p ->
                    TextButton(onClick = { vm.addToPlaylist(p.id, songs); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text(p.name + "  (" + p.songs.size + ")", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

