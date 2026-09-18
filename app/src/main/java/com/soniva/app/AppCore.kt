package com.soniva.app

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.soniva.app.data.db.SonivaDatabase
import com.soniva.app.data.download.DownloadManager
import com.soniva.app.data.lyrics.LyricsRepository
import com.soniva.app.data.network.NetworkMonitor
import com.soniva.app.data.prefs.SettingsRepository
import com.soniva.app.data.repo.LibraryRepository
import com.soniva.app.data.source.AuthorizedMusicSource
import com.soniva.app.data.source.LocalMusicSource
import com.soniva.app.data.source.SearchBundle
import com.soniva.app.data.source.UnavailableStreamingSource
import com.soniva.app.model.LyricsResult
import com.soniva.app.model.Playlist
import com.soniva.app.model.Song
import com.soniva.app.model.UserSettings
import com.soniva.app.playback.PlaybackState
import com.soniva.app.playback.PlayerController
import com.soniva.app.theme.SonivaTheme
import com.soniva.app.ui.SonivaRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppContainer(context: android.content.Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val db = Room.databaseBuilder(context.applicationContext, SonivaDatabase::class.java, "soniva.db").fallbackToDestructiveMigration().build()
    val network = NetworkMonitor(context.applicationContext, appScope)
    val localSource = LocalMusicSource(context)
    val authorizedSource = AuthorizedMusicSource()
    val streamingSource = UnavailableStreamingSource()
    val settings = SettingsRepository(context)
    val lyrics = LyricsRepository(db.lyricsDao(), network)
    val downloadManager = DownloadManager(context.applicationContext, db.downloadDao(), lyrics, network, settings, appScope)
    val library = LibraryRepository(db, localSource, authorizedSource)
    val player = PlayerController(context, library, appScope)
}

class SonivaApplication : Application() {
    lateinit var container: AppContainer; private set
    override fun onCreate() { super.onCreate(); container = AppContainer(this) }
}

class SonivaViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as SonivaApplication).container
    val settings: StateFlow<UserSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    val songs: StateFlow<List<Song>> = c.library.songs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val likedIds: StateFlow<Set<String>> = c.library.likedIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val likedSongs = c.library.likedSongs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentSongs = c.library.recentSongs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists = c.library.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val searchHistory = c.library.searchHistory.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playback: StateFlow<PlaybackState> = c.player.state
    val hasAudioPermission = MutableStateFlow(checkPermission())
    val searchQuery = MutableStateFlow("")
    val searchResults = MutableStateFlow(SearchBundle(emptyList(), emptyList(), emptyList()))
    val searching = MutableStateFlow(false)
    val lyrics = MutableStateFlow<LyricsResult?>(null)
    val lyricsLoading = MutableStateFlow(false)
    val snackbar = MutableStateFlow<String?>(null)

    // Offline downloads state
    val isOnline: StateFlow<Boolean> = c.network.isOnline
    val isWifi: StateFlow<Boolean> = c.network.isWifi
    val downloadProgress = c.downloadManager.progressMap
    val downloadedSongs: StateFlow<List<Song>> = c.library.downloadedSongs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val downloadedSongIds: StateFlow<Set<String>> = c.library.downloadedIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val downloadedPlaylists = c.downloadManager.downloadedPlaylists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val totalStorageUsed: StateFlow<Long> = c.downloadManager.totalStorageUsed.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val totalDownloadCount: StateFlow<Int> = c.downloadManager.totalDownloadCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private var searchJob: Job? = null
    private var lastLyricsId: String? = null

    init {
        c.player.connect()
        viewModelScope.launch { playback.collect { state -> val song = state.song ?: return@collect; if (song.id != lastLyricsId) loadLyrics(song) } }
        viewModelScope.launch { settings.collect { c.player.setSpeed(it.playbackSpeed) } }
        if (hasAudioPermission.value) refreshLibrary()
    }

    fun onPermissionUpdated() { hasAudioPermission.value = checkPermission(); if (hasAudioPermission.value) refreshLibrary() }
    fun refreshLibrary() { viewModelScope.launch { runCatching { c.library.scanLocal() } } }
    fun play(song: Song, queue: List<Song> = songs.value) { val q = if (queue.any { it.id == song.id }) queue else listOf(song) + queue; c.player.play(song, q) }
    fun playAll(list: List<Song>, shuffle: Boolean = false) { if (list.isEmpty()) return; c.player.playSongs(if (shuffle) list.shuffled() else list, 0); if (shuffle && !c.player.state.value.shuffle) c.player.toggleShuffle() }
    fun playPause() = c.player.playPause(); fun next() = c.player.next(); fun previous() = c.player.previous(); fun seek(ms: Long) = c.player.seekTo(ms)
    fun toggleShuffle() = c.player.toggleShuffle(); fun cycleRepeat() = c.player.cycleRepeat()
    fun addToQueue(song: Song) { c.player.addToQueue(song); snack("Added to queue") }
    fun playNext(song: Song) { c.player.playNext(song); snack("Playing next") }
    fun playQueueIndex(i: Int) = c.player.playQueueIndex(i); fun removeQueue(i: Int) = c.player.removeFromQueue(i); fun clearQueue() = c.player.clearQueue()
    fun toggleLike(song: Song) { viewModelScope.launch { snack(if (c.library.toggleLike(song)) "Added to Liked songs" else "Removed from Liked songs") } }
    fun onSearch(query: String) {
        searchQuery.value = query; searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(180)
            if (query.isBlank()) { searchResults.value = SearchBundle(emptyList(), emptyList(), emptyList()); searching.value = false; return@launch }
            searching.value = true; searchResults.value = c.library.search(query); searching.value = false
        }
    }
    fun clearSearchHistory() = viewModelScope.launch { c.library.clearSearchHistory() }
    fun createPlaylist(name: String, song: Song? = null) { viewModelScope.launch { val id = c.library.createPlaylist(name); if (song != null) c.library.addToPlaylist(id, song); snack("Playlist created") } }
    fun addToPlaylist(playlistId: Long, song: Song) { viewModelScope.launch { c.library.addToPlaylist(playlistId, song); snack("Added to playlist") } }
    fun deletePlaylist(id: Long) = viewModelScope.launch { c.library.deletePlaylist(id) }
    fun removeFromPlaylist(id: Long, songId: String) = viewModelScope.launch { c.library.removeFromPlaylist(id, songId) }
    fun playlistSongs(id: Long) = c.library.observePlaylistSongs(id)
    fun updateSettings(transform: (UserSettings) -> UserSettings) { viewModelScope.launch { c.settings.update(transform) } }
    fun setSleepMinutes(minutes: Int?) { if (minutes == null) c.player.setSleepTimer(null) else c.player.setSleepTimer(minutes * 60_000L); snack(if (minutes == null) "Sleep timer off" else "Sleep timer set") }
    fun sleepEndOfSong() { c.player.sleepEndOfSong(); snack("Pauses at end of song") }
    fun albums() = c.library.albums(songs.value); fun artists() = c.library.artists(songs.value)
    fun songsForAlbum(album: String, artist: String) = songs.value.filter { it.album == album && it.artist == artist }.sortedBy { it.trackNumber }
    fun songsForArtist(artist: String) = songs.value.filter { it.artist == artist }

    // Offline Downloads actions
    fun downloadSong(song: Song) {
        c.downloadManager.downloadSong(song) { _, msg ->
            if (msg != null) snack(msg)
        }
    }
    fun cancelDownload(songId: String) {
        c.downloadManager.cancelDownload(songId)
        snack("Download cancelled")
    }
    fun deleteDownload(songId: String) {
        c.downloadManager.deleteDownload(songId)
        snack("Download removed")
    }
    fun deleteAllDownloads() {
        c.downloadManager.deleteAllDownloads()
        snack("All downloads deleted")
    }
    fun downloadPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val s = playlistSongs(playlist.id).first()
            c.downloadManager.downloadPlaylist(playlist, s) { started, total ->
                snack("Downloading $started of $total songs")
            }
        }
    }

    private fun loadLyrics(song: Song) { lastLyricsId = song.id; lyrics.value = null; viewModelScope.launch { lyricsLoading.value = true; lyrics.value = runCatching { c.lyrics.fetch(song) }.getOrNull(); lyricsLoading.value = false } }
    fun snack(msg: String) { snackbar.value = msg; viewModelScope.launch { delay(1800); if (snackbar.value == msg) snackbar.value = null } }
    private fun checkPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(getApplication(), perm) == PackageManager.PERMISSION_GRANTED
    }
}

class MainActivity : ComponentActivity() {
    private val vm by viewModels<SonivaViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { val settings by vm.settings.collectAsStateWithLifecycle(); SonivaTheme(settings.themeMode, settings.dynamicColor) { SonivaRoot(vm) } }
    }
    override fun onResume() { super.onResume(); vm.onPermissionUpdated() }
}
