package com.stellarbeats.innertube

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

// ──────────────────────────────────────────────
// Request context (serialized to JSON)
// ──────────────────────────────────────────────

@Serializable
data class InnertubeContext(
    @SerialName("client") val client: InnertubeClient,
    @SerialName("user") val user: InnertubeUser = InnertubeUser(),
    @SerialName("request") val request: InnertubeRequest = InnertubeRequest(),
)

@Serializable
data class InnertubeClient(
    @SerialName("clientName") val clientName: String = "ANDROID_MUSIC",
    @SerialName("clientVersion") val clientVersion: String = "6.31.56",
    @SerialName("androidSdkVersion") val androidSdkVersion: Int = 34,
    @SerialName("hl") val hl: String = Locale.getDefault().language,
    @SerialName("gl") val gl: String = Locale.getDefault().country,
    @SerialName("deviceModel") val deviceModel: String = "Pixel 7",
    @SerialName("osName") val osName: String = "Android",
    @SerialName("osVersion") val osVersion: String = "14",
    @SerialName("package") val packageName: String = "com.google.android.apps.youtube.music",
)

@Serializable
data class InnertubeUser(
    @SerialName("lockedSafetyMode") val lockedSafetyMode: Boolean = false,
)

@Serializable
data class InnertubeRequest(
    @SerialName("internalExperimentFlags") val internalExperimentFlags: List<String> = emptyList(),
    @SerialName("useSsl") val useSsl: Boolean = true,
)

// ──────────────────────────────────────────────
// Response models (built by parser, not deserialized)
// ──────────────────────────────────────────────

enum class SearchFilter(val value: String) {
    SONGS("EgWKAQIIAWoKEAkQBRAKEAMQBQ"),
    VIDEOS("EgWKAQIQAWoKEAkQChAEEAMQBQ"),
    ALBUMS("EgWKAQIYAWoKEAkQChAFEAQ QBQ"),
    ARTISTS("EgWKAQIgAWoKEAkQChAEEAUQBQ"),
    PLAYLISTS("EgWKAQIoAWoKEAkQChAEEAMQBQ"),
}

enum class ContentType {
    SONG, VIDEO, ALBUM, ARTIST, PLAYLIST, PROFILE, UNKNOWN
}

data class NavigationEndpoint(
    val type: ContentType,
    val browseId: String? = null,
    val videoId: String? = null,
    val playlistId: String? = null,
    val params: String? = null,
    val watchEndpointTimedTextTrackId: String? = null,
)

data class Thumbnail(
    val url: String,
    val width: Int,
    val height: Int,
)

data class SearchResult(
    val items: List<SearchItem>,
    val continuation: String?,
)

sealed class SearchItem {
    abstract val id: String
    abstract val title: String
    abstract val thumbnail: Thumbnail?
    abstract val endpoint: NavigationEndpoint?
    abstract val subtitle: String?

    data class Song(
        override val id: String,          // videoId
        override val title: String,
        override val thumbnail: Thumbnail?,
        override val endpoint: NavigationEndpoint?,
        override val subtitle: String?,    // "Artist • Album • Duration"
        val artists: List<SearchItem.Artist>,
        val album: SearchItem.Album?,
        val duration: Int?,                // seconds
    ) : SearchItem()

    data class Album(
        override val id: String,          // browseId (MPRE...)
        override val title: String,
        override val thumbnail: Thumbnail?,
        override val endpoint: NavigationEndpoint?,
        override val subtitle: String?,    // "Year • Songs count"
        val year: Int?,
        val songCount: Int?,
        val artists: List<SearchItem.Artist>,
    ) : SearchItem()

    data class Artist(
        override val id: String,          // browseId (UC...)
        override val title: String,
        override val thumbnail: Thumbnail?,
        override val endpoint: NavigationEndpoint?,
        override val subtitle: String?,    // subscriber count or genre
    ) : SearchItem()

    data class Playlist(
        override val id: String,          // playlistId (VL... or OL...)
        override val title: String,
        override val thumbnail: Thumbnail?,
        override val endpoint: NavigationEndpoint?,
        override val subtitle: String?,    // "Creator • Song count"
        val songCount: Int?,
        val author: String?,
    ) : SearchItem()

    data class Unknown(
        override val id: String = "",
        override val title: String = "",
        override val thumbnail: Thumbnail? = null,
        override val endpoint: NavigationEndpoint? = null,
        override val subtitle: String? = null,
    ) : SearchItem()
}

data class PlayerResponse(
    val videoId: String,
    val title: String,
    val artist: String,
    val artistEndpoint: NavigationEndpoint?,
    val album: String?,
    val albumEndpoint: NavigationEndpoint?,
    val thumbnail: Thumbnail?,
    val durationSeconds: Int,
    val streams: List<AudioStream>,
    val lyricsEndpoint: NavigationEndpoint?,
    val relatedEndpoint: NavigationEndpoint?,
)

data class AudioStream(
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val sampleRate: Int,
    val channels: Int,
    val contentLength: Long?,
    val url: String,
)

data class PlayabilityStatus(
    val status: String,
    val reason: String?,
    val isPlayable: Boolean,
)

data class BrowseResult(
    val title: String?,
    val items: List<BrowseItem>,
    val continuation: String?,
)

sealed class BrowseItem {
    abstract val id: String
    abstract val title: String
    abstract val thumbnail: Thumbnail?
    abstract val endpoint: NavigationEndpoint?
    abstract val subtitle: String?

    data class Song(
        override val id: String,
        override val title: String,
        override val thumbnail: Thumbnail?,
        override val endpoint: NavigationEndpoint?,
        override val subtitle: String?,
        val artists: List<SearchItem.Artist>,
        val album: SearchItem.Album?,
        val duration: Int?,
        val videoId: String,
    ) : BrowseItem()

    data class Album(
        override val id: String,
        override val title: String,
        override val thumbnail: Thumbnail?,
        override val endpoint: NavigationEndpoint?,
        override val subtitle: String?,
        val year: Int?,
        val artists: List<SearchItem.Artist>,
    ) : BrowseItem()

    data class Artist(
        override val id: String,
        override val title: String,
        override val thumbnail: Thumbnail?,
        override val endpoint: NavigationEndpoint?,
        override val subtitle: String?,
    ) : BrowseItem()

    data class Playlist(
        override val id: String,
        override val title: String,
        override val thumbnail: Thumbnail?,
        override val endpoint: NavigationEndpoint?,
        override val subtitle: String?,
        val songCount: Int?,
    ) : BrowseItem()

    data class Unknown(
        override val id: String = "",
        override val title: String = "",
        override val thumbnail: Thumbnail? = null,
        override val endpoint: NavigationEndpoint? = null,
        override val subtitle: String? = null,
    ) : BrowseItem()
}

data class HomeResult(
    val sections: List<HomeSection>,
    val continuation: String?,
)

data class HomeSection(
    val title: String,
    val items: List<SearchItem>,
    val endpoint: NavigationEndpoint?,
    val continuation: String?,
)

data class NextResult(
    val items: List<BrowseItem.Song>,
    val autoplayVideoId: String?,
    val continuation: String?,
    val lyricsEndpoint: NavigationEndpoint?,
)

data class MoodPlaylist(
    val title: String,
    val thumbnail: Thumbnail?,
    val endpoint: NavigationEndpoint?,
    val params: String?,
)

// ──────────────────────────────────────────────
// Exceptions
// ──────────────────────────────────────────────

open class InnertubeException(message: String, cause: Throwable? = null) : Exception(message, cause)

class PlayabilityException(
    val status: String,
    val reason: String,
) : InnertubeException("Playability error: $status — $reason")

class StreamException(message: String, cause: Throwable? = null) : InnertubeException(message, cause)
