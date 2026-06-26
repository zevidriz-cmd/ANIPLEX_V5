import React, { useEffect, useState, useRef } from "react";
import { useSearchParams, Link } from "react-router-dom";
import { search, getSuggestions, getGenre, filterAnime, resolveMAL, getAnimeDetail, getHome } from "../services/api";
import { useProfile } from "../context/ProfileContext";
import AnimeCard from "../components/AnimeCard";
import { GridShimmer } from "../components/Shimmer";
import { Search as SearchIcon, Filter, X, ChevronLeft, ChevronRight, SlidersHorizontal, MoreVertical, Trash2, History, Play } from "lucide-react";

const JIKAN_GENRE_MAPPING = {
  "Action": 1,
  "Adventure": 2,
  "Comedy": 4,
  "Drama": 8,
  "Fantasy": 10,
  "Horror": 14,
  "Mystery": 7,
  "Romance": 22,
  "Sci-Fi": 24,
  "Slice of Life": 36,
  "Sports": 30,
  "Supernatural": 37,
  "Thriller": 41,
  "Music": 19,
  "Mecha": 18,
  "Psychological": 40
};

const LOCAL_ACCURATE_MAPPINGS = [
  { keywords: ["onepiece", "one piece", "the one piece"], malId: 21, id: "1642" },
  { keywords: ["naruto"], malId: 20, id: "958" },
  { keywords: ["naruto shippuden", "narutoshippuden", "shippuden"], malId: 1735, id: "1498" },
  { keywords: ["bleach"], malId: 269, id: "1057" },
  { keywords: ["hunter x hunter", "hunterxhunter", "hxh"], malId: 11061, id: "68" },
  { keywords: ["black clover", "blackclover"], malId: 34572, id: "2121" },
  { keywords: ["fairy tail", "fairytail"], malId: 6702, id: "489" },
  { keywords: ["dragon ball z", "dragonballz", "dbz"], malId: 813, id: "1456" },
  { keywords: ["dragon ball super", "dragonballsuper", "dbs"], malId: 30694, id: "132" },
  { keywords: ["boruto"], malId: 34566, id: "4587" }
];

export default function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const genreParam = searchParams.get("genre") || "";
  const { activeProfile } = useProfile();

  const [keyword, setKeyword] = useState("");
  const [results, setResults] = useState([]);
  const [suggestions, setSuggestions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [genreHeroAnime, setGenreHeroAnime] = useState(null);
  const [fallbackType, setFallbackType] = useState(""); // "", "anilist", "offline"
  
  // Search History State
  const [searchHistory, setSearchHistory] = useState([]);
  const [activeSearchMenuIndex, setActiveSearchMenuIndex] = useState(-1);
  const inputRef = useRef(null);

  // Pagination
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [hasNextPage, setHasNextPage] = useState(false);

  // Filters state
  const [showFilters, setShowFilters] = useState(false);
  const [type, setType] = useState("");
  const [status, setStatus] = useState("");
  const [selectedGenres, setSelectedGenres] = useState(genreParam ? [genreParam] : []);
  const [sort, setSort] = useState("");

  const searchTimeoutRef = useRef(null);

  // Load search history when activeProfile changes
  useEffect(() => {
    if (activeProfile) {
      const historyKey = `anistream_search_history_${activeProfile.id}`;
      const stored = JSON.parse(localStorage.getItem(historyKey) || "[]");
      setSearchHistory(stored);
    }
  }, [activeProfile]);

  const saveSearchQuery = (query) => {
    if (!query || !query.trim() || !activeProfile) return;
    const trimmed = query.trim();
    const historyKey = `anistream_search_history_${activeProfile.id}`;
    let current = JSON.parse(localStorage.getItem(historyKey) || "[]");
    
    current = current.filter(x => x !== trimmed);
    current.unshift(trimmed);
    current = current.slice(0, 5); // limit to 5 recent queries
    
    localStorage.setItem(historyKey, JSON.stringify(current));
    setSearchHistory(current);
  };

  const deleteSearchQuery = (queryToDelete) => {
    if (!activeProfile) return;
    const historyKey = `anistream_search_history_${activeProfile.id}`;
    let current = JSON.parse(localStorage.getItem(historyKey) || "[]");
    current = current.filter(x => x !== queryToDelete);
    localStorage.setItem(historyKey, JSON.stringify(current));
    setSearchHistory(current);
  };

  // Close search menu on click outside
  useEffect(() => {
    function handleClickOutside(event) {
      if (activeSearchMenuIndex !== -1 && !event.target.closest(".recent-search-options-wrapper")) {
        setActiveSearchMenuIndex(-1);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [activeSearchMenuIndex]);

  const handleToggleSearchMenu = (e, index) => {
    e.preventDefault();
    e.stopPropagation();
    setActiveSearchMenuIndex(activeSearchMenuIndex === index ? -1 : index);
  };

  // Sync genre parameter from URL if clicked from Home genre chips
  useEffect(() => {
    if (genreParam) {
      setSelectedGenres([genreParam]);
      setKeyword("");
      loadGenreData(genreParam, 1);
    }
  }, [genreParam]);

  const loadGenreData = async (genre, p) => {
    setLoading(true);
    setSuggestions([]);
    setGenreHeroAnime(null);

    const genreId = JIKAN_GENRE_MAPPING[genre];
    if (!genreId) {
      try {
        const data = await getGenre(genre, p);
        setResults(data?.animes || []);
        setPage(data?.currentPage || 1);
        setTotalPages(data?.totalPages || 1);
        setHasNextPage(data?.hasNextPage || false);
      } catch (e) {
        console.error(e);
      } finally {
        setLoading(false);
      }
      return;
    }

    try {
      const res = await fetch(`https://api.jikan.moe/v4/anime?genres=${genreId}&order_by=score&sort=desc&page=${p}&limit=16`);
      if (!res.ok) throw new Error("Jikan genre fetch failed");
      const json = await res.json();
      const jikanAnimes = json.data || [];

      const resolvePromises = jikanAnimes.map(async (item) => {
        try {
          const resolveData = await resolveMAL(item.mal_id);
          if (resolveData && resolveData.anikotoId) {
            const detailData = await getAnimeDetail(resolveData.anikotoId);
            const info = detailData?.anime?.info;
            if (info) {
              return {
                id: info.id,
                name: info.name,
                poster: info.poster,
                duration: info.stats?.duration || info.duration || "",
                type: info.stats?.type || info.type || "TV",
                rating: info.stats?.rating || info.rating || "",
                episodes: info.stats?.episodes || info.episodes || { sub: 0, dub: 0 },
                description: info.description || item.synopsis || ""
              };
            }
          }
        } catch {
          // ignore
        }
        return null;
      });

      const resolvedAnimes = (await Promise.all(resolvePromises)).filter(Boolean);

      if (p === 1 && resolvedAnimes.length > 0) {
        setGenreHeroAnime(resolvedAnimes[0]);
        setResults(resolvedAnimes.slice(1));
      } else {
        setResults(resolvedAnimes);
      }

      setPage(p);
      setTotalPages(json.pagination?.last_visible_page || 1);
      setHasNextPage(json.pagination?.has_next_page || false);
    } catch (err) {
      console.error("Failed to load Jikan genre data:", err);
      try {
        const data = await getGenre(genre, p);
        setResults(data?.animes || []);
        setPage(data?.currentPage || 1);
        setTotalPages(data?.totalPages || 1);
        setHasNextPage(data?.hasNextPage || false);
      } catch (fallbackErr) {
        console.error(fallbackErr);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleSearchSubmit = async (p = 1, searchKeyword = keyword) => {
    const queryToSearch = typeof searchKeyword === "string" ? searchKeyword : keyword;
    
    // Check if any filters are active
    const hasFilters = !!(type || status || selectedGenres.length > 0 || sort);
    const hasKeyword = !!(queryToSearch && queryToSearch.trim());

    if (!hasKeyword && !hasFilters) {
      setResults([]);
      return;
    }

    setLoading(true);
    setSuggestions([]);
    inputRef.current?.blur(); // Blur search input to dismiss mobile keyboard!

    try {
      let data;
      if (hasFilters && !hasKeyword) {
        // Fetch from filter API
        data = await filterAnime({
          type: type || undefined,
          status: status || undefined,
          genres: selectedGenres.length > 0 ? selectedGenres.join(",") : undefined,
          sort: sort || undefined,
          page: p
        });
      } else {
        // Fetch from search API
        data = await search(queryToSearch || "all", p);
        if (hasFilters && data?.animes) {
          // Apply client-side filter for type if it is set
          let filtered = [...data.animes];
          if (type) {
            filtered = filtered.filter(a => a.type?.toLowerCase() === type.toLowerCase());
          }
          data.animes = filtered;
        }
      }

      let finalAnimes = data?.animes || [];

      // Smart enrichment client-side (only on page 1)
      if (hasKeyword && p === 1) {
        const cleanedQuery = queryToSearch.trim().toLowerCase();
        
        // 1. Gather potential matched IDs
        const matchedIds = [];
        
        // A. Check local accurate mappings first
        for (const entry of LOCAL_ACCURATE_MAPPINGS) {
          if (entry.keywords.some(kw => cleanedQuery.includes(kw) || kw.includes(cleanedQuery))) {
            if (!matchedIds.includes(entry.id)) {
              matchedIds.push(entry.id);
            }
          }
        }

        // B. Dynamic Jikan (MAL) search fallback to handle other shows
        try {
          // Only trigger Jikan lookup if we didn't find local match or want additional results
          const jikanRes = await fetch(`https://api.jikan.moe/v4/anime?q=${encodeURIComponent(queryToSearch)}&limit=3`);
          if (jikanRes.ok) {
            const jikanJson = await jikanRes.json();
            const malIds = (jikanJson.data || []).map(item => item.mal_id);
            
            // Resolve MAL IDs to streaming IDs using our backend resolveMAL endpoint
            const resolvePromises = malIds.map(async (malId) => {
              try {
                const res = await resolveMAL(malId);
                return res && res.anikotoId ? res.anikotoId : null;
              } catch {
                return null;
              }
            });
            const resolvedIds = await Promise.all(resolvePromises);
            for (const rid of resolvedIds) {
              if (rid && !matchedIds.includes(rid)) {
                matchedIds.push(rid);
              }
            }
          }
        } catch (jikanErr) {
          console.warn("Jikan search enrichment failed:", jikanErr);
        }

        // 2. Fetch details for matched IDs that are NOT already in the results list
        const idsToFetch = matchedIds.filter(id => !finalAnimes.some(a => String(a.id) === String(id)));
        
        if (idsToFetch.length > 0) {
          const detailPromises = idsToFetch.map(async (id) => {
            try {
              const detail = await getAnimeDetail(id);
              const info = detail?.anime?.info;
              if (info) {
                return {
                  id: info.id,
                  name: info.name,
                  poster: info.poster,
                  duration: info.stats?.duration || info.duration || "",
                  type: info.stats?.type || info.type || "TV",
                  rating: info.stats?.rating || info.rating || "",
                  episodes: info.stats?.episodes || info.episodes || { sub: 0, dub: 0 }
                };
              }
            } catch (err) {
              console.warn(`Failed to fetch details for matched anime ${id}:`, err);
            }
            return null;
          });

          const enrichedAnimes = (await Promise.all(detailPromises)).filter(Boolean);
          
          // Prepend enriched animes to the results
          finalAnimes = [...enrichedAnimes, ...finalAnimes];
        }
      }

      setResults(finalAnimes);
      setPage(data?.currentPage || 1);
      setTotalPages(data?.totalPages || 1);
      setHasNextPage(data?.hasNextPage || false);
      setFallbackType(""); // Success, clear fallback type

      if (hasKeyword) {
        saveSearchQuery(queryToSearch);
      }
    } catch (e) {
      console.error("Search error:", e);
      if (hasKeyword) {
        // 1. Try AniList GraphQL search fallback
        try {
          const graphqlQuery = `
            query ($search: String) {
              Page(page: 1, perPage: 12) {
                media(search: $search, type: ANIME) {
                  idMal
                }
              }
            }
          `;
          
          const anilistRes = await fetch("https://graphql.anilist.co", {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              "Accept": "application/json"
            },
            body: JSON.stringify({
              query: graphqlQuery,
              variables: { search: queryToSearch }
            })
          });

          if (anilistRes.ok) {
            const anilistJson = await anilistRes.json();
            const mediaList = anilistJson.data?.Page?.media || [];
            const malIds = mediaList.map(m => m.idMal).filter(Boolean);

            if (malIds.length > 0) {
              // Resolve MAL IDs in parallel to our database IDs
              const resolvePromises = malIds.map(async (malId) => {
                try {
                  const res = await resolveMAL(malId);
                  return res && res.anikotoId ? res.anikotoId : null;
                } catch {
                  return null;
                }
              });
              const resolvedIds = (await Promise.all(resolvePromises)).filter(Boolean);

              if (resolvedIds.length > 0) {
                // Fetch details in parallel
                const detailPromises = resolvedIds.map(async (id) => {
                  try {
                    const detail = await getAnimeDetail(id);
                    const info = detail?.anime?.info;
                    if (info) {
                      return {
                        id: info.id,
                        name: info.name,
                        poster: info.poster,
                        duration: info.stats?.duration || info.duration || "",
                        type: info.stats?.type || info.type || "TV",
                        rating: info.stats?.rating || info.rating || "",
                        episodes: info.stats?.episodes || info.episodes || { sub: 0, dub: 0 }
                      };
                    }
                  } catch (err) {
                    console.warn(`Fallback detail enrichment failed for ID ${id}:`, err);
                  }
                  return null;
                });

                let resolvedAnimes = (await Promise.all(detailPromises)).filter(Boolean);

                if (hasFilters) {
                  if (type) {
                    resolvedAnimes = resolvedAnimes.filter(a => a.type?.toLowerCase() === type.toLowerCase());
                  }
                }

                if (resolvedAnimes.length > 0) {
                  setResults(resolvedAnimes);
                  setPage(1);
                  setTotalPages(1);
                  setHasNextPage(false);
                  setFallbackType("anilist");
                  saveSearchQuery(queryToSearch);
                  return;
                }
              }
            }
          }
        } catch (anilistErr) {
          console.warn("AniList search backup failed:", anilistErr);
        }

        // 2. Secondary Fallback: Offline cached home list catalog
        try {
          const cleanedQuery = queryToSearch.trim().toLowerCase();
          let fallbackResults = [];

          // Check local mappings first for matching keyword
          const matchedLocalIds = [];
          for (const entry of LOCAL_ACCURATE_MAPPINGS) {
            if (entry.keywords.some(kw => cleanedQuery.includes(kw) || kw.includes(cleanedQuery))) {
              matchedLocalIds.push(entry.id);
            }
          }

          // Query cached home API lists
          try {
            const homeData = await getHome();
            const allHomeAnimes = [
              ...(homeData?.spotlightAnimes || []),
              ...(homeData?.trendingAnimes || []),
              ...(homeData?.topAiringAnimes || []),
              ...(homeData?.recentlyAddedAnimes || []),
              ...(homeData?.mostPopularAnimes || []),
              ...(homeData?.topUpcomingAnimes || [])
            ];

            for (const anime of allHomeAnimes) {
              const matchesTitle = anime.name?.toLowerCase().includes(cleanedQuery) || 
                                   anime.jname?.toLowerCase().includes(cleanedQuery);
              if (matchesTitle && !fallbackResults.some(r => String(r.id) === String(anime.id))) {
                fallbackResults.push({
                  id: anime.id,
                  name: anime.name,
                  poster: anime.poster,
                  duration: anime.duration || anime.stats?.duration || "",
                  type: anime.type || anime.stats?.type || "TV",
                  rating: anime.rate || anime.rating || "",
                  episodes: anime.episodes || anime.stats?.episodes || { sub: 0, dub: 0 }
                });
              }
            }
          } catch (homeErr) {
            console.warn("Failed to fetch home lists for search fallback:", homeErr);
          }

          // For any local mappings that weren't in the home lists, enrich them directly
          const idsToFetch = matchedLocalIds.filter(id => !fallbackResults.some(r => String(r.id) === String(id)));
          if (idsToFetch.length > 0) {
            const detailPromises = idsToFetch.map(async (id) => {
              try {
                const detail = await getAnimeDetail(id);
                const info = detail?.anime?.info;
                if (info) {
                  return {
                    id: info.id,
                    name: info.name,
                    poster: info.poster,
                    duration: info.stats?.duration || info.duration || "",
                    type: info.stats?.type || info.type || "TV",
                    rating: info.stats?.rating || info.rating || "",
                    episodes: info.stats?.episodes || info.episodes || { sub: 0, dub: 0 }
                  };
                }
              } catch (err) {
                console.warn(`Fallback detail enrichment failed for ID ${id}:`, err);
              }
              return null;
            });
            const enrichedLocal = (await Promise.all(detailPromises)).filter(Boolean);
            fallbackResults = [...enrichedLocal, ...fallbackResults];
          }

          // Apply client-side filters if active
          if (hasFilters) {
            if (type) {
              fallbackResults = fallbackResults.filter(a => a.type?.toLowerCase() === type.toLowerCase());
            }
          }

          if (fallbackResults.length > 0) {
            setResults(fallbackResults);
            setPage(1);
            setTotalPages(1);
            setHasNextPage(false);
            setFallbackType("offline");
            saveSearchQuery(queryToSearch);
            return;
          }
        } catch (fallbackErr) {
          console.error("Critical fallback search error:", fallbackErr);
        }
      }
      setResults([]);
      setFallbackType("");
    } finally {
      setLoading(false);
    }
  };

  // Debounced autocomplete suggestions
  const handleKeywordChange = (e) => {
    const val = e.target.value;
    setKeyword(val);

    if (searchTimeoutRef.current) clearTimeout(searchTimeoutRef.current);

    if (!val.trim()) {
      setSuggestions([]);
      return;
    }

    searchTimeoutRef.current = setTimeout(async () => {
      try {
        const data = await getSuggestions(val);
        setSuggestions(data?.suggestions || []);
      } catch (err) {
        console.warn(err);
      }
    }, 400);
  };

  useEffect(() => {
    // If we have default filters on load, perform search
    if (!genreParam && keyword) {
      handleSearchSubmit(1);
    }
  }, []);

  const handleKeyPress = (e) => {
    if (e.key === "Enter") {
      handleSearchSubmit(1);
    }
  };

  const clearFilters = () => {
    setType("");
    setStatus("");
    setSelectedGenres([]);
    setSort("");
    setSearchParams({});
    setResults([]);
  };

  const genresList = [
    "Action", "Adventure", "Comedy", "Drama", "Fantasy", 
    "Horror", "Mystery", "Romance", "Sci-Fi", "Slice of Life", 
    "Sports", "Supernatural", "Thriller", "Music", "Mecha", "Psychological"
  ];

  return (
    <div className="search-page fade-in container">
      <div className="search-header-panel">
        <div className="search-bar-wrapper">
          <SearchIcon className="search-icon-svg" size={20} />
          <input 
            ref={inputRef}
            type="text" 
            placeholder="Search anime title, movies, ova..."
            className="search-input-box"
            value={keyword}
            onChange={handleKeywordChange}
            onKeyPress={handleKeyPress}
            autoFocus
          />
          {keyword && (
            <button className="clear-search-btn" onClick={() => { setKeyword(""); setSuggestions([]); }}>
              <X size={18} />
            </button>
          )}

          {/* Autocomplete Suggestions Dropdown */}
          {suggestions.length > 0 && (
            <div className="suggestions-dropdown">
              {suggestions.map((item) => (
                <Link 
                  key={item.id} 
                  to={`/anime/${item.id}`} 
                  className="suggestion-item"
                  onClick={() => {
                    saveSearchQuery(item.name);
                    inputRef.current?.blur();
                    setSuggestions([]);
                  }}
                >
                  <div className="suggestion-poster">
                    <img src={item.poster} alt={item.name} />
                  </div>
                  <div className="suggestion-info">
                    <h4 className="suggestion-title">{item.name}</h4>
                    <p className="suggestion-meta">
                      {item.moreInfo?.join(" • ")}
                    </p>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </div>

        <button 
          className={`btn btn-secondary filter-toggle-btn ${showFilters ? "active" : ""}`}
          onClick={() => setShowFilters(!showFilters)}
        >
          <SlidersHorizontal size={18} /> Filters
        </button>

        <button className="btn btn-primary" onClick={() => handleSearchSubmit(1)}>
          Search
        </button>
      </div>

      <div className="search-body-panel">
        {/* Filters Sidebar */}
        {showFilters && (
          <aside className="filters-sidebar fade-in">
            <div className="filter-section">
              <h3>Type</h3>
              <div className="filter-options">
                {["", "tv", "movie", "ova", "ona", "special"].map((t) => (
                  <button 
                    key={t}
                    onClick={() => setType(t)}
                    className={type === t ? "active" : ""}
                  >
                    {t ? t.toUpperCase() : "All"}
                  </button>
                ))}
              </div>
            </div>

            <div className="filter-section">
              <h3>Status</h3>
              <div className="filter-options">
                {["", "airing", "completed", "upcoming"].map((s) => (
                  <button 
                    key={s}
                    onClick={() => setStatus(s)}
                    className={status === s ? "active" : ""}
                  >
                    {s ? s.charAt(0).toUpperCase() + s.slice(1) : "All"}
                  </button>
                ))}
              </div>
            </div>

            <div className="filter-section">
              <h3>Genres</h3>
              <div className="filter-genres-grid">
                {genresList.map((g) => {
                  const isSel = selectedGenres.includes(g);
                  return (
                    <button
                      key={g}
                      onClick={() => {
                        if (isSel) {
                          setSelectedGenres(selectedGenres.filter(x => x !== g));
                        } else {
                          setSelectedGenres([g]); // single genre select for simple API query compatibility
                          setKeyword("");
                          setSearchParams({ genre: g });
                        }
                      }}
                      className={isSel ? "active" : ""}
                    >
                      {g}
                    </button>
                  );
                })}
              </div>
            </div>

            <button className="btn btn-secondary clear-filters-btn" onClick={clearFilters}>
              Reset Filters
            </button>
          </aside>
        )}

        {/* Results Columns */}
        <div className="search-results-column">
          {fallbackType && (
            <div className={`search-fallback-banner ${fallbackType}`}>
              <span className="fallback-badge">
                {fallbackType === "anilist" ? "AniList Backup" : "Offline Catalog"}
              </span>
              <p>
                {fallbackType === "anilist" 
                  ? "Search server is experiencing downtime. Serving results via AniList backup."
                  : "Search server is experiencing downtime. Showing matches from popular and trending titles."}
              </p>
            </div>
          )}
          {loading ? (
            <GridShimmer count={8} />
          ) : (results.length > 0 || (genreParam && page === 1 && genreHeroAnime)) ? (
            <>
              {genreParam && page === 1 && genreHeroAnime && (
                <div className="genre-hero-banner" style={{
                  position: "relative",
                  width: "100%",
                  borderRadius: "12px",
                  overflow: "hidden",
                  marginBottom: "28px",
                  border: "1px solid var(--border)",
                  background: "#0a0a0a"
                }}>
                  <div style={{
                    position: "absolute",
                    inset: 0,
                    backgroundImage: `url(${genreHeroAnime.poster})`,
                    backgroundSize: "cover",
                    backgroundPosition: "center",
                    filter: "blur(40px) brightness(0.25)",
                    opacity: 0.5,
                    zIndex: 0
                  }} />

                  <div style={{
                    position: "relative",
                    zIndex: 1,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    padding: "32px 40px",
                    gap: "40px"
                  }} className="genre-hero-container">
                    <div style={{
                      flex: 1,
                      display: "flex",
                      flexDirection: "column",
                      gap: "12px",
                      textAlign: "left"
                    }}>
                      <span style={{
                        backgroundColor: "var(--primary)",
                        color: "white",
                        fontSize: "0.75rem",
                        fontWeight: "800",
                        padding: "4px 10px",
                        borderRadius: "4px",
                        width: "fit-content",
                        textTransform: "uppercase",
                        letterSpacing: "0.05em"
                      }}>
                        Featured {genreParam}
                      </span>
                      <h1 style={{
                        fontSize: "2.2rem",
                        fontWeight: "800",
                        color: "white",
                        lineHeight: "1.2",
                        margin: 0
                      }}>
                        {genreHeroAnime.name}
                      </h1>
                      <div style={{
                        display: "flex",
                        gap: "12px",
                        fontSize: "0.8rem",
                        color: "var(--text-secondary)",
                        fontWeight: "600"
                      }}>
                        <span>{genreHeroAnime.type}</span>
                        <span>•</span>
                        <span>{genreHeroAnime.duration}</span>
                        {genreHeroAnime.rating && (
                          <>
                            <span>•</span>
                            <span style={{ color: "#F5A623" }}>★ {genreHeroAnime.rating}</span>
                          </>
                        )}
                      </div>
                      <p style={{
                        fontSize: "0.95rem",
                        color: "var(--text-secondary)",
                        lineHeight: "1.6",
                        margin: "8px 0 0 0",
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        display: "-webkit-box",
                        WebkitLineClamp: 3,
                        WebkitBoxOrient: "vertical"
                      }}>
                        {genreHeroAnime.description.replace(/<[^>]*>/g, "")}
                      </p>
                      <div style={{ display: "flex", gap: "12px", marginTop: "12px" }}>
                        <Link 
                          to={`/anime/${genreHeroAnime.id}`} 
                          className="btn btn-primary" 
                          style={{ padding: "10px 24px" }}
                          onClick={() => saveSearchQuery(genreHeroAnime.name)}
                        >
                          <Play size={16} fill="currentColor" style={{ marginRight: "6px" }} /> Watch Now
                        </Link>
                      </div>
                    </div>

                    <div style={{
                      width: "180px",
                      height: "260px",
                      borderRadius: "8px",
                      overflow: "hidden",
                      boxShadow: "0 10px 25px rgba(0,0,0,0.5)",
                      border: "1px solid rgba(255,255,255,0.1)",
                      flexShrink: 0
                    }} className="genre-hero-poster-wrapper">
                      <img src={genreHeroAnime.poster} alt={genreHeroAnime.name} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
                    </div>
                  </div>
                </div>
              )}

              {results.length > 0 && (
                <div className="grid-layout">
                  {results.map((anime) => (
                    <AnimeCard 
                      key={anime.id}
                      id={anime.id}
                      name={anime.name}
                      poster={anime.poster}
                      type={anime.type}
                      episodes={anime.episodes}
                      rating={anime.rate}
                      onClick={() => saveSearchQuery(anime.name)}
                    />
                  ))}
                </div>
              )}

              {/* Pagination Controls */}
              {totalPages > 1 && (
                <div className="search-pagination flex-center">
                  <button 
                    className="btn btn-secondary pag-btn"
                    disabled={page === 1}
                    onClick={() => {
                      if (genreParam) loadGenreData(genreParam, page - 1);
                      else handleSearchSubmit(page - 1);
                    }}
                  >
                    <ChevronLeft size={16} /> Prev
                  </button>
                  <span className="page-lbl">Page {page} of {totalPages}</span>
                  <button 
                    className="btn btn-secondary pag-btn"
                    disabled={!hasNextPage}
                    onClick={() => {
                      if (genreParam) loadGenreData(genreParam, page + 1);
                      else handleSearchSubmit(page + 1);
                    }}
                  >
                    Next <ChevronRight size={16} />
                  </button>
                </div>
              )}
            </>
          ) : !keyword.trim() && searchHistory.length > 0 ? (
            <div className="recent-searches-container fade-in">
              <h3 className="recent-searches-title">Recent Searches</h3>
              <div className="recent-searches-list">
                {searchHistory.map((queryText, index) => (
                  <div key={index} className="recent-search-row">
                    <button 
                      className="recent-search-item-btn"
                      onClick={() => {
                        setKeyword(queryText);
                        handleSearchSubmit(1, queryText);
                      }}
                    >
                      <History size={16} className="text-muted" />
                      <span className="recent-search-text">{queryText}</span>
                    </button>
                    
                    <div className="recent-search-options-wrapper">
                      <button 
                        className="recent-search-dots-btn"
                        onClick={(e) => handleToggleSearchMenu(e, index)}
                        title="Options"
                      >
                        <MoreVertical size={16} />
                      </button>
                      
                      {activeSearchMenuIndex === index && (
                        <div className="recent-search-menu">
                          <button 
                            className="recent-menu-item delete"
                            onClick={(e) => {
                              e.stopPropagation();
                              deleteSearchQuery(queryText);
                              setActiveSearchMenuIndex(-1);
                            }}
                          >
                            <Trash2 size={12} /> Delete
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div className="search-empty flex-center text-center">
              <div>
                <SearchIcon size={48} className="search-empty-icon" style={{ margin: "0 auto 1rem", color: "var(--text-muted)" }} />
                <h3>No search results</h3>
                <p className="text-muted" style={{ marginTop: "4px" }}>
                  Try entering keywords or browse genres to explore AniStream's catalog.
                </p>
              </div>
            </div>
          )}
        </div>
      </div>

      <style>{`
        .search-page {
          padding-top: calc(var(--header-height) + 20px);
          min-height: 100vh;
        }
        .search-header-panel {
          display: flex;
          gap: 1rem;
          margin-bottom: 2rem;
          align-items: center;
        }
        .search-bar-wrapper {
          position: relative;
          flex-grow: 1;
          display: flex;
          align-items: center;
        }
        .search-icon-svg {
          position: absolute;
          left: 14px;
          color: var(--text-muted);
          pointer-events: none;
        }
        .search-input-box {
          width: 100%;
          padding: 0.85rem 1rem 0.85rem 2.8rem;
          background-color: var(--bg-card);
          border: 1px solid var(--border);
          border-radius: 8px;
          color: white;
          font-family: var(--font-family);
          font-size: 1rem;
          transition: var(--transition);
        }
        .search-input-box:focus {
          outline: none;
          border-color: var(--primary);
          box-shadow: 0 0 10px rgba(229, 9, 20, 0.2);
        }
        .clear-search-btn {
          position: absolute;
          right: 14px;
          background: none;
          border: none;
          color: var(--text-muted);
          cursor: pointer;
        }
        .clear-search-btn:hover {
          color: white;
        }

        /* Auto-suggestions dropdown styling */
        .suggestions-dropdown {
          position: absolute;
          top: calc(100% + 8px);
          left: 0;
          right: 0;
          background: #141414;
          border: 1px solid var(--border);
          border-radius: 8px;
          max-height: 350px;
          overflow-y: auto;
          box-shadow: 0 10px 30px rgba(0, 0, 0, 0.6);
          z-index: 120;
          padding: 6px;
        }
        .suggestion-item {
          display: flex;
          gap: 12px;
          padding: 8px;
          text-decoration: none;
          color: white;
          border-radius: 6px;
          transition: var(--transition);
        }
        .suggestion-item:hover {
          background-color: rgba(255, 255, 255, 0.05);
        }
        .suggestion-poster {
          width: 44px;
          height: 60px;
          border-radius: 4px;
          overflow: hidden;
          background-color: #242424;
          flex-shrink: 0;
        }
        .suggestion-poster img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }
        .suggestion-info {
          display: flex;
          flex-direction: column;
          justify-content: center;
          min-width: 0;
        }
        .suggestion-title {
          font-size: 0.85rem;
          font-weight: 600;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
          margin-bottom: 2px;
        }
        .suggestion-meta {
          font-size: 0.75rem;
          color: var(--text-secondary);
        }

        .filter-toggle-btn.active {
          border-color: var(--primary);
          color: var(--primary);
        }

        /* Search body columns layout */
        .search-body-panel {
          display: flex;
          gap: 2.5rem;
          align-items: flex-start;
        }
        .filters-sidebar {
          width: 280px;
          background-color: var(--bg-card);
          border: 1px solid var(--border);
          border-radius: 8px;
          padding: 1.5rem;
          display: flex;
          flex-direction: column;
          gap: 1.5rem;
          flex-shrink: 0;
          position: sticky;
          top: calc(var(--header-height) + 20px);
        }
        .filter-section h3 {
          font-size: 0.85rem;
          text-transform: uppercase;
          letter-spacing: 0.05em;
          color: var(--text-muted);
          margin-bottom: 8px;
        }
        .filter-options {
          display: flex;
          flex-wrap: wrap;
          gap: 6px;
        }
        .filter-options button {
          flex-grow: 1;
          background-color: var(--bg);
          border: 1px solid var(--border);
          color: var(--text-secondary);
          padding: 6px 12px;
          font-size: 0.8rem;
          font-weight: 600;
          cursor: pointer;
          border-radius: 4px;
          font-family: var(--font-family);
          transition: var(--transition);
        }
        .filter-options button.active {
          background-color: var(--primary);
          color: white;
          border-color: var(--primary);
        }
        
        .filter-genres-grid {
          display: grid;
          grid-template-columns: 1fr 1fr;
          gap: 4px;
        }
        .filter-genres-grid button {
          background-color: var(--bg);
          border: 1px solid var(--border);
          color: var(--text-secondary);
          padding: 6px 8px;
          font-size: 0.75rem;
          font-weight: 500;
          cursor: pointer;
          border-radius: 4px;
          font-family: var(--font-family);
          transition: var(--transition);
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .filter-genres-grid button.active {
          background-color: rgba(229, 9, 20, 0.15);
          color: #ff8080;
          border-color: var(--primary);
          font-weight: 700;
        }
        .clear-filters-btn {
          width: 100%;
          font-size: 0.85rem;
        }
        
        .search-results-column {
          flex-grow: 1;
        }
        .search-empty {
          min-height: 40vh;
        }
        
        .search-pagination {
          margin-top: 3rem;
          gap: 1.5rem;
        }
        .page-lbl {
          font-size: 0.9rem;
          font-weight: 600;
          color: var(--text-secondary);
        }
        .pag-btn {
          padding: 0.5rem 1.2rem;
          font-size: 0.85rem;
        }

        @media (max-width: 992px) {
          .search-body-panel {
            flex-direction: column;
          }
          .filters-sidebar {
            width: 100%;
            position: relative;
            top: 0;
          }
          .filter-genres-grid {
            grid-template-columns: repeat(4, 1fr);
          }
        }
        @media (max-width: 768px) {
          .search-header-panel {
            flex-direction: column;
            align-items: stretch;
          }
          .filter-genres-grid {
            grid-template-columns: repeat(2, 1fr);
          }
        }

        /* Recent Searches Styles */
        .recent-searches-container {
          max-width: 600px;
          margin: 1rem 0;
          animation: fadeIn 0.25s ease-out;
        }
        .recent-searches-title {
          font-size: 1.1rem;
          font-weight: 700;
          color: white;
          margin-bottom: 1rem;
          border-bottom: 1px solid var(--border);
          padding-bottom: 8px;
        }
        .recent-searches-list {
          display: flex;
          flex-direction: column;
          gap: 8px;
        }
        .recent-search-row {
          position: relative;
          display: flex;
          align-items: center;
          justify-content: space-between;
          background-color: var(--bg-card);
          border: 1px solid var(--border);
          border-radius: 6px;
          padding: 8px 12px;
          transition: var(--transition);
        }
        .recent-search-row:hover {
          background-color: rgba(255, 255, 255, 0.03);
          border-color: rgba(255, 255, 255, 0.15);
        }
        .recent-search-item-btn {
          flex-grow: 1;
          display: flex;
          align-items: center;
          gap: 12px;
          background: none;
          border: none;
          color: var(--text-secondary);
          text-align: left;
          font-size: 0.95rem;
          font-family: var(--font-family);
          cursor: pointer;
          padding: 6px 0;
          transition: var(--transition);
          min-width: 0;
        }
        .recent-search-item-btn:hover {
          color: white;
        }
        .recent-search-text {
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
          font-weight: 500;
        }
        .recent-search-options-wrapper {
          position: relative;
          display: flex;
          align-items: center;
          margin-left: 10px;
        }
        .recent-search-dots-btn {
          background: none;
          border: none;
          color: var(--text-muted);
          cursor: pointer;
          padding: 6px;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          transition: var(--transition);
        }
        .recent-search-dots-btn:hover {
          color: white;
          background-color: rgba(255, 255, 255, 0.08);
        }
        .recent-search-menu {
          position: absolute;
          top: 100%;
          right: 0;
          background: #141414;
          border: 1px solid var(--border);
          border-radius: 6px;
          padding: 4px;
          min-width: 100px;
          box-shadow: 0 5px 15px rgba(0, 0, 0, 0.5);
          z-index: 10;
          animation: fadeIn 0.15s ease-out;
        }
        .recent-menu-item {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 6px 10px;
          color: var(--text-secondary);
          background: none;
          border: none;
          font-size: 0.8rem;
          font-weight: 600;
          border-radius: 4px;
          width: 100%;
          text-align: left;
          cursor: pointer;
          font-family: var(--font-family);
          transition: var(--transition);
        }
        .recent-menu-item:hover {
          background-color: rgba(255, 255, 255, 0.05);
          color: white;
        }
        .recent-menu-item.delete:hover {
          background-color: rgba(229, 9, 20, 0.1);
          color: var(--primary);
        }
        .search-fallback-banner {
          display: flex;
          align-items: center;
          gap: 16px;
          padding: 16px 20px;
          border-radius: 10px;
          margin-bottom: 24px;
          color: var(--text-secondary);
          backdrop-filter: blur(10px);
          animation: fadeIn 0.3s ease-out;
        }
        .search-fallback-banner.anilist {
          background: rgba(61, 180, 242, 0.05);
          border: 1px solid rgba(61, 180, 242, 0.25);
        }
        .search-fallback-banner.offline {
          background: rgba(245, 166, 35, 0.05);
          border: 1px solid rgba(245, 166, 35, 0.2);
        }
        .search-fallback-banner p {
          margin: 0;
          font-size: 0.92rem;
          font-weight: 500;
          line-height: 1.4;
        }
        .fallback-badge {
          display: inline-flex;
          align-items: center;
          font-size: 0.72rem;
          font-weight: 800;
          text-transform: uppercase;
          letter-spacing: 0.05em;
          padding: 4px 10px;
          border-radius: 4px;
          white-space: nowrap;
          flex-shrink: 0;
        }
        .anilist .fallback-badge {
          background-color: rgba(61, 180, 242, 0.15);
          color: #3db4f2;
          border: 1px solid rgba(61, 180, 242, 0.3);
        }
        .offline .fallback-badge {
          background-color: rgba(245, 166, 35, 0.15);
          color: #f5a623;
          border: 1px solid rgba(245, 166, 35, 0.3);
        }
        @media (max-width: 768px) {
          .search-fallback-banner {
            flex-direction: column;
            align-items: flex-start;
            gap: 10px;
            padding: 14px 16px;
          }
        }
      `}</style>
    </div>
  );
}
