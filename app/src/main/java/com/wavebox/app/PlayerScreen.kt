@file:OptIn(ExperimentalMaterial3Api::class)

package com.wavebox.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

@Composable
fun PlayerScreen(vm: AppViewModel, onClose: () -> Unit) {
    val s = vm.nowSong
    // The empty-tap handler stops taps on blank areas (like the cover) from falling through
    // to the song list underneath the player.
    GradientBackground(
        seed = vm.nowId ?: "home", animate = vm.animateBg,
        modifier = Modifier.pointerInput(Unit) { detectTapGestures { } }
    ) {
        if (s == null) {
            Column(Modifier.fillMaxSize().systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Close") }
                }
                EmptyState("Nothing is playing")
            }
            return@GradientBackground
        }

        val ctx = LocalContext.current
        val prog by rememberProgress(vm)
        val (pos, dur) = prog
        var drag by remember { mutableStateOf<Float?>(null) }
        var showQueue by remember { mutableStateOf(false) }
        var showSleep by remember { mutableStateOf(false) }
        val dlMap by vm.dlProgress.collectAsState()
        val dl = dlMap[s.id]
        val coverScale by animateFloatAsState(if (vm.isPlaying) 1f else 0.9f, tween(500), label = "cover")
        val fav = vm.isFav(s.id)

        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Close") }
                Text(
                    "Now playing", modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = { showQueue = true }) { Icon(Icons.Default.QueueMusic, "Queue") }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight, 340.dp)
                Cover(
                    s.cover.ifBlank { s.thumb },
                    Modifier.size(side).scale(coverScale).shadow(20.dp, RoundedCornerShape(28.dp)),
                    28.dp
                )
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(s.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { vm.toggleFav(s) }) {
                    Icon(
                        if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Like",
                        tint = if (fav) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Slider(
                value = drag ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                onValueChange = { drag = it },
                onValueChangeFinished = {
                    drag?.let { vm.seekTo((it * dur).toLong()) }
                    drag = null
                }
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(fmtMs(if (drag != null) ((drag ?: 0f) * dur).toLong() else pos), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(fmtMs(dur), style = MaterialTheme.typography.labelMedium)
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = vm::toggleShuffle) {
                    Icon(
                        Icons.Default.Shuffle, "Shuffle",
                        tint = if (vm.shuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = vm::prev, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(34.dp))
                }
                FilledIconButton(onClick = vm::toggle, modifier = Modifier.size(72.dp)) {
                    Icon(
                        if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (vm.isPlaying) "Pause" else "Play", modifier = Modifier.size(38.dp)
                    )
                }
                IconButton(onClick = vm::next, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = vm::cycleRepeat) {
                    Icon(
                        if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Repeat",
                        tint = if (vm.repeatMode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
            ) {
                if (s.links.isNotEmpty()) {
                    when {
                        dl != null -> IconButton(onClick = { vm.cancelDownload(s.id) }) {
                            if (dl >= 100) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            else CircularProgressIndicator(progress = { dl / 100f }, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                        vm.isDownloaded(s.id) -> Icon(Icons.Default.DownloadDone, "Downloaded", tint = MaterialTheme.colorScheme.primary)
                        else -> IconButton(onClick = { vm.download(s) }) { Icon(Icons.Default.Download, "Download") }
                    }
                }
                IconButton(onClick = { vm.pickFor = listOf(s) }) { Icon(Icons.Default.PlaylistAdd, "Add to playlist") }
                if (s.url.isNotBlank()) IconButton(onClick = { shareSong(ctx, s) }) { Icon(Icons.Default.Share, "Share") }
                IconButton(onClick = { showSleep = true }) {
                    Icon(
                        Icons.Default.Bedtime, "Sleep timer",
                        tint = if (vm.sleepMinutes > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (showQueue) {
            ModalBottomSheet(onDismissRequest = { showQueue = false }) {
                Text("Up next", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                LazyColumn(Modifier.fillMaxWidth()) {
                    itemsIndexed(vm.queueIds, key = { i, id -> "$i-$id" }) { i, id ->
                        val q = vm.songById(id)
                        if (q != null) {
                            val current = id == vm.nowId
                            Row(
                                Modifier.fillMaxWidth().clickable { vm.jumpTo(i) }.padding(horizontal = 20.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Cover(q.thumb, Modifier.size(44.dp), 10.dp)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(
                                        q.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(q.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (!current) IconButton(onClick = { vm.removeFromQueue(i) }) { Icon(Icons.Default.Close, "Remove") }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }

        if (showSleep) {
            AlertDialog(
                onDismissRequest = { showSleep = false },
                title = { Text("Sleep timer") },
                text = {
                    Column {
                        listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "1 hour").forEach { (m, label) ->
                            TextButton(onClick = { vm.setSleep(m); showSleep = false }, modifier = Modifier.fillMaxWidth()) {
                                Text(label + if (vm.sleepMinutes == m) "   ✓" else "", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showSleep = false }) { Text("Close") } }
            )
        }
    }
}
