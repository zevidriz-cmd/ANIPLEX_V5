package com.aniplex.app.domain.model

data class EpisodeStream(
    val videoUrl: String,
    val isHls: Boolean,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val introStart: Long = 0L,
    val introEnd: Long = 0L,
    val outroStart: Long = 0L,
    val outroEnd: Long = 0L
)

data class SubtitleTrack(
    val url: String,
    val label: String,
    val isDefault: Boolean = false
)
