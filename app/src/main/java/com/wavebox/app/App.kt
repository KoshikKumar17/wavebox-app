@file:OptIn(ExperimentalMaterial3Api::class)

package com.wavebox.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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

    BackHandler(enabled = showFull) { showFull = false }
    BackHandler(enabled = !showFull && vm.detail != null) { vm.closeDetail() }
    BackHandler(enabled = !showFull && vm.detail == null && vm.tab != 0) { vm.tab = 0 }

    Box(Modifier.fillMaxSize()) {
        GradientBackground(seed = vm.nowId ?: "home", animate = vm.animateBg) {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snack) },
                bottomBar = {
                    Column {
                        if (vm.nowSong != null) MiniPlayer(vm) { showFull = true }
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

        AnimatedVisibility(
            visible = showFull,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            PlayerScreen(vm, onClose = { showFull = false })
        }

        vm.pickFor?.let { songs -> PlaylistPickerDialog(vm, songs) { vm.pickFor = null } }
    }
}
