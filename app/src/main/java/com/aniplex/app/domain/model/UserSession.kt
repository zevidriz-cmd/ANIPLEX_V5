package com.aniplex.app.domain.model

data class UserSession(
    val uid: String,
    val email: String,
    val displayName: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
