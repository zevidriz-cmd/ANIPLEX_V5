package com.aniplex.app.data.repository

import com.aniplex.app.data.local.dao.CacheDao
import com.aniplex.app.data.local.entity.CacheEntity
import com.aniplex.app.data.mapper.toDomain
import com.aniplex.app.presentation.screens.player.DebugLogManager
import com.aniplex.app.data.remote.api.HiAnimeApiService
import com.aniplex.app.data.remote.api.FallbackApiService
import com.aniplex.app.data.remote.dto.AnimeDetailResponse
import com.aniplex.app.data.remote.dto.EpisodesResponse
import com.aniplex.app.data.remote.dto.HomeResponse
import com.aniplex.app.data.remote.dto.SeasonsResponse
import com.aniplex.app.data.remote.dto.SeasonsDataDto
import com.aniplex.app.data.remote.dto.MegaplaySourcesResponse
import com.aniplex.app.data.local.preferences.PreferenceManager
import com.aniplex.app.domain.model.*
import com.aniplex.app.domain.repository.AnimeRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

data class LocalMapping(
    val keywords: List<String>,
    val malId: Int,
    val id: String
)

val LOCAL_ACCURATE_MAPPINGS = listOf(
    LocalMapping(listOf("onepiece", "one piece", "the one piece"), 21, "1642"),
    LocalMapping(listOf("naruto"), 20, "958"),
    LocalMapping(listOf("naruto shippuden", "narutoshippuden", "shippuden"), 1735, "1498"),
    LocalMapping(listOf("bleach"), 269, "1057"),
    LocalMapping(listOf("hunter x hunter", "hunterxhunter", "hxh"), 11061, "68"),
    LocalMapping(listOf("black clover", "blackclover"), 34572, "2121"),
    LocalMapping(listOf("fairy tail", "fairytail"), 6702, "489"),
    LocalMapping(listOf("dragon ball z", "dragonballz", "dbz"), 813, "1456"),
    LocalMapping(listOf("dragon ball super", "dragonballsuper", "dbs"), 30694, "132"),
    LocalMapping(listOf("boruto"), 34566, "4587"),
    LocalMapping(listOf("reincarnated as a slime", "slime", "scarlet bond"), 48761, "7200"),
    LocalMapping(listOf("demon slayer", "kimetsu no yaiba", "mugen train"), 39597, "5870"),
    LocalMapping(listOf("jujutsu kaisen", "jujutsu kaisen 0", "jujutsu kaisen movie"), 48561, "6688")
)

class AnimeRepositoryImpl @Inject constructor(
    private val apiService: HiAnimeApiService,
    private val fallbackApiService: FallbackApiService,
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
        var emittedCache = false

        if (cachedEntity != null) {
            try {
                val cachedResponse = gson.fromJson(cachedEntity.jsonContent, HomeResponse::class.java)
                emit(Result.Success(cachedResponse.data.toDomain()))
                emittedCache = true
            } catch (e: Exception) {
                // Ignore and proceed
            }
        }

        val cacheExpired = cachedEntity == null || (currentTime - cachedEntity.timestamp > HOME_CACHE_LIFETIME)

        if (forceRefresh || cacheExpired || !emittedCache) {
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
                    if (!emittedCache) {
                        emit(Result.Error("API returned success = false"))
                    }
                }
            } catch (e: Exception) {
                if (!emittedCache) {
                    emit(Result.Error(e.localizedMessage ?: "Unknown network error"))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun getAnimeDetail(id: String, forceRefresh: Boolean): Flow<Result<AnimeDetail>> = flow {
        val cacheKey = "detail_$id"
        emit(Result.Loading)

        val cachedEntity = cacheDao.getCache(cacheKey)
        val currentTime = System.currentTimeMillis()
        var emittedCache = false

        if (cachedEntity != null) {
            try {
                val cachedResponse = gson.fromJson(cachedEntity.jsonContent, AnimeDetailResponse::class.java)
                emit(Result.Success(cachedResponse.data.toDomain()))
                emittedCache = true
            } catch (e: Exception) {
                // Ignore and proceed
            }
        }

        val cacheExpired = cachedEntity == null || (currentTime - cachedEntity.timestamp > DETAIL_CACHE_LIFETIME)

        if (forceRefresh || cacheExpired || !emittedCache) {
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
                    if (!emittedCache) {
                        emit(Result.Error("API returned success = false"))
                    }
                }
            } catch (e: Exception) {
                if (!emittedCache) {
                    emit(Result.Error(e.localizedMessage ?: "Network error"))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun getMalIdFromAnimeIdOrCache(animeId: String): String {
        if (animeId.startsWith("mal-")) {
            return animeId.substringAfter("mal-")
        }
        try {
            val cachedDetail = getCachedAnimeDetail(animeId)
            if (cachedDetail != null && cachedDetail.malId.isNotBlank()) {
                return cachedDetail.malId
            }
        } catch (e: Exception) {
            // ignore
        }
        try {
            val detailResponse = apiService.getAnimeDetail(animeId)
            if (detailResponse.success) {
                return detailResponse.data.anime.info.malId ?: ""
            }
        } catch (e: Exception) {
            // ignore
        }
        return ""
    }

    override fun getEpisodes(id: String, forceRefresh: Boolean): Flow<Result<List<Episode>>> = flow {
        val cacheKey = "episodes_$id"
        emit(Result.Loading)

        val cachedEntity = cacheDao.getCache(cacheKey)
        val currentTime = System.currentTimeMillis()
        var emittedResponse: EpisodesResponse? = null

        // 1. Emit cache immediately if it exists (Stale-While-Revalidate)
        if (cachedEntity != null) {
            try {
                val cachedResponse = gson.fromJson(cachedEntity.jsonContent, EpisodesResponse::class.java)
                emittedResponse = cachedResponse
                val domainEpisodes = cachedResponse.data.episodes.map { it.toDomain() }
                
                val malId = getMalIdFromAnimeIdOrCache(id)
                val hasJikanCache = if (malId.isNotBlank()) {
                    cacheDao.getCache("jikan_episodes_$malId") != null
                } else false

                if (hasJikanCache) {
                    val enriched = syncEpisodesWithJikanFiller(id, domainEpisodes)
                    emit(Result.Success(enriched))
                } else {
                    emit(Result.Success(domainEpisodes))
                }
            } catch (e: Exception) {
                // Ignore and proceed
            }
        }

        val cacheExpired = cachedEntity == null || (currentTime - cachedEntity.timestamp > EPISODES_CACHE_LIFETIME)

        if (forceRefresh || cacheExpired || emittedResponse == null) {
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
                    
                    val malId = getMalIdFromAnimeIdOrCache(id)
                    val hasJikanCache = if (malId.isNotBlank()) {
                        cacheDao.getCache("jikan_episodes_$malId") != null
                    } else false

                    if (hasJikanCache) {
                        val enriched = syncEpisodesWithJikanFiller(id, domainEpisodes)
                        emit(Result.Success(enriched))
                    } else {
                        emit(Result.Success(domainEpisodes))
                        if (malId.isNotBlank()) {
                            try {
                                val enriched = syncEpisodesWithJikanFiller(id, domainEpisodes)
                                emit(Result.Success(enriched))
                            } catch (e: Exception) {
                                // Ignore Jikan enrichment failure since base episodes are already emitted
                            }
                        }
                    }
                } else {
                    if (emittedResponse == null) {
                        emit(Result.Error("API returned success = false"))
                    }
                }
            } catch (e: Exception) {
                if (emittedResponse == null) {
                    emit(Result.Error(e.localizedMessage ?: "Network error"))
                }
            }
        } else {
            // Cache was emitted, but if Jikan cache wasn't ready, load it now
            val cachedResponse = emittedResponse
            if (cachedResponse != null) {
                val domainEpisodes = cachedResponse.data.episodes.map { it.toDomain() }
                val malId = getMalIdFromAnimeIdOrCache(id)
                if (malId.isNotBlank()) {
                    val hasJikanCache = cacheDao.getCache("jikan_episodes_$malId") != null
                    if (!hasJikanCache) {
                        try {
                            val enriched = syncEpisodesWithJikanFiller(id, domainEpisodes)
                            emit(Result.Success(enriched))
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun syncEpisodesWithJikanFiller(animeId: String, episodes: List<Episode>): List<Episode> {
        if (episodes.isEmpty()) return episodes
        
        val malId = getMalIdFromAnimeIdOrCache(animeId)
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

    private suspend fun resolveBackupAnimeList(items: List<Anime>, isBackupVal: Boolean = false): List<Anime> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            items.map { anime ->
                val malId = if (anime.id.startsWith("mal-")) {
                    anime.id.substringAfter("mal-")
                } else {
                    null
                }

                if (malId != null) {
                    val cacheKey = "resolve_mal_$malId"
                    val cachedEntity = cacheDao.getCache(cacheKey)
                    var resolvedId = cachedEntity?.jsonContent

                    if (resolvedId.isNullOrBlank()) {
                        try {
                            val response = apiService.resolveMAL(malId)
                            if (response.success && response.data != null) {
                                resolvedId = response.data.anikotoId
                                cacheDao.insertCache(
                                    CacheEntity(
                                        cacheKey = cacheKey,
                                        jsonContent = resolvedId,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            // ignore resolution network error
                        }
                    }

                    if (!resolvedId.isNullOrBlank()) {
                        try {
                            val detailResponse = apiService.getAnimeDetail(resolvedId)
                            if (detailResponse.success) {
                                val detail = detailResponse.data.anime
                                 return@map anime.copy(
                                     id = resolvedId,
                                     title = detail.info.name,
                                     poster = detail.info.poster,
                                     type = detail.info.stats?.type ?: anime.type,
                                     duration = detail.info.stats?.duration ?: anime.duration,
                                     subEpisodes = detail.info.stats?.episodes?.sub ?: anime.subEpisodes,
                                     dubEpisodes = detail.info.stats?.episodes?.dub ?: anime.dubEpisodes,
                                     rate = detail.info.stats?.rating ?: anime.rate,
                                     isBackup = false
                                 )
                             }
                         } catch (e: Exception) {
                             return@map anime.copy(id = resolvedId, isBackup = isBackupVal)
                         }
                    }
                }
                anime.copy(isBackup = isBackupVal)
            }
        }
    }

    override fun search(query: String, page: Int): Flow<Result<List<Anime>>> = flow<Result<List<Anime>>> {
        emit(Result.Loading)

        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            emit(Result.Success(emptyList()))
            return@flow
        }

        try {
            // 1. Gather results from primary search and stop-word-free fallback search in parallel
            val stopWordsRegex = Regex("\\b(a|an|the)\\b", RegexOption.IGNORE_CASE)
            val hasStopWords = stopWordsRegex.containsMatchIn(trimmedQuery)
            val words = trimmedQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }

            val mergedAnimes = mutableListOf<Anime>()
            var primarySearchFailed = false

            coroutineScope {
                // A. Primary search call
                val primaryDeferred = async(Dispatchers.IO) {
                    try {
                        val response = apiService.search(trimmedQuery, page)
                        if (response.success) {
                            response.data.animes?.map { it.toDomain() } ?: emptyList()
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }

                // B. Stop-word fallback search call
                val fallbackDeferred: kotlinx.coroutines.Deferred<List<Anime>?>? = if (hasStopWords && words.size > 1) {
                    val fallbackQuery = trimmedQuery.replace(stopWordsRegex, "").replace(Regex("\\s+"), " ").trim()
                    if (fallbackQuery.lowercase() != trimmedQuery.lowercase()) {
                        async(Dispatchers.IO) {
                            try {
                                val response = apiService.search(fallbackQuery, page)
                                if (response.success) {
                                    response.data.animes?.map { it.toDomain() } ?: emptyList()
                                } else null
                            } catch (e: Exception) {
                                null
                            }
                        }
                    } else null
                } else null

                val primaryResults = primaryDeferred.await()
                val fallbackResults = fallbackDeferred?.await()

                primarySearchFailed = (primaryResults == null) && (fallbackDeferred == null || fallbackResults == null)

                if (primaryResults != null) {
                    mergedAnimes.addAll(primaryResults)
                }
                if (fallbackResults != null) {
                    val existingIds = mergedAnimes.map { it.id }.toSet()
                    fallbackResults.forEach { anime ->
                        if (anime.id !in existingIds) {
                            mergedAnimes.add(anime)
                        }
                    }
                }

                // If both failed or are empty, and query length is >= 3, try Jikan search as fallback primary provider
                if (mergedAnimes.isEmpty() && trimmedQuery.length >= 3) {
                    try {
                        val JIKAN_API_URL = "https://api.jikan.moe/v4/anime"
                        val encodedQuery = java.net.URLEncoder.encode(trimmedQuery, "UTF-8")
                        val url = "$JIKAN_API_URL?q=$encodedQuery&page=$page"
                        val request = okhttp3.Request.Builder().url(url).build()
                        val resultJson = withContext(Dispatchers.IO) {
                            val okResponse = okHttpClient.newCall(request).execute()
                            if (okResponse.isSuccessful) okResponse.body?.string() else null
                        }
                        if (!resultJson.isNullOrEmpty()) {
                            val parsed = gson.fromJson(resultJson, JikanSearchRes::class.java)
                            val rawList = parsed.data?.mapNotNull { item ->
                                if (item.mal_id == null) return@mapNotNull null
                                Anime(
                                    id = "mal-${item.mal_id}",
                                    title = item.title ?: "Unknown",
                                    poster = item.images?.webp?.large_image_url ?: item.images?.webp?.image_url ?: "",
                                    type = item.type ?: "TV",
                                    duration = item.duration ?: "",
                                    subEpisodes = item.episodes ?: 0,
                                    dubEpisodes = 0,
                                    rate = item.score?.toString() ?: "",
                                    isBackup = primarySearchFailed
                                )
                            } ?: emptyList()
                            if (rawList.isNotEmpty()) {
                                mergedAnimes.addAll(resolveBackupAnimeList(rawList, isBackupVal = primarySearchFailed))
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }

            // 2. Client-side smart enrichment (only on page 1)
            var finalAnimes = mergedAnimes.toList()
            if (page == 1) {
                val cleanedQuery = trimmedQuery.lowercase()
                val matchedIds = mutableListOf<String>()

                // A. Check local accurate mappings first
                for (entry in LOCAL_ACCURATE_MAPPINGS) {
                    if (entry.keywords.any { cleanedQuery.contains(it) || it.contains(cleanedQuery) }) {
                        if (entry.id !in matchedIds) {
                            matchedIds.add(entry.id)
                        }
                    }
                }

                // B. Dynamic Jikan search fallback for other shows (if primary is not Jikan fallback itself)
                if (!primarySearchFailed) {
                    try {
                        val JIKAN_API_URL = "https://api.jikan.moe/v4/anime"
                        val encodedQuery = java.net.URLEncoder.encode(trimmedQuery, "UTF-8")
                        val url = "$JIKAN_API_URL?q=$encodedQuery&limit=8"
                        val request = okhttp3.Request.Builder().url(url).build()
                        val jikanJson = withContext(Dispatchers.IO) {
                            val okResponse = okHttpClient.newCall(request).execute()
                            if (okResponse.isSuccessful) okResponse.body?.string() else null
                        }
                        if (!jikanJson.isNullOrEmpty()) {
                            val parsed = gson.fromJson(jikanJson, JikanSearchRes::class.java)
                            val malIds = parsed.data?.mapNotNull { it.mal_id } ?: emptyList()

                            // Resolve MAL IDs in parallel
                            coroutineScope {
                                val resolveJobs = malIds.map { malId ->
                                    async(Dispatchers.IO) {
                                        val cacheKey = "resolve_mal_$malId"
                                        val cachedEntity = cacheDao.getCache(cacheKey)
                                        var resolvedId = cachedEntity?.jsonContent
                                        if (resolvedId.isNullOrBlank()) {
                                            try {
                                                val response = apiService.resolveMAL(malId.toString())
                                                if (response.success && response.data != null) {
                                                    resolvedId = response.data.anikotoId
                                                    cacheDao.insertCache(
                                                        CacheEntity(
                                                            cacheKey = cacheKey,
                                                            jsonContent = resolvedId,
                                                            timestamp = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                // ignore
                                            }
                                        }
                                        resolvedId
                                    }
                                }
                                val resolvedIds = resolveJobs.awaitAll().filterNotNull()
                                for (rid in resolvedIds) {
                                    if (rid.isNotBlank() && rid !in matchedIds) {
                                        matchedIds.add(rid)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                // C. Fetch details for matched IDs that are NOT already in the results list
                val idsToFetch = matchedIds.filter { id -> !finalAnimes.any { it.id == id } }
                if (idsToFetch.isNotEmpty()) {
                    coroutineScope {
                        val fetchJobs = idsToFetch.map { id ->
                            async(Dispatchers.IO) {
                                try {
                                    val detailResponse = apiService.getAnimeDetail(id)
                                    if (detailResponse.success) {
                                        val detail = detailResponse.data.anime
                                        Anime(
                                            id = id,
                                            title = detail.info.name,
                                            poster = detail.info.poster,
                                            type = detail.info.stats?.type ?: "TV",
                                            duration = detail.info.stats?.duration ?: "",
                                            subEpisodes = detail.info.stats?.episodes?.sub ?: 0,
                                            dubEpisodes = detail.info.stats?.episodes?.dub ?: 0,
                                            rate = detail.info.stats?.rating ?: "",
                                            isBackup = false
                                        )
                                    } else null
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        }
                        val enrichedAnimes = fetchJobs.awaitAll().filterNotNull()
                        finalAnimes = enrichedAnimes + finalAnimes
                    }
                }
            }

            // If we have AniList fallback (as a last resort when nothing is found and it failed)
            if (finalAnimes.isEmpty() && primarySearchFailed) {
                // Try AniList search as backup search
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

                    val variables = mapOf("search" to trimmedQuery, "page" to page, "perPage" to 15)
                    val payload = mapOf("query" to queryStr, "variables" to variables)
                    val jsonPayload = gson.toJson(payload)

                    val jsonString = withContext(Dispatchers.IO) {
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
                        } else null
                    }

                    if (jsonString != null) {
                        val parsed: ALSearchResponse = gson.fromJson(jsonString, ALSearchResponse::class.java)
                        val rawList: List<Anime> = parsed.data?.searchPage?.media?.mapNotNull { item ->
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
                                rate = if (item.averageScore != null) String.format(java.util.Locale.US, "%.2f", item.averageScore / 10.0) else "",
                                isBackup = true
                            )
                        } ?: emptyList()
                        if (rawList.isNotEmpty()) {
                            finalAnimes = resolveBackupAnimeList(rawList)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }

            emit(Result.Success(finalAnimes))

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emit(Result.Error(e.message ?: "Search failed"))
        }
    }.flowOn(Dispatchers.IO)

    override fun searchHiAnime(query: String): Flow<Result<List<Anime>>> = search(query, 1)

    override fun getSuggestions(query: String): Flow<Result<List<Anime>>> = flow {
        emit(Result.Loading)
        try {
            val response = apiService.getSuggestions(query)
            if (response.success && !response.data.suggestions.isNullOrEmpty()) {
                val animeList = response.data.suggestions.map { item ->
                    Anime(
                        id = item.id,
                        title = item.name,
                        poster = item.poster,
                        type = item.moreInfo?.firstOrNull() ?: "TV",
                        duration = item.moreInfo?.getOrNull(2) ?: "",
                        subEpisodes = 0,
                        dubEpisodes = 0,
                        rate = "",
                        isBackup = false
                    )
                }
                emit(Result.Success(animeList))
            } else {
                search(query, 1).collect { emit(it) }
            }
        } catch (e: Exception) {
            search(query, 1).collect { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

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
    }.flowOn(Dispatchers.IO)

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
    }.flowOn(Dispatchers.IO)

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

        val cacheKey = "schedule_$targetDate"
        val cachedEntity = cacheDao.getCache(cacheKey)
        val currentTime = System.currentTimeMillis()
        var emittedCache = false

        val SCHEDULE_CACHE_LIFETIME = 6 * 60 * 60 * 1000L // 6 hours

        if (cachedEntity != null) {
            try {
                val listType = object : com.google.gson.reflect.TypeToken<List<ScheduleItem>>() {}.type
                val cachedList = gson.fromJson<List<ScheduleItem>>(cachedEntity.jsonContent, listType)
                if (!cachedList.isNullOrEmpty()) {
                    emit(Result.Success(cachedList))
                    emittedCache = true
                }
            } catch (e: Exception) {
                // Ignore and proceed
            }
        }

        val cacheExpired = cachedEntity == null || (currentTime - cachedEntity.timestamp > SCHEDULE_CACHE_LIFETIME)

        if (cacheExpired || !emittedCache) {
            var fetchedList: List<ScheduleItem>? = null

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
                val schedulesList = aniResponse.data?.schedulePage?.airingSchedules?.map { schedule ->
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
                    fetchedList = schedulesList
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                // Fall back to subsequent layers if AniList query fails
            }

            // 2. Fetch from remote proxy (preferred since it has correct active schedule episode numbers!)
            if (fetchedList == null) {
                val realDate = getRealWorldDateStr(date)
                try {
                    val response = apiService.getSchedules(realDate)
                    if (response.success) {
                        val list = response.data.scheduledAnimes?.map { it.toDomain() }?.distinctBy { it.id } ?: emptyList()
                        if (list.isNotEmpty()) {
                            fetchedList = list
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    // Fall back
                }
            }

            // 3. Fallback to Jikan API directly
            if (fetchedList == null) {
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
                        fetchedList = schedulesList
                    }
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                }
            }

            if (!fetchedList.isNullOrEmpty()) {
                // Save to Room cache
                cacheDao.insertCache(
                    CacheEntity(
                        cacheKey = cacheKey,
                        jsonContent = gson.toJson(fetchedList),
                        timestamp = currentTime
                    )
                )
                emit(Result.Success(fetchedList))
            } else {
                if (!emittedCache) {
                    emit(Result.Error("Schedule load failed"))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

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
    }.flowOn(Dispatchers.IO)

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
                    var isHls = source.type.equals("hls", ignoreCase = true) || videoUrl.contains(".m3u8")
                    var finalSubtitles = emptyList<SubtitleTrack>()
                    var introStart = data.intro?.let { it.start * 1000L } ?: 0L
                    var introEnd = data.intro?.let { it.end * 1000L } ?: 0L
                    var outroStart = data.outro?.let { it.start * 1000L } ?: 0L
                    var outroEnd = data.outro?.let { it.end * 1000L } ?: 0L

                    // --- DIAGNOSTIC: Log raw API response ---
                    DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] API response: videoUrl=$videoUrl | type=${source.type} | isHls=$isHls")

                    // If API returned an HLS URL directly but it's not proxied, wrap it through our proxy
                    // (Cloudflare WAF blocks direct mobile requests to stream CDNs)
                    if (isHls && !videoUrl.contains(STREAM_PROXY_BASE)) {
                        val cleanHlsUrl = videoUrl.removePrefix("https://").removePrefix("http://")
                        videoUrl = "$STREAM_PROXY_BASE/$cleanHlsUrl"
                        DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] HLS URL proxied: $videoUrl")
                    }

                    // Try to programmatically extract direct stream link from Zoro iframe to bypass background WebView sniffing
                    // Broadened guard: attempt extraction for ANY non-HLS URL (not just specific domains)
                    if (!isHls) {
                        DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Entering programmatic extraction for non-HLS URL")
                        try {
                            val cleanUrl = videoUrl.removePrefix("https://").removePrefix("http://")
                            val proxyUrl = "$STREAM_PROXY_BASE/$cleanUrl"
                            DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 1: Fetching iframe HTML via proxy: $proxyUrl")
                            val iframeRequest = okhttp3.Request.Builder()
                                .url(proxyUrl)
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                .build()
                            val iframeResponse = okHttpClient.newCall(iframeRequest).execute()
                            DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 1 response: ${iframeResponse.code}")
                            if (iframeResponse.isSuccessful) {
                                val html = iframeResponse.body?.string() ?: ""
                                DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 1 HTML length: ${html.length}")
                                val megaplayRegex = Regex("""src=["'](https://megaplay\.buzz/stream/s-[1-9]/\d+/(?:sub|dub))["']""")
                                val megaplayMatch = megaplayRegex.find(html)
                                val megaplayUrl = megaplayMatch?.groupValues?.get(1)
                                DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 2: Megaplay URL from regex: ${megaplayUrl ?: "NOT FOUND"}")
                                if (megaplayUrl != null) {
                                    val cleanMegaplayUrl = megaplayUrl.removePrefix("https://").removePrefix("http://")
                                    val megaplayProxyUrl = "$STREAM_PROXY_BASE/$cleanMegaplayUrl"
                                    DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 2: Fetching megaplay page: $megaplayProxyUrl")
                                    val megaplayRequest = okhttp3.Request.Builder()
                                        .url(megaplayProxyUrl)
                                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                        .build()
                                    val megaplayResponse = okHttpClient.newCall(megaplayRequest).execute()
                                    DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 2 response: ${megaplayResponse.code}")
                                    if (megaplayResponse.isSuccessful) {
                                        val megaplayHtml = megaplayResponse.body?.string() ?: ""
                                        val dataIdRegex = Regex("""data-id=["'](\d+)["']""")
                                        val dataIdMatch = dataIdRegex.find(megaplayHtml)
                                        val dataId = dataIdMatch?.groupValues?.get(1)
                                        DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 3: data-id=${dataId ?: "NOT FOUND"}")
                                        if (dataId != null) {
                                            var sourcesJson: MegaplaySourcesResponse? = null
                                            var resolved = false
                                            try {
                                                val getSourcesUrl = "$STREAM_PROXY_BASE/megaplay.buzz/stream/getSources?id=$dataId"
                                                DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 4a: Trying getSources: $getSourcesUrl")
                                                val sourcesRequest = okhttp3.Request.Builder()
                                                    .url(getSourcesUrl)
                                                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                                    .header("X-Requested-With", "XMLHttpRequest")
                                                    .build()
                                                val sourcesResponse = okHttpClient.newCall(sourcesRequest).execute()
                                                DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 4a response: ${sourcesResponse.code}")
                                                if (sourcesResponse.isSuccessful) {
                                                    val body = sourcesResponse.body?.string()
                                                    if (body != null) {
                                                        DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 4a body length: ${body.length}")
                                                        val parsed = gson.fromJson(body, MegaplaySourcesResponse::class.java)
                                                        if (parsed?.sources?.file != null) {
                                                            sourcesJson = parsed
                                                            resolved = true
                                                            DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 4a SUCCESS: file=${parsed.sources.file}")
                                                        } else {
                                                            DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 4a: sources.file is null in response")
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 4a exception: ${e.message}")
                                            }
                                            if (!resolved) {
                                                val getSourcesNewUrl = "$STREAM_PROXY_BASE/megaplay.buzz/stream/getSourcesNew?id=$dataId"
                                                DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 4b: Trying getSourcesNew: $getSourcesNewUrl")
                                                val sourcesNewRequest = okhttp3.Request.Builder()
                                                    .url(getSourcesNewUrl)
                                                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                                    .header("X-Requested-With", "XMLHttpRequest")
                                                    .build()
                                                val sourcesNewResponse = okHttpClient.newCall(sourcesNewRequest).execute()
                                                DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 4b response: ${sourcesNewResponse.code}")
                                                if (sourcesNewResponse.isSuccessful) {
                                                    val body = sourcesNewResponse.body?.string()
                                                    if (body != null) {
                                                        DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 4b body length: ${body.length}")
                                                        sourcesJson = gson.fromJson(body, MegaplaySourcesResponse::class.java)
                                                        if (sourcesJson?.sources?.file != null) {
                                                            DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 4b SUCCESS: file=${sourcesJson?.sources?.file}")
                                                        } else {
                                                            DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 4b: sources.file is null in response")
                                                        }
                                                    }
                                                }
                                            }
                                            val fileUrl = sourcesJson?.sources?.file
                                            if (fileUrl != null) {
                                                val cleanFileUrl = fileUrl.removePrefix("https://").removePrefix("http://")
                                                videoUrl = "$STREAM_PROXY_BASE/$cleanFileUrl"
                                                isHls = true
                                                finalSubtitles = sourcesJson?.tracks?.mapNotNull { track ->
                                                    if (track.file != null) {
                                                        val cleanTrackUrl = track.file.removePrefix("https://").removePrefix("http://")
                                                        val proxiedTrackUrl = "$STREAM_PROXY_BASE/$cleanTrackUrl"
                                                        SubtitleTrack(
                                                            url = proxiedTrackUrl,
                                                            label = track.label ?: "English",
                                                            isDefault = track.label?.equals("English", ignoreCase = true) == true
                                                        )
                                                    } else null
                                                } ?: emptyList<SubtitleTrack>()
                                                sourcesJson?.intro?.start?.let { introStart = (it * 1000).toLong() }
                                                sourcesJson?.intro?.end?.let { introEnd = (it * 1000).toLong() }
                                                sourcesJson?.outro?.start?.let { outroStart = (it * 1000).toLong() }
                                                sourcesJson?.outro?.end?.let { outroEnd = (it * 1000).toLong() }
                                                DebugLogManager.log("ANIPLEX_PLAYER", "Successfully extracted Zoro stream programmatically: $videoUrl")
                                            }
                                        }
                                    } else {
                                        DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 2: megaplay response FAILED")
                                    }
                                } else {
                                    DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 2: Megaplay regex found no match in iframe HTML")
                                }
                            } else {
                                DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Step 1: iframe proxy returned HTTP ${iframeResponse.code}")
                            }
                        } catch (e: Exception) {
                            DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Programmatic extraction exception: ${e.javaClass.simpleName}: ${e.message}", e)
                        }
                        DebugLogManager.log("ANIPLEX_PLAYER", "[Zoro Diag] Final state after extraction attempt: isHls=$isHls | videoUrl=$videoUrl")
                    }

                    if (useUncensored && isOptionA) {
                        if (videoUrl.contains("/sub")) {
                            videoUrl = videoUrl.replace("/sub", "/sub?version=uncut")
                        } else if (videoUrl.contains("/dub")) {
                            videoUrl = videoUrl.replace("/dub", "/dub?version=uncut")
                        } else if (!videoUrl.contains("embed") && !videoUrl.contains("megaplay")) {
                            videoUrl = if (videoUrl.contains("?")) "$videoUrl&version=uncut" else "$videoUrl?version=uncut"
                        }
                    }

                    if (finalSubtitles.isEmpty()) {
                        val originalSubtitles = data.tracks?.filter { it.kind == "captions" || it.kind == "subtitles" }?.map {
                            // Proxy subtitle URLs through STREAM_PROXY_BASE to bypass CDN restrictions
                            val subtitleUrl = if (!it.file.contains(STREAM_PROXY_BASE)) {
                                val cleanSubUrl = it.file.removePrefix("https://").removePrefix("http://")
                                "$STREAM_PROXY_BASE/$cleanSubUrl"
                            } else {
                                it.file
                            }
                            SubtitleTrack(
                                url = subtitleUrl,
                                label = it.label ?: "English",
                                isDefault = it.label?.equals("english", ignoreCase = true) == true
                            )
                        } ?: emptyList()
                        DebugLogManager.log("ANIPLEX_SUBS", "[Zoro Diag] Fallback subtitles from API tracks: ${originalSubtitles.size} tracks")
                        finalSubtitles = if (useUncensored && isOptionA) {
                            originalSubtitles + SubtitleTrack(
                                url = "https://example.com/uncensored_indicator.vtt",
                                label = "Uncensored Mode ACTIVE 🌟",
                                isDefault = false
                            )
                        } else {
                            originalSubtitles
                        }
                    }

                    val stream = EpisodeStream(
                        videoUrl = videoUrl,
                        isHls = isHls,
                        subtitles = finalSubtitles,
                        introStart = introStart,
                        introEnd = introEnd,
                        outroStart = outroStart,
                        outroEnd = outroEnd
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
    }.flowOn(Dispatchers.IO)

    private val STREAM_PROXY_BASE = "https://anistream-proxy.f1886391.workers.dev"

    /**
     * Fetch fallback stream from the Netlify serverless function.
     * Mirrors the website's fetchBackupFromApi() in web/src/services/api.js
     */
    override fun getFallbackStream(
        malId: String?,
        episodeNumber: Int,
        title: String?,
        provider: String
    ): Flow<Result<EpisodeStream>> = flow {
        emit(Result.Loading)
        try {
            val response = fallbackApiService.getFallbackStream(malId, episodeNumber, title, provider)
            if (response.success && !response.sources.isNullOrEmpty()) {
                // Find the HLS source
                val hlsSource = response.sources.find {
                    it.type.equals("hls", ignoreCase = true) || it.url.contains(".m3u8")
                } ?: response.sources.first()

                // Proxy the HLS URL through our Cloudflare worker (same as website)
                val cleanHlsUrl = hlsSource.url.removePrefix("https://")
                val proxiedHlsUrl = "$STREAM_PROXY_BASE/$cleanHlsUrl"

                // Map subtitles
                val subtitles = response.subtitles?.mapNotNull { sub ->
                    if (sub.url != null) {
                        val cleanTrackUrl = sub.url.removePrefix("https://").removePrefix("http://")
                        val proxiedTrackUrl = "$STREAM_PROXY_BASE/$cleanTrackUrl"
                        SubtitleTrack(
                            url = proxiedTrackUrl,
                            label = sub.lang ?: "English",
                            isDefault = sub.lang.equals("English", ignoreCase = true)
                        )
                    } else null
                } ?: emptyList()

                val stream = EpisodeStream(
                    videoUrl = proxiedHlsUrl,
                    isHls = true,
                    subtitles = subtitles,
                    introStart = response.intro?.start?.let { (it * 1000).toLong() } ?: 0L,
                    introEnd = response.intro?.end?.let { (it * 1000).toLong() } ?: 0L,
                    outroStart = response.outro?.start?.let { (it * 1000).toLong() } ?: 0L,
                    outroEnd = response.outro?.end?.let { (it * 1000).toLong() } ?: 0L
                )
                emit(Result.Success(stream))
            } else {
                emit(Result.Error(response.error ?: "No sources returned from fallback API"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.localizedMessage ?: "Fallback stream fetch failed"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Extract MegaPlay's direct .m3u8 stream via their MAL-based URL.
     * Mirrors the website's getMegaplayDirectStream() in web/src/services/api.js
     */
    override fun getMegaplayDirectStream(
        malId: String,
        episodeNumber: Int,
        audioCategory: String
    ): Flow<Result<EpisodeStream>> = flow {
        emit(Result.Loading)
        try {
            // 1. Fetch MegaPlay's MAL-based player page
            val megaplayPageUrl = "$STREAM_PROXY_BASE/megaplay.buzz/stream/mal/$malId/$episodeNumber/$audioCategory"
            val pageRequest = okhttp3.Request.Builder()
                .url(megaplayPageUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val pageResponse = okHttpClient.newCall(pageRequest).execute()
            if (!pageResponse.isSuccessful) throw IOException("MegaPlay page fetch failed: ${pageResponse.code}")
            val pageHtml = pageResponse.body?.string() ?: throw IOException("MegaPlay page body is empty")

            // 2. Try to extract embedded player URL (s-1, s-2, etc.)
            val embedRegex = Regex("""src=["'](https://megaplay\.buzz/stream/s-[1-9]/\d+/(?:sub|dub))["']""")
            val embedMatch = embedRegex.find(pageHtml)
            var playerHtml = pageHtml

            if (embedMatch != null) {
                val innerUrl = embedMatch.groupValues[1].removePrefix("https://")
                val innerRequest = okhttp3.Request.Builder()
                    .url("$STREAM_PROXY_BASE/$innerUrl")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                val innerResponse = okHttpClient.newCall(innerRequest).execute()
                if (innerResponse.isSuccessful) {
                    playerHtml = innerResponse.body?.string() ?: playerHtml
                }
            }

            // 3. Extract data-id from the player HTML
            val dataIdRegex = Regex("""data-id=["'](\d+)["']""")
            val dataIdMatch = dataIdRegex.find(playerHtml)
            val dataId = dataIdMatch?.groupValues?.get(1)
                ?: throw IOException("Could not extract data-id from MegaPlay player")

            // 4. Try getSources first, fall back to getSourcesNew
            var sourcesJson: MegaplaySourcesResponse? = null
            var resolved = false

            try {
                val sourcesRequest = okhttp3.Request.Builder()
                    .url("$STREAM_PROXY_BASE/megaplay.buzz/stream/getSources?id=$dataId")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build()
                val sourcesResponse = okHttpClient.newCall(sourcesRequest).execute()
                if (sourcesResponse.isSuccessful) {
                    val body = sourcesResponse.body?.string()
                    if (body != null) {
                        val parsed = gson.fromJson(body, MegaplaySourcesResponse::class.java)
                        if (parsed?.sources?.file != null) {
                            sourcesJson = parsed
                            resolved = true
                        }
                    }
                }
            } catch (_: Exception) {
                // getSources failed, try getSourcesNew
            }

            if (!resolved) {
                val sourcesNewRequest = okhttp3.Request.Builder()
                    .url("$STREAM_PROXY_BASE/megaplay.buzz/stream/getSourcesNew?id=$dataId")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build()
                val sourcesNewResponse = okHttpClient.newCall(sourcesNewRequest).execute()
                if (!sourcesNewResponse.isSuccessful) throw IOException("MegaPlay getSourcesNew failed: ${sourcesNewResponse.code}")
                val body = sourcesNewResponse.body?.string()
                    ?: throw IOException("MegaPlay getSourcesNew body is empty")
                sourcesJson = gson.fromJson(body, MegaplaySourcesResponse::class.java)
            }

            val fileUrl = sourcesJson?.sources?.file
                ?: throw IOException("MegaPlay HLS master.m3u8 not found")

            // 5. Proxy the HLS URL and subtitle tracks
            val cleanFileUrl = fileUrl.removePrefix("https://")
            val proxiedFileUrl = "$STREAM_PROXY_BASE/$cleanFileUrl"

            val subtitles = sourcesJson?.tracks?.mapNotNull { track ->
                if (track.file != null) {
                    val cleanTrackUrl = track.file.removePrefix("https://").removePrefix("http://")
                    val proxiedTrackUrl = "$STREAM_PROXY_BASE/$cleanTrackUrl"
                    SubtitleTrack(
                        url = proxiedTrackUrl,
                        label = track.label ?: "English",
                        isDefault = track.label?.equals("English", ignoreCase = true) == true
                    )
                } else null
            } ?: emptyList()

            val stream = EpisodeStream(
                videoUrl = proxiedFileUrl,
                isHls = true,
                subtitles = subtitles,
                introStart = sourcesJson?.intro?.start?.let { (it * 1000).toLong() } ?: 0L,
                introEnd = sourcesJson?.intro?.end?.let { (it * 1000).toLong() } ?: 0L,
                outroStart = sourcesJson?.outro?.start?.let { (it * 1000).toLong() } ?: 0L,
                outroEnd = sourcesJson?.outro?.end?.let { (it * 1000).toLong() } ?: 0L
            )
            emit(Result.Success(stream))
        } catch (e: Exception) {
            emit(Result.Error(e.localizedMessage ?: "MegaPlay direct stream extraction failed"))
        }
    }.flowOn(Dispatchers.IO)

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
    }.flowOn(Dispatchers.IO)

    private suspend fun filterReleasedSeasons(seasons: List<Season>): List<Season> {
        if (seasons.isEmpty()) return emptyList()
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            kotlinx.coroutines.coroutineScope {
                seasons.map { season ->
                    async {
                        val isResolvable = try {
                            val overrides = setOf(
                                "39535", "45576", "51179", "55818", "55888", "59193", "58752", "50360",
                                "38000", "40456", "49926", "47778", "51019", "55701", "59192", "62546", "47398", "48861"
                            )
                            if (season.malId in overrides) {
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
            var seasonsList: List<Season> = emptyList()

            // 1. Try Shikimori + AniList bulk lookup first
            try {
                val shikimoriRequest = okhttp3.Request.Builder()
                    .url("https://shikimori.one/api/animes/$malId/franchise")
                    .build()
                val shikimoriJson = withContext(Dispatchers.IO) {
                    okHttpClient.newCall(shikimoriRequest).execute().use { response ->
                        if (response.isSuccessful) response.body?.string() else null
                    }
                }
                if (!shikimoriJson.isNullOrEmpty()) {
                    val parsedShikimori = gson.fromJson(shikimoriJson, ShikimoriFranchiseResponse::class.java)
                    val allowedKinds = listOf(
                        "tv сериал", "фильм", "спецвыпуск", "tv спецвыпуск", "ova", "ona", 
                        "tv", "movie", "special", "ova", "ona", "tv_special"
                    )
                    val franchiseIds = parsedShikimori.nodes
                        ?.filter { node ->
                            val kind = (node.kind ?: "").lowercase()
                            node.id != null && allowedKinds.contains(kind)
                        }
                        ?.map { it.id!!.toInt() }
                        ?: emptyList()

                    if (franchiseIds.isNotEmpty()) {
                        val mediaQuery = """
                            query (${'$'}ids: [Int]) {
                              Page(page: 1, perPage: 50) {
                                media(idMal_in: ${'$'}ids, type: ANIME) {
                                  idMal
                                  title {
                                    english
                                    romaji
                                    userPreferred
                                  }
                                  coverImage {
                                    large
                                  }
                                  format
                                  startDate {
                                    year
                                    month
                                    day
                                  }
                                  episodes
                                  relations {
                                    edges {
                                      relationType
                                      node {
                                        idMal
                                      }
                                    }
                                  }
                                }
                              }
                            }
                        """.trimIndent()

                        val variables = mapOf("ids" to franchiseIds)
                        val payload = mapOf("query" to mediaQuery, "variables" to variables)
                        val jsonPayload = gson.toJson(payload)

                        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                        val alRequest = okhttp3.Request.Builder()
                            .url("https://graphql.anilist.co")
                            .post(requestBody)
                            .build()

                        val alJson = withContext(Dispatchers.IO) {
                            okHttpClient.newCall(alRequest).execute().use { response ->
                                if (response.isSuccessful) response.body?.string() else null
                            }
                        }

                        if (!alJson.isNullOrEmpty()) {
                            val alResponse = gson.fromJson(alJson, ALSearchResponse::class.java)
                            val mediaList = alResponse.data?.searchPage?.media ?: emptyList()
                            
                            val mediaMap = mediaList.associateBy { it.idMal }
                            val startNodeMalId = malId.toIntOrNull()
                            
                            if (startNodeMalId != null && mediaMap.containsKey(startNodeMalId)) {
                                var rootId = startNodeMalId
                                val visitedPrequels = mutableSetOf<Int>()
                                while (true) {
                                    val node = mediaMap[rootId] ?: break
                                    val prequelEdge = node.relations?.edges?.find { it.relationType == "PREQUEL" }
                                    val pId = prequelEdge?.node?.idMal
                                    if (pId != null && pId != rootId && pId in mediaMap && pId !in visitedPrequels) {
                                        visitedPrequels.add(pId)
                                        rootId = pId
                                    } else {
                                        break
                                    }
                                }

                                val mainTimeline = mutableListOf<ALSearchMedia>()
                                var currentId: Int? = rootId
                                val visitedSequels = mutableSetOf<Int>()

                                while (currentId != null) {
                                    val node = mediaMap[currentId] ?: break
                                    if (currentId !in visitedSequels) {
                                        mainTimeline.add(node)
                                        visitedSequels.add(currentId)
                                    }
                                    val sequelEdge = node.relations?.edges?.find { it.relationType == "SEQUEL" }
                                    val nextId = sequelEdge?.node?.idMal
                                    if (nextId != null && nextId != currentId && nextId !in visitedSequels) {
                                        currentId = nextId
                                    } else {
                                        break
                                    }
                                }

                                val sideStories = mutableListOf<ALSearchMedia>()
                                // Omit ALTERNATIVE to hide Burn the Witch and similar spin-offs
                                val sideStoryTypes = setOf("SIDE_STORY", "SUMMARY")
                                for (mainNode in mainTimeline) {
                                    mainNode.relations?.edges?.forEach { edge ->
                                        if (edge.relationType in sideStoryTypes) {
                                            val sId = edge.node?.idMal
                                            if (sId != null && sId !in visitedSequels) {
                                                val sNode = mediaMap[sId]
                                                if (sNode != null && sNode !in sideStories) {
                                                    sideStories.add(sNode)
                                                }
                                            }
                                        }
                                    }
                                }

                                val sortedSideStories = sideStories.sortedWith(compareBy { media ->
                                    val year = media.startDate?.year ?: 9999
                                    val month = media.startDate?.month ?: 12
                                    val day = media.startDate?.day ?: 31
                                    year * 10000L + month * 100L + day
                                })

                                fun resolveLocalIdSync(mId: String): String? {
                                    val override = when (mId) {
                                        "39535" -> "5694"
                                        "45576" -> "6675"
                                        "51179" -> "6537"
                                        "55818" -> "6537"
                                        "55888" -> "6159"
                                        "59193" -> "8800"
                                        "58752" -> "8800"
                                        "50360" -> "7045"
                                        "40456" -> "5870"
                                        "49926" -> "6871"
                                        "47778" -> "7024"
                                        "51019" -> "6905"
                                        "55701" -> "6054"
                                        "59192" -> "8138"
                                        "62546" -> "8847"
                                        "47398" -> "7019"
                                        "48861" -> "7032"
                                        "38524" -> "1585"
                                        "40052" -> "5687"
                                        "48583" -> "6694"
                                        "51535" -> "6476"
                                        "55639" -> "6476"
                                        "38000" -> "1551"
                                        else -> null
                                    }
                                    if (override != null) return override
                                    val mapping = LOCAL_ACCURATE_MAPPINGS.find { it.malId.toString() == mId }
                                    return mapping?.id
                                }

                                var tvIndex = 1
                                val finalSeasonsList = mutableListOf<Season>()
                                val seenLocalIds = mutableSetOf<String>()

                                for (media in mainTimeline) {
                                    val titleLower = (media.title?.english ?: media.title?.userPreferred ?: media.title?.romaji ?: "").lowercase()
                                    val isMain = media.format == "TV" || 
                                            media.format == "TV_SHORT" || 
                                            (media.format == "ONA" && (media.episodes ?: 0) > 2) ||
                                            (media.format == "SPECIAL" && (
                                                titleLower.contains("final season") ||
                                                titleLower.contains("final chapters") ||
                                                titleLower.contains("kanketsu-hen") ||
                                                titleLower.contains("kanketsuhen")
                                            ))
                                    val malIdStr = (media.idMal ?: 0).toString()
                                    val localId = resolveLocalIdSync(malIdStr)

                                    if (localId != null) {
                                        if (seenLocalIds.contains(localId)) {
                                            continue
                                        }
                                        seenLocalIds.add(localId)
                                    }

                                    val sNum = if (isMain) tvIndex++ else 0
                                    finalSeasonsList.add(
                                        Season(
                                            malId = malIdStr,
                                            resolvedId = localId,
                                            title = media.title?.english ?: media.title?.userPreferred ?: media.title?.romaji ?: "Unknown",
                                            poster = media.coverImage?.large ?: "",
                                            episodes = media.episodes ?: 0,
                                            seasonNumber = sNum,
                                            format = media.format ?: "TV",
                                            relationType = if (isMain) "MAIN" else "SIDE_STORY"
                                        )
                                    )
                                }

                                for (media in sortedSideStories) {
                                    val malIdStr = (media.idMal ?: 0).toString()
                                    val localId = resolveLocalIdSync(malIdStr)

                                    if (localId != null) {
                                        if (seenLocalIds.contains(localId)) {
                                            continue
                                        }
                                        seenLocalIds.add(localId)
                                    }

                                    finalSeasonsList.add(
                                        Season(
                                            malId = malIdStr,
                                            resolvedId = localId,
                                            title = media.title?.english ?: media.title?.userPreferred ?: media.title?.romaji ?: "Unknown",
                                            poster = media.coverImage?.large ?: "",
                                            episodes = media.episodes ?: 0,
                                            seasonNumber = 0,
                                            format = media.format ?: "MOVIE",
                                            relationType = "SIDE_STORY"
                                        )
                                    )
                                }
                                seasonsList = finalSeasonsList
                            } else {
                                seasonsList = emptyList()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Non-blocking
            }

            // 2. Fallback to original backend SeasonsResponse
            if (seasonsList.isEmpty()) {
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
                    seasonsList = response.data.seasons?.map { it.toDomain() } ?: emptyList()
                } else {
                    val errorMsg = lastException?.localizedMessage ?: "Failed to fetch seasons"
                    emit(Result.Error(errorMsg))
                    return@flow
                }
            }

            if (seasonsList.isNotEmpty()) {
                // Save cache for the requested MAL ID and all mapped seasons
                val seasonsDtoList = seasonsList.map { season ->
                    com.aniplex.app.data.remote.dto.SeasonDto(
                        malId = season.malId,
                        title = season.title,
                        poster = season.poster,
                        episodes = season.episodes,
                        seasonNumber = season.seasonNumber
                    )
                }

                seasonsList.forEach { season ->
                    try {
                        val linkedResponse = SeasonsResponse(
                            success = true,
                            data = SeasonsDataDto(
                                seasons = seasonsDtoList,
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
                val filteredSeasons = filterReleasedSeasons(seasonsList)
                emit(Result.Success(filteredSeasons))
            } else {
                emit(Result.Error("No seasons found"))
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

    override fun getSeasonalAnime(year: Int?, season: String?, page: Int): Flow<Result<SeasonalData>> = flow {
        emit(Result.Loading)
        try {
            val urlString = if (year == null || season == null) {
                "https://api.jikan.moe/v4/seasons/now?page=$page&limit=24"
            } else {
                "https://api.jikan.moe/v4/seasons/$year/${season.lowercase()}?page=$page&limit=24"
            }
            val request = okhttp3.Request.Builder().url(urlString).build()
            val resultJson = withContext(Dispatchers.IO) {
                val okResponse = okHttpClient.newCall(request).execute()
                if (okResponse.isSuccessful) okResponse.body?.string() else null
            }

            if (!resultJson.isNullOrEmpty()) {
                val parsed = gson.fromJson(resultJson, JikanSeasonalResponse::class.java)
                val rawList = parsed.data?.mapNotNull { item ->
                    if (item.mal_id == null) return@mapNotNull null
                    val genreString = item.genres?.mapNotNull { it.name }?.joinToString(" • ") ?: ""
                    Anime(
                        id = "mal-${item.mal_id}",
                        title = item.title_english ?: item.title ?: "Unknown",
                        poster = item.images?.webp?.large_image_url ?: item.images?.webp?.image_url ?: item.images?.jpg?.image_url ?: "",
                        type = item.type ?: "TV",
                        duration = item.duration ?: "",
                        subEpisodes = item.episodes ?: 0,
                        dubEpisodes = 0,
                        rate = item.score?.toString() ?: "",
                        isBackup = true,
                        description = item.synopsis ?: "",
                        genres = genreString
                    )
                } ?: emptyList()

                val totalPages = parsed.pagination?.last_visible_page ?: 1
                val hasNextPage = parsed.pagination?.has_next_page ?: false

                emit(Result.Success(SeasonalData(rawList, totalPages, hasNextPage)))
            } else {
                emit(Result.Error("Failed to fetch seasonal anime data"))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.localizedMessage ?: "Failed to fetch seasonal anime"))
        }
    }.flowOn(Dispatchers.IO)

    override fun resolveMAL(malId: String): Flow<Result<String>> = flow {
        if (malId.isBlank()) {
            emit(Result.Error("Blank MAL ID"))
            return@flow
        }

        // Handle known duplicate MAL ID mappings
        val overrideId = when (malId) {
            // Mushoku Tensei
            "39535" -> "5694"   // Season 1 Part 1
            "45576" -> "6675"   // Season 1 Part 2
            "51179" -> "6537"   // Season 2 Part 1
            "55818" -> "6537"   // Season 2 Episode 0 "Guardian Fitz" -> resolves to S2 Part 1
            "55888" -> "6159"   // Season 2 Part 2
            "59193" -> "8800"   // Season 3 Part 1
            "58752" -> "8800"   // Season 3 Alternative
            "50360" -> "7045"   // Eris Special

            // Demon Slayer
            "38000" -> "1551"   // Demon Slayer: Kimetsu no Yaiba S1
            "40456" -> "5870"   // Mugen Train Movie
            "49926" -> "6871"   // Mugen Train TV Arc
            "47778" -> "7024"   // Entertainment District Arc
            "51019" -> "6905"   // Swordsmith Village Arc
            "55701" -> "6054"   // Hashira Training Arc
            "59192" -> "8138"   // Infinity Castle Movie 1
            "62546" -> "8847"   // Infinity Castle Movie 2
            "47398" -> "7019"   // Valentine School
            "48861" -> "7032"   // Utage Special

            // Attack on Titan
            "38524" -> "1585"   // Season 3 Part 2 -> Season 3 (merged)
            "40052" -> "5687"   // Final Season Part 1
            "48583" -> "6694"   // Final Season Part 2
            "51535" -> "6476"   // Final Season Part 3
            "55639" -> "6476"   // Final Season Part 4 -> Part 3 (merged)
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

    override fun getTmdbStoryArcs(
        malId: String,
        title: String,
        episodes: List<Episode>
    ): Flow<Result<List<StoryArc>>> = flow {
        emit(Result.Loading)
        try {
            val tmdbId = resolveTmdId(malId, title)
            if (tmdbId == null) {
                emit(Result.Success(emptyList()))
                return@flow
            }
            val seasons = fetchTmdSeasons(tmdbId)
            val arcs = buildArcsFromSeasons(seasons, episodes)
            emit(Result.Success(arcs))
        } catch (e: Exception) {
            emit(Result.Error(e.localizedMessage ?: "Failed to build story arcs"))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun resolveTmdId(malId: String, title: String): String? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val tmdbApiKey = "7332f575cc32b96fb7e03ccb66ea80b4"
        val staticMappings = mapOf(
            "21" to "81797",     // One Piece
            "20" to "78857",     // Naruto
            "1735" to "79824",   // Naruto Shippuden
            "269" to "74796",    // Bleach
            "11061" to "252322", // Hunter x Hunter (2011)
            "6702" to "114801",  // Fairy Tail
            "34572" to "331753", // Black Clover
            "813" to "81472",    // Dragon Ball Z
            "30694" to "295068", // Dragon Ball Super
            "34566" to "321285"  // Boruto: Naruto Next Generations
        )

        var tvdbId = staticMappings[malId]
        
        if (tvdbId == null && malId.isNotBlank()) {
            try {
                // Step 1: Query Kitsu mappings for the MAL ID to find Kitsu's internal ID
                val kitsuUrl = "https://kitsu.io/api/edge/mappings?filter[externalSite]=myanimelist/anime&filter[externalId]=$malId&include=item"
                val request = okhttp3.Request.Builder().url(kitsuUrl).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val root = gson.fromJson(body, com.google.gson.JsonObject::class.java)
                        val included = root.getAsJsonArray("included")
                        if (included != null && included.size() > 0) {
                            val firstItem = included.get(0).asJsonObject
                            val kitsuIdObj = firstItem.get("id")
                            if (kitsuIdObj != null && !kitsuIdObj.isJsonNull) {
                                val kitsuId = kitsuIdObj.asString
                                // Step 2: Query Kitsu mappings again with Kitsu's internal ID to find the TVDB ID
                                val mappingsUrl = "https://kitsu.io/api/edge/anime/$kitsuId/mappings"
                                val mappingsReq = okhttp3.Request.Builder().url(mappingsUrl).build()
                                okHttpClient.newCall(mappingsReq).execute().use { mappingsResp ->
                                    if (mappingsResp.isSuccessful) {
                                        val mappingsBody = mappingsResp.body?.string() ?: ""
                                        val mappingsRoot = gson.fromJson(mappingsBody, com.google.gson.JsonObject::class.java)
                                        val data = mappingsRoot.getAsJsonArray("data")
                                        if (data != null) {
                                            for (element in data) {
                                                val attributes = element.asJsonObject.getAsJsonObject("attributes")
                                                val extSite = attributes?.get("externalSite")?.asString ?: ""
                                                if (extSite == "thetvdb" || extSite == "thetvdb/series") {
                                                    val extIdObj = attributes.get("externalId")
                                                    if (extIdObj != null && !extIdObj.isJsonNull) {
                                                        tvdbId = extIdObj.asString
                                                    }
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore kitsu resolution exceptions and fallback to direct search
            }
        }

        if (!tvdbId.isNullOrBlank()) {
            val cleanTvdbId = if (tvdbId!!.contains("/")) tvdbId!!.split("/")[0] else tvdbId
            try {
                val findUrl = "https://api.themoviedb.org/3/find/$cleanTvdbId?api_key=$tmdbApiKey&external_source=tvdb_id"
                val request = okhttp3.Request.Builder().url(findUrl).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val root = gson.fromJson(body, com.google.gson.JsonObject::class.java)
                        val tvResults = root.getAsJsonArray("tv_results")
                        if (tvResults != null && tvResults.size() > 0) {
                            val tvIdObj = tvResults.get(0).asJsonObject.get("id")
                            if (tvIdObj != null && !tvIdObj.isJsonNull) {
                                return@withContext tvIdObj.asString
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore find exceptions
            }
        }

        // Fallback search by title
        if (title.isNotBlank()) {
            try {
                val searchUrl = "https://api.themoviedb.org/3/search/tv?api_key=$tmdbApiKey&query=${java.net.URLEncoder.encode(title, "UTF-8")}"
                val request = okhttp3.Request.Builder().url(searchUrl).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val root = gson.fromJson(body, com.google.gson.JsonObject::class.java)
                        val results = root.getAsJsonArray("results")
                        if (results != null && results.size() > 0) {
                            val tvIdObj = results.get(0).asJsonObject.get("id")
                            if (tvIdObj != null && !tvIdObj.isJsonNull) {
                                return@withContext tvIdObj.asString
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore search exceptions
            }
        }
        return@withContext null
    }

    private suspend fun fetchTmdSeasons(tmdbId: String): List<TmdbSeasonDto> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val tmdbApiKey = "7332f575cc32b96fb7e03ccb66ea80b4"
        val tvUrl = "https://api.themoviedb.org/3/tv/$tmdbId?api_key=$tmdbApiKey"
        val request = okhttp3.Request.Builder().url(tvUrl).build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = gson.fromJson(body, com.google.gson.JsonObject::class.java)
                    val seasonsArray = root.getAsJsonArray("seasons")
                    if (seasonsArray != null) {
                        val listType = object : com.google.gson.reflect.TypeToken<List<TmdbSeasonDto>>() {}.type
                        return@withContext gson.fromJson(seasonsArray, listType)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return@withContext emptyList<TmdbSeasonDto>()
    }

    private fun buildArcsFromSeasons(seasons: List<TmdbSeasonDto>, episodes: List<Episode>): List<StoryArc> {
        if (seasons.isEmpty() || episodes.isEmpty()) return emptyList()

        val validSeasons = seasons
            .filter { it.season_number > 0 }
            .sortedBy { it.season_number }

        var currentStart = 1
        val batches = ArrayList<StoryArc>()
        for ((index, season) in validSeasons.withIndex()) {
            val start = currentStart
            val end = currentStart + season.episode_count - 1
            currentStart = end + 1

            val batchEps = episodes.filter { it.number in start..end }
            batches.add(
                StoryArc(
                    index = index,
                    start = start,
                    end = end,
                    label = "${season.name} ($start-$end)",
                    episodes = batchEps,
                    episodesCount = season.episode_count
                )
            )
        }

        if (batches.isNotEmpty()) {
            val maxEpNum = episodes.last().number
            val lastBatch = batches.last()
            if (maxEpNum > lastBatch.end) {
                val originalName = validSeasons.last().name
                val adjustedEps = episodes.filter { it.number >= lastBatch.start }
                batches[batches.lastIndex] = StoryArc(
                    index = lastBatch.index,
                    start = lastBatch.start,
                    end = maxEpNum,
                    label = "$originalName (${lastBatch.start}-$maxEpNum)",
                    episodes = adjustedEps,
                    episodesCount = lastBatch.episodesCount
                )
            }
        }

        val filteredBatches = batches.filter { it.episodes.isNotEmpty() }

        // Sanity checks:
        // 1. If it resulted in 0 or 1 batch, and we have many episodes (e.g. > 25), it's not a useful grouping.
        if (filteredBatches.size <= 1 && episodes.size > 25) {
            return emptyList()
        }

        // 2. If the total episodes covered in the batches is significantly less than the stream episodes (< 90%),
        //    it means TMDb data is incomplete.
        val totalCoveredEps = filteredBatches.sumOf { it.episodes.size }
        if (totalCoveredEps < episodes.size * 0.9 && episodes.size > 25) {
            return emptyList()
        }

        return filteredBatches
    }
}

private data class TmdbSeasonDto(
    val season_number: Int,
    val episode_count: Int,
    val name: String
)

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
    val aired: JikanAired? = null,
    val genres: List<JikanGenre>? = null,
    val synopsis: String? = null
)

private data class JikanGenre(
    val name: String?
)

private data class JikanSeasonalResponse(
    val data: List<JikanAnime>?,
    val pagination: JikanPagination?
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
    @com.google.gson.annotations.SerializedName("Page")
    val schedulePage: AniPage?
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

private data class ALSearchResponse(
    val data: ALSearchData?
)

private data class ALSearchData(
    @com.google.gson.annotations.SerializedName("Page")
    val searchPage: ALSearchPage?
)

private data class ALSearchPage(
    val media: List<ALSearchMedia>?
)

private data class ALSearchMedia(
    val id: Int?,
    val idMal: Int?,
    val title: AniTitle?,
    val coverImage: AniCoverImage?,
    val type: String?,
    val format: String?,
    val duration: Int?,
    val episodes: Int?,
    val averageScore: Double?,
    val startDate: AniDate?,
    val relations: AniRelations?
)

private data class AniRelations(
    val edges: List<AniRelationEdge>?
)

private data class AniRelationEdge(
    val relationType: String?,
    val node: ALSearchMedia?
)

private data class AniDate(
    val year: Int?,
    val month: Int?,
    val day: Int?
)

private data class ShikimoriFranchiseResponse(
    val nodes: List<ShikimoriFranchiseNode>?
)

private data class ShikimoriFranchiseNode(
    val id: Long?,
    val kind: String?,
    val name: String?
)
