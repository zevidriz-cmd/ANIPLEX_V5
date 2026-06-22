import React, { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { collection, query, orderBy, getDocs, doc, deleteDoc } from "firebase/firestore";
import { db } from "../config/firebase";
import { useAuth } from "../context/AuthContext";
import { useProfile } from "../context/ProfileContext";
import { getHome, resolveMAL, search, STREAM_PROXY_BASE } from "../services/api";
import Carousel from "../components/Carousel";
import AnimeCard from "../components/AnimeCard";
import { BillboardShimmer, GridShimmer } from "../components/Shimmer";
import { Play, ChevronLeft, ChevronRight, Loader2 } from "lucide-react";

export default function HomePage() {
  const { currentUser } = useAuth();
  const { activeProfile } = useProfile();
  const navigate = useNavigate();
  
  const [homeData, setHomeData] = useState(null);
  const [historyList, setHistoryList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [historyLoading, setHistoryLoading] = useState(true);
  
  // Real airing timeline states
  const [airingTimeline, setAiringTimeline] = useState([]);
  const [airingLoading, setAiringLoading] = useState(true);
  const [airingDayLabel, setAiringDayLabel] = useState("Today");
  const [resolvingId, setResolvingId] = useState(false);
  const [resolvingMsg, setResolvingMsg] = useState("");
  
  const airingScrollRef = React.useRef(null);

  const scrollAiring = (direction) => {
    if (airingScrollRef.current) {
      const container = airingScrollRef.current;
      const cardWidth = 240;
      const scrollAmount = direction === "left" ? -cardWidth * 2 : cardWidth * 2;
      container.scrollBy({ left: scrollAmount, behavior: "smooth" });
    }
  };

  const handleScrollAiring = (e) => {
    const scrollLeft = e.target.scrollLeft;
    const cardWidth = 240; // 220px card width + 20px gap
    const index = Math.min(
      airingTimeline.length - 1,
      Math.max(0, Math.round(scrollLeft / cardWidth))
    );
    if (airingTimeline[index]) {
      setAiringDayLabel(airingTimeline[index].dayLabel);
    }
  };

  // Click handler to resolve MAL IDs to HiAnime streaming IDs on-the-fly
  const handleAnimeClick = async (e, item) => {
    if (!item.id.startsWith("mal-")) {
      return; // Standard routing
    }

    e.preventDefault();
    setResolvingId(true);
    setResolvingMsg(`Connecting to stream for "${item.name}"...`);

    const malId = item.id.substring(4); // Remove "mal-" prefix
    try {
      // 1. Resolve via cached MAL ID database on the proxy worker
      const resolveRes = await resolveMAL(malId);
      if (resolveRes && resolveRes.anikotoId) {
        setResolvingId(false);
        navigate(`/anime/${resolveRes.anikotoId}`);
        return;
      }
    } catch (err) {
      console.warn("resolveMAL failed, searching by keyword title:", err);
    }

    try {
      // 2. Fallback to Search API matching
      const searchRes = await search(item.name);
      if (searchRes && searchRes.animes && searchRes.animes.length > 0) {
        const matchId = searchRes.animes[0].id;
        setResolvingId(false);
        navigate(`/anime/${matchId}`);
        return;
      }
    } catch (err) {
      console.warn("Search fallback resolution failed:", err);
    }

    // 3. Double-fallback: Redirect directly to Search page with prefilled title
    setResolvingId(false);
    navigate(`/search?keyword=${encodeURIComponent(item.name)}`);
  };

  // Fetch API Home data
  useEffect(() => {
    async function loadData() {
      try {
        const data = await getHome();
        setHomeData(data);
      } catch (err) {
        console.error("Home API fetch error:", err);
      } finally {
        setLoading(false);
      }
    }
    loadData();
  }, []);

  // Fetch Jikan airing timeline once homeData is loaded
  useEffect(() => {
    if (loading) return; // Wait for main home data first
    
    async function loadAiringTimeline() {
      setAiringLoading(true);
      const now = new Date();
      try {
        const weekdays = ["sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"];
        const daysToFetch = [];
        
        for (let i = 0; i < 4; i++) {
          const d = new Date();
          d.setDate(now.getDate() - i);
          const dayIndex = d.getDay();
          const dayName = weekdays[dayIndex];
          
          let label = "";
          if (i === 0) {
            label = "Today";
          } else if (i === 1) {
            label = "Yesterday";
          } else {
            label = d.toLocaleDateString("en-US", { weekday: "long" });
          }
          
          daysToFetch.push({ dayName, label });
        }

        const allAiring = [];
        for (const dayInfo of daysToFetch) {
          let jikanRes = await fetch(`${STREAM_PROXY_BASE}/api.jikan.moe/v4/schedules?filter=${dayInfo.dayName}`).catch(() => null);
          if (!jikanRes || !jikanRes.ok) {
            jikanRes = await fetch(`https://api.jikan.moe/v4/schedules?filter=${dayInfo.dayName}`).catch(() => null);
          }
          if (jikanRes && jikanRes.ok) {
            const json = await jikanRes.json();
            const mapped = (json.data || []).map(item => {
              let airTimeStr = item.broadcast?.time || "Airing Today";
              if (item.broadcast?.time) {
                try {
                  const [hourStr, minStr] = item.broadcast.time.split(":");
                  const hour = parseInt(hourStr, 10);
                  const period = hour >= 12 ? "PM" : "AM";
                  const hour12 = hour % 12 || 12;
                  airTimeStr = `${hour12}:${minStr} ${period}`;
                } catch (e) {
                  // ignore
                }
              }

              return {
                id: `mal-${item.mal_id}`,
                name: item.title_english || item.title,
                airTime: airTimeStr,
                episodes: { sub: item.episodes || "?" },
                poster: item.images?.jpg?.image_url || "",
                dayLabel: dayInfo.label
              };
            });
            allAiring.push(...mapped);
          }
          // Sleep to avoid rate limits
          await new Promise(resolve => setTimeout(resolve, 200));
        }

        if (allAiring.length > 0) {
          setAiringTimeline(allAiring);
          setAiringDayLabel(allAiring[0].dayLabel);
        } else {
          throw new Error("No airing data returned from Jikan");
        }
      } catch (err) {
        console.warn("Failed to load Jikan schedules for timeline, using HiAnime fallback:", err);
        // Fallback to homeData's recently added animes
        const recentlyAdded = homeData?.recentlyAddedAnimes || [];
        const fallback = recentlyAdded.map((anime, i) => {
          const itemDate = new Date(now.getTime() - i * 9 * 60 * 60 * 1000);
          const nowDay = now.toDateString();
          const itemDay = itemDate.toDateString();
          
          const yesterday = new Date();
          yesterday.setDate(now.getDate() - 1);
          const yesterdayDay = yesterday.toDateString();
          
          let label = "";
          if (nowDay === itemDay) {
            label = "Today";
          } else if (yesterdayDay === itemDay) {
            label = "Yesterday";
          } else {
            label = itemDate.toLocaleDateString("en-US", { weekday: "long" });
          }
          
          const airTime = itemDate.toLocaleTimeString("en-US", {
            hour: "numeric",
            minute: "2-digit",
            hour12: true
          });
          
          return {
            ...anime,
            airTime,
            dayLabel: label
          };
        });
        setAiringTimeline(fallback);
        if (fallback.length > 0) {
          setAiringDayLabel(fallback[0].dayLabel);
        }
      } finally {
        setAiringLoading(false);
      }
    }

    loadAiringTimeline();
  }, [loading, homeData]);

  // Fetch Firestore watch history for continue watching
  useEffect(() => {
    if (!currentUser || !activeProfile) {
      setHistoryList([]);
      setHistoryLoading(false);
      return;
    }

    async function loadHistory() {
      setHistoryLoading(true);
      try {
        const q = query(
          collection(db, "users", currentUser.uid, "profiles", activeProfile.id, "history"),
          orderBy("updatedAt", "desc")
        );
        const snap = await getDocs(q);
        const list = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
        // Filter history list: only show items where progress is active and not fully finished
        const filteredList = list.filter(item => {
          if (item.totalDuration <= 0) return true; // clean resume next ep
          // Let's hide items that are 98%+ watched
          return (item.progressPosition / item.totalDuration) < 0.98;
        });
        setHistoryList(filteredList);
      } catch (err) {
        console.warn("Error loading home history:", err);
      } finally {
        setHistoryLoading(false);
      }
    }
    loadHistory();
  }, [currentUser, activeProfile]);

  const handleRemoveItem = async (animeId) => {
    if (!currentUser || !activeProfile) return;
    const docRef = doc(db, "users", currentUser.uid, "profiles", activeProfile.id, "history", animeId);
    try {
      await deleteDoc(docRef);
      setHistoryList(prev => prev.filter(item => item.animeId !== animeId));
    } catch (e) {
      console.error("Error removing watch history item:", e);
    }
  };

  if (loading) {
    return (
      <div className="home-loading-wrapper">
        <BillboardShimmer />
        <div className="container">
          <div style={{ margin: "2rem 0" }}>
            <h2 className="section-title">Loading trending anime...</h2>
            <GridShimmer count={6} />
          </div>
        </div>
      </div>
    );
  }

  const spotlightAnimes = homeData?.spotlightAnimes || [];
  const trendingAnimes = homeData?.trendingAnimes || [];
  const topAiringAnimes = homeData?.topAiringAnimes || [];
  const mostPopularAnimes = homeData?.mostPopularAnimes || [];

  return (
    <div className="home-page">
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

      {spotlightAnimes.length > 0 && <Carousel items={spotlightAnimes} />}

      <div className="container home-content-sections">
        {/* Continue Watching Section */}
        {!historyLoading && historyList.length > 0 && (
          <section className="home-section">
            <h2 className="section-title">Continue Watching</h2>
            <div className="horizontal-scroll-row">
              {historyList.map((item) => {
                const percent = item.totalDuration > 0 ? (item.progressPosition / item.totalDuration) * 100 : 0;
                
                // Construct progress info text
                let progressText = `Episode ${item.episodeNumber}`;
                if (item.totalDuration > 0) {
                  const remainingSec = Math.floor((item.totalDuration - item.progressPosition) / 1000);
                  const remainingMin = Math.floor(remainingSec / 60);
                  if (remainingMin > 0) {
                    progressText += ` • ${remainingMin}m remaining`;
                  } else {
                    progressText += ` • ${remainingSec}s remaining`;
                  }
                } else {
                  progressText += ` • Start watching`;
                }

                return (
                  <div key={item.id} className="scroll-card-item">
                    <AnimeCard 
                      id={item.animeId}
                      name={item.animeTitle}
                      poster={item.poster}
                      progressPercent={percent}
                      episodeInfo={progressText}
                      onRemove={handleRemoveItem}
                      episodeId={item.episodeId}
                      audioCategory={item.audioCategory}
                    />
                  </div>
                );
              })}
            </div>
          </section>
        )}

        {/* Trending Now Section */}
        {trendingAnimes.length > 0 && (
          <section className="home-section">
            <h2 className="section-title">Trending Now</h2>
            <div className="horizontal-scroll-row">
              {trendingAnimes.map((anime) => (
                <div key={anime.id} className="scroll-card-item">
                  <AnimeCard 
                    id={anime.id}
                    name={anime.name}
                    poster={anime.poster}
                    type={anime.type}
                    episodes={anime.episodes}
                    rating={anime.rate}
                  />
                </div>
              ))}
            </div>
          </section>
        )}

        {/* Top Airing Section */}
        {topAiringAnimes.length > 0 && (
          <section className="home-section">
            <h2 className="section-title">Top Airing</h2>
            <div className="horizontal-scroll-row">
              {topAiringAnimes.map((anime) => (
                <div key={anime.id} className="scroll-card-item">
                  <AnimeCard 
                    id={anime.id}
                    name={anime.name}
                    poster={anime.poster}
                    type={anime.type}
                    episodes={anime.episodes}
                    rating={anime.rate}
                  />
                </div>
              ))}
            </div>
          </section>
        )}

        {/* Recently Added Section - Landscape scroll-synced layout with Desktop Chevrons */}
        {airingLoading ? (
          <section className="home-section">
            <h2 className="section-title" style={{ marginBottom: "12px" }}>Recently Added Episodes</h2>
            <div className="airing-scroll-container-wrapper">
              <div className="airing-scroll-row">
                {Array.from({ length: 6 }).map((_, i) => (
                  <div key={i} className="airing-card-item">
                    <div className="shimmer-landscape-loading" style={{
                      backgroundColor: "var(--bg-card)",
                      borderRadius: "8px",
                      padding: "8px",
                      display: "flex",
                      flexDirection: "column",
                      gap: "8px",
                      width: "100%",
                      height: "160px"
                    }}>
                      <div className="shimmer" style={{ height: "12px", width: "40%", borderRadius: "2px", backgroundColor: "#222" }}></div>
                      <div className="shimmer" style={{ flexGrow: 1, borderRadius: "6px", backgroundColor: "#222" }}></div>
                      <div className="shimmer" style={{ height: "12px", width: "80%", borderRadius: "3px", backgroundColor: "#222" }}></div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </section>
        ) : (
          airingTimeline.length > 0 && (
            <section className="home-section">
              <div className="section-header-row">
                <h2 className="section-title" style={{ marginBottom: "2px" }}>Recently Added Episodes</h2>
                <div className="airing-subtitle text-primary">{airingDayLabel}</div>
              </div>
              
              <div className="airing-scroll-container-wrapper">
                <button 
                  className="carousel-arrow left" 
                  onClick={() => scrollAiring("left")} 
                  aria-label="Scroll Left"
                >
                  <ChevronLeft size={24} />
                </button>
                
                <div className="airing-scroll-row" ref={airingScrollRef} onScroll={handleScrollAiring}>
                  {airingTimeline.map((anime) => (
                    <div key={anime.id} className="airing-card-item">
                      <div className="airing-landscape-card">
                        <div className="air-time-cyan">{anime.airTime}</div>
                        <Link 
                          to={`/anime/${anime.id}`} 
                          onClick={(e) => handleAnimeClick(e, anime)}
                          className="airing-landscape-link"
                        >
                          <div className="landscape-poster-wrapper">
                            <img src={anime.poster} alt={anime.name} loading="lazy" />
                            <div className="landscape-poster-overlay">
                              <span className="landscape-play-icon-circle">
                                <Play size={18} fill="white" />
                              </span>
                            </div>
                            <div className="landscape-episodes-badges">
                              {anime.episodes?.sub !== undefined && anime.episodes?.sub !== null && (
                                <span className="badge sub">SUB {anime.episodes.sub}</span>
                              )}
                              {anime.episodes?.dub !== undefined && anime.episodes?.dub !== null && (
                                <span className="badge dub">DUB {anime.episodes.dub}</span>
                              )}
                            </div>
                          </div>
                          <div className="landscape-card-info">
                            <h3 className="anime-name" title={anime.name}>{anime.name}</h3>
                          </div>
                        </Link>
                      </div>
                    </div>
                  ))}
                </div>

                <button 
                  className="carousel-arrow right" 
                  onClick={() => scrollAiring("right")} 
                  aria-label="Scroll Right"
                >
                  <ChevronRight size={24} />
                </button>
              </div>
            </section>
          )
        )}

        {/* Most Popular Section */}
        {mostPopularAnimes.length > 0 && (
          <section className="home-section">
            <h2 className="section-title">Most Popular</h2>
            <div className="horizontal-scroll-row">
              {mostPopularAnimes.map((anime) => (
                <div key={anime.id} className="scroll-card-item">
                  <AnimeCard 
                    id={anime.id}
                    name={anime.name}
                    poster={anime.poster}
                    type={anime.type}
                    episodes={anime.episodes}
                    rating={anime.rate}
                  />
                </div>
              ))}
            </div>
          </section>
        )}

        {/* Genres Navigation Grid */}
        {homeData?.genres && (
          <section className="home-section genres-section">
            <h2 className="section-title">Browse by Genre</h2>
            <div className="genres-chip-grid">
              {homeData.genres.map((genre) => (
                <Link key={genre} to={`/search?genre=${encodeURIComponent(genre)}`} className="genre-chip-btn">
                  {genre}
                </Link>
              ))}
            </div>
          </section>
        )}
      </div>

      <style>{`
        .home-page {
          padding-bottom: 2rem;
        }
        .home-loading-wrapper {
          padding-bottom: 2rem;
        }
        .home-content-sections {
          display: flex;
          flex-direction: column;
          gap: 2.5rem;
          margin-top: -2rem; /* Pull up to overlap gradient */
          position: relative;
          z-index: 30;
        }
        .home-section {
          width: 100%;
        }
        .section-title {
          font-size: 1.25rem;
          font-weight: 700;
          color: white;
          margin-bottom: 1rem;
          letter-spacing: -0.5px;
          text-transform: capitalize;
        }
        
        /* Custom horizontal scrolling row */
        .horizontal-scroll-row {
          display: flex;
          gap: 1.2rem;
          overflow-x: auto;
          padding: 8px 0 20px;
          scroll-snap-type: x mandatory;
          scrollbar-width: none; /* Firefox */
        }
        .horizontal-scroll-row::-webkit-scrollbar {
          display: none; /* Chrome, Safari, Opera */
        }
        .scroll-card-item {
          flex: 0 0 200px;
          scroll-snap-align: start;
        }
        @media (max-width: 768px) {
          .scroll-card-item {
            flex: 0 0 150px;
          }
        }
        
        .genres-chip-grid {
          display: flex;
          flex-wrap: wrap;
          gap: 0.8rem;
          margin-top: 1rem;
        }
        .genre-chip-btn {
          background-color: var(--bg-card);
          border: 1px solid var(--border);
          color: var(--text-primary);
          text-decoration: none;
          padding: 0.6rem 1.2rem;
          font-size: 0.85rem;
          font-weight: 600;
          border-radius: 6px;
          transition: var(--transition);
        }
        .genre-chip-btn:hover {
          background-color: var(--primary);
          border-color: var(--primary);
          color: white;
          transform: translateY(-2px);
        }

        /* Recently Added Landscape Row styles */
        .section-header-row {
          display: flex;
          flex-direction: column;
          margin-bottom: 0.8rem;
        }
        .airing-subtitle {
          font-size: 0.85rem;
          font-weight: 700;
          letter-spacing: 0.05em;
          text-transform: uppercase;
        }
        .airing-scroll-row {
          display: flex;
          gap: 20px;
          overflow-x: auto;
          padding: 8px 0 20px;
          scroll-snap-type: x mandatory;
          scrollbar-width: none;
        }
        .airing-scroll-row::-webkit-scrollbar {
          display: none;
        }
        .airing-card-item {
          flex: 0 0 220px;
          scroll-snap-align: start;
        }
        .airing-landscape-card {
          position: relative;
          background-color: var(--bg-card);
          border-radius: 8px;
          overflow: hidden;
          transition: var(--transition);
        }
        .airing-landscape-card:hover {
          transform: translateY(-4px) scale(1.02);
        }
        .air-time-cyan {
          color: #00FFCC;
          font-size: 0.8rem;
          font-weight: 700;
          margin-bottom: 6px;
          letter-spacing: 0.5px;
        }
        .airing-landscape-link {
          text-decoration: none;
          color: inherit;
          display: flex;
          flex-direction: column;
        }
        .landscape-poster-wrapper {
          position: relative;
          width: 220px;
          height: 115px;
          border-radius: 6px;
          overflow: hidden;
          background-color: #1a1a1a;
        }
        .landscape-poster-wrapper img {
          width: 100%;
          height: 100%;
          object-fit: cover;
          transition: transform 0.5s ease;
        }
        .airing-landscape-card:hover .landscape-poster-wrapper img {
          transform: scale(1.06);
        }
        .landscape-poster-overlay {
          position: absolute;
          inset: 0;
          background: linear-gradient(to top, rgba(0, 0, 0, 0.7) 0%, rgba(0,0,0,0.1) 100%);
          display: flex;
          align-items: center;
          justify-content: center;
          transition: opacity 0.25s ease;
          opacity: 0;
        }
        .airing-landscape-card:hover .landscape-poster-overlay {
          opacity: 1;
        }
        .landscape-play-icon-circle {
          width: 36px;
          height: 36px;
          border-radius: 50%;
          background-color: var(--primary);
          display: flex;
          align-items: center;
          justify-content: center;
          color: white;
          transform: scale(0.85);
          transition: transform 0.25s cubic-bezier(0.175, 0.885, 0.32, 1.275);
        }
        .airing-landscape-card:hover .landscape-play-icon-circle {
          transform: scale(1);
        }
        .landscape-episodes-badges {
          position: absolute;
          bottom: 8px;
          left: 8px;
          display: flex;
          gap: 4px;
          z-index: 3;
        }
        .landscape-card-info {
          padding: 8px 4px;
        }
        @media (max-width: 768px) {
          .airing-card-item {
            flex: 0 0 180px;
          }
          .landscape-poster-wrapper {
            width: 180px;
            height: 95px;
          }
        }

        /* Desktop Carousel arrows styling */
        .airing-scroll-container-wrapper {
          position: relative;
          width: 100%;
        }
        .carousel-arrow {
          position: absolute;
          top: calc(50% - 20px);
          width: 40px;
          height: 40px;
          border-radius: 50%;
          background-color: rgba(10, 10, 10, 0.85);
          border: 1px solid var(--border);
          color: white;
          display: flex;
          align-items: center;
          justify-content: center;
          cursor: pointer;
          z-index: 35;
          transition: var(--transition);
          opacity: 0;
        }
        .airing-scroll-container-wrapper:hover .carousel-arrow {
          opacity: 1;
        }
        .carousel-arrow:hover {
          background-color: var(--primary);
          border-color: var(--primary);
          transform: scale(1.1);
        }
        .carousel-arrow.left {
          left: -15px;
        }
        .carousel-arrow.right {
          right: -15px;
        }
        @media (max-width: 768px) {
          .carousel-arrow {
            display: none !important;
          }
        }

        /* Loading resolution overlay */
        .resolution-overlay {
          position: fixed;
          inset: 0;
          background-color: rgba(0, 0, 0, 0.85);
          backdrop-filter: blur(8px);
          z-index: 9999;
          display: flex;
          align-items: center;
          justify-content: center;
        }
        .resolution-box {
          background-color: #141414;
          border: 1px solid var(--border);
          border-radius: 12px;
          padding: 30px;
          max-width: 400px;
          width: 90%;
          box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
        }
        .resolution-box h3 {
          color: white;
          font-size: 1.2rem;
          margin-bottom: 8px;
        }
        .spin-icon {
          animation: spin 1s linear infinite;
        }
        @keyframes spin {
          100% { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
}
