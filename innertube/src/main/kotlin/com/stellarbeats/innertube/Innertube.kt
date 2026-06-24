package com.stellarbeats.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.TimeUnit

object Innertube {

    private const val BASE_URL = "https://music.youtube.com/youtubei/v1/"
    private const val CLIENT_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    private const val USER_AGENT = "com.google.android.apps.youtube.music/6.31.56 (Linux; U; Android 14; Pixel 7 Build/UQ1A.240205.004) gzip"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("X-Goog-Api-Format-Version", "2")
                .header("Accept", "application/json")
                .build())
        }.build()

    private val json = Json {
        ignoreUnknownKeys = true; isLenient = true
        coerceInputValues = true; encodeDefaults = true
    }

    var visitorId: String? = null
    var locale: Locale = Locale.getDefault()
        set(value) { field = value; context = buildContext() }

    private var context: InnertubeContext = buildContext()

    private fun buildContext() = InnertubeContext(client = InnertubeClient(hl = locale.language, gl = locale.country))

    private suspend fun post(endpoint: String, additionalParams: Map<String, Any?> = emptyMap()): JsonObject = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("context", json.encodeToJsonElement(InnertubeContext.serializer(), context))
            additionalParams.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Number -> put(key, value)
                    is Boolean -> put(key, value)
                    null -> {}
                    else -> put(key, json.encodeToJsonElement(JsonObject.serializer(), value as JsonObject))
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
            throw InnertubeException("HTTP ${response.code} ${response.message} for $endpoint")
        }
        val responseString = response.body?.string() ?: throw InnertubeException("Empty response from $endpoint")
        response.close()
        val parsed = json.parseToJsonElement(responseString).jsonObject
        parsed["responseContext"]?.jsonObject?.get("visitorData")
            ?.let { visitorId = it.toString().removeSurrounding("\"") }
        parsed
    }

    suspend fun home(): Result<HomeResult> = runCatching { InnertubeParser.parseHome(post("browse", mapOf("browseId" to "FEmusic_home"))) }
    suspend fun search(query: String, filter: SearchFilter? = null): Result<SearchResult> = runCatching {
        val params = mutableMapOf<String, Any?>("query" to query)
        filter?.let { params["params"] = it.value }
        InnertubeParser.parseSearch(post("search", params))
    }
    suspend fun player(videoId: String): Result<PlayerResponse> = runCatching {
        InnertubeParser.parsePlayer(post("player", mapOf("videoId" to videoId)))
    }
    suspend fun browse(browseId: String, params: String? = null, continuation: String? = null): Result<BrowseResult> = runCatching {
        val map = mutableMapOf<String, Any?>("browseId" to browseId)
        params?.let { map["params"] = it }
        continuation?.let { map["continuation"] = it }
        InnertubeParser.parseBrowse(post("browse", map))
    }
    suspend fun next(videoId: String, playlistId: String? = null, continuation: String? = null): Result<NextResult> = runCatching {
        val map = mutableMapOf<String, Any?>("videoId" to videoId)
        playlistId?.let { map["playlistId"] = it }
        continuation?.let { map["continuation"] = it }
        InnertubeParser.parseNext(post("next", map))
    }
    suspend fun moodPlaylists(): Result<List<MoodPlaylist>> = runCatching {
        InnertubeParser.parseMoodPlaylists(post("browse", mapOf("browseId" to "FEmusic_moods_and_genres")))
    }

    fun resolveUrl(url: String): Pair<ContentType, String>? {
        val uri = try { android.net.Uri.parse(url) } catch (_: Exception) { return null }
        uri.getQueryParameter("v")?.let { return ContentType.SONG to it }
        if (uri.host?.endsWith("youtu.be") == true) uri.lastPathSegment?.let { return ContentType.SONG to it }
        uri.getQueryParameter("list")?.let { return ContentType.PLAYLIST to it }
        uri.lastPathSegment?.let { segment ->
            return when {
                segment.startsWith("MPRE") -> ContentType.ALBUM to segment
                segment.startsWith("UC") -> ContentType.ARTIST to segment
                segment.startsWith("VL") || segment.startsWith("OL") || segment.startsWith("PL") || segment.startsWith("RD") -> ContentType.PLAYLIST to segment
                else -> null
            }
        }
        return null
    }
}
