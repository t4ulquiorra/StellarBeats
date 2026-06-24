package com.stellarbeats.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object LyricsProvider {

    private const val LRC_LIB_BASE = "https://lrclib.net/api"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.NONE
                }
            )
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val xmlFactory by lazy {
        XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
    }

    suspend fun fetch(
        title: String,
        artist: String,
        album: String? = null,
        durationMs: Long? = null,
        youtubeCaptionUrl: String? = null,
        jioSaavnLyricsText: String? = null,
    ): Lyrics {
        return withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext Lyrics.EMPTY

            val lrcLibDeferred = async { fetchFromLrcLib(title, artist, album, durationMs) }
            val ytDeferred = async {
                if (youtubeCaptionUrl.isNullOrBlank()) null
                else fetchFromYouTube(youtubeCaptionUrl)
            }
            val jioSaavnDeferred = async {
                if (jioSaavnLyricsText.isNullOrBlank()) null
                else parsePlainText(jioSaavnLyricsText, LyricsSource.JIOSAAVN)
            }

            val lrcLibResult = lrcLibDeferred.await()
            val ytResult = ytDeferred.await()
            val jioSaavnResult = jioSaavnDeferred.await()

            val synced = listOf(lrcLibResult, ytResult).firstOrNull { it != null && it.isSynced }
            if (synced != null) return@withContext synced

            val unsynced = listOf(lrcLibResult, ytResult, jioSaavnResult).firstOrNull { it != null && it.lines.isNotEmpty() }
            if (unsynced != null) return@withContext unsynced

            Lyrics.EMPTY
        }
    }

    suspend fun hasSyncedLyrics(
        title: String,
        artist: String,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext false
            try {
                val result = fetchFromLrcLib(title, artist, null, null)
                result != null && result.isSynced
            } catch (_: Exception) {
                false
            }
        }
    }

    private suspend fun fetchFromLrcLib(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
    ): Lyrics? {
        return try {
            val precise = fetchLrcLibPrecise(title, artist, album, durationMs)
            if (precise != null) return precise
            fetchLrcLibSearch(title, artist, durationMs)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchLrcLibPrecise(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
    ): Lyrics? {
        val params = buildString {
            append("?artist_name=${URLEncoder.encode(artist, "UTF-8")}")
            append("&track_name=${URLEncoder.encode(title, "UTF-8")}")
            album?.let { append("&album_name=${URLEncoder.encode(it, "UTF-8")}") }
            durationMs?.let { append("&duration=${it / 1000f}") }
        }

        val raw = httpGet("$LRC_LIB_BASE/get$params") ?: return null
        val track = json.decodeFromString<LrcLibTrack>(raw)

        if (durationMs != null && track.duration != null) {
            if (kotlin.math.abs(track.duration * 1000 - durationMs) > 5000) {
                return null
            }
        }

        return lrcLibTrackToLyrics(track)
    }

    private suspend fun fetchLrcLibSearch(
        title: String,
        artist: String,
        durationMs: Long?,
    ): Lyrics? {
        val query = "$artist $title"
        val raw = httpGet("$LRC_LIB_BASE/search?q=${URLEncoder.encode(query, "UTF-8")}")
            ?: return null

        val results = json.decodeFromString<List<LrcLibResult>>(raw)
        if (results.isEmpty()) return null

        val scored = results.map { track ->
            var score = 0

            if (track.trackName.equals(title, ignoreCase = true)) score += 10
            else if (track.trackName.contains(title, ignoreCase = true) ||
                title.contains(track.trackName, ignoreCase = true)
            ) score += 5

            if (track.artistName.equals(artist, ignoreCase = true)) score += 10
            else if (track.artistName.contains(artist, ignoreCase = true) ||
                artist.contains(track.artistName, ignoreCase = true)
            ) score += 5

            if (!track.syncedLyrics.isNullOrBlank()) score += 20

            durationMs?.let { known ->
                track.duration?.let { lrcDur ->
                    val diff = kotlin.math.abs(lrcDur * 1000 - known)
                    score += maxOf(0, (10 - diff / 2000).toInt())
                }
            }

            if (!track.instrumental) score += 2

            track to score
        }

        val best = scored.maxByOrNull { it.second } ?: return null
        if (best.second < 5) return null

        return lrcLibTrackToLyrics(best.first)
    }

    private fun lrcLibTrackToLyrics(track: LrcLibTrack): Lyrics? {
        if (track.instrumental) {
            return Lyrics(
                lines = listOf(LyricsLine(null, "", isInstrumental = true)),
                isSynced = false,
                source = LyricsSource.LRCLIB,
                remoteUrl = "https://lrclib.net/lyrics/${track.id}",
                providerColor = LyricsSource.LRCLIB.accentColor,
            )
        }

        val syncedLyrics = track.syncedLyrics
        if (!syncedLyrics.isNullOrBlank()) {
            val parsed = LrcParser.parse(syncedLyrics)
            if (parsed.lines.isNotEmpty()) {
                return Lyrics(
                    lines = parsed.lines,
                    isSynced = true,
                    source = LyricsSource.LRCLIB,
                    remoteUrl = "https://lrclib.net/lyrics/${track.id}",
                    providerColor = LyricsSource.LRCLIB.accentColor,
                )
            }
        }

        val plainLyrics = track.plainLyrics
        if (!plainLyrics.isNullOrBlank()) {
            return Lyrics(
                lines = parsePlainText(plainLyrics, LyricsSource.LRCLIB).lines,
                isSynced = false,
                source = LyricsSource.LRCLIB,
                remoteUrl = "https://lrclib.net/lyrics/${track.id}",
                providerColor = LyricsSource.LRCLIB.accentColor,
            )
        }

        return null
    }

    private suspend fun fetchFromYouTube(captionBaseUrl: String): Lyrics? {
        return try {
            val separator = if (captionBaseUrl.contains("?")) "&" else "?"
            val url = "$captionBaseUrl${separator}fmt=srv3"

            val xml = httpGet(url) ?: return null
            val track = YouTubeCaptionParser.parse(xml, xmlFactory)

            if (track.lines.isEmpty()) return null

            Lyrics(
                lines = if (track.isAutoGenerated) {
                    mergeAutoGenLines(track.lines)
                } else {
                    track.lines
                },
                isSynced = true,
                source = LyricsSource.YOUTUBE,
                providerColor = LyricsSource.YOUTUBE.accentColor,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun mergeAutoGenLines(lines: List<LyricsLine>): List<LyricsLine> {
        if (lines.size <= 1) return lines

        val merged = mutableListOf<LyricsLine>()
        var buffer = StringBuilder()
        var startTime: Long? = null
        var lastTime: Long = 0

        val punctuation = setOf('.', '!', '?', ',', ';', ':', '—', '-')

        fun flush() {
            val text = buffer.toString().trim()
            if (text.isNotBlank()) {
                merged.add(LyricsLine(startTime, text))
            }
            buffer.clear()
            startTime = null
        }

        for (i in lines.indices) {
            val line = lines[i]
            val time = line.timeMs ?: continue
            val text = line.text.trim()
            if (text.isBlank()) continue

            if (startTime == null) startTime = time

            val endsWithPunctuation = text.lastOrNull() in punctuation

            buffer.append(text)
            lastTime = time

            val nextTime = lines.getOrNull(i + 1)?.timeMs
            val nextGap = if (nextTime != null) nextTime - time else Long.MAX_VALUE

            if (endsWithPunctuation || nextGap > 400) {
                flush()
            } else {
                buffer.append(" ")
            }
        }

        flush()

        return merged
    }

    private fun parsePlainText(text: String, source: LyricsSource): Lyrics {
        val lines = text
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "[chorus]" && it != "[verse]" && it != "[intro]" }
            .map { LyricsLine(timeMs = null, text = it) }

        return Lyrics(
            lines = lines,
            isSynced = false,
            source = source,
            providerColor = source.accentColor,
        )
    }

    private fun httpGet(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "StellarBeats/1.0")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.body?.close()
                return null
            }

            val body = response.body?.string()
            response.close()
            body
        } catch (_: Exception) {
            null
        }
    }
}

internal object LrcParser {

    private val TIME_TAG_REGEX = Regex(
        """\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?\]"""
    )

    private val META_TAG_REGEX = Regex(
        """\[(ti|ar|al|by|offset):(.+)\]""",
        RegexOption.IGNORE_CASE
    )

    fun parse(lrcText: String): LrcParseResult {
        val lines = mutableListOf<LyricsLine>()
        val metadataBuilder = Builder()
        var errors = 0

        for (rawLine in lrcText.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isBlank()) continue

            val metaMatch = META_TAG_REGEX.find(trimmed)
            if (metaMatch != null) {
                val key = metaMatch.groupValues[1].lowercase()
                val value = metaMatch.groupValues[2].trim()
                when (key) {
                    "ti" -> metadataBuilder.title = value
                    "ar" -> metadataBuilder.artist = value
                    "al" -> metadataBuilder.album = value
                    "by" -> metadataBuilder.by = value
                    "offset" -> metadataBuilder.offset = value.toLongOrNull() ?: 0
                }
                continue
            }

            val timestamps = TIME_TAG_REGEX.findAll(trimmed).toList()
            if (timestamps.isEmpty()) {
                lines.add(LyricsLine(timeMs = null, text = trimmed))
                continue
            }

            val lastMatchEnd = timestamps.last().range.last + 1
            val text = trimmed.substring(lastMatchEnd).trim()

            if (text.equals("<instrumental>", ignoreCase = true) ||
                text.equals("♪ instrumental ♪", ignoreCase = true) ||
                text.equals("instrumental", ignoreCase = true)
            ) {
                timestamps.forEach { match ->
                    lines.add(LyricsLine(
                        timeMs = parseTime(match) + metadataBuilder.offset,
                        text = "",
                        isInstrumental = true,
                    ))
                }
                continue
            }

            if (text.isBlank()) {
                errors++
                continue
            }

            timestamps.forEach { match ->
                val timeMs = parseTime(match) + metadataBuilder.offset
                if (timeMs < 0) {
                    errors++
                    return@forEach
                }
                lines.add(LyricsLine(timeMs = timeMs, text = text))
            }
        }

        val sorted = lines.sortedWith(
            compareBy<LyricsLine> { it.timeMs ?: Long.MAX_VALUE }
        )

        return LrcParseResult(
            lines = sorted,
            metadata = metadataBuilder.build(),
            parseErrors = errors,
        )
    }

    private fun parseTime(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLongOrNull() ?: 0
        val seconds = match.groupValues[2].toLongOrNull() ?: 0
        val fraction = match.groupValues[3]

        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLongOrNull()?.let { it * 100 } ?: 0L
            2 -> fraction.toLongOrNull()?.let { it * 10 } ?: 0L
            else -> fraction.toLongOrNull() ?: 0L
        }

        return (minutes * 60_000) + (seconds * 1000) + fractionMs
    }

    private class Builder {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var by: String? = null
        var offset: Long = 0

        fun build() = LrcMetadata(title, artist, album, by, offset)
    }
}

internal object YouTubeCaptionParser {

    fun parse(xml: String, factory: XmlPullParserFactory): YouTubeCaptionTrack {
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())

        val lines = mutableListOf<LyricsLine>()
        var isAutoGenerated = false
        var totalDuration = 0L
        var count = 0

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "text") {
                val start = parser.getAttributeValue(null, "start")?.toFloatOrNull() ?: continue
                val dur = parser.getAttributeValue(null, "dur")?.toFloatOrNull() ?: 0f

                eventType = parser.next()
                if (eventType == XmlPullParser.TEXT) {
                    val text = parser.text?.trim()?.replace("&amp;", "&")
                        ?.replace("&lt;", "<")?.replace("&gt;", ">")
                        ?.replace("&quot;", "\"")?.replace("&#39;", "'")
                        ?: continue

                    if (text.isNotBlank()) {
                        lines.add(LyricsLine(
                            timeMs = (start * 1000).toLong(),
                            text = text,
                        ))
                        totalDuration += (dur * 1000).toLong()
                        count++
                    }
                }
            }
            eventType = parser.next()
        }

        isAutoGenerated = if (count > 0) {
            (totalDuration / count) < 800
        } else {
            false
        }

        return YouTubeCaptionTrack(lines, isAutoGenerated)
    }
}
