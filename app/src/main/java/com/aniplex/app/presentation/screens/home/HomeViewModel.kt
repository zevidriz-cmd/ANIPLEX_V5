package com.aniplex.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniplex.app.data.local.preferences.PreferenceManager
import com.aniplex.app.data.local.preferences.ProfileManager
import com.aniplex.app.domain.model.Anime
import com.aniplex.app.domain.model.HistoryItem
import com.aniplex.app.domain.model.HomeData
import com.aniplex.app.domain.model.Result
import com.aniplex.app.domain.repository.AnimeRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val homeData: HomeData) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: AnimeRepository,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val preferenceManager: PreferenceManager,
    private val profileManager: ProfileManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _recentlyAddedEpisodes = MutableStateFlow<List<RecentlyAddedItem>>(emptyList())
    val recentlyAddedEpisodes: StateFlow<List<RecentlyAddedItem>> = _recentlyAddedEpisodes.asStateFlow()

    val continueWatchingList: StateFlow<List<HistoryItem>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val profileId = profileManager.activeProfile.value?.id
        val collectionRef = if (profileId != null) {
            firestore.collection("users").document(userId)
                .collection("profiles").document(profileId)
                .collection("history")
        } else {
            firestore.collection("users").document(userId)
                .collection("history")
        }

        val listener = collectionRef
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        try {
                            val progress = doc.getLong("progressPosition") ?: 0L
                            val duration = doc.getLong("totalDuration") ?: 0L
                            // Filter out completed episodes/seasons (progress >= 95% of total duration)
                            val isFinished = duration > 0L && (progress.toFloat() / duration.toFloat() >= 0.95f)
                            if (isFinished) {
                                null
                            } else {
                                HistoryItem(
                                    animeId = doc.getString("animeId") ?: doc.id,
                                    animeTitle = doc.getString("animeTitle") ?: "",
                                    poster = doc.getString("poster") ?: "",
                                    episodeId = doc.getString("episodeId") ?: "",
                                    episodeNumber = doc.getLong("episodeNumber")?.toInt() ?: 1,
                                    episodeTitle = doc.getString("episodeTitle") ?: "",
                                    progressPosition = progress,
                                    totalDuration = duration,
                                    updatedAt = doc.getLong("updatedAt") ?: 0L
                                )
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                    trySend(items)
                } else {
                    trySend(emptyList())
                }
            }

        awaitClose { listener.remove() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        getHomePage(forceRefresh = false)
        loadRecentlyAddedEpisodes()
    }

    fun getHomePage(forceRefresh: Boolean) {
        viewModelScope.launch {
            repository.getHomePage(forceRefresh).collect { result ->
                when (result) {
                    is Result.Loading -> {
                        _uiState.value = HomeUiState.Loading
                    }
                    is Result.Success -> {
                        _uiState.value = HomeUiState.Success(result.data)
                    }
                    is Result.Error -> {
                        _uiState.value = HomeUiState.Error(result.message)
                    }
                }
            }
        }
    }

    fun refresh() {
        getHomePage(forceRefresh = true)
        loadRecentlyAddedEpisodes()
    }

    fun loadRecentlyAddedEpisodes() {
        viewModelScope.launch(Dispatchers.IO) {
            val sdfStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            
            val calendar = java.util.Calendar.getInstance()
            val todayStr = sdfStr.format(calendar.time)
            
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            val yesterdayStr = sdfStr.format(calendar.time)
            
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            val beforeYesterdayStr = sdfStr.format(calendar.time)

            val dates = listOf(todayStr, yesterdayStr, beforeYesterdayStr)
            val itemsList = java.util.Collections.synchronizedList(mutableListOf<RecentlyAddedItem>())

            val jobs = dates.map { dateStr ->
                launch {
                    var result: Result<List<com.aniplex.app.domain.model.ScheduleItem>>? = null
                    repository.getSchedules(dateStr).collect { res ->
                        if (res is Result.Success || res is Result.Error) {
                            result = res
                        }
                    }
                    if (result is Result.Success) {
                        val label = when (dateStr) {
                            todayStr -> "Today"
                            yesterdayStr -> "Yesterday"
                            else -> {
                                val sdfDisplay = java.text.SimpleDateFormat("MMMM d", java.util.Locale.US)
                                try {
                                    val dateObj = sdfStr.parse(dateStr)
                                    if (dateObj != null) sdfDisplay.format(dateObj) else dateStr
                                } catch (e: Exception) {
                                    dateStr
                                }
                            }
                        }

                        val mapped = (result as Result.Success).data.mapNotNull { schedule ->
                            val timestamp = parseScheduleItemTimestamp(dateStr, schedule.time) ?: System.currentTimeMillis()
                            
                            // Show only already released episodes
                            if (timestamp <= System.currentTimeMillis()) {
                                val anime = Anime(
                                    id = schedule.id,
                                    title = schedule.title,
                                    poster = schedule.poster ?: "",
                                    subEpisodes = schedule.episode,
                                    dubEpisodes = 0
                                )
                                val epsText = "Episode ${schedule.episode} • Subtitled"
                                
                                RecentlyAddedItem(
                                    anime = anime,
                                    dayLabel = formatLocalDay(timestamp),
                                    timeLabel = formatLocalTime(timestamp),
                                    timestamp = timestamp,
                                    subtitleText = epsText
                                )
                            } else {
                                null
                            }
                        }
                        itemsList.addAll(mapped)
                    }
                }
            }
            
            jobs.forEach { it.join() }

            // Sort descending chronologically
            val sortedList = itemsList.distinctBy { it.anime.id }.sortedByDescending { it.timestamp }
            withContext(Dispatchers.Main) {
                _recentlyAddedEpisodes.value = sortedList
            }
        }
    }

    private fun formatLocalTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        sdf.timeZone = java.util.TimeZone.getDefault()
        return sdf.format(java.util.Date(timestamp))
    }

    private fun formatLocalDay(timestamp: Long): String {
        val itemCalendar = java.util.Calendar.getInstance()
        itemCalendar.timeInMillis = timestamp

        val todayCalendar = java.util.Calendar.getInstance()
        val yesterdayCalendar = java.util.Calendar.getInstance()
        yesterdayCalendar.add(java.util.Calendar.DAY_OF_YEAR, -1)

        return when {
            isSameDay(itemCalendar, todayCalendar) -> "Today"
            isSameDay(itemCalendar, yesterdayCalendar) -> "Yesterday"
            else -> {
                val sdfDisplay = java.text.SimpleDateFormat("MMMM d", java.util.Locale.getDefault())
                sdfDisplay.timeZone = java.util.TimeZone.getDefault()
                sdfDisplay.format(java.util.Date(timestamp))
            }
        }
    }

    private fun isSameDay(cal1: java.util.Calendar, cal2: java.util.Calendar): Boolean {
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun parseScheduleItemTimestamp(dateString: String, timeString: String): Long? {
        return try {
            val cleanTime = timeString.trim().uppercase()
            val format1 = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            val format2 = java.text.SimpleDateFormat("yyyy-MM-dd h:mm a", java.util.Locale.US)
            val format3 = java.text.SimpleDateFormat("yyyy-MM-dd h:mma", java.util.Locale.US)
            format1.timeZone = java.util.TimeZone.getTimeZone("UTC")
            format2.timeZone = java.util.TimeZone.getTimeZone("UTC")
            format3.timeZone = java.util.TimeZone.getTimeZone("UTC")

            val combined = "$dateString $cleanTime"
            val date = try {
                format1.parse(combined)
            } catch (e: Exception) {
                try {
                    format2.parse(combined)
                } catch (e2: Exception) {
                    format3.parse(combined)
                }
            }
            date?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun formatTimeTo12Hour(timeString: String): String {
        return try {
            val cleanTime = timeString.trim().uppercase()
            val format24 = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
            val format12 = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
            val format12NoSpace = java.text.SimpleDateFormat("h:mma", java.util.Locale.US)
            
            val parsedDate = try {
                format24.parse(cleanTime)
            } catch (e: Exception) {
                try {
                    format12.parse(cleanTime)
                } catch (e2: Exception) {
                    format12NoSpace.parse(cleanTime)
                }
            }
            if (parsedDate != null) {
                format12.format(parsedDate)
            } else {
                timeString
            }
        } catch (e: Exception) {
            timeString
        }
    }

    fun addToWatchlist(animeId: String, title: String, poster: String) {
        val userId = auth.currentUser?.uid ?: return
        val profileId = profileManager.activeProfile.value?.id
        viewModelScope.launch {
            try {
                val docRef = if (profileId != null) {
                    firestore.collection("users").document(userId)
                        .collection("profiles").document(profileId)
                        .collection("watchlist").document(animeId)
                } else {
                    firestore.collection("users").document(userId)
                        .collection("watchlist").document(animeId)
                }
                val data = hashMapOf(
                    "id" to animeId,
                    "name" to title,
                    "poster" to poster,
                    "addedAt" to System.currentTimeMillis()
                )
                docRef.set(data).await()
            } catch (e: Exception) {
                // Squelch
            }
        }
    }

    fun removeFromWatchlist(animeId: String) {
        val userId = auth.currentUser?.uid ?: return
        val profileId = profileManager.activeProfile.value?.id
        viewModelScope.launch {
            try {
                val docRef = if (profileId != null) {
                    firestore.collection("users").document(userId)
                        .collection("profiles").document(profileId)
                        .collection("watchlist").document(animeId)
                } else {
                    firestore.collection("users").document(userId)
                        .collection("watchlist").document(animeId)
                }
                docRef.delete().await()
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
                preferenceManager.clearLocalHistoryItem(animeId, profileId)
                val docRef = if (profileId != null) {
                    firestore.collection("users").document(userId)
                        .collection("profiles").document(profileId)
                        .collection("history").document(animeId)
                } else {
                    firestore.collection("users").document(userId)
                        .collection("history").document(animeId)
                }
                docRef.delete().await()
            } catch (e: Exception) {
                // Squelch
            }
        }
    }

    fun markAsWatched(animeId: String, title: String, poster: String) {
        val userId = auth.currentUser?.uid ?: return
        val profileId = profileManager.activeProfile.value?.id
        viewModelScope.launch {
            try {
                // 1. Update Watchlist status to "completed"
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

                // 2. Only update History if an active history record already exists (avoid creating fake history records for unstarted shows)
                val historyRef = if (profileId != null) {
                    firestore.collection("users").document(userId)
                        .collection("profiles").document(profileId)
                        .collection("history").document(animeId)
                } else {
                    firestore.collection("users").document(userId)
                        .collection("history").document(animeId)
                }
                val existingDoc = historyRef.get().await()
                if (existingDoc.exists()) {
                    val existingDuration = existingDoc.getLong("totalDuration") ?: 100L
                    val targetDuration = if (existingDuration > 0L) existingDuration else 100L
                    val data = hashMapOf<String, Any>(
                        "progressPosition" to targetDuration,
                        "totalDuration" to targetDuration,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    historyRef.update(data).await()
                }
            } catch (e: Exception) {
                // Squelch
            }
        }
    }

    val watchlistMap: StateFlow<Map<String, String>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(emptyMap())
            close()
            return@callbackFlow
        }

        val profileId = profileManager.activeProfile.value?.id
        val collectionRef = if (profileId != null) {
            firestore.collection("users").document(userId)
                .collection("profiles").document(profileId)
                .collection("watchlist")
        } else {
            firestore.collection("users").document(userId)
                .collection("watchlist")
        }

        val listener = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                trySend(emptyMap())
                return@addSnapshotListener
            }
            val map = snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val status = doc.getString("status") ?: "watching"
                id to status
            }.toMap()
            trySend(map)
        }

        awaitClose { listener.remove() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    fun markAsWatching(animeId: String, title: String, poster: String) {
        val userId = auth.currentUser?.uid ?: return
        val profileId = profileManager.activeProfile.value?.id
        viewModelScope.launch {
            try {
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
                    "status" to "watching",
                    "addedAt" to System.currentTimeMillis()
                )
                watchlistRef.set(watchlistData, com.google.firebase.firestore.SetOptions.merge()).await()
            } catch (e: Exception) {
                // Squelch
            }
        }
    }
}
