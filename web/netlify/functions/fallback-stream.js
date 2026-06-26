import { ANIME, META } from "@consumet/extensions";

export async function handler(event, context) {
  const responseHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Content-Type': 'application/json',
  };

  if (event.httpMethod === 'OPTIONS') {
    return {
      statusCode: 200,
      headers: responseHeaders,
      body: '',
    };
  }

  const { malId, episodeNumber, title, provider } = event.queryStringParameters || {};
  
  if (!episodeNumber) {
    return {
      statusCode: 400,
      headers: responseHeaders,
      body: JSON.stringify({ success: false, error: "Missing episodeNumber" }),
    };
  }

  const epNum = parseInt(episodeNumber, 10);
  const selectedProvider = provider || "gogoanime";

  try {
    let sources = null;

    // 1. Try AniList Meta Provider first if we have a malId
    if (malId && malId !== "0" && malId !== "") {
      try {
        console.log(`[Fallback API] Resolving MAL ID ${malId} with AniList Meta via ${selectedProvider}...`);
        
        // Map MAL ID to AniList ID via AniList GraphQL
        let anilistId = null;
        const query = `
          query ($id: Int) {
            Media (idMal: $id, type: ANIME) {
              id
            }
          }
        `;
        const graphResponse = await fetch("https://graphql.anilist.co", {
          method: "POST",
          headers: { "Content-Type": "application/json", "Accept": "application/json" },
          body: JSON.stringify({ query, variables: { id: parseInt(malId, 10) } }),
        });
        
        if (graphResponse.ok) {
          const graphJson = await graphResponse.json();
          anilistId = graphJson?.data?.Media?.id;
        }

        if (anilistId) {
          console.log(`[Fallback API] Mapped MAL ID ${malId} to AniList ID ${anilistId}`);
          
          // Instantiate the AniList Meta provider with our backing provider
          const providerBacking = selectedProvider === "animepahe" ? new ANIME.AnimePahe() : new ANIME.Hianime();
          const anilist = new META.Anilist(providerBacking);
          
          const animeInfo = await anilist.fetchAnimeInfo(anilistId.toString());
          if (animeInfo && animeInfo.episodes) {
            const targetEp = animeInfo.episodes.find(e => e.number === epNum);
            if (targetEp) {
              console.log(`[Fallback API] Found AniList episode ID ${targetEp.id} for episode ${epNum}`);
              sources = await anilist.fetchEpisodeSources(targetEp.id);
            }
          }
        }
      } catch (err) {
        console.warn(`[Fallback API] AniList Meta mapping failed:`, err.message);
      }
    }

    // 2. Fallback to Title-based search if AniList failed or wasn't tried
    if ((!sources || !sources.sources || sources.sources.length === 0) && title) {
      try {
        console.log(`[Fallback API] Searching title "${title}" on provider ${selectedProvider}...`);
        const providerInstance = selectedProvider === "animepahe" ? new ANIME.AnimePahe() : new ANIME.Hianime();
        const searchResults = await providerInstance.search(title);
        
        if (searchResults && searchResults.results && searchResults.results.length > 0) {
          // Take the first search result
          const bestMatch = searchResults.results[0];
          console.log(`[Fallback API] Best match found: ${bestMatch.title} (${bestMatch.id})`);
          
          const animeInfo = await providerInstance.fetchAnimeInfo(bestMatch.id);
          if (animeInfo && animeInfo.episodes) {
            const targetEp = animeInfo.episodes.find(e => e.number === epNum);
            if (targetEp) {
              console.log(`[Fallback API] Found search episode ID ${targetEp.id} for episode ${epNum}`);
              sources = await providerInstance.fetchEpisodeSources(targetEp.id);
            }
          }
        }
      } catch (err) {
        console.warn(`[Fallback API] Title search fallback failed:`, err.message);
      }
    }

    if (!sources || !sources.sources || sources.sources.length === 0) {
      throw new Error("No streaming sources could be resolved from Gogoanime or AnimePahe.");
    }

    return {
      statusCode: 200,
      headers: responseHeaders,
      body: JSON.stringify({
        success: true,
        provider: selectedProvider,
        sources: sources.sources,
        subtitles: sources.subtitles || [],
        intro: sources.intro || { start: 0, end: 0 },
        outro: sources.outro || { start: 0, end: 0 },
      }),
    };

  } catch (error) {
    console.error("[Fallback API] Error:", error);
    return {
      statusCode: 500,
      headers: responseHeaders,
      body: JSON.stringify({ success: false, error: error.message }),
    };
  }
}
