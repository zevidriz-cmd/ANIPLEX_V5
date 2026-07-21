package com.aniplex.app.presentation.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniplex.app.data.local.preferences.PreferenceManager
import com.aniplex.app.data.local.preferences.ProfileManager
import com.aniplex.app.domain.model.AnimeDetail
import com.aniplex.app.domain.model.Episode
import com.aniplex.app.domain.model.EpisodeStream
import com.aniplex.app.domain.model.SubtitleTrack
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Success(val stream: EpisodeStream) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
    data class WebViewFallback(val embedUrl: String, val subtitles: List<SubtitleTrack> = emptyList()) : PlayerUiState
    data class IframeFallback(val iframeUrl: String, val provider: String) : PlayerUiState
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
            profileManager.saveSettingsToFirestore()
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
            profileManager.saveSettingsToFirestore()
        }

    var subtitlesEnabled: Boolean
        get() = preferenceManager.subtitlesEnabled
        set(value) {
            preferenceManager.subtitlesEnabled = value
            profileManager.saveSettingsToFirestore()
        }

    var defaultAudioCategory: String
        get() = preferenceManager.defaultAudioCategory
        set(value) {
            preferenceManager.defaultAudioCategory = value
            profileManager.saveSettingsToFirestore()
        }

    val preferredProvider: String
        get() = preferenceManager.preferredProvider

    var subtitleSizeScale: Float
        get() = preferenceManager.subtitleSizeScale
        set(value) {
            preferenceManager.subtitleSizeScale = value
            profileManager.saveSettingsToFirestore()
        }

    var subtitleColor: String
        get() = preferenceManager.subtitleColor
        set(value) {
            preferenceManager.subtitleColor = value
            profileManager.saveSettingsToFirestore()
        }

    var subtitleBgOpacity: Float
        get() = preferenceManager.subtitleBgOpacity
        set(value) {
            preferenceManager.subtitleBgOpacity = value
            profileManager.saveSettingsToFirestore()
        }

    var subtitleStyle: String
        get() = preferenceManager.subtitleStyle
        set(value) {
            preferenceManager.subtitleStyle = value
            profileManager.saveSettingsToFirestore()
        }

    var subtitlePosition: Float
        get() = preferenceManager.subtitlePosition
        set(value) {
            preferenceManager.subtitlePosition = value
            profileManager.saveSettingsToFirestore()
        }



    var preferredQuality: String
        get() = preferenceManager.preferredQuality
        set(value) {
            preferenceManager.preferredQuality = value
            profileManager.saveSettingsToFirestore()
        }

    var preferredAnimeVersion: String
        get() = preferenceManager.preferredAnimeVersion
        set(value) {
            preferenceManager.preferredAnimeVersion = value
            profileManager.saveSettingsToFirestore()
        }

    val hevcDecoderEnabled: Boolean
        get() = preferenceManager.hevcDecoderEnabled

    val dolbyAtmosEnabled: Boolean
        get() = preferenceManager.dolbyAtmosEnabled

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

    private val _currentEpisodeId = MutableStateFlow("")

    private val _skipTimes = MutableStateFlow<SkipTimes>(SkipTimes())
    val skipTimes: StateFlow<SkipTimes> = _skipTimes.asStateFlow()

    private var lastFetchedSkipKey: String? = null
    private var detailsJob: Job? = null
    private var episodesJob: Job? = null
    private var lastAnimeId: String? = null

    private fun fetchSkipTimes(malIdStr: String, episodeNumber: Int) {
        val malId = malIdStr.toIntOrNull() ?: return
        val key = "${malId}_$episodeNumber"
        if (lastFetchedSkipKey == key) return
        lastFetchedSkipKey = key

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

    // Fallback chain state (mirrors website's getBackupStream pipeline)
    private val _activeProvider = MutableStateFlow("zoro")
    val activeProvider: StateFlow<String> = _activeProvider.asStateFlow()

    private val _fallbackStatusMessage = MutableStateFlow<String?>(null)
    val fallbackStatusMessage: StateFlow<String?> = _fallbackStatusMessage.asStateFlow()

    private val _aniNekoTree = MutableStateFlow<com.aniplex.app.data.remote.dto.AniNekoServersResponse?>(null)
    val aniNekoTree: StateFlow<com.aniplex.app.data.remote.dto.AniNekoServersResponse?> = _aniNekoTree.asStateFlow()

    private val _isEnumeratingAniNeko = MutableStateFlow(false)
    val isEnumeratingAniNeko: StateFlow<Boolean> = _isEnumeratingAniNeko.asStateFlow()

    fun setFallbackStatusMessage(message: String?) {
        _fallbackStatusMessage.value = message
    }

    fun fetchAniNekoServers() {
        val title = _animeDetail.value?.name
        val epNum = _currentEpisode.value?.number ?: 1
        if (title.isNullOrBlank()) return

        viewModelScope.launch {
            _isEnumeratingAniNeko.value = true
            repository.getAniNekoServers(title, epNum).collect { result ->
                if (result is Result.Success) {
                    _aniNekoTree.value = result.data
                }
                _isEnumeratingAniNeko.value = false
            }
        }
    }

    fun selectManualServer(provider: String, mode: String? = null, serverId: String? = null) {
        val epId = _currentEpisodeId.value
        val malId = _animeDetail.value?.malId
        val epNum = _currentEpisode.value?.number ?: 1
        val title = _animeDetail.value?.name
        val srv = serverId ?: "hd-1"
        val cat = mode ?: defaultAudioCategory

        // Persist user's mode choice globally across app restarts and auto-advance
        if (mode != null) {
            preferenceManager.defaultAudioCategory = mode
        }

        streamJob?.cancel()
        _uiState.value = PlayerUiState.Loading
        _fallbackStatusMessage.value = "Loading ${mode ?: serverId ?: provider}..."

        streamJob = viewModelScope.launch {
            val (malId, title) = awaitMetadataAndGetDetails()
            if (provider == "zoro") {
                _activeProvider.value = "zoro"
                val zoroCat = if (cat == "hsub") "sub" else cat
                repository.getEpisodeStream(epId, srv, zoroCat).collect { result ->
                    handleStreamResult(result, "zoro", epId, srv, zoroCat)
                }
            } else {
                _activeProvider.value = "gogoanime"
                repository.getFallbackStream(malId, epNum, title, "gogoanime", cat, srv).collect { result ->
                    handleStreamResult(result, "gogoanime", epId, srv, cat)
                }
            }
        }
    }

    private val failedProviders = mutableSetOf<String>()
    private var streamJob: Job? = null

    init {
        DebugLogManager.isLoggingEnabled = enableDiagnostics

        viewModelScope.launch {
            combine(_episodes, _currentEpisodeId) { list, id ->
                list.find { it.id == id }
            }.collect { ep ->
                if (ep != null) {
                    _currentEpisode.value = ep
                }
            }
        }

        viewModelScope.launch {
            combine(_animeDetail, _currentEpisode) { detail, episode ->
                if (detail != null && episode != null) {
                    Pair(detail.malId, episode.number)
                } else null
            }.collect { pair ->
                if (pair != null) {
                    val (malId, epNum) = pair
                    if (malId.isNotEmpty() && malId != "0") {
                        fetchSkipTimes(malId, epNum)
                    }
                }
            }
        }
    }

    fun initialize(animeId: String, episodeId: String, category: String, server: String = "hd-1", initialSavedProgress: Long = 0L, episodeNumber: Int = 0) {
        _uiState.value = PlayerUiState.Loading
        _skipTimes.value = SkipTimes() // Reset skip times on load
        _initialProgress.value = initialSavedProgress // Reset initial progress too to avoid cross-anime progress leak
        _activeProvider.value = preferredProvider
        _fallbackStatusMessage.value = null
        failedProviders.clear()
        
        lastFetchedSkipKey = null
        _currentEpisodeId.value = episodeId

        val isNewAnime = (animeId != lastAnimeId)
        lastAnimeId = animeId

        if (isNewAnime) {
            _animeDetail.value = null
            _currentEpisode.value = null
            _episodes.value = emptyList()
            detailsJob?.cancel()
            episodesJob?.cancel()

            detailsJob = viewModelScope.launch {
                // 1. Fetch Anime Detail (for poster image)
                repository.getAnimeDetail(animeId, false).collect { result ->
                    if (result is Result.Success) {
                        posterUrl = result.data.poster
                        _animeDetail.value = result.data
                    }
                }
            }

            episodesJob = viewModelScope.launch {
                // 2. Fetch Episodes List (to support next/prev navigation)
                repository.getEpisodes(animeId, false).collect { result ->
                    if (result is Result.Success) {
                        _episodes.value = result.data
                    }
                }
            }
        } else {
            // Same anime, reactively update the active episode details instantly from existing list
            val ep = _episodes.value.find { it.id == episodeId }
            if (ep != null) {
                _currentEpisode.value = ep
            }
        }

        viewModelScope.launch {
            // 1. Try local cache first for zero-latency resume
            try {
                val localItem = preferenceManager.getLocalHistoryItem(animeId)
                if (localItem != null) {
                    val epNum = if (episodeNumber > 0) episodeNumber else (_currentEpisode.value?.number ?: 0)
                    val matchesEpisode = (localItem.episodeId == episodeId) || (localItem.episodeNumber > 0 && epNum > 0 && localItem.episodeNumber == epNum)
                    if (matchesEpisode && _initialProgress.value <= 0L) {
                        _initialProgress.value = localItem.progressPosition
                        DebugLogManager.log("ANIPLEX_PROGRESS", "Successfully resolved and loaded initialProgress from local history cache: ${localItem.progressPosition}")
                    }
                }
                
                // If not matched or still 0, try specific local progress by ID/Number
                if (_initialProgress.value <= 0L) {
                    val localProgress = preferenceManager.getLocalWatchProgress(animeId, episodeId, episodeNumber)
                    if (localProgress > 0L) {
                        _initialProgress.value = localProgress
                        DebugLogManager.log("ANIPLEX_PROGRESS", "Successfully resolved and loaded initialProgress from local progress cache: $localProgress")
                    }
                }
            } catch (e: Exception) {
                DebugLogManager.log("ANIPLEX_PROGRESS", "Error checking local watch history cache: ${e.message}")
            }

            // 2. Fetch Watch History from Firestore (to sync with other devices)
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
                        val savedEpisodeNum = doc.getLong("episodeNumber")?.toInt() ?: 0
                        val epNum = if (episodeNumber > 0) episodeNumber else (_currentEpisode.value?.number ?: 0)
                        val matchesEpisode = (savedEpisodeId == episodeId) || (savedEpisodeNum > 0 && epNum > 0 && savedEpisodeNum == epNum)
                        DebugLogManager.log("ANIPLEX_PROGRESS", "Firestore History doc found. savedEpisodeId: $savedEpisodeId, savedEpisodeNum: $savedEpisodeNum, epNum: $epNum, matches: $matchesEpisode")
                        if (matchesEpisode) {
                            val dbProgress = doc.getLong("progressPosition") ?: 0L
                            if (_initialProgress.value <= 0L) {
                                _initialProgress.value = dbProgress
                                DebugLogManager.log("ANIPLEX_PROGRESS", "Successfully resolved and loaded initialProgress from Firestore: $dbProgress")
                            }
                            
                            // Keep local cache synced if db has a newer/different value
                            val localProgress = preferenceManager.getLocalWatchProgress(animeId, episodeId, episodeNumber)
                            if (dbProgress > 0L && dbProgress != localProgress) {
                                val animeTitle = doc.getString("animeTitle") ?: ""
                                val poster = doc.getString("poster") ?: ""
                                val episodeTitle = doc.getString("episodeTitle") ?: ""
                                val item = com.aniplex.app.domain.model.HistoryItem(
                                    animeId = animeId,
                                    animeTitle = animeTitle,
                                    poster = poster,
                                    episodeId = savedEpisodeId ?: episodeId,
                                    episodeNumber = savedEpisodeNum,
                                    episodeTitle = episodeTitle,
                                    progressPosition = dbProgress,
                                    totalDuration = doc.getLong("totalDuration") ?: 0L,
                                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                                )
                                preferenceManager.saveLocalHistoryItem(item)
                            }
                        }
                    } else {
                        DebugLogManager.log("ANIPLEX_PROGRESS", "No watch history document exists in Firestore for animeId: $animeId")
                    }
                } catch (e: Exception) {
                    DebugLogManager.log("ANIPLEX_PROGRESS", "Error fetching watch history from Firestore: ${e.message}", e)
                }
            }
        }

        // 4. Clean up old season history/watchlist entries from the same franchise
        if (isNewAnime) {
            viewModelScope.launch {
                try {
                    val userId = auth.currentUser?.uid ?: return@launch
                    val profileId = profileManager.activeProfile.value?.id

                    // Wait for anime detail to be available
                    val malId = awaitMetadataAndGetMalId() ?: return@launch

                    // Get seasons for the current anime
                    var seasons: List<com.aniplex.app.domain.model.Season> = emptyList()
                    repository.getSeasons(malId).collect { result ->
                        if (result is Result.Success) {
                            seasons = result.data
                        }
                    }

                    if (seasons.size <= 1) return@launch

                    // Find current anime in seasons to check its relationType
                    val currentSeasonObj = seasons.find { it.resolvedId == animeId || it.malId == malId }
                    
                    // Only perform cleanup if current anime is a MAIN season
                    if (currentSeasonObj?.relationType == "MAIN") {
                        // Get all related animeIds from the franchise (excluding current) that are also MAIN seasons
                        val relatedAnimeIds = seasons
                            .filter { it.relationType == "MAIN" && it.resolvedId != null && it.resolvedId != animeId }
                            .mapNotNull { it.resolvedId }

                        for (oldAnimeId in relatedAnimeIds) {
                            // Delete old season's history entry
                            val oldHistRef = if (profileId != null) {
                                firestore.collection("users").document(userId)
                                    .collection("profiles").document(profileId)
                                    .collection("history").document(oldAnimeId)
                            } else {
                                firestore.collection("users").document(userId)
                                    .collection("history").document(oldAnimeId)
                            }
                            val oldHistSnap = oldHistRef.get().await()
                            if (oldHistSnap.exists()) {
                                oldHistRef.delete().await()
                                DebugLogManager.log("ANIPLEX_PLAYER", "Cleaned up old season history for animeId: $oldAnimeId")
                            }

                            // Delete old season's watchlist entry only if it was "watching" (not "completed")
                            val oldWatchlistRef = if (profileId != null) {
                                firestore.collection("users").document(userId)
                                    .collection("profiles").document(profileId)
                                    .collection("watchlist").document(oldAnimeId)
                            } else {
                                firestore.collection("users").document(userId)
                                    .collection("watchlist").document(oldAnimeId)
                            }
                            val oldWatchlistSnap = oldWatchlistRef.get().await()
                            if (oldWatchlistSnap.exists()) {
                                val oldStatus = oldWatchlistSnap.getString("status")
                                if (oldStatus == "watching" || oldStatus == "planning") {
                                    oldWatchlistRef.delete().await()
                                    DebugLogManager.log("ANIPLEX_PLAYER", "Cleaned up old season watchlist ($oldStatus) for animeId: $oldAnimeId")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Non-blocking
                }
            }
        }

        loadPlaybackStream(episodeId, server, category)
    }

    private fun loadPlaybackStream(
        episodeId: String,
        server: String,
        category: String
    ) {
        streamJob?.cancel()

        // Define the try sequence matching the website
        val sequence = if (preferredProvider == "gogoanime") {
            listOf("gogoanime", "zoro", "animepahe", "megaplay-direct")
        } else {
            listOf("zoro", "gogoanime", "animepahe", "megaplay-direct")
        }

        // Find the first provider not in failedProviders
        val provider = sequence.firstOrNull { it !in failedProviders }
        if (provider == null) {
            // All direct providers failed, go to iframe fallback
            loadIframeFallback(category)
            return
        }

        _activeProvider.value = provider
        DebugLogManager.log("ANIPLEX_PLAYER", "Attempting provider: $provider")

        when (provider) {
            "zoro" -> {
                _fallbackStatusMessage.value = if (preferredProvider == "zoro") {
                    "Connecting to primary server (Zoro)..."
                } else {
                    "Gogoanime failed. Switching to Zoro..."
                }
                streamJob = viewModelScope.launch {
                    val zoroCat = if (category == "hsub") "sub" else category
                    repository.getEpisodeStream(episodeId, server, zoroCat).collect { result ->
                        handleStreamResult(result, provider, episodeId, server, zoroCat)
                    }
                }
            }
            "gogoanime", "animepahe" -> {
                _fallbackStatusMessage.value = if (provider == "gogoanime") {
                    "Connecting to Gogoanime backup..."
                } else {
                    "Gogoanime failed. Fetching AnimePahe backup..."
                }
                streamJob = viewModelScope.launch {
                    val (malId, title) = awaitMetadataAndGetDetails()
                    val epNum = _currentEpisode.value?.number ?: 1

                    if (malId == null && title.isNullOrEmpty()) {
                        DebugLogManager.log("ANIPLEX_PLAYER", "MAL metadata unavailable for provider $provider, skipping...")
                        failedProviders.add(provider)
                        loadPlaybackStream(episodeId, server, category)
                        return@launch
                    }

                    repository.getFallbackStream(malId, epNum, title, provider, category, server).collect { result ->
                        handleStreamResult(result, provider, episodeId, server, category)
                    }
                }
            }
            "megaplay-direct" -> {
                _fallbackStatusMessage.value = "Extracting MegaPlay direct stream..."
                streamJob = viewModelScope.launch {
                    val (malId, _) = awaitMetadataAndGetDetails()
                    val epNum = _currentEpisode.value?.number ?: 1

                    if (malId == null) {
                        DebugLogManager.log("ANIPLEX_PLAYER", "MAL ID unavailable for MegaPlay, skipping...")
                        failedProviders.add(provider)
                        loadPlaybackStream(episodeId, server, category)
                        return@launch
                    }

                    repository.getMegaplayDirectStream(malId, epNum, category).collect { result ->
                        handleStreamResult(result, provider, episodeId, server, category)
                    }
                }
            }
        }
    }

    private suspend fun awaitMetadataAndGetDetails(): Pair<String?, String?> {
        var count = 0
        while ((_animeDetail.value == null || _animeDetail.value?.name.isNullOrEmpty() || _currentEpisode.value == null) && count < 50) {
            delay(200)
            count++
        }
        val detail = _animeDetail.value
        val malId = detail?.malId?.takeIf { it.isNotEmpty() && it != "0" }
        val title = detail?.name?.takeIf { it.isNotEmpty() }
        return Pair(malId, title)
    }

    private suspend fun awaitMetadataAndGetMalId(): String? = awaitMetadataAndGetDetails().first

    private fun handleStreamResult(
        result: Result<EpisodeStream>,
        provider: String,
        episodeId: String,
        server: String,
        category: String
    ) {
        when (result) {
            is Result.Success -> {
                if (result.data.isHls) {
                    _uiState.value = PlayerUiState.Success(stream = result.data)
                } else {
                    _uiState.value = PlayerUiState.WebViewFallback(embedUrl = result.data.videoUrl, subtitles = result.data.subtitles)
                }
                _fallbackStatusMessage.value = when (provider) {
                    "zoro" -> null
                    "gogoanime" -> "Streaming via Gogoanime backup."
                    "animepahe" -> "Streaming via AnimePahe backup."
                    "megaplay-direct" -> "Streaming via MegaPlay direct."
                    else -> null
                }
            }
            is Result.Error -> {
                DebugLogManager.log("ANIPLEX_PLAYER", "Provider $provider failed: ${result.message}")
                failedProviders.add(provider)
                loadPlaybackStream(episodeId, server, category)
            }
            is Result.Loading -> {
                _uiState.value = PlayerUiState.Loading
            }
        }
    }

    /**
     * Run the full fallback chain exactly as the website does:
     * 1. Gogoanime (Netlify serverless API)
     * 2. AnimePahe (Netlify serverless API)
     * 3. MegaPlay Direct (.m3u8 extraction)
     * 4. MegaPlay Iframe (fullscreen WebView as absolute last resort)
     *
     * Called by PlayerScreen when all Zoro WebView sniffing servers have failed.
     */
    fun runFallbackChain(audioCategory: String) {
        failedProviders.add("zoro")
        val epId = _currentEpisode.value?.id ?: ""
        if (epId.isNotEmpty()) {
            loadPlaybackStream(
                episodeId = epId,
                server = "hd-1",
                category = audioCategory
            )
        } else {
            loadIframeFallback(audioCategory)
        }
    }

    fun handlePlaybackError(audioCategory: String) {
        val current = _activeProvider.value
        DebugLogManager.log("ANIPLEX_PLAYER", "Playback failed for provider: $current. Adding to failed providers.")
        failedProviders.add(current)
        
        val epId = _currentEpisode.value?.id ?: ""
        if (epId.isNotEmpty() && current != "megaplay-iframe") {
            loadPlaybackStream(
                episodeId = epId,
                server = "hd-1",
                category = audioCategory
            )
        } else {
            loadIframeFallback(audioCategory)
        }
    }

    private fun loadIframeFallback(audioCategory: String) {
        val malId = _animeDetail.value?.malId
        val epNum = _currentEpisode.value?.number ?: 1

        if (malId.isNullOrEmpty() || malId == "0") {
            _fallbackStatusMessage.value = "No MAL ID available. Loading backup iframe player..."
            _uiState.value = PlayerUiState.IframeFallback(
                iframeUrl = "about:blank",
                provider = "megaplay"
            )
            return
        }

        _fallbackStatusMessage.value = "All HLS streams failed. Loaded Backup Player (Iframe)."
        _activeProvider.value = "megaplay-iframe"
        val iframeUrl = "https://megaplay.buzz/stream/mal/$malId/$epNum/$audioCategory"
        _uiState.value = PlayerUiState.IframeFallback(
            iframeUrl = iframeUrl,
            provider = "megaplay"
        )
    }

    /**
     * Switch the iframe backup server (matches website's Backup Server selector).
     */
    fun switchIframeServer(server: String) {
        val malId = _animeDetail.value?.malId ?: return
        val epNum = _currentEpisode.value?.number ?: 1
        val audioCategory = defaultAudioCategory.lowercase()

        val iframeUrl = when (server) {
            "megaplay" -> "https://megaplay.buzz/stream/mal/$malId/$epNum/$audioCategory"
            "vidsrc-to" -> "https://vidsrc.to/embed/anime/$malId/$epNum"
            "vidsrc-me" -> "https://vidsrc.me/embed/anime?mal=$malId&ep=$epNum"
            "embed-su" -> "https://embed.su/embed/anime/$malId/$epNum"
            else -> "https://megaplay.buzz/stream/mal/$malId/$epNum/$audioCategory"
        }

        _activeProvider.value = "$server-iframe"
        _uiState.value = PlayerUiState.IframeFallback(
            iframeUrl = iframeUrl,
            provider = server
        )
    }

    fun stopPeriodicProgressSaving() {
        // Obsolete
    }

    private val progressSaveScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

        progressSaveScope.launch {
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

                val historyItem = com.aniplex.app.domain.model.HistoryItem(
                    animeId = animeId,
                    animeTitle = animeTitle,
                    poster = posterUrl ?: "",
                    episodeId = finalEpisodeId,
                    episodeNumber = finalEpisodeNumber,
                    episodeTitle = finalEpisodeTitle,
                    progressPosition = finalProgress,
                    totalDuration = finalDuration,
                    updatedAt = System.currentTimeMillis()
                )
                preferenceManager.saveLocalHistoryItem(historyItem)
                DebugLogManager.log("ANIPLEX_PROGRESS", "Saved progress locally: $finalProgress / $finalDuration")

                val data = hashMapOf(
                    "animeId" to animeId,
                    "animeTitle" to animeTitle,
                    "poster" to (posterUrl ?: ""),
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
                
                DebugLogManager.log("ANIPLEX_PROGRESS", "Saving progress to Firestore doc: ${docRef.path}. progress: $finalProgress / $finalDuration")
                docRef.set(data).await()

                // Also update/sync Watchlist status in Firestore
                val watchlistRef = if (profileId != null) {
                    firestore.collection("users").document(userId)
                        .collection("profiles").document(profileId)
                        .collection("watchlist").document(animeId)
                } else {
                    firestore.collection("users").document(userId)
                        .collection("watchlist").document(animeId)
                }

                val isLastEpisode = currentIndex == currentList.size - 1
                val targetStatus = if (isNearEnd && isLastEpisode) "completed" else "watching"

                val watchlistData = hashMapOf(
                    "id" to animeId,
                    "name" to animeTitle,
                    "poster" to posterUrl,
                    "status" to targetStatus,
                    "addedAt" to System.currentTimeMillis()
                )
                watchlistRef.set(watchlistData, com.google.firebase.firestore.SetOptions.merge()).await()
                DebugLogManager.log("ANIPLEX_PROGRESS", "Successfully saved progress to Firestore!")
            } catch (e: Exception) {
                DebugLogManager.log("ANIPLEX_PROGRESS", "Error saving progress to Firestore: ${e.message}", e)
            }
        }
    }

    fun getBackupSubtitles(onBackupSubtitlesFound: (List<com.aniplex.app.domain.model.SubtitleTrack>) -> Unit) {
        viewModelScope.launch {
            val malId = awaitMetadataAndGetMalId()
            val title = _animeDetail.value?.name
            val epNum = _currentEpisode.value?.number ?: 1

            if (malId == null && title.isNullOrEmpty()) return@launch

            val providers = listOf("gogoanime", "animepahe")
            for (provider in providers) {
                var found = false
                try {
                    repository.getFallbackStream(malId, epNum, title, provider).collect { result ->
                        if (result is Result.Success && result.data.subtitles.isNotEmpty()) {
                            onBackupSubtitlesFound(result.data.subtitles)
                            found = true
                        }
                    }
                    if (found) break
                } catch (e: Exception) {
                    // Ignore
                }
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

    fun getNextSeasonFirstEpisode(
        currentAnimeId: String,
        onNextSeasonFound: (nextAnimeId: String, nextEpId: String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val malId = awaitMetadataAndGetMalId() ?: return@launch
                var seasons: List<com.aniplex.app.domain.model.Season> = emptyList()
                repository.getSeasons(malId).collect { result ->
                    if (result is Result.Success) {
                        seasons = result.data
                    }
                }

                if (seasons.isEmpty()) return@launch

                val mainSeasons = seasons.filter { it.relationType == "MAIN" || it.seasonNumber > 0 }
                val currentSeasonIndex = mainSeasons.indexOfFirst { 
                    it.resolvedId == currentAnimeId || it.malId == malId 
                }

                if (currentSeasonIndex != -1 && currentSeasonIndex < mainSeasons.size - 1) {
                    val nextSeason = mainSeasons[currentSeasonIndex + 1]
                    var targetResolvedId = nextSeason.resolvedId
                    if (targetResolvedId.isNullOrBlank() && nextSeason.malId.isNotBlank()) {
                        repository.resolveMAL(nextSeason.malId).collect { res ->
                            if (res is Result.Success) {
                                targetResolvedId = res.data
                            }
                        }
                    }
                    val nextAnimeId = targetResolvedId ?: return@launch

                    var nextEpisodes: List<com.aniplex.app.domain.model.Episode> = emptyList()
                    repository.getEpisodes(nextAnimeId, false).collect { result ->
                        if (result is Result.Success) {
                            nextEpisodes = result.data
                        }
                    }

                    if (nextEpisodes.isNotEmpty()) {
                        val firstEpId = nextEpisodes.first().id
                        DebugLogManager.log("ANIPLEX_PLAYER", "Cross-season autoplay to next season: ${nextSeason.title} (animeId: $nextAnimeId)")
                        onNextSeasonFound(nextAnimeId, firstEpId)
                    }
                }
            } catch (e: Exception) {
                DebugLogManager.log("ANIPLEX_PLAYER", "Error in getNextSeasonFirstEpisode: $e")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPeriodicProgressSaving()
    }
}
