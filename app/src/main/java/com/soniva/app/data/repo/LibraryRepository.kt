package com.soniva.app.data.repo

import android.net.Uri
import com.soniva.app.data.db.DownloadedSongEntity
import com.soniva.app.data.db.LikedSongEntity
import com.soniva.app.data.db.PlaylistEntity
import com.soniva.app.data.db.PlaylistSongEntity
import com.soniva.app.data.db.RecentPlayEntity
import com.soniva.app.data.db.SearchHistoryEntity
import com.soniva.app.data.db.SonivaDatabase
import com.soniva.app.data.source.AuthorizedMusicSource
import com.soniva.app.data.source.LocalMusicSource
import com.soniva.app.data.source.SearchBundle
import com.soniva.app.model.Album
import com.soniva.app.model.Artist
import com.soniva.app.model.Playlist
import com.soniva.app.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File

class LibraryRepository(
    private val db: SonivaDatabase,
    private val local: LocalMusicSource,
    private val authorized: AuthorizedMusicSource = AuthorizedMusicSource()
) {
    // Combined observable songs (local + authorized)
    val songs: Flow<List<Song>> = combine(
        local.songs,
        authorized.observeSongs(),
        db.downloadDao().observeDownloadedSongIds()
    ) { localList, authList, downloadedIds ->
        val idSet = downloadedIds.toSet()
        val all = (localList + authList).distinctBy { it.id }
        all.map { s ->
            if (idSet.contains(s.id)) {
                s.copy(isDownloaded = true)
            } else s
        }
    }

    val downloadedSongs: Flow<List<Song>> = db.downloadDao().observeDownloads().map { list ->
        list.map { it.toDownloadedSong() }
    }

    val downloadedIds: Flow<Set<String>> = db.downloadDao().observeDownloadedSongIds().map { it.toSet() }

    val likedIds: Flow<Set<String>> = db.likedDao().observeLikedIds().map { it.toSet() }
    val likedSongs: Flow<List<Song>> = db.likedDao().observeLiked().map { list -> list.map { it.toSong() } }
    val recentSongs: Flow<List<Song>> = db.recentDao().observeRecent().map { list -> list.map { it.toSong() } }
    val playlists: Flow<List<Playlist>> = db.playlistDao().observePlaylists().map { lists -> lists.map { p -> Playlist(p.id, p.name, p.createdAt, 0) } }
    val searchHistory = db.searchHistoryDao().observe()

    suspend fun scanLocal() {
        local.refresh()
        authorized.refresh()
    }

    fun albums(songList: List<Song>): List<Album> = songList.groupBy { it.album to it.artist }
        .map { (k, list) -> Album(k.first, k.second, list.first().artworkUri, list.size, list.maxOf { it.year }) }
        .sortedBy { it.name.lowercase() }

    fun artists(songList: List<Song>): List<Artist> = songList.groupBy { it.artist }
        .map { (name, list) -> Artist(name, list.first().artworkUri, list.size, list.map { it.album }.distinct().size) }
        .sortedBy { it.name.lowercase() }

    suspend fun search(query: String): SearchBundle {
        if (query.isNotBlank()) db.searchHistoryDao().upsert(SearchHistoryEntity(query.trim(), System.currentTimeMillis()))
        val localRes = local.search(query)
        val authRes = authorized.search(query)
        val combinedSongs = (localRes.songs + authRes.songs).distinctBy { it.id }
        val combinedAlbums = (localRes.albums + authRes.albums).distinctBy { it.name + it.artist }
        val combinedArtists = (localRes.artists + authRes.artists).distinctBy { it.name }
        return SearchBundle(combinedSongs, combinedAlbums, combinedArtists)
    }

    suspend fun clearSearchHistory() = db.searchHistoryDao().clear()

    suspend fun toggleLike(song: Song): Boolean {
        val liked = db.likedDao().isLiked(song.id)
        if (liked) {
            db.likedDao().unlike(song.id)
        } else {
            db.likedDao().like(LikedSongEntity(
                song.id,
                song.title,
                song.artist,
                song.album,
                song.durationMs,
                (song.localUri ?: song.uri).toString(),
                song.artworkUri?.toString(),
                System.currentTimeMillis()
            ))
        }
        return !liked
    }

    suspend fun recordPlay(song: Song) {
        db.recentDao().upsert(RecentPlayEntity(
            song.id,
            song.title,
            song.artist,
            song.album,
            song.durationMs,
            (song.localUri ?: song.uri).toString(),
            song.artworkUri?.toString(),
            System.currentTimeMillis()
        ))
    }

    suspend fun createPlaylist(name: String): Long = db.playlistDao().insert(PlaylistEntity(name = name.trim().ifBlank { "New playlist" }, createdAt = System.currentTimeMillis()))
    suspend fun deletePlaylist(id: Long) { db.playlistDao().clearSongs(id); db.playlistDao().delete(id) }
    fun observePlaylistSongs(id: Long): Flow<List<Song>> = db.playlistDao().observeSongs(id).map { list -> list.map { it.toSong() } }

    suspend fun addToPlaylist(playlistId: Long, song: Song) {
        val existing = db.playlistDao().getSongs(playlistId)
        if (existing.any { it.songId == song.id }) return
        db.playlistDao().insertSongs(listOf(PlaylistSongEntity(
            playlistId,
            song.id,
            song.title,
            song.artist,
            song.album,
            song.durationMs,
            (song.localUri ?: song.uri).toString(),
            song.artworkUri?.toString(),
            existing.size
        )))
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: String) {
        db.playlistDao().removeSong(playlistId, songId)
        db.playlistDao().replaceOrder(playlistId, db.playlistDao().getSongs(playlistId).mapIndexed { i, s -> s.copy(position = i) })
    }

    suspend fun getDownloadedSong(songId: String): Song? {
        return db.downloadDao().getDownload(songId)?.toDownloadedSong()
    }

    private fun LikedSongEntity.toSong() = Song(songId, title, artist, album, durationMs, Uri.parse(uri), artworkUri?.let { Uri.parse(it) })
    private fun RecentPlayEntity.toSong() = Song(songId, title, artist, album, durationMs, Uri.parse(uri), artworkUri?.let { Uri.parse(it) })
    private fun PlaylistSongEntity.toSong() = Song(songId, title, artist, album, durationMs, Uri.parse(uri), artworkUri?.let { Uri.parse(it) })

    private fun DownloadedSongEntity.toDownloadedSong(): Song {
        val fileUri = Uri.fromFile(File(localFilePath))
        val artUri = localArtPath?.let { Uri.fromFile(File(it)) } ?: (if (originalUri.isNotBlank()) Uri.parse(originalUri) else null)
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
