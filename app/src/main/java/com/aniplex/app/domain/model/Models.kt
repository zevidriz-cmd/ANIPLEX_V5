package com.aniplex.app.domain.model

data class Anime(
    val id: String,
    val title: String,
    val poster: String,
    val type: String = "TV",
    val duration: String = "",
    val subEpisodes: Int = 0,
    val dubEpisodes: Int = 0,
    val rate: String = "",
    val isBackup: Boolean = false,
    val description: String = "",
    val genres: String = ""
)

data class SpotlightAnime(
    val id: String,
    val name: String,
    val poster: String,
    val description: String = "",
    val rank: Int = 0,
    val otherInfo: List<String> = emptyList()
)

data class AnimeDetail(
    val id: String,
    val name: String,
    val poster: String,
    val description: String = "",
    val rating: String = "",
    val quality: String = "",
    val subEpisodes: Int = 0,
    val dubEpisodes: Int = 0,
    val totalEpisodes: Int = 0,
    val type: String = "TV",
    val duration: String = "",
    val status: String = "",
    val aired: String = "",
    val premiere: String = "",
    val genres: List<String> = emptyList(),
    val producers: List<String> = emptyList(),
    val studio: String = "",
    val recommendations: List<Anime> = emptyList(),
    val relatedAnimes: List<Anime> = emptyList(),
    val malId: String = ""
)

data class Episode(
    val id: String,
    val number: Int,
    val title: String = "",
    val isFiller: Boolean = false
)

data class EpisodeList(
    val totalEpisodes: Int,
    val episodes: List<Episode>
)

data class Character(
    val id: String,
    val name: String,
    val poster: String,
    val role: String // e.g. "Main", "Supporting"
)

data class ScheduleItem(
    val id: String,
    val title: String,
    val time: String,
    val episode: Int,
    val poster: String? = null
)

data class HomeData(
    val spotlightAnimes: List<SpotlightAnime> = emptyList(),
    val trendingAnimes: List<Anime> = emptyList(),
    val topAiringAnimes: List<Anime> = emptyList(),
    val recentlyUpdatedAnimes: List<Anime> = emptyList(),
    val mostPopularAnimes: List<Anime> = emptyList(),
    val topUpcomingAnimes: List<Anime> = emptyList(),
    val genres: List<String> = emptyList()
)

data class Season(
    val malId: String,
    val resolvedId: String? = null,
    val title: String,
    val poster: String = "",
    val episodes: Int = 0,
    val seasonNumber: Int = 0,
    val format: String = "TV",
    val relationType: String = ""
)

data class SeasonalData(
    val animes: List<Anime>,
    val totalPages: Int,
    val hasNextPage: Boolean
)
