import React, { useEffect, useState } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { doc, getDoc, setDoc, deleteDoc } from "firebase/firestore";
import { db } from "../config/firebase";
import { useAuth } from "../context/AuthContext";
import { useProfile } from "../context/ProfileContext";
import { 
  getAnimeDetail, getEpisodes, getCharacters, 
  getSeasons, resolveMAL, search 
} from "../services/api";
import { DetailsShimmer } from "../components/Shimmer";
import AnimeCard from "../components/AnimeCard";
import { 
  Play, Bookmark, BookmarkCheck, Star, Users, List, 
  ChevronRight, ArrowLeft, RefreshCw, ChevronDown 
} from "lucide-react";

const getMediaType = (season) => {
  const titleLower = season.title.toLowerCase();
  
  if (titleLower.includes("movie") || titleLower.includes("film") || titleLower.includes("theatrical") || season.episodes === 1) {
    return "Movie";
  }
  if (titleLower.includes("ova") || titleLower.includes("o.v.a") || titleLower.includes("oad")) {
    return "OVA";
  }
  if (titleLower.includes("special") || titleLower.includes("recap") || titleLower.includes("summary") || titleLower.includes("preview")) {
    return "Special";
  }
  if (titleLower.includes("ona") || titleLower.includes("spin-off")) {
    return "ONA";
  }
  return "TV";
};

const getMediaBadge = (season) => {
  const type = getMediaType(season);
  if (type === "Movie") return "Movie";
  if (type === "OVA") return "OVA";
  if (type === "Special") return "Special";
  if (type === "ONA") return "ONA";
  return season.seasonNumber ? `Season ${season.seasonNumber}` : "TV Season";
};

const getSeasonDisplayTitle = (season) => {
  if (!season) return "";
  const title = season.title;
  const titleLower = title.toLowerCase();
  
  const seasonMatch = titleLower.match(/(\d+)(st|nd|rd|th)?\s*season/i) || titleLower.match(/season\s*(\d+)/i);
  if (seasonMatch) {
    const num = parseInt(seasonMatch[1]);
    const partMatch = titleLower.match(/part\s*(\d+)/i);
    if (partMatch) {
      return `Season ${num} Part ${partMatch[1]}`;
    }
    return `Season ${num}`;
  }

  // Fallback to checking the seasonNumber property if it exists
  if (season.seasonNumber) {
    const num = parseInt(season.seasonNumber);
    if (!isNaN(num) && num > 0) {
      const partMatch = titleLower.match(/part\s*(\d+)/i);
      if (partMatch) {
        return `Season ${num} Part ${partMatch[1]}`;
      }
      return `Season ${num}`;
    }
  }
  
  const type = getMediaType(season);
  if (type !== "TV") return type;
  
  return "Season 1";
};

const getShortSeasonBadge = (season) => {
  if (!season) return "";
  const titleLower = season.title.toLowerCase();
  const seasonMatch = titleLower.match(/(\d+)(st|nd|rd|th)?\s*season/i) || titleLower.match(/season\s*(\d+)/i);
  if (seasonMatch) {
    const num = seasonMatch[1];
    const partMatch = titleLower.match(/part\s*(\d+)/i);
    if (partMatch) {
      return `S${num} P${partMatch[1]}`;
    }
    return `S${num}`;
  }
  return season.seasonNumber ? `S${season.seasonNumber}` : "S1";
};

export default function DetailPage() {
  const { id: animeId } = useParams();
  const { currentUser } = useAuth();
  const { activeProfile } = useProfile();
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState(null);
  const [episodes, setEpisodes] = useState([]);
  const [characters, setCharacters] = useState([]);
  const [seasons, setSeasons] = useState([]);
  const [audioPreference, setAudioPreference] = useState("sub"); // 'sub' or 'dub'

  // Firestore Sync States
  const [isWatchlisted, setIsWatchlisted] = useState(false);
  const [watchlistStatus, setWatchlistStatus] = useState("");
  const [showWatchlistMenu, setShowWatchlistMenu] = useState(false);
  const [historyItem, setHistoryItem] = useState(null);
  const [userRating, setUserRating] = useState(0);
  
  // Versions
  const [hasAltVersion, setHasAltVersion] = useState(false);
  const [currentVersion, setCurrentVersion] = useState("censored"); // 'censored' or 'uncensored'
  const [resolvingVersion, setResolvingVersion] = useState(false);
  const [seasonDropdownOpen, setSeasonDropdownOpen] = useState(false);
  const [showMobileRating, setShowMobileRating] = useState(false);
  const [showAllSynopsis, setShowAllSynopsis] = useState(false);

  useEffect(() => {
    async function loadData() {
      setLoading(true);
      setSeasons([]);
      setHasAltVersion(false);
      try {
        // Fetch Details, Episodes, Characters in parallel
        const [detailData, epData, charData] = await Promise.all([
          getAnimeDetail(animeId),
          getEpisodes(animeId).catch(() => ({ episodes: [] })),
          getCharacters(animeId).catch(() => ({ characters: [] }))
        ]);

        setDetail(detailData);
        setEpisodes(epData?.episodes || []);
        setCharacters(charData?.characters || []);

        // Audio preference default to whatever episodes have
        const hasSub = epData?.episodes?.length > 0;
        setAudioPreference(hasSub ? "sub" : "dub");

        // Determine if current title is uncut/uncensored
        const nameLower = detailData?.anime?.info?.name?.toLowerCase() || "";
        const isUncut = nameLower.includes("uncut") || nameLower.includes("uncensored") || animeId.includes("-uncut");
        setCurrentVersion(isUncut ? "uncensored" : "censored");

        // Sync user firestore states
        if (currentUser && activeProfile) {
          const profilePath = ["users", currentUser.uid, "profiles", activeProfile.id];
          
          // Parallel Firestore reads
          const [watchSnap, histSnap, rateSnap] = await Promise.all([
            getDoc(doc(db, ...profilePath, "watchlist", animeId)),
            getDoc(doc(db, ...profilePath, "history", animeId)),
            getDoc(doc(db, ...profilePath, "ratings", animeId))
          ]);

          setIsWatchlisted(watchSnap.exists());
          if (watchSnap.exists()) {
            setWatchlistStatus(watchSnap.data().status || "planning");
          } else {
            setWatchlistStatus("");
          }
          if (histSnap.exists()) {
            setHistoryItem(histSnap.data());
          } else {
            setHistoryItem(null);
          }
          if (rateSnap.exists()) {
            setUserRating(rateSnap.data().rating || 0);
          } else {
            setUserRating(0);
          }
        }

        // Set loading to false now, letting the page display instantly!
        setLoading(false);

        // Fetch seasons asynchronously in the background
        const malId = detailData?.anime?.info?.malId;
        if (malId && malId !== "0" && malId !== "") {
          getSeasons(malId)
            .then(async (seasonData) => {
              let list = seasonData?.seasons || [];
              const currentMalId = parseInt(malId);
              const hasCurrent = list.some(s => parseInt(s.malId) === currentMalId);
              if (!hasCurrent && detailData?.anime?.info) {
                const currentSeason = {
                  malId: currentMalId,
                  title: detailData.anime.info.name,
                  poster: detailData.anime.info.poster,
                  seasonNumber: null
                };
                list = [currentSeason, ...list];
              }

              // Parallel seasons resolution
              const resolvedList = await Promise.all(
                list.map(async (s) => {
                  try {
                    if (parseInt(s.malId) === currentMalId) {
                      return { ...s, isResolvable: true };
                    }
                    const res = await resolveMAL(s.malId);
                    if (res && res.anikotoId && res.anikotoId !== "") {
                      return { ...s, isResolvable: true };
                    }
                    const searchRes = await search(s.title);
                    if (searchRes && searchRes.animes && searchRes.animes.length > 0) {
                      return { ...s, isResolvable: true };
                    }
                    return { ...s, isResolvable: false };
                  } catch {
                    return { ...s, isResolvable: false };
                  }
                })
              );

              let filteredList = resolvedList.filter(s => s.isResolvable);
              filteredList.sort((a, b) => {
                const aNum = a.seasonNumber ? parseInt(a.seasonNumber) : null;
                const bNum = b.seasonNumber ? parseInt(b.seasonNumber) : null;
                if (aNum !== null && bNum !== null) return aNum - bNum;
                if (aNum !== null) return -1;
                if (bNum !== null) return 1;
                return 0;
              });

              setSeasons(filteredList);

              if (filteredList.length > 0) {
                const baseTitle = detailData?.anime?.info?.name
                  .replace(/\s*\(uncut\)/gi, "")
                  .replace(/\s*\(uncensored\)/gi, "")
                  .replace(/\s*\(censored\)/gi, "")
                  .trim().toLowerCase();

                const hasAlt = filteredList.some(s => {
                  const sTitle = s.title.toLowerCase();
                  const isSUncut = sTitle.includes("uncut") || sTitle.includes("uncensored");
                  return sTitle.includes(baseTitle) && (isSUncut !== isUncut);
                });
                setHasAltVersion(hasAlt);
              }
            })
            .catch(e => console.warn("Background seasons fetch error:", e));
        }

      } catch (err) {
        console.error("Detail page fetch error:", err);
        setLoading(false);
      }
    }
    loadData();
  }, [animeId, currentUser, activeProfile]);

  const handleUpdateWatchlistStatus = async (status) => {
    if (!currentUser || !activeProfile) {
      alert("Please sign in and select a profile first!");
      return;
    }

    const docRef = doc(db, "users", currentUser.uid, "profiles", activeProfile.id, "watchlist", animeId);
    try {
      if (status === "") {
        await deleteDoc(docRef);
        setIsWatchlisted(false);
        setWatchlistStatus("");
      } else {
        const item = {
          id: animeId,
          name: detail.anime.info.name,
          poster: detail.anime.info.poster,
          status: status,
          addedAt: Date.now()
        };
        await setDoc(docRef, item);
        setIsWatchlisted(true);
        setWatchlistStatus(status);
      }
    } catch (e) {
      console.error("Watchlist save error:", e);
    }
  };

  const handleRatingSelect = async (stars) => {
    if (!currentUser || !activeProfile) return;
    const docRef = doc(db, "users", currentUser.uid, "profiles", activeProfile.id, "ratings", animeId);
    try {
      if (userRating === stars) {
        await deleteDoc(docRef);
        setUserRating(0);
      } else {
        await setDoc(docRef, { rating: stars, ratedAt: Date.now() });
        setUserRating(stars);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleSwitchVersion = async (targetVer) => {
    if (targetVer === currentVersion) return;
    setResolvingVersion(true);

    try {
      const baseTitle = detail.anime.info.name
        .replace(/\s*\(uncut\)/gi, "")
        .replace(/\s*\(uncensored\)/gi, "")
        .replace(/\s*\(censored\)/gi, "")
        .trim();

      const searchTitle = targetVer === "uncensored" ? `${baseTitle} uncut` : baseTitle;
      const searchRes = await search(searchTitle);
      
      const matched = searchRes?.animes?.find(a => {
        const titleLower = a.name.toLowerCase();
        const matchesBase = titleLower.includes(baseTitle.toLowerCase());
        const isMatchedUncut = titleLower.includes("uncut") || titleLower.includes("uncensored") || a.id.includes("-uncut");
        return matchesBase && (isMatchedUncut === (targetVer === "uncensored"));
      });

      if (matched && matched.id !== animeId) {
        navigate(`/anime/${matched.id}`);
      } else {
        alert(`Alternative ${targetVer === "uncensored" ? "Uncut" : "TV"} version not found.`);
      }
    } catch (e) {
      console.error("Error switching version:", e);
    } finally {
      setResolvingVersion(false);
    }
  };

  const handleSeasonClick = async (season) => {
    if (season.malId === detail.anime.info.malId) return;
    setLoading(true);
    try {
      const resolved = await resolveMAL(season.malId);
      if (resolved && resolved.anikotoId) {
        navigate(`/anime/${resolved.anikotoId}`);
      } else {
        // Fallback search by title
        const searchRes = await search(season.title);
        if (searchRes?.animes?.length > 0) {
          navigate(`/anime/${searchRes.animes[0].id}`);
        } else {
          alert("Season redirect is unavailable.");
          setLoading(false);
        }
      }
    } catch (e) {
      console.error(e);
      setLoading(false);
    }
  };

  if (loading) {
    return <DetailsShimmer />;
  }

  if (!detail) {
    return (
      <div className="container text-center detail-error-page flex-center">
        <div>
          <h2>Failed to load anime details.</h2>
          <Link to="/" className="btn btn-primary" style={{ marginTop: "1rem" }}>
            <ArrowLeft size={16} /> Go Home
          </Link>
        </div>
      </div>
    );
  }

  const { info, moreInfo } = detail.anime;
  const recommendedAnimes = detail.recommendedAnimes || [];

  const currentSeason = seasons.find(s => Number(s.malId) === Number(info?.malId));
  const currentSeasonTitle = currentSeason 
    ? getSeasonDisplayTitle(currentSeason)
    : (info?.name || "Select Season");

  const tvSeasons = seasons.filter(s => {
    const type = getMediaType(s);
    return type === "TV" || (s.seasonNumber && parseInt(s.seasonNumber) > 0);
  });
  
  const moviesAndSpecials = seasons.filter(s => {
    const type = getMediaType(s);
    return type !== "TV" && !(s.seasonNumber && parseInt(s.seasonNumber) > 0);
  });

  // Determine watch / resume link
  let playEpisodeId = episodes[0]?.episodeId;
  let playEpisodeNumber = 1;
  let isResume = false;

  if (historyItem && historyItem.episodeId) {
    playEpisodeId = historyItem.episodeId;
    playEpisodeNumber = historyItem.episodeNumber;
    isResume = true;
  }

  return (
    <div className="detail-page fade-in">
      {/* Hero Header Section */}
      <div className="detail-hero">
        <div className="hero-backdrop-image" style={{ backgroundImage: `url(${info.poster})` }}></div>
        <div className="hero-gradient"></div>
        <div className="container hero-container">
          <div className="hero-poster-wrapper">
            <img src={info.poster} alt={info.name} className="hero-poster" />
          </div>

          <div className="hero-meta-content">
            <h1 className="anime-title-h1">{info.name}</h1>
            
            <div className="meta-stats-row">
              {info.stats?.rating && <span className="stat-pill rating">{info.stats.rating}</span>}
              {info.stats?.quality && <span className="stat-pill quality">{info.stats.quality}</span>}
              {info.stats?.type && <span className="stat-pill type">{info.stats.type}</span>}
              {info.stats?.duration && <span className="stat-pill duration">{info.stats.duration}</span>}
              {episodes.length > 0 && (
                <span className="stat-pill eps-count">
                  {episodes.length} Episodes
                </span>
              )}
            </div>

            {/* TV vs UNCUT Switcher */}
            {hasAltVersion && (
              <div className="version-switcher-row">
                <span className="version-label">Version:</span>
                {["censored", "uncensored"].map((ver) => (
                  <button
                    key={ver}
                    className={`version-choice-btn ${currentVersion === ver ? "active" : ""}`}
                    onClick={() => handleSwitchVersion(ver)}
                    disabled={resolvingVersion}
                  >
                    {resolvingVersion && currentVersion !== ver ? (
                      <RefreshCw size={12} className="spin-icon" />
                    ) : null}
                    {ver === "uncensored" ? "Uncut (Uncensored)" : "TV Broadcast"}
                  </button>
                ))}
              </div>
            )}

            {/* Action buttons */}
            <div className="actions-row">
              {episodes.length > 0 ? (
                <Link to={`/watch/${animeId}/${playEpisodeId}`} className="btn btn-primary watch-now-btn">
                  <Play size={18} fill="white" /> {isResume ? `Resume Ep ${playEpisodeNumber}` : "Watch Episode 1"}
                </Link>
              ) : (
                <button className="btn btn-primary watch-now-btn" disabled>No Episodes Available</button>
              )}

              <div className="watchlist-dropdown-wrapper desktop-only">
                <button 
                  className={`btn ${isWatchlisted ? "btn-secondary" : "btn-secondary"} watchlist-toggle-btn`}
                  onClick={() => setShowWatchlistMenu(!showWatchlistMenu)}
                  type="button"
                >
                  {isWatchlisted ? <BookmarkCheck size={18} /> : <Bookmark size={18} />}
                  <span>
                    {isWatchlisted 
                      ? `List: ${
                          watchlistStatus === "watching" 
                            ? "Watching" 
                            : watchlistStatus === "completed" 
                            ? "Completed" 
                            : "Plan to Watch"
                        }` 
                      : "Add to List"}
                  </span>
                  <ChevronDown size={14} style={{ marginLeft: "4px" }} />
                </button>
                {showWatchlistMenu && (
                  <>
                    <div className="dropdown-backplate" onClick={() => setShowWatchlistMenu(false)} />
                    <div className="watchlist-menu-dropdown">
                      {[
                        { id: "planning", name: "Plan to Watch" },
                        { id: "watching", name: "Watching" },
                        { id: "completed", name: "Completed" }
                      ].map(opt => (
                        <button 
                          key={opt.id}
                          className={`watchlist-menu-item ${watchlistStatus === opt.id ? "active" : ""}`}
                          onClick={() => {
                            handleUpdateWatchlistStatus(opt.id);
                            setShowWatchlistMenu(false);
                          }}
                          type="button"
                        >
                          {opt.name}
                        </button>
                      ))}
                      {isWatchlisted && (
                        <button 
                          className="watchlist-menu-item remove-item"
                          onClick={() => {
                            handleUpdateWatchlistStatus("");
                            setShowWatchlistMenu(false);
                          }}
                          type="button"
                        >
                          Remove from List
                        </button>
                      )}
                    </div>
                  </>
                )}
              </div>

              {/* Mobile icon actions group */}
              <div className="mobile-action-buttons-group">
                <div className="mobile-watchlist-wrapper">
                  <button 
                    className="action-icon-btn watchlist-toggle-btn" 
                    onClick={() => setShowWatchlistMenu(!showWatchlistMenu)} 
                    type="button"
                  >
                    {isWatchlisted ? <BookmarkCheck size={20} className="active-icon" /> : <Bookmark size={20} />}
                    <span>
                      {isWatchlisted 
                        ? (watchlistStatus === "watching" 
                            ? "Watching" 
                            : watchlistStatus === "completed" 
                            ? "Completed" 
                            : "Planning") 
                        : "My List"}
                    </span>
                  </button>
                  {showWatchlistMenu && (
                    <>
                      <div className="dropdown-backplate" onClick={() => setShowWatchlistMenu(false)} style={{ zIndex: 110 }} />
                      <div className="watchlist-menu-dropdown mobile-menu">
                        <div className="mobile-menu-header">Select Status</div>
                        {[
                          { id: "planning", name: "Plan to Watch" },
                          { id: "watching", name: "Watching" },
                          { id: "completed", name: "Completed" }
                        ].map(opt => (
                          <button 
                            key={opt.id}
                            className={`watchlist-menu-item ${watchlistStatus === opt.id ? "active" : ""}`}
                            onClick={() => {
                              handleUpdateWatchlistStatus(opt.id);
                              setShowWatchlistMenu(false);
                            }}
                            type="button"
                          >
                            {opt.name}
                          </button>
                        ))}
                        {isWatchlisted && (
                          <button 
                            className="watchlist-menu-item remove-item"
                            onClick={() => {
                              handleUpdateWatchlistStatus("");
                              setShowWatchlistMenu(false);
                            }}
                            type="button"
                          >
                            Remove from List
                          </button>
                        )}
                      </div>
                    </>
                  )}
                </div>

                <button className="action-icon-btn rate-toggle-btn" onClick={() => setShowMobileRating(!showMobileRating)} type="button">
                  <Star size={20} className={userRating > 0 ? "active-icon" : ""} fill={userRating > 0 ? "currentColor" : "none"} />
                  <span>{userRating > 0 ? `Rated ${userRating}/5` : "Rate"}</span>
                </button>
              </div>

              {/* 5-Star Ratings */}
              {currentUser && activeProfile && (
                <div className={`ratings-interactive ${showMobileRating ? "mobile-show" : ""}`}>
                  <span className="rating-lbl">Rate:</span>
                  <div className="star-row">
                    {[1, 2, 3, 4, 5].map((stars) => (
                      <button
                        key={stars}
                        className={`star-btn ${userRating >= stars ? "active" : ""}`}
                        onClick={() => {
                          handleRatingSelect(stars);
                          setShowMobileRating(false);
                        }}
                        type="button"
                      >
                        <Star size={18} fill={userRating >= stars ? "currentColor" : "none"} />
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>

            <div className="synopsis-box">
              <p className="synopsis-text">
                {info.description?.length > 150 && !showAllSynopsis 
                  ? `${info.description.substring(0, 150)}...` 
                  : info.description}
                {info.description?.length > 150 && (
                  <button 
                    className="see-more-btn" 
                    onClick={() => setShowAllSynopsis(!showAllSynopsis)}
                    type="button"
                  >
                    {showAllSynopsis ? " See Less" : " See More"}
                  </button>
                )}
              </p>
            </div>
          </div>
        </div>
      </div>

      <div className="container detail-content-body">
        <div className="detail-grid-layout">
          {/* Main Content Area */}
          <div className="main-detail-column">
            {/* Episodes Selection */}
            {episodes.length > 0 && (
              <section className="detail-section">
                <div className="section-header-row">
                  <div className="episodes-title-wrapper">
                    <h2 className="section-title"><List size={18} /> Episodes</h2>
                    
                    {/* Netflix/Crunchyroll Season Selector Dropdown */}
                    {seasons.length > 1 && (
                      <div className="season-selector-dropdown-wrapper">
                        <button 
                          className="season-dropdown-toggle-btn"
                          onClick={() => setSeasonDropdownOpen(!seasonDropdownOpen)}
                          type="button"
                        >
                          <span className="dropdown-label">{currentSeasonTitle}</span>
                          <ChevronDown size={14} className={`dropdown-chevron ${seasonDropdownOpen ? "open" : ""}`} />
                        </button>
                        
                        {seasonDropdownOpen && (
                          <>
                            <div className="dropdown-backplate" onClick={() => setSeasonDropdownOpen(false)} />
                            <div className="season-dropdown-menu">
                              {/* TV Seasons Group */}
                              {tvSeasons.length > 0 && (
                                <div className="dropdown-section">
                                  <div className="dropdown-section-header">TV Seasons</div>
                                  {tvSeasons.map((s) => {
                                    const isActive = Number(s.malId) === Number(info.malId);
                                    return (
                                      <div
                                        key={s.malId}
                                        className={`season-dropdown-item ${isActive ? "active" : ""}`}
                                        onClick={() => {
                                          setSeasonDropdownOpen(false);
                                          handleSeasonClick(s);
                                        }}
                                      >
                                        <div className="dropdown-item-season-meta">
                                          {getSeasonDisplayTitle(s)}
                                        </div>
                                        <div className="dropdown-item-season-title" title={s.title}>
                                          {s.title}
                                        </div>
                                      </div>
                                    );
                                  })}
                                </div>
                              )}
                              
                              {/* Movies & Specials Group */}
                              {moviesAndSpecials.length > 0 && (
                                <div className="dropdown-section">
                                  <div className="dropdown-section-header">Movies & Specials</div>
                                  {moviesAndSpecials.map((s) => {
                                    const isActive = Number(s.malId) === Number(info.malId);
                                    const badge = getMediaBadge(s);
                                    return (
                                      <div
                                        key={s.malId}
                                        className={`season-dropdown-item ${isActive ? "active" : ""}`}
                                        onClick={() => {
                                          setSeasonDropdownOpen(false);
                                          handleSeasonClick(s);
                                        }}
                                      >
                                        <div className="dropdown-item-season-meta">
                                          {badge}
                                        </div>
                                        <div className="dropdown-item-season-title" title={s.title}>
                                          {s.title}
                                        </div>
                                      </div>
                                    );
                                  })}
                                </div>
                              )}
                            </div>
                          </>
                        )}
                      </div>
                    )}
                  </div>
                  
                  {info.stats?.episodes?.dub && info.stats?.episodes?.sub && (
                    <div className="sub-dub-tabs">
                      <button 
                        className={audioPreference === "sub" ? "active" : ""} 
                        onClick={() => setAudioPreference("sub")}
                      >
                        Subbed
                      </button>
                      <button 
                        className={audioPreference === "dub" ? "active" : ""} 
                        onClick={() => setAudioPreference("dub")}
                      >
                        Dubbed
                      </button>
                    </div>
                  )}
                </div>
                
                <div className="episodes-grid-list">
                  {episodes.map((ep) => {
                    const isWatched = historyItem?.episodeId === ep.episodeId || (historyItem?.episodeNumber > ep.number);
                    let progressPercent = 0;
                    if (historyItem) {
                      if (historyItem.episodeId === ep.episodeId) {
                        if (historyItem.totalDuration > 0) {
                          progressPercent = (historyItem.progressPosition / historyItem.totalDuration) * 100;
                        }
                      } else if (historyItem.episodeNumber > ep.number) {
                        progressPercent = 100;
                      }
                    }

                    return (
                      <Link 
                        key={ep.episodeId} 
                        to={`/watch/${animeId}/${ep.episodeId}?audio=${audioPreference}`} 
                        className={`episode-grid-card ${isWatched ? "watched" : ""}`}
                      >
                        <div className="ep-num">
                          <Play size={12} className="ep-play-icon" fill="currentColor" />
                          <span>{ep.number}</span>
                        </div>
                        <div className="ep-details">
                          <h4 className="ep-title" title={ep.title || `Episode ${ep.number}`}>
                            {ep.title || `Episode ${ep.number}`}
                          </h4>
                          {ep.isFiller && <span className="filler-tag">Filler</span>}
                        </div>
                        {progressPercent > 0 && (
                          <div className="ep-progress-bar-container">
                            <div className="ep-progress-bar-fill" style={{ width: `${progressPercent}%` }}></div>
                          </div>
                        )}
                      </Link>
                    );
                  })}
                </div>
              </section>
            )}

            {/* TV Seasons Selection */}
            {tvSeasons.length > 1 && (
              <section className="detail-section animate-slide-up">
                <h2 className="section-title"><RefreshCw size={18} /> Alternative Seasons</h2>
                <div className="horizontal-scroll-row">
                  {tvSeasons.map((season) => (
                    <div 
                      key={season.malId} 
                      className={`season-card-item ${Number(season.malId) === Number(info.malId) ? "active" : ""}`}
                      onClick={() => handleSeasonClick(season)}
                    >
                      <div className="season-poster-wrapper">
                        {season.poster ? (
                          <img src={season.poster} alt={season.title} className="season-poster" />
                        ) : (
                          <div className="season-poster-fallback flex-center">
                            <span>{season.title.charAt(0)}</span>
                          </div>
                        )}
                        <span className="season-badge">{getShortSeasonBadge(season)}</span>
                      </div>
                      <h4 className="season-title" title={season.title}>{season.title}</h4>
                    </div>
                  ))}
                </div>
              </section>
            )}

            {/* Movies & Specials Selection */}
            {moviesAndSpecials.length > 0 && (
              <section className="detail-section animate-slide-up">
                <h2 className="section-title"><RefreshCw size={18} /> Movies & Specials</h2>
                <div className="horizontal-scroll-row">
                  {moviesAndSpecials.map((season) => (
                    <div 
                      key={season.malId} 
                      className={`season-card-item ${Number(season.malId) === Number(info.malId) ? "active" : ""}`}
                      onClick={() => handleSeasonClick(season)}
                    >
                      <div className="season-poster-wrapper">
                        {season.poster ? (
                          <img src={season.poster} alt={season.title} className="season-poster" />
                        ) : (
                          <div className="season-poster-fallback flex-center">
                            <span>{season.title.charAt(0)}</span>
                          </div>
                        )}
                        <span className="season-badge type-badge">{getMediaType(season)}</span>
                      </div>
                      <h4 className="season-title" title={season.title}>{season.title}</h4>
                    </div>
                  ))}
                </div>
              </section>
            )}

            {/* Characters Section */}
            {characters.length > 0 && (
              <section className="detail-section">
                <h2 className="section-title"><Users size={18} /> Characters</h2>
                <div className="horizontal-scroll-row">
                  {characters.map((char) => (
                    <div key={char.id} className="character-card">
                      <div className="char-avatar-wrapper">
                        <img src={char.poster} alt={char.name} className="char-avatar-img" />
                      </div>
                      <h4 className="char-name" title={char.name}>{char.name}</h4>
                      <p className="char-role">{char.role || "Supporting"}</p>
                    </div>
                  ))}
                </div>
              </section>
            )}

            {/* Recommendations */}
            {recommendedAnimes.length > 0 && (
              <section className="detail-section">
                <h2 className="section-title">Recommended Anime</h2>
                <div className="grid-layout">
                  {recommendedAnimes.slice(0, 12).map((rec) => (
                    <AnimeCard 
                      key={rec.id}
                      id={rec.id}
                      name={rec.name}
                      poster={rec.poster}
                      type={rec.type}
                      episodes={rec.episodes}
                    />
                  ))}
                </div>
              </section>
            )}
          </div>

          {/* Sidebar Meta Column */}
          <div className="sidebar-detail-column">
            <div className="more-info-card">
              <h3>More Info</h3>
              <div className="info-item">
                <span className="lbl">Status</span>
                <span className="val">{moreInfo?.status || "Unknown"}</span>
              </div>
              <div className="info-item">
                <span className="lbl">Aired</span>
                <span className="val">{moreInfo?.aired || "Unknown"}</span>
              </div>
              <div className="info-item">
                <span className="lbl">Premiered</span>
                <span className="val">{moreInfo?.premiered || "Unknown"}</span>
              </div>
              <div className="info-item">
                <span className="lbl">Studio</span>
                <span className="val">{moreInfo?.studio || "Unknown"}</span>
              </div>
              <div className="info-item list">
                <span className="lbl">Producers</span>
                <div className="val-tags">
                  {moreInfo?.producers?.map((p) => (
                    <span key={p} className="val-tag">{p}</span>
                  ))}
                </div>
              </div>
              <div className="info-item list">
                <span className="lbl">Genres</span>
                <div className="val-tags">
                  {moreInfo?.genres?.map((g) => (
                    <span key={g} className="val-tag genre">{g}</span>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <style>{`
        /* Watchlist Dropdown Menus */
        .watchlist-dropdown-wrapper {
          position: relative;
        }
        .watchlist-menu-dropdown {
          position: absolute;
          top: calc(100% + 8px);
          left: 0;
          background-color: #141414;
          border: 1px solid var(--border);
          border-radius: 8px;
          min-width: 180px;
          box-shadow: 0 10px 25px rgba(0, 0, 0, 0.5);
          display: flex;
          flex-direction: column;
          padding: 6px;
          z-index: 100;
          animation: scaleHUD 0.15s ease-out;
        }
        .watchlist-menu-item {
          background: none;
          border: none;
          color: var(--text-secondary);
          padding: 10px 14px;
          text-align: left;
          font-size: 0.85rem;
          font-family: var(--font-family);
          font-weight: 600;
          border-radius: 4px;
          cursor: pointer;
          transition: var(--transition);
        }
        .watchlist-menu-item:hover {
          background-color: #242424;
          color: white;
        }
        .watchlist-menu-item.active {
          background-color: var(--primary);
          color: white;
        }
        .watchlist-menu-item.remove-item {
          border-top: 1px solid var(--border);
          margin-top: 4px;
          border-radius: 0 0 4px 4px;
          color: #ff4d4d;
        }
        .watchlist-menu-item.remove-item:hover {
          background-color: rgba(255, 77, 77, 0.1);
        }
        
        /* Mobile specific positioning */
        .mobile-watchlist-wrapper {
          position: relative;
        }
        .watchlist-menu-dropdown.mobile-menu {
          position: fixed;
          bottom: 0;
          left: 0;
          right: 0;
          top: auto;
          width: 100%;
          border-radius: 16px 16px 0 0;
          border: none;
          border-top: 1px solid var(--border);
          background-color: #141414;
          padding: 16px;
          box-shadow: 0 -10px 30px rgba(0,0,0,0.6);
          z-index: 120;
          animation: slideUpMobile 0.25s cubic-bezier(0.16, 1, 0.3, 1);
        }
        .mobile-menu-header {
          font-size: 0.95rem;
          font-weight: 700;
          color: white;
          margin-bottom: 12px;
          text-align: center;
          padding-bottom: 8px;
          border-bottom: 1px solid var(--border);
        }
        @keyframes slideUpMobile {
          from { transform: translateY(100%); }
          to { transform: translateY(0); }
        }

        .detail-page {
          padding-bottom: 4rem;
        }
        .detail-error-page {
          min-height: 70vh;
        }
        .detail-hero {
          position: relative;
          width: 100%;
          min-height: 60vh;
          display: flex;
          align-items: flex-end;
          padding: 8rem 0 3rem;
        }
        .hero-backdrop-image {
          position: absolute;
          inset: 0;
          background-size: cover;
          background-position: center 20%;
          z-index: 0;
        }
        .hero-gradient {
          position: absolute;
          inset: 0;
          background: linear-gradient(to top, var(--bg) 0%, rgba(10, 10, 10, 0.7) 60%, rgba(10, 10, 10, 0.9) 100%);
          z-index: 1;
        }
        .hero-container {
          position: relative;
          z-index: 2;
          display: flex;
          gap: 2.5rem;
          width: 100%;
        }
        .hero-poster-wrapper {
          width: 240px;
          flex-shrink: 0;
          box-shadow: 0 10px 30px rgba(0, 0, 0, 0.8);
          border-radius: 8px;
          overflow: hidden;
          background-color: var(--bg-card);
        }
        .hero-poster {
          width: 100%;
          display: block;
          aspect-ratio: 1/1.42;
          object-fit: cover;
        }
        .hero-meta-content {
          flex-grow: 1;
          display: flex;
          flex-direction: column;
          justify-content: flex-end;
        }
        .anime-title-h1 {
          font-size: clamp(1.8rem, 4vw, 2.8rem);
          font-weight: 800;
          line-height: 1.2;
          color: white;
          margin-bottom: 1rem;
        }
        .meta-stats-row {
          display: flex;
          flex-wrap: wrap;
          gap: 0.6rem;
          margin-bottom: 1.5rem;
          align-items: center;
        }
        .stat-pill {
          background-color: rgba(255, 255, 255, 0.1);
          color: var(--text-primary);
          padding: 3px 10px;
          border-radius: 4px;
          font-size: 0.8rem;
          font-weight: 600;
        }
        .stat-pill.rating {
          background-color: var(--secondary);
          color: #0A0A0A;
          font-weight: 800;
        }
        .stat-pill.quality {
          background-color: var(--primary);
          color: white;
          font-weight: 800;
        }
        
        /* Version switcher */
        .version-switcher-row {
          display: flex;
          align-items: center;
          gap: 0.8rem;
          margin-bottom: 1.5rem;
        }
        .version-label {
          font-size: 0.85rem;
          font-weight: 700;
          color: var(--text-secondary);
        }
        .version-choice-btn {
          background: rgba(255, 255, 255, 0.05);
          border: 1px solid var(--border);
          color: var(--text-secondary);
          padding: 4px 12px;
          border-radius: 4px;
          cursor: pointer;
          font-size: 0.8rem;
          font-weight: 600;
          font-family: var(--font-family);
          transition: var(--transition);
          display: flex;
          align-items: center;
          gap: 4px;
        }
        .version-choice-btn.active {
          background: white;
          color: #0A0A0A;
          border-color: white;
        }
        .spin-icon {
          animation: spin 1s linear infinite;
        }
        @keyframes spin {
          100% { transform: rotate(360deg); }
        }

        .actions-row {
          display: flex;
          flex-wrap: wrap;
          align-items: center;
          gap: 1rem;
          margin-bottom: 1.5rem;
        }
        .watch-now-btn {
          padding: 0.85rem 2rem;
          font-size: 1rem;
        }
        .watchlist-toggle-btn {
          padding: 0.85rem 1.5rem;
          font-size: 1rem;
        }
        .watchlist-toggle-btn.desktop-only {
          display: inline-flex;
        }
        .mobile-action-buttons-group {
          display: none;
        }
        .ratings-interactive {
          display: flex;
          align-items: center;
          gap: 8px;
          background-color: rgba(255, 255, 255, 0.05);
          padding: 8px 16px;
          border-radius: 6px;
          border: 1px solid var(--border);
        }
        .rating-lbl {
          font-size: 0.8rem;
          text-transform: uppercase;
          letter-spacing: 0.05em;
          font-weight: 700;
          color: var(--text-secondary);
        }
        .star-row {
          display: flex;
          gap: 2px;
        }
        .star-btn {
          background: none;
          border: none;
          color: var(--text-muted);
          cursor: pointer;
          transition: var(--transition);
        }
        .star-btn.active {
          color: var(--secondary);
        }
        .star-btn:hover {
          transform: scale(1.15);
        }

        .synopsis-box {
          background-color: rgba(0, 0, 0, 0.4);
          padding: 1.2rem;
          border-radius: 8px;
          border-left: 3px solid var(--primary);
        }
        .synopsis-text {
          font-size: 0.95rem;
          line-height: 1.6;
          color: var(--text-secondary);
        }
        .see-more-btn {
          background: none;
          border: none;
          color: var(--primary);
          font-weight: 700;
          cursor: pointer;
          font-family: var(--font-family);
          font-size: 0.85rem;
          padding: 0 4px;
          display: inline;
          transition: var(--transition);
        }
        .see-more-btn:hover {
          text-decoration: underline;
          color: var(--primary-hover);
        }

        /* Detail body contents */
        .detail-content-body {
          margin-top: 3rem;
        }
        .detail-grid-layout {
          display: grid;
          grid-template-columns: 1fr 320px;
          gap: 3rem;
        }
        .main-detail-column {
          display: flex;
          flex-direction: column;
          gap: 3rem;
        }
        .detail-section {
          width: 100%;
        }
        .section-header-row {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: 1.2rem;
          border-bottom: 1px solid var(--border);
          padding-bottom: 8px;
        }
        .section-header-row .section-title {
          margin-bottom: 0;
          display: flex;
          align-items: center;
          gap: 6px;
        }
        .sub-dub-tabs {
          display: flex;
          background-color: var(--bg-card);
          border: 1px solid var(--border);
          border-radius: 6px;
          padding: 2px;
        }
        .sub-dub-tabs button {
          background: none;
          border: none;
          color: var(--text-secondary);
          padding: 4px 12px;
          font-size: 0.8rem;
          font-weight: 700;
          border-radius: 4px;
          cursor: pointer;
          font-family: var(--font-family);
        }
        .sub-dub-tabs button.active {
          background-color: var(--primary);
          color: white;
        }

        /* Episodes selector list */
        .episodes-grid-list {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
          gap: 1rem;
        }
        .episode-grid-card {
          display: flex;
          align-items: center;
          background-color: var(--bg-card);
          border: 1px solid var(--border);
          border-radius: 6px;
          text-decoration: none;
          color: white;
          overflow: hidden;
          transition: var(--transition);
          position: relative;
          padding-bottom: 3px; /* Space for the progress bar at the bottom */
        }
        .episode-grid-card:hover {
          border-color: var(--primary);
          background-color: #1c1c1c;
          transform: translateY(-2px);
        }
        .episode-grid-card.watched {
          opacity: 0.65;
          border-color: rgba(255, 255, 255, 0.1);
        }
        .ep-num {
          background-color: rgba(255, 255, 255, 0.05);
          width: 50px;
          align-self: stretch;
          display: flex;
          align-items: center;
          justify-content: center;
          font-weight: 800;
          font-size: 1.1rem;
          color: var(--text-secondary);
          border-right: 1px solid var(--border);
          flex-shrink: 0;
        }
        .ep-play-icon {
          display: none;
        }
        .ep-progress-bar-container {
          position: absolute;
          bottom: 0;
          left: 0;
          right: 0;
          height: 3px;
          background: rgba(255, 255, 255, 0.15);
          overflow: hidden;
        }
        .ep-progress-bar-fill {
          height: 100%;
          background: var(--primary);
        }
        .episode-grid-card:hover .ep-num {
          background-color: var(--primary);
          color: white;
        }
        .ep-details {
          padding: 10px 14px;
          display: flex;
          flex-direction: column;
          gap: 3px;
          flex-grow: 1;
          min-width: 0;
        }
        .ep-title {
          font-size: 0.85rem;
          font-weight: 600;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .filler-tag {
          align-self: flex-start;
          background-color: rgba(245, 166, 35, 0.15);
          color: var(--secondary);
          font-size: 0.65rem;
          font-weight: 700;
          padding: 1px 4px;
          border-radius: 3px;
        }

        /* Characters lists */
        .character-card {
          flex: 0 0 110px;
          display: flex;
          flex-direction: column;
          align-items: center;
          text-align: center;
          gap: 4px;
        }
        .char-avatar-wrapper {
          width: 90px;
          height: 90px;
          border-radius: 50%;
          overflow: hidden;
          border: 2px solid var(--border);
          background-color: var(--bg-card);
        }
        .char-avatar-img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }
        .char-name {
          font-size: 0.8rem;
          font-weight: 600;
          color: white;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
          width: 100%;
        }
        .char-role {
          font-size: 0.7rem;
          color: var(--text-muted);
        }

        /* Horizontal scrolling row */
        .horizontal-scroll-row {
          display: flex;
          gap: 1.2rem;
          overflow-x: auto;
          padding: 8px 0 20px;
          scrollbar-width: none;
        }
        .horizontal-scroll-row::-webkit-scrollbar {
          display: none;
        }

        /* Episodes title section */
        .episodes-title-wrapper {
          display: flex;
          align-items: center;
          gap: 1rem;
          position: relative;
        }

        .season-selector-dropdown-wrapper {
          position: relative;
          z-index: 100;
        }

        .season-dropdown-toggle-btn {
          background-color: var(--bg-input);
          border: 1px solid var(--border);
          color: white;
          padding: 5px 12px;
          border-radius: 4px;
          font-family: var(--font-family);
          font-size: 0.8rem;
          font-weight: 700;
          cursor: pointer;
          display: flex;
          align-items: center;
          gap: 6px;
          transition: var(--transition);
        }

        .season-dropdown-toggle-btn:hover {
          border-color: var(--primary);
          background-color: #242424;
        }

        .dropdown-chevron {
          transition: transform 0.2s ease;
          color: var(--text-secondary);
        }

        .dropdown-chevron.open {
          transform: rotate(180deg);
        }

        .dropdown-backplate {
          position: fixed;
          inset: 0;
          z-index: 98;
        }

        .season-dropdown-menu {
          position: absolute;
          top: calc(100% + 6px);
          left: 0;
          width: 290px;
          max-height: 320px;
          overflow-y: auto;
          background-color: #141414;
          border: 1px solid var(--border);
          border-radius: 6px;
          box-shadow: 0 10px 25px rgba(0, 0, 0, 0.6);
          z-index: 99;
          padding: 4px;
        }

        .dropdown-section {
          margin-bottom: 8px;
        }
        .dropdown-section:last-child {
          margin-bottom: 0;
        }
        .dropdown-section-header {
          font-size: 0.65rem;
          color: var(--text-muted);
          font-weight: 800;
          text-transform: uppercase;
          letter-spacing: 0.05em;
          padding: 6px 12px 2px;
          border-bottom: 1px solid rgba(255, 255, 255, 0.03);
          margin-bottom: 4px;
        }

        .season-dropdown-item {
          padding: 8px 12px;
          border-radius: 4px;
          cursor: pointer;
          transition: var(--transition);
          display: flex;
          flex-direction: column;
          gap: 1px;
        }

        .season-dropdown-item:hover {
          background-color: rgba(255, 255, 255, 0.05);
        }

        .season-dropdown-item.active {
          background-color: rgba(229, 9, 20, 0.15);
          border-left: 3px solid var(--primary);
          padding-left: 9px;
        }

        .dropdown-item-season-meta {
          font-size: 0.65rem;
          color: var(--text-muted);
          font-weight: 700;
          text-transform: uppercase;
        }

        .season-dropdown-item.active .dropdown-item-season-meta {
          color: var(--primary);
        }

        .dropdown-item-season-title {
          font-size: 0.8rem;
          font-weight: 600;
          color: var(--text-primary);
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        /* Seasons lists */
        .season-card-item {
          flex: 0 0 130px;
          cursor: pointer;
          display: flex;
          flex-direction: column;
          gap: 6px;
          transition: var(--transition);
        }
        .season-card-item:hover {
          transform: translateY(-4px);
        }
        .season-poster-wrapper {
          position: relative;
          width: 100%;
          padding-top: 142%;
          border-radius: 6px;
          overflow: hidden;
          border: 2px solid transparent;
          background-color: var(--bg-card);
          box-shadow: 0 4px 10px rgba(0, 0, 0, 0.4);
          transition: var(--transition);
        }
        .season-card-item:hover .season-poster-wrapper {
          border-color: var(--primary);
          box-shadow: 0 4px 15px rgba(229, 9, 20, 0.3);
        }
        .season-card-item.active .season-poster-wrapper {
          border-color: var(--primary);
          box-shadow: 0 4px 15px rgba(229, 9, 20, 0.45);
        }
        .season-poster {
          position: absolute;
          inset: 0;
          width: 100%;
          height: 100%;
          object-fit: cover;
          transition: transform 0.4s ease;
        }
        .season-card-item:hover .season-poster {
          transform: scale(1.05);
        }
        .season-poster-fallback {
          position: absolute;
          inset: 0;
          background-color: #242424;
          font-weight: 800;
          font-size: 2rem;
          color: #555;
        }
        .season-badge {
          position: absolute;
          top: 6px;
          left: 6px;
          background-color: var(--primary);
          color: white;
          padding: 1px 4px;
          font-size: 0.65rem;
          font-weight: 700;
          border-radius: 3px;
          box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
        }
        .season-badge.type-badge {
          background-color: var(--secondary);
          color: #0A0A0A;
          font-weight: 800;
        }
        .season-title {
          font-size: 0.8rem;
          font-weight: 600;
          color: var(--text-secondary);
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          overflow: hidden;
          text-overflow: ellipsis;
          line-height: 1.3;
          height: 2.6em;
        }
        .season-card-item:hover .season-title {
          color: white;
        }

        /* More Info Card sidebar */
        .more-info-card {
          background-color: var(--bg-card);
          border: 1px solid var(--border);
          border-radius: 8px;
          padding: 1.5rem;
          display: flex;
          flex-direction: column;
          gap: 1.2rem;
          position: sticky;
          top: calc(var(--header-height) + 20px);
        }
        .more-info-card h3 {
          font-size: 1.1rem;
          font-weight: 700;
          border-bottom: 1px solid var(--border);
          padding-bottom: 8px;
          margin-bottom: 4px;
        }
        .info-item {
          display: flex;
          justify-content: space-between;
          font-size: 0.85rem;
          gap: 15px;
        }
        .info-item.list {
          flex-direction: column;
          gap: 6px;
        }
        .info-item .lbl {
          font-weight: 600;
          color: var(--text-secondary);
        }
        .info-item .val {
          color: white;
          text-align: right;
        }
        .val-tags {
          display: flex;
          flex-wrap: wrap;
          gap: 4px;
        }
        .val-tag {
          background-color: rgba(255, 255, 255, 0.05);
          padding: 2px 6px;
          border-radius: 4px;
          font-size: 0.75rem;
          color: var(--text-secondary);
        }
        .val-tag.genre {
          background-color: rgba(229, 9, 20, 0.1);
          color: #ff8080;
          font-weight: 600;
        }

        @media (max-width: 992px) {
          .detail-grid-layout {
            grid-template-columns: 1fr;
          }
          .more-info-card {
            position: relative;
            top: 0;
          }
        }
        @media (max-width: 768px) {
          .desktop-only {
            display: none !important;
          }
          .detail-hero {
            display: flex;
            flex-direction: column;
            background: none !important;
            padding: 0;
            min-height: auto;
          }
          .hero-backdrop-image {
            position: relative;
            width: 100%;
            height: 0;
            padding-top: 56.25%; /* 16:9 Banner Aspect Ratio */
            background-size: cover;
            background-position: center;
            z-index: 1;
          }
          .hero-gradient {
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            height: 56.25vw;
            background: linear-gradient(to top, var(--bg) 0%, rgba(10, 10, 10, 0.3) 50%, rgba(10, 10, 10, 0.6) 100%);
            z-index: 2;
          }
          .hero-container {
            display: block;
            width: 100%;
            padding: 1.5rem 4%;
            margin-top: 0;
            position: relative;
            z-index: 3;
            text-align: left !important;
          }
          .hero-poster-wrapper {
            display: none !important;
          }
          .hero-meta-content {
            align-items: flex-start;
          }
          .anime-title-h1 {
            font-size: 1.6rem;
            text-align: left;
            margin-bottom: 0.5rem;
            line-height: 1.3;
          }
          .meta-stats-row {
            justify-content: flex-start !important;
            margin-bottom: 1rem;
            gap: 0.5rem;
          }
          .stat-pill {
            font-size: 0.75rem;
            padding: 2px 8px;
          }
          .actions-row {
            flex-direction: column;
            align-items: stretch;
            width: 100%;
            gap: 0.8rem;
            margin-bottom: 1.2rem;
          }
          .watch-now-btn {
            width: 100%;
            padding: 0.8rem 1.5rem;
            font-size: 0.95rem;
            justify-content: center;
            display: flex;
            align-items: center;
          }
          .mobile-action-buttons-group {
            display: flex;
            justify-content: flex-start;
            gap: 2.5rem;
            width: 100%;
            margin-top: 0.4rem;
            padding-left: 0.5rem;
          }
          .action-icon-btn {
            background: none;
            border: none;
            color: var(--text-secondary);
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 6px;
            cursor: pointer;
            font-size: 0.75rem;
            font-weight: 600;
            font-family: var(--font-family);
            transition: var(--transition);
            padding: 4px 8px;
          }
          .action-icon-btn:hover, .action-icon-btn:active {
            color: white;
          }
          .action-icon-btn .active-icon {
            color: var(--primary);
          }
          .ratings-interactive {
            display: none;
            width: 100%;
            background-color: var(--bg-card);
            border: 1px solid var(--border);
            padding: 10px 16px;
            border-radius: 8px;
            justify-content: center;
            gap: 15px;
            margin-top: 0.5rem;
            animation: fadeIn 0.2s ease-out;
          }
          .ratings-interactive.mobile-show {
            display: flex;
            align-items: center;
          }
          .synopsis-box {
            background: none;
            padding: 0;
            border-left: none;
            margin-top: 0.5rem;
          }
          .synopsis-text {
            font-size: 0.85rem;
            line-height: 1.5;
            color: var(--text-secondary);
            text-align: left;
          }
          .episodes-grid-list {
            grid-template-columns: 1fr;
            gap: 0.8rem;
          }
          .episode-grid-card {
            border-radius: 6px;
            padding: 4px 0;
            position: relative;
            background-color: var(--bg-card);
            border-color: var(--border);
          }
          .ep-num {
            width: 44px;
            font-size: 0.95rem;
            border-right: 1px solid var(--border);
            flex-direction: column;
            gap: 2px;
            background-color: rgba(255, 255, 255, 0.02);
          }
          .ep-play-icon {
            display: block;
            color: var(--text-muted);
            margin-bottom: 2px;
          }
          .episode-grid-card:hover .ep-play-icon {
            color: white;
          }
          .ep-details {
            padding: 8px 12px;
          }
          .ep-title {
            font-size: 0.85rem;
          }
        }
      `}</style>
    </div>
  );
}
