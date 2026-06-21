package com.aniplex.app.data.remote.api

import com.aniplex.app.data.remote.dto.AniSkipResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AniSkipApiService {

    @GET("v2/skip-times/{animeId}/{episodeNumber}")
    suspend fun getSkipTimes(
        @Path("animeId") animeId: Int,
        @Path("episodeNumber") episodeNumber: Int,
        @Query("types[]") types: List<String> = listOf("op", "ed", "mixed-op", "recap"),
        @Query("episodeLength") episodeLength: Int = 0
    ): AniSkipResponse?
}
