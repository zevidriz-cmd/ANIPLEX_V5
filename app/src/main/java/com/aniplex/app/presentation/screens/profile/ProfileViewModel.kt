package com.aniplex.app.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniplex.app.data.local.dao.CacheDao
import com.aniplex.app.data.local.preferences.PreferenceManager
import com.aniplex.app.domain.model.UserSession
import com.aniplex.app.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
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

    val currentUser = authRepository.authStateFlow.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = authRepository.currentUser
    )

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

    private val _preferredProvider = MutableStateFlow(preferenceManager.preferredProvider)
    val preferredProvider = _preferredProvider.asStateFlow()

    // Subtitle Customizer states
    private val _subtitleSizeScale = MutableStateFlow(preferenceManager.subtitleSizeScale)
    val subtitleSizeScale = _subtitleSizeScale.asStateFlow()

    private val _subtitleColor = MutableStateFlow(preferenceManager.subtitleColor)
    val subtitleColor = _subtitleColor.asStateFlow()

    private val _subtitleBgOpacity = MutableStateFlow(preferenceManager.subtitleBgOpacity)
    val subtitleBgOpacity = _subtitleBgOpacity.asStateFlow()

    private val _subtitleStyle = MutableStateFlow(preferenceManager.subtitleStyle)
    val subtitleStyle = _subtitleStyle.asStateFlow()

    private val _subtitlePosition = MutableStateFlow(preferenceManager.subtitlePosition)
    val subtitlePosition = _subtitlePosition.asStateFlow()

    private val _hevcDecoderEnabled = MutableStateFlow(preferenceManager.hevcDecoderEnabled)
    val hevcDecoderEnabled = _hevcDecoderEnabled.asStateFlow()

    private val _dolbyAtmosEnabled = MutableStateFlow(preferenceManager.dolbyAtmosEnabled)
    val dolbyAtmosEnabled = _dolbyAtmosEnabled.asStateFlow()

    private val _preferredAccentColor = MutableStateFlow(preferenceManager.preferredAccentColor)
    val preferredAccentColor = _preferredAccentColor.asStateFlow()

    private var watchlistListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var historyListener: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        // Collect active profile updates reactively to setup listener connections
        viewModelScope.launch {
            activeProfile.collect {
                observeProfileData()
            }
        }

        // Collect preference updates reactively
        viewModelScope.launch {
            preferenceManager.defaultAudioCategoryFlow.collect { _defaultAudioCategory.value = it }
        }
        viewModelScope.launch {
            preferenceManager.autoplayNextEpisodeFlow.collect { _autoplayNextEpisode.value = it }
        }
        viewModelScope.launch {
            preferenceManager.preferredQualityFlow.collect { _preferredQuality.value = it }
        }
        viewModelScope.launch {
            preferenceManager.skipIntroFlow.collect { _skipIntro.value = it }
        }
        viewModelScope.launch {
            preferenceManager.skipOutroFlow.collect { _skipOutro.value = it }
        }
        viewModelScope.launch {
            preferenceManager.downloadOverCellularFlow.collect { _downloadOverCellular.value = it }
        }
        viewModelScope.launch {
            preferenceManager.preferredProviderFlow.collect { _preferredProvider.value = it }
        }
        viewModelScope.launch {
            preferenceManager.subtitleSizeScaleFlow.collect { _subtitleSizeScale.value = it }
        }
        viewModelScope.launch {
            preferenceManager.subtitleColorFlow.collect { _subtitleColor.value = it }
        }
        viewModelScope.launch {
            preferenceManager.subtitleBgOpacityFlow.collect { _subtitleBgOpacity.value = it }
        }
        viewModelScope.launch {
            preferenceManager.subtitleStyleFlow.collect { _subtitleStyle.value = it }
        }
        viewModelScope.launch {
            preferenceManager.subtitlePositionFlow.collect { _subtitlePosition.value = it }
        }
        viewModelScope.launch {
            preferenceManager.hevcDecoderEnabledFlow.collect { _hevcDecoderEnabled.value = it }
        }
        viewModelScope.launch {
            preferenceManager.dolbyAtmosEnabledFlow.collect { _dolbyAtmosEnabled.value = it }
        }
        viewModelScope.launch {
            preferenceManager.accentColorFlow.collect { _preferredAccentColor.value = it }
        }
    }

    private fun observeProfileData() {
        watchlistListener?.remove()
        historyListener?.remove()

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

            watchlistListener = watchlistRef.addSnapshotListener { snapshot, _ ->
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

            historyListener = historyRef.addSnapshotListener { snapshot, _ ->
                _historyCount.value = snapshot?.size() ?: 0
                val eps = snapshot?.documents?.sumOf { doc ->
                    doc.getLong("episodeNumber")?.toInt() ?: 0
                } ?: 0
                _episodesCount.value = eps
            }
        } else {
            _watchlistCount.value = 0
            _historyCount.value = 0
            _episodesCount.value = 0
        }
    }

    override fun onCleared() {
        super.onCleared()
        watchlistListener?.remove()
        historyListener?.remove()
    }

    fun setDefaultAudioCategory(value: String) {
        preferenceManager.defaultAudioCategory = value
        _defaultAudioCategory.value = value
        profileManager.saveSettingsToFirestore()
    }

    fun setAutoplayNextEpisode(value: Boolean) {
        preferenceManager.autoplayNextEpisode = value
        _autoplayNextEpisode.value = value
        profileManager.saveSettingsToFirestore()
    }

    fun setPreferredQuality(value: String) {
        preferenceManager.preferredQuality = value
        _preferredQuality.value = value
        profileManager.saveSettingsToFirestore()
    }

    fun setSkipIntro(value: Boolean) {
        preferenceManager.skipIntro = value
        _skipIntro.value = value
        profileManager.saveSettingsToFirestore()
    }

    fun setSkipOutro(value: Boolean) {
        preferenceManager.skipOutro = value
        _skipOutro.value = value
        profileManager.saveSettingsToFirestore()
    }

    fun setDownloadOverCellular(value: Boolean) {
        preferenceManager.downloadOverCellular = value
        _downloadOverCellular.value = value
        profileManager.saveSettingsToFirestore()
    }

    fun setPreferredProvider(value: String) {
        preferenceManager.preferredProvider = value
        _preferredProvider.value = value
        profileManager.saveSettingsToFirestore()
    }

    fun setSubtitleSizeScale(value: Float) {
        preferenceManager.subtitleSizeScale = value
        _subtitleSizeScale.value = value
        profileManager.saveSettingsToFirestore()
    }

    fun setSubtitleColor(value: String) {
        preferenceManager.subtitleColor = value
        _subtitleColor.value = value
        profileManager.saveSettingsToFirestore()
    }

    fun setSubtitleBgOpacity(value: Float) {
        preferenceManager.subtitleBgOpacity = value
        _subtitleBgOpacity.value = value
        profileManager.saveSettingsToFirestore()
    }

    fun setSubtitleStyle(value: String) {
        preferenceManager.subtitleStyle = value
        _subtitleStyle.value = value
        profileManager.saveSettingsToFirestore()
    }

    fun setSubtitlePosition(value: Float) {
        preferenceManager.subtitlePosition = value
        _subtitlePosition.value = value
        profileManager.saveSettingsToFirestore()
    }

    fun setHevcDecoderEnabled(value: Boolean) {
        preferenceManager.hevcDecoderEnabled = value
        _hevcDecoderEnabled.value = value
        profileManager.saveSettingsToFirestore()
    }

    fun setDolbyAtmosEnabled(value: Boolean) {
        preferenceManager.dolbyAtmosEnabled = value
        _dolbyAtmosEnabled.value = value
        profileManager.saveSettingsToFirestore()
    }

    fun setPreferredAccentColor(value: String) {
        preferenceManager.preferredAccentColor = value
        _preferredAccentColor.value = value
        profileManager.saveSettingsToFirestore()
    }

    val isGoogleUser: Boolean
        get() = auth.currentUser?.providerData?.any { it.providerId == "google.com" } == true

    fun changeEmail(password: String, newEmail: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser
        val email = user?.email
        if (user == null || email == null) {
            onError("User session invalid or expired")
            return
        }
        
        viewModelScope.launch {
            try {
                val credential = EmailAuthProvider.getCredential(email, password)
                user.reauthenticate(credential).await()
                user.updateEmail(newEmail).await()
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to change email")
            }
        }
    }

    fun changePassword(password: String, newPassword: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser
        val email = user?.email
        if (user == null || email == null) {
            onError("User session invalid or expired")
            return
        }
        
        viewModelScope.launch {
            try {
                val credential = EmailAuthProvider.getCredential(email, password)
                user.reauthenticate(credential).await()
                user.updatePassword(newPassword).await()
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to change password")
            }
        }
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
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                profileManager.clearActiveProfile()
                authRepository.signOut()
            }
            onSuccess()
        }
    }
}
