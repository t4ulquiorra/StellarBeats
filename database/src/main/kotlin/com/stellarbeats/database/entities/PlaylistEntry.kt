package com.stellarbeats.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Junction table for many-to-many relationship between playlists and tracks.
 *
 * The [position] field allows ordered playlists. When a track is inserted
 * at a position, all entries at that position and beyond are shifted.
 *
 * Deleting an entry does NOT delete the track from [LocalTrack] —
 * tracks are only removed from the library when the user explicitly
 * removes them from all playlists, un-likes them, and has no download.
 */
@Entity(
    tableName = "playlist_entries",
    primaryKeys = ["playlist_id", "track_id"],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["playlist_id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LocalTrack::class,
            parentColumns = ["track_id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["playlist_id", "position"], name = "idx_entries_playlist_position"),
        Index(value = ["track_id"], name = "idx_entries_track_id"),
        Index(value = ["date_added"], name = "idx_entries_date_added"),
    ],
)
data class PlaylistEntry(
    @ColumnInfo(name = "playlist_id")
    val playlistId: String,

    @ColumnInfo(name = "track_id")
    val trackId: String,

    /**
     * 0-based position in the playlist.
     * Must be unique within a playlist.
     * Managed by the DAO to prevent gaps and duplicates.
     */
    @ColumnInfo(name = "position")
    val position: Int = 0,

    /**
     * When this specific entry was added.
     * Different from the track's [LocalTrack.dateAdded] —
     * a track can be added to multiple playlists at different times.
     */
    @ColumnInfo(name = "date_added")
    val dateAdded: Long = System.currentTimeMillis(),

    /**
     * Optional note the user can attach to a track in a playlist.
     * Rarely used, but useful for "practice list" type playlists.
     */
    @ColumnInfo(name = "note")
    val note: String? = null,
)
