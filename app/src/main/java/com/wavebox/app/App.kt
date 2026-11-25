@file:OptIn(ExperimentalMaterial3Api::class)

package com.wavebox.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

private data class TabItem(val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("Home", Icons.Default.Home),
    TabItem("Search", Icons.Default.Search),
    TabItem("Library", Icons.Default.LibraryMusic),
    TabItem("Music", Icons.Default.Download),
    TabItem("Settings", Icons.Default.Settings)
)

@Composable
fun WaveboxApp(vm: AppViewModel) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }
    var showFull by rememberSaveable { mutableStateOf(false) }

    // 0 = player collapsed to the bottom bar, 1 = full player. One value drives the whole motion,
    // so the player follows the finger and a flick keeps its speed (like YouTube Music).
    var expand by remember { mutableFloatStateOf(if (showFull) 1f else 0f) }
    var barPx by remember { mutableFloatStateOf(0f) }
    var settle by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val playerVisible by remember { derivedStateOf { expand > 0.001f } }

    BackHandler(enabled = showFull) { settle?.cancel(); showFull = false; settle = scope.launch { animate(expand, 0f, 0f, spring(Spring.DampingRatioNoBouncy, 380f)) { v, _ -> expand = v.coerceIn(0f, 1f) } } }
    BackHandler(enabled = !showFull && vm.detail != null) { vm.closeDetail() }
    BackHandler(enabled = !showFull && vm.detail == null && vm.tab != 0) { vm.tab = 0 }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat()
        val travel = (heightPx - barPx).coerceAtLeast(1f)

        fun settleTo(target: Float, velocity: Float = 0f) {
            settle?.cancel()
            showFull = target > 0.5f
            settle = scope.launch {
                animate(expand, target, velocity, spring(Spring.DampingRatioNoBouncy, 380f)) { v, _ ->
                    expand = v.coerceIn(0f, 1f)
                }
            }
        }

        val dragState = rememberDraggableState { dy ->
            settle?.cancel()
            expand = (expand - dy / travel).coerceIn(0f, 1f)
        }
        val dragMod = Modifier.draggable(
            state = dragState, orientation = Orientation.Vertical,
            onDragStopped = { v ->
                val target = when {
                    v < -700f -> 1f
                    v > 700f -> 0f
                    expand > 0.5f -> 1f
                    else -> 0f
                }
                settleTo(target, -v / travel)
            }
        )

        GradientBackground(seed = vm.nowId ?: "home", animate = vm.animateBg, style = vm.gradientStyle) {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snack) },
                bottomBar = {
                    Column(Modifier.onSizeChanged { barPx = it.height.toFloat() }) {
                        if (vm.nowSong != null) MiniPlayer(vm, onOpen = { settleTo(1f) }, modifier = dragMod)
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)) {
                            tabs.forEachIndexed { i, t ->
                                NavigationBarItem(
                                    selected = vm.tab == i,
                                    onClick = { vm.closeDetail(); vm.tab = i },
                                    icon = { Icon(t.icon, t.label) },
                                    label = { Text(t.label) }
                                )
                            }
                        }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    val d = vm.detail
                    if (d != null) {
                        DetailScreen(vm, d)
                    } else {
                        when (vm.tab) {
                            0 -> HomeScreen(vm)
                            1 -> SearchScreen(vm)
                            2 -> LibraryScreen(vm)
                            3 -> DownloadsScreen(vm)
                            else -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }

        if (playerVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // top edge starts at the bottom bar and follows the finger up to the screen top
                        translationY = (1f - expand) * travel
                        alpha = minOf(1f, expand * 3f)
                        val r = (28f * (1f - expand)).dp
                        shape = RoundedCornerShape(topStart = r, topEnd = r)
                        clip = true
                    }
                    .then(dragMod)
            ) {
                PlayerScreen(vm, onClose = { settleTo(0f) })
            }
        }

        vm.pickFor?.let { songs -> PlaylistPickerDialog(vm, songs) { vm.pickFor = null } }
    }
}
