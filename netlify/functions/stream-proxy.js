export async function handler(event, context) {
  const responseHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': '*',
    'Access-Control-Allow-Methods': 'GET, HEAD, POST, OPTIONS',
  };

  if (event.httpMethod === 'OPTIONS') {
    return {
      statusCode: 200,
      headers: responseHeaders,
      body: '',
    };
  }

  const path = event.path.replace(/^\/\.netlify\/functions\/stream-proxy\/?/, '');
  if (!path) {
    return {
      statusCode: 200,
      headers: {
        ...responseHeaders,
        'Content-Type': 'text/plain',
      },
      body: 'AniStream Netlify Proxy is running!',
    };
  }

  // Reconstruct target URL with all query parameters intact
  const queryParams = new URLSearchParams(event.queryStringParameters);
  const queryStr = queryParams.toString();
  const targetUrl = 'https://' + path + (queryStr ? '?' + queryStr : '');

  try {
    const fetchHeaders = {
      'accept': '*/*',
      'accept-language': 'en-US,en;q=0.9',
      'sec-ch-ua': '"Not A(Brand";v="99", "Google Chrome";v="121", "Chromium";v="121"',
      'sec-ch-ua-mobile': '?0',
      'sec-ch-ua-platform': '"Windows"',
      'sec-fetch-dest': 'empty',
      'sec-fetch-mode': 'cors',
      'sec-fetch-site': 'cross-site',
      'Referer': 'https://megaplay.buzz/',
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36'
    };

    const response = await fetch(targetUrl, { headers: fetchHeaders });
    if (!response.ok) {
      return {
        statusCode: response.status,
        headers: responseHeaders,
        body: `Proxy failed to fetch target: ${response.statusText}`,
      };
    }

    const contentType = response.headers.get('content-type') || '';
    const isPlaylist = targetUrl.split('?')[0].endsWith('.m3u8') || contentType.includes('mpegurl') || contentType.includes('application/x-mpegURL');

    if (isPlaylist) {
      let bodyText = await response.text();
      
      // Rewrite absolute URLs:
      // If it contains .m3u8, rewrite to point to this Netlify function
      // Otherwise, rewrite to point to the Cloudflare Worker proxy
      bodyText = bodyText.replace(/https:\/\/([a-zA-Z0-9.-]+\.[a-zA-Z]{2,})(\/[^\s"']*)/g, (m, domain, urlPath) => {
        if (urlPath.includes('.m3u8')) {
          return `/.netlify/functions/stream-proxy/${domain}${urlPath}`;
        } else {
          return `https://anistream-proxy.f1886391.workers.dev/${domain}${urlPath}`;
        }
      });

      return {
        statusCode: 200,
        headers: {
          ...responseHeaders,
          'Content-Type': 'application/vnd.apple.mpegurl',
        },
        body: bodyText,
      };
    }

    // Binary / Segment fallback
    const arrayBuffer = await response.arrayBuffer();
    return {
      statusCode: 200,
      headers: {
        ...responseHeaders,
        'Content-Type': contentType || 'application/octet-stream',
      },
      body: Buffer.from(arrayBuffer).toString('base64'),
      isBase64Encoded: true,
    };

  } catch (err) {
    return {
      statusCode: 500,
      headers: responseHeaders,
      body: `Proxy error: ${err.message}`,
    };
  }
}
