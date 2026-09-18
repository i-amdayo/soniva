package com.soniva.app.model
import android.net.Uri
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri,
    val artworkUri: Uri?,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val dateAdded: Long = 0L,
    val downloadUrl: String? = null,
    val isDownloadable: Boolean = false,
    val isDownloaded: Boolean = false,
    val localUri: Uri? = null,
    val fileSizeBytes: Long = 0L
)

enum class DownloadStatus {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

data class DownloadProgress(
    val songId: String,
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val error: String? = null
)

data class DownloadedSong(
    val song: Song,
    val localFilePath: String,
    val localArtPath: String?,
    val fileSizeBytes: Long,
    val downloadedAt: Long,
    val hasLyrics: Boolean = false
)

data class DownloadedPlaylist(
    val playlist: Playlist,
    val songCount: Int,
    val totalSizeBytes: Long,
    val isFullyDownloaded: Boolean
)
data class Album(val name: String, val artist: String, val artworkUri: Uri?, val songCount: Int, val year: Int = 0)
data class Artist(val name: String, val artworkUri: Uri?, val songCount: Int, val albumCount: Int)
data class Playlist(val id: Long, val name: String, val createdAt: Long, val songCount: Int = 0)
data class LyricsResult(val plain: String?, val synced: List<LyricLine>, val source: String) {
    val hasSynced get() = synced.isNotEmpty()
    val hasAny get() = hasSynced || !plain.isNullOrBlank()
}
data class LyricLine(val timeMs: Long, val text: String)
enum class RepeatMode { OFF, ALL, ONE }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class StreamQuality { LOW, MEDIUM, HIGH }
enum class ArtworkQuality { LOW, HIGH }
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val wifiOnlyDownloads: Boolean = true,
    val dataSaver: Boolean = false,
    val streamQuality: StreamQuality = StreamQuality.HIGH,
    val downloadQuality: StreamQuality = StreamQuality.HIGH,
    val artworkQuality: ArtworkQuality = ArtworkQuality.HIGH,
    val syncedLyrics: Boolean = true,
    val gapless: Boolean = true,
    val normalizeVolume: Boolean = false
)

