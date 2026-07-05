
async function test() {
  const url = "https://megaplay.buzz/stream/getSources?id=87552";
  console.log(`Fetching directly with X-Requested-With: ${url}`);
  const res = await fetch(url, {
    headers: {
      'Referer': 'https://megaplay.buzz/',
      'X-Requested-With': 'XMLHttpRequest',
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36',
      'accept': '*/*',
      'accept-language': 'en-US,en;q=0.9',
    }
  });
  console.log(`Status: ${res.status}`);
  const text = await res.text();
  console.log("Response:", text);
}
test().catch(console.error);
