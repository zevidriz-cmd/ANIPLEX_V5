const BASE_URL = "https://aniplex-proxy.f1886391.workers.dev/api/v2";

export const STREAM_PROXY_BASE = import.meta.env.VITE_STREAM_PROXY_URL || "/stream-proxy";

async function fetchJson(url) {
  try {
    const res = await fetch(url, {
      headers: {
        "Accept": "application/json",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
      }
    });
    if (!res.ok) {
      throw new Error(`API error: ${res.status}`);
    }
    const json = await res.json();
    if (json.success) {
      return json.data;
    } else {
      throw new Error(json.error || "Request failed");
    }
  } catch (err) {
    console.error(`Error fetching ${url}:`, err);
    throw err;
  }
}

export async function getHome() {
  return fetchJson(`${BASE_URL}/home`);
}

export async function getAnimeDetail(id) {
  return fetchJson(`${BASE_URL}/anime/${id}`);
}

export async function getEpisodes(id) {
  return fetchJson(`${BASE_URL}/episodes/${id}`);
}

export async function search(keyword, page = 1) {
  return fetchJson(`${BASE_URL}/search?keyword=${encodeURIComponent(keyword)}&page=${page}`);
}

export async function filterAnime({ type, status, genres, sort, page = 1 }) {
  const params = new URLSearchParams();
  if (type) params.append("type", type);
  if (status) params.append("status", status);
  if (genres) params.append("genres", genres);
  if (sort) params.append("sort", sort);
  params.append("page", page);
  return fetchJson(`${BASE_URL}/filter?${params.toString()}`);
}

export async function getSuggestions(keyword) {
  return fetchJson(`${BASE_URL}/suggestion?keyword=${encodeURIComponent(keyword)}`);
}

export async function getCategory(category, page = 1) {
  return fetchJson(`${BASE_URL}/animes/${category}?page=${page}`);
}

export async function getGenre(genre, page = 1) {
  return fetchJson(`${BASE_URL}/animes/genre/${encodeURIComponent(genre)}?page=${page}`);
}

export async function getSchedules(date = null) {
  const query = date ? `?date=${date}` : "";
  return fetchJson(`${BASE_URL}/schedules${query}`);
}

export async function getCharacters(id) {
  return fetchJson(`${BASE_URL}/characters/${id}`);
}

export async function getStreamSources(episodeId, server = "hd-1", category = "sub") {
  return fetchJson(`${BASE_URL}/episode/sources?animeEpisodeId=${encodeURIComponent(episodeId)}&server=${server}&category=${category}`);
}

export async function getDirectStream(episodeId, server = "hd-1", category = "sub", malId = null, epNumber = null, title = null) {
  try {
    // 1. Get the iframe source from our existing API
    const sourcesData = await getStreamSources(episodeId, server, category);
    const iframeUrl = sourcesData?.sources?.[0]?.url;
    if (!iframeUrl) throw new Error("No iframe source found");

    // 2. Fetch the iframe HTML via our local proxy to extract megaplay player ID
    const cleanUrl = iframeUrl.replace(/https:\/\//, '');
    const proxyUrl = `${STREAM_PROXY_BASE}/${cleanUrl}`;

    const response = await fetch(proxyUrl);
    if (!response.ok) throw new Error(`Proxy error fetching iframe: ${response.status}`);
    const html = await response.text();

    // Extract the megaplay source URL inside the iframe
    const megaplayMatch = html.match(/src=["'](https:\/\/megaplay\.buzz\/stream\/s-[1-9]\/\d+\/(?:sub|dub))["']/);
    const megaplayUrl = megaplayMatch ? megaplayMatch[1] : null;
    if (!megaplayUrl) throw new Error("Megaplay player URL not found in iframe");

    // Fetch megaplay player HTML to extract the data-id
    const cleanMegaplayUrl = megaplayUrl.replace(/https:\/\//, '');
    const megaplayProxyUrl = `${STREAM_PROXY_BASE}/${cleanMegaplayUrl}`;
    const megaplayRes = await fetch(megaplayProxyUrl);
    if (!megaplayRes.ok) throw new Error(`Proxy error fetching player: ${megaplayRes.status}`);
    const megaplayHtml = await megaplayRes.text();

    // Extract data-id="175229"
    const dataIdMatch = megaplayHtml.match(/data-id=["'](\d+)["']/);
    const dataId = dataIdMatch ? dataIdMatch[1] : null;
    if (!dataId) throw new Error("Could not extract data-id from player");

    // 3. Try to fetch getSources first, fall back to getSourcesNew if needed
    let sourcesNewJson;
    let success = false;

    try {
      const sourcesProxyUrl = `${STREAM_PROXY_BASE}/megaplay.buzz/stream/getSources?id=${dataId}`;
      const res = await fetch(sourcesProxyUrl, {
        headers: {
          "X-Requested-With": "XMLHttpRequest"
        }
      });
      if (res.ok) {
        const data = await res.json();
        if (data?.sources?.file) {
          sourcesNewJson = data;
          success = true;
        }
      }
    } catch (err) {
      console.warn("getSources failed, trying getSourcesNew:", err);
    }

    if (!success) {
      const sourcesNewProxyUrl = `${STREAM_PROXY_BASE}/megaplay.buzz/stream/getSourcesNew?id=${dataId}`;
      const res = await fetch(sourcesNewProxyUrl, {
        headers: {
          "X-Requested-With": "XMLHttpRequest"
        }
      });
      if (!res.ok) throw new Error(`Proxy error fetching HLS sources: ${res.status}`);
      sourcesNewJson = await res.json();
    }

    const fileUrl = sourcesNewJson?.sources?.file;
    if (!fileUrl) throw new Error("HLS master.m3u8 file not found in response");

    // Rewrite the fileUrl to go through our proxy (to bypass Cloudflare WAF on playlist domains)
    const cleanFileUrl = fileUrl.replace(/https:\/\//, '');
    const proxiedFileUrl = `${STREAM_PROXY_BASE}/${cleanFileUrl}`;

    // Rewrite subtitle tracks to go through proxy too
    const proxiedTracks = (sourcesNewJson?.tracks || []).map(track => {
      if (track.file) {
        const cleanTrackUrl = track.file.replace(/https:\/\//, '');
        return {
          ...track,
          file: `${STREAM_PROXY_BASE}/${cleanTrackUrl}`
        };
      }
      return track;
    });

    return {
      hlsUrl: proxiedFileUrl,
      tracks: proxiedTracks,
      intro: sourcesNewJson?.intro || { start: 0, end: 0 },
      outro: sourcesNewJson?.outro || { start: 0, end: 0 }
    };

  } catch (err) {
    console.warn("Zoro direct stream failed, attempting backup flow...", err);
    // Execute fallback loop if malId and epNumber are provided
    if (malId && epNumber) {
      return await getBackupStream(malId, epNumber, title);
    }
    throw err;
  }
}

export async function getBackupStream(malId, epNumber, title, onStatusUpdate = null) {
  // Try Gogoanime first
  try {
    if (onStatusUpdate) onStatusUpdate("Zoro streams failed. Fetching Gogoanime backup...");
    console.log(`[Backup Flow] Attempting Gogoanime backup...`);
    const gogoData = await fetchBackupFromApi(malId, epNumber, title, "gogoanime");
    return gogoData;
  } catch (gogoErr) {
    console.warn(`[Backup Flow] Gogoanime backup failed:`, gogoErr);
    // Try AnimePahe second
    try {
      if (onStatusUpdate) onStatusUpdate("Gogoanime failed. Fetching AnimePahe backup...");
      console.log(`[Backup Flow] Attempting AnimePahe backup...`);
      const paheData = await fetchBackupFromApi(malId, epNumber, title, "animepahe");
      return paheData;
    } catch (paheErr) {
      console.warn(`[Backup Flow] AnimePahe backup failed:`, paheErr);
      // Try MegaPlay Direct as 3rd fallback (extracts .m3u8 for our custom player)
      try {
        if (onStatusUpdate) onStatusUpdate("Extracting MegaPlay direct stream...");
        console.log(`[Backup Flow] Attempting MegaPlay direct stream extraction...`);
        const megaData = await getMegaplayDirectStream(malId, epNumber);
        return megaData;
      } catch (megaErr) {
        console.error(`[Backup Flow] All backup streaming providers failed (including MegaPlay direct).`);
        throw new Error("No backup streams could be resolved from any provider.");
      }
    }
  }
}

export async function getSingleBackupStream(malId, epNumber, title, provider) {
  return await fetchBackupFromApi(malId, epNumber, title, provider);
}

async function fetchBackupFromApi(malId, epNumber, title, provider) {
  const params = new URLSearchParams();
  if (malId) params.append("malId", malId);
  if (epNumber) params.append("episodeNumber", epNumber);
  if (title) params.append("title", title);
  params.append("provider", provider);

  const netlifyBase = "https://anistream-web.netlify.app";
  const res = await fetch(`${netlifyBase}/.netlify/functions/fallback-stream?${params.toString()}`);
  if (!res.ok) {
    throw new Error(`Fallback API failed with status ${res.status}`);
  }
  const json = await res.json();
  if (!json.success || !json.sources || json.sources.length === 0) {
    throw new Error(json.error || "No sources returned from fallback API");
  }

  // Find HLS source (.m3u8)
  const hlsSource = json.sources.find(s => s.type === "hls" || s.url.includes(".m3u8")) || json.sources[0];
  if (!hlsSource) {
    throw new Error("No usable HLS source found in fallback payload");
  }

  // Proxy the HLS master playlist URL
  const cleanHlsUrl = hlsSource.url.replace(/https:\/\//, '');
  const proxiedHlsUrl = `${STREAM_PROXY_BASE}/${cleanHlsUrl}`;

  // Proxy subtitle track files
  const proxiedTracks = (json.subtitles || []).map(track => {
    if (track.url) {
      const cleanTrackUrl = track.url.replace(/https:\/\//, '');
      return {
        file: `${STREAM_PROXY_BASE}/${cleanTrackUrl}`,
        label: track.lang || "English",
        kind: "captions",
        default: track.lang === "English"
      };
    }
    return track;
  });

  return {
    hlsUrl: proxiedHlsUrl,
    tracks: proxiedTracks,
    intro: json.intro || { start: 0, end: 0 },
    outro: json.outro || { start: 0, end: 0 },
    provider: provider
  };
}

/**
 * Extract MegaPlay's direct .m3u8 stream via their MAL-based URL.
 * This gives us smooth playback in our custom HLS.js player instead of
 * the buffering-heavy iframe fallback.
 */
export async function getMegaplayDirectStream(malId, epNumber, audioCategory = "sub") {
  try {
    // 1. Fetch MegaPlay's MAL-based player page
    const megaplayPageUrl = `megaplay.buzz/stream/mal/${malId}/${epNumber}/${audioCategory}`;
    const pageRes = await fetch(`${STREAM_PROXY_BASE}/${megaplayPageUrl}`);
    if (!pageRes.ok) throw new Error(`MegaPlay page fetch failed: ${pageRes.status}`);
    const pageHtml = await pageRes.text();

    // 2. Try to extract embedded player URL (s-1, s-2, etc.)
    const embedMatch = pageHtml.match(/src=["'](https:\/\/megaplay\.buzz\/stream\/s-[1-9]\/\d+\/(?:sub|dub))["']/);
    let playerHtml = pageHtml;

    if (embedMatch) {
      // Fetch the inner player page
      const innerUrl = embedMatch[1].replace(/https:\/\//, '');
      const innerRes = await fetch(`${STREAM_PROXY_BASE}/${innerUrl}`);
      if (innerRes.ok) {
        playerHtml = await innerRes.text();
      }
    }

    // 3. Extract data-id from the player HTML
    const dataIdMatch = playerHtml.match(/data-id=["'](\d+)["']/);
    const dataId = dataIdMatch ? dataIdMatch[1] : null;
    if (!dataId) throw new Error("Could not extract data-id from MegaPlay player");

    // 4. Try getSources first, fall back to getSourcesNew
    let sourcesJson;
    let resolved = false;

    try {
      const res = await fetch(`${STREAM_PROXY_BASE}/megaplay.buzz/stream/getSources?id=${dataId}`, {
        headers: { "X-Requested-With": "XMLHttpRequest" }
      });
      if (res.ok) {
        const data = await res.json();
        if (data?.sources?.file) {
          sourcesJson = data;
          resolved = true;
        }
      }
    } catch (e) {
      console.warn("MegaPlay getSources failed, trying getSourcesNew:", e);
    }

    if (!resolved) {
      const res = await fetch(`${STREAM_PROXY_BASE}/megaplay.buzz/stream/getSourcesNew?id=${dataId}`, {
        headers: { "X-Requested-With": "XMLHttpRequest" }
      });
      if (!res.ok) throw new Error(`MegaPlay getSourcesNew failed: ${res.status}`);
      sourcesJson = await res.json();
    }

    const fileUrl = sourcesJson?.sources?.file;
    if (!fileUrl) throw new Error("MegaPlay HLS master.m3u8 not found");

    // 5. Proxy the HLS URL and subtitle tracks
    const cleanFileUrl = fileUrl.replace(/https:\/\//, '');
    const proxiedFileUrl = `${STREAM_PROXY_BASE}/${cleanFileUrl}`;

    const proxiedTracks = (sourcesJson?.tracks || []).map(track => {
      if (track.file) {
        const cleanTrackUrl = track.file.replace(/https:\/\//, '');
        return {
          ...track,
          file: `${STREAM_PROXY_BASE}/${cleanTrackUrl}`
        };
      }
      return track;
    });

    console.log(`[MegaPlay Direct] Successfully extracted stream for MAL ${malId} ep ${epNumber}`);

    return {
      hlsUrl: proxiedFileUrl,
      tracks: proxiedTracks,
      intro: sourcesJson?.intro || { start: 0, end: 0 },
      outro: sourcesJson?.outro || { start: 0, end: 0 },
      provider: "megaplay-direct"
    };

  } catch (err) {
    console.error("[MegaPlay Direct] Extraction failed:", err);
    throw err;
  }
}

export async function getSeasons(malId) {
  return fetchJson(`${BASE_URL}/seasons/${malId}`);
}

const STATIC_MAL_RESOLUTIONS = {
  // Mushoku Tensei
  "39535": "5694",   // Season 1 Part 1
  "45576": "6675",   // Season 1 Part 2
  "51179": "6537",   // Season 2 Part 1
  "55888": "6159",   // Season 2 Part 2
  "59193": "8800",   // Season 3
  "58752": "8800",   // Season 3 Alternative MAL ID
  "50360": "7045",   // Eris Special
  
  // Demon Slayer
  "38000": "1551",   // Season 1
  "49926": "17870",  // Mugen Train Arc
  "47074": "18056"   // Entertainment District Arc
};

export async function resolveMAL(malId) {
  const parsedId = String(malId);
  if (STATIC_MAL_RESOLUTIONS[parsedId]) {
    return { success: true, anikotoId: STATIC_MAL_RESOLUTIONS[parsedId] };
  }
  return fetchJson(`${BASE_URL}/resolve-mal/${parsedId}`);
}

// AniSkip API integration (direct endpoint)
export async function getSkipTimes(malId, episodeNumber) {
  try {
    const res = await fetch(`https://api.aniskip.com/v2/skip-times/${malId}/${episodeNumber}?types[]=op&types[]=ed&types[]=mixed-op&types[]=recap&episodeLength=0`);
    if (!res.ok) return null;
    const json = await res.json();
    return json.results || null;
  } catch (err) {
    console.warn("AniSkip error:", err);
    return null;
  }
}
