package com.aniplex.app.presentation.screens.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniplex.app.domain.model.Anime
import com.aniplex.app.domain.model.Result
import com.aniplex.app.domain.repository.AnimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BrowseUiState {
    data object Loading : BrowseUiState
    data class Success(val results: List<Anime>, val hasNextPage: Boolean) : BrowseUiState
    data class Error(val message: String) : BrowseUiState
    data object Empty : BrowseUiState
}

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: AnimeRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow("most-popular")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre: StateFlow<String?> = _selectedGenre.asStateFlow()

    private val _uiState = MutableStateFlow<BrowseUiState>(BrowseUiState.Loading)
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    private var currentPage = 1
    private var isCurrentlyLoadingNextPage = false
    private val allResults = mutableListOf<Anime>()

    init {
        loadData()
    }

    fun onCategoryChange(category: String) {
        _selectedCategory.value = category
        _selectedGenre.value = null // reset genre filter when category changes
        loadData(isNewLoad = true)
    }

    fun onGenreChange(genre: String?) {
        _selectedGenre.value = genre
        loadData(isNewLoad = true)
    }

    fun loadData(isNewLoad: Boolean = true) {
        if (isNewLoad) {
            currentPage = 1
            allResults.clear()
            _uiState.value = BrowseUiState.Loading
        } else {
            isCurrentlyLoadingNextPage = true
        }

        val categoryVal = _selectedCategory.value
        val genreVal = _selectedGenre.value

        viewModelScope.launch {
            val flowResult = if (genreVal != null) {
                // Fetch by genre if genre filter is selected
                repository.getAnimeByGenre(genreVal, currentPage)
            } else {
                // Fetch by category (tv, movie, ova, etc.)
                repository.getAnimeByCategory(categoryVal, currentPage)
            }

            flowResult.collect { result ->
                when (result) {
                    is Result.Loading -> {
                        if (isNewLoad) _uiState.value = BrowseUiState.Loading
                    }
                    is Result.Success -> {
                        isCurrentlyLoadingNextPage = false
                        val newItems = result.data
                        allResults.addAll(newItems)
                        
                        if (allResults.isEmpty()) {
                            _uiState.value = BrowseUiState.Empty
                        } else {
                            val hasNext = newItems.size >= 15
                            _uiState.value = BrowseUiState.Success(allResults.toList(), hasNext)
                        }
                    }
                    is Result.Error -> {
                        isCurrentlyLoadingNextPage = false
                        if (isNewLoad) {
                            _uiState.value = BrowseUiState.Error(result.message)
                        }
                    }
                }
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state is BrowseUiState.Success && state.hasNextPage && !isCurrentlyLoadingNextPage) {
            currentPage++
            loadData(isNewLoad = false)
        }
    }
}
