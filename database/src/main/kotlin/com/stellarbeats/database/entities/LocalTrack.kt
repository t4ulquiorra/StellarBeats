package com.stellarbeats.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The central track entity — the single source of truth for any track
 * the user has interacted with, regardless of its origin.
 *
 * This normalizes data from InnerTube (YouTube Music) and JioSaavn
 * into one schema. Every other table references tracks by [trackId].
 *
 * A track enters the database when the user:
 * - Plays it (play history)
 * - Adds it to a playlist
 * - Downloads it for offline
 * - Likes/favorites it
 *
 * It does NOT enter the database just from appearing in search results.
 * This keeps the DB lean — it's a *user library*, not a cache.
 *
 * ## ID scheme
 * - YouTube: `"yt:{videoId}"` — e.g., `"yt:dQw4w9WgXcQ"`
 * - JioSaavn: `"js:{songId}"` — e.g., `"js:123456"`
 *
 * This prevents collisions between sources and makes it trivial
 * to determine which source to use for playback.
 */
@Entity(
    tableName = "local_tracks",
    indices = [
        Index(value = ["title"], name = "idx_tracks_title"),
        Index(value = ["album_id"], name = "idx_tracks_album"),
        Index(value = ["source"], name = "idx_tracks_source"),
        Index(value = ["date_added"], name = "idx_tracks_date_added"),
        Index(value = ["date_played"], name = "idx_tracks_date_played"),
        Index(value = ["liked"], name = "idx_tracks_liked"),
    ],
)
data class LocalTrack(
    /** Composite ID: "yt:{videoId}" or "js:{songId}" */
    @PrimaryKey
    @ColumnInfo(name = "track_id")
    val trackId: String,

    // ── Identity ────────────────────────────

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "artists_json")
    val artistsJson: String, // JSON array of ArtistRef: [{"name":"Queen","id":"UC..."}, ...]

    @ColumnInfo(name = "album")
    val album: String?,

    @ColumnInfo(name = "album_id")
    val albumId: String?,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long?,

    @ColumnInfo(name = "year")
    val year: Int?,

    @ColumnInfo(name = "language")
    val language: String?,

    // ── Artwork ─────────────────────────────

    /** Thumbnail URL — highest quality available. Null means use placeholder. */
    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String?,

    /**
     * Dominant color extracted from the album art, as ARGB int.
     * Used for dynamic theming on the player screen.
     * Populated by the app module after image load, not by the source APIs.
     */
    @ColumnInfo(name = "dominant_color")
    val dominantColor: Int?,

    // ── Source ──────────────────────────────

    /**
     * "youtube" or "jiosaavn"
     * Determines which backend to hit for playback, lyrics, etc.
     */
    @ColumnInfo(name = "source")
    val source: String,

    /**
     * Source-specific ID without prefix.
     * For YouTube: the videoId.
     * For JioSaavn: the numeric song ID.
     * Stored separately to avoid string parsing at query time.
     */
    @ColumnInfo(name = "source_id")
    val sourceId: String,

    /** JioSaavn-only: highest quality download URL from the API response. */
    @ColumnInfo(name = "js_stream_url")
    val jsStreamUrl: String?,

    /** JioSaavn-only: quality label ("320kbps", "160kbps", etc.) */
    @ColumnInfo(name = "js_stream_quality")
    val jsStreamQuality: String?,

    // ── Metadata flags ─────────────────────

    @ColumnInfo(name = "explicit")
    val explicit: Boolean = false,

    @ColumnInfo(name = "has_lyrics")
    val hasLyrics: Boolean = false,

    /** JioSaavn lyrics ID for fetching plain text lyrics. */
    @ColumnInfo(name = "lyrics_id")
    val lyricsId: String?,

    @ColumnInfo(name = "play_count")
    val playCount: Long = 0,

    // ── User state ─────────────────────────

    @ColumnInfo(name = "liked")
    val liked: Boolean = false,

    /** Total times the user has played this track. Incremented by the player. */
    @ColumnInfo(name = "local_play_count")
    val localPlayCount: Int = 0,

    // ── Timestamps ─────────────────────────

    /** When this track was first added to the library. */
    @ColumnInfo(name = "date_added")
    val dateAdded: Long = System.currentTimeMillis(),

    /** Last time the user played this track. Null if never played locally. */
    @ColumnInfo(name = "date_played")
    val datePlayed: Long? = null,
)

/**
 * Lightweight artist reference stored as JSON in [LocalTrack.artistsJson].
 */
data class ArtistRef(
    val name: String,
    val id: String?,
)
