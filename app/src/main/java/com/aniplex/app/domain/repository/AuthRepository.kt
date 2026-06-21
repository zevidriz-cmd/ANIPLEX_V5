package com.aniplex.app.domain.repository

import com.aniplex.app.domain.model.UserSession
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: UserSession?
    val authStateFlow: Flow<UserSession?>
    
    suspend fun signIn(email: String, password: String): Result<UserSession>
    suspend fun signUp(email: String, password: String, username: String): Result<UserSession>
    suspend fun signInWithGoogle(idToken: String): Result<UserSession>
    suspend fun signOut()
    suspend fun syncUserProfile(user: UserSession): Result<Unit>
}
