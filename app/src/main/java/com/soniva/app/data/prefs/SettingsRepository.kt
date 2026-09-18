package com.soniva.app.data.prefs
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.soniva.app.model.ArtworkQuality
import com.soniva.app.model.StreamQuality
import com.soniva.app.model.ThemeMode
import com.soniva.app.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
private val Context.dataStore by preferencesDataStore("soniva_settings")
class SettingsRepository(private val context: Context) {
    private val theme = stringPreferencesKey("theme")
    private val dynamic = booleanPreferencesKey("dynamic")
    private val speed = floatPreferencesKey("speed")
    private val wifiOnly = booleanPreferencesKey("wifi_only")
    private val dataSaver = booleanPreferencesKey("data_saver")
    private val streamQ = stringPreferencesKey("stream_q")
    private val downloadQ = stringPreferencesKey("download_q")
    private val artQ = stringPreferencesKey("art_q")
    private val synced = booleanPreferencesKey("synced_lyrics")
    private val gapless = booleanPreferencesKey("gapless")
    private val normalize = booleanPreferencesKey("normalize")
    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            themeMode = runCatching { ThemeMode.valueOf(p[theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = p[dynamic] ?: true,
            playbackSpeed = p[speed] ?: 1f,
            wifiOnlyDownloads = p[wifiOnly] ?: true,
            dataSaver = p[dataSaver] ?: false,
            streamQuality = runCatching { StreamQuality.valueOf(p[streamQ] ?: "HIGH") }.getOrDefault(StreamQuality.HIGH),
            downloadQuality = runCatching { StreamQuality.valueOf(p[downloadQ] ?: "HIGH") }.getOrDefault(StreamQuality.HIGH),
            artworkQuality = runCatching { ArtworkQuality.valueOf(p[artQ] ?: "HIGH") }.getOrDefault(ArtworkQuality.HIGH),
            syncedLyrics = p[synced] ?: true,
            gapless = p[gapless] ?: true,
            normalizeVolume = p[normalize] ?: false
        )
    }
    suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.dataStore.edit { p ->
            val n = transform(UserSettings(
                themeMode = runCatching { ThemeMode.valueOf(p[theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
                dynamicColor = p[dynamic] ?: true,
                playbackSpeed = p[speed] ?: 1f,
                wifiOnlyDownloads = p[wifiOnly] ?: true,
                dataSaver = p[dataSaver] ?: false,
                streamQuality = runCatching { StreamQuality.valueOf(p[streamQ] ?: "HIGH") }.getOrDefault(StreamQuality.HIGH),
                downloadQuality = runCatching { StreamQuality.valueOf(p[downloadQ] ?: "HIGH") }.getOrDefault(StreamQuality.HIGH),
                artworkQuality = runCatching { ArtworkQuality.valueOf(p[artQ] ?: "HIGH") }.getOrDefault(ArtworkQuality.HIGH),
                syncedLyrics = p[synced] ?: true,
                gapless = p[gapless] ?: true,
                normalizeVolume = p[normalize] ?: false
            ))
            p[theme] = n.themeMode.name; p[dynamic] = n.dynamicColor; p[speed] = n.playbackSpeed
            p[wifiOnly] = n.wifiOnlyDownloads; p[dataSaver] = n.dataSaver
            p[streamQ] = n.streamQuality.name; p[downloadQ] = n.downloadQuality.name
            p[artQ] = n.artworkQuality.name; p[synced] = n.syncedLyrics; p[gapless] = n.gapless; p[normalize] = n.normalizeVolume
        }
    }
}
