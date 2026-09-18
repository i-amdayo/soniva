package com.soniva.app.data.source

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.soniva.app.model.Album
import com.soniva.app.model.Artist
import com.soniva.app.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

interface MusicSource {
    val id: String
    val displayName: String
    val supportsStreaming: Boolean
    fun observeSongs(): Flow<List<Song>>
    suspend fun refresh()
    suspend fun search(query: String): SearchBundle
}

data class SearchBundle(val songs: List<Song>, val albums: List<Album>, val artists: List<Artist>)

class UnavailableStreamingSource : MusicSource {
    override val id = "unavailable_streaming"
    override val displayName = "YouTube (Restricted - No Offline Downloads)"
    override val supportsStreaming = false
    override fun observeSongs(): Flow<List<Song>> = flowOf(emptyList())
    override suspend fun refresh() = Unit
    override suspend fun search(query: String) = SearchBundle(emptyList(), emptyList(), emptyList())
}

class AuthorizedMusicSource : MusicSource {
    override val id = "authorized_open"
    override val displayName = "Authorized Open Audio"
    override val supportsStreaming = true

    private val sampleTracks = listOf(
        Song(
            id = "auth-beethoven-moonlight",
            title = "Moonlight Sonata (Adagio)",
            artist = "Ludwig van Beethoven",
            album = "Classical Masterpieces",
            durationMs = 315000L,
            uri = Uri.parse("https://ia800504.us.archive.org/11/items/MoonlightSonata_755/Beethoven-MoonlightSonata.mp3"),
            artworkUri = Uri.parse("https://upload.wikimedia.org/wikipedia/commons/thumb/6/6f/Beethoven.jpg/320px-Beethoven.jpg"),
            downloadUrl = "https://ia800504.us.archive.org/11/items/MoonlightSonata_755/Beethoven-MoonlightSonata.mp3",
            isDownloadable = true,
            year = 1801
        ),
        Song(
            id = "auth-debussy-clair-de-lune",
            title = "Clair de Lune",
            artist = "Claude Debussy",
            album = "Suite Bergamasque",
            durationMs = 304000L,
            uri = Uri.parse("https://ia800301.us.archive.org/15/items/ClairDeLune_677/Debussy-ClairDeLune.mp3"),
            artworkUri = Uri.parse("https://upload.wikimedia.org/wikipedia/commons/thumb/1/1a/Claude_Debussy_atelier_Nadar.jpg/320px-Claude_Debussy_atelier_Nadar.jpg"),
            downloadUrl = "https://ia800301.us.archive.org/15/items/ClairDeLune_677/Debussy-ClairDeLune.mp3",
            isDownloadable = true,
            year = 1905
        ),
        Song(
            id = "auth-vivaldi-spring",
            title = "The Four Seasons: Spring",
            artist = "Antonio Vivaldi",
            album = "The Four Seasons",
            durationMs = 216000L,
            uri = Uri.parse("https://ia800501.us.archive.org/34/items/FourSeasonsVivaldi/01_Spring_mvt_1_Allegro_-_John_Harrison_violin.mp3"),
            artworkUri = Uri.parse("https://upload.wikimedia.org/wikipedia/commons/thumb/b/bd/Antonio_Vivaldi_portrait.jpg/320px-Antonio_Vivaldi_portrait.jpg"),
            downloadUrl = "https://ia800501.us.archive.org/34/items/FourSeasonsVivaldi/01_Spring_mvt_1_Allegro_-_John_Harrison_violin.mp3",
            isDownloadable = true,
            year = 1725
        ),
        Song(
            id = "auth-pachelbel-canon",
            title = "Canon in D Major",
            artist = "Johann Pachelbel",
            album = "Baroque Treasures",
            durationMs = 360000L,
            uri = Uri.parse("https://ia800301.us.archive.org/10/items/CanonInD_637/Pachelbel-CanonInD.mp3"),
            artworkUri = Uri.parse("https://upload.wikimedia.org/wikipedia/commons/thumb/c/cd/Johann_Pachelbel.jpg/320px-Johann_Pachelbel.jpg"),
            downloadUrl = "https://ia800301.us.archive.org/10/items/CanonInD_637/Pachelbel-CanonInD.mp3",
            isDownloadable = true,
            year = 1680
        )
    )

    private val _songs = MutableStateFlow(sampleTracks)
    override fun observeSongs(): Flow<List<Song>> = _songs
    override suspend fun refresh() { _songs.value = sampleTracks }

    override suspend fun search(query: String): SearchBundle {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return SearchBundle(emptyList(), emptyList(), emptyList())
        val found = sampleTracks.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
        }
        val albums = found.groupBy { it.album to it.artist }.map { (k, list) ->
            Album(k.first, k.second, list.first().artworkUri, list.size, list.maxOf { it.year })
        }
        val artists = found.groupBy { it.artist }.map { (name, list) ->
            Artist(name, list.first().artworkUri, list.size, list.map { it.album }.toSet().size)
        }
        return SearchBundle(found, albums, artists)
    }
}

class LocalMusicSource(private val context: Context) : MusicSource {
    override val id = "local"
    override val displayName = "On this phone"
    override val supportsStreaming = false
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs
    override fun observeSongs() = songs
    override suspend fun refresh() { _songs.value = scan() }
    override suspend fun search(query: String): SearchBundle {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return SearchBundle(emptyList(), emptyList(), emptyList())
        val found = _songs.value.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q) }
        val albums = found.groupBy { it.album to it.artist }.map { (k, list) -> Album(k.first, k.second, list.first().artworkUri, list.size, list.maxOf { it.year }) }
        val artists = found.groupBy { it.artist }.map { (name, list) -> Artist(name, list.first().artworkUri, list.size, list.map { it.album }.toSet().size) }
        return SearchBundle(found, albums, artists)
    }
    private suspend fun scan(): List<Song> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.ALBUM_ID)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC}!=0 AND ${MediaStore.Audio.Media.DURATION}>=30000"
        val out = mutableListOf<Song>()
        try {
            context.contentResolver.query(collection, projection, selection, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val art = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), c.getLong(albumIdCol))
                    val artistName = c.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist"
                    val albumName = c.getString(albumCol)?.takeIf { it.isNotBlank() } ?: "Unknown album"
                    out += Song(
                        id = "local-$id",
                        title = c.getString(titleCol) ?: "Unknown title",
                        artist = artistName,
                        album = albumName,
                        durationMs = c.getLong(durCol),
                        uri = uri,
                        artworkUri = art,
                        trackNumber = c.getInt(trackCol) % 1000,
                        year = c.getInt(yearCol),
                        dateAdded = c.getLong(addedCol),
                        isDownloadable = true
                    )
                }
            }
        } catch (_: SecurityException) { return@withContext emptyList() }
        out
    }
}
