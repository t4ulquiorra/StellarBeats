package com.stellarbeats.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stellarbeats.app.repository.HomeSection
import com.stellarbeats.app.ui.player.PlayerViewModel
import com.stellarbeats.database.entities.LocalTrack
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun HomeScreen(onTrackClick: (LocalTrack) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    
    when {
        state.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        state.error != null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Something went wrong", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(text = state.error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        else -> {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 16.dp)) {
                item {
                    Text(text = "Good evening", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(24.dp))
                    
                    // JioSaavn Test Button
                    Button(
                        onClick = { playerViewModel.playTestTrack() },
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Text("Play JioSaavn Test Track")
                    }
                    
                    // Show Player Loading / Error states
                    if (playerState.isLoading) {
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.padding(horizontal = 24.dp))
                    }
                    playerState.error?.let { error ->
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Player Error: $error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
                items(state.sections) { section ->
                    HomeSectionRow(section = section, onTrackClick = onTrackClick)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeSectionRow(section: HomeSection, onTrackClick: (LocalTrack) -> Unit) {
    Column {
        Text(text = section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface)
        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items = section.tracks, key = { it.trackId }) { track ->
                TrackCard(track = track, onClick = { onTrackClick(track) })
            }
        }
    }
}

@Composable
private fun TrackCard(track: LocalTrack, onClick: () -> Unit) {
    Column(modifier = Modifier.width(160.dp).clickable(onClick = onClick)) {
        AsyncImage(model = track.thumbnailUrl, contentDescription = track.title, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.height(8.dp))
        Text(text = track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
        Text(text = track.artistsJson.parseFirstArtistName(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun String.parseFirstArtistName(): String {
    return try {
        val json = Json { ignoreUnknownKeys = true }
        val arr = json.parseToJsonElement(this).jsonArray
        arr.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""
    } catch (_: Exception) { this }
}
