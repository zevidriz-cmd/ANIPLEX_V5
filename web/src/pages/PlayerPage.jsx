import React, { useEffect, useState, useRef, useMemo } from "react";
import { useParams, useNavigate, useSearchParams, Link } from "react-router-dom";
import { doc, getDoc, setDoc, deleteDoc } from "firebase/firestore";
import { db } from "../config/firebase";
import { useAuth } from "../context/AuthContext";
import { useProfile } from "../context/ProfileContext";
import { getAnimeDetail, getEpisodes, getStreamSources, getSkipTimes, getDirectStream, getBackupStream, getMegaplayDirectStream, getSingleBackupStream, getSeasons } from "../services/api";
import { resolveTmdId, fetchTmdSeasons, buildArcsFromSeasons } from "../services/tmdb";
import VideoPlayer from "../components/VideoPlayer";
import { ArrowLeft, RefreshCw, AlertTriangle, ChevronDown, Check, CheckSquare } from "lucide-react";

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

export default function PlayerPage() {
  const { animeId, episodeId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const audioCategory = searchParams.get("audio") || localStorage.getItem("anistream_audio_preference") || "sub"; // 'sub' or 'dub'

  const { currentUser } = useAuth();
  const { activeProfile } = useProfile();
  const navigate = useNavigate();

  const [initialLoading, setInitialLoading] = useState(true);
  const [scraping, setScraping] = useState(false);
  const [loadedAnimeId, setLoadedAnimeId] = useState("");
  const [error, setError] = useState("");
  const [animeDetail, setAnimeDetail] = useState(null);
  const [episodes, setEpisodes] = useState([]);
  const [currentEpisode, setCurrentEpisode] = useState(null);
  const [selectedServer, setSelectedServer] = useState("hd-1");

  // Batching & dropdown UI states
  const [selectedBatchIndex, setSelectedBatchIndex] = useState(0);
  const [showBatchDropdown, setShowBatchDropdown] = useState(false);
  const batchDropdownRef = useRef(null);

  // Story Arcs & User History States
  const [tmdbSeasons, setTmdbSeasons] = useState(null);
  const [tmdbId, setTmdbId] = useState(null);
  const [historyItem, setHistoryItem] = useState(null);

  // Batching & Arc logic using useMemo
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

  // Click outside to close batch dropdown
  useEffect(() => {
    function handleClickOutside(event) {
      if (batchDropdownRef.current && !batchDropdownRef.current.contains(event.target)) {
        setShowBatchDropdown(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // Load TMDb ID and story arcs
  useEffect(() => {
    const malId = animeDetail?.anime?.info?.malId;
    const title = animeDetail?.anime?.info?.name;
    if (!malId || malId === "0" || malId === "") {
      setTmdbSeasons(null);
      setTmdbId(null);
      return;
    }

    let isMounted = true;
    async function getArcs() {
      try {
        const resolvedId = await resolveTmdId(malId, title);
        if (resolvedId && isMounted) {
          setTmdbId(resolvedId);
          const seasons = await fetchTmdSeasons(resolvedId);
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
  }, [animeDetail]);

  // Auto-select batch index when currentEpisode changes
  useEffect(() => {
    if (batches.length > 1 && currentEpisode) {
      const targetEp = currentEpisode.number;
      const idx = batches.findIndex(b => targetEp >= b.start && targetEp <= b.end);
      if (idx >= 0 && idx < batches.length) {
        setSelectedBatchIndex(idx);
      } else {
        setSelectedBatchIndex(0);
      }
    } else {
      setSelectedBatchIndex(0);
    }
  }, [batches, currentEpisode]);

  // Stream & Skip times
  const [streamData, setStreamData] = useState(null);
  const [directStream, setDirectStream] = useState(null);
  const [backupTracks, setBackupTracks] = useState([]);
  const [fetchingBackupSubs, setFetchingBackupSubs] = useState(false);
  const [backupAttempted, setBackupAttempted] = useState(false);
  const [skipTimes, setSkipTimes] = useState(null);
  const [initialSavedProgress, setInitialSavedProgress] = useState(0);
  const [loadingStatus, setLoadingStatus] = useState("Scraping stream sources...");
  const [fallbackNotice, setFallbackNotice] = useState(null);
  const [failedProviders, setFailedProviders] = useState([]);

  const lastLoadedEpIdRef = useRef(null);
  const lastLoadedServerRef = useRef(null);
  const lastLoadedAudioRef = useRef(null);

  // Helper: filter out thumbnail/metadata tracks, keep only real subtitle tracks
  const getSubtitleOnlyTracks = (tracks) => {
    if (!tracks || !Array.isArray(tracks)) return [];
    return tracks.filter(t => {
      const kind = (t.kind || '').toLowerCase();
      const label = (t.label || '').toLowerCase();
      return kind !== 'thumbnails' && label !== 'thumbnails';
    });
  };

  const combinedTracks = useMemo(() => {
    const primaryTracks = directStream ? directStream.tracks : (streamData?.tracks || []);
    const seenLabels = new Set(primaryTracks.map(t => t.label?.toLowerCase()));
    const uniqueBackupTracks = backupTracks.filter(t => !seenLabels.has(t.label?.toLowerCase()));
    return [...primaryTracks, ...uniqueBackupTracks];
  }, [directStream, streamData, backupTracks]);

  const handleSubtitleError = async (failedTrack) => {
    if (getSubtitleOnlyTracks(backupTracks).length > 0 || fetchingBackupSubs) return;
    
    const rawMalId = animeDetail?.anime?.info?.malId;
    const malId = (rawMalId === "0" || !rawMalId) ? null : rawMalId;
    const animeTitle = animeDetail?.anime?.info?.name;
    const epNumber = currentEpisode?.number;
    
    if (!epNumber || (!malId && !animeTitle)) return;
    
    setFetchingBackupSubs(true);
    setBackupAttempted(true);
    console.log("[PlayerPage] Subtitle track failed or empty. Triggering automated backup subtitle fetch...");
    
    let resolvedBackup = false;
    const providers = ["gogoanime", "animepahe"];
    for (const provider of providers) {
      try {
        console.log(`[PlayerPage] Fetching backup subtitles from ${provider}...`);
        const res = await getSingleBackupStream(malId, epNumber, animeTitle, provider);
        const realTracks = getSubtitleOnlyTracks(res?.tracks);
        if (realTracks.length > 0) {
          console.log(`[PlayerPage] Successfully resolved ${realTracks.length} backup subtitle tracks from ${provider}`);
          setBackupTracks(realTracks);
          resolvedBackup = true;
          break;
        }
      } catch (err) {
        console.warn(`[PlayerPage] Failed to fetch backup subtitles from ${provider}:`, err);
      }
    }
    setFetchingBackupSubs(false);

    if (!resolvedBackup) {
      // Don't auto-swap provider — the video stream itself is fine,
      // just no soft subtitles available. Show a notice instead.
      console.warn("[PlayerPage] No soft subtitles found from any provider.");
      setFallbackNotice({
        type: "warning",
        message: "Subtitles are unavailable for this episode on the current server. Try switching to Gogoanime (hardcoded subtitles) from the server options below."
      });
    }
  };

  // Auto-detect missing subtitles: only when watching SUB and no real subtitle tracks exist
  useEffect(() => {
    // Don't check for subtitles in dub mode — dub doesn't need them
    if (audioCategory !== "sub") return;
    if (!directStream || fetchingBackupSubs || backupAttempted) return;

    const primaryTracks = directStream.tracks || [];
    const realSubTracks = getSubtitleOnlyTracks(primaryTracks);
    const realBackupSubs = getSubtitleOnlyTracks(backupTracks);

    if (realSubTracks.length === 0 && realBackupSubs.length === 0) {
      // Delay to allow <track> elements to attempt loading first
      const timer = setTimeout(() => {
        handleSubtitleError(null);
      }, 3000);
      return () => clearTimeout(timer);
    }
  }, [directStream, backupTracks, fetchingBackupSubs, backupAttempted, audioCategory]);

  const handlePlaybackError = (providerName) => {
    console.warn(`[PlayerPage] Playback failed for provider: ${providerName}. Adding to failed list.`);
    setFailedProviders(prev => {
      if (prev.includes(providerName)) return prev;
      return [...prev, providerName];
    });
  };

  const availableAudioCategories = useMemo(() => {
    const categories = ["sub"];
    const hasDub = animeDetail?.anime?.info?.stats?.episodes?.dub && animeDetail.anime.info.stats.episodes.dub > 0;
    if (hasDub) {
      categories.push("dub");
    }
    return categories;
  }, [animeDetail]);

  const handleAudioCategoryChange = (newCategory) => {
    console.log(`[PlayerPage] Switching audio category to: ${newCategory}`);
    const params = new URLSearchParams(searchParams);
    params.set("audio", newCategory);
    setSearchParams(params);
  };

  useEffect(() => {
    let isMounted = true;
    async function loadPlayer() {
      setError("");
      // Reset failed providers if this is a fresh load of a different episode/server/audio
      const isFreshLoad = lastLoadedEpIdRef.current !== episodeId || 
                           lastLoadedServerRef.current !== selectedServer || 
                           lastLoadedAudioRef.current !== audioCategory;

      if (isFreshLoad) {
        lastLoadedEpIdRef.current = episodeId;
        lastLoadedServerRef.current = selectedServer;
        lastLoadedAudioRef.current = audioCategory;
        if (failedProviders.length > 0) {
          setFailedProviders([]);
          return;
        }
      }
      const showChanged = loadedAnimeId !== animeId;
      if (showChanged) {
        setInitialLoading(true);
      } else {
        setScraping(true);
      }

      try {
        let currentDetail = animeDetail;
        let currentEps = episodes;

        // 1. Load anime detail and episodes only if show changed or is not loaded
        if (showChanged || !currentDetail || currentEps.length === 0) {
          const [detailData, epData] = await Promise.all([
            getAnimeDetail(animeId),
            getEpisodes(animeId)
          ]);

          if (!isMounted) return;
          currentDetail = detailData;
          currentEps = epData?.episodes || [];

          setAnimeDetail(detailData);
          setEpisodes(currentEps);
          setLoadedAnimeId(animeId);
        }

        const ep = currentEps.find(e => e.episodeId === episodeId);
        if (isMounted) {
          setCurrentEpisode(ep);
        }

        // 2. Fetch watch history and manage watchlist status from Firestore
        if (currentUser && activeProfile) {
          const docRef = doc(db, "users", currentUser.uid, "profiles", activeProfile.id, "history", animeId);
          const histSnap = await getDoc(docRef);
          if (isMounted) {
            if (histSnap.exists()) {
              const hist = histSnap.data();
              setHistoryItem(hist);
              if (hist.episodeId === episodeId) {
                setInitialSavedProgress(hist.progressPosition || 0);
              } else {
                setInitialSavedProgress(0);
              }
            } else {
              setHistoryItem(null);
              setInitialSavedProgress(0);
            }
          }

          // Auto-manage "watching" watchlist status
          try {
            const watchlistRef = doc(db, "users", currentUser.uid, "profiles", activeProfile.id, "watchlist", animeId);
            const watchlistSnap = await getDoc(watchlistRef);
            if (!watchlistSnap.exists()) {
              await setDoc(watchlistRef, {
                id: animeId,
                name: currentDetail.anime.info.name,
                poster: currentDetail.anime.info.poster,
                status: "watching",
                addedAt: Date.now()
              });
            } else {
              const watchlistData = watchlistSnap.data();
              if (watchlistData.status === "planning") {
                await setDoc(watchlistRef, {
                  status: "watching"
                }, { merge: true });
              }
            }
          } catch (e) {
            console.warn("Error managing auto-watching status:", e);
          }

          // Clean up old season history/watchlist entries from the same franchise
          try {
            const malId = currentDetail.anime.info.malId;
            if (malId && malId !== "0") {
              // Try to get cached seasons data first (DetailPage caches this)
              let seasons = null;
              const cached = sessionStorage.getItem(`seasons_v6_${malId}`);
              if (cached) {
                const parsed = JSON.parse(cached);
                seasons = parsed.seasons || parsed;
              } else {
                seasons = await getSeasons(malId);
              }

              if (seasons && seasons.length > 1) {
                // Find current anime in seasons to check its relationType
                const currentSeasonObj = seasons.find(s => String(s.resolvedId) === String(animeId) || String(s.malId) === String(malId));
                
                // Only perform cleanup if current anime is a MAIN season
                if (currentSeasonObj && currentSeasonObj.relationType === "MAIN") {
                  // Get all related animeIds from the franchise (excluding current) that are also MAIN seasons
                  const relatedAnimeIds = seasons
                    .filter(s => s.relationType === "MAIN" && s.resolvedId && String(s.resolvedId) !== String(animeId))
                    .map(s => s.resolvedId);

                  for (const oldAnimeId of relatedAnimeIds) {
                  // Delete old season's history entry
                  const oldHistRef = doc(db, "users", currentUser.uid, "profiles", activeProfile.id, "history", String(oldAnimeId));
                  const oldHistSnap = await getDoc(oldHistRef);
                  if (oldHistSnap.exists()) {
                    await deleteDoc(oldHistRef);
                    console.log(`[PlayerPage] Cleaned up old season history for animeId: ${oldAnimeId}`);
                  }

                  // Delete old season's watchlist entry only if it was "watching" (not "completed")
                  const oldWatchlistRef = doc(db, "users", currentUser.uid, "profiles", activeProfile.id, "watchlist", String(oldAnimeId));
                  const oldWatchlistSnap = await getDoc(oldWatchlistRef);
                  if (oldWatchlistSnap.exists()) {
                    const oldStatus = oldWatchlistSnap.data()?.status;
                    if (oldStatus === "watching" || oldStatus === "planning") {
                      await deleteDoc(oldWatchlistRef);
                      console.log(`[PlayerPage] Cleaned up old season watchlist (${oldStatus}) for animeId: ${oldAnimeId}`);
                    }
                  }
                }
              }
            }
          }
        } catch (e) {
          console.warn("Error cleaning up old season entries:", e);
        }
      }

        // Clear previous stream data to trigger a clean transition in VideoPlayer
        if (isMounted) {
          setStreamData(null);
          setDirectStream(null);
          setBackupTracks([]);
          setBackupAttempted(false);
          setFallbackNotice(null);
          setLoadingStatus("Connecting to primary server (Zoro)...");
        }

        const malId = currentDetail?.anime?.info?.malId;
        const animeTitle = currentDetail?.anime?.info?.name;

        // 3. Fetch stream sources
        const isZoroSettingEnabled = localStorage.getItem("anistream_zoro_enabled") !== "false";
        const isGogoSettingEnabled = localStorage.getItem("anistream_gogoanime_enabled") !== "false";
        const preferredProviderSetting = localStorage.getItem("anistream_preferred_provider") || "zoro";

        const isZoroEnabled = isZoroSettingEnabled && !failedProviders.includes("zoro");
        const isGogoanimeEnabled = isGogoSettingEnabled && !failedProviders.includes("gogoanime");
        const isAnimePaheEnabled = !failedProviders.includes("animepahe");
        const isMegaDirectEnabled = !failedProviders.includes("megaplay-direct");

        const tryZoro = async () => {
          if (isMounted) setLoadingStatus("Connecting to primary server (Zoro)...");
          const stream = await getStreamSources(episodeId, selectedServer, audioCategory);
          if (isMounted) setStreamData(stream);
          if (isMounted) setLoadingStatus("Extracting high-quality direct stream...");
          const res = await getDirectStream(episodeId, selectedServer, audioCategory, malId, ep.number, animeTitle);
          return { ...res, provider: "zoro" };
        };

        const tryGogoanime = async () => {
          if (isMounted) setLoadingStatus("Connecting to Gogoanime backup server...");
          const res = await getSingleBackupStream(malId, ep.number, animeTitle, "gogoanime");
          return res;
        };

        const tryAnimePahe = async () => {
          if (isMounted) setLoadingStatus("Connecting to AnimePahe backup server...");
          const res = await getSingleBackupStream(malId, ep.number, animeTitle, "animepahe");
          return res;
        };

        // Build the prioritized sequence of providers to try
        const providersSequence = [];

        if (preferredProviderSetting === "zoro") {
          if (isZoroEnabled) providersSequence.push("zoro");
          if (isGogoanimeEnabled) providersSequence.push("gogoanime");
        } else {
          // preferredProviderSetting === "gogoanime"
          if (isGogoanimeEnabled) providersSequence.push("gogoanime");
          if (isZoroEnabled) providersSequence.push("zoro");
        }
        if (isAnimePaheEnabled) providersSequence.push("animepahe");

        let success = false;

        for (const provider of providersSequence) {
          try {
            let stream = null;
            if (provider === "zoro") {
              stream = await tryZoro();
            } else if (provider === "gogoanime") {
              stream = await tryGogoanime();
            } else if (provider === "animepahe") {
              stream = await tryAnimePahe();
            }

            if (isMounted && stream) {
              setDirectStream(stream);
              // Show notification if it's a fallback provider
              if (provider !== providersSequence[0]) {
                const readableNames = {
                  zoro: "Primary server (Zoro)",
                  gogoanime: "Backup server (Gogoanime)",
                  animepahe: "Backup server (AnimePahe)"
                };
                setFallbackNotice({
                  type: "info",
                  message: `Fallback: switched to ${readableNames[provider]}.`
                });
              } else if (provider === "gogoanime" && preferredProviderSetting === "gogoanime") {
                setFallbackNotice({
                  type: "info",
                  message: "Streaming via Gogoanime preference."
                });
              } else {
                setFallbackNotice(null);
              }
              success = true;
              break;
            }
          } catch (err) {
            console.warn(`[Fallback Sequence] Provider "${provider}" failed:`, err);
          }
        }

        // Final failover: try MegaPlay Direct before falling back to Iframe
        if (!success && isMegaDirectEnabled && malId) {
          try {
            if (isMounted) setLoadingStatus("Extracting MegaPlay direct stream...");
            console.log("[Final Failover] Attempting MegaPlay direct stream extraction...");
            const megaStream = await getMegaplayDirectStream(malId, ep.number, audioCategory);
            if (isMounted) {
              setDirectStream(megaStream);
              setFallbackNotice({
                type: "info",
                message: "Switched to MegaPlay direct stream."
              });
              success = true;
            }
          } catch (megaErr) {
            console.warn("[Final Failover] MegaPlay direct extraction failed:", megaErr);
          }
        }

        // If even MegaPlay Direct failed, load Iframe as absolute last resort
        if (!success && isMounted) {
          setDirectStream(null);
          setFallbackNotice({
            type: "warning",
            message: "Direct servers under maintenance. Loaded Backup Player (Iframe)."
          });
        }

        // 4. Fetch AniSkip times if MAL ID is available
        if (malId && malId !== "0" && ep) {
          try {
            const skip = await getSkipTimes(malId, ep.number);
            if (isMounted && skip) {
              // Map AniSkip response to intro/outro format
              const opSegment = skip.find(s => s.result_type === "op");
              const edSegment = skip.find(s => s.result_type === "ed");

              setSkipTimes({
                intro: opSegment ? { start: opSegment.interval.start, end: opSegment.interval.end } : null,
                outro: edSegment ? { start: edSegment.interval.start, end: edSegment.interval.end } : null
              });
            }
          } catch (skipErr) {
            console.warn("[AniSkip] Failed to fetch skip times:", skipErr);
          }
        }

        // 5. Fetch Jikan fillers asynchronously in the background
        if (malId && malId !== "0" && malId !== "") {
          const cachedFillers = getCachedFillers(malId);
          if (cachedFillers) {
            if (isMounted) {
              setEpisodes(prev =>
                prev.map(epItem => ({
                  ...epItem,
                  isFiller: !!cachedFillers.fillerMap[epItem.number],
                  isRecap: !!cachedFillers.recapMap[epItem.number]
                }))
              );
            }
          } else {
            fetchJikanFillers(malId, currentEps.length).then(({ fillerMap, recapMap }) => {
              if (isMounted) {
                setEpisodes(prev =>
                  prev.map(epItem => ({
                    ...epItem,
                    isFiller: !!fillerMap[epItem.number],
                    isRecap: !!recapMap[epItem.number]
                  }))
                );
                setCachedFillers(malId, { fillerMap, recapMap });
              }
            });
          }
        }
      } catch (err) {
        console.error("Player page loading error:", err);
        if (isMounted) {
          setError("Failed to extract video streams. Please try another episode or server.");
        }
      } finally {
        if (isMounted) {
          setInitialLoading(false);
          setScraping(false);
        }
      }
    }
    loadPlayer();
    return () => {
      isMounted = false;
    };
  }, [animeId, episodeId, audioCategory, selectedServer, currentUser, activeProfile, failedProviders]);

  const markAsCompleted = async () => {
    if (!currentUser || !activeProfile || !animeDetail) return;
    try {
      const watchlistRef = doc(db, "users", currentUser.uid, "profiles", activeProfile.id, "watchlist", animeId);
      await setDoc(watchlistRef, {
        id: animeId,
        name: animeDetail.anime.info.name,
        poster: animeDetail.anime.info.poster,
        status: "completed",
        addedAt: Date.now()
      }, { merge: true });
      console.log("Anime marked as completed in watchlist");
    } catch (e) {
      console.warn("Error marking anime as completed:", e);
    }
  };

  const markBatchAsWatched = async (batch) => {
    if (!currentUser || !activeProfile || !animeDetail || !episodes || episodes.length === 0) return;

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
          animeTitle: animeDetail.anime.info.name,
          poster: animeDetail.anime.info.poster,
          episodeId: targetEp.episodeId,
          episodeNumber: targetEp.number,
          episodeTitle: targetEp.title || `Episode ${targetEp.number}`,
          progressPosition: 0,
          totalDuration: 0,
          audioCategory: audioCategory,
          updatedAt: Date.now()
        };
      } else {
        const lastEp = episodes[episodes.length - 1];
        data = {
          animeId,
          animeTitle: animeDetail.anime.info.name,
          poster: animeDetail.anime.info.poster,
          episodeId: lastEp.episodeId,
          episodeNumber: lastEp.number + 1,
          episodeTitle: lastEp.title || `Episode ${lastEp.number}`,
          progressPosition: 1,
          totalDuration: 1,
          audioCategory: audioCategory,
          updatedAt: Date.now()
        };

        await markAsCompleted();
      }

      const docRef = doc(db, "users", currentUser.uid, "profiles", activeProfile.id, "history", animeId);
      await setDoc(docRef, data);
      setHistoryItem(data);
    } catch (err) {
      console.warn("Failed to mark batch as watched:", err);
    }
  };

  const handleProgressSave = async (progressMs, durationMs, progressEpId) => {
    if (!progressEpId || progressEpId !== episodeId) return;
    if (!currentUser || !activeProfile || !animeDetail || !currentEpisode) return;

    try {
      const isNearEnd = (durationMs > 0) && (progressMs / durationMs >= 0.90 || (durationMs - progressMs) <= 120000);

      const currentIndex = episodes.findIndex(e => e.episodeId === currentEpisode.episodeId);

      let finalEpisodeId = currentEpisode.episodeId;
      let finalEpisodeNumber = currentEpisode.number;
      let finalEpisodeTitle = currentEpisode.title || `Episode ${currentEpisode.number}`;
      let finalProgress = progressMs;
      let finalDuration = durationMs;

      // If near end (90%+ watched), automatically prep next episode
      if (isNearEnd && currentIndex !== -1 && currentIndex < episodes.length - 1) {
        const nextEp = episodes[currentIndex + 1];
        finalEpisodeId = nextEp.episodeId;
        finalEpisodeNumber = nextEp.number;
        finalEpisodeTitle = nextEp.title || `Episode ${nextEp.number}`;
        finalProgress = 0;
        finalDuration = 0; // Clean start
      }

      const data = {
        animeId,
        animeTitle: animeDetail.anime.info.name,
        poster: animeDetail.anime.info.poster,
        episodeId: finalEpisodeId,
        episodeNumber: finalEpisodeNumber,
        episodeTitle: finalEpisodeTitle,
        progressPosition: finalProgress,
        totalDuration: finalDuration,
        audioCategory: audioCategory,
        updatedAt: Date.now()
      };

      const docRef = doc(db, "users", currentUser.uid, "profiles", activeProfile.id, "history", animeId);
      await setDoc(docRef, data);
      setHistoryItem(data);

      // Automatically mark as completed in watchlist if last episode is watched near end
      const isLastEpisode = currentIndex !== -1 && currentIndex === episodes.length - 1;
      if (isNearEnd && isLastEpisode) {
        await markAsCompleted();
      }
    } catch (e) {
      console.warn("Error saving progress to Firestore:", e);
    }
  };

  const handleEpisodeEnded = async (endedEpId) => {
    if (!endedEpId || endedEpId !== episodeId) {
      console.log(`[PlayerPage] Guarded handleEpisodeEnded: endedEpId (${endedEpId}) !== route episodeId (${episodeId})`);
      return;
    }

    const currentIndex = episodes.findIndex(e => e.episodeId === episodeId);
    const isLastEpisode = currentIndex !== -1 && currentIndex === episodes.length - 1;

    const autoplaySetting = localStorage.getItem("anistream_autoplay") !== "false";
    if (autoplaySetting) {
      if (currentIndex !== -1 && currentIndex < episodes.length - 1) {
        const nextEp = episodes[currentIndex + 1];
        navigate(`/watch/${animeId}/${nextEp.episodeId}?audio=${audioCategory}`);
        return;
      }

      // Cross-season autoplay when reaching the end of a season
      if (isLastEpisode && animeDetail?.anime?.info?.malId) {
        try {
          const malId = animeDetail.anime.info.malId;
          let seasons = null;
          const cached = sessionStorage.getItem(`seasons_v6_${malId}`);
          if (cached) {
            const parsed = JSON.parse(cached);
            seasons = parsed.seasons || parsed;
          } else {
            seasons = await getSeasons(malId);
          }

          if (seasons && seasons.length > 0) {
            // Filter main timeline TV seasons (ordered chronologically S1, S2, S3...)
            const mainSeasons = seasons.filter(s => s.relationType === "MAIN" || (s.seasonNumber && s.seasonNumber > 0));
            
            // Find current season in mainSeasons list
            const currentSeasonIndex = mainSeasons.findIndex(s => String(s.resolvedId) === String(animeId) || String(s.malId) === String(malId));
            
            if (currentSeasonIndex !== -1 && currentSeasonIndex < mainSeasons.length - 1) {
              const nextSeason = mainSeasons[currentSeasonIndex + 1];
              let targetResolvedId = nextSeason?.resolvedId;
              if (!targetResolvedId && nextSeason?.malId) {
                const resolved = await resolveMAL(nextSeason.malId);
                targetResolvedId = resolved?.anikotoId;
              }

              if (targetResolvedId) {
                const nextEpData = await getEpisodes(targetResolvedId);
                const nextEps = nextEpData?.episodes || [];
                if (nextEps.length > 0) {
                  const firstEp = nextEps[0];
                  console.log(`[PlayerPage] Cross-season autoplay to next season: ${nextSeason.title} (animeId: ${targetResolvedId})`);
                  navigate(`/watch/${targetResolvedId}/${firstEp.episodeId}?audio=${audioCategory}`);
                  return;
                }
              }
            }
          }
        } catch (e) {
          console.warn("[PlayerPage] Cross-season autoplay check failed:", e);
        }
      }
    }

    if (isLastEpisode) {
      markAsCompleted();
    }
    navigate(`/anime/${animeId}`);
  };

  // Calculate relative season and episode for TMDB fallback
  const tmdbEpisodeInfo = useMemo(() => {
    const epNum = currentEpisode?.number || 1;
    if (!tmdbSeasons || tmdbSeasons.length === 0) {
      return { season: 1, episode: epNum };
    }

    // Filter out Season 0 (Specials) and sort chronologically by season number
    const validSeasons = tmdbSeasons
      .filter((s) => s.season_number > 0)
      .sort((a, b) => a.season_number - b.season_number);

    let accumulatedEpisodes = 0;
    for (const season of validSeasons) {
      if (epNum > accumulatedEpisodes && epNum <= accumulatedEpisodes + season.episode_count) {
        return {
          season: season.season_number,
          episode: epNum - accumulatedEpisodes
        };
      }
      accumulatedEpisodes += season.episode_count;
    }

    // Fallback: if not found in any season range, default to the last season
    const lastSeason = validSeasons[validSeasons.length - 1];
    return {
      season: lastSeason ? lastSeason.season_number : 1,
      episode: Math.max(1, epNum - (accumulatedEpisodes - (lastSeason ? lastSeason.episode_count : 0)))
    };
  }, [tmdbSeasons, currentEpisode]);

  // Extract M3U8 source link
  const hlsSource = directStream ? directStream.hlsUrl : streamData?.sources?.find(s => s.type === "hls" || s.url.includes(".m3u8"))?.url;
  
  // Build fallback URL
  let embedFallback = streamData?.videoUrl || (streamData?.sources?.[0]?.url);
  if (!embedFallback) {
    if (tmdbId) {
      embedFallback = `https://vidsrc.to/embed/tv/${tmdbId}/${tmdbEpisodeInfo.season}/${tmdbEpisodeInfo.episode}`;
    } else if (animeDetail?.anime?.info?.malId) {
      embedFallback = `https://vidsrc.to/embed/anime/${animeDetail.anime.info.malId}/${currentEpisode?.number || 1}`;
    } else {
      embedFallback = "";
    }
  }

  // Setup skip ranges (prefer AniSkip, fallback to streamData intro/outro)
  const skipIntroRange = skipTimes?.intro || (directStream ? directStream.intro : streamData?.intro);
  const skipOutroRange = skipTimes?.outro || (directStream ? directStream.outro : streamData?.outro);

  const currentIndex = episodes.findIndex(e => e.episodeId === episodeId);
  const nextEpisode = currentIndex !== -1 && currentIndex < episodes.length - 1 ? episodes[currentIndex + 1] : null;



  return (
    <div className="player-screen-page">
      <div className="container player-nav-back">
        <Link to={`/anime/${animeId}`} className="back-link">
          <ArrowLeft size={16} /> Back to info
        </Link>
      </div>

      <div className="container player-wrapper-block">
        {initialLoading ? (
          <div className="player-loading-placeholder flex-center">
            <div className="text-center">
              <RefreshCw size={44} className="spin-icon" style={{ margin: "0 auto 1rem" }} />
              <h3>{loadingStatus}</h3>
              <p className="text-muted">Extracting secure links and subtitles</p>
            </div>
          </div>
        ) : error ? (
          <div className="player-error-placeholder flex-center text-center">
            <div>
              <AlertTriangle size={48} className="error-icon" style={{ margin: "0 auto 1rem" }} />
              <h2>Stream Extraction Failed</h2>
              <p className="text-muted" style={{ margin: "0.5rem 0 1.5rem" }}>{error}</p>
              <div className="flex-center" style={{ gap: "1rem" }}>
                <Link to={`/anime/${animeId}`} className="btn btn-secondary">
                  Back to Details
                </Link>
                <button onClick={() => window.location.reload()} className="btn btn-primary">
                  Retry Extraction
                </button>
              </div>
            </div>
          </div>
        ) : (
          <VideoPlayer
            src={hlsSource}
            tracks={combinedTracks}
            onSubtitleError={handleSubtitleError}
            intro={skipIntroRange}
            outro={skipOutroRange}
            initialTime={initialSavedProgress}
            onProgress={(progress, duration) => handleProgressSave(progress, duration, currentEpisode?.episodeId)}
            onEnded={() => handleEpisodeEnded(currentEpisode?.episodeId)}
            embedUrl={embedFallback}
            fallbackNotice={fallbackNotice}
            loadingStatus={loadingStatus}
            animeTitle={animeDetail?.anime?.info?.name}
            episodeNumber={currentEpisode?.number || 1}
            onBack={() => navigate(`/anime/${animeId}`)}
            nextEpisode={nextEpisode ? {
              episodeId: nextEpisode.episodeId,
              number: nextEpisode.number,
              title: nextEpisode.title || `Episode ${nextEpisode.number}`,
              poster: animeDetail?.anime?.info?.poster
            } : null}
            onNext={() => {
              if (nextEpisode) {
                navigate(`/watch/${animeId}/${nextEpisode.episodeId}?audio=${audioCategory}`);
              }
            }}
            externalLoading={scraping}
            malId={animeDetail?.anime?.info?.malId}
            tmdbId={tmdbId}
            tmdbEpisodeInfo={tmdbEpisodeInfo}
            audioCategory={audioCategory}
            onPlaybackError={handlePlaybackError}
            provider={directStream ? directStream.provider || "zoro" : "zoro"}
            availableAudioCategories={availableAudioCategories}
            onAudioCategoryChange={handleAudioCategoryChange}
          />
        )}
      </div>

      {/* Show Title & Current Episode Bar under player */}
      {!initialLoading && !error && animeDetail && (
        <div className="container player-show-title-bar">
          <Link to={`/anime/${animeId}`} className="player-show-title-link">
            <h2>{animeDetail?.anime?.info?.name}</h2>
          </Link>
          {currentEpisode && (
            <p className="player-show-ep-subtitle">
              Episode {currentEpisode.number} {currentEpisode.title ? `• ${currentEpisode.title}` : ""}
            </p>
          )}
        </div>
      )}

      {/* Server Selector Bar */}
      {!initialLoading && !error && (
        <div className="container server-selector-bar">
          <span className="server-label">Change Server:</span>
          <div className="server-buttons">
            {[
              { id: "hd-1", name: "Server 1 (HD-1)" },
              { id: "rapidcloud", name: "Server 2 (RapidCloud)" },
              { id: "megastream", name: "Server 3 (MegaStream)" }
            ].map((srv) => (
              <button
                key={srv.id}
                className={`server-btn ${selectedServer === srv.id ? "active" : ""}`}
                onClick={() => setSelectedServer(srv.id)}
              >
                {srv.name}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Up Next / Episode Navigation in Player */}
      {!initialLoading && !error && currentEpisode && (
        <div className="container player-episodes-navigation">
          <div className="episodes-header-row">
            <div className="episodes-title-area">
              <h3>Episodes List</h3>
              <div className="episodes-legend">
                <span className="legend-item"><span className="legend-dot filler"></span> Filler</span>
                <span className="legend-item"><span className="legend-dot recap"></span> Recap</span>
              </div>
            </div>

            {/* Episode Batch Selector Dropdown */}
            {batches.length > 1 && (
              <div className="season-selector-wrapper" ref={batchDropdownRef}>
                <button
                  className="season-select-btn"
                  onClick={() => setShowBatchDropdown(!showBatchDropdown)}
                  type="button"
                >
                  <span className="dropdown-label">{batches[selectedBatchIndex]?.label}</span>
                  <ChevronDown size={16} className={`chevron ${showBatchDropdown ? "open" : ""}`} />
                </button>

                {showBatchDropdown && (
                  <div className="season-dropdown-menu episode-batch-dropdown-menu" style={{ minWidth: "280px", width: "max-content", right: 0, left: "auto" }}>
                    <div className="dropdown-section-header">Episode Batches</div>
                    {batches.map((batch) => {
                      const isActive = batch.index === selectedBatchIndex;
                      return (
                        <button
                          key={batch.index}
                          className={`season-dropdown-item ${isActive ? "active" : ""} ${batch.isFinished ? "finished" : ""}`}
                          style={{
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "space-between",
                            gap: "1.2rem",
                            padding: "8px 12px",
                            flexDirection: "row",
                            width: "100%",
                            border: "none",
                            background: "transparent",
                            cursor: "pointer"
                          }}
                          onClick={() => {
                            setSelectedBatchIndex(batch.index);
                            setShowBatchDropdown(false);
                          }}
                          type="button"
                        >
                          <div style={{ display: "flex", alignItems: "center", gap: "6px", minWidth: 0, flex: 1 }}>
                            {batch.isFinished && (
                              <Check size={14} style={{ color: "#46D369", flexShrink: 0 }} />
                            )}
                            <span style={{ textOverflow: "ellipsis", overflow: "hidden", whiteSpace: "nowrap", margin: 0, color: "inherit", font: "inherit", fontWeight: isActive ? "700" : "500" }}>
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
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            )}
          </div>
          <div className="navigation-episodes-list">
            {activeEpisodes.map(ep => (
              <Link
                key={ep.episodeId}
                to={`/watch/${animeId}/${ep.episodeId}?audio=${audioCategory}`}
                className={`nav-ep-btn ${ep.episodeId === episodeId ? "active" : ""} ${ep.isFiller ? "filler" : ""} ${ep.isRecap ? "recap" : ""}`}
                title={ep.isFiller ? "Filler Episode" : ep.isRecap ? "Recap Episode" : `Episode ${ep.number}`}
              >
                Ep {ep.number}
              </Link>
            ))}
          </div>
        </div>
      )}

      <style>{`
        .player-screen-page {
          padding-top: 20px;
          padding-bottom: 4rem;
          min-height: 100vh;
          background: #050505;
        }
        .server-selector-bar {
          margin-top: 1.5rem;
          display: flex;
          align-items: center;
          gap: 1rem;
        }
        .server-label {
          font-size: 0.9rem;
          font-weight: 700;
          color: var(--text-secondary);
        }
        .server-buttons {
          display: flex;
          gap: 8px;
        }
        .server-btn {
          background-color: var(--bg-card);
          border: 1px solid var(--border);
          color: var(--text-secondary);
          padding: 8px 16px;
          border-radius: 6px;
          cursor: pointer;
          font-family: var(--font-family);
          font-size: 0.85rem;
          font-weight: 600;
          transition: var(--transition);
        }
        .server-btn:hover {
          background-color: #242424;
          color: white;
          border-color: #444;
        }
        .server-btn.active {
          background-color: var(--primary);
          color: white;
          border-color: var(--primary);
        }
        .player-nav-back {
          margin-bottom: 12px;
        }
        .back-link {
          color: var(--text-secondary);
          text-decoration: none;
          font-weight: 600;
          font-size: 0.9rem;
          display: inline-flex;
          align-items: center;
          gap: 6px;
          transition: var(--transition);
        }
        .back-link:hover {
          color: white;
        }
        .player-wrapper-block {
          width: 100%;
        }
        .player-loading-placeholder, .player-error-placeholder {
          width: 100%;
          padding-top: 56.25%; /* 16:9 aspect ratio */
          background-color: var(--bg-card);
          border-radius: 8px;
          border: 1px solid var(--border);
          position: relative;
        }
        .player-loading-placeholder > div, .player-error-placeholder > div {
          position: absolute;
          top: 50%;
          left: 50%;
          transform: translate(-50%, -50%);
          width: 90%;
        }
        .error-icon {
          color: var(--primary);
        }

        .player-episodes-navigation {
          margin-top: 2rem;
        }
        .episodes-header-row {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: 1rem;
          position: relative;
        }
        .episodes-title-area {
          display: flex;
          flex-direction: column;
          gap: 4px;
        }
        .player-episodes-navigation h3 {
          font-size: 1.1rem;
          font-weight: 700;
          margin-bottom: 0;
          color: white;
        }
        .episodes-legend {
          display: flex;
          gap: 12px;
          font-size: 0.75rem;
          color: var(--text-secondary);
        }
        .legend-item {
          display: flex;
          align-items: center;
          gap: 4px;
        }
        .legend-dot {
          width: 8px;
          height: 8px;
          border-radius: 50%;
        }
        .legend-dot.filler {
          background-color: #ff4d4d;
        }
        .legend-dot.recap {
          background-color: #ffaa00;
        }

        /* Season/Batch selector styles */
        .season-selector-wrapper {
          position: relative;
        }
        .season-select-btn {
          display: flex;
          align-items: center;
          gap: 6px;
          background: #1a1a1a;
          border: 1px solid var(--border);
          color: white;
          padding: 8px 16px;
          border-radius: 6px;
          font-weight: 600;
          font-size: 0.9rem;
          cursor: pointer;
          transition: var(--transition);
        }
        .season-select-btn:hover {
          background: #242424;
        }
        .chevron {
          transition: transform 0.2s ease;
        }
        .chevron.open {
          transform: rotate(180deg);
        }
        .season-dropdown-menu {
          position: absolute;
          top: calc(100% + 8px);
          background: #1a1a1a;
          border: 1px solid var(--border);
          border-radius: 8px;
          padding: 6px;
          box-shadow: 0 10px 30px rgba(0,0,0,0.5);
          z-index: 100;
          display: flex;
          flex-direction: column;
          gap: 2px;
        }
        .dropdown-section-header {
          font-size: 0.7rem;
          font-weight: 700;
          text-transform: uppercase;
          color: var(--text-muted);
          padding: 6px 10px;
          letter-spacing: 0.05em;
        }
        .season-dropdown-item {
          display: flex;
          align-items: center;
          justify-content: space-between;
          width: 100%;
          background: transparent;
          border: none;
          color: var(--text-secondary);
          padding: 8px 12px;
          text-align: left;
          font-size: 0.85rem;
          font-weight: 600;
          border-radius: 4px;
          cursor: pointer;
          transition: var(--transition);
        }
        .season-dropdown-item:hover {
          background: rgba(255, 255, 255, 0.05);
          color: white;
        }
        .season-dropdown-item.active {
          background: rgba(229, 9, 20, 0.1);
          color: var(--primary);
        }
        .season-dropdown-item.finished {
          opacity: 0.7;
        }
        .season-dropdown-item.finished.active {
          background: rgba(229, 9, 20, 0.08);
        }
        
        .nav-ep-btn.filler {
          border-left: 3px solid #ff4d4d;
        }
        .nav-ep-btn.recap {
          border-left: 3px solid #ffaa00;
        }
        .nav-ep-btn.active.filler {
          border-left: 3px solid #ff4d4d;
        }
        .nav-ep-btn.active.recap {
          border-left: 3px solid #ffaa00;
        }

        .navigation-episodes-list {
          display: flex;
          flex-wrap: wrap;
          gap: 8px;
        }
        .nav-ep-btn {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          width: 54px;
          height: 38px;
          background-color: var(--bg-card);
          border: 1px solid var(--border);
          color: var(--text-secondary);
          font-weight: 700;
          font-size: 0.85rem;
          text-decoration: none;
          border-radius: 4px;
          transition: var(--transition);
        }
        .nav-ep-btn:hover {
          background-color: #242424;
          color: white;
          border-color: #444;
        }
        .nav-ep-btn.active {
          background-color: var(--primary);
          color: white;
          border-color: var(--primary);
        }

        @media (max-width: 768px) {
          .player-screen-page {
            padding-top: 0;
            padding-bottom: 2rem;
          }
          .player-nav-back {
            display: none !important;
          }
          .player-wrapper-block {
            padding: 0 !important;
          }
          .player-container {
            border-radius: 0 !important;
            border-left: none !important;
            border-right: none !important;
          }
          .server-selector-bar {
            flex-direction: column;
            align-items: flex-start;
            gap: 8px;
            margin-top: 1rem;
          }
          .server-buttons {
            width: 100%;
            overflow-x: auto;
            padding-bottom: 4px;
            scrollbar-width: none;
          }
          .server-buttons::-webkit-scrollbar {
            display: none;
          }
          .server-btn {
            flex: 0 0 auto;
            font-size: 0.8rem;
            padding: 6px 12px;
          }
          .player-episodes-navigation {
            margin-top: 1.5rem;
          }
          .episodes-header-row {
            flex-direction: column;
            align-items: flex-start;
            gap: 12px;
            margin-bottom: 1rem;
          }
          .navigation-episodes-list {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(50px, 1fr));
            gap: 8px;
          }
          .nav-ep-btn {
            width: 100%;
            height: 36px;
            font-size: 0.8rem;
          }
        }
        .player-show-title-bar {
          margin-top: 1.2rem;
          margin-bottom: 0.8rem;
        }
        .player-show-title-link {
          text-decoration: none;
          color: #ffffff;
          display: inline-block;
          transition: color 0.2s ease, transform 0.2s ease;
        }
        .player-show-title-link:hover {
          color: var(--primary, #e50914);
          transform: translateX(2px);
        }
        .player-show-title-link h2 {
          font-size: 1.4rem;
          font-weight: 700;
          margin: 0;
          line-height: 1.3;
        }
        .player-show-ep-subtitle {
          color: var(--text-muted, #a3a3a3);
          font-size: 0.95rem;
          margin: 4px 0 0 0;
        }
      `}</style>
    </div>
  );
}
