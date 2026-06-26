const TMDB_API_KEY = "7332f575cc32b96fb7e03ccb66ea80b4";

// Static mapping for major long-running shows to speed up load times (MAL ID -> TVDB ID)
const STATIC_MAPPINGS = {
  21: 81797,     // One Piece
  20: 78857,     // Naruto
  1735: 79824,   // Naruto Shippuden
  269: 74796,    // Bleach
  11061: 252322, // Hunter x Hunter (2011)
  6702: 114801,  // Fairy Tail
  34572: 331753, // Black Clover
  813: 81472,    // Dragon Ball Z
  30694: 295068, // Dragon Ball Super
  34566: 321285  // Boruto: Naruto Next Generations
};

/**
 * Resolves a MyAnimeList ID to a TMDb TV Show ID using Kitsu mappings and TMDb Find.
 */
export async function resolveTmdId(malId) {
  try {
    const idNum = parseInt(malId);
    if (isNaN(idNum)) return null;

    // Check static mapping first
    let tvdbId = STATIC_MAPPINGS[idNum];

    if (!tvdbId) {
      // Step 1: Query Kitsu mappings for the MAL ID to find Kitsu's internal ID
      const kitsuRes = await fetch(
        `https://kitsu.io/api/edge/mappings?filter[externalSite]=myanimelist/anime&filter[externalId]=${malId}&include=item`
      );
      if (!kitsuRes.ok) return null;
      const kitsuJson = await kitsuRes.json();
      const kitsuId = kitsuJson.included?.[0]?.id;
      if (!kitsuId) return null;

      // Step 2: Query Kitsu mappings again with Kitsu's internal ID to find the TVDB ID
      const mappingsRes = await fetch(
        `https://kitsu.io/api/edge/anime/${kitsuId}/mappings`
      );
      if (!mappingsRes.ok) return null;
      const mappingsJson = await mappingsRes.json();

      // Find the mapping that corresponds to theTVDB
      const tvdbMapping = (mappingsJson.data || []).find(
        (m) =>
          m.attributes?.externalSite === "thetvdb" ||
          m.attributes?.externalSite === "thetvdb/series"
      );
      if (!tvdbMapping) return null;
      tvdbId = tvdbMapping.attributes.externalId;
    }

    if (!tvdbId) return null;

    // Clean tvdbId if it has a slash (e.g. 74796/1)
    if (typeof tvdbId === "string" && tvdbId.includes("/")) {
      tvdbId = tvdbId.split("/")[0];
    }

    // Step 3: Query TMDb's find endpoint using the TVDB ID to get TMDb's TV Show ID
    const tmdbRes = await fetch(
      `https://api.themoviedb.org/3/find/${tvdbId}?api_key=${TMDB_API_KEY}&external_source=tvdb_id`
    );
    if (!tmdbRes.ok) return null;
    const tmdbJson = await tmdbRes.json();
    if (tmdbJson.tv_results && tmdbJson.tv_results.length > 0) {
      return tmdbJson.tv_results[0].id;
    }
  } catch (err) {
    console.warn("Failed to resolve TMDb ID dynamically:", err);
  }
  return null;
}

/**
 * Fetches the seasons list from TMDb for a given TMDb TV Show ID.
 */
export async function fetchTmdSeasons(tmdbId) {
  try {
    const res = await fetch(
      `https://api.themoviedb.org/3/tv/${tmdbId}?api_key=${TMDB_API_KEY}`
    );
    if (!res.ok) throw new Error(`TMDb API returned status ${res.status}`);
    const json = await res.json();
    return json.seasons || [];
  } catch (err) {
    console.warn(`Failed to fetch TMDb seasons for show ${tmdbId}:`, err);
    return [];
  }
}

/**
 * Computes absolute episode start/end boundaries for TMDb seasons and builds arc batches.
 */
export function buildArcsFromSeasons(seasons, episodes) {
  if (!seasons || seasons.length === 0 || !episodes || episodes.length === 0) {
    return [];
  }

  // Filter out Season 0 (Specials) and sort chronologically by season number
  const validSeasons = seasons
    .filter((s) => s.season_number > 0)
    .sort((a, b) => a.season_number - b.season_number);

  let currentStart = 1;
  const batches = validSeasons.map((season, index) => {
    const start = currentStart;
    const end = currentStart + season.episode_count - 1;
    currentStart = end + 1;

    // Filter which episodes of the current stream fall into this season's range
    const batchEps = episodes.filter(
      (ep) => ep.number >= start && ep.number <= end
    );

    return {
      index,
      start,
      end,
      label: `${season.name} (${start}-${end})`,
      episodes: batchEps,
      episodesCount: season.episode_count,
    };
  });

  // Adjust ongoing/currently airing series boundaries
  if (batches.length > 0) {
    const maxEpNum = episodes[episodes.length - 1].number;
    const lastBatch = batches[batches.length - 1];

    if (maxEpNum > lastBatch.end) {
      const originalName = validSeasons[validSeasons.length - 1].name;
      lastBatch.end = maxEpNum;
      lastBatch.label = `${originalName} (${lastBatch.start}-${maxEpNum})`;
      lastBatch.episodes = episodes.filter(
        (ep) => ep.number >= lastBatch.start && ep.number <= maxEpNum
      );
    }
  }

  // Only return batches that contain actual episodes in our stream source
  const filteredBatches = batches.filter((b) => b.episodes.length > 0);

  // Sanity check:
  // 1. If it resulted in 0 or 1 batch, and we have many episodes (e.g. > 25), it's not a useful grouping.
  // 2. If the total episodes covered in the batches is significantly less than the stream episodes,
  //    it means TMDb data is incomplete (e.g., Naruto).
  // In either case, we return an empty array so the client falls back to standard 25-episode batches.
  if (filteredBatches.length <= 1 && episodes.length > 25) {
    return [];
  }

  const totalCoveredEps = filteredBatches.reduce((sum, b) => sum + b.episodes.length, 0);
  if (totalCoveredEps < episodes.length * 0.9 && episodes.length > 25) {
    return [];
  }

  return filteredBatches;
}
