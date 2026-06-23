package com.stellarbeats.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stellarbeats.app.repository.HomeData
import com.stellarbeats.app.repository.HomeSection
import com.stellarbeats.app.repository.MusicRepository
import com.stellarbeats.database.entities.LocalTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val sections: List<HomeSection> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadHome()
    }

    fun refresh() = loadHome()

    private fun loadHome() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val data = repository.homeSections()
                _uiState.value = HomeUiState(
                    sections = data.sections.filter { it.tracks.isNotEmpty() },
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = HomeUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load home",
                )
            }
        }
    }
}
