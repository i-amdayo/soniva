package com.soniva.app.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soniva.app.SonivaViewModel
import com.soniva.app.model.Album
import com.soniva.app.model.Artist
import com.soniva.app.model.Playlist
import com.soniva.app.model.Song

enum class TabDest { Home, Search, Library, Settings }
sealed class Overlay {
    data object None : Overlay()
    data object NowPlaying : Overlay()
    data object Lyrics : Overlay()
    data object DownloadsPage : Overlay()
    data class AlbumPage(val album: Album) : Overlay()
    data class ArtistPage(val artist: Artist) : Overlay()
    data class PlaylistPage(val playlist: Playlist) : Overlay()
}

@Composable
fun SonivaRoot(vm: SonivaViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val liked by vm.likedSongs.collectAsStateWithLifecycle()
    val likedIds by vm.likedIds.collectAsStateWithLifecycle()
    val recent by vm.recentSongs.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val history by vm.searchHistory.collectAsStateWithLifecycle()
    val hasPerm by vm.hasAudioPermission.collectAsStateWithLifecycle()
    val snack by vm.snackbar.collectAsStateWithLifecycle()
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val lyricsLoading by vm.lyricsLoading.collectAsStateWithLifecycle()
    val isOnline by vm.isOnline.collectAsStateWithLifecycle()
    val downloadedIds by vm.downloadedSongIds.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(TabDest.Home) }
    var overlay by remember { mutableStateOf<Overlay>(Overlay.None) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var createSeed by remember { mutableStateOf<Song?>(null) }
    var newName by remember { mutableStateOf("My playlist") }
    val snackHost = remember { SnackbarHostState() }
    LaunchedEffect(snack) { snack?.let { snackHost.showSnackbar(it) } }
    val permission = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onPermissionUpdated() }
    LaunchedEffect(Unit) { if (!hasPerm) launcher.launch(permission) }

    when (val o = overlay) {
        Overlay.NowPlaying -> {
            NowPlayingScreen(vm, playback, playback.song?.id in likedIds, { overlay = Overlay.None }, { overlay = Overlay.Lyrics })
            return
        }
        Overlay.Lyrics -> {
            LyricsScreen(playback.song, lyrics, lyricsLoading, playback.positionMs, settings.syncedLyrics) { overlay = Overlay.NowPlaying }
            return
        }
        Overlay.DownloadsPage -> {
            DownloadsScreen(vm, { overlay = Overlay.None }, { overlay = Overlay.PlaylistPage(it) }) { actionSong = it }
            return
        }
        is Overlay.AlbumPage -> {
            CollectionScreen(o.album.name, "${o.album.artist}${if (o.album.year > 0) " · ${o.album.year}" else ""}", o.album.artworkUri, vm.songsForAlbum(o.album.name, o.album.artist), vm) { overlay = Overlay.None }
            return
        }
        is Overlay.ArtistPage -> {
            CollectionScreen(o.artist.name, "${o.artist.songCount} songs", o.artist.artworkUri, vm.songsForArtist(o.artist.name), vm) { overlay = Overlay.None }
            return
        }
        is Overlay.PlaylistPage -> {
            val tracks by vm.playlistSongs(o.playlist.id).collectAsState(initial = emptyList())
            CollectionScreen(o.playlist.name, "${tracks.size} songs", null, tracks, vm, playlist = o.playlist, onDelete = { vm.deletePlaylist(o.playlist.id); overlay = Overlay.None }, onRemove = { vm.removeFromPlaylist(o.playlist.id, it.id) }) { overlay = Overlay.None }
            return
        }
        else -> Unit
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackHost) },
        bottomBar = {
            Column {
                MiniPlayer(playback, { vm.playPause() }) { overlay = Overlay.NowPlaying }
                NavigationBar {
                    NavigationBarItem(tab == TabDest.Home, { tab = TabDest.Home }, { Icon(Icons.Rounded.Home, "Home") }, label = { Text("Home") })
                    NavigationBarItem(tab == TabDest.Search, { tab = TabDest.Search }, { Icon(Icons.Rounded.Search, "Search") }, label = { Text("Search") })
                    NavigationBarItem(tab == TabDest.Library, { tab = TabDest.Library }, { Icon(Icons.Rounded.LibraryMusic, "Library") }, label = { Text("Library") })
                    NavigationBarItem(tab == TabDest.Settings, { tab = TabDest.Settings }, { Icon(Icons.Rounded.Settings, "Settings") }, label = { Text("Settings") })
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Offline Status Banner
            OfflineBanner(isOnline = isOnline)

            if (!hasPerm) {
                Text("Soniva needs access to audio files on this phone.", Modifier.padding(16.dp))
                Button({ launcher.launch(permission) }, Modifier.padding(horizontal = 16.dp)) { Text("Allow music access") }
            }
            when (tab) {
                TabDest.Home -> HomeScreen(vm, songs, recent, liked, { overlay = Overlay.AlbumPage(it) }, { overlay = Overlay.ArtistPage(it) })
                TabDest.Search -> SearchScreen(vm, query, results, searching, history, { overlay = Overlay.AlbumPage(it) }, { overlay = Overlay.ArtistPage(it) }) { actionSong = it }
                TabDest.Library -> LibraryScreen(vm, songs, liked, recent, playlists, onDownloads = { overlay = Overlay.DownloadsPage }, onAlbum = { overlay = Overlay.AlbumPage(it) }, onArtist = { overlay = Overlay.ArtistPage(it) }, onPlaylist = { overlay = Overlay.PlaylistPage(it) }, onCreate = { createSeed = null; showCreate = true }) { actionSong = it }
                TabDest.Settings -> SettingsScreen(vm, settings)
            }
        }
    }
    actionSong?.let { song ->
        ModalBottomSheet({ actionSong = null }) {
            Text(song.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            TextButton({ vm.playNext(song); actionSong = null }, Modifier.fillMaxWidth()) { Text("Play next") }
            TextButton({ vm.addToQueue(song); actionSong = null }, Modifier.fillMaxWidth()) { Text("Add to queue") }
            TextButton({ vm.toggleLike(song); actionSong = null }, Modifier.fillMaxWidth()) { Text("Toggle liked") }

            if (song.id in downloadedIds) {
                TextButton({ vm.deleteDownload(song.id); actionSong = null }, Modifier.fillMaxWidth()) {
                    Text("Delete download", color = MaterialTheme.colorScheme.error)
                }
            } else if (song.isDownloadable || song.downloadUrl != null) {
                TextButton({ vm.downloadSong(song); actionSong = null }, Modifier.fillMaxWidth()) {
                    Text("Download for offline")
                }
            }

            TextButton({ createSeed = song; showCreate = true; actionSong = null }, Modifier.fillMaxWidth()) { Text("New playlist with this song") }
            playlists.forEach { p -> TextButton({ vm.addToPlaylist(p.id, song); actionSong = null }, Modifier.fillMaxWidth()) { Text("Add to ${p.name}") } }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (showCreate) {
        AlertDialog(
            { showCreate = false },
            { Text("New playlist") },
            { OutlinedTextField(newName, { newName = it }, singleLine = true) },
            confirmButton = { TextButton({ vm.createPlaylist(newName, createSeed); showCreate = false }) { Text("Create") } },
            dismissButton = { TextButton({ showCreate = false }) { Text("Cancel") } }
        )
    }
}
