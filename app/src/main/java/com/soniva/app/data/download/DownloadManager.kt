package com.soniva.app.data.download

import android.content.Context
import android.net.Uri
import com.soniva.app.data.db.DownloadDao
import com.soniva.app.data.db.DownloadedPlaylistEntity
import com.soniva.app.data.db.DownloadedSongEntity
import com.soniva.app.data.lyrics.LyricsRepository
import com.soniva.app.data.network.NetworkMonitor
import com.soniva.app.data.prefs.SettingsRepository
import com.soniva.app.model.DownloadProgress
import com.soniva.app.model.DownloadStatus
import com.soniva.app.model.DownloadedPlaylist
import com.soniva.app.model.DownloadedSong
import com.soniva.app.model.Playlist
import com.soniva.app.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class DownloadManager(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val lyricsRepository: LyricsRepository,
    private val networkMonitor: NetworkMonitor,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope
) {
    private val downloadsDir = File(context.filesDir, "soniva_downloads").apply { if (!exists()) mkdirs() }
    private val artDir = File(context.filesDir, "soniva_art").apply { if (!exists()) mkdirs() }

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val _progressMap = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progressMap: StateFlow<Map<String, DownloadProgress>> = _progressMap.asStateFlow()

    // Observable downloaded songs from Room
    val downloadedSongs: Flow<List<Song>> = downloadDao.observeDownloads().map { list ->
        list.map { entity -> entity.toSong() }
    }

    val downloadedSongIds: Flow<Set<String>> = downloadDao.observeDownloadedSongIds().map { it.toSet() }

    val totalStorageUsed: Flow<Long> = downloadDao.observeTotalStorageUsed().map { it ?: 0L }
    val totalDownloadCount: Flow<Int> = downloadDao.observeDownloadCount()

    val downloadedPlaylists: Flow<List<DownloadedPlaylist>> = downloadDao.observeDownloadedPlaylists().map { list ->
        list.map { entity ->
            DownloadedPlaylist(
                playlist = Playlist(entity.playlistId, entity.name, entity.downloadedAt, entity.songCount),
                songCount = entity.songCount,
                totalSizeBytes = entity.totalSizeBytes,
                isFullyDownloaded = true
            )
        }
    }

    init {
        // Clean up orphaned .tmp files on startup
        scope.launch(Dispatchers.IO) {
            cleanOrphanedFiles()
        }
    }

    fun isDownloaded(songId: String): Boolean {
        return _progressMap.value[songId]?.status == DownloadStatus.DOWNLOADED
    }

    fun getDownloadStatus(songId: String): DownloadStatus {
        return _progressMap.value[songId]?.status ?: DownloadStatus.NOT_DOWNLOADED
    }

    fun downloadSong(song: Song, playlistId: Long? = null, onResult: ((Boolean, String?) -> Unit)? = null) {
        if (!song.isDownloadable && song.downloadUrl == null) {
            onResult?.invoke(false, "Downloading is not supported or permitted for this source.")
            return
        }

        // Check active download
        if (activeJobs.containsKey(song.id)) {
            onResult?.invoke(false, "Track is already downloading.")
            return
        }

        scope.launch {
            val isAlreadyDownloaded = downloadDao.getDownload(song.id) != null
            if (isAlreadyDownloaded) {
                _progressMap.update {
                    it + (song.id to DownloadProgress(song.id, 1f, 0L, 0L, DownloadStatus.DOWNLOADED))
                }
                onResult?.invoke(true, "Song is already downloaded.")
                return@launch
            }

            val settings = settingsRepository.settings.first()
            if (settings.wifiOnlyDownloads && !networkMonitor.isWifi.value) {
                onResult?.invoke(false, "Wi-Fi is required to download (Wi-Fi only enabled in Settings).")
                return@launch
            }

            if (!networkMonitor.isOnline.value && song.downloadUrl?.startsWith("http") == true) {
                onResult?.invoke(false, "Cannot download while offline.")
                return@launch
            }

            // Enqueue download
            _progressMap.update {
                it + (song.id to DownloadProgress(song.id, 0f, 0L, 0L, DownloadStatus.QUEUED))
            }
            onResult?.invoke(true, "Download started")

            val job = launch(Dispatchers.IO) {
                executeDownload(song, playlistId)
            }
            activeJobs[song.id] = job
            job.invokeOnCompletion {
                activeJobs.remove(song.id)
            }
        }
    }

    fun downloadPlaylist(playlist: Playlist, songs: List<Song>, onComplete: (Int, Int) -> Unit) {
        scope.launch {
            val downloadable = songs.filter { it.isDownloadable || it.downloadUrl != null }
            if (downloadable.isEmpty()) {
                onComplete(0, 0)
                return@launch
            }

            var started = 0
            for (song in downloadable) {
                val isDownloaded = downloadDao.getDownload(song.id) != null
                if (!isDownloaded && !activeJobs.containsKey(song.id)) {
                    downloadSong(song, playlistId = playlist.id)
                    started++
                }
            }

            // Save playlist entry
            downloadDao.insertDownloadedPlaylist(
                DownloadedPlaylistEntity(
                    playlistId = playlist.id,
                    name = playlist.name,
                    songCount = downloadable.size,
                    totalSizeBytes = downloadable.sumOf { it.fileSizeBytes },
                    downloadedAt = System.currentTimeMillis()
                )
            )
            onComplete(started, downloadable.size)
        }
    }

    fun cancelDownload(songId: String) {
        val job = activeJobs.remove(songId)
        job?.cancel()
        _progressMap.update { it - songId }

        scope.launch(Dispatchers.IO) {
            val sanitized = sanitizeFileName(songId)
            val tempFile = File(downloadsDir, "$sanitized.tmp")
            if (tempFile.exists()) tempFile.delete()
        }
    }

    fun deleteDownload(songId: String) {
        cancelDownload(songId)
        scope.launch(Dispatchers.IO) {
            val entity = downloadDao.getDownload(songId)
            if (entity != null) {
                runCatching { File(entity.localFilePath).delete() }
                if (entity.localArtPath != null) {
                    runCatching { File(entity.localArtPath).delete() }
                }
                downloadDao.deleteDownload(songId)
            }
            _progressMap.update { it - songId }
        }
    }

    fun deleteAllDownloads() {
        // Cancel all running downloads
        activeJobs.forEach { (_, job) -> job.cancel() }
        activeJobs.clear()
        _progressMap.value = emptyMap()

        scope.launch(Dispatchers.IO) {
            runCatching {
                downloadsDir.listFiles()?.forEach { it.delete() }
                artDir.listFiles()?.forEach { it.delete() }
            }
            downloadDao.deleteAllDownloads()
            downloadDao.deleteAllDownloadedPlaylists()
        }
    }

    private suspend fun executeDownload(song: Song, playlistId: Long?) {
        val sanitized = sanitizeFileName(song.id)
        val tempFile = File(downloadsDir, "$sanitized.tmp")
        val targetFile = File(downloadsDir, "$sanitized.mp3")

        _progressMap.update {
            it + (song.id to DownloadProgress(song.id, 0f, 0L, 0L, DownloadStatus.DOWNLOADING))
        }

        try {
            val urlString = song.downloadUrl ?: song.uri.toString()
            val isHttp = urlString.startsWith("http://") || urlString.startsWith("https://")

            val inputStream: InputStream
            val totalBytes: Long

            if (isHttp) {
                val url = URL(urlString)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "Soniva/1.0.0 (personal)")
                    connect()
                }

                if (conn.responseCode !in 200..299) {
                    throw Exception("HTTP Error: ${conn.responseCode} ${conn.responseMessage}")
                }

                val length = conn.contentLengthLong
                totalBytes = if (length > 0) length else (song.durationMs * 16) // estimate ~128kbps if unknown
                inputStream = conn.inputStream
            } else {
                val uri = song.uri
                val crStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Cannot open local audio stream")
                totalBytes = crStream.available().toLong().coerceAtLeast(1L)
                inputStream = crStream
            }

            var bytesCopied = 0L
            var lastUpdateMs = System.currentTimeMillis()

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateMs > 120) {
                            val progressFraction = (bytesCopied.toFloat() / totalBytes).coerceIn(0f, 0.99f)
                            _progressMap.update {
                                it + (song.id to DownloadProgress(
                                    songId = song.id,
                                    progress = progressFraction,
                                    bytesDownloaded = bytesCopied,
                                    totalBytes = totalBytes,
                                    status = DownloadStatus.DOWNLOADING
                                ))
                            }
                            lastUpdateMs = now
                        }
                    }
                    output.flush()
                }
            }

            // Rename tempFile to targetFile atomically
            if (targetFile.exists()) targetFile.delete()
            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            val finalSize = targetFile.length()

            // 1. Download & cache artwork locally
            val localArtPath = cacheArtworkLocally(song)

            // 2. Fetch & cache lyrics locally
            var lyricsCached = false
            try {
                val lyrics = lyricsRepository.fetch(song)
                if (lyrics.hasAny) {
                    lyricsRepository.cacheLyrics(song.id, lyrics)
                    lyricsCached = true
                }
            } catch (_: Exception) {}

            // 3. Save to Room database
            val existingPlaylists = playlistId?.toString() ?: ""
            val entity = DownloadedSongEntity(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs,
                originalUri = song.uri.toString(),
                localFilePath = targetFile.absolutePath,
                localArtPath = localArtPath,
                fileSizeBytes = finalSize,
                downloadedAt = System.currentTimeMillis(),
                hasLyrics = lyricsCached,
                associatedPlaylistIds = existingPlaylists
            )
            downloadDao.insertDownload(entity)

            // 4. Update progress to DOWNLOADED
            _progressMap.update {
                it + (song.id to DownloadProgress(
                    songId = song.id,
                    progress = 1f,
                    bytesDownloaded = finalSize,
                    totalBytes = finalSize,
                    status = DownloadStatus.DOWNLOADED
                ))
            }

        } catch (e: CancellationException) {
            runCatching { tempFile.delete() }
            _progressMap.update { it - song.id }
            throw e
        } catch (e: Exception) {
            runCatching { tempFile.delete() }
            _progressMap.update {
                it + (song.id to DownloadProgress(
                    songId = song.id,
                    progress = 0f,
                    bytesDownloaded = 0L,
                    totalBytes = 0L,
                    status = DownloadStatus.FAILED,
                    error = e.localizedMessage ?: "Download failed"
                ))
            }
        }
    }

    private fun cacheArtworkLocally(song: Song): String? {
        val artUri = song.artworkUri ?: return null
        val artStr = artUri.toString()
        val artTargetFile = File(artDir, "${sanitizeFileName(song.id)}_art.jpg")

        return try {
            if (artStr.startsWith("http://") || artStr.startsWith("https://")) {
                val conn = (URL(artStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                if (conn.responseCode in 200..299) {
                    conn.inputStream.use { input ->
                        FileOutputStream(artTargetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    artTargetFile.absolutePath
                } else null
            } else if (artStr.startsWith("content://") || artStr.startsWith("file://")) {
                context.contentResolver.openInputStream(artUri)?.use { input ->
                    FileOutputStream(artTargetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                artTargetFile.absolutePath
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanOrphanedFiles() {
        runCatching {
            downloadsDir.listFiles { _, name -> name.endsWith(".tmp") }?.forEach {
                it.delete()
            }
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }

    private fun DownloadedSongEntity.toSong(): Song {
        val fileUri = Uri.fromFile(File(localFilePath))
        val artUri = localArtPath?.let { Uri.fromFile(File(it)) } ?: Uri.parse(originalUri)
        return Song(
            id = songId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            uri = fileUri,
            artworkUri = artUri,
            isDownloadable = true,
            isDownloaded = true,
            localUri = fileUri,
            fileSizeBytes = fileSizeBytes,
            dateAdded = downloadedAt
        )
    }
}
