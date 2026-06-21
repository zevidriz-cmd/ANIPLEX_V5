package com.aniplex.app.domain.repository

import com.aniplex.app.domain.model.*
import kotlinx.coroutines.flow.Flow

interface AnimeRepository {
    fun getHomePage(forceRefresh: Boolean): Flow<Result<HomeData>>
    fun getAnimeDetail(id: String, forceRefresh: Boolean): Flow<Result<AnimeDetail>>
    fun getEpisodes(id: String, forceRefresh: Boolean): Flow<Result<List<Episode>>>
    fun search(query: String, page: Int): Flow<Result<List<Anime>>>
    fun searchHiAnime(query: String): Flow<Result<List<Anime>>>
    fun getSuggestions(query: String): Flow<Result<List<Anime>>>
    fun getAnimeByCategory(category: String, page: Int): Flow<Result<List<Anime>>>
    fun getAnimeByGenre(genre: String, page: Int): Flow<Result<List<Anime>>>
    fun getSchedules(date: String?): Flow<Result<List<ScheduleItem>>>
    fun getCharacters(id: String): Flow<Result<List<Character>>>
    fun getEpisodeStream(episodeId: String, server: String, category: String): Flow<Result<EpisodeStream>>
    fun filterAnime(
        type: String?,
        status: String?,
        genres: String?,
        sort: String?,
        language: String?,
        page: Int
    ): Flow<Result<List<Anime>>>
    fun getSeasons(malId: String, forceRefresh: Boolean = false): Flow<Result<List<Season>>>
    fun resolveMAL(malId: String): Flow<Result<String>>
    suspend fun getCachedAnimeDetail(id: String): AnimeDetail?
    fun getSkipTimes(animeId: Int, episodeNumber: Int, episodeLength: Double? = null): Flow<Result<SkipTimes>>
}
