package com.aniplex.app.data.mapper

import com.aniplex.app.data.remote.dto.*
import com.aniplex.app.domain.model.*

fun AnimeDto.toDomain(): Anime {
    return Anime(
        id = id,
        title = name,
        poster = poster,
        type = type ?: "TV",
        duration = duration ?: "",
        subEpisodes = episodes?.sub ?: 0,
        dubEpisodes = episodes?.dub ?: 0,
        rate = rate ?: ""
    )
}

fun SpotlightAnimeDto.toDomain(): SpotlightAnime {
    return SpotlightAnime(
        id = id,
        name = name,
        poster = poster,
        description = description ?: "",
        rank = rank ?: 0,
        otherInfo = otherInfo ?: emptyList()
    )
}

fun AnimeDetailDataDto.toDomain(): AnimeDetail {
    val info = anime.info
    val stats = info.stats
    val moreInfo = anime.moreInfo
    
    return AnimeDetail(
        id = info.id,
        name = info.name,
        poster = info.poster,
        description = info.description ?: "",
        rating = stats?.rating ?: "",
        quality = stats?.quality ?: "",
        subEpisodes = stats?.episodes?.sub ?: 0,
        dubEpisodes = stats?.episodes?.dub ?: 0,
        totalEpisodes = (stats?.episodes?.sub ?: 0).coerceAtLeast(stats?.episodes?.dub ?: 0),
        type = stats?.type ?: "TV",
        duration = stats?.duration ?: "",
        status = moreInfo.status ?: "",
        aired = moreInfo.aired ?: "",
        premiere = moreInfo.premiered ?: "",
        genres = moreInfo.genres ?: emptyList(),
        producers = moreInfo.producers ?: emptyList(),
        studio = moreInfo.studio ?: "",
        recommendations = recommendedAnimes?.map { it.toDomain() } ?: emptyList(),
        relatedAnimes = relatedAnimes?.map { it.toDomain() } ?: emptyList(),
        malId = info.malId ?: ""
    )
}

fun EpisodeDto.toDomain(): Episode {
    return Episode(
        id = episodeId,
        number = number,
        title = title ?: "Episode $number",
        isFiller = isFiller ?: false
    )
}

fun CharacterDto.toDomain(): Character {
    return Character(
        id = id,
        name = name,
        poster = poster,
        role = role ?: "Supporting"
    )
}

fun ScheduleItemDto.toDomain(): ScheduleItem {
    return ScheduleItem(
        id = id,
        title = name,
        time = time,
        episode = episode,
        poster = poster
    )
}

fun HomeDataDto.toDomain(): HomeData {
    return HomeData(
        spotlightAnimes = spotlightAnimes?.map { it.toDomain() } ?: emptyList(),
        trendingAnimes = trendingAnimes?.map { it.toDomain() } ?: emptyList(),
        topAiringAnimes = topAiringAnimes?.map { it.toDomain() } ?: emptyList(),
        recentlyUpdatedAnimes = recentlyAddedAnimes?.map { it.toDomain() } ?: emptyList(),
        mostPopularAnimes = mostPopularAnimes?.map { it.toDomain() } ?: emptyList(),
        topUpcomingAnimes = topUpcomingAnimes?.map { it.toDomain() } ?: emptyList(),
        genres = genres ?: emptyList()
    )
}

fun SeasonDto.toDomain(): Season {
    return Season(
        malId = malId,
        title = title,
        poster = poster ?: "",
        episodes = episodes ?: 0,
        seasonNumber = seasonNumber
    )
}
