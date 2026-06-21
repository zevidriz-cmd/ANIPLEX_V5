package com.aniplex.app.domain.model

data class HistoryItem(
    val animeId: String,
    val animeTitle: String,
    val poster: String,
    val episodeId: String,
    val episodeNumber: Int,
    val episodeTitle: String = "",
    val progressPosition: Long = 0,
    val totalDuration: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
