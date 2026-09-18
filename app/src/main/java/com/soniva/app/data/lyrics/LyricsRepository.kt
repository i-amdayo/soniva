package com.soniva.app.data.lyrics

import com.soniva.app.data.db.CachedLyricsEntity
import com.soniva.app.data.db.LyricsDao
import com.soniva.app.data.network.NetworkMonitor
import com.soniva.app.model.LyricsResult
import com.soniva.app.model.Song
import com.soniva.app.util.parseLrc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LyricsRepository(
    private val lyricsDao: LyricsDao? = null,
    private val networkMonitor: NetworkMonitor? = null
) {
    suspend fun fetch(song: Song): LyricsResult = withContext(Dispatchers.IO) {
        // 1. Check local offline cache first
        val cached = lyricsDao?.getLyrics(song.id)
        if (cached != null && (!cached.plainLyrics.isNullOrBlank() || !cached.syncedLyricsLrc.isNullOrBlank())) {
            val syncedLines = cached.syncedLyricsLrc?.let { parseLrc(it) } ?: emptyList()
            return@withContext LyricsResult(cached.plainLyrics, syncedLines, cached.source)
        }

        // 2. If offline, do not attempt network requests
        if (networkMonitor?.isOnline?.value == false) {
            return@withContext LyricsResult(null, emptyList(), "offline")
        }

        // 3. Online fetch
        try {
            val artist = URLEncoder.encode(song.artist, "UTF-8")
            val title = URLEncoder.encode(song.title, "UTF-8")
            val album = URLEncoder.encode(song.album, "UTF-8")
            val duration = (song.durationMs / 1000).coerceAtLeast(1)

            val rawGet = getRaw("https://lrclib.net/api/get?artist_name=$artist&track_name=$title&album_name=$album&duration=$duration")
            val parsed = rawGet?.let { parseJsonToResult(it) }
                ?: parseSearch(song)

            if (parsed != null && parsed.first.hasAny) {
                val (res, rawSynced) = parsed
                lyricsDao?.cacheLyrics(
                    CachedLyricsEntity(
                        songId = song.id,
                        plainLyrics = res.plain,
                        syncedLyricsLrc = rawSynced,
                        source = res.source,
                        cachedAt = System.currentTimeMillis()
                    )
                )
                res
            } else {
                LyricsResult(null, emptyList(), "none")
            }
        } catch (_: Exception) {
            LyricsResult(null, emptyList(), "error")
        }
    }

    suspend fun cacheLyrics(songId: String, lyrics: LyricsResult, rawSyncedLrc: String? = null) = withContext(Dispatchers.IO) {
        val lrc = rawSyncedLrc ?: lyrics.synced.takeIf { it.isNotEmpty() }?.joinToString("\n") { line ->
            val totalSec = line.timeMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val ms = (line.timeMs % 1000) / 10
            String.format(java.util.Locale.US, "[%02d:%02d.%02d]%s", min, sec, ms, line.text)
        }
        lyricsDao?.cacheLyrics(
            CachedLyricsEntity(
                songId = songId,
                plainLyrics = lyrics.plain,
                syncedLyricsLrc = lrc,
                source = lyrics.source,
                cachedAt = System.currentTimeMillis()
            )
        )
    }

    private fun parseSearch(song: Song): Pair<LyricsResult, String?>? {
        val q = URLEncoder.encode("${song.artist} ${song.title}", "UTF-8")
        val raw = getRaw("https://lrclib.net/api/search?q=$q") ?: return null
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        if (arr.length() == 0) return null
        return parseObject(arr.optJSONObject(0))
    }

    private fun parseJsonToResult(jsonStr: String): Pair<LyricsResult, String?>? {
        val obj = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return null
        return parseObject(obj)
    }

    private fun parseObject(obj: JSONObject?): Pair<LyricsResult, String?>? {
        if (obj == null) return null
        val synced = obj.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = obj.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        if (synced == null && plain == null) return null
        val result = LyricsResult(plain, synced?.let { parseLrc(it) } ?: emptyList(), "lrclib")
        return Pair(result, synced)
    }

    private fun getRaw(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "Soniva/1.0.0 (personal)")
        }
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
