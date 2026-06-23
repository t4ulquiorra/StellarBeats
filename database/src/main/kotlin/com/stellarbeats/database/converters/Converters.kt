package com.stellarbeats.database.converters

import androidx.room.TypeConverter
import com.stellarbeats.database.entities.ArtistRef
import com.stellarbeats.database.entities.DownloadState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room TypeConverters for columns that store complex types as JSON strings.
 *
 * Room only supports primitive types (String, Int, Long, Boolean, Float, Double)
 * as column values. Everything else must be serialized to/from a string.
 *
 * These converters are registered on the database class via @TypeConverters
 * and automatically applied to all DAOs and entities in this database.
 */
class Converters {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    // ── Artist list ──────────────────────────

    @TypeConverter
    fun fromArtistList(artists: List<ArtistRef>): String {
        return json.encodeToString(artists)
    }

    @TypeConverter
    fun toArtistList(value: String): List<ArtistRef> {
        return try {
            json.decodeFromString<List<ArtistRef>>(value)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── String list (generic) ────────────────

    @TypeConverter
    funFromStringList(list: List<String>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return try {
            json.decodeFromString<List<String>>(value)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Download state enum ──────────────────

    @TypeConverter
    fun fromDownloadState(state: DownloadState): String {
        return state.name
    }

    @TypeConverter
    fun toDownloadState(value: String): DownloadState {
        return try {
            DownloadState.valueOf(value)
        } catch (e: Exception) {
            DownloadState.QUEUED
        }
    }

    // ── Nullable Long list (for playlist song order caching) ──

    @TypeConverter
    fun fromLongList(list: List<Long>?): String? {
        if (list == null) return null
        return json.encodeToString(list)
    }

    @TypeConverter
    fun toLongList(value: String?): List<Long>? {
        if (value == null) return null
        return try {
            json.decodeFromString<List<Long>>(value)
        } catch (e: Exception) {
            null
        }
    }
}
