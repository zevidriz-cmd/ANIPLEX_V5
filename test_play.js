

const BASE_URL = "https://aniplex-proxy.f1886391.workers.dev/api/v2";

async function test() {
  const animeId = "8725";
  console.log(`Fetching episodes for anime ${animeId}...`);
  const epRes = await fetch(`${BASE_URL}/episodes/${animeId}`);
  const epJson = await epRes.json();
  if (!epJson.success) {
    console.error("Failed to fetch episodes:", epJson);
    return;
  }
  
  const episodes = epJson.data.episodes;
  console.log(`Found ${episodes.length} episodes.`);
  
  // Let's test episode 4
  const ep4 = episodes.find(e => e.number === 4);
  if (!ep4) {
    console.error("Episode 4 not found!");
    return;
  }
  
  console.log(`Episode 4 ID: ${ep4.episodeId}`);
  
  // Now fetch sources
  console.log(`Fetching stream sources for episode 4...`);
  const sourcesRes = await fetch(`${BASE_URL}/episode/sources?animeEpisodeId=${encodeURIComponent(ep4.episodeId)}&server=hd-1&category=sub`);
  const sourcesJson = await sourcesRes.json();
  console.log("Sources Response:", JSON.stringify(sourcesJson, null, 2));
}

test().catch(console.error);
