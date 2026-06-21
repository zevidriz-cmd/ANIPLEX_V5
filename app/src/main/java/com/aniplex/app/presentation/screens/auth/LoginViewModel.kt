package com.aniplex.app.presentation.screens.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniplex.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun signIn(email: String, password: String) {
        if (!validateInputs(email, password)) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.signIn(email.trim(), password)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                .onFailure { exception ->
                    _uiState.update { it.copy(isLoading = false, error = exception.localizedMessage ?: "Sign in failed") }
                }
        }
    }

    fun signUp(email: String, password: String, username: String) {
        if (username.isBlank()) {
            _uiState.update { it.copy(error = "Username cannot be empty") }
            return
        }
        if (!validateInputs(email, password)) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.signUp(email.trim(), password, username.trim())
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                .onFailure { exception ->
                    _uiState.update { it.copy(isLoading = false, error = exception.localizedMessage ?: "Sign up failed") }
                }
        }
    }

    fun signInWithGoogle(idToken: String) {
        if (idToken.isBlank()) {
            _uiState.update { 
                it.copy(
                    isLoading = false, 
                    error = "Google Sign-In failed: The ID token is missing. Please ensure your SHA-1 signature and package name are registered in Firebase."
                ) 
            }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.signInWithGoogle(idToken)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                .onFailure { exception ->
                    _uiState.update { it.copy(isLoading = false, error = exception.localizedMessage ?: "Google sign-in failed") }
                }
        }
    }

    fun setErrorMessage(message: String) {
        _uiState.update { it.copy(error = message, isLoading = false) }
    }

    fun setLoading(isLoading: Boolean) {
        _uiState.update { it.copy(isLoading = isLoading) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            _uiState.update { it.copy(error = "Please enter a valid email address") }
            return false
        }
        if (password.length < 6) {
            _uiState.update { it.copy(error = "Password must be at least 6 characters long") }
            return false
        }
        return true
    }
}
