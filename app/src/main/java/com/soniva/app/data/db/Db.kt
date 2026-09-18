package com.soniva.app.data.db
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playlists")
data class PlaylistEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val createdAt: Long)
@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongEntity(val playlistId: Long, val songId: String, val title: String, val artist: String, val album: String, val durationMs: Long, val uri: String, val artworkUri: String?, val position: Int)
@Entity(tableName = "liked_songs")
data class LikedSongEntity(@PrimaryKey val songId: String, val title: String, val artist: String, val album: String, val durationMs: Long, val uri: String, val artworkUri: String?, val likedAt: Long)
@Entity(tableName = "recent_plays")
data class RecentPlayEntity(@PrimaryKey val songId: String, val title: String, val artist: String, val album: String, val durationMs: Long, val uri: String, val artworkUri: String?, val playedAt: Long)
@Entity(tableName = "search_history")
data class SearchHistoryEntity(@PrimaryKey val query: String, val searchedAt: Long)

@Entity(tableName = "downloaded_songs")
data class DownloadedSongEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val originalUri: String,
    val localFilePath: String,
    val localArtPath: String?,
    val fileSizeBytes: Long,
    val downloadedAt: Long,
    val hasLyrics: Boolean = false,
    val associatedPlaylistIds: String = ""
)

@Entity(tableName = "downloaded_playlists")
data class DownloadedPlaylistEntity(
    @PrimaryKey val playlistId: Long,
    val name: String,
    val songCount: Int,
    val totalSizeBytes: Long,
    val downloadedAt: Long
)

@Entity(tableName = "cached_lyrics")
data class CachedLyricsEntity(
    @PrimaryKey val songId: String,
    val plainLyrics: String?,
    val syncedLyricsLrc: String?,
    val source: String,
    val cachedAt: Long
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC") fun observePlaylists(): Flow<List<PlaylistEntity>>
    @Insert suspend fun insert(entity: PlaylistEntity): Long
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId") suspend fun clearSongs(playlistId: Long)
    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position") fun observeSongs(playlistId: Long): Flow<List<PlaylistSongEntity>>
    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position") suspend fun getSongs(playlistId: Long): List<PlaylistSongEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSongs(songs: List<PlaylistSongEntity>)
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId") suspend fun removeSong(playlistId: Long, songId: String)
    @Transaction suspend fun replaceOrder(playlistId: Long, songs: List<PlaylistSongEntity>) { clearSongs(playlistId); insertSongs(songs) }
}
@Dao
interface LikedDao {
    @Query("SELECT * FROM liked_songs ORDER BY likedAt DESC") fun observeLiked(): Flow<List<LikedSongEntity>>
    @Query("SELECT songId FROM liked_songs") fun observeLikedIds(): Flow<List<String>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun like(entity: LikedSongEntity)
    @Query("DELETE FROM liked_songs WHERE songId = :songId") suspend fun unlike(songId: String)
    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE songId = :songId)") suspend fun isLiked(songId: String): Boolean
}
@Dao
interface RecentDao {
    @Query("SELECT * FROM recent_plays ORDER BY playedAt DESC LIMIT :limit") fun observeRecent(limit: Int = 40): Flow<List<RecentPlayEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: RecentPlayEntity)
}
@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 20") fun observe(): Flow<List<SearchHistoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: SearchHistoryEntity)
    @Query("DELETE FROM search_history") suspend fun clear()
}
@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloaded_songs ORDER BY downloadedAt DESC")
    fun observeDownloads(): Flow<List<DownloadedSongEntity>>

    @Query("SELECT * FROM downloaded_songs ORDER BY downloadedAt DESC")
    suspend fun getAllDownloads(): List<DownloadedSongEntity>

    @Query("SELECT * FROM downloaded_songs WHERE songId = :songId")
    suspend fun getDownload(songId: String): DownloadedSongEntity?

    @Query("SELECT songId FROM downloaded_songs")
    fun observeDownloadedSongIds(): Flow<List<String>>

    @Query("SELECT songId FROM downloaded_songs")
    suspend fun getDownloadedSongIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(entity: DownloadedSongEntity)

    @Query("DELETE FROM downloaded_songs WHERE songId = :songId")
    suspend fun deleteDownload(songId: String)

    @Query("DELETE FROM downloaded_songs")
    suspend fun deleteAllDownloads()

    @Query("SELECT SUM(fileSizeBytes) FROM downloaded_songs")
    fun observeTotalStorageUsed(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM downloaded_songs")
    fun observeDownloadCount(): Flow<Int>

    @Query("SELECT * FROM downloaded_playlists ORDER BY downloadedAt DESC")
    fun observeDownloadedPlaylists(): Flow<List<DownloadedPlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadedPlaylist(entity: DownloadedPlaylistEntity)

    @Query("DELETE FROM downloaded_playlists WHERE playlistId = :playlistId")
    suspend fun deleteDownloadedPlaylist(playlistId: Long)

    @Query("DELETE FROM downloaded_playlists")
    suspend fun deleteAllDownloadedPlaylists()
}
@Dao
interface LyricsDao {
    @Query("SELECT * FROM cached_lyrics WHERE songId = :songId")
    suspend fun getLyrics(songId: String): CachedLyricsEntity?

    @Query("SELECT * FROM cached_lyrics WHERE songId = :songId")
    fun observeLyrics(songId: String): Flow<CachedLyricsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheLyrics(entity: CachedLyricsEntity)

    @Query("DELETE FROM cached_lyrics WHERE songId = :songId")
    suspend fun deleteLyrics(songId: String)

    @Query("DELETE FROM cached_lyrics")
    suspend fun clearAllLyrics()
}

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        LikedSongEntity::class,
        RecentPlayEntity::class,
        SearchHistoryEntity::class,
        DownloadedSongEntity::class,
        DownloadedPlaylistEntity::class,
        CachedLyricsEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SonivaDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun likedDao(): LikedDao
    abstract fun recentDao(): RecentDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun lyricsDao(): LyricsDao
}
