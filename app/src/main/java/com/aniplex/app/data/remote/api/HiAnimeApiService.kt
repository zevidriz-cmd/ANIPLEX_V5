package com.aniplex.app.data.remote.api

import com.aniplex.app.data.remote.dto.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface HiAnimeApiService {

    @GET("api/v2/home")
    suspend fun getHomePage(): HomeResponse

    @GET("api/v2/anime/{id}")
    suspend fun getAnimeDetail(@Path("id") id: String): AnimeDetailResponse

    @GET("api/v2/episodes/{id}")
    suspend fun getEpisodes(@Path("id") id: String): EpisodesResponse

    @GET("api/v2/search")
    suspend fun search(
        @Query("keyword") keyword: String,
        @Query("page") page: Int = 1
    ): SearchResponse

    @GET("api/v2/suggestion")
    suspend fun getSuggestions(@Query("keyword") keyword: String): SuggestionsResponse

    @GET("api/v2/animes/{category}")
    suspend fun getAnimeByCategory(
        @Path("category") category: String,
        @Query("page") page: Int = 1
    ): SearchResponse

    @GET("api/v2/animes/genre/{genre}")
    suspend fun getAnimeByGenre(
        @Path("genre") genre: String,
        @Query("page") page: Int = 1
    ): SearchResponse

    @GET("api/v2/schedules")
    suspend fun getSchedules(@Query("date") date: String? = null): ScheduleResponse

    @GET("api/v2/characters/{id}")
    suspend fun getCharacters(
        @Path("id") id: String,
        @Query("page") page: Int = 1
    ): CharactersResponse

    @GET("api/v2/episode/sources")
    suspend fun getEpisodeSources(
        @Query("animeEpisodeId") episodeId: String,
        @Query("server") server: String = "hd-1",
        @Query("category") category: String = "sub"
    ): StreamSourcesResponse

    @GET("api/v2/filter")
    suspend fun filterAnime(
        @Query("type") type: String? = null,
        @Query("status") status: String? = null,
        @Query("genres") genres: String? = null,
        @Query("sort") sort: String? = null,
        @Query("language") language: String? = null,
        @Query("page") page: Int = 1
    ): SearchResponse

    @GET("api/v2/seasons/{malId}")
    suspend fun getSeasons(@Path("malId") malId: String): SeasonsResponse

    @GET("api/v2/resolve-mal/{malId}")
    suspend fun resolveMAL(@Path("malId") malId: String): ResolveMALResponse
}
