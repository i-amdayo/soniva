package com.soniva.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soniva.app.BuildConfig
import com.soniva.app.SonivaViewModel
import com.soniva.app.data.db.SearchHistoryEntity
import com.soniva.app.data.source.SearchBundle
import com.soniva.app.model.Album
import com.soniva.app.model.Artist
import com.soniva.app.model.ArtworkQuality
import com.soniva.app.model.DownloadStatus
import com.soniva.app.model.LyricsResult
import com.soniva.app.model.Playlist
import com.soniva.app.model.RepeatMode
import com.soniva.app.model.Song
import com.soniva.app.model.StreamQuality
import com.soniva.app.model.ThemeMode
import com.soniva.app.model.UserSettings
import com.soniva.app.playback.PlaybackState
import com.soniva.app.util.formatBytes
import com.soniva.app.util.formatTime

@Composable
fun HomeScreen(
    vm: SonivaViewModel,
    songs: List<Song>,
    recent: List<Song>,
    liked: List<Song>,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit
) {
    val albums = remember(songs) { vm.albums() }
    val artists = remember(songs) { vm.artists() }
    val downloadedIds by vm.downloadedSongIds.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()

    if (songs.isEmpty()) {
        EmptyState("Your library is quiet", "Grant music access or explore open music on this phone.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text("Good listening", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Soniva", style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ vm.playAll(songs, true) }) {
                        Icon(Icons.Rounded.Shuffle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Shuffle all")
                    }
                    FilledTonalButton({ if (liked.isNotEmpty()) vm.playAll(liked) }) {
                        Icon(Icons.Rounded.Favorite, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Liked")
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { Text("Recently played", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recent.take(16), { it.id }) { Cover(it.title, it.artist, it.artworkUri) { vm.play(it, songs) } }
                }
            }
        }
        item { Text("Quick picks", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp)) }
        items(songs.take(8), { "p-${it.id}" }) { song ->
            SongRow(
                song = song,
                onClick = { vm.play(song, songs) },
                downloadProgress = downloadProgress[song.id],
                isDownloaded = song.id in downloadedIds,
                onDownload = { vm.downloadSong(song) },
                onCancelDownload = { vm.cancelDownload(song.id) }
            )
        }
        if (albums.isNotEmpty()) {
            item { Text("Albums", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(albums.take(16), { it.name + it.artist }) { Cover(it.name, it.artist, it.artworkUri) { onAlbum(it) } }
                }
            }
        }
        if (artists.isNotEmpty()) {
            item { Text("Artists", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(artists.take(16), { it.name }) { Cover(it.name, "${it.songCount} songs", it.artworkUri) { onArtist(it) } }
                }
            }
        }
        item {
            Column(
                Modifier
                    .padding(16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp)
            ) {
                Text("Music & Offline Downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Soniva plays offline-downloaded songs and music on this device. Content from authorized sources can be saved offline. YouTube does not allow direct downloading and is not scraped.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun Cover(title: String, subtitle: String, art: Uri?, onClick: () -> Unit) {
    Column(Modifier.width(132.dp).clickable(onClick = onClick)) {
        Artwork(art, size = 132.dp, corner = 16.dp)
        Spacer(Modifier.height(8.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SearchScreen(
    vm: SonivaViewModel,
    query: String,
    results: SearchBundle,
    searching: Boolean,
    history: List<SearchHistoryEntity>,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit,
    onMore: (Song) -> Unit
) {
    val downloadedIds by vm.downloadedSongIds.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            query,
            vm::onSearch,
            Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Songs, artists, albums") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton({ vm.onSearch("") }) { Icon(Icons.Rounded.Close, "Clear") } },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        when {
            query.isBlank() -> LazyColumn {
                item { ListItem({ Text("Recent searches") }, trailingContent = { TextButton({ vm.clearSearchHistory() }) { Text("Clear") } }) }
                items(history, { it.query }) { ListItem({ Text(it.query) }, leadingContent = { Icon(Icons.Rounded.History, null) }, modifier = Modifier.clickable { vm.onSearch(it.query) }) }
            }
            searching -> CircularProgressIndicator(Modifier.padding(24.dp))
            results.songs.isEmpty() && results.albums.isEmpty() && results.artists.isEmpty() -> EmptyState("No results", "Nothing matching that query.")
            else -> LazyColumn {
                if (results.songs.isNotEmpty()) {
                    item { Text("Songs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp)) }
                    items(results.songs.take(25), { it.id }) { song ->
                        SongRow(
                            song = song,
                            onClick = { vm.play(song) },
                            downloadProgress = downloadProgress[song.id],
                            isDownloaded = song.id in downloadedIds,
                            onDownload = { vm.downloadSong(song) },
                            onCancelDownload = { vm.cancelDownload(song.id) },
                            onMore = { onMore(song) }
                        )
                    }
                }
                if (results.artists.isNotEmpty()) {
                    item { Text("Artists", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp)) }
                    items(results.artists, { it.name }) { ListItem({ Text(it.name) }, supportingContent = { Text("${it.songCount} songs") }, modifier = Modifier.clickable { onArtist(it) }) }
                }
                if (results.albums.isNotEmpty()) {
                    item { Text("Albums", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp)) }
                    items(results.albums, { it.name + it.artist }) { ListItem({ Text(it.name) }, supportingContent = { Text(it.artist) }, modifier = Modifier.clickable { onAlbum(it) }) }
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(
    vm: SonivaViewModel,
    songs: List<Song>,
    liked: List<Song>,
    recent: List<Song>,
    playlists: List<Playlist>,
    onDownloads: () -> Unit,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onCreate: () -> Unit,
    onMore: (Song) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val labels = listOf("Songs", "Liked", "Playlists", "Albums", "Artists", "Recent")
    val downloadedSongs by vm.downloadedSongs.collectAsStateWithLifecycle()
    val downloadedIds by vm.downloadedSongIds.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()

    Column {
        // Dedicated Offline Downloads Section
        ListItem(
            headlineContent = {
                Text("Offline Downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            },
            supportingContent = {
                Text("${downloadedSongs.size} tracks downloaded · Play without internet")
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = "Offline Downloads",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            },
            trailingContent = {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Open Downloads")
            },
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable(onClick = onDownloads)
        )

        ScrollableTabRow(tab, edgePadding = 12.dp) {
            labels.forEachIndexed { i, l ->
                Tab(tab == i, { tab = i }, text = { Text(l) })
            }
        }

        when (tab) {
            0 -> if (songs.isEmpty()) EmptyState("No songs", "Add audio files or explore available songs.")
            else LazyColumn {
                items(songs, { it.id }) { song ->
                    SongRow(
                        song = song,
                        onClick = { vm.play(song, songs) },
                        downloadProgress = downloadProgress[song.id],
                        isDownloaded = song.id in downloadedIds,
                        onDownload = { vm.downloadSong(song) },
                        onCancelDownload = { vm.cancelDownload(song.id) },
                        onMore = { onMore(song) }
                    )
                }
            }
            1 -> if (liked.isEmpty()) EmptyState("No liked songs", "Tap the heart on a track you love.")
            else LazyColumn {
                items(liked, { it.id }) { song ->
                    SongRow(
                        song = song,
                        onClick = { vm.play(song, liked) },
                        downloadProgress = downloadProgress[song.id],
                        isDownloaded = song.id in downloadedIds,
                        onDownload = { vm.downloadSong(song) },
                        onCancelDownload = { vm.cancelDownload(song.id) },
                        onMore = { onMore(song) }
                    )
                }
            }
            2 -> LazyColumn {
                item {
                    ListItem(
                        { Text("New playlist") },
                        leadingContent = { IconButton(onCreate) { Icon(Icons.Rounded.Add, "Create") } },
                        modifier = Modifier.clickable(onClick = onCreate)
                    )
                }
                items(playlists, { it.id }) {
                    ListItem({ Text(it.name) }, modifier = Modifier.clickable { onPlaylist(it) })
                }
            }
            3 -> LazyColumn {
                items(vm.albums(), { it.name + it.artist }) {
                    ListItem({ Text(it.name) }, supportingContent = { Text("${it.artist} · ${it.songCount}") }, modifier = Modifier.clickable { onAlbum(it) })
                }
            }
            4 -> LazyColumn {
                items(vm.artists(), { it.name }) {
                    ListItem({ Text(it.name) }, supportingContent = { Text("${it.songCount} songs") }, modifier = Modifier.clickable { onArtist(it) })
                }
            }
            else -> if (recent.isEmpty()) EmptyState("Nothing played yet", "Start a song and it will appear here.")
            else LazyColumn {
                items(recent, { it.id }) { song ->
                    SongRow(
                        song = song,
                        onClick = { vm.play(song, recent) },
                        downloadProgress = downloadProgress[song.id],
                        isDownloaded = song.id in downloadedIds,
                        onDownload = { vm.downloadSong(song) },
                        onCancelDownload = { vm.cancelDownload(song.id) },
                        onMore = { onMore(song) }
                    )
                }
            }
        }
    }
}

@Composable
fun CollectionScreen(
    title: String,
    subtitle: String,
    art: Uri?,
    tracks: List<Song>,
    vm: SonivaViewModel,
    playlist: Playlist? = null,
    onDelete: (() -> Unit)? = null,
    onRemove: ((Song) -> Unit)? = null,
    onBack: () -> Unit
) {
    val downloadedIds by vm.downloadedSongIds.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row {
            IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Spacer(Modifier.weight(1f))
            if (onDelete != null) IconButton(onDelete) { Icon(Icons.Rounded.Delete, "Delete") }
        }
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Artwork(art, size = 180.dp, corner = 20.dp)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton({ vm.playAll(tracks) }) { Text("Play") }
                FilledTonalButton({ vm.playAll(tracks, true) }) { Text("Shuffle") }
                FilledTonalButton({
                    if (playlist != null) {
                        vm.downloadPlaylist(playlist)
                    } else {
                        tracks.forEach { vm.downloadSong(it) }
                    }
                }) {
                    Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Download")
                }
            }
        }
        LazyColumn {
            items(tracks, { it.id }) { song ->
                SongRow(
                    song = song,
                    onClick = { vm.play(song, tracks) },
                    downloadProgress = downloadProgress[song.id],
                    isDownloaded = song.id in downloadedIds,
                    onDownload = { vm.downloadSong(song) },
                    onCancelDownload = { vm.cancelDownload(song.id) },
                    onMore = onRemove?.let { r -> { r(song) } }
                )
            }
        }
    }
}

@Composable
fun MiniPlayer(state: PlaybackState, onToggle: () -> Unit, onExpand: () -> Unit) {
    val song = state.song ?: return
    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onExpand)
    ) {
        LinearProgressIndicator(progress, Modifier.fillMaxWidth())
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(song.artworkUri, size = 44.dp, corner = 8.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onToggle) { Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play pause") }
        }
    }
}

@Composable
fun NowPlayingScreen(
    vm: SonivaViewModel,
    state: PlaybackState,
    liked: Boolean,
    onClose: () -> Unit,
    onLyrics: () -> Unit
) {
    val song = state.song
    var sleep by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf(false) }
    val downloadedIds by vm.downloadedSongIds.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(Icons.Rounded.ExpandMore, "Close") }
            Text("Now playing", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton({ sleep = true }) { Icon(Icons.Rounded.Timer, "Sleep timer") }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Artwork(song?.artworkUri, size = 280.dp, corner = 28.dp) }
        Spacer(Modifier.height(20.dp))
        Text(song?.title ?: "Nothing playing", style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(song?.let { "${it.artist} · ${it.album}" } ?: "Choose a song from your library", color = MaterialTheme.colorScheme.onSurfaceVariant)
        val duration = state.durationMs.coerceAtLeast(1L)
        Slider(state.positionMs.coerceIn(0L, duration).toFloat(), { vm.seek(it.toLong()) }, valueRange = 0f..duration.toFloat())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatTime(state.positionMs)); Text(formatTime(state.durationMs)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton({ vm.toggleShuffle() }) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
            IconButton({ vm.previous() }) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(36.dp)) }
            Box(Modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable { vm.playPause() }, contentAlignment = Alignment.Center) {
                Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play pause", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
            }
            IconButton({ vm.next() }) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(36.dp)) }
            IconButton({ vm.cycleRepeat() }) { Icon(if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat", tint = if (state.repeatMode == RepeatMode.OFF) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton({ song?.let { vm.toggleLike(it) } }) { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite") }
            song?.let { s ->
                val status = when {
                    downloadProgress[s.id] != null -> downloadProgress[s.id]!!.status
                    s.id in downloadedIds || s.isDownloaded -> DownloadStatus.DOWNLOADED
                    else -> DownloadStatus.NOT_DOWNLOADED
                }
                DownloadIconButton(
                    status = status,
                    progress = downloadProgress[s.id]?.progress ?: 0f,
                    onDownload = { vm.downloadSong(s) },
                    onCancel = { vm.cancelDownload(s.id) }
                )
            }
            IconButton(onLyrics) { Icon(Icons.Rounded.Notes, "Lyrics") }
            IconButton({ queue = true }) { Icon(Icons.Rounded.QueueMusic, "Queue") }
        }
        state.sleepUntilEpoch?.let { Text("Sleep timer · ${formatTime((it - System.currentTimeMillis()).coerceAtLeast(0))}", color = MaterialTheme.colorScheme.primary) }
    }
    if (sleep) ModalBottomSheet({ sleep = false }) {
        Text("Sleep timer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        listOf(15, 30, 45, 60).forEach { m -> TextButton({ vm.setSleepMinutes(m); sleep = false }, Modifier.fillMaxWidth()) { Text("$m minutes") } }
        TextButton({ vm.sleepEndOfSong(); sleep = false }, Modifier.fillMaxWidth()) { Text("End of song") }
        TextButton({ vm.setSleepMinutes(null); sleep = false }, Modifier.fillMaxWidth()) { Text("Turn off") }
        Spacer(Modifier.height(24.dp))
    }
    if (queue) ModalBottomSheet({ queue = false }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Queue", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton({ vm.clearQueue(); queue = false }) { Text("Clear") }
        }
        LazyColumn {
            itemsIndexed(state.queue, { i, s -> "${s.id}-$i" }) { index, item ->
                Row(Modifier.fillMaxWidth().clickable { vm.playQueueIndex(index) }.padding(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = if (index == state.queueIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text(item.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton({ vm.removeQueue(index) }) { Text("Remove") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun LyricsScreen(song: Song?, lyrics: LyricsResult?, loading: Boolean, positionMs: Long, synced: Boolean, onClose: () -> Unit) {
    val lines = lyrics?.synced.orEmpty()
    val active = if (synced && lines.isNotEmpty()) lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0) else -1
    val listState = rememberLazyListState()
    LaunchedEffect(active) { if (active >= 0) runCatching { listState.animateScrollToItem(active) } }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        IconButton(onClose) { Icon(Icons.Rounded.ExpandMore, "Close") }
        Text(song?.title ?: "Lyrics", style = MaterialTheme.typography.headlineMedium)
        Text(song?.artist ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            lyrics == null || !lyrics.hasAny -> Text("Lyrics unavailable.")
            lyrics.hasSynced && synced -> LazyColumn(state = listState) {
                itemsIndexed(lines) { index, line ->
                    Text(line.text, Modifier.fillMaxWidth().padding(vertical = 8.dp), style = if (index == active) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge, color = if (index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> Text(lyrics.plain ?: "Lyrics unavailable.")
        }
    }
}

@Composable
fun SettingsScreen(vm: SonivaViewModel, settings: UserSettings) {
    val storageUsed by vm.totalStorageUsed.collectAsStateWithLifecycle()
    val downloadCount by vm.totalDownloadCount.collectAsStateWithLifecycle()

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
        Text("Appearance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        Row(Modifier.padding(horizontal = 8.dp)) {
            ThemeMode.entries.forEach { mode ->
                TextButton({ vm.updateSettings { it.copy(themeMode = mode) } }) {
                    Text(mode.name.lowercase().replaceFirstChar(Char::titlecase), color = if (settings.themeMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        ListItem({ Text("Dynamic colors") }, trailingContent = { Switch(settings.dynamicColor, { v -> vm.updateSettings { it.copy(dynamicColor = v) } }) })
        Text("Playback", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        ListItem({ Text("Speed ${"%.1f".format(settings.playbackSpeed)}x") }, supportingContent = { Slider(settings.playbackSpeed, { v -> vm.updateSettings { it.copy(playbackSpeed = v) } }, valueRange = 0.5f..2f) })
        ListItem({ Text("Gapless playback") }, trailingContent = { Switch(settings.gapless, { v -> vm.updateSettings { it.copy(gapless = v) } }) })
        ListItem({ Text("Data saver") }, trailingContent = { Switch(settings.dataSaver, { v -> vm.updateSettings { it.copy(dataSaver = v) } }) })

        Text("Downloads & Storage", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        ListItem(
            headlineContent = { Text("Downloaded tracks") },
            supportingContent = { Text("$downloadCount tracks · ${formatBytes(storageUsed)} used") }
        )
        ListItem(
            headlineContent = { Text("Wi-Fi only downloads") },
            supportingContent = { Text("Only download tracks when connected to Wi-Fi") },
            trailingContent = { Switch(settings.wifiOnlyDownloads, { v -> vm.updateSettings { it.copy(wifiOnlyDownloads = v) } }) }
        )
        ListItem(
            headlineContent = { Text("Synchronized lyrics") },
            supportingContent = { Text("Cached locally for offline playback") },
            trailingContent = { Switch(settings.syncedLyrics, { v -> vm.updateSettings { it.copy(syncedLyrics = v) } }) }
        )
        Row(Modifier.padding(8.dp)) { StreamQuality.entries.forEach { q -> TextButton({ vm.updateSettings { it.copy(streamQuality = q) } }) { Text(q.name) } } }
        Row(Modifier.padding(8.dp)) { ArtworkQuality.entries.forEach { q -> TextButton({ vm.updateSettings { it.copy(artworkQuality = q) } }) { Text("Art ${q.name}") } } }
        ListItem({ Text("Soniva") }, supportingContent = { Text("Version ${BuildConfig.VERSION_NAME}") })
        ListItem({ Text("Music source") }, supportingContent = { Text("Local files & authorized open audio. No unofficial YouTube extractor is bundled.") })
        ListItem({ Text("Legal") }, supportingContent = { Text("Play and store audio you have the right to keep. Offline downloads respect terms of service.") })
    }
}
