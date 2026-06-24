package com.stellarbeats.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Unified lyrics provider with multi-source fallback.
 *
 * Resolution order:
 * 1. **LRCLIB** — synced LRC lyrics from community database
 * 2. **YouTube captions** — timed text from the InnerTube player response
 * 3. **JioSaavn** — plain text, no timestamps (last resort)
 *
 * The provider is stateless and safe to call from any thread.
 * All I/O runs on Dispatchers.IO.
 *
 * Usage from the app's repository:
 * ```kotlin
 * val lyrics = LyricsProvider.fetch(
 *     title = "Bohemian Rhapsody",
 *     artist = "Queen",
 *     album = "A Night at the Opera",
 *     durationMs = 354000,
 *     youtubeCaptionUrl = playerResponse.lyricsEndpoint?.watchEndpointTimedTextTrackId,
 *     jioSaavnLyricsText = jioSaavnLyrics,
 * )
 * ```
 */
object LyricsProvider {

    private const val LRC_LIB_BASE = "https://lrclib.net/api"

    // ── HTTP client ─────────────────────────────

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

    // ── XML parser factory (thread-safe after init) ──

    private val xmlFactory by lazy {
        XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
    }

    // ──────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────

    /**
     * Fetch lyrics from all available sources, returning the best result.
     *
     * Sources are queried concurrently. The first synced result wins.
     * If no synced lyrics are found, the best unsynced result is returned.
     *
     * @param title Track title
     * @param artist Primary artist name
     * @param album Album name (helps LRCLIB disambiguate)
     * @param durationMs Track duration in ms (helps LRCLIB pick the right version)
     * @param youtubeCaptionUrl The caption base URL from InnerTube's
     *        watchEndpointTimedTextTrackId. This is the YouTube timedtext
     *        URL that returns XML when fetched with &fmt=srv3
     * @param jioSaavnLyricsText Plain text lyrics from JioSaavn API
     * @return Best available [Lyrics], or [Lyrics.EMPTY] if nothing found
     */
    suspend fun fetch(
        title: String,
        artist: String,
        album: String? = null,
        durationMs: Long? = null,
        youtubeCaptionUrl: String? = null,
        jioSaavnLyricsText: String? = null,
    ): Lyrics = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext Lyrics.EMPTY

        // Launch all sources concurrently
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

        // Priority 1: Any synced source
        listOf(lrcLibResult, ytResult)
            .firstOrNull { it != null && it.isSynced }
            ?.let { return@withContext it }

        // Priority 2: Unsynced but non-empty
        listOf(lrcLibResult, ytResult, jioSaavnResult)
            .firstOrNull { it != null && it.lines.isNotEmpty() }
            ?.let { return@withContext it }

        Lyrics.EMPTY
    }

    /**
     * Quick check — does LRCLIB have synced lyrics for this track?
     *
     * Lighter than [fetch] because it only hits LRCLIB and stops
     * as soon as it finds synced lyrics. Useful for pre-fetching
     * the "lyrics" icon indicator on track list items.
     */
    suspend fun hasSyncedLyrics(
        title: String,
        artist: String,
    ): Boolean = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext false
        try {
            val result = fetchFromLrcLib(title, artist, null, null)
            result != null && result.isSynced
        } catch (_: Exception) {
            false
        }
    }

    // ──────────────────────────────────────────
    // Source 1: LRCLIB
    // ──────────────────────────────────────────

    private suspend fun fetchFromLrcLib(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
    ): Lyrics? {
        return try {
            // Try the precise lookup first
            val precise = fetchLrcLibPrecise(title, artist, album, durationMs)
            if (precise != null) return precise

            // Fallback to search
            fetchLrcLibSearch(title, artist, durationMs)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Precise lookup by artist + track name.
     * GET /api/get?artist_name=X&track_name=X
     * Returns a single object or 404.
     */
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

        // Duration sanity check — if we have a known duration and the
        // LRCLIB result differs by more than 5 seconds, it's likely
        // a different version (radio edit, live, etc.)
        durationMs?.let { known ->
            track.duration?.let { lrcDuration ->
                if (kotlin.math.abs(lrcDuration * 1000 - known) > 5000) {
                    return null
                }
            }
        }

        lrcLibTrackToLyrics(track)
    }

    /**
     * Search-based fallback.
     * GET /api/search?q={artist} {title}
     * Returns array of results — we pick the best match.
     */
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

        // Score each result and pick the best
        val scored = results.map { track ->
            var score = 0

            // Exact title match
            if (track.trackName.equals(title, ignoreCase = true)) score += 10
            else if (track.trackName.contains(title, ignoreCase = true) ||
                title.contains(track.trackName, ignoreCase = true)
            ) score += 5

            // Exact artist match
            if (track.artistName.equals(artist, ignoreCase = true)) score += 10
            else if (track.artistName.contains(artist, ignoreCase = true) ||
                artist.contains(track.artistName, ignoreCase = true)
            ) score += 5

            // Has synced lyrics (huge bonus)
            if (!track.syncedLyrics.isNullOrBlank()) score += 20

            // Duration proximity
            durationMs?.let { known ->
                track.duration?.let { lrcDur ->
                    val diff = kotlin.math.abs(lrcDur * 1000 - known)
                    score += maxOf(0, (10 - diff / 2000).toInt())
                }
            }

            // Prefer non-instrumental (instrumental tracks have no lyrics anyway)
            if (!track.instrumental) score += 2

            track to score
        }

        val best = scored.maxByOrNull { it.second } ?: return null
        if (best.second < 5) return null // Threshold — don't return garbage matches

        return lrcLibTrackToLyrics(best.first)
    }

    private fun lrcLibTrackToLyrics(track: LrcLibTrack): Lyrics? {
        // Instrumental marker
        if (track.instrumental) {
            return Lyrics(
                lines = listOf(LyricsLine(null, "", isInstrumental = true)),
                isSynced = false,
                source = LyricsSource.LRCLIB,
                remoteUrl = "https://lrclib.net/lyrics/${track.id}",
                providerColor = LyricsSource.LRCLIB.accentColor,
            )
        }

        // Try synced lyrics first
        track.syncedLyrics?.let { lrcText ->
            if (lrcText.isNotBlank()) {
                val parsed = LrcParser.parse(lrcText)
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
        }

        // Fallback to plain lyrics
        track.plainLyrics?.let { plainText ->
            if (plainText.isNotBlank()) {
                return Lyrics(
                    lines = parsePlainText(plainText, LyricsSource.LRCLIB).lines,
                    isSynced = false,
                    source = LyricsSource.LRCLIB,
                    remoteUrl = "https://lrclib.net/lyrics/${track.id}",
                    providerColor = LyricsSource.LRCLIB.accentColor,
                )
            }
        }

        return null
    }

    // ──────────────────────────────────────────
    // Source 2: YouTube captions
    // ──────────────────────────────────────────

    /**
     * Fetch and parse YouTube timed text captions.
     *
     * The URL comes from InnerTube's player response:
     * ```
     * playerResponse.lyricsEndpoint.watchEndpointTimedTextTrackId
     * ```
     *
     * This is actually the full baseUrl from captionTracks[0],
     * which looks like:
     * ```
     * https://www.youtube.com/api/timedtext?v=xxx&exp=x&tlang=en&tk=xxx
     * ```
     *
     * We append `&fmt=srv3` to get the clean XML format.
     */
    private suspend fun fetchFromYouTube(captionBaseUrl: String): Lyrics? {
        return try {
            // The URL might already have query params
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

    /**
     * Merge auto-generated YouTube captions.
     *
     * Auto-generated captions are often word-by-word:
     * ```
     * [0:00.500] I
     * [0:00.600] will
     * [0:00.800] always
     * [0:01.100] love
     * [0:01.400] you
     * ```
     *
     * This merges consecutive short segments into natural lines
     * using heuristics:
     * - Merge words until a pause > 400ms or a punctuation mark
     * - The merged line's timestamp is the first word's timestamp
     */
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

            // Check for a gap (pause between words)
            val gap = time - lastTime
            val endsWithPunctuation = text.lastOrNull() in punctuation

            buffer.append(text)
            lastTime = time

            // Flush conditions:
            // 1. Large gap between this word and the next
            val nextTime = lines.getOrNull(i + 1)?.timeMs
            val nextGap = if (nextTime != null) nextTime - time else Long.MAX_VALUE

            if (endsWithPunctuation || nextGap > 400) {
                flush()
            } else {
                buffer.append(" ")
            }
        }

        // Flush remaining buffer
        flush()

        return merged
    }

    // ──────────────────────────────────────────
    // Source 3: Plain text (JioSaavn / fallback)
    // ──────────────────────────────────────────

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

    // ──────────────────────────────────────────
    // HTTP helper
    // ──────────────────────────────────────────

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

// ──────────────────────────────────────────────
// LRC format parser
// ──────────────────────────────────────────────

/**
 * Parses LRC (Lyrics RC) format text into timed [LyricsLine] objects.
 *
 * LRC format reference:
 * ```
 * [mm:ss.xx]Lyric text
 * [mm:ss.xxx]Lyric text
 * [<mm:ss.xx>Lyric text      (extended — backward offset)
 *
 * Metadata tags (ignored for display):
 * [ti:Title]
 * [ar:Artist]
 * [al:Album]
 * [by:Creator]
 * [offset:+/-ms]
 * ```
 *
 * Handles edge cases:
 * - Multiple timestamps per line (line repeated at different times)
 * - Decimal precision: 2 digits ([00:12.34]) or 3 digits ([00:12.345])
 * - Negative offsets
 * - Lines with no timestamp (treated as unsynced)
 * - Empty lines and whitespace
 */
internal object LrcParser {

    // Matches [mm:ss.xx] or [mm:ss.xxx] at the start of a line
    private val TIME_TAG_REGEX = Regex(
        """\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?\]"""
    )

    // Metadata tags
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

            // Check for metadata tag
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
                continue // Skip metadata lines from lyric output
            }

            // Extract all timestamps from this line
            val timestamps = TIME_TAG_REGEX.findAll(trimmed).toList()
            if (timestamps.isEmpty()) {
                // No timestamp — unsynced line
                lines.add(LyricsLine(timeMs = null, text = trimmed))
                continue
            }

            // The lyric text is everything after the last timestamp tag
            val lastMatchEnd = timestamps.last().range.last + 1
            val text = trimmed.substring(lastMatchEnd).trim()

            // Check for instrumental marker
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

            // A line can have multiple timestamps (same lyric at different times)
            timestamps.forEach { match ->
                val timeMs = parseTime(match) + metadataBuilder.offset
                if (timeMs < 0) {
                    errors++
                    return@forEach
                }
                lines.add(LyricsLine(timeMs = timeMs, text = text))
            }
        }

        // Sort by time, nulls last
        val sorted = lines.sortedWith(
            compareBy<LyricsLine> { it.timeMs ?: Long.MAX_VALUE }
        )

        return LrcParseResult(
            lines = sorted,
            metadata = metadataBuilder.build(),
            parseErrors = errors,
        )
    }

    /**
     * Parse a single [mm:ss.xx] or [mm:ss.xxx] match into milliseconds.
     *
     * Minutes can be 1-3 digits (for tracks > 59:59).
     * Seconds are always 2 digits.
     * Fractional part is 1-3 digits, interpreted as:
     * - 2 digits: centiseconds (e.g., .34 → 340ms)
     * - 3 digits: milliseconds (e.g., .345 → 345ms)
     * - 1 digit: treated as centiseconds × 100 (e.g., .3 → 300ms)
     */
    private fun parseTime(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLongOrNull() ?: 0
        val seconds = match.groupValues[2].toLongOrNull() ?: 0
        val fraction = match.groupValues[3]

        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLongOrNull()?.let { it * 100 } ?: 0L
            2 -> fraction.toLongOrNull()?.let { it * 10 } ?: 0L
            else -> fraction.toLongOrNull() ?: 0L // 3+ digits, take first 3 as ms
        }

        return (minutes * 60_000) + (seconds * 1000) + fractionMs
    }

    // Simple builder to avoid @Serializable overhead on internal type
    private class Builder {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var by: String? = null
        var offset: Long = 0

        fun build() = LrcMetadata(title, artist, album, by, offset)
    }
}

// ──────────────────────────────────────────────
// YouTube caption XML parser
// ──────────────────────────────────────────────

/**
 * Parses YouTube's timed text XML (srv3 format) into [LyricsLine] objects.
 *
 * Input XML structure:
 * ```xml
 * <transcript>
 *   <text start="0.5" dur="0.4">I</text>
 *   <text start="0.9" dur="0.5">will</text>
 *   <text start="1.4" dur="0.6">always</text>
 *   ...
 * </transcript>
 * ```
 *
 * Or for manual captions:
 * ```xml
 * <transcript>
 *   <text start="0.0" dur="3.5">Is this the real life?</text>
 *   <text start="3.5" dur="2.8">Is this just fantasy?</text>
 *   ...
 * </transcript>
 * ```
 *
 * Auto-generated captions have short durations (< 0.8s per segment)
 * and single words. Manual captions have longer durations and full lines.
 */
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

                // Move to text content
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

        // Heuristic: if average duration per segment < 800ms, it's auto-generated
        isAutoGenerated = if (count > 0) {
            (totalDuration / count) < 800
        } else {
            false
        }

        return YouTubeCaptionTrack(lines, isAutoGenerated)
    }
}
