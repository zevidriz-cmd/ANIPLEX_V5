import { ANIME, META } from "@consumet/extensions";
import * as cheerio from "cheerio";

async function scrapeAniNeko(title, episodeNumber) {
  const base = "https://anineko.to";
  const searchUrl = `${base}/browser?keyword=${encodeURIComponent(title)}`;
  console.log(`[Scraper] Searching for "${title}"...`);
  
  // 1. Search Phase (Heuristic Match)
  const searchRes = await fetch(searchUrl, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36'
    }
  });
  if (!searchRes.ok) throw new Error(`Search failed: ${searchRes.status}`);
  const searchHtml = await searchRes.text();
  const $ = cheerio.load(searchHtml);
  
  let watchPath = null;
  const targetTitleLower = title.toLowerCase().trim();
  const cleanTitleLower = targetTitleLower.replace(/[^a-z0-9]/g, ""); // e.g. "onepiece"
  
  $('a').each((i, el) => {
    const href = $(el).attr('href') || '';
    if (!href.includes('/watch/')) return;
    
    const hrefSlug = href.split('/').pop().toLowerCase();
    const cleanHrefSlug = hrefSlug.replace(/[^a-z0-9]/g, "");
    
    const linkTextLower = $(el).text().toLowerCase().trim();
    const cleanLinkText = linkTextLower.replace(/[^a-z0-9]/g, "");
    
    const imgAltLower = ($(el).find('img').attr('alt') || '').toLowerCase().trim();
    const cleanImgAlt = imgAltLower.replace(/[^a-z0-9]/g, "");

    if (
      cleanLinkText === cleanTitleLower || 
      cleanHrefSlug === cleanTitleLower || 
      cleanImgAlt === cleanTitleLower
    ) {
      watchPath = href;
      return false; // Break loop
    }
  });

  if (!watchPath) {
    $('a').each((i, el) => {
      const href = $(el).attr('href') || '';
      if (!href.includes('/watch/')) return;

      const linkTextLower = $(el).text().toLowerCase().trim();
      const imgAltLower = ($(el).find('img').attr('alt') || '').toLowerCase().trim();

      if (linkTextLower.includes(targetTitleLower) || imgAltLower.includes(targetTitleLower)) {
        watchPath = href;
        return false;
      }
    });
  }

  if (!watchPath) {
    $('a').each((i, el) => {
      const href = $(el).attr('href') || '';
      if (href.includes('/watch/') && href !== '/watch/' && !href.includes('search') && !href.includes('browser')) {
        watchPath = href;
        return false;
      }
    });
  }

  if (!watchPath) {
    throw new Error(`No watch link found for "${title}"`);
  }
  
  // 2. Fetch watch page (Episode Link Heuristic Match)
  const watchUrl = `${base}${watchPath}`;
  console.log(`[Scraper] Fetching watch page: ${watchUrl}`);
  const watchRes = await fetch(watchUrl, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36'
    }
  });
  if (!watchRes.ok) throw new Error(`Details page failed: ${watchRes.status}`);
  const watchHtml = await watchRes.text();
  const $$ = cheerio.load(watchHtml);
  
  let epPath = null;
  const targetEp = parseInt(episodeNumber, 10);
  
  $$('a').each((i, el) => {
    const href = $$(el).attr('href') || '';
    if (!href.includes('/watch/') || href === watchPath) return;
    
    const text = $$(el).text().trim().toLowerCase();
    const epRegex = new RegExp(`(?:ep|episode|ch)?\\s*\\b0*${targetEp}\\b`, 'i');
    
    if (epRegex.test(text)) {
      const match = text.match(/\b\d+\b/g);
      if (match && match.map(Number).includes(targetEp)) {
        epPath = href;
        return false;
      }
    }
  });

  if (!epPath) {
    $$('*').each((i, el) => {
      if (el.children && el.children.length > 0) return;
      const text = $$(el).text().trim().toLowerCase();
      const epRegex = new RegExp(`(?:ep|episode|ch)?\\s*\\b0*${targetEp}\\b`, 'i');
      if (epRegex.test(text)) {
        const parentLink = $$(el).closest('a');
        if (parentLink.length > 0) {
          const href = parentLink.attr('href');
          if (href && href.includes('/watch/')) {
            epPath = href;
            return false;
          }
        }
      }
    });
  }
  
  if (!epPath) {
    throw new Error(`Episode ${episodeNumber} not found on watch page`);
  }
  
  // 3. Fetch episode page (Player Iframe Heuristic Match)
  const epUrl = `${base}${epPath}`;
  console.log(`[Scraper] Fetching episode page: ${epUrl}`);
  const epRes = await fetch(epUrl, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36'
    }
  });
  if (!epRes.ok) throw new Error(`Episode page failed: ${epRes.status}`);
  const epHtml = await epRes.text();
  const $$$ = cheerio.load(epHtml);
  
  let iframeUrl = null;
  $$$('[data-video], [data-src], [data-href], iframe, button, a').each((i, el) => {
    if (iframeUrl) return;
    
    const dataVideo = $$$ (el).attr('data-video');
    const dataSrc = $$$ (el).attr('data-src');
    const dataHref = $$$ (el).attr('data-href');
    const src = $$$ (el).attr('src');
    const href = $$$ (el).attr('href');
    
    const val = dataVideo || dataSrc || dataHref || src || href || '';
    if (val.startsWith('http://') || val.startsWith('https://') || val.startsWith('//')) {
      const valLower = val.toLowerCase();
      const isStreamHost = valLower.includes('embed') || valLower.includes('player') || valLower.includes('stream') || valLower.includes('video') || valLower.includes('.site') || valLower.includes('.buzz');
      if (isStreamHost) {
        iframeUrl = val.startsWith('//') ? `https:${val}` : val;
      }
    }
  });

  if (!iframeUrl) {
    $$$('[data-video], [data-src]').each((i, el) => {
      const val = $$$ (el).attr('data-video') || $$$ (el).attr('data-src');
      if (val) {
        iframeUrl = val.startsWith('//') ? `https:${val}` : val;
        return false;
      }
    });
  }
  
  if (!iframeUrl) {
    throw new Error("No player server links found on page");
  }
  
  console.log(`[Scraper] Found iframe URL: ${iframeUrl}`);
  
  // 4. Fetch player iframe page to extract m3u8 source
  const playerRes = await fetch(iframeUrl, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36'
    }
  });
  if (!playerRes.ok) throw new Error(`Player page failed: ${playerRes.status}`);
  const playerHtml = await playerRes.text();
  
  const srcMatch = playerHtml.match(/const\s+src\s*=\s*["'](https:\/\/.*\.m3u8)["']/);
  const hlsUrl = srcMatch ? srcMatch[1] : null;
  if (!hlsUrl) {
    throw new Error("Could not find master.m3u8 URL in iframe player page script");
  }
  
  return {
    sources: [{
      url: hlsUrl,
      isM3U8: true,
      quality: "default"
    }],
    subtitles: [],
    intro: { start: 0, end: 0 },
    outro: { start: 0, end: 0 }
  };
}

function withTimeout(promise, ms) {
  const timeout = new Promise((_, reject) =>
    setTimeout(() => reject(new Error("Timeout")), ms)
  );
  return Promise.race([promise, timeout]);
}

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

    // 1. Check if custom scraper should handle Gogoanime
    if (selectedProvider === "gogoanime" && title) {
      try {
        console.log(`[Fallback API] Invoking custom AniNeko scraper for title: "${title}"...`);
        sources = await scrapeAniNeko(title, epNum);
      } catch (err) {
        console.warn("[Fallback API] Custom AniNeko scraper failed:", err.message);
      }
    }

    // 2. Try AniList Meta Provider if sources not yet resolved
    if ((!sources || !sources.sources || sources.sources.length === 0) && malId && malId !== "0" && malId !== "") {
      try {
        console.log(`[Fallback API] Resolving MAL ID ${malId} with AniList Meta via ${selectedProvider}...`);
        
        const resolveMeta = async () => {
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
            
            const providerBacking = selectedProvider === "animepahe" ? new ANIME.AnimePahe() : new ANIME.Hianime();
            if (selectedProvider === "animepahe") {
              providerBacking.baseUrl = "https://animepahe.ru";
            }
            const anilist = new META.Anilist(providerBacking);
            
            const animeInfo = await anilist.fetchAnimeInfo(anilistId.toString());
            if (animeInfo && animeInfo.episodes) {
              const targetEp = animeInfo.episodes.find(e => e.number === epNum);
              if (targetEp) {
                console.log(`[Fallback API] Found AniList episode ID ${targetEp.id} for episode ${epNum}`);
                return await anilist.fetchEpisodeSources(targetEp.id);
              }
            }
          }
          return null;
        };

        sources = await withTimeout(resolveMeta(), 8000);
      } catch (err) {
        console.warn(`[Fallback API] AniList Meta mapping failed:`, err.message);
      }
    }

    // 3. Fallback to Title-based search if AniList failed or wasn't tried
    if ((!sources || !sources.sources || sources.sources.length === 0) && title) {
      try {
        console.log(`[Fallback API] Searching title "${title}" on provider ${selectedProvider}...`);
        
        const resolveTitleSearch = async () => {
          const providerInstance = selectedProvider === "animepahe" ? new ANIME.AnimePahe() : new ANIME.Hianime();
          if (selectedProvider === "animepahe") {
            providerInstance.baseUrl = "https://animepahe.ru";
          }
          const searchResults = await providerInstance.search(title);
          
          if (searchResults && searchResults.results && searchResults.results.length > 0) {
            const bestMatch = searchResults.results[0];
            console.log(`[Fallback API] Best match found: ${bestMatch.title} (${bestMatch.id})`);
            
            const animeInfo = await providerInstance.fetchAnimeInfo(bestMatch.id);
            if (animeInfo && animeInfo.episodes) {
              const targetEp = animeInfo.episodes.find(e => e.number === epNum);
              if (targetEp) {
                console.log(`[Fallback API] Found search episode ID ${targetEp.id} for episode ${epNum}`);
                return await providerInstance.fetchEpisodeSources(targetEp.id);
              }
            }
          }
          return null;
        };

        sources = await withTimeout(resolveTitleSearch(), 8000);
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
