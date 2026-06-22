import React, { useEffect, useState, useRef } from "react";
import { useSearchParams, Link } from "react-router-dom";
import { search, getSuggestions, getGenre, filterAnime } from "../services/api";
import { useProfile } from "../context/ProfileContext";
import AnimeCard from "../components/AnimeCard";
import { GridShimmer } from "../components/Shimmer";
import { Search as SearchIcon, Filter, X, ChevronLeft, ChevronRight, SlidersHorizontal, MoreVertical, Trash2, History } from "lucide-react";

export default function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const genreParam = searchParams.get("genre") || "";
  const { activeProfile } = useProfile();

  const [keyword, setKeyword] = useState("");
  const [results, setResults] = useState([]);
  const [suggestions, setSuggestions] = useState([]);
  const [loading, setLoading] = useState(false);
  
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

      setResults(data?.animes || []);
      setPage(data?.currentPage || 1);
      setTotalPages(data?.totalPages || 1);
      setHasNextPage(data?.hasNextPage || false);

      if (hasKeyword) {
        saveSearchQuery(queryToSearch);
      }
    } catch (e) {
      console.error("Search error:", e);
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
          {loading ? (
            <GridShimmer count={8} />
          ) : results.length > 0 ? (
            <>
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
      `}</style>
    </div>
  );
}
