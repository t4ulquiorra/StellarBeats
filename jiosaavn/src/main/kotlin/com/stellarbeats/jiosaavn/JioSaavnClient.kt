package com.stellarbeats.jiosaavn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * JioSaavn API client via meloapi.vercel.app.
 *
 * meloapi is a serverless proxy that wraps JioSaavn's internal API
 * and returns clean JSON. It handles JioSaavn's encryption, token
 * generation, and image URL decoding.
 *
 * All methods return `Result<T>` — never throw from the caller's
 * perspective. Errors are captured in `Result.failure`.
 *
 * Thread-safe. All requests run on Dispatchers.IO.
 */
object JioSaavnClient {

    private const val BASE_URL = "https://meloapi.vercel.app"

    /**
     * JioSaavn's CDN serves images with encoded URLs.
     * The meloapi typically returns decoded URLs, but we handle
     * both cases. This regex matches the encoded pattern.
     */
    private val IMAGE_URL_PATTERN = Regex("""150x150|500x500""")

    // ── HTTP client ─────────────────────────────

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "StellarBeats/1.0")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // ── Core request ────────────────────────────

    private suspend fun get(
        path: String,
        queryParams: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$BASE_URL$path")
            if (queryParams.isNotEmpty()) {
                append("?")
                append(queryParams.entries.joinToString("&") { (k, v) ->
                    "${k}=${URLEncoder.encode(v, "UTF-8")}"
                })
            }
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            response.body?.close()
            throw JioSaavnApiException(
                response.code,
                response.message.ifBlank { "HTTP ${response.code}" }
            )
        }

        val body = response.body?.string()
            ?: throw JioSaavnApiException(response.code, "Empty response body")

        response.close()
        body
    }

    private inline fun <reified T> decode(jsonString: String): T {
        return try {
            json.decodeFromString<T>(jsonString)
        } catch (e: Exception) {
            throw JioSaavnParseException("Failed to decode ${T::class.simpleName}", e)
        }
    }

    // ── Public API ──────────────────────────────

    /**
     * Fetch home page data: trending, playlists, albums, artists, charts.
     */
    suspend fun home(): Result<ParsedHome> = runCatching {
        val raw = get("/home")
        val response = decode<JioSaavnResponse<JioSaavnHomeData>>(raw)
        val data = response.data

        ParsedHome(
            trending = data.trending.orEmpty().map { it.toParsed() },
            topPlaylists = data.topPlaylists.orEmpty().map { it.toParsed() },
            newAlbums = data.newAlbums.orEmpty().map { it.toParsed(data.topArtists) },
            charts = data.charts.orEmpty().map { it.toParsed() },
            topArtists = data.topArtists.orEmpty().map { it.toParsed() },
        )
    }

    /**
     * Search across all content types.
     *
     * @param query Search string
     * @param page 1-based page number
     * @param limit Results per page (max ~50)
     */
    suspend fun search(
        query: String,
        page: Int = 1,
        limit: Int = 20,
    ): Result<ParsedSearchResult> = runCatching {
        val raw = get("/search/songs", mapOf(
            "query" to query,
            "page" to page.toString(),
            "limit" to limit.toString(),
        ))
        val response = decode<JioSaavnListResponse<JioSaavnSong>>(raw)
        val songs = response.data.map { it.toParsed() }

        // For a full search, you'd also hit /search/albums, /search/artists, /search/playlists
        // but meloapi's /search/songs is the most useful. Additional calls can be made
        // lazily when the user switches tabs.
        ParsedSearchResult(songs = songs)
    }

    /**
     * Search specifically for albums.
     */
    suspend fun searchAlbums(
        query: String,
        page: Int = 1,
        limit: Int = 20,
    ): Result<List<ParsedAlbum>> = runCatching {
        val raw = get("/search/albums", mapOf(
            "query" to query,
            "page" to page.toString(),
            "limit" to limit.toString(),
        ))
        val response = decode<JioSaavnListResponse<JioSaavnAlbum>>(raw)
        response.data.map { it.toParsed() }
    }

    /**
     * Search specifically for artists.
     */
    suspend fun searchArtists(
        query: String,
        page: Int = 1,
        limit: Int = 20,
    ): Result<List<ParsedArtist>> = runCatching {
        val raw = get("/search/artists", mapOf(
            "query" to query,
            "page" to page.toString(),
            "limit" to limit.toString(),
        ))
        val response = decode<JioSaavnListResponse<JioSaavnArtist>>(raw)
        response.data.map { it.toParsed() }
    }

    /**
     * Search specifically for playlists.
     */
    suspend fun searchPlaylists(
        query: String,
        page: Int = 1,
        limit: Int = 20,
    ): Result<List<ParsedPlaylist>> = runCatching {
        val raw = get("/search/playlists", mapOf(
            "query" to query,
            "page" to page.toString(),
            "limit" to limit.toString(),
        ))
        val response = decode<JioSaavnListResponse<JioSaavnPlaylist>>(raw)
        response.data.map { it.toParsed() }
    }

    /**
     * Get full details for a single song.
     *
     * Returns the highest quality stream URL available.
     */
    suspend fun song(id: String): Result<ParsedSong> = runCatching {
        val raw = get("/songs/$id")
        // Some endpoints return an array, some a single object
        val parsed = json.parseToJsonElement(raw)

        val songJson = when {
            parsed is JsonObject -> parsed
            parsed.jsonArray.isNotEmpty() -> parsed.jsonArray[0].jsonObject
            else -> throw JioSaavnParseException("Unexpected response shape for song $id")
        }

        val song = json.decodeFromJsonElement<JioSaavnSong>(songJson)
        song.toParsed()
    }

    /**
     * Get full album details with all songs.
     */
    suspend fun album(id: String): Result<ParsedAlbum> = runCatching {
        val raw = get("/albums/$id")
        val response = decode<JioSaavnResponse<JioSaavnAlbum>>(raw)
        response.data.toParsed()
    }

    /**
     * Get full playlist details with all songs.
     */
    suspend fun playlist(id: String): Result<ParsedPlaylist> = runCatching {
        val raw = get("/playlists/$id")
        val response = decode<JioSaavnResponse<JioSaavnPlaylist>>(raw)
        response.data.toParsed()
    }

    /**
     * Get artist details: bio, top songs, top albums.
     */
    suspend fun artist(id: String): Result<ParsedArtist> = runCatching {
        val raw = get("/artists/$id")
        val response = decode<JioSaavnResponse<JioSaavnArtist>>(raw)
        response.data.toParsed()
    }

    /**
     * Get lyrics for a song by its JioSaavn ID.
     *
     * Returns the raw lyrics text. Timestamp-synced lyrics
     * from JioSaavn are rare — use the [lyrics] module for
     * LRC-synced lyrics from LRCLIB as primary source.
     */
    suspend fun lyrics(id: String): Result<String?> = runCatching {
        val raw = get("/lyrics/$id")
        val response = decode<JioSaavnResponse<JioSaavnLyrics>>(raw)
        response.data.lyrics
    }

    // ── Parsers: raw → clean models ─────────────

    private fun JioSaavnSong.toParsed(): ParsedSong {
        // Pick the highest quality download URL
        // JioSaavn quality labels: "320kbps", "160kbps", "96kbps"
        val sortedDownloads = downloadUrl.orEmpty()
            .filter { it.link != null }
            .sortedByDescending { qualityRank(it.quality) }

        val bestStream = sortedDownloads.firstOrNull()

        return ParsedSong(
            id = id,
            title = cleanText(name),
            artists = parseArtistList(primaryArtists, primaryArtistsId),
            album = album?.let { cleanText(it) },
            albumId = albumId,
            duration = duration,
            year = year?.toIntOrNull(),
            language = language,
            hasLyrics = hasLyrics == true,
            lyricsId = lyricsId,
            playCount = playCount?.parseCount(),
            thumbnailUrl = resolveImageUrl(image?.url, "500x500"),
            streamUrl = bestStream?.link,
            streamQuality = bestStream?.quality,
            explicit = explicitContent == true,
        )
    }

    private fun JioSaavnAlbum.toParsed(
        artistLookup: List<JioSaavnArtist>? = null,
    ): ParsedAlbum {
        val artists = parseArtistList(primaryArtists, primaryArtistsId)
        return ParsedAlbum(
            id = id,
            title = cleanText(name),
            year = year?.toIntOrNull(),
            songCount = songCount,
            artists = artists,
            thumbnailUrl = resolveImageUrl(image?.url, "500x500"),
            songs = songs.orEmpty().map { it.toParsed() },
            url = url,
        )
    }

    private fun JioSaavnPlaylist.toParsed(): ParsedPlaylist {
        return ParsedPlaylist(
            id = id,
            title = cleanText(name),
            songCount = songCount,
            followerCount = followerCount?.parseCount(),
            authorName = buildString {
                firstname?.let { append(it) }
                lastname?.let { if (isNotEmpty()) append(" ") ; append(it) }
            }.ifBlank { null },
            thumbnailUrl = resolveImageUrl(image?.url, "500x500"),
            songs = songs.orEmpty().map { it.toParsed() },
            url = url,
        )
    }

    private fun JioSaavnArtist.toParsed(): ParsedArtist {
        return ParsedArtist(
            id = id,
            name = cleanText(name),
            thumbnailUrl = resolveImageUrl(image?.url, "500x500"),
            followerCount = (followerCount ?: fanCount)?.parseCount(),
            description = description,
            topSongs = topSongs.orEmpty().map { it.toParsed() },
            topAlbums = topAlbums.orEmpty().map { it.toParsed() },
            dedicatedPlaylist = dedicatedPlaylist?.toParsed(),
        )
    }

    // ── Utility functions ───────────────────────

    /**
     * JioSaavn sometimes returns text with HTML entities or encoded characters.
     * Clean up common issues.
     */
    private fun cleanText(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .trim()
    }

    /**
     * Parse "Artist1, Artist2" + "123,456" into structured refs.
     *
     * JioSaavn returns comma-separated artist names and IDs.
     * They should pair 1:1 by position.
     */
    private fun parseArtistList(
        names: String?,
        ids: String?,
    ): List<ParsedSong.ArtistRef> {
        if (names.isNullOrBlank()) return emptyList()

        val nameList = names.split(", ").map { it.trim() }.filter { it.isNotBlank() }
        val idList = ids?.split(", ")?.map { it.trim() } ?: emptyList()

        return nameList.mapIndexed { index, name ->
            ParsedSong.ArtistRef(
                name = name,
                id = idList.getOrNull(index),
            )
        }
    }

    /**
     * Resolve and normalize image URLs.
     *
     * JioSaavn images sometimes come with size prefixes or encoded paths.
     * meloapi usually handles this, but we sanitize just in case.
     */
    private fun resolveImageUrl(url: String?, preferredSize: String = "500x500"): String? {
        if (url.isNullOrBlank()) return null

        // If URL already has a size spec, replace with preferred
        return if (IMAGE_URL_PATTERN.containsMatchIn(url)) {
            url.replace(IMAGE_URL_PATTERN, preferredSize)
        } else {
            url
        }
    }

    /**
     * Rank stream quality for selection.
     * 320kbps > 160kbps > 96kbps > unknown
     */
    private fun qualityRank(quality: String?): Int {
        return when (quality?.lowercase()?.replace("kbps", "")?.trim()?.toIntOrNull()) {
            320 -> 3
            160 -> 2
            128 -> 1
            96 -> 0
            else -> -1
        }
    }

    /**
     * Parse JioSaavn's count strings like "10234567" or "1.2M" into a Long.
     */
    private fun String.parseCount(): Long {
        val trimmed = trim()
        return when {
            trimmed.endsWith("M", ignoreCase = true) ->
                (trimmed.dropLast(1).toDoubleOrNull() ?: 0.0) * 1_000_000
            trimmed.endsWith("K", ignoreCase = true) ->
                (trimmed.dropLast(1).toDoubleOrNull() ?: 0.0) * 1_000
            trimmed.endsWith("B", ignoreCase = true) ->
                (trimmed.dropLast(1).toDoubleOrNull() ?: 0.0) * 1_000_000_000
            else -> trimmed.toLongOrNull() ?: 0L
        }.toLong()
    }
}
