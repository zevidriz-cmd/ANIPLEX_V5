import React, { useEffect, useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { resolveMAL, search } from "../services/api";
import AnimeCard from "../components/AnimeCard";
import { GridShimmer } from "../components/Shimmer";
import { Calendar, ChevronLeft, ChevronRight, Loader2, Play, Star } from "lucide-react";

const SEASONS_LIST = [
  { id: "winter", label: "Winter" },
  { id: "spring", label: "Spring" },
  { id: "summer", label: "Summer" },
  { id: "fall", label: "Fall" }
];

const getCurrentSeasonAndYear = () => {
  const d = new Date();
  const year = d.getFullYear();
  const month = d.getMonth();
  let season = "winter";
  if (month >= 3 && month <= 5) season = "spring";
  else if (month >= 6 && month <= 8) season = "summer";
  else if (month >= 9 && month <= 11) season = "fall";
  return { season, year };
};

export default function SeasonalPage() {
  const navigate = useNavigate();
  const { season: currentSeason, year: currentYear } = getCurrentSeasonAndYear();
  
  const [selectedSeason, setSelectedSeason] = useState(currentSeason);
  const [selectedYear, setSelectedYear] = useState(currentYear);
  const [results, setResults] = useState([]);
  const [heroAnime, setHeroAnime] = useState(null);
  const [loading, setLoading] = useState(true);
  const [resolvingId, setResolvingId] = useState(false);
  const [resolvingMsg, setResolvingMsg] = useState("");

  // Pagination
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [hasNextPage, setHasNextPage] = useState(false);

  // Generate year options from currentYear+1 down to 2016
  const yearsList = [];
  for (let y = currentYear + 1; y >= 2016; y--) {
    yearsList.push(y);
  }

  // Load Seasonal Data
  useEffect(() => {
    let isMounted = true;
    
    async function fetchSeasonal() {
      setLoading(true);
      setHeroAnime(null);
      try {
        const isCurrent = selectedSeason === currentSeason && selectedYear === currentYear;
        const endpoint = isCurrent 
          ? `https://api.jikan.moe/v4/seasons/now?page=${page}&limit=24`
          : `https://api.jikan.moe/v4/seasons/${selectedYear}/${selectedSeason}?page=${page}&limit=24`;
        
        const res = await fetch(endpoint);
        if (!res.ok) throw new Error("Failed to fetch seasonal anime");
        const json = await res.json();
        
        if (!isMounted) return;
        
        const list = json.data || [];
        
        // Map Jikan response to standard structure
        const mappedList = list.map(item => ({
          id: `mal-${item.mal_id}`,
          malId: item.mal_id,
          name: item.title_english || item.title,
          poster: item.images?.jpg?.image_url || "",
          type: item.type || "TV",
          episodes: { sub: item.episodes || 0, dub: 0 },
          rating: item.score || "",
          description: item.synopsis || "",
          genres: (item.genres || []).map(g => g.name).join(" • ")
        }));

        // Sort items by score descending
        const sortedList = [...mappedList].sort((a, b) => {
          const scoreA = parseFloat(a.rating) || 0;
          const scoreB = parseFloat(b.rating) || 0;
          return scoreB - scoreA;
        });

        // Set top-rated anime as Hero Banner
        if (page === 1 && sortedList.length > 0) {
          setHeroAnime(sortedList[0]);
          setResults(mappedList.filter(item => item.id !== sortedList[0].id));
        } else {
          setResults(mappedList);
        }

        setTotalPages(json.pagination?.last_visible_page || 1);
        setHasNextPage(json.pagination?.has_next_page || false);
      } catch (err) {
        console.error("Seasonal load error:", err);
      } finally {
        if (isMounted) setLoading(false);
      }
    }

    fetchSeasonal();
    return () => {
      isMounted = false;
    };
  }, [selectedSeason, selectedYear, page]);

  // Handle click on seasonal card
  const handleAnimeClick = async (e, anime) => {
    e.preventDefault();
    setResolvingId(true);
    setResolvingMsg(`Resolving stream for "${anime.name}"...`);

    const malId = anime.malId;
    try {
      const resolveRes = await resolveMAL(malId);
      if (resolveRes && resolveRes.anikotoId) {
        setResolvingId(false);
        navigate(`/anime/${resolveRes.anikotoId}`);
        return;
      }
    } catch (err) {
      console.warn("resolveMAL failed, trying search:", err);
    }

    try {
      const searchRes = await search(anime.name);
      if (searchRes && searchRes.animes && searchRes.animes.length > 0) {
        const matchId = searchRes.animes[0].id;
        setResolvingId(false);
        navigate(`/anime/${matchId}`);
        return;
      }
    } catch (err) {
      console.warn("Search matching failed:", err);
    }

    setResolvingId(false);
    navigate(`/search?keyword=${encodeURIComponent(anime.name)}`);
  };

  return (
    <div className="seasonal-page fade-in container" style={{ marginTop: "var(--header-height)", paddingTop: "20px", paddingBottom: "40px" }}>
      {/* Search-to-play Resolution Overlay */}
      {resolvingId && (
        <div className="resolution-overlay flex-center">
          <div className="resolution-box text-center">
            <Loader2 size={44} className="spin-icon text-primary" style={{ margin: "0 auto 1.2rem" }} />
            <h3>Resolving Stream Source</h3>
            <p className="text-muted">{resolvingMsg}</p>
          </div>
        </div>
      )}

      {/* Header controls section */}
      <div style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: "1.2rem",
        marginBottom: "24px",
        borderBottom: "1px solid var(--border)",
        paddingBottom: "16px"
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
          <Calendar size={24} className="text-primary" />
          <h2 style={{ fontSize: "1.6rem", fontWeight: "800", margin: 0 }}>Seasonal Anime</h2>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: "12px", flexWrap: "wrap" }}>
          {/* Season Tabs */}
          <div style={{
            display: "flex",
            background: "rgba(255,255,255,0.05)",
            padding: "4px",
            borderRadius: "8px",
            border: "1px solid var(--border)"
          }}>
            {SEASONS_LIST.map(s => (
              <button
                key={s.id}
                onClick={() => {
                  setSelectedSeason(s.id);
                  setPage(1);
                }}
                style={{
                  background: selectedSeason === s.id ? "var(--primary)" : "none",
                  color: selectedSeason === s.id ? "white" : "var(--text-secondary)",
                  border: "none",
                  padding: "6px 12px",
                  borderRadius: "6px",
                  fontSize: "0.85rem",
                  fontWeight: "600",
                  cursor: "pointer",
                  transition: "all 0.2s"
                }}
              >
                {s.label}
              </button>
            ))}
          </div>

          {/* Year selector */}
          <select
            value={selectedYear}
            onChange={(e) => {
              setSelectedYear(Number(e.target.value));
              setPage(1);
            }}
            style={{
              background: "#141414",
              color: "white",
              border: "1px solid var(--border)",
              borderRadius: "8px",
              padding: "8px 16px",
              fontSize: "0.85rem",
              fontWeight: "600",
              cursor: "pointer",
              outline: "none"
            }}
          >
            {yearsList.map(y => (
              <option key={y} value={y}>{y}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Seasonal Hero Banner */}
      {!loading && heroAnime && page === 1 && (
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
            backgroundImage: `url(${heroAnime.poster})`,
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
                Top Rated • {selectedSeason.toUpperCase()} {selectedYear}
              </span>
              <h1 style={{
                fontSize: "2.2rem",
                fontWeight: "800",
                color: "white",
                lineHeight: "1.2",
                margin: 0
              }}>
                {heroAnime.name}
              </h1>
              <div style={{
                display: "flex",
                gap: "12px",
                fontSize: "0.8rem",
                color: "var(--text-secondary)",
                fontWeight: "600",
                alignItems: "center"
              }}>
                <span>{heroAnime.type}</span>
                <span>•</span>
                <span style={{ color: "#F5A623", display: "flex", alignItems: "center", gap: "2px" }}>
                  <Star size={12} fill="#F5A623" /> {heroAnime.rating}
                </span>
                {heroAnime.genres && (
                  <>
                    <span>•</span>
                    <span>{heroAnime.genres}</span>
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
                {heroAnime.description.replace(/<[^>]*>/g, "")}
              </p>
              <div style={{ display: "flex", gap: "12px", marginTop: "12px" }}>
                <button
                  onClick={(e) => handleAnimeClick(e, heroAnime)}
                  className="btn btn-primary"
                  style={{ padding: "10px 24px", display: "flex", alignItems: "center", gap: "6px", cursor: "pointer", border: "none" }}
                >
                  <Play size={16} fill="currentColor" /> Watch Now
                </button>
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
              <img src={heroAnime.poster} alt={heroAnime.name} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
            </div>
          </div>
        </div>
      )}

      {/* Grid Results Section */}
      {loading ? (
        <GridShimmer count={8} />
      ) : results.length > 0 ? (
        <>
          <div className="grid-layout">
            {results.map((anime) => (
              <a
                key={anime.id}
                href={`/anime/${anime.id}`}
                onClick={(e) => handleAnimeClick(e, anime)}
                className="anime-card-link-wrapper"
                style={{ textDecoration: "none", color: "inherit" }}
              >
                <AnimeCard
                  id={anime.id}
                  name={anime.name}
                  poster={anime.poster}
                  type={anime.type}
                  episodes={anime.episodes}
                  rating={anime.rating}
                />
              </a>
            ))}
          </div>

          {/* Pagination Controls */}
          {totalPages > 1 && (
            <div className="search-pagination flex-center" style={{ marginTop: "32px" }}>
              <button
                className="btn btn-secondary pag-btn"
                disabled={page === 1}
                onClick={() => setPage(page - 1)}
              >
                <ChevronLeft size={16} /> Prev
              </button>
              <span className="page-lbl">Page {page} of {totalPages}</span>
              <button
                className="btn btn-secondary pag-btn"
                disabled={!hasNextPage}
                onClick={() => setPage(page + 1)}
              >
                Next <ChevronRight size={16} />
              </button>
            </div>
          )}
        </>
      ) : (
        <div className="text-center" style={{ padding: "40px", color: "var(--text-muted)" }}>
          No anime found for this season.
        </div>
      )}
    </div>
  );
}
