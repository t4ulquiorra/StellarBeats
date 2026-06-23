package com.stellarbeats.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-created playlist.
 *
 * System playlists (Liked, Most Played, Recently Played) use reserved IDs
 * and are created on first launch by the app module — not by the user.
 */
@Entity(
    tableName = "playlists",
    indices = [
        Index(value = ["name"], name = "idx_playlists_name"),
        Index(value = ["date_modified"], name = "idx_playlists_date_modified"),
    ],
)
data class Playlist(
    @PrimaryKey
    @ColumnInfo(name = "playlist_id")
    val playlistId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    /** Thumbnail URL — typically the artwork of the first track, or null. */
    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String? = null,

    /** Cached track count. Updated by triggers or manual refresh. */
    @ColumnInfo(name = "track_count")
    val trackCount: Int = 0,

    /**
     * Total duration of all tracks in seconds.
     * Cached for display without JOINing the entries table.
     */
    @ColumnInfo(name = "total_duration_ms")
    val totalDurationMs: Long = 0,

    /** If true, this playlist can't be deleted or renamed by the user. */
    @ColumnInfo(name = "is_system")
    val isSystem: Boolean = false,

    @ColumnInfo(name = "date_created")
    val dateCreated: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "date_modified")
    val dateModified: Long = System.currentTimeMillis(),
) {
    companion object {
        /** "yt:{playlistId}" or "js:{playlistId}" — remote playlists synced locally. */
        fun remoteId(source: String, remotePlaylistId: String): String =
            "${source}_remote:${remotePlaylistId}"

        /** System playlist IDs — never collide with user or remote IDs. */
        const val ID_LIKED = "system:liked"
        const val ID_MOST_PLAYED = "system:most_played"
        const val ID_RECENTLY_PLAYED = "system:recently_played"
        const val ID_DOWNLOADED = "system:downloaded"
    }
}
