package com.aniplex.app.data.repository

import com.aniplex.app.data.local.dao.CacheDao
import com.aniplex.app.data.local.entity.CacheEntity
import com.aniplex.app.data.mapper.toDomain
import com.aniplex.app.data.remote.api.HiAnimeApiService
import com.aniplex.app.data.remote.dto.AnimeDetailResponse
import com.aniplex.app.data.remote.dto.EpisodesResponse
import com.aniplex.app.data.remote.dto.HomeResponse
import com.aniplex.app.data.remote.dto.SeasonsResponse
import com.aniplex.app.data.remote.dto.SeasonsDataDto
import com.aniplex.app.data.local.preferences.PreferenceManager
import com.aniplex.app.domain.model.*
import com.aniplex.app.domain.repository.AnimeRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import javax.inject.Inject

class AnimeRepositoryImpl @Inject constructor(
    private val apiService: HiAnimeApiService,
    private val cacheDao: CacheDao,
    private val gson: Gson,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val preferenceManager: PreferenceManager,
    private val apiSkipApiService: com.aniplex.app.data.remote.api.AniSkipApiService
) : AnimeRepository {

    private val HOME_CACHE_LIFETIME = 10 * 60 * 1000L // 10 minutes
    private val DETAIL_CACHE_LIFETIME = 30 * 60 * 1000L // 30 minutes
    private val EPISODES_CACHE_LIFETIME = 60 * 60 * 1000L // 1 hour

    override fun getHomePage(forceRefresh: Boolean): Flow<Result<HomeData>> = flow {
        val cacheKey = "home_page"
        emit(Result.Loading)

        val cachedEntity = cacheDao.getCache(cacheKey)
        val currentTime = System.currentTimeMillis()

        if (cachedEntity != null && !forceRefresh && (currentTime - cachedEntity.timestamp < HOME_CACHE_LIFETIME)) {
            try {
                val cachedResponse = gson.fromJson(cachedEntity.jsonContent, HomeResponse::class.java)
                emit(Result.Success(cachedResponse.data.toDomain()))
                return@flow
            } catch (e: Exception) {
                // JSON parsing failed, fallback to network
            }
        }

        try {
            val response = apiService.getHomePage()
            if (response.success) {
                cacheDao.insertCache(
                    CacheEntity(
                        cacheKey = cacheKey,
                        jsonContent = gson.toJson(response),
                        timestamp = currentTime
                    )
                )
                emit(Result.Success(response.data.toDomain()))
            } else {
                emit(Result.Error("API returned success = false"))
            }
        } catch (e: Exception) {
            if (cachedEntity != null) {
                try {
                    val cachedResponse = gson.fromJson(cachedEntity.jsonContent, HomeResponse::class.java)
                    emit(Result.Success(cachedResponse.data.toDomain()))
                } catch (jsonEx: Exception) {
                    emit(Result.Error(e.localizedMessage ?: "Unknown network error"))
                }
            } else {
                emit(Result.Error(e.localizedMessage ?: "Unknown network error"))
            }
        }
    }

    override fun getAnimeDetail(id: String, forceRefresh: Boolean): Flow<Result<AnimeDetail>> = flow {
        val cacheKey = "detail_$id"
        emit(Result.Loading)

        val cachedEntity = cacheDao.getCache(cacheKey)
        val currentTime = System.currentTimeMillis()

        if (cachedEntity != null && !forceRefresh && (currentTime - cachedEntity.timestamp < DETAIL_CACHE_LIFETIME)) {
            try {
                val cachedResponse = gson.fromJson(cachedEntity.jsonContent, AnimeDetailResponse::class.java)
                emit(Result.Success(cachedResponse.data.toDomain()))
                return@flow
            } catch (e: Exception) {
                // Fallback to network
            }
        }

        try {
            val response = apiService.getAnimeDetail(id)
            if (response.success) {
                cacheDao.insertCache(
                    CacheEntity(
                        cacheKey = cacheKey,
                        jsonContent = gson.toJson(response),
                        timestamp = currentTime
                    )
                )
                emit(Result.Success(response.data.toDomain()))
            } else {
                emit(Result.Error("API returned success = false"))
            }
        } catch (e: Exception) {
            if (cachedEntity != null) {
                try {
                    val cachedResponse = gson.fromJson(cachedEntity.jsonContent, AnimeDetailResponse::class.java)
                    emit(Result.Success(cachedResponse.data.toDomain()))
                } catch (jsonEx: Exception) {
                    emit(Result.Error(e.localizedMessage ?: "Network error"))
                }
            } else {
                emit(Result.Error(e.localizedMessage ?: "Network error"))
            }
        }
    }

    override fun getEpisodes(id: String, forceRefresh: Boolean): Flow<Result<List<Episode>>> = flow {
        val cacheKey = "episodes_$id"
        emit(Result.Loading)

        val cachedEntity = cacheDao.getCache(cacheKey)
        val currentTime = System.currentTimeMillis()

        if (cachedEntity != null && !forceRefresh && (currentTime - cachedEntity.timestamp < EPISODES_CACHE_LIFETIME)) {
            try {
                val cachedResponse = gson.fromJson(cachedEntity.jsonContent, EpisodesResponse::class.java)
                val domainEpisodes = cachedResponse.data.episodes.map { it.toDomain() }
                val enrichedEpisodes = syncEpisodesWithJikanFiller(id, domainEpisodes)
                emit(Result.Success(enrichedEpisodes))
                return@flow
            } catch (e: Exception) {
                // Fallback to network
            }
        }

        try {
            val response = apiService.getEpisodes(id)
            if (response.success) {
                cacheDao.insertCache(
                    CacheEntity(
                        cacheKey = cacheKey,
                        jsonContent = gson.toJson(response),
                        timestamp = currentTime
                    )
                )
                val domainEpisodes = response.data.episodes.map { it.toDomain() }
                val enrichedEpisodes = syncEpisodesWithJikanFiller(id, domainEpisodes)
                emit(Result.Success(enrichedEpisodes))
            } else {
                emit(Result.Error("API returned success = false"))
            }
        } catch (e: Exception) {
            if (cachedEntity != null) {
                try {
                    val cachedResponse = gson.fromJson(cachedEntity.jsonContent, EpisodesResponse::class.java)
                    val domainEpisodes = cachedResponse.data.episodes.map { it.toDomain() }
                    val enrichedEpisodes = syncEpisodesWithJikanFiller(id, domainEpisodes)
                    emit(Result.Success(enrichedEpisodes))
                } catch (jsonEx: Exception) {
                    emit(Result.Error(e.localizedMessage ?: "Network error"))
                }
            } else {
                emit(Result.Error(e.localizedMessage ?: "Network error"))
            }
        }
    }

    private suspend fun syncEpisodesWithJikanFiller(animeId: String, episodes: List<Episode>): List<Episode> {
        if (episodes.isEmpty()) return episodes
        
        var malId = ""
        if (animeId.startsWith("mal-")) {
            malId = animeId.substringAfter("mal-")
        } else {
            // First try looking up cached details
            try {
                val cachedDetail = getCachedAnimeDetail(animeId)
                if (cachedDetail != null && cachedDetail.malId.isNotBlank()) {
                    malId = cachedDetail.malId
                }
            } catch (e: Exception) {
                // ignore
            }
            
            // If still blank, try fetching detail via api
            if (malId.isBlank()) {
                try {
                    val detailResponse = apiService.getAnimeDetail(animeId)
                    if (detailResponse.success) {
                        malId = detailResponse.data.anime.info.malId ?: ""
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
        
        if (malId.isBlank()) {
            return episodes
        }
        
        // Fetch Jikan lists with cache check
        val jikanCacheKey = "jikan_episodes_$malId"
        val cachedJikan = cacheDao.getCache(jikanCacheKey)
        val currentTime = System.currentTimeMillis()
        val JIKAN_CACHE_LIFETIME = 7 * 24 * 60 * 60 * 1000L // 7 days cache lifetime
        
        val jikanEpisodes = mutableListOf<JikanEpisodeItem>()
        
        if (cachedJikan != null && (currentTime - cachedJikan.timestamp < JIKAN_CACHE_LIFETIME)) {
            try {
                val cachedList = gson.fromJson(cachedJikan.jsonContent, Array<JikanEpisodeItem>::class.java)
                jikanEpisodes.addAll(cachedList)
            } catch (e: Exception) {
                // ignore
            }
        }
        
        if (jikanEpisodes.isEmpty()) {
            try {
                // Fetch first page
                val firstPageUrl = "https://api.jikan.moe/v4/anime/$malId/episodes?page=1"
                val request = okhttp3.Request.Builder().url(firstPageUrl).build()
                val apiResponseJson = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val okCall = okHttpClient.newCall(request).execute()
                    if (okCall.isSuccessful) okCall.body?.string() else null
                }
                
                if (!apiResponseJson.isNullOrEmpty()) {
                    val parsed = gson.fromJson(apiResponseJson, JikanEpisodesResponse::class.java)
                    parsed.data?.let { jikanEpisodes.addAll(it) }
                    val lastPage = parsed.pagination?.last_visible_page ?: 1
                    
                    if (lastPage > 1) {
                        for (p in 2..lastPage) {
                            kotlinx.coroutines.delay(350) // rate-limit backing off
                            val nextPageUrl = "https://api.jikan.moe/v4/anime/$malId/episodes?page=$p"
                            val nextRequest = okhttp3.Request.Builder().url(nextPageUrl).build()
                            val nextPageJson = kotlinx.coroutines.withContext(Dispatchers.IO) {
                                val okCall = okHttpClient.newCall(nextRequest).execute()
                                if (okCall.isSuccessful) okCall.body?.string() else null
                            }
                            if (!nextPageJson.isNullOrEmpty()) {
                                val parsedNext = gson.fromJson(nextPageJson, JikanEpisodesResponse::class.java)
                                parsedNext.data?.let { jikanEpisodes.addAll(it) }
                            }
                        }
                    }
                    
                    // Cache the compiled episodes list
                    if (jikanEpisodes.isNotEmpty()) {
                        cacheDao.insertCache(
                            CacheEntity(
                                cacheKey = jikanCacheKey,
                                jsonContent = gson.toJson(jikanEpisodes),
                                timestamp = currentTime
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AnimeRepositoryImpl", "Error syncing filler with Jikan: ${e.message}")
            }
        }
        
        if (jikanEpisodes.isEmpty()) {
            return episodes
        }
        
        // Build a set of filler/recap episode numbers
        val fillerEpisodeNumbers = jikanEpisodes.filter { it.filler == true || it.recap == true }
            .mapNotNull { it.mal_id }
            .toSet()
        
        android.util.Log.d("AnimeRepositoryImpl", "Found filler episode numbers for malId $malId: $fillerEpisodeNumbers")
        
        return episodes.map { ep ->
            if (fillerEpisodeNumbers.contains(ep.number)) {
                ep.copy(isFiller = true)
            } else {
                ep
            }
        }
    }

    override fun search(query: String, page: Int): Flow<Result<List<Anime>>> = flow {
        emit(Result.Loading)
        
        // 1. Try fetching via AniList GraphQL
        try {
            val queryStr = """
                query (${'$'}search: String, ${'$'}page: Int, ${'$'}perPage: Int) {
                  Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                    media(search: ${'$'}search, type: ANIME) {
                      id
                      idMal
                      title {
                        english
                        romaji
                        userPreferred
                      }
                      coverImage {
                        extraLarge
                        large
                        medium
                      }
                      type
                      format
                      duration
                      episodes
                      averageScore
                    }
                  }
                }
            """.trimIndent()

            val variables = mapOf("search" to query, "page" to page, "perPage" to 15)
            val payload = mapOf("query" to queryStr, "variables" to variables)
            val jsonPayload = gson.toJson(payload)

            val jsonString = kotlinx.coroutines.withContext(Dispatchers.IO) {
                val url = java.net.URL("https://graphql.anilist.co")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.doOutput = true
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                connection.outputStream.use { os ->
                    val input = jsonPayload.toByteArray(charset("utf-8"))
                    os.write(input, 0, input.size)
                }

                if (connection.responseCode == 200) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    throw java.io.IOException("AniList Http Error ${connection.responseCode}: $errorText")
                }
            }

            val parsed = gson.fromJson(jsonString, AniListSearchResponse::class.java)
            val mapped = parsed.data?.Page?.media?.mapNotNull { item ->
                val malId = item.idMal
                val idString = if (malId != null && malId > 0) "mal-$malId" else "anilist-${item.id}"
                Anime(
                    id = idString,
                    title = item.title?.english ?: item.title?.userPreferred ?: item.title?.romaji ?: "Unknown",
                    poster = item.coverImage?.extraLarge ?: item.coverImage?.large ?: "",
                    type = item.format ?: item.type ?: "TV",
                    duration = if (item.duration != null) "${item.duration}m" else "",
                    subEpisodes = item.episodes ?: 0,
                    dubEpisodes = 0,
                    rate = if (item.averageScore != null) String.format(java.util.Locale.US, "%.2f", item.averageScore / 10.0) else ""
                )
            } ?: emptyList()

            if (mapped.isNotEmpty()) {
                emit(Result.Success(mapped))
                return@flow
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("AnimeRepositoryImpl", "AniList search failed: ${e.message}")
        }
        
        // 2. Try Jikan as fallback (only for query strings longer than or equal to 3 characters)
        if (query.trim().length >= 3) {
            try {
                val JIKAN_API_URL = "https://api.jikan.moe/v4/anime"
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "$JIKAN_API_URL?q=$encodedQuery&page=$page"
                
                val request = okhttp3.Request.Builder().url(url).build()
                val resultJson = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val okResponse = okHttpClient.newCall(request).execute()
                    if (okResponse.isSuccessful) okResponse.body?.string() else null
                }
                if (!resultJson.isNullOrEmpty()) {
                    val parsed = gson.fromJson(resultJson, JikanSearchRes::class.java)
                    val mapped = parsed.data?.mapNotNull { item ->
                        if (item.mal_id == null) return@mapNotNull null
                        Anime(
                            id = "mal-${item.mal_id}",
                            title = item.title ?: "Unknown",
                            poster = item.images?.webp?.large_image_url ?: item.images?.webp?.image_url ?: "",
                            type = item.type ?: "TV",
                            duration = item.duration ?: "",
                            subEpisodes = item.episodes ?: 0,
                            dubEpisodes = 0,
                            rate = item.score?.toString() ?: ""
                        )
                    } ?: emptyList()
                    
                    if (mapped.isNotEmpty()) {
                        emit(Result.Success(mapped))
                        return@flow
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
        
        // 3. Fallback to Aniplex proxy API (only for query strings longer than or equal to 3 characters)
        if (query.trim().length >= 3) {
            try {
                val response = apiService.search(query, page)
                if (response.success) {
                    emit(Result.Success(response.data.animes?.map { it.toDomain() } ?: emptyList()))
                    return@flow
                } else {
                    emit(Result.Error("Search failed"))
                    return@flow
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                emit(Result.Error(e.localizedMessage ?: "Search request failed"))
                return@flow
            }
        }

        // If we reached here peacefully and nothing emitted yet, return empty list instead of throwing an error
        emit(Result.Success(emptyList()))
    }

    override fun searchHiAnime(query: String): Flow<Result<List<Anime>>> = search(query, 1)

    override fun getSuggestions(query: String): Flow<Result<List<Anime>>> = flow {
        emit(Result.Loading)
        
        // 1. Try fetching via AniList GraphQL
        try {
            val queryStr = """
                query (${'$'}search: String, ${'$'}page: Int, ${'$'}perPage: Int) {
                  Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                    media(search: ${'$'}search, type: ANIME) {
                      id
                      idMal
                      title {
                        english
                        romaji
                        userPreferred
                      }
                      coverImage {
                        extraLarge
                        large
                        medium
                      }
                      type
                      format
                      duration
                      episodes
                      averageScore
                    }
                  }
                }
            """.trimIndent()

            val variables = mapOf("search" to query, "page" to 1, "perPage" to 10)
            val payload = mapOf("query" to queryStr, "variables" to variables)
            val jsonPayload = gson.toJson(payload)

            val jsonString = kotlinx.coroutines.withContext(Dispatchers.IO) {
                val url = java.net.URL("https://graphql.anilist.co")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.doOutput = true
                connection.connectTimeout = 6000
                connection.readTimeout = 6000

                connection.outputStream.use { os ->
                    val input = jsonPayload.toByteArray(charset("utf-8"))
                    os.write(input, 0, input.size)
                }

                if (connection.responseCode == 200) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    throw java.io.IOException("AniList Http Error ${connection.responseCode}: $errorText")
                }
            }

            val parsed = gson.fromJson(jsonString, AniListSearchResponse::class.java)
            val mapped = parsed.data?.Page?.media?.mapNotNull { item ->
                val malId = item.idMal
                val idString = if (malId != null && malId > 0) "mal-$malId" else "anilist-${item.id}"
                Anime(
                    id = idString,
                    title = item.title?.english ?: item.title?.userPreferred ?: item.title?.romaji ?: "Unknown",
                    poster = item.coverImage?.extraLarge ?: item.coverImage?.large ?: "",
                    type = item.format ?: item.type ?: "TV",
                    duration = if (item.duration != null) "${item.duration}m" else "",
                    subEpisodes = item.episodes ?: 0,
                    dubEpisodes = 0,
                    rate = if (item.averageScore != null) String.format(java.util.Locale.US, "%.2f", item.averageScore / 10.0) else ""
                )
            } ?: emptyList()

            if (mapped.isNotEmpty()) {
                emit(Result.Success(mapped))
                return@flow
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("AnimeRepositoryImpl", "AniList suggestions failed: ${e.message}")
        }

        // 2. Try Jikan as fallback (only for queries longer than or equal to 3 characters)
        if (query.trim().length >= 3) {
            try {
                val JIKAN_API_URL = "https://api.jikan.moe/v4/anime"
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "$JIKAN_API_URL?q=$encodedQuery&limit=10"
                
                val request = okhttp3.Request.Builder().url(url).build()
                val resultJson = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val okResponse = okHttpClient.newCall(request).execute()
                    if (okResponse.isSuccessful) okResponse.body?.string() else null
                }
                if (!resultJson.isNullOrEmpty()) {
                    val parsed = gson.fromJson(resultJson, JikanSearchRes::class.java)
                    val mapped = parsed.data?.mapNotNull { item ->
                        if (item.mal_id == null) return@mapNotNull null
                        Anime(
                            id = "mal-${item.mal_id}",
                            title = item.title ?: "Unknown",
                            poster = item.images?.webp?.large_image_url ?: item.images?.webp?.image_url ?: "",
                            type = item.type ?: "TV",
                            duration = item.duration ?: "",
                            subEpisodes = item.episodes ?: 0,
                            dubEpisodes = 0,
                            rate = item.score?.toString() ?: ""
                        )
                    } ?: emptyList()
                    if (mapped.isNotEmpty()) {
                        emit(Result.Success(mapped))
                        return@flow
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
        
        // 3. Last resort fallback to proxy suggestions (only for queries longer than or equal to 3 characters)
        if (query.trim().length >= 3) {
            try {
                val response = apiService.getSuggestions(query)
                if (response.success) {
                    val list = response.data.suggestions?.map {
                        Anime(
                            id = it.id,
                            title = it.name,
                            poster = it.poster,
                            type = it.moreInfo?.firstOrNull() ?: "",
                            duration = it.moreInfo?.getOrNull(1) ?: "",
                            subEpisodes = 0,
                            dubEpisodes = 0,
                            rate = ""
                        )
                    } ?: emptyList()
                    emit(Result.Success(list))
                    return@flow
                } else {
                    emit(Result.Error("Suggestions failed"))
                    return@flow
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                emit(Result.Error(e.localizedMessage ?: "Suggestions failed"))
                return@flow
            }
        }

        // Return empty list gracefully if reached here
        emit(Result.Success(emptyList()))
    }

    override fun getAnimeByCategory(category: String, page: Int): Flow<Result<List<Anime>>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.getAnimeByCategory(category, page)
            if (response.success) {
                emit(Result.Success(response.data.animes?.map { it.toDomain() } ?: emptyList()))
            } else {
                emit(Result.Error("Category loading failed"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.localizedMessage ?: "Category request failed"))
        }
    }

    override fun getAnimeByGenre(genre: String, page: Int): Flow<Result<List<Anime>>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.getAnimeByGenre(genre, page)
            if (response.success) {
                emit(Result.Success(response.data.animes?.map { it.toDomain() } ?: emptyList()))
            } else {
                emit(Result.Error("Genre loading failed"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.localizedMessage ?: "Genre request failed"))
        }
    }

    private fun convertJSTToUTC(jstTimeStr: String?): String {
        if (jstTimeStr.isNullOrEmpty()) return "12:00"
        val parts = jstTimeStr.split(':')
        if (parts.size < 2) return jstTimeStr
        val hours = parts[0].toIntOrNull()
        val minutes = parts[1].toIntOrNull()
        if (hours == null || minutes == null) return jstTimeStr
        
        var utcHours = hours - 9
        if (utcHours < 0) {
            utcHours += 24
        }
        
        val paddedHours = utcHours.toString().padStart(2, '0')
        val paddedMinutes = minutes.toString().padStart(2, '0')
        return "$paddedHours:$paddedMinutes"
    }

    companion object {
        @Volatile
        private var realWorldOffsetMillis: Long? = null
    }

    private fun calculateAiringEpisode(airedFromStr: String?, totalEpisodes: Int?): Int {
        if (airedFromStr.isNullOrEmpty()) {
            return totalEpisodes ?: 1
        }
        try {
            val datePart = if (airedFromStr.contains("T")) {
                airedFromStr.substringBefore("T")
            } else {
                airedFromStr
            }
            
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val airedDate = sdf.parse(datePart) ?: return totalEpisodes ?: 1
            
            val currentTime = System.currentTimeMillis()
            if (currentTime < airedDate.time) {
                return 1
            }
            
            val diffMs = currentTime - airedDate.time
            val diffDays = diffMs / (1000L * 60 * 60 * 24)
            val calculatedEpisode = (diffDays / 7).toInt() + 1
            
            if (totalEpisodes != null && totalEpisodes > 0) {
                return calculatedEpisode.coerceAtMost(totalEpisodes)
            }
            return calculatedEpisode
        } catch (e: Exception) {
            return totalEpisodes ?: 1
        }
    }

    private suspend fun getRealWorldDateStr(dateStr: String?): String? {
        if (dateStr == null) return null
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val requestedDate = sdf.parse(dateStr) ?: return dateStr

            // 1. Calculate relative offset in days from the device's actual today
            val calDeviceToday = java.util.Calendar.getInstance()
            calDeviceToday.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calDeviceToday.set(java.util.Calendar.MINUTE, 0)
            calDeviceToday.set(java.util.Calendar.SECOND, 0)
            calDeviceToday.set(java.util.Calendar.MILLISECOND, 0)

            val calRequested = java.util.Calendar.getInstance()
            calRequested.time = requestedDate
            calRequested.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calRequested.set(java.util.Calendar.MINUTE, 0)
            calRequested.set(java.util.Calendar.SECOND, 0)
            calRequested.set(java.util.Calendar.MILLISECOND, 0)

            val diffMs = calRequested.timeInMillis - calDeviceToday.timeInMillis
            val diffDays = Math.round(diffMs.toDouble() / (24 * 60 * 60 * 1000)).toInt()

            if (realWorldOffsetMillis == null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val endpoint = "https://aniplex-proxy.f1886391.workers.dev/ping"
                        val connection = java.net.URL(endpoint).openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 3000
                        connection.readTimeout = 3000
                        val dateHeader = connection.getHeaderField("Date")
                        if (dateHeader != null) {
                            val sdfStr = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", java.util.Locale.US)
                            sdfStr.timeZone = java.util.TimeZone.getTimeZone("GMT")
                            val cleanHeader = dateHeader.replace(" GMT", "").trim()
                            val parsedServerDate = sdfStr.parse(cleanHeader)
                            if (parsedServerDate != null) {
                                realWorldOffsetMillis = parsedServerDate.time - System.currentTimeMillis()
                            }
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }

            val realTodayTime = System.currentTimeMillis() + (realWorldOffsetMillis ?: 0L)
            val calRealTarget = java.util.Calendar.getInstance()
            calRealTarget.timeInMillis = realTodayTime
            calRealTarget.add(java.util.Calendar.DAY_OF_YEAR, diffDays)

            return sdf.format(calRealTarget.time)
        } catch (e: Exception) {
            return dateStr
        }
    }

    override fun getSchedules(date: String?): Flow<Result<List<ScheduleItem>>> = flow {
        emit(Result.Loading)
        
        val targetDate = date ?: {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            sdf.format(java.util.Date())
        }()

        // 1. Fetch from AniList GraphQL API (Direct accurate schedule with exact episode count and MAL link!)
        try {
            val (startSec, endSec) = try {
                val sdfStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val parsedDate = sdfStr.parse(targetDate) ?: java.util.Date()
                val start = parsedDate.time / 1000L
                val end = start + (24 * 60 * 60) - 1
                Pair(start, end)
            } catch (e: Exception) {
                val currentMid = (System.currentTimeMillis() / 1000L) / (24 * 60 * 60) * (24 * 60 * 60)
                Pair(currentMid, currentMid + (24 * 60 * 60) - 1)
            }

            val query = """
                query (${'$'}start: Int, ${'$'}end: Int) {
                  Page(page: 1, perPage: 50) {
                    airingSchedules(airingAt_greater: ${'$'}start, airingAt_lesser: ${'$'}end, sort: TIME) {
                      id
                      episode
                      airingAt
                      media {
                        id
                        idMal
                        title {
                          english
                          romaji
                          userPreferred
                        }
                        coverImage {
                          extraLarge
                          large
                        }
                      }
                    }
                  }
                }
            """.trimIndent()

            val variables = mapOf("start" to startSec, "end" to endSec)
            val payload = mapOf("query" to query, "variables" to variables)
            val jsonPayload = gson.toJson(payload)

            val jsonString = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val url = java.net.URL("https://graphql.anilist.co")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.doOutput = true
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                connection.outputStream.use { os ->
                    val input = jsonPayload.toByteArray(charset("utf-8"))
                    os.write(input, 0, input.size)
                }

                if (connection.responseCode == 200) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    throw java.io.IOException("AniList Http Error ${connection.responseCode}: $errorText")
                }
            }

            val aniResponse = gson.fromJson(jsonString, AniListGraphQLResponse::class.java)
            val schedulesList = aniResponse.data?.Page?.airingSchedules?.map { schedule ->
                val media = schedule.media
                val title = media?.title?.english ?: media?.title?.romaji ?: media?.title?.userPreferred ?: "Unknown Title"
                val poster = media?.coverImage?.extraLarge ?: media?.coverImage?.large ?: ""
                
                val sdfTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val formattedTime = if (schedule.airingAt != null) {
                    sdfTime.format(java.util.Date(schedule.airingAt * 1000L))
                } else {
                    "00:00"
                }

                val malId = media?.idMal
                val idString = if (malId != null && malId > 0) "mal-$malId" else "anilist-${schedule.id ?: schedule.media?.id ?: schedule.hashCode()}"

                ScheduleItem(
                    id = idString,
                    title = title,
                    time = formattedTime,
                    episode = schedule.episode ?: 1,
                    poster = poster
                )
            }?.distinctBy { it.id } ?: emptyList()

            if (schedulesList.isNotEmpty()) {
                emit(Result.Success(schedulesList))
                return@flow
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            // Fall back to subsequent layers if AniList query fails
        }

        val realDate = getRealWorldDateStr(date)
        
        // 2. Fetch from remote proxy (preferred since it has correct active schedule episode numbers!)
        try {
            val response = apiService.getSchedules(realDate)
            if (response.success) {
                val list = response.data.scheduledAnimes?.map { it.toDomain() }?.distinctBy { it.id } ?: emptyList()
                if (list.isNotEmpty()) {
                    emit(Result.Success(list))
                    return@flow
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Fall back
        }

        // 3. Fallback to Jikan API directly
        try {
            val dayOfWeek = try {
                val cal = java.util.Calendar.getInstance()
                if (date != null) {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    val parsedDate = sdf.parse(date)
                    if (parsedDate != null) {
                        cal.time = parsedDate
                    }
                }
                val dayNum = cal.get(java.util.Calendar.DAY_OF_WEEK)
                when (dayNum) {
                    java.util.Calendar.SUNDAY -> "sunday"
                    java.util.Calendar.MONDAY -> "monday"
                    java.util.Calendar.TUESDAY -> "tuesday"
                    java.util.Calendar.WEDNESDAY -> "wednesday"
                    java.util.Calendar.THURSDAY -> "thursday"
                    java.util.Calendar.FRIDAY -> "friday"
                    java.util.Calendar.SATURDAY -> "saturday"
                    else -> "monday"
                }
            } catch (t: Throwable) {
                "monday"
            }

            val urlString = "https://api.jikan.moe/v4/schedules?filter=$dayOfWeek"
            
            val jsonString = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val connection = java.net.URL(urlString).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                connection.setRequestProperty("Accept", "application/json")
                
                if (connection.responseCode == 200) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    throw java.io.IOException("HTTP error: ${connection.responseCode}")
                }
            }
            
            val jikanResponse = gson.fromJson(jsonString, JikanSchedulesResponse::class.java)
            val schedulesList = jikanResponse.data?.mapIndexed { index, anime ->
                val malId = anime.mal_id
                val idString = if (malId != null) "mal-$malId" else "mal-fallback-$index"
                val name = anime.title_english ?: anime.title ?: "Unknown Title"
                val time = convertJSTToUTC(anime.broadcast?.time)
                val poster = anime.images?.webp?.large_image_url 
                    ?: anime.images?.webp?.image_url 
                    ?: anime.images?.jpg?.large_image_url 
                    ?: anime.images?.jpg?.image_url 
                    ?: ""
                    
                ScheduleItem(
                    id = idString,
                    title = name,
                    time = time,
                    episode = calculateAiringEpisode(anime.aired?.from, anime.episodes),
                    poster = poster
                )
            }?.distinctBy { it.id } ?: emptyList()
            
            if (schedulesList.isNotEmpty()) {
                emit(Result.Success(schedulesList))
            } else {
                emit(Result.Error("Schedule load failed"))
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            emit(Result.Error(t.localizedMessage ?: "Schedule request failed"))
        }
    }

    override fun getCharacters(id: String): Flow<Result<List<Character>>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.getCharacters(id)
            if (response.success) {
                emit(Result.Success(response.data.characters?.map { it.toDomain() } ?: emptyList()))
            } else {
                emit(Result.Error("Characters loading failed"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.localizedMessage ?: "Characters request failed"))
        }
    }

    override fun getEpisodeStream(episodeId: String, server: String, category: String): Flow<Result<EpisodeStream>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.getEpisodeSources(episodeId, server, category)
            if (response.success) {
                val data = response.data
                val source = data.sources?.firstOrNull()
                if (source != null) {
                    val isChainedSoldierEp = episodeId in listOf("114679", "114988", "116941", "117733", "119239", "119827", "120013", "120736", "121518", "121826", "122126", "122135")
                    val isGushingEp = episodeId in listOf("114664", "114670", "115816", "117709", "119152", "119824", "119998", "120643", "121489", "121671", "122125", "122421", "122422")
                    val isOptionA = isChainedSoldierEp || isGushingEp
                    val useUncensored = preferenceManager.preferredAnimeVersion == "uncensored"

                    var videoUrl = source.url
                    if (useUncensored && isOptionA) {
                        if (videoUrl.contains("/sub")) {
                            videoUrl = videoUrl.replace("/sub", "/sub?version=uncut")
                        } else if (videoUrl.contains("/dub")) {
                            videoUrl = videoUrl.replace("/dub", "/dub?version=uncut")
                        } else {
                            videoUrl = if (videoUrl.contains("?")) "$videoUrl&version=uncut" else "$videoUrl?version=uncut"
                        }
                    }

                    val originalSubtitles = data.tracks?.filter { it.kind == "captions" || it.kind == "subtitles" }?.map {
                        SubtitleTrack(
                            url = it.file,
                            label = it.label ?: "English",
                            isDefault = it.label?.equals("english", ignoreCase = true) == true
                        )
                    } ?: emptyList()

                    val finalSubtitles = if (useUncensored && isOptionA) {
                        originalSubtitles + SubtitleTrack(
                            url = "https://example.com/uncensored_indicator.vtt",
                            label = "Uncensored Mode ACTIVE 🌟",
                            isDefault = false
                        )
                    } else {
                        originalSubtitles
                    }

                    val stream = EpisodeStream(
                        videoUrl = videoUrl,
                        isHls = source.type.equals("hls", ignoreCase = true) || videoUrl.contains(".m3u8"),
                        subtitles = finalSubtitles,
                        introStart = data.intro?.let { it.start * 1000L } ?: 0L,
                        introEnd = data.intro?.let { it.end * 1000L } ?: 0L,
                        outroStart = data.outro?.let { it.start * 1000L } ?: 0L,
                        outroEnd = data.outro?.let { it.end * 1000L } ?: 0L
                    )
                    emit(Result.Success(stream))
                } else {
                    emit(Result.Error("No video source link returned by API"))
                }
            } else {
                emit(Result.Error("API returned success = false"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.localizedMessage ?: "Failed to load episode stream sources"))
        }
    }

    override fun filterAnime(
        type: String?,
        status: String?,
        genres: String?,
        sort: String?,
        language: String?,
        page: Int
    ): Flow<Result<List<Anime>>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.filterAnime(
                type = type,
                status = status,
                genres = genres,
                sort = sort,
                language = language,
                page = page
            )
            if (response.success) {
                emit(Result.Success(response.data.animes?.map { it.toDomain() } ?: emptyList()))
            } else {
                emit(Result.Error("Filter request failed"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.localizedMessage ?: "Filter request failed"))
        }
    }

    private suspend fun filterReleasedSeasons(seasons: List<Season>): List<Season> {
        if (seasons.isEmpty()) return emptyList()
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            kotlinx.coroutines.coroutineScope {
                seasons.map { season ->
                    async {
                        val isResolvable = try {
                            if (season.malId == "38000" || season.malId == "49926") {
                                true
                            } else {
                                val cacheKey = "resolve_mal_${season.malId}"
                                val cached = cacheDao.getCache(cacheKey)
                                if (cached != null && cached.jsonContent.isNotBlank()) {
                                    true
                                } else {
                                    val resolveResponse = apiService.resolveMAL(season.malId)
                                    if (resolveResponse.success && resolveResponse.data != null && resolveResponse.data.anikotoId.isNotBlank()) {
                                        cacheDao.insertCache(
                                            CacheEntity(
                                                cacheKey = cacheKey,
                                                jsonContent = resolveResponse.data.anikotoId,
                                                timestamp = System.currentTimeMillis()
                                            )
                                        )
                                        true
                                    } else {
                                        false
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            false
                        }
                        season to isResolvable
                    }
                }.map { it.await() }
            }
        }.filter { it.second }.map { it.first }
    }

    override fun getSeasons(malId: String, forceRefresh: Boolean): Flow<Result<List<Season>>> = flow {
        if (malId.isBlank()) {
            emit(Result.Success(emptyList()))
            return@flow
        }
        val cacheKey = "seasons_$malId"
        emit(Result.Loading)

        val cachedEntity = cacheDao.getCache(cacheKey)
        val currentTime = System.currentTimeMillis()
        val SEASONS_CACHE_LIFETIME = 7 * 24 * 60 * 60 * 1000L // 7 days

        if (cachedEntity != null && !forceRefresh) {
            try {
                val cachedResponse = gson.fromJson(cachedEntity.jsonContent, SeasonsResponse::class.java)
                val seasons = cachedResponse.data.seasons?.map { it.toDomain() } ?: emptyList()
                if (seasons.isNotEmpty()) {
                    val filteredSeasons = filterReleasedSeasons(seasons)
                    emit(Result.Success(filteredSeasons))
                    if (currentTime - cachedEntity.timestamp < SEASONS_CACHE_LIFETIME) {
                        return@flow
                    }
                }
            } catch (e: Exception) {
                // Fallback to network
            }
        }

        try {
            var response: SeasonsResponse? = null
            var lastException: Exception? = null
            val maxRetries = 3
            for (attempt in 1..maxRetries) {
                try {
                    val apiResponse = apiService.getSeasons(malId)
                    if (apiResponse.success) {
                        response = apiResponse
                        break
                    } else if (attempt < maxRetries) {
                        kotlinx.coroutines.delay(1000L * attempt)
                    }
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < maxRetries) {
                        kotlinx.coroutines.delay(1000L * attempt)
                    }
                }
            }

            if (response != null && response.success) {
                val seasons = response.data.seasons?.map { it.toDomain() } ?: emptyList()
                
                // Cache this seasons list NOT ONLY under the requested malId,
                // but ALSO under the malId of EVERY single season in the list!
                // This guarantees that all secondary seasons are linked to the exact same full list of seasons.
                if (seasons.isNotEmpty()) {
                    seasons.forEach { season ->
                        try {
                            val linkedResponse = SeasonsResponse(
                                success = true,
                                data = SeasonsDataDto(
                                    seasons = response.data.seasons,
                                    currentMalId = season.malId
                                )
                            )
                            cacheDao.insertCache(
                                CacheEntity(
                                    cacheKey = "seasons_${season.malId}",
                                    jsonContent = gson.toJson(linkedResponse),
                                    timestamp = currentTime
                                )
                            )
                        } catch (e: Exception) {
                            // Non-blocking
                        }
                    }
                }
                val filteredSeasons = filterReleasedSeasons(seasons)
                emit(Result.Success(filteredSeasons))
            } else {
                val errorMsg = lastException?.localizedMessage ?: "Failed to fetch seasons"
                emit(Result.Error(errorMsg))
            }
        } catch (e: Exception) {
            if (cachedEntity != null) {
                try {
                    val cachedResponse = gson.fromJson(cachedEntity.jsonContent, SeasonsResponse::class.java)
                    val seasons = cachedResponse.data.seasons?.map { it.toDomain() } ?: emptyList()
                    val filteredSeasons = filterReleasedSeasons(seasons)
                    emit(Result.Success(filteredSeasons))
                } catch (jsonEx: Exception) {
                    emit(Result.Error(e.localizedMessage ?: "Failed to fetch seasons"))
                }
            } else {
                emit(Result.Error(e.localizedMessage ?: "Failed to fetch seasons"))
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun resolveMAL(malId: String): Flow<Result<String>> = flow {
        if (malId.isBlank()) {
            emit(Result.Error("Blank MAL ID"))
            return@flow
        }

        // Handle known duplicate MAL ID mappings (e.g., Demon Slayer S1 TV vs Sibling's Bond Movie)
        val overrideId = when (malId) {
            "38000" -> "1551" // Demon Slayer: Kimetsu no Yaiba S1 (26 episodes) instead of Sibling's Bond Movie (1 episode)
            "49926" -> "17870" // Demon Slayer Mugen Train TV Arc (7 episodes)
            else -> null
        }
        if (overrideId != null) {
            emit(Result.Success(overrideId))
            return@flow
        }

        val cacheKey = "resolve_mal_$malId"
        emit(Result.Loading)

        val cachedEntity = cacheDao.getCache(cacheKey)
        val currentTime = System.currentTimeMillis()
        val RESOLVE_CACHE_LIFETIME = 30 * 24 * 60 * 60 * 1000L // 30 days

        if (cachedEntity != null) {
            try {
                val resolvedId = cachedEntity.jsonContent
                if (resolvedId.isNotBlank()) {
                    emit(Result.Success(resolvedId))
                    if (currentTime - cachedEntity.timestamp < RESOLVE_CACHE_LIFETIME) {
                        return@flow
                    }
                }
            } catch (e: Exception) {
                // Fallback
            }
        }

        try {
            val response = apiService.resolveMAL(malId)
            if (response.success && response.data != null) {
                val resolvedId = response.data.anikotoId
                cacheDao.insertCache(
                    CacheEntity(
                        cacheKey = cacheKey,
                        jsonContent = resolvedId,
                        timestamp = currentTime
                    )
                )
                emit(Result.Success(resolvedId))
            } else {
                if (cachedEntity != null) {
                    emit(Result.Success(cachedEntity.jsonContent))
                } else {
                    emit(Result.Error("Could not resolve MAL ID"))
                }
            }
        } catch (e: Exception) {
            if (cachedEntity != null) {
                emit(Result.Success(cachedEntity.jsonContent))
            } else {
                emit(Result.Error(e.localizedMessage ?: "Failed to resolve MAL ID"))
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun getCachedAnimeDetail(id: String): AnimeDetail? {
        val cachedEntity = cacheDao.getCache("detail_$id") ?: return null
        return try {
            val cachedResponse = gson.fromJson(cachedEntity.jsonContent, AnimeDetailResponse::class.java)
            cachedResponse.data.toDomain()
        } catch (e: Exception) {
            null
        }
    }

    override fun getSkipTimes(animeId: Int, episodeNumber: Int, episodeLength: Double?): Flow<Result<SkipTimes>> = flow {
        emit(Result.Loading)
        val cacheKey = "aniskip_${animeId}_$episodeNumber"
        val cachedEntity = cacheDao.getCache(cacheKey)
        val currentTime = System.currentTimeMillis()
        val ONE_DAY = 24 * 60 * 60 * 1000L

        if (cachedEntity != null && (currentTime - cachedEntity.timestamp < ONE_DAY)) {
            try {
                val cachedResponse = gson.fromJson(cachedEntity.jsonContent, com.aniplex.app.data.remote.dto.AniSkipResponse::class.java)
                emit(Result.Success(mapAniSkipResponseToDomain(cachedResponse)))
                return@flow
            } catch (e: Exception) {
                // Ignore and fetch from web
            }
        }

        try {
            val lengthQuery = episodeLength?.toInt() ?: 0
            val response = apiSkipApiService.getSkipTimes(
                animeId = animeId,
                episodeNumber = episodeNumber,
                episodeLength = lengthQuery
            )
            if (response != null && response.found && response.results != null) {
                cacheDao.insertCache(
                    CacheEntity(
                        cacheKey = cacheKey,
                        jsonContent = gson.toJson(response),
                        timestamp = currentTime
                    )
                )
                emit(Result.Success(mapAniSkipResponseToDomain(response)))
            } else {
                emit(Result.Success(SkipTimes()))
            }
        } catch (e: Exception) {
            if (cachedEntity != null) {
                try {
                    val cachedResponse = gson.fromJson(cachedEntity.jsonContent, com.aniplex.app.data.remote.dto.AniSkipResponse::class.java)
                    emit(Result.Success(mapAniSkipResponseToDomain(cachedResponse)))
                } catch (jsonEx: Exception) {
                    emit(Result.Error("Failed to fetch skip times: ${e.localizedMessage}"))
                }
            } else {
                emit(Result.Success(SkipTimes()))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun mapAniSkipResponseToDomain(response: com.aniplex.app.data.remote.dto.AniSkipResponse): SkipTimes {
        var introStart = -1L
        var introEnd = -1L
        var outroStart = -1L
        var outroEnd = -1L

        response.results?.forEach { result ->
            val startMs = (result.interval.startTime * 1000).toLong()
            val endMs = (result.interval.endTime * 1000).toLong()
            when (result.skipType) {
                "op" -> {
                    introStart = startMs
                    introEnd = endMs
                }
                "ed" -> {
                    outroStart = startMs
                    outroEnd = endMs
                }
            }
        }
        return SkipTimes(
            introStart = introStart,
            introEnd = introEnd,
            outroStart = outroStart,
            outroEnd = outroEnd
        )
    }
}

private data class JikanSchedulesResponse(
    val data: List<JikanAnime>?
)

private data class JikanSearchRes(
    val data: List<JikanAnime>?
)

private data class JikanAired(
    val from: String? = null
)

private data class JikanAnime(
    val mal_id: Int?,
    val title: String?,
    val title_english: String?,
    val images: JikanImages?,
    val broadcast: JikanBroadcast?,
    val type: String? = null,
    val duration: String? = null,
    val episodes: Int? = null,
    val score: Double? = null,
    val aired: JikanAired? = null
)

private data class JikanImages(
    val webp: JikanImagesStyle?,
    val jpg: JikanImagesStyle?
)

private data class JikanImagesStyle(
    val large_image_url: String?,
    val image_url: String?
)

private data class JikanBroadcast(
    val time: String?
)

private data class AniListGraphQLResponse(
    val data: AniListData?
)

private data class AniListData(
    val Page: AniPage?
)

private data class AniPage(
    val airingSchedules: List<AniAiringSchedule>?
)

private data class AniAiringSchedule(
    val id: Int?,
    val episode: Int?,
    val airingAt: Long?,
    val media: AniMedia?
)

private data class AniMedia(
    val id: Int?,
    val idMal: Int?,
    val title: AniTitle?,
    val coverImage: AniCoverImage?
)

private data class AniTitle(
    val english: String?,
    val romaji: String?,
    val userPreferred: String?
)

private data class AniCoverImage(
    val extraLarge: String?,
    val large: String?
)

private data class JikanEpisodesResponse(
    val data: List<JikanEpisodeItem>?,
    val pagination: JikanPagination?
)

private data class JikanEpisodeItem(
    val mal_id: Int?,
    val title: String?,
    val filler: Boolean?,
    val recap: Boolean?
)

private data class JikanPagination(
    val last_visible_page: Int?,
    val has_next_page: Boolean?
)

private data class AniListSearchResponse(
    val data: AniListSearchData?
)

private data class AniListSearchData(
    val Page: AniListSearchPage?
)

private data class AniListSearchPage(
    val media: List<AniListMedia>?
)

private data class AniListMedia(
    val id: Int?,
    val idMal: Int?,
    val title: AniTitle?,
    val coverImage: AniCoverImage?,
    val type: String?,
    val format: String?,
    val duration: Int?,
    val episodes: Int?,
    val averageScore: Double?
)
