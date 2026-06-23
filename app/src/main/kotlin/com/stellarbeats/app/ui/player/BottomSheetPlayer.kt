package com.stellarbeats.app.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stellarbeats.app.ui.theme.DynamicPlayerTheme
import com.stellarbeats.database.entities.LocalTrack
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun BottomSheetPlayer(viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetExpanded by viewModel.sheetExpanded.collectAsStateWithLifecycle()
    val track = state.currentTrack ?: return

    val sheetOffset = remember { androidx.compose.animation.core.Animatable(1f) }

    LaunchedEffect(sheetExpanded) {
        sheetOffset.animateTo(
            targetValue = if (sheetExpanded) 0f else 1f,
            animationSpec = androidx.compose.animation.core.spring(
                stiffness = 300,
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (sheetExpanded) Modifier.background(Color.Black.copy(alpha = 0.6f * (1f - sheetOffset.value)))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null)
                { viewModel.collapseSheet() } else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .then(if (sheetExpanded) Modifier.fillMaxSize() else Modifier.height(72.dp))
        ) {
            if (sheetExpanded) {
                DynamicPlayerTheme(dominantColor = state.dominantColor) {
                    FullPlayer(
                        state = state,
                        onSeek = viewModel::seekToProgress,
                        onPlayPause = viewModel::playPause,
                        onNext = viewModel::next,
                        onPrevious = viewModel::previous,
                        onToggleLyrics = viewModel::toggleLyrics,
                        onCollapse = viewModel::collapseSheet,
                        onColorExtracted = viewModel::updateDominantColor,
                        onToggleLike = { },
                    )
                }
            } else {
                MiniPlayer(track = track, isPlaying = state.isPlaying, onPlayPause = viewModel::playPause, onClick = viewModel::expandSheet)
            }
        }
    }
}

@Composable
private fun MiniPlayer(track: LocalTrack, isPlaying: Boolean, onPlayPause: () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = track.thumbnailUrl, contentDescription = track.title,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(text = track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            Text(text = track.artistsJson.parseFirstArtistName(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onPlayPause) {
            Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun FullPlayer(
    state: PlayerUiState,
    onSeek: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleLyrics: () -> Unit,
    onCollapse: () -> Unit,
    onColorExtracted: (Int) -> Unit,
    onToggleLike: () -> Unit,
) {
    val track = state.currentTrack ?: return
    val progress = if (state.durationMs > 0) state.currentPositionMs.toFloat() / state.durationMs.toFloat() else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse) { Icon(Icons.Default.ChevronDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
            Text(text = "Now Playing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { }) { Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
        }
        Spacer(Modifier.weight(1f))
        AsyncImage(
            model = track.thumbnailUrl, contentDescription = track.title,
            modifier = Modifier.size(300.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.weight(1f))
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            Text(text = track.title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(text = track.artistsJson.parseFirstArtistName(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        WavySlider(value = progress, onValueChange = onSeek, isPlaying = state.isPlaying, activeColor = MaterialTheme.colorScheme.primary, inactiveColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = formatTime(state.currentPositionMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = formatTime(state.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleLike) {
                Icon(imageVector = if (track.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, tint = if (track.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp)) }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(72.dp).background(color = MaterialTheme.colorScheme.primary, shape = CircleShape)) {
                Icon(imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.SkipNext, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp)) }
            IconButton(onClick = onToggleLyrics) {
                Text(text = "LYR", style = MaterialTheme.typography.labelSmall, color = if (state.showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(32.dp))
        AnimatedContent(targetState = state.showLyrics, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "lyrics") { show ->
            if (show && state.lyrics != null) {
                LyricsView(lyrics = state.lyrics!!, currentPositionMs = state.currentPositionMs, accentColor = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 32.dp))
            } else { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun LyricsView(lyrics: com.stellarbeats.lyrics.Lyrics, currentPositionMs: Long, accentColor: Color, modifier: Modifier = Modifier) {
    val activeIndex = lyrics.indexOfTime(currentPositionMs)
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(lyrics.lines) { index, line ->
            val isActive = if (lyrics.isSynced) index == activeIndex else false
            val isPast = if (lyrics.isSynced) index < activeIndex else false
            Text(
                text = if (line.isInstrumental) "♪ Instrumental ♪" else line.text,
                style = MaterialTheme.typography.bodyLarge,
                color = when { isActive -> accentColor; isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f); else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) },
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.animateItem(),
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms < 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun String.parseFirstArtistName(): String {
    return try {
        val json = Json { ignoreUnknownKeys = true }
        val arr = json.parseToJsonElement(this).jsonArray
        arr.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""
    } catch (_: Exception) { this }
}
