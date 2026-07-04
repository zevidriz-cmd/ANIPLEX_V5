package com.aniplex.app.domain.model

data class StoryArc(
    val index: Int,
    val start: Int,
    val end: Int,
    val label: String,
    val episodes: List<Episode>,
    val episodesCount: Int
)
