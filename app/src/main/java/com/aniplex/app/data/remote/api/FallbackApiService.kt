package com.aniplex.app.data.remote.api

import com.aniplex.app.data.remote.dto.FallbackStreamResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for querying the Netlify serverless fallback-stream function.
 * This mirrors the website's fetchBackupFromApi() in web/src/services/api.js,
 * which calls https://anistream-web.netlify.app/.netlify/functions/fallback-stream
 */
interface FallbackApiService {

    @GET(".netlify/functions/fallback-stream")
    suspend fun getFallbackStream(
        @Query("malId") malId: String?,
        @Query("episodeNumber") episodeNumber: Int,
        @Query("title") title: String? = null,
        @Query("provider") provider: String = "gogoanime"
    ): FallbackStreamResponse
}
