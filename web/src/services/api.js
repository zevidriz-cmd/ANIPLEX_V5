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

export async function getDirectStream(episodeId, server = "hd-1", category = "sub") {
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

  // Rewrite the fileUrl to go through our Netlify function proxy (to bypass Cloudflare WAF on playlist domains)
  const cleanFileUrl = fileUrl.replace(/https:\/\//, '');
  const netlifyProxyBase = 'https://anistream-web-f1886391.netlify.app/.netlify/functions/stream-proxy';
  const proxiedFileUrl = `${netlifyProxyBase}/${cleanFileUrl}`;

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
}


export async function getSeasons(malId) {
  return fetchJson(`${BASE_URL}/seasons/${malId}`);
}

export async function resolveMAL(malId) {
  return fetchJson(`${BASE_URL}/resolve-mal/${malId}`);
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
