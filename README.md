# Soniva

Personal Android music app. Kotlin, Jetpack Compose, Material 3, Media3.

## What works

- Scan and play local audio (MP3, M4A, FLAC, WAV, OGG) via MediaStore
- Background playback with Media3 / notification / lock-screen / Bluetooth controls
- Queue, shuffle, repeat, seek, playback speed
- Liked songs, playlists, recently played (Room)
- Search + search history
- Home, album, artist, library screens
- Mini player and full Now Playing
- Sleep timer
- Light / dark / system themes + dynamic color
- Synced and plain lyrics from lrclib.net (no API key). Highlight follows Media3 position.
- Offline local library

## What is intentionally not included

YouTube Music has **no official public streaming API**. An unofficial scraper would violate YouTube terms and is not bundled.

Streaming, remote recommendations, and source-side downloads sit behind a `MusicSource` interface. `LocalMusicSource` is implemented. `UnavailableStreamingSource` is the placeholder for a future licensed provider.

No copyrighted lyrics are hardcoded.

## Build the APK (no PC)

1. Create a free GitHub account on your phone.
2. Create a new repository and upload this project (or push the zip contents).
3. Open the Actions tab → **Build Soniva APK** → Run workflow.
4. When it finishes, download the **Soniva** artifact (the APK).
5. On your phone allow installs from that source and install `app-debug.apk`.

The first launch asks for music-file permission. Grant it so Soniva can see songs already on the phone.
