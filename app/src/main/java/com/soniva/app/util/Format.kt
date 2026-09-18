package com.soniva.app.util

import com.soniva.app.model.LyricLine
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val total = TimeUnit.MILLISECONDS.toSeconds(ms)
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

fun parseLrc(lrc: String): List<LyricLine> {
    val pattern = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?](.*)""")
    return lrc.lineSequence().mapNotNull { raw ->
        val match = pattern.find(raw.trim()) ?: return@mapNotNull null
        val min = match.groupValues[1].toLong(); val sec = match.groupValues[2].toLong(); val frac = match.groupValues[3]
        val text = match.groupValues[4].trim(); if (text.isEmpty()) return@mapNotNull null
        val extra = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
        LyricLine(min * 60_000 + sec * 1000 + extra, text)
    }.sortedBy { it.timeMs }.toList()
}
