package com.aniplex.app.presentation.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniplex.app.data.local.preferences.PreferenceManager
import com.aniplex.app.data.local.preferences.ProfileManager
import com.aniplex.app.domain.model.AnimeDetail
import com.aniplex.app.domain.model.Episode
import com.aniplex.app.domain.model.EpisodeStream
import com.aniplex.app.domain.model.SkipTimes
import com.aniplex.app.domain.model.Result
import com.aniplex.app.domain.repository.AnimeRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Success(val stream: EpisodeStream) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
    data class WebViewFallback(val embedUrl: String) : PlayerUiState
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: AnimeRepository,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val preferenceManager: PreferenceManager,
    private val profileManager: ProfileManager
) : ViewModel() {

    var autoplayNextEpisode: Boolean
        get() = preferenceManager.autoplayNextEpisode
        set(value) {
            preferenceManager.autoplayNextEpisode = value
        }

    var enableDiagnostics: Boolean
        get() = preferenceManager.enableDiagnostics
        set(value) {
            preferenceManager.enableDiagnostics = value
            DebugLogManager.isLoggingEnabled = value
        }

    val skipIntro: Boolean
        get() = preferenceManager.skipIntro

    val skipOutro: Boolean
        get() = preferenceManager.skipOutro

    var playbackSpeed: Float
        get() = preferenceManager.playbackSpeed
        set(value) {
            preferenceManager.playbackSpeed = value
        }

    var subtitlesEnabled: Boolean
        get() = preferenceManager.subtitlesEnabled
        set(value) {
            preferenceManager.subtitlesEnabled = value
        }

    var defaultAudioCategory: String
        get() = preferenceManager.defaultAudioCategory
        set(value) {
            preferenceManager.defaultAudioCategory = value
        }

    init {
        DebugLogManager.isLoggingEnabled = enableDiagnostics
    }

    var preferredQuality: String
        get() = preferenceManager.preferredQuality
        set(value) {
            preferenceManager.preferredQuality = value
        }

    var preferredAnimeVersion: String
        get() = preferenceManager.preferredAnimeVersion
        set(value) {
            preferenceManager.preferredAnimeVersion = value
        }

    fun setPreferredAnimeVersion(version: String, onStatusMsg: (String) -> Unit) {
        preferenceManager.preferredAnimeVersion = version
        val currentDetail = animeDetail.value
        val currentEp = currentEpisode.value ?: return
        if (currentDetail == null) return

        val currentId = currentDetail.id
        val currentTitle = currentDetail.name
        val malId = currentDetail.malId

        // Option A Manual Redirection Bypass for "Chained Soldier" and "Gushing over Magical Girls"
        if (currentId == "6245" || malId == "50392" || currentId == "5926" || malId == "54722") {
            val targetIsUncut = version == "uncensored"
            onStatusMsg("Switched/Reloading ${if (targetIsUncut) "Uncut (Uncensored)" else "TV (Censored)"} version!")
            initialize(currentId, currentEp.id, defaultAudioCategory.lowercase())
            return
        }

        // Determine if current anime is uncut
        val isCurrentUncut = currentTitle.contains("uncut", ignoreCase = true) ||
                currentTitle.contains("uncensored", ignoreCase = true) ||
                currentId.contains("-uncut", ignoreCase = true)

        val targetIsUncut = version == "uncensored"

        if (isCurrentUncut == targetIsUncut) {
            onStatusMsg("Already playing ${if (targetIsUncut) "Uncut" else "TV"} version.")
            return
        }

        onStatusMsg("Searching for ${if (targetIsUncut) "Uncut" else "TV"} version...")

        viewModelScope.launch {
            val baseTitle = currentTitle
                .replace(Regex("(?i)\\s*\\(uncut\\)"), "")
                .replace(Regex("(?i)\\s*\\(uncensored\\)"), "")
                .replace(Regex("(?i)\\s*\\(censored\\)"), "")
                .replace(Regex("(?i)\\s*\\(tv-broadcast\\)"), "")
                .replace(Regex("(?i)\\s*\\(tv\\)"), "")
                .trim()

            var matchedAnikotoId: String? = null

            // search direct HiAnime scraper
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

            if (matchedAnikotoId != null && matchedAnikotoId != currentId) {
                // Fetch episodes of matching alternative version
                repository.getEpisodes(matchedAnikotoId!!, false).collect { result ->
                    if (result is Result.Success) {
                        val matchingEp = result.data.find { it.number == currentEp.number } ?: result.data.firstOrNull()
                        if (matchingEp != null) {
                            onStatusMsg("Switched/Reloading ${if (targetIsUncut) "Uncut (Uncensored)" else "TV (Censored)"} version!")
                            initialize(matchedAnikotoId!!, matchingEp.id, defaultAudioCategory.lowercase())
                        } else {
                            onStatusMsg("Corresponding episode of ${if (targetIsUncut) "Uncut" else "TV"} version not found.")
                        }
                    } else if (result is Result.Error) {
                        onStatusMsg("Failed to load alternative episodes.")
                    }
                }
            } else {
                preferenceManager.preferredAnimeVersion = if (isCurrentUncut) "uncensored" else "censored"
                onStatusMsg("Alternative ${if (targetIsUncut) "Uncut" else "TV Broadcast"} version not available for this series.")
            }
        }
    }


    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _episodes = MutableStateFlow<List<Episode>>(emptyList())
    val episodes: StateFlow<List<Episode>> = _episodes.asStateFlow()

    private val _initialProgress = MutableStateFlow(0L)
    val initialProgress: StateFlow<Long> = _initialProgress.asStateFlow()

    private val _animeDetail = MutableStateFlow<AnimeDetail?>(null)
    val animeDetail: StateFlow<AnimeDetail?> = _animeDetail.asStateFlow()

    private val _currentEpisode = MutableStateFlow<Episode?>(null)
    val currentEpisode: StateFlow<Episode?> = _currentEpisode.asStateFlow()

    private val _skipTimes = MutableStateFlow<SkipTimes>(SkipTimes())
    val skipTimes: StateFlow<SkipTimes> = _skipTimes.asStateFlow()

    private fun fetchSkipTimes(malIdStr: String, episodeNumber: Int) {
        val malId = malIdStr.toIntOrNull() ?: return
        viewModelScope.launch {
            repository.getSkipTimes(malId, episodeNumber).collect { result ->
                if (result is Result.Success) {
                    _skipTimes.value = result.data
                }
            }
        }
    }

    private val _likeCount = MutableStateFlow(16800)
    val likeCount: StateFlow<Int> = _likeCount.asStateFlow()
    
    private val _isLiked = MutableStateFlow(false)
    val isLiked: StateFlow<Boolean> = _isLiked.asStateFlow()

    private val _dislikeCount = MutableStateFlow(36)
    val dislikeCount: StateFlow<Int> = _dislikeCount.asStateFlow()
    
    private val _isDisliked = MutableStateFlow(false)
    val isDisliked: StateFlow<Boolean> = _isDisliked.asStateFlow()

    private var progressSaveJob: Job? = null
    private var posterUrl: String = ""

    fun initialize(animeId: String, episodeId: String, category: String, server: String = "hd-1", initialSavedProgress: Long = 0L) {
        _uiState.value = PlayerUiState.Loading
        _skipTimes.value = SkipTimes() // Reset skip times on load
        _initialProgress.value = initialSavedProgress // Reset initial progress too to avoid cross-anime progress leak
        
        viewModelScope.launch {
            // 1. Fetch Anime Detail (for poster image)
            repository.getAnimeDetail(animeId, false).collect { result ->
                if (result is Result.Success) {
                    posterUrl = result.data.poster
                    _animeDetail.value = result.data
                    
                    val epNum = _currentEpisode.value?.number
                    val mId = result.data.malId
                    if (epNum != null && mId.isNotEmpty()) {
                        fetchSkipTimes(mId, epNum)
                    }
                }
            }
        }

        viewModelScope.launch {
            // 2. Fetch Episodes List (to support next/prev navigation)
            repository.getEpisodes(animeId, false).collect { result ->
                if (result is Result.Success) {
                    _episodes.value = result.data
                    val ep = result.data.find { it.id == episodeId }
                    _currentEpisode.value = ep
                    
                    val mId = _animeDetail.value?.malId
                    if (ep != null && mId != null && mId.isNotEmpty()) {
                        fetchSkipTimes(mId, ep.number)
                    }
                }
            }
        }

        viewModelScope.launch {
            // 3. Fetch Watch History (to resume playback)
            val userId = auth.currentUser?.uid
            val profileId = profileManager.activeProfile.value?.id
            if (userId != null) {
                try {
                    val docRef = if (profileId != null) {
                        firestore.collection("users").document(userId)
                            .collection("profiles").document(profileId)
                            .collection("history").document(animeId)
                    } else {
                        firestore.collection("users").document(userId)
                            .collection("history").document(animeId)
                    }
                    val doc = docRef.get().await()
                    if (doc.exists()) {
                        val savedEpisodeId = doc.getString("episodeId")
                        if (savedEpisodeId == episodeId) {
                            val dbProgress = doc.getLong("progressPosition") ?: 0L
                            if (_initialProgress.value <= 0L) {
                                _initialProgress.value = dbProgress
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore, default to 0
                }
            }
        }

        viewModelScope.launch {
            repository.getEpisodeStream(episodeId, server, category).collect { result ->
                when (result) {
                    is Result.Success -> {
                        if (result.data.isHls) {
                            _uiState.value = PlayerUiState.Success(stream = result.data)
                        } else {
                            _uiState.value = PlayerUiState.WebViewFallback(embedUrl = result.data.videoUrl)
                        }
                    }
                    is Result.Error -> {
                        _uiState.value = PlayerUiState.Error(result.message)
                    }
                    is Result.Loading -> {
                        _uiState.value = PlayerUiState.Loading
                    }
                }
            }
        }
    }

    fun stopPeriodicProgressSaving() {
        // Obsolete
    }

    fun saveProgress(
        animeId: String,
        animeTitle: String,
        episodeId: String,
        episodeNumber: Int,
        episodeTitle: String,
        progress: Long,
        duration: Long
    ) {
        val userId = auth.currentUser?.uid ?: return
        val profileId = profileManager.activeProfile.value?.id
        if (progress <= 0 || duration <= 0) return

        viewModelScope.launch {
            try {
                // Check if user is near the end of the episode (90% or higher, or last 2 minutes / 120 seconds)
                val currentList = _episodes.value
                val currentIndex = currentList.indexOfFirst { it.id == episodeId || it.number == episodeNumber }

                var finalEpisodeId = episodeId
                var finalEpisodeNumber = episodeNumber
                var finalEpisodeTitle = episodeTitle
                var finalProgress = progress
                var finalDuration = duration

                val isNearEnd = (duration > 0L) && (progress.toFloat() / duration.toFloat() >= 0.90f || (duration - progress) <= 120_000L)
                if (isNearEnd && currentIndex != -1 && currentIndex < currentList.size - 1) {
                    val nextEp = currentList[currentIndex + 1]
                    finalEpisodeId = nextEp.id
                    finalEpisodeNumber = nextEp.number
                    finalEpisodeTitle = nextEp.title
                    finalProgress = 0L
                    finalDuration = 0L // clean resume state for the next episode
                }

                val data = hashMapOf(
                    "animeId" to animeId,
                    "animeTitle" to animeTitle,
                    "poster" to posterUrl,
                    "episodeId" to finalEpisodeId,
                    "episodeNumber" to finalEpisodeNumber,
                    "episodeTitle" to finalEpisodeTitle,
                    "progressPosition" to finalProgress,
                    "totalDuration" to finalDuration,
                    "updatedAt" to System.currentTimeMillis()
                )
                val docRef = if (profileId != null) {
                    firestore.collection("users").document(userId)
                        .collection("profiles").document(profileId)
                        .collection("history").document(animeId)
                } else {
                    firestore.collection("users").document(userId)
                        .collection("history").document(animeId)
                }
                docRef.set(data).await()
            } catch (e: Exception) {
                // Ignore silent error
            }
        }
    }

    fun toggleLike() {
        if (_isLiked.value) {
            _isLiked.value = false
            _likeCount.value -= 1
        } else {
            _isLiked.value = true
            _likeCount.value += 1
            if (_isDisliked.value) toggleDislike()
        }
    }

    fun toggleDislike() {
        if (_isDisliked.value) {
            _isDisliked.value = false
            _dislikeCount.value -= 1
        } else {
            _isDisliked.value = true
            _dislikeCount.value += 1
            if (_isLiked.value) toggleLike()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPeriodicProgressSaving()
    }
}
