package com.soniva.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.soniva.app.data.repo.LibraryRepository
import com.soniva.app.model.RepeatMode
import com.soniva.app.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SonivaPlayerService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_LOCAL).build()
        mediaSession = MediaSession.Builder(this, player).setId("soniva").build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession
    override fun onDestroy() { mediaSession?.run { player.release(); release() }; mediaSession = null; super.onDestroy() }
}

data class PlaybackState(
    val song: Song? = null,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val sleepUntilEpoch: Long? = null
)

class PlayerController(
    context: Context,
    private val library: LibraryRepository,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var sleepJob: Job? = null
    private val songByMediaId = LinkedHashMap<String, Song>()
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    fun connect() {
        if (controller != null) return
        val future = MediaController.Builder(appContext, SessionToken(appContext, ComponentName(appContext, SonivaPlayerService::class.java))).buildAsync()
        future.addListener({
            try { controller = future.get(); controller?.addListener(listener); startProgress(); publish() } catch (_: Exception) {}
        }, MoreExecutors.directExecutor())
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        scope.launch(Dispatchers.Main) {
            val resolvedSongs = songs.map { song ->
                if (song.localUri != null) song
                else {
                    val downloaded = library.getDownloadedSong(song.id)
                    if (downloaded != null) song.copy(localUri = downloaded.localUri, uri = downloaded.uri, isDownloaded = true)
                    else song
                }
            }
            songByMediaId.clear()
            resolvedSongs.forEach { songByMediaId[it.id] = it }
            val c = controller ?: return@launch
            c.setMediaItems(resolvedSongs.map { it.toItem() }, startIndex.coerceIn(0, resolvedSongs.lastIndex), 0L)
            c.prepare()
            c.play()
            _state.update { it.copy(queue = resolvedSongs, queueIndex = startIndex, song = resolvedSongs.getOrNull(startIndex)) }
            resolvedSongs.getOrNull(startIndex)?.let { s -> scope.launch { library.recordPlay(s) } }
        }
    }

    fun play(song: Song, extras: List<Song> = emptyList()) {
        val queue = if (extras.isNotEmpty()) extras else listOf(song)
        playSongs(queue, queue.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: 0)
    }

    fun playPause() { val c = controller ?: return; if (c.isPlaying) c.pause() else c.play() }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) { controller?.seekTo(ms.coerceAtLeast(0L)) }
    fun toggleShuffle() { val c = controller ?: return; c.shuffleModeEnabled = !c.shuffleModeEnabled; _state.update { it.copy(shuffle = c.shuffleModeEnabled) } }
    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
        _state.update { it.copy(repeatMode = c.repeatMode.toRepeat()) }
    }
    fun setSpeed(speed: Float) { controller?.setPlaybackSpeed(speed.coerceIn(0.5f, 2f)) }

    fun addToQueue(song: Song) {
        scope.launch(Dispatchers.Main) {
            val resolved = if (song.localUri != null) song else {
                val downloaded = library.getDownloadedSong(song.id)
                if (downloaded != null) song.copy(localUri = downloaded.localUri, uri = downloaded.uri, isDownloaded = true) else song
            }
            songByMediaId[resolved.id] = resolved
            controller?.addMediaItem(resolved.toItem())
            _state.update { it.copy(queue = it.queue + resolved) }
        }
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        scope.launch(Dispatchers.Main) {
            val resolved = if (song.localUri != null) song else {
                val downloaded = library.getDownloadedSong(song.id)
                if (downloaded != null) song.copy(localUri = downloaded.localUri, uri = downloaded.uri, isDownloaded = true) else song
            }
            songByMediaId[resolved.id] = resolved
            val nextIndex = (c.currentMediaItemIndex + 1).coerceAtLeast(0)
            c.addMediaItem(nextIndex, resolved.toItem())
            _state.update {
                val q = it.queue.toMutableList()
                q.add((it.queueIndex + 1).coerceAtMost(q.size), resolved)
                it.copy(queue = q)
            }
        }
    }

    fun playQueueIndex(index: Int) { controller?.seekToDefaultPosition(index); controller?.play() }
    fun removeFromQueue(index: Int) {
        if (index !in _state.value.queue.indices) return
        controller?.removeMediaItem(index)
        _state.update { val q = it.queue.toMutableList(); q.removeAt(index); it.copy(queue = q) }
    }
    fun clearQueue() { controller?.clearMediaItems(); songByMediaId.clear(); _state.update { it.copy(queue = emptyList(), song = null, isPlaying = false) } }

    fun setSleepTimer(durationMs: Long?) {
        sleepJob?.cancel()
        if (durationMs == null) { _state.update { it.copy(sleepUntilEpoch = null) }; return }
        _state.update { it.copy(sleepUntilEpoch = System.currentTimeMillis() + durationMs) }
        sleepJob = scope.launch { delay(durationMs); controller?.pause(); _state.update { it.copy(sleepUntilEpoch = null) } }
    }
    fun sleepEndOfSong() { setSleepTimer((_state.value.durationMs - _state.value.positionMs).coerceAtLeast(500L)) }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { publish() }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { publish(); currentSong()?.let { s -> scope.launch { library.recordPlay(s) } } }
    }

    private fun publish() {
        val c = controller ?: return; val song = currentSong()
        _state.update { it.copy(song = song, queueIndex = c.currentMediaItemIndex.coerceAtLeast(0), isPlaying = c.isPlaying, positionMs = c.currentPosition, durationMs = c.duration.takeIf { d -> d > 0 } ?: (song?.durationMs ?: 0L), shuffle = c.shuffleModeEnabled, repeatMode = c.repeatMode.toRepeat()) }
    }

    private fun currentSong(): Song? = songByMediaId[controller?.currentMediaItem?.mediaId]

    private fun startProgress() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) { controller?.let { c -> _state.update { it.copy(positionMs = c.currentPosition, durationMs = c.duration.takeIf { d -> d > 0 } ?: it.durationMs, isPlaying = c.isPlaying) } }; delay(400) }
        }
    }

    private fun Song.toItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(localUri ?: uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri)
                .build()
        )
        .build()

    private fun Int.toRepeat() = when (this) { Player.REPEAT_MODE_ALL -> RepeatMode.ALL; Player.REPEAT_MODE_ONE -> RepeatMode.ONE; else -> RepeatMode.OFF }
}
