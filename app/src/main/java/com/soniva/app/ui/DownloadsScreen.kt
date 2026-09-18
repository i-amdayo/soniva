package com.soniva.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownloadDone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soniva.app.SonivaViewModel
import com.soniva.app.model.Playlist
import com.soniva.app.model.Song
import com.soniva.app.util.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    vm: SonivaViewModel,
    onBack: () -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onMore: (Song) -> Unit
) {
    val downloadedSongs by vm.downloadedSongs.collectAsStateWithLifecycle()
    val downloadedPlaylists by vm.downloadedPlaylists.collectAsStateWithLifecycle()
    val storageUsed by vm.totalStorageUsed.collectAsStateWithLifecycle()
    val totalCount by vm.totalDownloadCount.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val filteredSongs = remember(downloadedSongs, searchQuery) {
        if (searchQuery.isBlank()) downloadedSongs
        else {
            val q = searchQuery.trim().lowercase()
            downloadedSongs.filter {
                it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
            }
        }
    }

    val filteredPlaylists = remember(downloadedPlaylists, searchQuery) {
        if (searchQuery.isBlank()) downloadedPlaylists
        else {
            val q = searchQuery.trim().lowercase()
            downloadedPlaylists.filter { it.playlist.name.lowercase().contains(q) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top App Bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Offline Downloads",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (downloadedSongs.isNotEmpty()) {
                IconButton(onClick = { showDeleteAllDialog = true }) {
                    Icon(
                        Icons.Rounded.DeleteSweep,
                        contentDescription = "Delete all downloads",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Storage progress card
        StorageIndicatorCard(
            usedBytes = storageUsed,
            trackCount = totalCount,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        // Search & Filter
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Filter downloads by title, artist, or album") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        // Tabs
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Songs (${downloadedSongs.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Playlists (${downloadedPlaylists.size})") }
            )
        }

        Spacer(Modifier.height(8.dp))

        // Content
        when (selectedTab) {
            0 -> {
                if (filteredSongs.isEmpty()) {
                    if (downloadedSongs.isEmpty()) {
                        EmptyState(
                            title = "No downloaded music",
                            subtitle = "Tap the download icon on any song to save it for offline listening."
                        )
                    } else {
                        EmptyState(
                            title = "No matching downloads",
                            subtitle = "Try searching with a different term."
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { vm.playAll(filteredSongs) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Play all")
                        }
                        FilledTonalButton(
                            onClick = { vm.playAll(filteredSongs, shuffle = true) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Shuffle, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle")
                        }
                    }

                    LazyColumn(Modifier.fillMaxSize()) {
                        items(filteredSongs, key = { it.id }) { song ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                                        vm.deleteDownload(song.id)
                                        true
                                    } else false
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val isDismissing = dismissState.targetValue != SwipeToDismissBoxValue.Settled
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.errorContainer)
                                            .padding(horizontal = 24.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        if (isDismissing) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Rounded.Delete,
                                                    contentDescription = "Delete download",
                                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    "Delete",
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            ) {
                                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                                    SongRow(
                                        song = song,
                                        onClick = { vm.play(song, filteredSongs) },
                                        downloadProgress = downloadProgress[song.id],
                                        isDownloaded = true,
                                        onDownload = null,
                                        onCancelDownload = { vm.cancelDownload(song.id) },
                                        onMore = { onMore(song) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                if (filteredPlaylists.isEmpty()) {
                    EmptyState(
                        title = "No downloaded playlists",
                        subtitle = "Playlists you download for offline playback will show up here."
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(filteredPlaylists, key = { it.playlist.id }) { dp ->
                            ListItem(
                                headlineContent = { Text(dp.playlist.name, fontWeight = FontWeight.SemiBold) },
                                supportingContent = {
                                    Text("${dp.songCount} songs · ${formatBytes(dp.totalSizeBytes)}")
                                },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { onPlaylist(dp.playlist) }) {
                                            Icon(Icons.Rounded.PlayArrow, "Open playlist")
                                        }
                                    }
                                },
                                modifier = Modifier.clickable { onPlaylist(dp.playlist) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog for Delete All
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete all downloads?") },
            text = {
                Text("This will remove all downloaded music (${formatBytes(storageUsed)}) and cached lyrics from device storage. You can re-download them at any time.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteAllDownloads()
                        showDeleteAllDialog = false
                    }
                ) {
                    Text("Delete all", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
