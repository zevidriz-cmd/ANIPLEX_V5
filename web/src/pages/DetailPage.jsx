import React, { useEffect, useState, useMemo } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { doc, getDoc, setDoc, deleteDoc } from "firebase/firestore";
import { db } from "../config/firebase";
import { useAuth } from "../context/AuthContext";
import { useProfile } from "../context/ProfileContext";
import { 
  getAnimeDetail, getEpisodes, getCharacters, 
  getSeasons, resolveMAL, search, STREAM_PROXY_BASE 
} from "../services/api";
import { resolveTmdId, fetchTmdSeasons, buildArcsFromSeasons } from "../services/tmdb";
import { DetailsShimmer } from "../components/Shimmer";
import AnimeCard from "../components/AnimeCard";
import { 
  Play, Bookmark, BookmarkCheck, Star, Users, List, 
  ChevronRight, ArrowLeft, RefreshCw, ChevronDown, Film, Check, RotateCcw,
  CheckSquare, Link2
} from "lucide-react";

// Seasons cache to prevent redundant fetching and shimmer loading
const getCachedSeasons = (malId) => {
  try {
    const cached = sessionStorage.getItem(`seasons_v6_${malId}`);
    return cached ? JSON.parse(cached) : null;
  } catch {
    return null;
  }
};

const setCachedSeasons = (malId, data) => {
  try {
    sessionStorage.setItem(`seasons_v6_${malId}`, JSON.stringify(data));
  } catch (e) {
    console.warn("Error caching seasons:", e);
  }
};

const getCachedFillers = (malId) => {
  try {
    const cached = sessionStorage.getItem(`fillers_${malId}`);
    return cached ? JSON.parse(cached) : null;
  } catch {
    return null;
  }
};

const setCachedFillers = (malId, data) => {
  try {
    sessionStorage.setItem(`fillers_${malId}`, JSON.stringify(data));
  } catch (e) {
    console.warn("Error caching fillers:", e);
  }
};

const fetchJikanFillers = async (malId, totalEps) => {
  try {
    const pageCount = Math.ceil(totalEps / 100);
    const fillerMap = {};
    const recapMap = {};

    for (let page = 1; page <= pageCount; page++) {
      if (page > 1) {
        await new Promise((resolve) => setTimeout(resolve, 500));
      }
      const url = `https://api.jikan.moe/v4/anime/${malId}/episodes?page=${page}`;
      const res = await fetch(url);
      if (!res.ok) {
        if (res.status === 429) {
          // Rate limit: backoff and retry once
          await new Promise((resolve) => setTimeout(resolve, 1000));
          const retryRes = await fetch(url);
          if (!retryRes.ok) break;
          const retryJson = await retryRes.json();
          if (retryJson.data) {
            retryJson.data.forEach((ep) => {
              if (ep.filler) fillerMap[ep.mal_id] = true;
              if (ep.recap) recapMap[ep.mal_id] = true;
            });
            continue;
          }
        }
        break;
      }
      const json = await res.json();
      if (json.data && json.data.length > 0) {
        json.data.forEach((ep) => {
          if (ep.filler) fillerMap[ep.mal_id] = true;
          if (ep.recap) recapMap[ep.mal_id] = true;
        });
      } else {
        break;
      }
    }
    return { fillerMap, recapMap };
  } catch (err) {
    console.warn("Error fetching fillers:", err);
    return { fillerMap: {}, recapMap: {} };
  }
};


const getMediaType = (season) => {
  if (season.format) {
    const f = season.format.toUpperCase();
    if (f === "MOVIE") return "Movie";
    if (f === "SPECIAL") return "Special";
    if (f === "OVA") return "OVA";
    if (f === "ONA") return "ONA";
    if (f === "TV" || f === "TV_SHORT") return "TV";
  }

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
  if (season.relationType === "MAIN" && season.seasonNumber > 0) {
    return `Season ${season.seasonNumber}`;
  }
  return getMediaBadge(season);
};

const getShortSeasonBadge = (season) => {
  if (!season) return "";
  if (season.relationType === "MAIN" && season.seasonNumber > 0) {
    return `S${season.seasonNumber}`;
  }
  const type = getMediaType(season);
  if (type !== "TV") return type.substring(0, 3).toUpperCase();
  return "S1";
};

const checkIsPart = (titleA, titleB) => {
  return false;
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
  const [seasonsLoading, setSeasonsLoading] = useState(false);
  const [relations, setRelations] = useState([]);
  const [relationsLoading, setRelationsLoading] = useState(false);
  const [resolvingRelationId, setResolvingRelationId] = useState(null);
  const [audioPreference, setAudioPreference] = useState(() => {
    const saved = localStorage.getItem("anistream_preferred_mode") || localStorage.getItem("anistream_audio_preference");
    return (saved && ["sub", "hsub", "dub"].includes(saved)) ? saved : "hsub";
  });

  // Firestore Sync States
  const [isWatchlisted, setIsWatchlisted] = useState(false);
  const [watchlistStatus, setWatchlistStatus] = useState("");
  const [showWatchlistMenu, setShowWatchlistMenu] = useState(false);
  const [historyItem, setHistoryItem] = useState(null);
  const [isPrequelCompleted, setIsPrequelCompleted] = useState(false);
  const [userRating, setUserRating] = useState(0);
  
  // Versions
  const [hasAltVersion, setHasAltVersion] = useState(false);
  const [currentVersion, setCurrentVersion] = useState("censored"); // 'censored' or 'uncensored'
  const [resolvingVersion, setResolvingVersion] = useState(false);
  const [seasonDropdownOpen, setSeasonDropdownOpen] = useState(false);
  const [selectedBatchIndex, setSelectedBatchIndex] = useState(0);
  const [episodeDropdownOpen, setEpisodeDropdownOpen] = useState(false);
  const [showAllSynopsis, setShowAllSynopsis] = useState(false);
  const [showMoreDetails, setShowMoreDetails] = useState(false);
  const [shareSuccess, setShareSuccess] = useState(false);

  // Determine next season in franchise if available
  const nextFranchiseSeason = useMemo(() => {
    if (!seasons || seasons.length <= 1) return null;
    const mainSeasons = seasons.filter(s => s.relationType === "MAIN" && s.seasonNumber > 0 && s.format !== "MOVIE");
    const currentIndex = mainSeasons.findIndex(s => String(s.resolvedId) === String(animeId) || String(s.malId) === String(detail?.anime?.info?.malId));
    if (currentIndex !== -1 && currentIndex < mainSeasons.length - 1) {
      return mainSeasons[currentIndex + 1];
    }
    return null;
  }, [seasons, animeId, detail]);

  // Determine watch / resume / next season link
  const isSeasonCompleted = isPrequelCompleted || (historyItem && historyItem.episodeNumber >= (episodes?.length || 0) && (historyItem.progressPosition / (historyItem.totalDuration || 1)) >= 0.90);
  const playEpisodeNumber = historyItem?.episodeNumber || 1;

  let playButtonText = "Start Watching E1";
  let playTargetUrl = episodes && episodes[0] ? `/watch/${animeId}/${episodes[0].episodeId}?audio=${audioPreference}` : `/anime/${animeId}`;

  if (isSeasonCompleted) {
    if (nextFranchiseSeason && nextFranchiseSeason.resolvedId) {
      const sNum = nextFranchiseSeason.seasonNumber ? `Season ${nextFranchiseSeason.seasonNumber}` : "Next Season";
      playButtonText = `Watch ${sNum}`;
      playTargetUrl = `/anime/${nextFranchiseSeason.resolvedId}`;
    } else {
      playButtonText = "Rewatch E1";
    }
  } else if (historyItem && historyItem.episodeId) {
    playButtonText = `Resume E${historyItem.episodeNumber}`;
    playTargetUrl = `/watch/${animeId}/${historyItem.episodeId}?audio=${audioPreference}`;
  }

  // Batching & Arc logic
  const [tmdbSeasons, setTmdbSeasons] = useState(null);

  const batches = useMemo(() => {
    if (!episodes || episodes.length === 0) return [];
    
    // If we have TMDb seasons, use them to build story arcs!
    if (tmdbSeasons && tmdbSeasons.length > 0) {
      const arcs = buildArcsFromSeasons(tmdbSeasons, episodes);
      if (arcs && arcs.length > 0) {
        return arcs.map(arc => {
          const isFinished = arc.episodes.every(ep => {
            return historyItem?.episodeId === ep.episodeId || (historyItem?.episodeNumber > ep.number);
          });
          return {
            ...arc,
            isFinished
          };
        });
      }
    }
    
    // Fallback: standard 25-episode batches
    const BATCH_SIZE = 25;
    const fallbackBatches = [];
    for (let i = 0; i < episodes.length; i += BATCH_SIZE) {
      const start = i + 1;
      const end = Math.min(i + BATCH_SIZE, episodes.length);
      const batchEps = episodes.slice(i, i + BATCH_SIZE);
      
      const isFinished = batchEps.every(ep => {
        return historyItem?.episodeId === ep.episodeId || (historyItem?.episodeNumber > ep.number);
      });

      fallbackBatches.push({
        index: fallbackBatches.length,
        start,
        end,
        label: `Episodes ${start}-${end}`,
        episodes: batchEps,
        isFinished
      });
    }
    return fallbackBatches;
  }, [episodes, historyItem, tmdbSeasons]);

  // Active episodes for current batch selection
  const activeEpisodes = batches.length > 1 ? (batches[selectedBatchIndex]?.episodes || []) : episodes;

  // Auto-select the batch index containing the user's next episode to watch
  useEffect(() => {
    if (batches.length > 1) {
      const targetEp = playEpisodeNumber || 1;
      const idx = batches.findIndex(b => targetEp >= b.start && targetEp <= b.end);
      if (idx >= 0 && idx < batches.length) {
        setSelectedBatchIndex(idx);
      } else {
        setSelectedBatchIndex(0);
      }
    } else {
      setSelectedBatchIndex(0);
    }
  }, [batches, playEpisodeNumber]);

  // Load TMDb story arcs
  useEffect(() => {
    const malId = detail?.anime?.info?.malId;
    if (!malId || malId === "0" || malId === "") {
      setTmdbSeasons(null);
      return;
    }

    let isMounted = true;
    async function getArcs() {
      try {
        const tmdbId = await resolveTmdId(malId);
        if (tmdbId && isMounted) {
          const seasons = await fetchTmdSeasons(tmdbId);
          if (seasons && seasons.length > 0 && isMounted) {
            setTmdbSeasons(seasons);
          }
        }
      } catch (err) {
        console.warn("Failed to load TMDb story arcs:", err);
      }
    }
    
    getArcs();
    return () => {
      isMounted = false;
    };
  }, [detail]);

  // Load Jikan Relations
  useEffect(() => {
    const malId = detail?.anime?.info?.malId;
    if (!malId || malId === "0" || malId === "") {
      setRelations([]);
      return;
    }

    let isMounted = true;
    async function loadRelations() {
      setRelationsLoading(true);
      try {
        const res = await fetch(`https://api.jikan.moe/v4/anime/${malId}/relations`);
        if (!res.ok) throw new Error("Relations fetch failed");
        const json = await res.json();
        if (isMounted) {
          const rawRelations = json.data || [];
          const animeRelations = [];
          for (const relGroup of rawRelations) {
            const groupName = relGroup.relation;
            const animeEntries = (relGroup.entry || []).filter(e => e.type === "anime");
            
            for (const entry of animeEntries) {
              animeRelations.push({
                relation: groupName,
                malId: entry.mal_id,
                title: entry.name,
                url: entry.url
              });
            }
          }
          setRelations(animeRelations);
        }
      } catch (err) {
        console.warn("Failed to fetch relations:", err);
      } finally {
        if (isMounted) setRelationsLoading(false);
      }
    }

    loadRelations();
    return () => {
      isMounted = false;
    };
  }, [detail]);

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
        const rawEpisodes = epData?.episodes || [];
        setEpisodes(rawEpisodes);
        setCharacters(charData?.characters || []);

        // Respect locked audio preference from settings
        const savedAudioPref = localStorage.getItem("anistream_audio_preference") || "sub";
        setAudioPreference(savedAudioPref);

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

        // Fetch Jikan fillers asynchronously in the background
        const malId = detailData?.anime?.info?.malId;
        if (malId && malId !== "0" && malId !== "") {
          const cachedFillers = getCachedFillers(malId);
          if (cachedFillers) {
            setEpisodes(prev => 
              prev.map(ep => ({
                ...ep,
                isFiller: !!cachedFillers.fillerMap[ep.number],
                isRecap: !!cachedFillers.recapMap[ep.number]
              }))
            );
          } else {
            fetchJikanFillers(malId, rawEpisodes.length).then(({ fillerMap, recapMap }) => {
              setEpisodes(prev => 
                prev.map(ep => ({
                  ...ep,
                  isFiller: !!fillerMap[ep.number],
                  isRecap: !!recapMap[ep.number]
                }))
              );
              setCachedFillers(malId, { fillerMap, recapMap });
            });
          }
        }

        // Fetch seasons asynchronously in the background
        if (malId && malId !== "0" && malId !== "") {
          const seasonRedirects = {
            "55818": "51179",  // S2 Episode 0 "Guardian Fitz" -> S2 Part 1
            "58752": "51179",  // S3 Alt -> S2 Part 1
            "50360": "51179",  // Eris Special -> S2 Part 1
            "39535": "51179",  // S1 Part 1 -> S2 Part 1
            "45576": "51179",  // S1 Part 2 -> S2 Part 1
            "55888": "51179",  // S2 Part 2 -> S2 Part 1
            "59193": "51179",  // S3 Part 1 -> S2 Part 1
            
            // Demon Slayer redirects to Season 1
            "40456": "38000",
            "49926": "38000",
            "47778": "38000",
            "51019": "38000",
            "55701": "38000",
            "59192": "38000",
            "62546": "38000",
            "47398": "38000",
            "48861": "38000"
          };
          const seasonMalId = seasonRedirects[malId] || malId;
          const cached = getCachedSeasons(seasonMalId);
          if (cached) {
            setSeasons(cached.seasons);
            setHasAltVersion(cached.hasAltVersion);
            setSeasonsLoading(false);
          } else {
            setSeasonsLoading(true);
            getSeasons(seasonMalId)
              .then(async (seasonData) => {
                let list = seasonData || [];
                const isDifferent = !cached || JSON.stringify(cached.seasons) !== JSON.stringify(list);
                if (isDifferent) {
                  const freshData = {
                    seasons: list,
                    hasAltVersion: false
                  };
                  setSeasons(list);
                  setHasAltVersion(false);
                  
                // Save to cache for all MAL IDs in this franchise (including redirects)
                list.forEach(s => {
                  if (s.malId) {
                    setCachedSeasons(s.malId, freshData);
                  }
                });
                // Explicitly cache for redirects too
                const seasonRedirects = {
                  "55818": "51179",
                  "58752": "51179",
                  "50360": "51179",
                  "39535": "51179",
                  "45576": "51179",
                  "55888": "51179",
                  "59193": "51179",
                  
                  // Demon Slayer redirects to Season 1
                  "40456": "38000",
                  "49926": "38000",
                  "47778": "38000",
                  "51019": "38000",
                  "55701": "38000",
                  "59192": "38000",
                  "62546": "38000",
                  "47398": "38000",
                  "48861": "38000"
                };
                Object.keys(seasonRedirects).forEach(key => {
                  if (seasonRedirects[key] === String(seasonMalId)) {
                    setCachedSeasons(key, freshData);
                  }
                });
              }
              setSeasonsLoading(false);
            })
            .catch(e => {
              console.warn("Background seasons fetch error:", e);
              setSeasonsLoading(false);
            });
          }
        }

      } catch (err) {
        console.error("Detail page fetch error:", err);
        setLoading(false);
      }
    }
    loadData();
  }, [animeId, currentUser, activeProfile]);

  // Check if current season is a completed prequel of a later season in the franchise
  useEffect(() => {
    if (!currentUser || !activeProfile || !seasons || seasons.length <= 1) {
      setIsPrequelCompleted(false);
      return;
    }

    async function checkPrequelCompleted() {
      try {
        const mainSeasons = seasons.filter(s => s.relationType === "MAIN" && s.seasonNumber > 0 && s.format !== "MOVIE");
        const currentSeasonIndex = mainSeasons.findIndex(s => String(s.resolvedId) === String(animeId) || String(s.malId) === String(detail?.anime?.info?.malId));

        if (currentSeasonIndex !== -1 && currentSeasonIndex < mainSeasons.length - 1) {
          const laterSeasons = mainSeasons.slice(currentSeasonIndex + 1);
          for (const s of laterSeasons) {
            const idToCheck = s.resolvedId;
            if (idToCheck) {
              const hRef = doc(db, "users", currentUser.uid, "profiles", activeProfile.id, "history", String(idToCheck));
              const hSnap = await getDoc(hRef);
              if (hSnap.exists()) {
                setIsPrequelCompleted(true);
                return;
              }
            }
          }
        }
        setIsPrequelCompleted(false);
      } catch (e) {
        console.warn("Error checking prequel completion state:", e);
        setIsPrequelCompleted(false);
      }
    }

    checkPrequelCompleted();
  }, [currentUser, activeProfile, seasons, animeId, detail]);

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
    if (season.resolvedId) {
      navigate(`/anime/${season.resolvedId}`);
      return;
    }
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

  const handleShareClick = () => {
    try {
      navigator.clipboard.writeText(window.location.href);
      setShareSuccess(true);
      setTimeout(() => setShareSuccess(false), 2500);
    } catch (e) {
      console.warn("Share clipboard copy error:", e);
    }
  };

  const handleRelationClick = async (rel) => {
    if (resolvingRelationId) return;
    setResolvingRelationId(rel.malId);
    try {
      const res = await resolveMAL(rel.malId);
      if (res && res.anikotoId) {
        navigate(`/anime/${res.anikotoId}`);
      } else {
        alert(`"${rel.title}" is not directly linked, searching AniStream...`);
        navigate(`/search?keyword=${encodeURIComponent(rel.title)}`);
      }
    } catch (err) {
      console.error("Failed to resolve relation:", err);
      navigate(`/search?keyword=${encodeURIComponent(rel.title)}`);
    } finally {
      setResolvingRelationId(null);
    }
  };

  const markBatchAsWatched = async (batch) => {
    if (!currentUser || !activeProfile || !detail || !episodes || episodes.length === 0) return;
    
    try {
      const batchEps = batch.episodes;
      if (!batchEps || batchEps.length === 0) return;
      const lastEpInBatch = batchEps[batchEps.length - 1];
      
      const lastIndex = episodes.findIndex(ep => ep.episodeId === lastEpInBatch.episodeId);
      
      let targetEp = null;
      if (lastIndex !== -1 && lastIndex < episodes.length - 1) {
        targetEp = episodes[lastIndex + 1];
      }
      
      let data;
      if (targetEp) {
        data = {
          animeId,
          animeTitle: detail.anime.info.name,
          poster: detail.anime.info.poster,
          episodeId: targetEp.episodeId,
          episodeNumber: targetEp.number,
          episodeTitle: targetEp.title || `Episode ${targetEp.number}`,
          progressPosition: 0,
          totalDuration: 0,
          audioCategory: audioPreference,
          updatedAt: Date.now()
        };
      } else {
        const lastEp = episodes[episodes.length - 1];
        data = {
          animeId,
          animeTitle: detail.anime.info.name,
          poster: detail.anime.info.poster,
          episodeId: lastEp.episodeId,
          episodeNumber: lastEp.number + 1,
          episodeTitle: lastEp.title || `Episode ${lastEp.number}`,
          progressPosition: 1,
          totalDuration: 1,
          audioCategory: audioPreference,
          updatedAt: Date.now()
        };
        
        // Automatically mark as completed in watchlist
        const watchlistRef = doc(db, "users", currentUser.uid, "profiles", activeProfile.id, "watchlist", animeId);
        await setDoc(watchlistRef, {
          id: animeId,
          name: detail.anime.info.name,
          poster: detail.anime.info.poster,
          status: "completed",
          addedAt: Date.now()
        }, { merge: true });
        setIsWatchlisted(true);
        setWatchlistStatus("completed");
      }
      
      const docRef = doc(db, "users", currentUser.uid, "profiles", activeProfile.id, "history", animeId);
      await setDoc(docRef, data);
      setHistoryItem(data);
    } catch (err) {
      console.warn("Failed to mark batch as watched:", err);
    }
  };

  const getRelationColor = (relation) => {
    const rel = relation.toLowerCase();
    if (rel.includes("prequel")) return "#ff4d4d";
    if (rel.includes("sequel")) return "#46D369";
    if (rel.includes("parent")) return "#00A8E8";
    if (rel.includes("side story")) return "#E5A93B";
    if (rel.includes("spin-off") || rel.includes("spinoff")) return "#9B5DE5";
    return "var(--text-muted)";
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

  // Dynamically resolve season prefix (e.g. "S5" or "MOV") to display correct season in episode lists rather than hardcoding "S1"
  const getEpisodeSeasonPrefix = () => {
    if (currentSeason) {
      const badge = getShortSeasonBadge(currentSeason);
      if (badge && badge !== "S1") {
        return badge;
      }
    }
    
    // As a fallback (e.g. before seasons load), try matching patterns in the current anime's name
    const title = info?.name || "";
    const titleLower = title.toLowerCase();
    const seasonMatch = titleLower.match(/(\d+)(st|nd|rd|th)?\s*season/i) || titleLower.match(/season\s*(\d+)/i);
    if (seasonMatch) {
      const num = seasonMatch[1];
      const partMatch = titleLower.match(/part\s*(\d+)/i);
      if (partMatch) {
        return `S${num} P${partMatch[1]}`;
      }
      return `S${num}`;
    }
    
    // Second fallback: if currentSeason was found but had "S1" badge, return that
    if (currentSeason) {
      return getShortSeasonBadge(currentSeason);
    }
    
    return "S1";
  };
  const seasonPrefix = getEpisodeSeasonPrefix();

  const tvSeasons = seasons.filter(s => {
    return s.relationType === "MAIN";
  });
  
  const moviesAndSpecials = seasons.filter(s => {
    return s.relationType !== "MAIN";
  });
  // Determine watch / resume link already calculated at the top-level


  return (
    <div className="detail-page fade-in">
      {/* Hero Header Section */}
      <div className="detail-hero">
        <div className="hero-backdrop-image" style={{ backgroundImage: `url(${info.poster})`, filter: "blur(30px) brightness(0.25)" }}></div>
        <div className="hero-gradient"></div>
        <div className="container hero-container" style={{ display: "flex", gap: "2.5rem", width: "100%", zIndex: 2 }}>
          <div className="hero-poster-wrapper">
            <img src={info.poster} alt={info.name} className="hero-poster" />
          </div>

          <div className="hero-meta-content" style={{ flexGrow: 1, display: "flex", flexDirection: "column", justifyContent: "flex-end" }}>
            <h1 className="anime-title-h1" style={{ fontSize: "clamp(2rem, 4vw, 3rem)", fontWeight: "900", textTransform: "uppercase", letterSpacing: "-0.5px", textShadow: "2px 2px 8px rgba(0,0,0,0.5)" }}>
              {info.name}
            </h1>
            
            {/* Metadata bullets list */}
            <div className="meta-stats-row" style={{ display: "flex", alignItems: "center", gap: "0.6rem", marginTop: "8px", flexWrap: "wrap", fontSize: "0.9rem" }}>
              <span className="stat-pill rating" style={{ background: "var(--primary)", color: "white", fontWeight: "800", padding: "2px 8px", borderRadius: "4px" }}>
                {(() => {
                  if (!moreInfo?.genres || moreInfo.genres.length === 0) return "14+";
                  const gLower = moreInfo.genres.map(g => g.toLowerCase());
                  if (gLower.some(g => ["ecchi", "horror", "gore", "seinen", "psychological", "thriller"].includes(g))) {
                    return "17+";
                  }
                  if (gLower.some(g => ["shounen", "action", "fantasy", "supernatural", "adventure"].includes(g))) {
                    return "14+";
                  }
                  return "13+";
                })()}
              </span>
              <span style={{ color: "var(--text-muted)" }}>•</span>
              {info.stats?.episodes?.sub && (
                <span className="stat-pill" style={{ backgroundColor: "#0070F3", color: "white", fontWeight: "700", fontSize: "0.7rem", padding: "2px 6px", borderRadius: "3px" }}>SUB</span>
              )}
              {info.stats?.episodes?.dub && (
                <span className="stat-pill" style={{ backgroundColor: "var(--secondary)", color: "#0A0A0A", fontWeight: "700", fontSize: "0.7rem", padding: "2px 6px", borderRadius: "3px", marginLeft: "4px" }}>DUB</span>
              )}
              {moreInfo?.genres && moreInfo.genres.length > 0 && (
                <>
                  <span style={{ color: "var(--text-muted)" }}>•</span>
                  <span style={{ color: "var(--text-secondary)" }}>
                    {moreInfo.genres.slice(0, 3).join(", ")}
                  </span>
                </>
              )}
            </div>

            {/* Stars row */}
            <div style={{ display: "flex", alignItems: "center", gap: "0.4rem", marginTop: "10px" }}>
              <div style={{ display: "flex", color: "#F5A623" }}>
                {[1, 2, 3, 4, 5].map((s) => {
                  const starsCount = Math.round((Number(info.stats?.rating) || 0) / 2);
                  return (
                    <Star 
                      key={s} 
                      size={16} 
                      fill={userRating > 0 ? (userRating >= s ? "#F5A623" : "none") : (starsCount >= s ? "#F5A623" : "none")} 
                      style={{ marginRight: "2px" }}
                    />
                  );
                })}
              </div>
              <span style={{ fontWeight: "700", color: "#F5A623" }}>
                {userRating > 0 ? `${userRating}.0` : ((Number(info.stats?.rating) || 0) / 2).toFixed(1)}
              </span>
              <span style={{ color: "var(--text-muted)", fontSize: "0.85rem" }}>
                ({(() => {
                  const averageRatingVal = Number(info.stats?.rating) || 0;
                  const mockVotes = Math.floor((averageRatingVal * 17923) % 250000);
                  return mockVotes >= 1000 ? (mockVotes / 1000).toFixed(1) + "K" : mockVotes;
                })()})
              </span>
            </div>

            {/* Primary Action Buttons */}
            <div className="actions-row" style={{ marginTop: "24px", display: "flex", alignItems: "center", gap: "1rem", flexWrap: "wrap" }}>
              {episodes.length > 0 ? (
                <Link to={playTargetUrl} className="btn btn-primary watch-now-btn" style={{ padding: "0.85rem 2.2rem", fontWeight: "700", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                  <Play size={18} fill="white" /> {playButtonText}
                </Link>
              ) : (
                <button className="btn btn-primary watch-now-btn" disabled>No Episodes Available</button>
              )}

              {/* Bookmark Toggle */}
              <button 
                className="btn btn-secondary" 
                onClick={() => {
                  if (isWatchlisted) {
                    handleUpdateWatchlistStatus("");
                  } else {
                    handleUpdateWatchlistStatus("planning");
                  }
                }}
                style={{ padding: "0.85rem", width: "48px", height: "48px", display: "inline-flex", alignItems: "center", justifyContent: "center" }}
                title={isWatchlisted ? "Remove from Watchlist" : "Add to Watchlist"}
                type="button"
              >
                {isWatchlisted ? <BookmarkCheck size={20} style={{ color: "var(--primary)" }} /> : <Bookmark size={20} />}
              </button>
            </div>

            {/* Sub-actions group */}
            <div style={{ display: "flex", alignItems: "center", gap: "2.5rem", marginTop: "24px", paddingLeft: "4px", flexWrap: "wrap" }}>
              {/* My List vertical action */}
              <button 
                onClick={() => {
                  if (isWatchlisted) {
                    handleUpdateWatchlistStatus("");
                  } else {
                    handleUpdateWatchlistStatus("planning");
                  }
                }}
                type="button"
                style={{
                  background: "none",
                  border: "none",
                  color: "var(--text-secondary)",
                  cursor: "pointer",
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  gap: "6px",
                  padding: 0,
                  fontSize: "0.75rem",
                  fontWeight: "700"
                }}
              >
                {isWatchlisted ? <BookmarkCheck size={20} style={{ color: "var(--primary)" }} /> : <Bookmark size={20} />}
                <span>{isWatchlisted ? "IN MY LIST" : "ADD TO LIST"}</span>
              </button>

              {/* Share vertical action */}
              <button 
                onClick={handleShareClick}
                type="button"
                style={{
                  background: "none",
                  border: "none",
                  color: "var(--text-secondary)",
                  cursor: "pointer",
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  gap: "6px",
                  padding: 0,
                  fontSize: "0.75rem",
                  fontWeight: "700",
                  position: "relative"
                }}
              >
                <Film size={20} />
                <span>{shareSuccess ? "COPIED!" : "SHARE"}</span>
                {shareSuccess && (
                  <span style={{
                    position: "absolute",
                    bottom: "32px",
                    background: "rgba(0,0,0,0.85)",
                    color: "white",
                    padding: "4px 8px",
                    borderRadius: "4px",
                    fontSize: "0.7rem",
                    whiteSpace: "nowrap",
                    border: "1px solid var(--border)"
                  }}>
                    Link Copied!
                  </span>
                )}
              </button>

              {/* Rate interactive */}
              {currentUser && activeProfile && (
                <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "4px" }}>
                  <div style={{ display: "flex", gap: "2px" }}>
                    {[1, 2, 3, 4, 5].map((s) => (
                      <button
                        key={s}
                        onClick={() => handleRatingSelect(s)}
                        type="button"
                        style={{ background: "none", border: "none", cursor: "pointer", padding: "2px", color: userRating >= s ? "#F5A623" : "var(--text-muted)" }}
                      >
                        <Star size={16} fill={userRating >= s ? "#F5A623" : "none"} />
                      </button>
                    ))}
                  </div>
                  <span style={{ fontSize: "0.75rem", fontWeight: "700", color: "var(--text-secondary)" }}>
                    {userRating > 0 ? `YOUR RATING: ${userRating}/5` : "RATE THIS SHOW"}
                  </span>
                </div>
              )}
            </div>

            {/* Synopsis and Expandable Details */}
            <div className="synopsis-box" style={{ marginTop: "24px", background: "none", border: "none", padding: 0 }}>
              <p className="synopsis-text" style={{ fontSize: "0.95rem", lineHeight: "1.6", color: "var(--text-secondary)" }}>
                {(() => {
                  const desc = (info.description || "").replace(/<[^>]*>/g, "");
                  if (desc.length <= 250) return desc;
                  return (
                    <>
                      {showAllSynopsis ? desc : `${desc.slice(0, 250)}...`}
                      <button
                        onClick={() => setShowAllSynopsis(!showAllSynopsis)}
                        type="button"
                        style={{
                          background: "none",
                          border: "none",
                          color: "var(--primary)",
                          fontWeight: "700",
                          cursor: "pointer",
                          padding: "0 6px",
                          fontSize: "0.85rem",
                          display: "inline",
                          textTransform: "uppercase"
                        }}
                      >
                        {showAllSynopsis ? "Less" : "More"}
                      </button>
                    </>
                  );
                })()}
              </p>
              
              {/* More Details Toggle */}
              <button
                onClick={() => setShowMoreDetails(!showMoreDetails)}
                type="button"
                style={{
                  background: "none",
                  border: "none",
                  color: "var(--primary)",
                  fontWeight: "700",
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "center",
                  gap: "4px",
                  fontSize: "0.85rem",
                  marginTop: "12px",
                  padding: "4px 0",
                  textTransform: "uppercase",
                  letterSpacing: "0.05em"
                }}
              >
                <span>{showMoreDetails ? "LESS DETAILS" : "MORE DETAILS"}</span>
                <ChevronDown size={16} style={{ transform: showMoreDetails ? "rotate(180deg)" : "rotate(0deg)", transition: "transform 0.2s" }} />
              </button>

              {/* Expanded details accordion container */}
              {showMoreDetails && (
                <div style={{
                  marginTop: "1.2rem",
                  padding: "1.2rem",
                  backgroundColor: "var(--bg-card)",
                  border: "1px solid var(--border)",
                  borderRadius: "8px",
                  display: "grid",
                  gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))",
                  gap: "1.2rem 2rem",
                  animation: "fadeIn 0.3s ease-out"
                }}>
                  <div>
                    <span style={{ display: "block", fontSize: "0.75rem", color: "var(--text-muted)", fontWeight: "700", textTransform: "uppercase" }}>Status</span>
                    <span style={{ color: "white", fontSize: "0.9rem", fontWeight: "600" }}>{moreInfo?.status || "Unknown"}</span>
                  </div>
                  <div>
                    <span style={{ display: "block", fontSize: "0.75rem", color: "var(--text-muted)", fontWeight: "700", textTransform: "uppercase" }}>Aired Dates</span>
                    <span style={{ color: "white", fontSize: "0.9rem", fontWeight: "600" }}>{moreInfo?.aired || "Unknown"}</span>
                  </div>
                  <div>
                    <span style={{ display: "block", fontSize: "0.75rem", color: "var(--text-muted)", fontWeight: "700", textTransform: "uppercase" }}>Premiered Season</span>
                    <span style={{ color: "white", fontSize: "0.9rem", fontWeight: "600" }}>{moreInfo?.premiered || "Unknown"}</span>
                  </div>
                  <div>
                    <span style={{ display: "block", fontSize: "0.75rem", color: "var(--text-muted)", fontWeight: "700", textTransform: "uppercase" }}>Studio</span>
                    <span style={{ color: "white", fontSize: "0.9rem", fontWeight: "600" }}>{moreInfo?.studio || "Unknown"}</span>
                  </div>
                  {moreInfo?.producers && moreInfo.producers.length > 0 && (
                    <div style={{ gridColumn: "1 / -1" }}>
                      <span style={{ display: "block", fontSize: "0.75rem", color: "var(--text-muted)", fontWeight: "700", textTransform: "uppercase", marginBottom: "4px" }}>Producers</span>
                      <div style={{ display: "flex", flexWrap: "wrap", gap: "6px" }}>
                        {moreInfo.producers.map((p) => (
                          <span key={p} style={{ backgroundColor: "rgba(255,255,255,0.05)", padding: "3px 8px", borderRadius: "4px", fontSize: "0.75rem", color: "var(--text-secondary)" }}>
                            {p}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="container detail-content-body" style={{ marginTop: "2rem" }}>
        <div className="main-detail-column" style={{ display: "flex", flexDirection: "column", gap: "3rem", width: "100%" }}>
          {/* Episodes Selection */}
          {episodes.length > 0 && (
            <section className="detail-section">
              <div className="section-header-row">
                <div className="episodes-title-wrapper">
                  <h2 className="section-title"><List size={18} /> Episodes</h2>
                  
                  {/* Netflix/Crunchyroll Season Selector Dropdown */}
                  {seasonsLoading ? (
                    <div className="season-selector-loading-shimmer shimmer" />
                  ) : (
                    seasons.length > 1 && (
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
                    )
                  )}

                  {/* Episode Batch Selector Dropdown */}
                  {batches.length > 1 && (
                    <div className="season-selector-dropdown-wrapper">
                      <button 
                        className="season-dropdown-toggle-btn"
                        onClick={() => setEpisodeDropdownOpen(!episodeDropdownOpen)}
                        type="button"
                      >
                        <span className="dropdown-label">{batches[selectedBatchIndex]?.label}</span>
                        <ChevronDown size={14} className={`dropdown-chevron ${episodeDropdownOpen ? "open" : ""}`} />
                      </button>
                    
                      {episodeDropdownOpen && (
                        <>
                          <div className="dropdown-backplate" onClick={() => setEpisodeDropdownOpen(false)} />
                          <div className="season-dropdown-menu episode-batch-dropdown-menu" style={{ minWidth: "280px", width: "max-content", right: 0, left: "auto" }}>
                            <div className="dropdown-section">
                              <div className="dropdown-section-header">Episode Batches</div>
                              {batches.map((batch) => {
                                const isActive = batch.index === selectedBatchIndex;
                                return (
                                  <div
                                    key={batch.index}
                                    className={`season-dropdown-item ${isActive ? "active" : ""} ${batch.isFinished ? "finished" : ""}`}
                                    style={{
                                      display: "flex",
                                      alignItems: "center",
                                      justifyContent: "space-between",
                                      gap: "1.2rem",
                                      padding: "8px 12px",
                                      flexDirection: "row"
                                    }}
                                    onClick={() => {
                                      setSelectedBatchIndex(batch.index);
                                      setEpisodeDropdownOpen(false);
                                    }}
                                  >
                                    <div style={{ display: "flex", alignItems: "center", gap: "6px", minWidth: 0, flex: 1 }}>
                                      {batch.isFinished && (
                                        <Check size={14} style={{ color: "#46D369", flexShrink: 0 }} />
                                      )}
                                      <span className="dropdown-item-season-title" style={{ textOverflow: "ellipsis", overflow: "hidden", whiteSpace: "nowrap", margin: 0 }}>
                                        {batch.label}
                                      </span>
                                    </div>
                                    <div style={{ display: "flex", alignItems: "center", gap: "10px", flexShrink: 0 }}>
                                      <span style={{ fontSize: "0.75rem", color: "var(--text-secondary)", fontWeight: "600" }}>
                                        {batch.episodes.length} {batch.episodes.length === 1 ? "Ep" : "Eps"}
                                      </span>
                                      {currentUser && activeProfile && (
                                        <button
                                          type="button"
                                          onClick={async (e) => {
                                            e.stopPropagation();
                                            await markBatchAsWatched(batch);
                                          }}
                                          style={{
                                            background: "none",
                                            border: "none",
                                            cursor: "pointer",
                                            color: batch.isFinished ? "#46D369" : "var(--text-muted)",
                                            padding: "2px",
                                            display: "flex",
                                            alignItems: "center",
                                            justifyContent: "center",
                                            transition: "color 0.2s"
                                          }}
                                          title="Mark all in this batch as watched"
                                        >
                                          <CheckSquare size={16} />
                                        </button>
                                      )}
                                    </div>
                                  </div>
                                );
                              })}
                            </div>
                          </div>
                        </>
                      )}
                    </div>
                  )}
                </div>

              </div>
              
              <div className="episodes-horizontal-list">
                {activeEpisodes.map((ep) => {
                  // Determine episode watch state: completed, in-progress, or unwatched
                  let progressPercent = 0;
                  let isCompleted = isPrequelCompleted;
                  let isInProgress = false;

                  if (!isCompleted && historyItem) {
                    if (historyItem.episodeId === ep.episodeId) {
                      // This is the episode the user is currently on
                      if (historyItem.totalDuration > 0) {
                        progressPercent = (historyItem.progressPosition / historyItem.totalDuration) * 100;
                        isCompleted = progressPercent >= 90;
                        isInProgress = !isCompleted && progressPercent > 0;
                      }
                    } else if (historyItem.episodeNumber > ep.number) {
                      // User has moved past this episode
                      isCompleted = true;
                      progressPercent = 100;
                    }
                  } else if (isCompleted) {
                    progressPercent = 100;
                  }

                  return (
                    <Link 
                      key={ep.episodeId} 
                      to={`/watch/${animeId}/${ep.episodeId}?audio=${audioPreference}`} 
                      className={`episode-list-card ${isCompleted ? "completed" : ""}`}
                    >
                      <div className="episode-thumbnail-wrapper">
                        <img src={info.poster} className="episode-thumbnail-img" alt={ep.title || `Episode ${ep.number}`} />
                        
                        {/* Completed: gray overlay + replay icon */}
                        {isCompleted ? (
                          <div className="completed-overlay">
                            <div className="replay-icon-btn">
                              <RotateCcw size={18} />
                            </div>
                          </div>
                        ) : (
                          <div className="circle-play-overlay">
                            <div className="circle-play-btn">
                              <Play size={16} fill="currentColor" />
                            </div>
                          </div>
                        )}

                        {/* In-progress: show progress bar only (no badge) */}
                        {isInProgress && (
                          <div className="ep-progress-bar-container" style={{ height: "4px", bottom: 0, position: "absolute", left: 0, right: 0 }}>
                            <div className="ep-progress-bar-fill" style={{ width: `${progressPercent}%`, backgroundColor: "var(--primary)", height: "100%" }}></div>
                          </div>
                        )}
                      </div>

                      <div className="episode-info-column">
                        <span className="episode-series-name">{info.name}</span>
                        <div className="episode-title-row">
                          <h4 className="episode-list-title">
                            {seasonPrefix} E{ep.number} – {ep.title || `Episode ${ep.number}`}
                          </h4>
                          {ep.isFiller && <span className="filler-tag" style={{ marginLeft: "6px" }}>Filler</span>}
                          {ep.isRecap && <span className="recap-tag" style={{ marginLeft: "6px" }}>Recap</span>}
                        </div>
                        <span className="episode-audio-type">
                          {audioPreference === "sub" ? "Subtitled" : "Dubbed"}
                        </span>
                      </div>

                      <div className="episode-right-actions">
                        <button 
                          className="episode-options-btn" 
                          onClick={(e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            alert("Episode options menu coming soon!");
                          }}
                          title="Episode Options"
                          type="button"
                        >
                          <ChevronRight size={18} />
                        </button>
                      </div>
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

          {/* Related Anime Chain */}
          {relations.length > 0 && (
            <section className="detail-section animate-slide-up">
              <h2 className="section-title"><Link2 size={18} /> Related Anime</h2>
              <div className="relations-scroll-row" style={{
                display: "flex",
                gap: "12px",
                overflowX: "auto",
                padding: "4px 4px 12px 4px",
                scrollbarWidth: "thin"
              }}>
                {relations.map((rel) => {
                  const isResolving = resolvingRelationId === rel.malId;
                  return (
                    <button
                      key={rel.malId}
                      onClick={() => handleRelationClick(rel)}
                      disabled={isResolving}
                      type="button"
                      className={`relation-badge-card ${isResolving ? "resolving" : ""}`}
                      style={{
                        display: "flex",
                        flexDirection: "column",
                        alignItems: "flex-start",
                        justifyContent: "center",
                        padding: "12px 16px",
                        background: "var(--bg-card)",
                        border: "1px solid var(--border)",
                        borderRadius: "8px",
                        cursor: "pointer",
                        transition: "all 0.2s ease-in-out",
                        minWidth: "180px",
                        maxWidth: "240px",
                        textAlign: "left",
                        flexShrink: 0
                      }}
                    >
                      <span style={{
                        fontSize: "0.7rem",
                        textTransform: "uppercase",
                        letterSpacing: "0.05em",
                        fontWeight: "800",
                        color: getRelationColor(rel.relation),
                        marginBottom: "4px"
                      }}>
                        {rel.relation}
                      </span>
                      <span style={{
                        fontSize: "0.85rem",
                        fontWeight: "600",
                        color: "white",
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        display: "-webkit-box",
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: "vertical",
                        lineHeight: "1.3"
                      }}>
                        {isResolving ? "Resolving..." : rel.title}
                      </span>
                    </button>
                  );
                })}
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
          align-self: flex-end;
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
        .episode-grid-card.completed {
          opacity: 0.55;
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
        .episode-grid-card.filler {
          border-left: 3px solid #ff4d4d;
        }
        .episode-grid-card.recap {
          border-left: 3px solid #ffaa00;
        }
        .filler-tag {
          align-self: flex-start;
          background-color: rgba(255, 77, 77, 0.15);
          color: rgb(255, 77, 77);
          border: 1px solid rgba(255, 77, 77, 0.3);
          font-size: 0.65rem;
          font-weight: 700;
          padding: 1px 6px;
          border-radius: 4px;
          text-transform: uppercase;
        }
        .recap-tag {
          align-self: flex-start;
          background-color: rgba(255, 170, 0, 0.15);
          color: rgb(255, 170, 0);
          border: 1px solid rgba(255, 170, 0, 0.3);
          font-size: 0.65rem;
          font-weight: 700;
          padding: 1px 6px;
          border-radius: 4px;
          text-transform: uppercase;
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

        .season-selector-loading-shimmer {
          width: 140px;
          height: 30px;
          border-radius: 4px;
          background-color: var(--bg-input);
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

        .season-dropdown-item.finished {
          color: #7f7f7f;
          opacity: 0.7;
        }
        .season-dropdown-item.finished .dropdown-item-season-title {
          color: #7f7f7f;
        }
        .season-dropdown-item.finished.active {
          border-left-color: #7f7f7f;
          background-color: rgba(127, 127, 127, 0.1);
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
            filter: brightness(0.4) !important;
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
            align-items: stretch;
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
          
          /* Dropdown alignment fixes on mobile to prevent left-side overflow */
          .section-header-row {
            position: relative !important;
          }
          .episodes-title-wrapper {
            position: static !important;
          }
          .season-selector-dropdown-wrapper {
            position: static !important;
          }
          .season-dropdown-menu {
            left: 16px !important;
            right: 16px !important;
            width: auto !important;
            max-width: calc(100vw - 32px) !important;
            margin: 0 auto !important;
            top: calc(100% + 6px) !important;
            z-index: 200 !important;
          }
          .dropdown-backplate {
            z-index: 199 !important;
          }
        }
      `}</style>
    </div>
  );
}
