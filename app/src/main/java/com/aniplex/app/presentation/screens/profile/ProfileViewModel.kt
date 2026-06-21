package com.aniplex.app.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniplex.app.data.local.dao.CacheDao
import com.aniplex.app.data.local.preferences.PreferenceManager
import com.aniplex.app.domain.model.UserSession
import com.aniplex.app.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.aniplex.app.data.local.preferences.ProfileManager
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val cacheDao: CacheDao,
    private val preferenceManager: PreferenceManager,
    private val profileManager: ProfileManager
) : ViewModel() {

    val activeProfile = profileManager.activeProfile

    val currentUser: UserSession?
        get() = authRepository.currentUser

    // Profile stats states
    private val _watchlistCount = MutableStateFlow(0)
    val watchlistCount = _watchlistCount.asStateFlow()

    private val _historyCount = MutableStateFlow(0)
    val historyCount = _historyCount.asStateFlow()

    private val _episodesCount = MutableStateFlow(0)
    val episodesCount = _episodesCount.asStateFlow()

    // Preferences states
    private val _defaultAudioCategory = MutableStateFlow(preferenceManager.defaultAudioCategory)
    val defaultAudioCategory = _defaultAudioCategory.asStateFlow()

    private val _autoplayNextEpisode = MutableStateFlow(preferenceManager.autoplayNextEpisode)
    val autoplayNextEpisode = _autoplayNextEpisode.asStateFlow()

    private val _preferredQuality = MutableStateFlow(preferenceManager.preferredQuality)
    val preferredQuality = _preferredQuality.asStateFlow()

    private val _skipIntro = MutableStateFlow(preferenceManager.skipIntro)
    val skipIntro = _skipIntro.asStateFlow()

    private val _skipOutro = MutableStateFlow(preferenceManager.skipOutro)
    val skipOutro = _skipOutro.asStateFlow()

    private val _downloadOverCellular = MutableStateFlow(preferenceManager.downloadOverCellular)
    val downloadOverCellular = _downloadOverCellular.asStateFlow()

    init {
        val userId = auth.currentUser?.uid
        val profileId = profileManager.activeProfile.value?.id
        if (userId != null) {
            val watchlistRef = if (profileId != null) {
                firestore.collection("users").document(userId)
                    .collection("profiles").document(profileId)
                    .collection("watchlist")
            } else {
                firestore.collection("users").document(userId)
                    .collection("watchlist")
            }

            watchlistRef.addSnapshotListener { snapshot, _ ->
                _watchlistCount.value = snapshot?.size() ?: 0
            }

            val historyRef = if (profileId != null) {
                firestore.collection("users").document(userId)
                    .collection("profiles").document(profileId)
                    .collection("history")
            } else {
                firestore.collection("users").document(userId)
                    .collection("history")
            }

            historyRef.addSnapshotListener { snapshot, _ ->
                _historyCount.value = snapshot?.size() ?: 0
                val eps = snapshot?.documents?.sumOf { doc ->
                    doc.getLong("episodeNumber")?.toInt() ?: 0
                } ?: 0
                _episodesCount.value = eps
            }
        }
    }

    fun setDefaultAudioCategory(value: String) {
        preferenceManager.defaultAudioCategory = value
        _defaultAudioCategory.value = value
    }

    fun setAutoplayNextEpisode(value: Boolean) {
        preferenceManager.autoplayNextEpisode = value
        _autoplayNextEpisode.value = value
    }

    fun setPreferredQuality(value: String) {
        preferenceManager.preferredQuality = value
        _preferredQuality.value = value
    }

    fun setSkipIntro(value: Boolean) {
        preferenceManager.skipIntro = value
        _skipIntro.value = value
    }

    fun setSkipOutro(value: Boolean) {
        preferenceManager.skipOutro = value
        _skipOutro.value = value
    }

    fun setDownloadOverCellular(value: Boolean) {
        preferenceManager.downloadOverCellular = value
        _downloadOverCellular.value = value
    }

    fun clearCache(onCompleted: () -> Unit) {
        viewModelScope.launch {
            try {
                cacheDao.clearAllCache()
            } catch (e: Exception) {
                // Ignore silent failure
            }
            onCompleted()
        }
    }

    fun signOut(onSuccess: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            profileManager.clearActiveProfile()
            onSuccess()
        }
    }
}
