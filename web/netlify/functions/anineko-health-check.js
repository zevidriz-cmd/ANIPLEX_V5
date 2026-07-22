import { scrapeAniNeko } from "./fallback-stream.js";

const FIRESTORE_DOC_URL = "https://firestore.googleapis.com/v1/projects/aniplex-app-f923b/databases/(default)/documents/system/anineko_health_check";

/**
 * Reads persisted state from Firestore via REST API
 */
async function getFirestoreState() {
  try {
    const res = await fetch(FIRESTORE_DOC_URL);
    if (!res.ok) return { consecutiveFailures: 0, alertSent: false };
    const data = await res.json();
    const fields = data.fields || {};
    return {
      consecutiveFailures: parseInt(fields.consecutiveFailures?.integerValue || "0", 10),
      alertSent: fields.alertSent?.booleanValue === true,
      lastStatus: fields.lastStatus?.stringValue || "Unknown",
      lastChecked: fields.lastChecked?.stringValue || null
    };
  } catch (e) {
    console.error("[Health Check] Error reading Firestore state:", e.message);
    return { consecutiveFailures: 0, alertSent: false };
  }
}

/**
 * Writes updated state to Firestore via REST API
 */
async function updateFirestoreState(state) {
  try {
    const body = {
      fields: {
        consecutiveFailures: { integerValue: state.consecutiveFailures },
        alertSent: { booleanValue: state.alertSent },
        lastStatus: { stringValue: state.lastStatus },
        lastChecked: { stringValue: new Date().toISOString() },
        lastFailureReason: { stringValue: state.lastFailureReason || "None" }
      }
    };
    await fetch(FIRESTORE_DOC_URL, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
  } catch (e) {
    console.error("[Health Check] Error writing Firestore state:", e.message);
  }
}

/**
 * Sends a Telegram notification via Telegram Bot API
 */
async function sendTelegramAlert(text) {
  const token = process.env.TELEGRAM_BOT_TOKEN;
  const chatId = process.env.TELEGRAM_CHAT_ID;

  if (!token || !chatId) {
    console.warn("[Health Check] TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID not configured in environment.");
    return false;
  }

  try {
    const telegramUrl = `https://api.telegram.org/bot${token}/sendMessage`;
    const res = await fetch(telegramUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        chat_id: chatId,
        text: text,
        parse_mode: "Markdown"
      })
    });
    const data = await res.json();
    if (!data.ok) {
      console.error("[Health Check] Telegram API error:", data);
      return false;
    }
    console.log("[Health Check] Telegram alert sent successfully!");
    return true;
  } catch (e) {
    console.error("[Health Check] Failed to send Telegram alert:", e.message);
    return false;
  }
}

/**
 * Validates an extracted .m3u8 stream URL via HTTP GET and body #EXTM3U check
 */
async function validateStreamUrl(streamUrl) {
  try {
    const res = await fetch(streamUrl, {
      method: "GET",
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
        "Referer": "https://anineko.to/"
      }
    });

    if (res.status !== 200 && res.status !== 206) {
      return { valid: false, error: `HTTP status ${res.status}` };
    }

    const bodyText = await res.text();
    const firstLine = bodyText.trim().split("\n")[0].trim();

    if (!firstLine.startsWith("#EXTM3U")) {
      return { valid: false, error: `Invalid playlist body (first line: '${firstLine.substring(0, 30)}')` };
    }

    return { valid: true, error: null, firstLine };
  } catch (e) {
    return { valid: false, error: `Fetch network error: ${e.message}` };
  }
}

export async function runHealthCheck(event, context) {
  console.log(`[Health Check] Invoked at ${new Date().toISOString()}`);

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

  const rawUrl = event.rawUrl || `https://anistream-web.netlify.app${event.path || ''}`;
  let searchParams = {};
  try {
    const parsedUrl = new URL(rawUrl);
    searchParams = Object.fromEntries(parsedUrl.searchParams.entries());
  } catch (e) {}

  const queryParams = { ...(event.queryStringParameters || {}), ...searchParams };
  const secretKey = process.env.HEALTH_CHECK_SECRET;
  
  // Security guard: Only honor test override params if provided key matches process.env.HEALTH_CHECK_SECRET
  const isAuthorizedTest = secretKey && queryParams.key === secretKey;
  const isSimulatedFailure = isAuthorizedTest && queryParams.testFailure === "true";
  const isSimulatedRecovery = isAuthorizedTest && queryParams.testRecovery === "true";

  const testTitle = queryParams.title || "One Piece";
  const testEpisode = parseInt(queryParams.episode || "1", 10);
  const modesToTest = ["sub", "hsub"];

  const results = {};
  let overallHealthy = true;
  let failureReason = null;
  let failedMode = null;

  if (isSimulatedFailure) {
    console.log("[Health Check] Simulation Mode: Authorized testFailure flag set.");
    overallHealthy = false;
    failedMode = "hsub";
    failureReason = "Simulated test failure for 2-strike alert verification";
  } else {
    for (const mode of modesToTest) {
      console.log(`[Health Check] Testing title="${testTitle}", ep=${testEpisode}, mode="${mode}"...`);
      try {
        const scrapeResult = await scrapeAniNeko(testTitle, testEpisode, mode);

        if (!scrapeResult || !scrapeResult.sources || scrapeResult.sources.length === 0) {
          overallHealthy = false;
          failedMode = mode;
          failureReason = `Scraper returned no valid stream sources for mode ${mode}`;
          results[mode] = { success: false, error: failureReason };
          break;
        }

        const streamUrl = scrapeResult.sources[0].url;
        console.log(`[Health Check] Mode "${mode}" extracted URL: ${streamUrl}`);

        const validation = await validateStreamUrl(streamUrl);
        if (!validation.valid) {
          overallHealthy = false;
          failedMode = mode;
          failureReason = `Stream URL validation failed for mode ${mode}: ${validation.error}`;
          results[mode] = { success: false, url: streamUrl, error: validation.error };
          break;
        }

        results[mode] = { success: true, url: streamUrl, status: 200, firstLine: validation.firstLine };
      } catch (e) {
        overallHealthy = false;
        failedMode = mode;
        failureReason = `Exception in scraper for mode ${mode}: ${e.message}`;
        results[mode] = { success: false, error: failureReason };
        break;
      }
    }
  }

  // Fetch state from Firestore
  const prevState = await getFirestoreState();
  let consecutiveFailures = prevState.consecutiveFailures;
  let alertSent = prevState.alertSent;
  let telegramDispatched = false;
  let telegramMessage = null;

  if (isSimulatedRecovery) {
    console.log("[Health Check] Simulation Mode: Authorized testRecovery flag set.");
    overallHealthy = true;
  }

  if (overallHealthy) {
    consecutiveFailures = 0;
    if (alertSent) {
      telegramMessage = `✅ *AniNeko Scraper Recovered*\n\nThe scraper health check passed successfully. Streaming functionality is operating normally again.\n\n• *Test Title*: ${testTitle} (Ep ${testEpisode})\n• *Timestamp*: ${new Date().toISOString()}`;
      telegramDispatched = await sendTelegramAlert(telegramMessage);
      alertSent = false;
    }
  } else {
    consecutiveFailures += 1;
    if (consecutiveFailures >= 2 && !alertSent) {
      telegramMessage = `⚠️ *AniNeko Scraper Alert*\n\nThe AniNeko fallback scraper check has failed **2 consecutive times** in a row.\n\n• *Failed Mode*: ${failedMode}\n• *Failure Reason*: ${failureReason}\n• *Timestamp*: ${new Date().toISOString()}\n\nPlease check Netlify function logs for details.`;
      telegramDispatched = await sendTelegramAlert(telegramMessage);
      alertSent = true;
    }
  }

  // Update Firestore state
  const newState = {
    consecutiveFailures,
    alertSent,
    lastStatus: overallHealthy ? "Healthy" : "Broken",
    lastFailureReason: overallHealthy ? null : failureReason
  };
  await updateFirestoreState(newState);

  return {
    statusCode: 200,
    headers: responseHeaders,
    body: JSON.stringify({
      status: newState.lastStatus,
      consecutiveFailures: newState.consecutiveFailures,
      alertSent: newState.alertSent,
      telegramDispatched,
      telegramMessage,
      failureReason,
      results,
      timestamp: new Date().toISOString()
    })
  };
}

export const handler = runHealthCheck;
