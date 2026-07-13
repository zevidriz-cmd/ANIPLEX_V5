package com.aniplex.app.presentation.screens.seasonal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniplex.app.domain.model.Anime
import com.aniplex.app.domain.model.SeasonalData
import com.aniplex.app.domain.repository.AnimeRepository
import com.aniplex.app.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

sealed interface ResolvingState {
    object Idle : ResolvingState
    data class Loading(val message: String) : ResolvingState
}

sealed interface SeasonalUiState {
    object Loading : SeasonalUiState
    data class Success(val data: SeasonalData) : SeasonalUiState
    data class Error(val message: String) : SeasonalUiState
}

@HiltViewModel
class SeasonalViewModel @Inject constructor(
    private val repository: AnimeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SeasonalUiState>(SeasonalUiState.Loading)
    val uiState: StateFlow<SeasonalUiState> = _uiState.asStateFlow()

    private val _resolvingState = MutableStateFlow<ResolvingState>(ResolvingState.Idle)
    val resolvingState: StateFlow<ResolvingState> = _resolvingState.asStateFlow()

    private val currentSeasonAndYear = getCurrentSeasonAndYear()

    private val _selectedSeason = MutableStateFlow(currentSeasonAndYear.first)
    val selectedSeason: StateFlow<String> = _selectedSeason.asStateFlow()

    private val _selectedYear = MutableStateFlow(currentSeasonAndYear.second)
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    private val _page = MutableStateFlow(1)
    val page: StateFlow<Int> = _page.asStateFlow()

    init {
        viewModelScope.launch {
            selectedSeason.collectLatest {
                fetchSeasonalData()
            }
        }
        viewModelScope.launch {
            selectedYear.collectLatest {
                fetchSeasonalData()
            }
        }
        viewModelScope.launch {
            page.collectLatest {
                fetchSeasonalData()
            }
        }
    }

    fun changeFilters(season: String, year: Int) {
        _selectedSeason.value = season
        _selectedYear.value = year
        _page.value = 1
    }

    fun setPage(page: Int) {
        _page.value = page
    }

    fun fetchSeasonalData() {
        val season = _selectedSeason.value
        val year = _selectedYear.value
        val p = _page.value

        viewModelScope.launch {
            repository.getSeasonalAnime(year, season, p).collect { result ->
                when (result) {
                    is Result.Loading -> _uiState.value = SeasonalUiState.Loading
                    is Result.Success -> {
                        _uiState.value = SeasonalUiState.Success(
                            SeasonalData(
                                animes = result.data.animes,
                                totalPages = result.data.totalPages,
                                hasNextPage = result.data.hasNextPage
                            )
                        )
                    }
                    is Result.Error -> _uiState.value = SeasonalUiState.Error(result.message)
                }
            }
        }
    }

    fun resolveAnime(
        anime: Anime,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            _resolvingState.value = ResolvingState.Loading("Resolving stream for \"${anime.title}\"...")
            val malId = anime.id.replace("mal-", "")
            try {
                // 1. Try resolveMAL
                repository.resolveMAL(malId).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            if (result.data.isNotBlank()) {
                                _resolvingState.value = ResolvingState.Idle
                                onSuccess(result.data)
                            } else {
                                performSearchFallback(anime, onSuccess, onFailure)
                            }
                        }
                        is Result.Error -> {
                            performSearchFallback(anime, onSuccess, onFailure)
                        }
                        is Result.Loading -> { /* no-op */ }
                    }
                }
            } catch (e: Exception) {
                performSearchFallback(anime, onSuccess, onFailure)
            }
        }
    }

    private fun performSearchFallback(
        anime: Anime,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            repository.search(anime.title, 1).collect { searchResult ->
                when (searchResult) {
                    is Result.Success -> {
                        if (searchResult.data.isNotEmpty()) {
                            val resolvedId = searchResult.data.first().id
                            _resolvingState.value = ResolvingState.Idle
                            onSuccess(resolvedId)
                        } else {
                            _resolvingState.value = ResolvingState.Idle
                            onFailure(anime.title)
                        }
                    }
                    is Result.Error -> {
                        _resolvingState.value = ResolvingState.Idle
                        onFailure(anime.title)
                    }
                    is Result.Loading -> { /* no-op */ }
                }
            }
        }
    }

    private fun getCurrentSeasonAndYear(): Pair<String, Int> {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) // 0-indexed
        val season = when (month) {
            in 0..2 -> "winter"
            in 3..5 -> "spring"
            in 6..8 -> "summer"
            else -> "fall"
        }
        return Pair(season, year)
    }
}
