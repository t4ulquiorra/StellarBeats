package com.stellarbeats.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Recent search queries stored locally.
 *
 * No user accounts, no server sync — this is purely for the
 * "recent searches" dropdown on the search screen.
 *
 * Duplicate queries update the timestamp instead of creating new rows.
 * Old entries (> 30 days) are pruned on app launch.
 */
@Entity(
    tableName = "search_history",
    indices = [
        Index(value = ["query"], name = "idx_search_history_query", unique = true),
        Index(value = ["date_searched"], name = "idx_search_history_date"),
    ],
)
data class SearchHistoryEntry(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** The raw search string the user typed. */
    @ColumnInfo(name = "query")
    val query: String,

    /**
     * Optional filter that was active when this search was made.
     * null = "All", "songs", "albums", "artists", "playlists"
     */
    @ColumnInfo(name = "filter")
    val filter: String? = null,

    @ColumnInfo(name = "date_searched")
    val dateSearched: Long = System.currentTimeMillis(),
)
