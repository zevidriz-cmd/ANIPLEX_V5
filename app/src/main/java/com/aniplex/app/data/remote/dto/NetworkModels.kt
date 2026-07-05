package com.aniplex.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// Generic base properties for anime list items
data class AnimeDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("poster") val poster: String,
    @SerializedName("type") val type: String?,
    @SerializedName("duration") val duration: String?,
    @SerializedName("episodes") val episodes: EpisodeCountDto?,
    @SerializedName("rate") val rate: String?
)

data class EpisodeCountDto(
    @SerializedName("sub") val sub: Int?,
    @SerializedName("dub") val dub: Int?
)

// Spotlight model on Home screen
data class SpotlightAnimeDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("poster") val poster: String,
    @SerializedName("description") val description: String?,
    @SerializedName("rank") val rank: Int?,
    @SerializedName("otherInfo") val otherInfo: List<String>?
)

// Home Page DTO
data class HomeResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: HomeDataDto
)

data class HomeDataDto(
    @SerializedName("spotlightAnimes") val spotlightAnimes: List<SpotlightAnimeDto>?,
    @SerializedName("trendingAnimes") val trendingAnimes: List<AnimeDto>?,
    @SerializedName("topAiringAnimes") val topAiringAnimes: List<AnimeDto>?,
    @SerializedName("recentlyAddedAnimes") val recentlyAddedAnimes: List<AnimeDto>?,
    @SerializedName("mostPopularAnimes") val mostPopularAnimes: List<AnimeDto>?,
    @SerializedName("topUpcomingAnimes") val topUpcomingAnimes: List<AnimeDto>?,
    @SerializedName("genres") val genres: List<String>?
)

// Detail Page DTO
data class AnimeDetailResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: AnimeDetailDataDto
)

data class AnimeDetailDataDto(
    @SerializedName("anime") val anime: AnimeDetailInfoDto,
    @SerializedName("recommendedAnimes") val recommendedAnimes: List<AnimeDto>?,
    @SerializedName("relatedAnimes") val relatedAnimes: List<AnimeDto>?
)

data class AnimeDetailInfoDto(
    @SerializedName("info") val info: InfoDto,
    @SerializedName("moreInfo") val moreInfo: MoreInfoDto
)

data class InfoDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("poster") val poster: String,
    @SerializedName("description") val description: String?,
    @SerializedName("malId") val malId: String?,
    @SerializedName("stats") val stats: StatsDto?
)

data class StatsDto(
    @SerializedName("rating") val rating: String?,
    @SerializedName("quality") val quality: String?,
    @SerializedName("episodes") val episodes: EpisodeCountDto?,
    @SerializedName("type") val type: String?,
    @SerializedName("duration") val duration: String?
)

data class MoreInfoDto(
    @SerializedName("status") val status: String?,
    @SerializedName("aired") val aired: String?,
    @SerializedName("premiered") val premiered: String?,
    @SerializedName("genres") val genres: List<String>?,
    @SerializedName("producers") val producers: List<String>?,
    @SerializedName("studio") val studio: String?
)

// Episodes DTO
data class EpisodesResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: EpisodesDataDto
)

data class EpisodesDataDto(
    @SerializedName("totalEpisodes") val totalEpisodes: Int,
    @SerializedName("episodes") val episodes: List<EpisodeDto>
)

data class EpisodeDto(
    @SerializedName("episodeId") val episodeId: String,
    @SerializedName("number") val number: Int,
    @SerializedName("title") val title: String?,
    @SerializedName("isFiller") val isFiller: Boolean?
)

// Search DTO
data class SearchResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: SearchDataDto
)

data class SearchDataDto(
    @SerializedName("animes") val animes: List<AnimeDto>?,
    @SerializedName("currentPage") val currentPage: Int,
    @SerializedName("hasNextPage") val hasNextPage: Boolean,
    @SerializedName("totalPages") val totalPages: Int
)

// Suggestions DTO
data class SuggestionsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: SuggestionsDataDto
)

data class SuggestionsDataDto(
    @SerializedName("suggestions") val suggestions: List<SuggestionItemDto>?
)

data class SuggestionItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("poster") val poster: String,
    @SerializedName("moreInfo") val moreInfo: List<String>?
)

// Schedule DTO
data class ScheduleResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: ScheduleDataDto
)

data class ScheduleDataDto(
    @SerializedName("scheduledAnimes") val scheduledAnimes: List<ScheduleItemDto>?
)

data class ScheduleItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("time") val time: String,
    @SerializedName("episode") val episode: Int,
    @SerializedName("poster") val poster: String? = null
)

// Characters DTO
data class CharactersResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: CharactersDataDto
)

data class CharactersDataDto(
    @SerializedName("characters") val characters: List<CharacterDto>?
)

data class CharacterDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("poster") val poster: String,
    @SerializedName("role") val role: String?
)

// Streaming Sources DTO
data class StreamSourcesResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: StreamSourcesDataDto
)

data class StreamSourcesDataDto(
    @SerializedName("sources") val sources: List<StreamSourceDto>?,
    @SerializedName("tracks") val tracks: List<StreamTrackDto>?,
    @SerializedName("intro") val intro: TimeRangeDto?,
    @SerializedName("outro") val outro: TimeRangeDto?
)

data class StreamSourceDto(
    @SerializedName("url") val url: String,
    @SerializedName("type") val type: String
)

data class StreamTrackDto(
    @SerializedName("file") val file: String,
    @SerializedName("label") val label: String?,
    @SerializedName("kind") val kind: String?
)

data class TimeRangeDto(
    @SerializedName("start") val start: Int,
    @SerializedName("end") val end: Int
)

// Seasons DTO
data class SeasonsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: SeasonsDataDto
)

data class SeasonsDataDto(
    @SerializedName("seasons") val seasons: List<SeasonDto>?,
    @SerializedName("currentMalId") val currentMalId: String?
)

data class SeasonDto(
    @SerializedName("malId") val malId: String,
    @SerializedName("title") val title: String,
    @SerializedName("poster") val poster: String?,
    @SerializedName("episodes") val episodes: Int?,
    @SerializedName("seasonNumber") val seasonNumber: Int
)

// Resolve MAL -> Anikoto ID
data class ResolveMALResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: ResolveMALDataDto?
)

data class ResolveMALDataDto(
    @SerializedName("anikotoId") val anikotoId: String,
    @SerializedName("title") val title: String?
)

// Fallback Stream DTOs (Netlify serverless function response)
data class FallbackStreamResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("sources") val sources: List<FallbackSourceDto>?,
    @SerializedName("subtitles") val subtitles: List<FallbackSubtitleDto>?,
    @SerializedName("intro") val intro: FallbackTimeRangeDto?,
    @SerializedName("outro") val outro: FallbackTimeRangeDto?,
    @SerializedName("provider") val provider: String?,
    @SerializedName("error") val error: String?
)

data class FallbackSourceDto(
    @SerializedName("url") val url: String,
    @SerializedName("type") val type: String?,
    @SerializedName("quality") val quality: String?
)

data class FallbackSubtitleDto(
    @SerializedName("url") val url: String?,
    @SerializedName("lang") val lang: String?
)

data class FallbackTimeRangeDto(
    @SerializedName("start") val start: Double?,
    @SerializedName("end") val end: Double?
)

// MegaPlay getSources / getSourcesNew response DTOs
data class MegaplaySourcesResponse(
    @SerializedName("sources") val sources: MegaplaySourceFileDto?,
    @SerializedName("tracks") val tracks: List<MegaplayTrackDto>?,
    @SerializedName("intro") val intro: FallbackTimeRangeDto?,
    @SerializedName("outro") val outro: FallbackTimeRangeDto?
)

data class MegaplaySourceFileDto(
    @SerializedName("file") val file: String?
)

data class MegaplayTrackDto(
    @SerializedName("file") val file: String?,
    @SerializedName("label") val label: String?,
    @SerializedName("kind") val kind: String?,
    @SerializedName("default") val default_: Boolean?
)
