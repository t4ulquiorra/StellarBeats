package com.stellarbeats.jiosaavn

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ──────────────────────────────────────────────
// API response wrappers
// ──────────────────────────────────────────────

/**
 * Generic paginated response from meloapi.
 *
 * meloapi wraps JioSaavn's internal API and returns JSON.
 * The structure is typically:
 * ```json
 * {
 *   "success": true,
 *   "data": { ... },
 *   "total": 100,
 *   "start": 0,
 *   "results": 20
 * }
 * ```
 *
 * But the `data` field shape varies per endpoint — sometimes it's an
 * object with nested arrays, sometimes a direct array. We handle both.
 */
@Serializable
data class JioSaavnResponse<T>(
    @SerialName("success") val success: Boolean,
    @SerialName("data") val data: T,
    @SerialName("total") val total: Int? = null,
    @SerialName("start") val start: Int? = null,
    @SerialName("results") val results: Int? = null,
)

/** Wrapper for endpoints that return an array directly under `data`. */
@Serializable
data class JioSaavnListResponse<T>(
    @SerialName("success") val success: Boolean,
    @SerialName("data") val data: List<T>,
    @SerialName("total") val total: Int? = null,
    @SerialName("start") val start: Int? = null,
    @SerialName("results") val results: Int? = null,
)

// ──────────────────────────────────────────────
// Song models
// ──────────────────────────────────────────────

@Serializable
data class JioSaavnSong(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("type") val type: String? = null,
    @SerialName("year") val year: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("duration") val duration: Int? = null,
    @SerialName("label") val label: String? = null,
    @SerialName("explicitContent") val explicitContent: Boolean? = null,
    @SerialName("copyright") val copyright: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("primaryArtists") val primaryArtists: String? = null,
    @SerialName("primaryArtistsId") val primaryArtistsId: String? = null,
    @SerialName("featuredArtists") val featuredArtists: String? = null,
    @SerialName("featuredArtistsId") val featuredArtistsId: String? = null,
    @SerialName("album") val album: String? = null,
    @SerialName("albumId") val albumId: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("hasLyrics") val hasLyrics: Boolean? = null,
    @SerialName("lyricsId") val lyricsId: String? = null,
    @SerialName("playCount") val playCount: String? = null,
    @SerialName("vcode") val vcode: String? = null,
    // Image URLs — JioSaavn provides multiple sizes
    @SerialName("image") val image: JioSaavnImage? = null,
    @SerialName("downloadUrl") val downloadUrl: List<JioSaavnDownloadUrl>? = null,
)

@Serializable
data class JioSaavnImage(
    @SerialName("quality") val quality: String? = null,
    @SerialName("url") val url: String? = null,
)

@Serializable
data class JioSaavnDownloadUrl(
    @SerialName("quality") val quality: String? = null,
    @SerialName("link") val link: String? = null,
)

/**
 * Parsed, normalized song ready for the player.
 * Extracted from the raw API response with cleaned-up fields.
 */
data class ParsedSong(
    val id: String,
    val title: String,
    val artists: List<ArtistRef>,
    val album: String?,
    val albumId: String?,
    val duration: Int?,          // seconds
    val year: Int?,
    val language: String?,
    val hasLyrics: Boolean,
    val lyricsId: String?,
    val playCount: Long?,
    val thumbnailUrl: String?,
    val streamUrl: String?,       // highest quality download URL
    val streamQuality: String?,
    val explicit: Boolean,
    val source: String = "jiosaavn",
) {
    data class ArtistRef(
        val name: String,
        val id: String?,
    )
}

// ──────────────────────────────────────────────
// Album models
// ──────────────────────────────────────────────

@Serializable
data class JioSaavnAlbum(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("year") val year: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("songCount") val songCount: Int? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("primaryArtists") val primaryArtists: String? = null,
    @SerialName("primaryArtistsId") val primaryArtistsId: String? = null,
    @SerialName("label") val label: String? = null,
    @SerialName("copyright") val copyright: String? = null,
    @SerialName("image") val image: JioSaavnImage? = null,
    @SerialName("songs") val songs: List<JioSaavnSong>? = null,
    @SerialName("url") val url: String? = null,
)

data class ParsedAlbum(
    val id: String,
    val title: String,
    val year: Int?,
    val songCount: Int?,
    val artists: List<ParsedSong.ArtistRef>,
    val thumbnailUrl: String?,
    val songs: List<ParsedSong>,
    val url: String?,
)

// ──────────────────────────────────────────────
// Playlist models
// ──────────────────────────────────────────────

@Serializable
data class JioSaavnPlaylist(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("type") val type: String? = null,
    @SerialName("followerCount") val followerCount: String? = null,
    @SerialName("songCount") val songCount: Int? = null,
    @SerialName("firstname") val firstname: String? = null,
    @SerialName("lastname") val lastname: String? = null,
    @SerialName("image") val image: JioSaavnImage? = null,
    @SerialName("songs") val songs: List<JioSaavnSong>? = null,
    @SerialName("url") val url: String? = null,
)

data class ParsedPlaylist(
    val id: String,
    val title: String,
    val songCount: Int?,
    val followerCount: Long?,
    val authorName: String?,
    val thumbnailUrl: String?,
    val songs: List<ParsedSong>,
    val url: String?,
)

// ──────────────────────────────────────────────
// Artist models
// ──────────────────────────────────────────────

@Serializable
data class JioSaavnArtist(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("role") val role: String? = null,
    @SerialName("image") val image: JioSaavnImage? = null,
    @SerialName("followerCount") val followerCount: String? = null,
    @SerialName("fanCount") val fanCount: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("topSongs") val topSongs: List<JioSaavnSong>? = null,
    @SerialName("topAlbums") val topAlbums: List<JioSaavnAlbum>? = null,
    @SerialName("dedicatedArtistPlaylist") val dedicatedPlaylist: JioSaavnPlaylist? = null,
)

data class ParsedArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val followerCount: Long?,
    val description: String?,
    val topSongs: List<ParsedSong>,
    val topAlbums: List<ParsedAlbum>,
    val dedicatedPlaylist: ParsedPlaylist?,
)

// ──────────────────────────────────────────────
// Lyrics
// ──────────────────────────────────────────────

@Serializable
data class JioSaavnLyrics(
    @SerialName("lyrics") val lyrics: String? = null,
    @SerialName("snippet") val snippet: String? = null,
    @SerialName("copyright") val copyright: String? = null,
    @SerialName("lyricsCensored") val lyricsCensored: Boolean? = null,
)

// ──────────────────────────────────────────────
// Home / Trending
// ──────────────────────────────────────────────

/**
 * meloapi's home endpoint typically returns sections:
 * - trending songs
 * - top playlists
 * - new albums
 * - charts
 * etc.
 *
 * The exact shape depends on the API version. We use a flexible
 * approach with nullable fields.
 */
@Serializable
data class JioSaavnHomeData(
    @SerialName("trending") val trending: List<JioSaavnSong>? = null,
    @SerialName("topPlaylists") val topPlaylists: List<JioSaavnPlaylist>? = null,
    @SerialName("newAlbums") val newAlbums: List<JioSaavnAlbum>? = null,
    @SerialName("charts") val charts: List<JioSaavnPlaylist>? = null,
    @SerialName("topArtists") val topArtists: List<JioSaavnArtist>? = null,
    @SerialName("promoted") val promoted: List<JioSaavnSong>? = null,
)

data class ParsedHome(
    val trending: List<ParsedSong>,
    val topPlaylists: List<ParsedPlaylist>,
    val newAlbums: List<ParsedAlbum>,
    val charts: List<ParsedPlaylist>,
    val topArtists: List<ParsedArtist>,
)

// ──────────────────────────────────────────────
// Search
// ──────────────────────────────────────────────

data class ParsedSearchResult(
    val songs: List<ParsedSong> = emptyList(),
    val albums: List<ParsedAlbum> = emptyList(),
    val artists: List<ParsedArtist> = emptyList(),
    val playlists: List<ParsedPlaylist> = emptyList(),
)

// ──────────────────────────────────────────────
// Exceptions
// ──────────────────────────────────────────────

open class JioSaavnException(message: String, cause: Throwable? = null) : Exception(message, cause)

class JioSaavnApiException(
    val statusCode: Int,
    override val message: String,
) : JioSaavnException("API error $statusCode: $message")

class JioSaavnParseException(message: String, cause: Throwable? = null) : JioSaavnException(message, cause)
