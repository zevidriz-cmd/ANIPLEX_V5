package com.aniplex.app.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniplex.app.data.local.preferences.ProfileManager
import com.aniplex.app.domain.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProfileSelectionState {
    data object Loading : ProfileSelectionState
    data class Success(val profiles: List<UserProfile>) : ProfileSelectionState
    data class Error(val message: String) : ProfileSelectionState
}

@HiltViewModel
class ProfileSelectionViewModel @Inject constructor(
    private val profileManager: ProfileManager,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileSelectionState>(ProfileSelectionState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _isMigrating = MutableStateFlow(false)
    val isMigrating = _isMigrating.asStateFlow()

    init {
        loadProfilesAndMigrate()
    }

    fun loadProfilesAndMigrate() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _uiState.value = ProfileSelectionState.Error("User not logged in")
            return
        }

        _uiState.value = ProfileSelectionState.Loading
        viewModelScope.launch {
            _isMigrating.value = true
            // Run Firestore background migration silently
            profileManager.checkAndMigrateUser(userId)
            _isMigrating.value = false

            // Load profiles
            refreshProfiles(userId)
        }
    }

    private suspend fun refreshProfiles(userId: String) {
        val list = profileManager.getProfiles(userId)
        _uiState.value = ProfileSelectionState.Success(list)
    }

    fun selectProfile(profile: UserProfile, onSuccess: () -> Unit) {
        profileManager.selectProfile(profile)
        onSuccess()
    }

    fun createProfile(name: String, avatarUrl: String, pin: String?, recoveryQuestion: String?, recoveryAnswer: String?, onComplete: (Result<UserProfile>) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        _uiState.value = ProfileSelectionState.Loading
        viewModelScope.launch {
            val result = profileManager.createProfile(userId, name, avatarUrl, pin, recoveryQuestion, recoveryAnswer)
            result.onSuccess {
                refreshProfiles(userId)
            }
            onComplete(result)
        }
    }

    fun updateProfile(id: String, name: String, avatarUrl: String, pin: String?, recoveryQuestion: String?, recoveryAnswer: String?, onComplete: (Result<Unit>) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        _uiState.value = ProfileSelectionState.Loading
        viewModelScope.launch {
            val result = profileManager.updateProfile(userId, id, name, avatarUrl, pin, recoveryQuestion, recoveryAnswer)
            result.onSuccess {
                refreshProfiles(userId)
            }
            onComplete(result)
        }
    }

    fun verifyAccountPassword(password: String, onResult: (Boolean) -> Unit) {
        val email = auth.currentUser?.email
        if (email.isNullOrBlank()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                    onResult(task.isSuccessful)
                }
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun deleteProfile(id: String, onComplete: (Result<Unit>) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        _uiState.value = ProfileSelectionState.Loading
        viewModelScope.launch {
            val result = profileManager.deleteProfile(userId, id)
            result.onSuccess {
                refreshProfiles(userId)
            }
            onComplete(result)
        }
    }

    fun verifyPin(profile: UserProfile, pin: String): Boolean {
        val hashed = profileManager.hashPin(pin)
        return hashed == profile.pin
    }

    fun signOut(onSuccess: () -> Unit) {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                profileManager.clearActiveProfile()
                auth.signOut()
            }
            onSuccess()
        }
    }
}
