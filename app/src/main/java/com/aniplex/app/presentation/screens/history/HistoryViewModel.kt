package com.aniplex.app.presentation.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniplex.app.data.local.preferences.PreferenceManager
import com.aniplex.app.data.local.preferences.ProfileManager
import com.aniplex.app.domain.model.HistoryItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Success(val list: List<HistoryItem>) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
    data object Empty : HistoryUiState
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val preferenceManager: PreferenceManager,
    private val profileManager: ProfileManager
) : ViewModel() {

    val defaultAudioCategory: String
        get() = preferenceManager.defaultAudioCategory


    val historyState: StateFlow<HistoryUiState> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(HistoryUiState.Error("User not authenticated"))
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
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(HistoryUiState.Error(error.localizedMessage ?: "Unknown error"))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        try {
                            HistoryItem(
                                animeId = doc.getString("animeId") ?: doc.id,
                                animeTitle = doc.getString("animeTitle") ?: "",
                                poster = doc.getString("poster") ?: "",
                                episodeId = doc.getString("episodeId") ?: "",
                                episodeNumber = doc.getLong("episodeNumber")?.toInt() ?: 1,
                                episodeTitle = doc.getString("episodeTitle") ?: "",
                                progressPosition = doc.getLong("progressPosition") ?: 0L,
                                totalDuration = doc.getLong("totalDuration") ?: 0L,
                                updatedAt = doc.getLong("updatedAt") ?: 0L
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (items.isEmpty()) {
                        trySend(HistoryUiState.Empty)
                    } else {
                        trySend(HistoryUiState.Success(items))
                    }
                } else {
                    trySend(HistoryUiState.Empty)
                }
            }

        awaitClose { listener.remove() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState.Loading
    )

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
                // Ignore silent delete failure
            }
        }
    }
}
