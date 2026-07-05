package com.aniplex.app.presentation.screens.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniplex.app.data.local.preferences.ProfileManager
import com.aniplex.app.domain.model.AnimeDetail
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

enum class SortOrder { RECENT_ACTIVITY, DATE_ADDED, ALPHABETICAL }

enum class WatchlistStatusFilter { ALL, WATCHING, PLANNING, COMPLETED }

data class WatchlistItem(
    val id: String,
    val title: String,
    val poster: String,
    val addedAt: Long,
    val isFavorite: Boolean = false,
    val status: String = "planning"
)

sealed interface WatchlistUiState {
    data object Loading : WatchlistUiState
    data class Success(val list: List<WatchlistItem>) : WatchlistUiState
    data class Error(val message: String) : WatchlistUiState
    data object Empty : WatchlistUiState
}

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val profileManager: ProfileManager
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(SortOrder.RECENT_ACTIVITY)
    val sortOrder = _sortOrder.asStateFlow()

    private val _statusFilter = MutableStateFlow(WatchlistStatusFilter.ALL)
    val statusFilter = _statusFilter.asStateFlow()

    private val _rawWatchlist = callbackFlow<List<WatchlistItem>> {
        val userId = auth.currentUser?.uid
        if (userId == null) {
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
            if (error != null) {
                close()
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val items = snapshot.documents.mapNotNull { doc ->
                    try {
                        val id = doc.getString("id") ?: doc.id
                        val title = doc.getString("name") ?: ""
                        val poster = doc.getString("poster") ?: ""
                        val addedAt = doc.getLong("addedAt") ?: 0L
                        val isFavorite = doc.getBoolean("isFavorite") ?: false
                        val status = doc.getString("status") ?: "planning"
                        WatchlistItem(id, title, poster, addedAt, isFavorite, status)
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

    val watchlistState: StateFlow<WatchlistUiState> = combine(_rawWatchlist, _sortOrder, _statusFilter) { items, order, filter ->
        val filtered = when (filter) {
            WatchlistStatusFilter.ALL -> items
            WatchlistStatusFilter.WATCHING -> items.filter { it.status == "watching" }
            WatchlistStatusFilter.PLANNING -> items.filter { it.status == "planning" }
            WatchlistStatusFilter.COMPLETED -> items.filter { it.status == "completed" }
        }

        if (filtered.isEmpty()) {
            WatchlistUiState.Empty
        } else {
            val sortedList = when (order) {
                SortOrder.RECENT_ACTIVITY -> filtered.sortedByDescending { it.addedAt }
                SortOrder.DATE_ADDED -> filtered.sortedBy { it.addedAt }
                SortOrder.ALPHABETICAL -> filtered.sortedBy { it.title.lowercase() }
            }
            WatchlistUiState.Success(sortedList)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WatchlistUiState.Loading
    )

    fun setStatusFilter(filter: WatchlistStatusFilter) {
        _statusFilter.value = filter
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun toggleFavorite(animeId: String, currentVal: Boolean) {
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
                docRef.set(mapOf("isFavorite" to !currentVal), SetOptions.merge()).await()
            } catch (e: Exception) {
                // Ignore silent failure
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
                // Ignore silent delete failure
            }
        }
    }

    fun toggleWatchlist(animeDetail: AnimeDetail) {
        val userId = auth.currentUser?.uid ?: return
        val profileId = profileManager.activeProfile.value?.id
        val list = _rawWatchlist.value
        val isAlreadyAdded = list.any { it.id == animeDetail.id }
        viewModelScope.launch {
            try {
                val docRef = if (profileId != null) {
                    firestore.collection("users").document(userId)
                        .collection("profiles").document(profileId)
                        .collection("watchlist").document(animeDetail.id)
                } else {
                    firestore.collection("users").document(userId)
                        .collection("watchlist").document(animeDetail.id)
                }
                if (isAlreadyAdded) {
                    docRef.delete().await()
                } else {
                    val data = hashMapOf(
                        "id" to animeDetail.id,
                        "name" to animeDetail.name,
                        "poster" to animeDetail.poster,
                        "status" to "planning",
                        "addedAt" to System.currentTimeMillis()
                    )
                    docRef.set(data).await()
                }
            } catch (e: Exception) {
                // Ignore failure
            }
        }
    }
}
