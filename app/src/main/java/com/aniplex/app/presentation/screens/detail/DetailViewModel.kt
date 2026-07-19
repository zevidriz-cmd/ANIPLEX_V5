package com.aniplex.app.presentation.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniplex.app.data.local.preferences.ProfileManager
import com.aniplex.app.domain.model.AnimeDetail
import com.aniplex.app.domain.model.Character
import com.aniplex.app.domain.model.Episode
import com.aniplex.app.domain.model.HistoryItem
import com.aniplex.app.domain.model.Result
import com.aniplex.app.domain.model.Season
import com.aniplex.app.domain.model.StoryArc
import com.aniplex.app.domain.repository.AnimeRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed interface DetailState<out T> {
    data object Loading : DetailState<Nothing>
    data class Success<out T>(val data: T) : DetailState<T>
    data class Error(val message: String) : DetailState<Nothing>
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: AnimeRepository,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val profileManager: ProfileManager,
    private val preferenceManager: com.aniplex.app.data.local.preferences.PreferenceManager
) : ViewModel() {

    private val _selectedVersion = MutableStateFlow(preferenceManager.preferredAnimeVersion)
    val selectedVersion: StateFlow<String> = _selectedVersion.asStateFlow()

    private val _hasMultipleVersions = MutableStateFlow(false)
    val hasMultipleVersions: StateFlow<Boolean> = _hasMultipleVersions.asStateFlow()

    private fun checkForAlternativeVersions(animeDetail: AnimeDetail) {
        val currentId = animeDetail.id
        val malId = animeDetail.malId
        if (currentId == "6245" || malId == "50392" || currentId == "5926" || malId == "54722") {
            _hasMultipleVersions.value = true
            return
        }

        _hasMultipleVersions.value = false
        viewModelScope.launch {
            val currentTitle = animeDetail.name
            val currentId = animeDetail.id
            val isCurrentUncut = currentTitle.contains("uncut", ignoreCase = true) ||
                    currentTitle.contains("uncensored", ignoreCase = true) ||
                    currentId.contains("-uncut", ignoreCase = true)

            val baseTitle = currentTitle
                .replace(Regex("(?i)\\s*\\(uncut\\)"), "")
                .replace(Regex("(?i)\\s*\\(uncensored\\)"), "")
                .replace(Regex("(?i)\\s*\\(censored\\)"), "")
                .replace(Regex("(?i)\\s*\\(tv-broadcast\\)"), "")
                .replace(Regex("(?i)\\s*\\(tv\\)"), "")
                .trim()

            var hasAlt = false

            // Check 1: Seasons
            val currentSeasons = (_seasonsState.value as? DetailState.Success)?.data ?: emptyList()
            if (currentSeasons.isNotEmpty()) {
                val hasInSeasons = currentSeasons.any { season ->
                    val seasonBaseTitle = season.title
                        .replace(Regex("(?i)\\s*\\(uncut\\)"), "")
                        .replace(Regex("(?i)\\s*\\(uncensored\\)"), "")
                        .replace(Regex("(?i)\\s*\\(censored\\)"), "")
                        .replace(Regex("(?i)\\s*\\(tv-broadcast\\)"), "")
                        .replace(Regex("(?i)\\s*\\(tv\\)"), "")
                        .trim()

                    val isBaseMatch = seasonBaseTitle.equals(baseTitle, ignoreCase = true)
                    val isSeasonUncut = season.title.contains("uncut", ignoreCase = true) ||
                            season.title.contains("uncensored", ignoreCase = true)

                    isBaseMatch && (isSeasonUncut != isCurrentUncut)
                }
                if (hasInSeasons) {
                    hasAlt = true
                }
            }

            // Check 2: Scraper Search directly on hianime
            if (!hasAlt) {
                val searchQuery = if (!isCurrentUncut) "$baseTitle uncut" else baseTitle
                repository.searchHiAnime(searchQuery).collect { result ->
                    if (result is Result.Success) {
                        val searchResults = result.data
                        val hasInSearch = searchResults.any { anime ->
                            val animeBaseTitle = anime.title
                                .replace(Regex("(?i)\\s*\\(uncut\\)"), "")
                                .replace(Regex("(?i)\\s*\\(uncensored\\)"), "")
                                .replace(Regex("(?i)\\s*\\(censored\\)"), "")
                                .replace(Regex("(?i)\\s*\\(tv-broadcast\\)"), "")
                                .replace(Regex("(?i)\\s*\\(tv\\)"), "")
                                .trim()

                            val isBaseMatch = animeBaseTitle.equals(baseTitle, ignoreCase = true) ||
                                    anime.title.contains(baseTitle, ignoreCase = true)
                            val isItemUncut = anime.title.contains("uncut", ignoreCase = true) ||
                                    anime.title.contains("uncensored", ignoreCase = true) ||
                                    anime.id.contains("-uncut", ignoreCase = true)

                            isBaseMatch && (isItemUncut != isCurrentUncut)
                        }
                        if (hasInSearch) {
                            hasAlt = true
                        }
                    }
                }
            }

            _hasMultipleVersions.value = hasAlt
        }
    }

    fun setSelectedVersion(version: String) {
        val currentDetail = (detailState.value as? DetailState.Success)?.data
        if (currentDetail == null) {
            _selectedVersion.value = version
            preferenceManager.preferredAnimeVersion = version
            return
        }

        val currentTitle = currentDetail.name
        val currentId = currentDetail.id
        val malId = currentDetail.malId

        // Option A Manual Redirection Bypass for "Chained Soldier" and "Gushing over Magical Girls"
        if (currentId == "6245" || malId == "50392" || currentId == "5926" || malId == "54722") {
            _selectedVersion.value = version
            preferenceManager.preferredAnimeVersion = version
            _resolutionError.value = "Switched to ${if (version == "uncensored") "Uncut (Uncensored)" else "TV (Censored)"} version!"
            return
        }

        // Check if current anime in view has uncut keywords
        val isCurrentUncut = currentTitle.contains("uncut", ignoreCase = true) ||
                currentTitle.contains("uncensored", ignoreCase = true) ||
                currentId.contains("-uncut", ignoreCase = true)

        val targetIsUncut = version == "uncensored"

        if (isCurrentUncut == targetIsUncut) {
            _selectedVersion.value = version
            preferenceManager.preferredAnimeVersion = version
            _resolutionError.value = "Already viewing ${if (targetIsUncut) "Uncut (Uncensored)" else "TV (Censored)"} version."
            return
        }

        _isResolvingSeason.value = true
        _resolutionError.value = "Searching for ${if (targetIsUncut) "Uncut" else "TV"} version..."

        viewModelScope.launch {
            val baseTitle = currentTitle
                .replace(Regex("(?i)\\s*\\(uncut\\)"), "")
                .replace(Regex("(?i)\\s*\\(uncensored\\)"), "")
                .replace(Regex("(?i)\\s*\\(censored\\)"), "")
                .replace(Regex("(?i)\\s*\\(tv-broadcast\\)"), "")
                .replace(Regex("(?i)\\s*\\(tv\\)"), "")
                .trim()

            var matchedAnikotoId: String? = null

            // 1. First, search alternative seasons/relations
            val currentSeasons = (_seasonsState.value as? DetailState.Success)?.data ?: emptyList()
            if (currentSeasons.isNotEmpty()) {
                val candidateSeason = currentSeasons.find { season ->
                    val seasonBaseTitle = season.title
                        .replace(Regex("(?i)\\s*\\(uncut\\)"), "")
                        .replace(Regex("(?i)\\s*\\(uncensored\\)"), "")
                        .replace(Regex("(?i)\\s*\\(censored\\)"), "")
                        .replace(Regex("(?i)\\s*\\(tv-broadcast\\)"), "")
                        .replace(Regex("(?i)\\s*\\(tv\\)"), "")
                        .trim()

                    val isBaseMatch = seasonBaseTitle.equals(baseTitle, ignoreCase = true)
                    val isSeasonUncut = season.title.contains("uncut", ignoreCase = true) ||
                            season.title.contains("uncensored", ignoreCase = true)

                    isBaseMatch && (isSeasonUncut == targetIsUncut)
                }

                if (candidateSeason != null) {
                    repository.resolveMAL(candidateSeason.malId).collect { result ->
                        if (result is Result.Success) {
                            matchedAnikotoId = result.data
                        }
                    }
                }
            }

            // 2. Second, fallback to HiAnime scraper search directly
            if (matchedAnikotoId == null) {
                val searchQuery = if (targetIsUncut) "$baseTitle uncut" else baseTitle
                repository.searchHiAnime(searchQuery).collect { result ->
                    if (result is Result.Success) {
                        val searchResults = result.data
                        val matchedItem = searchResults.find { anime ->
                            val animeBaseTitle = anime.title
                                .replace(Regex("(?i)\\s*\\(uncut\\)"), "")
                                .replace(Regex("(?i)\\s*\\(uncensored\\)"), "")
                                .replace(Regex("(?i)\\s*\\(censored\\)"), "")
                                .replace(Regex("(?i)\\s*\\(tv-broadcast\\)"), "")
                                .replace(Regex("(?i)\\s*\\(tv\\)"), "")
                                .trim()

                            val isBaseMatch = animeBaseTitle.equals(baseTitle, ignoreCase = true) ||
                                    anime.title.contains(baseTitle, ignoreCase = true)
                            val isItemUncut = anime.title.contains("uncut", ignoreCase = true) ||
                                    anime.title.contains("uncensored", ignoreCase = true) ||
                                    anime.id.contains("-uncut", ignoreCase = true)

                            isBaseMatch && (isItemUncut == targetIsUncut)
                        }
                        if (matchedItem != null) {
                            matchedAnikotoId = matchedItem.id
                        }
                    }
                }
            }

            _isResolvingSeason.value = false

            if (matchedAnikotoId != null && matchedAnikotoId != currentId) {
                _selectedVersion.value = version
                preferenceManager.preferredAnimeVersion = version
                _resolvedAnikotoId.value = matchedAnikotoId
                _resolutionError.value = "Switched to ${if (targetIsUncut) "Uncut (Uncensored)" else "TV (Censored)"} version!"
            } else {
                // Revert visual selection since we didn't find the alternate version
                val revertedVersion = if (isCurrentUncut) "uncensored" else "censored"
                _selectedVersion.value = revertedVersion
                preferenceManager.preferredAnimeVersion = revertedVersion
                _resolutionError.value = "Alternative ${if (targetIsUncut) "Uncut" else "TV Broadcast"} version not available for this series."
            }
        }
    }

    private val _detailState = MutableStateFlow<DetailState<AnimeDetail>>(DetailState.Loading)
    val detailState: StateFlow<DetailState<AnimeDetail>> = _detailState.asStateFlow()

    private val _episodesState = MutableStateFlow<DetailState<List<Episode>>>(DetailState.Loading)
    val episodesState: StateFlow<DetailState<List<Episode>>> = _episodesState.asStateFlow()

    private val _charactersState = MutableStateFlow<DetailState<List<Character>>>(DetailState.Loading)
    val charactersState: StateFlow<DetailState<List<Character>>> = _charactersState.asStateFlow()

    private val _isWatchlisted = MutableStateFlow(false)
    val isWatchlisted: StateFlow<Boolean> = _isWatchlisted.asStateFlow()

    private val _watchHistory = MutableStateFlow<HistoryItem?>(null)
    val watchHistory: StateFlow<HistoryItem?> = _watchHistory.asStateFlow()

    private val _userRating = MutableStateFlow(0)
    val userRating: StateFlow<Int> = _userRating.asStateFlow()

    private val _seasonsState = MutableStateFlow<DetailState<List<Season>>>(DetailState.Loading)
    val seasonsState: StateFlow<DetailState<List<Season>>> = _seasonsState.asStateFlow()

    private val _resolvedAnikotoId = MutableStateFlow<String?>(null)
    val resolvedAnikotoId: StateFlow<String?> = _resolvedAnikotoId.asStateFlow()

    private val _isResolvingSeason = MutableStateFlow(false)
    val isResolvingSeason: StateFlow<Boolean> = _isResolvingSeason.asStateFlow()

    private val _resolutionError = MutableStateFlow<String?>(null)
    val resolutionError: StateFlow<String?> = _resolutionError.asStateFlow()

    private val _storyArcsState = MutableStateFlow<DetailState<List<StoryArc>>>(DetailState.Loading)
    val storyArcsState: StateFlow<DetailState<List<StoryArc>>> = _storyArcsState.asStateFlow()

    fun loadAnimeData(animeId: String, forceRefresh: Boolean = false) {
        if (animeId.startsWith("mal-")) {
            val malId = animeId.substringAfter("mal-")
            resolveMALAndNavigate(malId)
            return
        }
        _detailState.value = DetailState.Loading
        _episodesState.value = DetailState.Loading
        _charactersState.value = DetailState.Loading
        _seasonsState.value = DetailState.Loading
        _storyArcsState.value = DetailState.Loading

        viewModelScope.launch {
            try {
                val cachedDetail = repository.getCachedAnimeDetail(animeId)
                if (cachedDetail != null && cachedDetail.malId.isNotBlank()) {
                    loadSeasons(cachedDetail.malId)
                }
            } catch (e: Exception) {
                // Squelch
            }
        }

        viewModelScope.launch {
            // Load Anime Details
            repository.getAnimeDetail(animeId, forceRefresh).collect { result ->
                when (result) {
                    is Result.Loading -> _detailState.value = DetailState.Loading
                    is Result.Success -> {
                        _detailState.value = DetailState.Success(result.data)
                        // Sync visual selector to match loaded content version
                        val hasUncutKeywords = result.data.name.contains("uncut", ignoreCase = true) ||
                                result.data.name.contains("uncensored", ignoreCase = true) ||
                                result.data.id.contains("-uncut", ignoreCase = true)
                        _selectedVersion.value = if (hasUncutKeywords) "uncensored" else "censored"
                        checkForAlternativeVersions(result.data)

                        // Load seasons when detail is available
                        if (result.data.malId.isNotBlank()) {
                            val currentSeasonsState = _seasonsState.value
                            val currentSeasons = (currentSeasonsState as? DetailState.Success)?.data
                            if (currentSeasons != null) {
                                val updatedSeasons = injectCurrentSeasonIfMissing(currentSeasons, result.data)
                                _seasonsState.value = DetailState.Success(updatedSeasons)
                            }
                            val alreadyHasSeason = currentSeasons?.any { it.malId == result.data.malId } == true
                            if (!alreadyHasSeason) {
                                loadSeasons(result.data.malId)
                            }
                        } else {
                            _seasonsState.value = DetailState.Success(emptyList())
                        }
                        checkAndLoadStoryArcs()
                    }
                    is Result.Error -> _detailState.value = DetailState.Error(result.message)
                }
            }
        }

        viewModelScope.launch {
            // Load Episodes
            repository.getEpisodes(animeId, forceRefresh).collect { result ->
                when (result) {
                    is Result.Loading -> _episodesState.value = DetailState.Loading
                    is Result.Success -> {
                        _episodesState.value = DetailState.Success(result.data)
                        checkAndLoadStoryArcs()
                    }
                    is Result.Error -> _episodesState.value = DetailState.Error(result.message)
                }
            }
        }

        viewModelScope.launch {
            // Load Characters
            repository.getCharacters(animeId).collect { result ->
                when (result) {
                    is Result.Loading -> _charactersState.value = DetailState.Loading
                    is Result.Success -> _charactersState.value = DetailState.Success(result.data)
                    is Result.Error -> _charactersState.value = DetailState.Error(result.message)
                }
            }
        }

        // Query Firestore status
        val userId = auth.currentUser?.uid
        val profileId = profileManager.activeProfile.value?.id
        if (userId != null) {
            viewModelScope.launch {
                try {
                    // 1. Check watchlist
                    val docRef = if (profileId != null) {
                        firestore.collection("users").document(userId)
                            .collection("profiles").document(profileId)
                            .collection("watchlist").document(animeId)
                    } else {
                        firestore.collection("users").document(userId)
                            .collection("watchlist").document(animeId)
                    }
                    val watchDoc = docRef.get().await()
                    _isWatchlisted.value = watchDoc.exists()
                } catch (e: Exception) {
                    _isWatchlisted.value = false
                }
            }

            refreshWatchHistory(animeId)

            viewModelScope.launch {
                try {
                    // 3. Check rating
                    val docRef = if (profileId != null) {
                        firestore.collection("users").document(userId)
                            .collection("profiles").document(profileId)
                            .collection("ratings").document(animeId)
                    } else {
                        firestore.collection("users").document(userId)
                            .collection("ratings").document(animeId)
                    }
                    val rateDoc = docRef.get().await()
                    _userRating.value = rateDoc.getLong("rating")?.toInt() ?: 0
                } catch (e: Exception) {
                    _userRating.value = 0
                }
            }
        }
    }

    fun toggleWatchlist(animeDetail: AnimeDetail) {
        val userId = auth.currentUser?.uid ?: return
        val profileId = profileManager.activeProfile.value?.id
        viewModelScope.launch {
            val docRef = if (profileId != null) {
                firestore.collection("users").document(userId)
                    .collection("profiles").document(profileId)
                    .collection("watchlist").document(animeDetail.id)
            } else {
                firestore.collection("users").document(userId)
                    .collection("watchlist").document(animeDetail.id)
            }
            if (_isWatchlisted.value) {
                try {
                    docRef.delete().await()
                    _isWatchlisted.value = false
                } catch (e: Exception) {
                    // Handle failure
                }
            } else {
                val data = hashMapOf(
                    "id" to animeDetail.id,
                    "name" to animeDetail.name,
                    "poster" to animeDetail.poster,
                    "status" to "planning",
                    "addedAt" to System.currentTimeMillis()
                )
                try {
                    docRef.set(data).await()
                    _isWatchlisted.value = true
                } catch (e: Exception) {
                    // Handle failure
                }
            }
        }
    }

    fun setRating(animeId: String, rating: Int) {
        val userId = auth.currentUser?.uid ?: return
        val profileId = profileManager.activeProfile.value?.id
        viewModelScope.launch {
            val docRef = if (profileId != null) {
                firestore.collection("users").document(userId)
                    .collection("profiles").document(profileId)
                    .collection("ratings").document(animeId)
            } else {
                firestore.collection("users").document(userId)
                    .collection("ratings").document(animeId)
            }
            try {
                if (rating == 0) {
                    docRef.delete().await()
                    _userRating.value = 0
                } else {
                    val data = hashMapOf(
                        "rating" to rating,
                        "ratedAt" to System.currentTimeMillis()
                    )
                    docRef.set(data).await()
                    _userRating.value = rating
                }
            } catch (e: Exception) {
                // Handle failure
            }
        }
    }

    private fun injectCurrentSeasonIfMissing(seasons: List<Season>, detail: AnimeDetail?): List<Season> {
        if (detail == null || detail.malId.isBlank()) return seasons
        val hasCurrent = seasons.any { it.malId == detail.malId }
        if (!hasCurrent) {
            val currentSeason = Season(
                malId = detail.malId,
                title = detail.name,
                poster = detail.poster,
                episodes = detail.subEpisodes,
                seasonNumber = 0
            )
            return listOf(currentSeason) + seasons
        }
        return seasons
    }

    private fun loadSeasons(malId: String, forceRefresh: Boolean = false) {
        if (malId.isBlank()) {
            _seasonsState.value = DetailState.Success(emptyList())
            return
        }
        viewModelScope.launch {
            repository.getSeasons(malId, forceRefresh).collect { result ->
                when (result) {
                    is Result.Loading -> {
                        if (_seasonsState.value !is DetailState.Success) {
                            _seasonsState.value = DetailState.Loading
                        }
                    }
                    is Result.Success -> {
                        val currentDetail = (detailState.value as? DetailState.Success)?.data
                        val updatedSeasons = injectCurrentSeasonIfMissing(result.data, currentDetail)
                        _seasonsState.value = DetailState.Success(updatedSeasons)
                    }
                    is Result.Error -> {
                        if (_seasonsState.value !is DetailState.Success) {
                            _seasonsState.value = DetailState.Error(result.message)
                        }
                    }
                }
            }
        }
    }

    fun retryLoadSeasons() {
        val currentDetail = (detailState.value as? DetailState.Success)?.data
        val malId = currentDetail?.malId
        if (!malId.isNullOrBlank()) {
            _seasonsState.value = DetailState.Loading
            loadSeasons(malId, forceRefresh = true)
        }
    }

    fun resolveMALAndNavigate(malId: String) {
        _resolvedAnikotoId.value = null
        _resolutionError.value = null
        _isResolvingSeason.value = true
        viewModelScope.launch {
            repository.resolveMAL(malId).collect { result ->
                when (result) {
                    is Result.Loading -> {
                        _isResolvingSeason.value = true
                    }
                    is Result.Success -> {
                        _isResolvingSeason.value = false
                        if (result.data != null) {
                            _resolvedAnikotoId.value = result.data
                        } else {
                            _resolutionError.value = "This season is not available on Megaplay yet."
                        }
                    }
                    is Result.Error -> {
                        _isResolvingSeason.value = false
                        _resolutionError.value = result.message ?: "Failed to resolve season."
                    }
                }
            }
        }
    }

    fun clearResolvedId() {
        _resolvedAnikotoId.value = null
    }

    fun clearResolutionError() {
        _resolutionError.value = null
    }

    fun markAsWatched(animeId: String, title: String, poster: String) {
        val userId = auth.currentUser?.uid ?: return
        val profileId = profileManager.activeProfile.value?.id
        viewModelScope.launch {
            try {
                val docRef = if (profileId != null) {
                    firestore.collection("users").document(userId)
                        .collection("profiles").document(profileId)
                        .collection("history").document(animeId)
                } else {
                    firestore.collection("users").document(userId)
                        .collection("history").document(animeId)
                }
                val data = hashMapOf(
                    "animeId" to animeId,
                    "animeTitle" to title,
                    "poster" to poster,
                    "episodeId" to "",
                    "episodeNumber" to 1,
                    "episodeTitle" to "Finished Watching",
                    "progressPosition" to 100L,
                    "totalDuration" to 100L,
                    "updatedAt" to System.currentTimeMillis()
                )
                docRef.set(data).await()
                _watchHistory.value = HistoryItem(
                    animeId = animeId,
                    animeTitle = title,
                    poster = poster,
                    episodeId = "",
                    episodeNumber = 1,
                    episodeTitle = "Finished Watching",
                    progressPosition = 100L,
                    totalDuration = 100L,
                    updatedAt = System.currentTimeMillis()
                )

                // Also update watchlist status to completed
                val watchlistRef = if (profileId != null) {
                    firestore.collection("users").document(userId)
                        .collection("profiles").document(profileId)
                        .collection("watchlist").document(animeId)
                } else {
                    firestore.collection("users").document(userId)
                        .collection("watchlist").document(animeId)
                }
                val watchlistData = hashMapOf(
                    "id" to animeId,
                    "name" to title,
                    "poster" to poster,
                    "status" to "completed",
                    "addedAt" to System.currentTimeMillis()
                )
                watchlistRef.set(watchlistData, com.google.firebase.firestore.SetOptions.merge()).await()
                _isWatchlisted.value = true
            } catch (e: Exception) {
                // Squelch
            }
        }
    }

    fun removeFromHistory(animeId: String) {
        val userId = auth.currentUser?.uid ?: return
        val profileId = profileManager.activeProfile.value?.id
        viewModelScope.launch {
            try {
                val docRef = if (profileId != null) {
                    firestore.collection("users").document(userId)
                        .collection("profiles").document(profileId)
                        .collection("history").document(animeId)
                } else {
                    firestore.collection("users").document(userId)
                        .collection("history").document(animeId)
                }
                docRef.delete().await()
                _watchHistory.value = null
            } catch (e: Exception) {
                // Squelch
            }
        }
    }

    private fun checkAndLoadStoryArcs() {
        val detail = (detailState.value as? DetailState.Success)?.data
        val eps = (episodesState.value as? DetailState.Success)?.data
        if (detail != null && eps != null) {
            viewModelScope.launch {
                repository.getTmdbStoryArcs(detail.malId, detail.name, eps).collect { result ->
                    when (result) {
                        is Result.Loading -> _storyArcsState.value = DetailState.Loading
                        is Result.Success -> _storyArcsState.value = DetailState.Success(result.data)
                        is Result.Error -> _storyArcsState.value = DetailState.Error(result.message)
                    }
                }
            }
        }
    }

    fun refreshWatchHistory(animeId: String) {
        // 1. Try local cache first for instant UI response
        val localItem = preferenceManager.getLocalHistoryItem(animeId)
        if (localItem != null) {
            _watchHistory.value = localItem
        }

        // 2. Fetch from Firestore in the background to sync
        val userId = auth.currentUser?.uid ?: return
        val profileId = profileManager.activeProfile.value?.id
        viewModelScope.launch {
            try {
                val docRef = if (profileId != null) {
                    firestore.collection("users").document(userId)
                        .collection("profiles").document(profileId)
                        .collection("history").document(animeId)
                } else {
                    firestore.collection("users").document(userId)
                        .collection("history").document(animeId)
                }
                val histDoc = docRef.get().await()
                if (histDoc.exists()) {
                    val dbItem = HistoryItem(
                        animeId = animeId,
                        animeTitle = histDoc.getString("animeTitle") ?: "",
                        poster = histDoc.getString("poster") ?: "",
                        episodeId = histDoc.getString("episodeId") ?: "",
                        episodeNumber = histDoc.getLong("episodeNumber")?.toInt() ?: 1,
                        episodeTitle = histDoc.getString("episodeTitle") ?: "",
                        progressPosition = histDoc.getLong("progressPosition") ?: 0L,
                        totalDuration = histDoc.getLong("totalDuration") ?: 0L,
                        updatedAt = histDoc.getLong("updatedAt") ?: System.currentTimeMillis()
                    )
                    
                    // Only update UI if Firestore has a newer update or local cache is empty
                    if (localItem == null || dbItem.updatedAt > localItem.updatedAt) {
                        _watchHistory.value = dbItem
                        preferenceManager.saveLocalHistoryItem(dbItem)
                    }
                } else {
                    if (localItem == null) {
                        _watchHistory.value = null
                    }
                }
            } catch (e: Exception) {
                // If offline or network fails, keep the local item
                if (localItem == null) {
                    _watchHistory.value = null
                }
            }
        }
    }
}
