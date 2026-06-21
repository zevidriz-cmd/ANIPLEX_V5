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
    data class Success(val results: List<Anime>, val hasNextPage: Boolean) : SearchUiState
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

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[len1][len2]
    }

    private fun calculateMatchScore(query: String, anime: Anime): Int {
        val title = anime.title.lowercase().trim()
        val q = query.lowercase().trim()
        if (q.isEmpty()) return 0

        var score = 0

        val cleanTitle = title.replace(Regex("[^a-z0-9]"), "")
        val cleanQ = q.replace(Regex("[^a-z0-9]"), "")

        // 1. Exact Match prioritization
        if (title == q) {
            score += 100000
        } else if (cleanQ.isNotEmpty() && cleanTitle == cleanQ) {
            score += 80000
        }

        // 2. Starts-with prioritization
        if (title.startsWith(q)) {
            score += 50000
        } else if (cleanQ.isNotEmpty() && cleanTitle.startsWith(cleanQ)) {
            score += 40000
        }

        // 3. Word boundary contains prioritization (e.g., query starts a word in title)
        if (title.contains(" $q") || title.contains("$q ")) {
            score += 20000
        }

        // 4. Substring contains prioritization
        if (title.contains(q)) {
            score += 10000
        } else if (cleanQ.isNotEmpty() && cleanTitle.contains(cleanQ)) {
            score += 5000
        }

        // 5. Typo tolerance / Similarity
        if (cleanQ.isNotEmpty() && cleanTitle.isNotEmpty()) {
            val distance = levenshteinDistance(cleanTitle, cleanQ)
            if (distance <= 2) {
                score += (2000 - (distance * 500))
            }
        }

        // 6. Tie-breaker type prioritization (very minor so text match always wins)
        val type = anime.type.lowercase().trim()
        if (type == "tv" || type == "tv series" || type.contains("tv")) {
            score += 400
        } else if (type == "ona" || type.contains("ona")) {
            score += 300
        } else if (type == "movie" || type.contains("movie")) {
            if (!q.contains("movie") && !q.contains("film")) {
                score -= 100
            } else {
                score += 200
            }
        }

        return score
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
                                val ranked = result.data.map { anime ->
                                    val score = calculateMatchScore(q, anime)
                                    anime to score
                                }.sortedWith(
                                    compareByDescending<Pair<Anime, Int>> { it.second }
                                        .thenBy { it.first.title }
                                ).map { it.first }
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
                            filteredItems.map { anime ->
                                val score = calculateMatchScore(queryVal, anime)
                                anime to score
                            }.sortedWith(
                                compareByDescending<Pair<Anime, Int>> { it.second }
                                    .thenBy { it.first.title }
                            ).map { it.first }
                        } else {
                            filteredItems
                        }

                        allResults.addAll(rankedItems)
                        
                        // Deduplicate entries by ID to ensure a completely pristine visual grid
                        val deDuplicated = allResults.distinctBy { it.id }
                        allResults.clear()
                        allResults.addAll(deDuplicated)
                        
                        if (allResults.isEmpty()) {
                            _uiState.value = SearchUiState.Empty
                        } else {
                            // Assume next page is available if we got a full batch of items
                            val hasNext = newItems.size >= 15
                            _uiState.value = SearchUiState.Success(allResults.toList(), hasNext)
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
