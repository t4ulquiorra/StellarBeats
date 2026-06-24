package com.stellarbeats.app.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.stellarbeats.app.di.PlayerExo
import com.stellarbeats.app.repository.MusicRepository
import com.stellarbeats.database.entities.LocalTrack
import com.stellarbeats.lyrics.Lyrics
import com.stellarbeats.lyrics.LyricsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PlayerUiState(
    val currentTrack: LocalTrack? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val dominantColor: Int? = null,
    val lyrics: Lyrics? = null,
    val showLyrics: Boolean = false,
    val queue: List<LocalTrack> = emptyList(),
    val queueIndex: Int = -1,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @PlayerExo private val player: ExoPlayer,
    private val repository: MusicRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _sheetExpanded = MutableStateFlow(false)
    val sheetExpanded: StateFlow<Boolean> = _sheetExpanded.asStateFlow()

    private var progressJob: Job? = null
    private var lyricsJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = playing)
                if (playing) startProgressUpdates() else stopProgressUpdates()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let { item ->
                    val trackId = item.mediaId
                    viewModelScope.launch {
                        val track = repository.trackDao.get(trackId)
                        if (track != null) {
                            _uiState.value = _uiState.value.copy(
                                currentTrack = track,
                                dominantColor = track.dominantColor,
                                durationMs = track.durationMs ?: player.duration.coerceAtLeast(0),
                            )
                            fetchLyrics(track)
                            repository.incrementPlayCount(trackId)
                        }
                    }
                }
            }
        })
    }

    fun play(track: LocalTrack, queue: List<LocalTrack> = listOf(track), index: Int = 0) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, queue = queue, queueIndex = index)
            try {
                repository.trackDao.insert(track)
                val streamUrl = repository.resolveStreamUrl(track)
                val mediaItem = MediaItem.Builder().setMediaId(track.trackId).setUri(Uri.parse(streamUrl)).build()
                val mediaItems = queue.map { t -> MediaItem.Builder().setMediaId(t.trackId).build() }
                player.setMediaItems(mediaItems, index, 0)
                player.prepare()
                player.playWhenReady = true
                _uiState.value = _uiState.value.copy(
                    currentTrack = track, isPlaying = true, isLoading = false, durationMs = track.durationMs ?: 0,
                )
                fetchLyrics(track)
                startProgressUpdates()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Playback failed")
            }
        }
    }

    fun playPause() { if (player.isPlaying) player.pause() else player.play() }
    fun next() { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
    fun previous() { if (player.currentPosition > 3000) player.seekTo(0) else if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() }
    fun seekTo(positionMs: Long) { player.seekTo(positionMs); _uiState.value = _uiState.value.copy(currentPositionMs = positionMs) }
    fun seekToProgress(progress: Float) { val duration = _uiState.value.durationMs; if (duration > 0) seekTo((progress * duration).toLong()) }
    fun toggleSheet() { _sheetExpanded.value = !_sheetExpanded.value }
    fun expandSheet() { _sheetExpanded.value = true }
    fun collapseSheet() { _sheetExpanded.value = false }
    fun toggleLyrics() { _uiState.value = _uiState.value.copy(showLyrics = !_uiState.value.showLyrics) }

    fun updateDominantColor(color: Int) {
        val track = _uiState.value.currentTrack ?: return
        _uiState.value = _uiState.value.copy(dominantColor = color)
        viewModelScope.launch { repository.updateDominantColor(track.trackId, color) }
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = viewModelScope.launch {
            while (true) {
                _uiState.value = _uiState.value.copy(
                    currentPositionMs = player.currentPosition.coerceAtLeast(0),
                    bufferedMs = player.bufferedPosition.coerceAtLeast(0),
                    durationMs = player.duration.coerceAtLeast(0).let { dur -> if (dur > 0) dur else _uiState.value.durationMs },
                )
                delay(200)
            }
        }
    }

    private fun stopProgressUpdates() { progressJob?.cancel(); progressJob = null }

    private fun fetchLyrics(track: LocalTrack) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch(Dispatchers.IO) {
            val lyricsId = track.lyricsId
            val jsLyrics = if (track.source == "jiosaavn" && track.hasLyrics && lyricsId != null) {
                com.stellarbeats.jiosaavn.JioSaavnClient.lyrics(lyricsId).getOrNull()
            } else null
            val lyrics = LyricsProvider.fetch(
                title = track.title,
                artist = track.artistsJson.parseFirstArtistName(),
                album = track.album,
                durationMs = track.durationMs,
                jioSaavnLyricsText = jsLyrics,
            )
            _uiState.value = _uiState.value.copy(lyrics = lyrics)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdates()
        lyricsJob?.cancel()
    }

    companion object {
        private fun String.parseFirstArtistName(): String {
            return try {
                val json = Json { ignoreUnknownKeys = true }
                val arr = json.parseToJsonElement(this).jsonArray
                arr.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""
            } catch (_: Exception) { "" }
        }
    }
}
