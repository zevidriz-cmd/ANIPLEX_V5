import { ANIME, META } from "@consumet/extensions";
import * as cheerio from "cheerio";
import { runHealthCheck } from "./anineko-health-check.js";

export function unpackDeanEdwards(code) {
  const match = code.match(/eval\(function\(p,a,c,k,e,d\)[\s\S]*?return p\}\('([\s\S]*?)',(\d+),(\d+),'([\s\S]*?)'\.split\('\|'\)/);
  if (!match) return null;
  let [_, p, a, c, k] = match;
  a = parseInt(a, 10);
  c = parseInt(c, 10);
  const keywords = k.split('|');
  const getWord = (num, rad) => {
    let prefix = num >= rad ? getWord(Math.floor(num / rad), rad) : '';
    let rem = num % rad;
    let char = rem > 35 ? String.fromCharCode(rem + 29) : rem.toString(36);
    return prefix + char;
  };
  while (c--) {
    if (keywords[c]) {
      const word = getWord(c, a);
      const reg = new RegExp('\\b' + word + '\\b', 'g');
      p = p.replace(reg, keywords[c]);
    }
  }
  return p;
}

export async function scrapeAniNeko(title, episodeNumber, mode = "sub", targetServer = null) {
  const base = "https://anineko.to";
  let epHtml = null;
  const targetEp = parseInt(episodeNumber, 10);
  const slug = title.toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  let epUrl = `${base}/watch/${slug}/ep-${targetEp}`;

  try {
    const directRes = await fetch(epUrl, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36'
      }
    });
    if (directRes.ok) {
      const text = await directRes.text();
      if (text.includes('server-items')) {
        console.log(`[AniNeko Scraper] Direct episode URL match: ${epUrl}`);
        epHtml = text;
      }
    }
  } catch (e) {}

  if (!epHtml) {
    const searchUrl = `${base}/browser?keyword=${encodeURIComponent(title)}`;
    console.log(`[AniNeko Scraper] Searching for "${title}" (mode: ${mode})...`);
    
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
    const cleanTitleLower = targetTitleLower.replace(/[^a-z0-9]/g, "");
  
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
      return false;
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
  
  // 2. Fetch watch page (Episode Link Match)
  const watchUrl = `${base}${watchPath}`;
  console.log(`[AniNeko Scraper] Fetching watch page: ${watchUrl}`);
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
  
  }

  if (!epHtml) {
    const epUrl = `${base}${epPath}`;
    console.log(`[AniNeko Scraper] Fetching episode page: ${epUrl}`);
    const epRes = await fetch(epUrl, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36'
      }
    });
    if (!epRes.ok) throw new Error(`Episode page failed: ${epRes.status}`);
    epHtml = await epRes.text();
  }

  const $$$ = cheerio.load(epHtml);

  // Target specific mode container (sub, hsub, dub)
  let targetMode = mode.toLowerCase();
  let serverContainer = $$$(`.server-items[data-id="${targetMode}"]`);
  if (!serverContainer || serverContainer.length === 0 || serverContainer.find('button.server-video, button.server').length === 0) {
    console.warn(`[AniNeko Scraper] Requested mode "${mode}" container not found or empty. Falling back to "sub" container.`);
    targetMode = "sub";
    serverContainer = $$$(`.server-items[data-id="sub"]`);
    if (!serverContainer || serverContainer.length === 0 || serverContainer.find('button.server-video, button.server').length === 0) {
      serverContainer = $$$('.server-items.lang-group').first();
    }
  }

  // Parse server buttons and subtitle URL
  const serverList = [];
  let extractedSubtitleUrl = null;

  serverContainer.find('button.server-video, button.server').each((i, el) => {
    const dataVideo = $$$(el).attr('data-video');
    if (!dataVideo) return;
    const name = $$$(el).text().trim().replace(/\s+/g, ' ');

    // Exclude Doodstream (HTTP 403 anti-bot)
    if (dataVideo.includes('playmogo.com') || name.toLowerCase().includes('doodstream')) {
      return;
    }

    serverList.push({ name, dataVideo });

    // Parse VTT subtitle URL if available
    if (!extractedSubtitleUrl) {
      try {
        const u = new URL(dataVideo);
        const vtt = u.searchParams.get('sub') || u.searchParams.get('caption_1') || u.searchParams.get('c1_file');
        if (vtt && vtt.startsWith('http')) {
          extractedSubtitleUrl = vtt;
        }
      } catch (e) {}
    }
  });

  if (serverList.length === 0) {
    throw new Error(`No compatible streaming servers found for mode "${targetMode}"`);
  }

  console.log(`[AniNeko Scraper] Found ${serverList.length} candidate servers for mode "${targetMode}". Subtitle URL: ${extractedSubtitleUrl || 'None'}`);

  // 4. Try servers in sequence (HD-1 -> StreamHG -> Earnvids)
  let resolvedStreamUrl = null;
  let activeServerName = null;

  let matchedServers = serverList;
  if (targetServer) {
    const matched = serverList.filter(s => s.name.toLowerCase().includes(targetServer.toLowerCase()) || targetServer.toLowerCase().includes(s.name.toLowerCase()));
    if (matched.length > 0) {
      matchedServers = matched;
    } else {
      console.log(`[AniNeko Scraper] Target server "${targetServer}" not found in candidate list. Trying all available servers.`);
    }
  }

  for (const server of matchedServers) {
    try {
      console.log(`[AniNeko Scraper] Attempting server: ${server.name} (${server.dataVideo})...`);
      const embedRes = await fetch(server.dataVideo, {
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36',
          'Referer': epUrl
        }
      });

      if (!embedRes.ok) {
        console.warn(`[AniNeko Scraper] Server ${server.name} returned HTTP ${embedRes.status}`);
        continue;
      }

      const embedHtml = await embedRes.text();

      // Method A: Direct Regex (HD-1 / vivibebe.site)
      let m3u8Match = embedHtml.match(/const\s+src\s*=\s*["'](https?:\/\/[^"'\s\\]+?\.m3u8[^"'\s\\]*)["']/i) ||
                      embedHtml.match(/(https?:\/\/[^"'\s\\]+?\.m3u8[^"'\s\\]*)/i);

      if (m3u8Match) {
        resolvedStreamUrl = m3u8Match[1];
      }

      // Method B: Dean Edwards Unpacker (StreamHG / Earnvids)
      if (!resolvedStreamUrl) {
        const unpackedHtml = unpackDeanEdwards(embedHtml);
        if (unpackedHtml) {
          const unpackedMatch = unpackedHtml.match(/https?:\/\/[^"'\s\\]+?\.(?:m3u8|mp4)[^"'\s\\]*/i);
          if (unpackedMatch) {
            resolvedStreamUrl = unpackedMatch[0];
          }
        }
      }

      if (resolvedStreamUrl) {
        activeServerName = server.name;
        console.log(`[AniNeko Scraper] Successfully extracted stream from ${server.name}: ${resolvedStreamUrl}`);
        break; // Early exit on success!
      }
    } catch (err) {
      console.warn(`[AniNeko Scraper] Error fetching server ${server.name}:`, err.message);
    }
  }

  if (!resolvedStreamUrl) {
    throw new Error(`Failed to extract playable stream from all available servers for mode "${targetMode}"`);
  }

  const subtitlesList = extractedSubtitleUrl
    ? [{ url: extractedSubtitleUrl, lang: "English" }]
    : [];

  return {
    sources: [{
      url: resolvedStreamUrl,
      isM3U8: resolvedStreamUrl.includes('.m3u8'),
      quality: "default",
      server: activeServerName
    }],
    subtitles: subtitlesList,
    intro: { start: 0, end: 0 },
    outro: { start: 0, end: 0 }
  };
}

export async function enumerateAniNekoServers(title, episodeNumber) {
  const base = "https://anineko.to";
  const searchUrl = `${base}/browser?keyword=${encodeURIComponent(title)}`;
  console.log(`[AniNeko Enumerator] Searching for "${title}"...`);

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
  const cleanTitleLower = targetTitleLower.replace(/[^a-z0-9]/g, "");

  // Pass 1: Exact equality match
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
      return false;
    }
  });

  // Pass 2: Substring match fallback
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

  // Pass 3: Fallback first result
  if (!watchPath) {
    $('a').each((i, el) => {
      const href = $(el).attr('href') || '';
      if (href.includes('/watch/') && href !== '/watch/' && !href.includes('search') && !href.includes('browser')) {
        watchPath = href;
        return false;
      }
    });
  }

  if (!watchPath) throw new Error(`Anime not found on AniNeko for search: "${title}"`);

  const watchUrl = `${base}${watchPath}`;
  console.log(`[AniNeko Enumerator] Fetching watch page: ${watchUrl}`);
  const watchRes = await fetch(watchUrl, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36'
    }
  });
  if (!watchRes.ok) throw new Error(`Watch page fetch failed: ${watchRes.status}`);
  const watchHtml = await watchRes.text();
  const $watch = cheerio.load(watchHtml);

  let targetEpPath = null;
  $watch('a[href*="/watch/"]').each((i, el) => {
    const href = $(el).attr('href') || '';
    const text = $(el).text().trim().toLowerCase();
    if (href.includes(`/ep-${episodeNumber}`) || text === `ep ${episodeNumber}` || text === `episode ${episodeNumber}` || text === `${episodeNumber}`) {
      targetEpPath = href;
      return false;
    }
  });

  if (!targetEpPath) throw new Error(`Episode ${episodeNumber} path not found`);

  const epUrl = `${base}${targetEpPath}`;
  console.log(`[AniNeko Enumerator] Fetching episode page: ${epUrl}`);
  const epRes = await fetch(epUrl, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36'
    }
  });
  if (!epRes.ok) throw new Error(`Episode page fetch failed: ${epRes.status}`);
  const epHtml = await epRes.text();
  const $ep = cheerio.load(epHtml);

  const modes = [];
  const servers = {};

  $ep('.server-items.lang-group').each((i, group) => {
    const modeId = $ep(group).attr('data-id');
    if (!modeId) return;
    if (!modes.includes(modeId)) modes.push(modeId);
    servers[modeId] = [];

    $ep(group).find('button.server-video, button.server').each((j, btn) => {
      const rawName = $ep(btn).text().trim().replace(/\s+/g, ' ');
      const dataVideo = $ep(btn).attr('data-video') || '';
      if (dataVideo && !dataVideo.includes('playmogo.com') && !rawName.toLowerCase().includes('doodstream')) {
        servers[modeId].push({
          name: rawName,
          embedUrl: dataVideo
        });
      }
    });
  });

  return {
    modes,
    servers
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

  const qs = event.queryStringParameters || {};
  const rawQ = event.rawQuery || event.rawUrl || "";
  const reqAction = qs.action || (rawQ.match(/action=([^&]+)/) || [])[1];

  if (reqAction === "health-check" || reqAction === "healthcheck" || rawQ.includes("health-check")) {
    try {
      return await runHealthCheck(event, context);
    } catch (e) {
      return {
        statusCode: 500,
        headers: responseHeaders,
        body: JSON.stringify({ success: false, error: `Health check execution error: ${e.message}` }),
      };
    }
  }

  let { malId, episodeNumber, title: rawTitle, provider, mode, server, action } = qs;
  let targetTitle = rawTitle;

  // Action: servers — Cheap Mode & Server Enumeration
  if (action === "servers") {
    if (!targetTitle) {
      return {
        statusCode: 400,
        headers: responseHeaders,
        body: JSON.stringify({ success: false, error: "Missing title parameter for action=servers" }),
      };
    }
    const epNum = parseInt(episodeNumber || "1", 10);
    try {
      const serverTree = await enumerateAniNekoServers(targetTitle, epNum);
      return {
        statusCode: 200,
        headers: responseHeaders,
        body: JSON.stringify({
          success: true,
          provider: "anineko",
          title: targetTitle,
          episodeNumber: epNum,
          modes: serverTree.modes,
          servers: serverTree.servers
        }),
      };
    } catch (err) {
      return {
        statusCode: 500,
        headers: responseHeaders,
        body: JSON.stringify({ success: false, error: err.message }),
      };
    }
  }

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

    if (!targetTitle && malId && malId !== "0" && malId !== "") {
      try {
        console.log(`[Fallback API] Title missing. Resolving MAL ID ${malId} via Jikan API...`);
        const jikanRes = await fetch(`https://api.jikan.moe/v4/anime/${malId}`);
        if (jikanRes.ok) {
          const jikanJson = await jikanRes.json();
          targetTitle = jikanJson?.data?.title || jikanJson?.data?.title_english;
          console.log(`[Fallback API] Jikan API resolved title: "${targetTitle}"`);
        }
      } catch (e) {
        console.warn(`[Fallback API] Jikan API title resolution failed: ${e.message}`);
      }
    }

    // 1. Check if custom scraper should handle Gogoanime
    if (selectedProvider === "gogoanime" && targetTitle) {
      try {
        console.log(`[Fallback API] Invoking custom AniNeko scraper for title: "${targetTitle}" (mode: ${mode || "sub"})...`);
        sources = await scrapeAniNeko(targetTitle, epNum, mode || "sub", server);
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
    if ((!sources || !sources.sources || sources.sources.length === 0) && targetTitle) {
      try {
        console.log(`[Fallback API] Searching title "${targetTitle}" on provider ${selectedProvider}...`);
        
        const resolveTitleSearch = async () => {
          const providerInstance = selectedProvider === "animepahe" ? new ANIME.AnimePahe() : new ANIME.Hianime();
          if (selectedProvider === "animepahe") {
            providerInstance.baseUrl = "https://animepahe.ru";
          }
          const searchResults = await providerInstance.search(targetTitle);
          
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
