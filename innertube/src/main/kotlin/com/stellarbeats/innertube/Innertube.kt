package com.stellarbeats.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * YouTube Music InnerTube API client.
 *
 * Directly communicates with youtubei/v1 endpoints — no NewPipe Extractor
 * dependency. This gives full control over Music-specific features like
 * mood playlists, home screen sections, and queue management that generic
 * extractors don't expose well.
 *
 * Thread-safe. All requests are suspend functions that run on Dispatchers.IO.
 *
 * Usage:
 * ```kotlin
 * val result = Innertube.search("Bohemian Rhapsody", SearchFilter.SONGS)
 * result.onSuccess { songs -> songs.items.filterIsInstance<SearchItem.Song>() }
 * ```
 */
object Innertube {

    private const val BASE_URL = "https://music.youtube.com/youtubei/v1/"
    private const val CLIENT_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

    /**
     * Public key embedded in every YouTube Music Android client.
     * Not a secret — it's the client identifier, identical across all
     * YT Music installations. Extracted from the APK's smali code.
     */
    private const val USER_AGENT =
        "com.google.android.apps.youtube.music/6.31.56 " +
        "(Linux; U; Android 14; Pixel 7 Build/UQ1A.240205.004) gzip"

    // ── HTTP client ─────────────────────────────

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("X-Goog-Api-Format-Version", "2")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    // ── Mutable state ───────────────────────────

    /**
     * Anonymous visitor ID. Generated on first use and should be persisted
     * across sessions. YouTube uses this for anonymous personalization.
     * Without it, some endpoints return less relevant results.
     */
    var visitorId: String? = null

    /** Override locale. Defaults to device locale. */
    var locale: Locale = Locale.getDefault()
        set(value) {
            field = value
            context = buildContext()
        }

    private var context: InnertubeContext = buildContext()

    private fun buildContext() = InnertubeContext(
        client = InnertubeClient(
            hl = locale.language,
            gl = locale.country,
        ),
    )

    // ── Core request ────────────────────────────

    private suspend fun post(
        endpoint: String,
        additionalParams: Map<String, Any?> = emptyMap(),
    ): JsonObject = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonElement("context", json.encodeToJsonElement(InnertubeContext.serializer(), context))
            additionalParams.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Number -> put(key, value)
                    is Boolean -> put(key, value)
                    null -> { /* skip */ }
                    else -> putJsonElement(key, json.encodeToJsonElement(value))
                }
            }
        }

        val request = Request.Builder()
            .url("$BASE_URL$endpoint?key=$CLIENT_KEY")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            response.body?.close()
            throw InnertubeException(
                "HTTP ${response.code} ${response.message} for $endpoint"
            )
        }

        val responseString = response.body?.string()
            ?: throw InnertubeException("Empty response body from $endpoint")

        response.close()

        val parsed = json.parseToJsonElement(responseString).jsonObject

        // Capture visitor ID if returned
        parsed["responseContext"]?.jsonObject
            ?.get("visitorData")
            ?.let { visitorId = it.toString().removeSurrounding("\"") }

        parsed
    }

    // ── Public API ──────────────────────────────

    /**
     * Fetch the YouTube Music home screen.
     *
     * Returns sections like "Quick picks", "Trending", "Recommended albums",
     * "Mood playlists", etc. The exact sections vary by region and time.
     */
    suspend fun home(): Result<HomeResult> = runCatching {
        val raw = post("browse", mapOf("browseId" to "FEmusic_home"))
        InnertubeParser.parseHome(raw)
    }

    /**
     * Search YouTube Music.
     *
     * @param query Search string
     * @param filter Optional content type filter. null returns mixed results.
     */
    suspend fun search(
        query: String,
        filter: SearchFilter? = null,
    ): Result<SearchResult> = runCatching {
        val params = mutableMapOf<String, Any?>("query" to query)
        filter?.let { params["params"] = it.value }
        val raw = post("search", params)
        InnertubeParser.parseSearch(raw)
    }

    /**
     * Get playback info for a video/song.
     *
     * Returns stream URLs, metadata, and endpoints for lyrics/related content.
     * The highest-bitrate audio stream is listed first.
     *
     * @param videoId YouTube video ID
     * @throws PlayabilityException if the video is unavailable/restricted
     * @throws StreamException if no audio streams can be extracted
     */
    suspend fun player(videoId: String): Result<PlayerResponse> = runCatching {
        val raw = post("player", mapOf("videoId" to videoId))
        InnertubeParser.parsePlayer(raw)
    }

    /**
     * Browse an artist, album, playlist, or category page.
     *
     * @param browseId InnerTube browse ID (e.g., "MPRE..." for albums,
     *                 "UC..." for artists, "VL..." for playlists,
     *                 "FEmusic_moods_and_genres" for moods)
     * @param params Optional params for filtered/mood browsing
     * @param continuation Pagination token from a previous response
     */
    suspend fun browse(
        browseId: String,
        params: String? = null,
        continuation: String? = null,
    ): Result<BrowseResult> = runCatching {
        val map = mutableMapOf<String, Any?>("browseId" to browseId)
        params?.let { map["params"] = it }
        continuation?.let { map["continuation"] = it }
        val raw = post("browse", map)
        InnertubeParser.parseBrowse(raw)
    }

    /**
     * Get the queue / "up next" for a song, plus lyrics endpoint.
     *
     * Called when a song starts playing to populate the queue.
     *
     * @param videoId Currently playing video ID
     * @param playlistId If playing from a playlist, its ID for context
     * @param continuation Pagination token for loading more queue items
     */
    suspend fun next(
        videoId: String,
        playlistId: String? = null,
        continuation: String? = null,
    ): Result<NextResult> = runCatching {
        val map = mutableMapOf<String, Any?>("videoId" to videoId)
        playlistId?.let { map["playlistId"] = it }
        continuation?.let { map["continuation"] = it }
        val raw = post("next", map)
        InnertubeParser.parseNext(raw)
    }

    /**
     * Load mood/genre playlist categories.
     *
     * These are the top-level mood entries (e.g., "Chill", "Energy",
     * "Focus", "Romance") that expand into sub-playlists when browsed.
     */
    suspend fun moodPlaylists(): Result<List<MoodPlaylist>> = runCatching {
        val raw = post("browse", mapOf("browseId" to "FEmusic_moods_and_genres"))
        InnertubeParser.parseMoodPlaylists(raw)
    }

    /**
     * Continue a paginated request using a continuation token.
     *
     * Works for search, browse, and home continuations.
     */
    suspend fun continueRequest(continuation: String): Result<SearchResult> = runCatching {
        val raw = post("browse", mapOf("continuation" to continuation))
        // Continuation responses have the same structure as search
        InnertubeParser.parseSearch(raw)
    }

    /**
     * Resolve a YouTube share URL to a video ID.
     *
     * Handles formats:
     * - https://music.youtube.com/watch?v=xxx
     * - https://youtu.be/xxx
     * - https://www.youtube.com/watch?v=xxx
     * - https://music.youtube.com/playlist?list=xxx
     */
    fun resolveUrl(url: String): Pair<ContentType, String>? {
        val uri = try {
            android.net.Uri.parse(url)
        } catch (_: Exception) {
            return null
        }

        // Direct video ID
        uri.getQueryParameter("v")?.let {
            return ContentType.SONG to it
        }

        // youtu.be short URL
        if (uri.host?.endsWith("youtu.be") == true) {
            uri.lastPathSegment?.let {
                return ContentType.SONG to it
            }
        }

        // Playlist ID
        uri.getQueryParameter("list")?.let {
            return ContentType.PLAYLIST to it
        }

        // Browse ID from music.youtube.com paths
        uri.lastPathSegment?.let { segment ->
            return when {
                segment.startsWith("MPRE") -> ContentType.ALBUM to segment
                segment.startsWith("UC") -> ContentType.ARTIST to segment
                segment.startsWith("VL") || segment.startsWith("OL") ||
                    segment.startsWith("PL") || segment.startsWith("RD") ->
                    ContentType.PLAYLIST to segment
                else -> null
            }
        }

        return null
    }
}
