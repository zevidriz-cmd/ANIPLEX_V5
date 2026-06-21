const fs = require('fs');

async function fetchAll() {
    let map = {};
    let totalPages = 1;
    for(let page = 1; page <= totalPages; page++) {
        try {
            const res = await fetch("http://anikotoapi.site/recent-anime?page=" + page + "&per_page=100");
            const json = await res.json();
            if (page === 1) totalPages = json.pagination.total_pages;
            
            for(const item of json.data) {
                if (item.mal_id) {
                    map[String(item.mal_id)] = item.id;
                }
            }
            console.log("Fetched page " + page + "/" + totalPages);
        } catch (e) {
            console.error(e);
        }
    }
    fs.writeFileSync('anikoto_map.json', JSON.stringify(map));
    console.log('Saved ' + Object.keys(map).length + ' mappings.');
}

fetchAll();
