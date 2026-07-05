package com.aniplex.app.presentation.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniplex.app.domain.model.Anime
import com.aniplex.app.domain.model.Result
import com.aniplex.app.domain.repository.AnimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val results: List<Anime>, val hasNextPage: Boolean, val fallbackType: String = "") : SearchUiState
    data class Error(val message: String) : SearchUiState
    data object Empty : SearchUiState
}

data class SearchTrigger(
    val query: String,
    val type: String?,
    val status: String?,
    val sort: String?,
    val lang: String?,
    val genres: Set<String>
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: AnimeRepository,
    private val preferenceManager: com.aniplex.app.data.local.preferences.PreferenceManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _suggestions = MutableStateFlow<List<Anime>>(emptyList())
    val suggestions: StateFlow<List<Anime>> = _suggestions.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    // Filter states
    val selectedType = MutableStateFlow<String?>(null)
    val selectedStatus = MutableStateFlow<String?>(null)
    val selectedSort = MutableStateFlow<String?>(null)
    val selectedLanguage = MutableStateFlow<String?>(null)
    val selectedGenres = MutableStateFlow<Set<String>>(emptySet())

    private var currentPage = 1
    private var isCurrentlyLoadingNextPage = false
    private val allResults = mutableListOf<Anime>()

    private var searchJob: kotlinx.coroutines.Job? = null
    private var suggestionsJob: kotlinx.coroutines.Job? = null

    private fun processSearchResults(animes: List<Anime>, query: String): List<Anime> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return animes

        val queryWords = q.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotEmpty() }
        if (queryWords.isEmpty()) return animes

        return animes.map { anime ->
            val title = anime.title.trim().lowercase()
            val titleWords = title.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotEmpty() }

            var score = 0.0

            // 1. Exact Match (highest priority)
            if (title == q) {
                score += 10000.0
            } else if (title.replace(Regex("[^a-zA-Z0-9]+"), "") == q.replace(Regex("[^a-zA-Z0-9]+"), "")) {
                score += 8000.0
            }

            // 2. Starts With Match
            if (title.startsWith(q)) {
                score += 5000.0
            }

            // 3. Word Overlap Calculation
            var overlapCount = 0
            for (qw in queryWords) {
                if (titleWords.contains(qw)) {
                    overlapCount++
                }
            }
            val overlapRatio = overlapCount.toDouble() / queryWords.size
            score += overlapRatio * 3000.0

            // 4. Consecutive Word Match Sequence (Phrase matching)
            if (queryWords.size > 1) {
                var maxConsecutive = 0
                var currentConsecutive = 0
                for (tw in titleWords) {
                    if (currentConsecutive < queryWords.size && tw == queryWords[currentConsecutive]) {
                        currentConsecutive++
                        if (currentConsecutive > maxConsecutive) {
                            maxConsecutive = currentConsecutive
                        }
                    } else {
                        currentConsecutive = 0
                    }
                }
                score += (maxConsecutive.toDouble() / queryWords.size) * 1500.0
            }

            // 5. Length Penalty (Shorter, more precise matches win over long titles)
            val lengthDiff = Math.abs(title.length - q.length)
            score -= lengthDiff * 5.0

            // 6. Type Tie-Breaker
            val type = anime.type.lowercase().trim()
            if (type == "tv" || type.contains("tv")) {
                score += 100.0
            } else if (type == "movie" || type.contains("movie")) {
                score += 50.0
            }

            anime to score
        }.sortedWith(
            compareByDescending<Pair<Anime, Double>> { it.second }
                .thenBy { it.first.title }
        ).map { it.first }
    }

    init {
        _recentSearches.value = preferenceManager.getRecentSearches()

        // Redesigned with combined reactive flows to dynamically support:
        // 1. Live debounced query suggestions in parallel (separate non-blocking job)
        // 2. Real-time automatic filter applications (reactive updates)
        // 3. Proper search query scoping when combining filters
        viewModelScope.launch {
            combine(
                _searchQuery,
                selectedType,
                selectedStatus,
                selectedSort,
                selectedLanguage,
                selectedGenres
            ) { array ->
                val query = array[0] as String
                val type = array[1] as String?
                val status = array[2] as String?
                val sort = array[3] as String?
                val lang = array[4] as String?
                @Suppress("UNCHECKED_CAST")
                val genres = array[5] as Set<String>
                SearchTrigger(query, type, status, sort, lang, genres)
            }
            .debounce(300)
            .collectLatest { trigger ->
                val q = trigger.query.trim()
                
                // Keep the live suggestions updated in parallel (does NOT suspend the main flow!)
                suggestionsJob?.cancel()
                if (q.length >= 2) {
                    suggestionsJob = viewModelScope.launch {
                        repository.getSuggestions(q).collect { result ->
                            if (result is Result.Success) {
                                val ranked = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                    processSearchResults(result.data, q)
                                }
                                _suggestions.value = ranked
                            }
                        }
                    }
                } else {
                    _suggestions.value = emptyList()
                }

                val hasFilters = trigger.type != null || trigger.status != null || 
                        trigger.sort != null || trigger.lang != null || trigger.genres.isNotEmpty()

                if (q.isNotEmpty() || hasFilters) {
                    performSearch(isNewSearch = true)
                } else {
                    _suggestions.value = emptyList()
                    _uiState.value = SearchUiState.Idle
                    searchJob?.cancel()
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun performSearch(isNewSearch: Boolean = true) {
        if (isNewSearch) {
            currentPage = 1
            allResults.clear()
            _uiState.value = SearchUiState.Loading
        } else {
            isCurrentlyLoadingNextPage = true
        }

        val queryVal = _searchQuery.value.trim()
        val typeVal = selectedType.value
        val statusVal = selectedStatus.value
        val sortVal = selectedSort.value
        val langVal = selectedLanguage.value
        val genresVal = selectedGenres.value.joinToString(",")

        val hasFilters = typeVal != null || statusVal != null || sortVal != null || langVal != null || genresVal.isNotEmpty()

        // Cancel previous search job to prevent API rate limiting, network hammering and overlapping race conditions
        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            val flowResult = if (queryVal.isNotEmpty()) {
                // If there is an active search query, always use the search endpoint to preserve the query scope!
                repository.search(queryVal, currentPage)
            } else {
                // Otherwise use the advanced filter API directly
                if (!hasFilters) {
                    _uiState.value = SearchUiState.Idle
                    return@launch
                }
                repository.filterAnime(
                    type = typeVal,
                    status = statusVal,
                    genres = if (genresVal.isEmpty()) null else genresVal,
                    sort = sortVal,
                    language = langVal,
                    page = currentPage
                )
            }

            flowResult.collect { result ->
                when (result) {
                    is Result.Loading -> {
                        if (isNewSearch) _uiState.value = SearchUiState.Loading
                    }
                    is Result.Success -> {
                        isCurrentlyLoadingNextPage = false
                        val newItems = result.data
                        
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                            // Apply strict scoping client-side filters on search results if query is active
                            val filteredItems = if (queryVal.isNotEmpty() && hasFilters) {
                                newItems.filter { anime ->
                                    val matchesType = typeVal == null || run {
                                        val aType = anime.type.replace(" ", "").replace("-", "").lowercase()
                                        val fType = typeVal.replace(" ", "").replace("-", "").lowercase()
                                        aType == fType || aType.contains(fType) || fType.contains(aType)
                                    }
                                    val matchesLanguage = langVal == null || when (langVal) {
                                        "sub" -> anime.subEpisodes > 0
                                        "dub" -> anime.dubEpisodes > 0
                                        "sub-dub" -> anime.subEpisodes > 0 || anime.dubEpisodes > 0
                                        else -> true
                                    }
                                    matchesType && matchesLanguage
                                }
                            } else {
                                newItems
                            }

                            // Apply smart fuzzy-match sorting/ranking on search results
                            val rankedItems = if (queryVal.isNotBlank()) {
                                processSearchResults(filteredItems, queryVal)
                            } else {
                                filteredItems
                            }

                            val isBackupActive = newItems.any { it.isBackup }

                            // Update results on main thread
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                isCurrentlyLoadingNextPage = false
                                allResults.addAll(rankedItems)
                                val deDuplicated = allResults.distinctBy { it.id }
                                allResults.clear()
                                allResults.addAll(deDuplicated)

                                if (allResults.isEmpty()) {
                                    _uiState.value = SearchUiState.Empty
                                } else {
                                    val hasNext = newItems.size >= 15
                                    _uiState.value = SearchUiState.Success(
                                        results = allResults.toList(),
                                        hasNextPage = hasNext,
                                        fallbackType = if (isBackupActive) "anilist" else ""
                                    )
                                }
                            }
                        }
                    }
                    is Result.Error -> {
                        isCurrentlyLoadingNextPage = false
                        if (isNewSearch) {
                            _uiState.value = SearchUiState.Error(result.message)
                        }
                    }
                }
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state is SearchUiState.Success && state.hasNextPage && !isCurrentlyLoadingNextPage) {
            currentPage++
            performSearch(isNewSearch = false)
        }
    }

    fun toggleGenre(genre: String) {
        val current = selectedGenres.value
        selectedGenres.value = if (current.contains(genre)) {
            current - genre
        } else {
            current + genre
        }
    }

    fun clearFilters() {
        selectedType.value = null
        selectedStatus.value = null
        selectedSort.value = null
        selectedLanguage.value = null
        selectedGenres.value = emptySet()
    }

    fun recordSearchQuery(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        val currentList = _recentSearches.value.toMutableList()
        currentList.remove(q)
        currentList.add(0, q)
        val limited = currentList.take(5)
        _recentSearches.value = limited
        preferenceManager.saveRecentSearches(limited)
    }

    fun removeRecentSearch(query: String) {
        val currentList = _recentSearches.value.toMutableList()
        currentList.remove(query)
        _recentSearches.value = currentList
        preferenceManager.saveRecentSearches(currentList)
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
        preferenceManager.saveRecentSearches(emptyList())
    }
}
