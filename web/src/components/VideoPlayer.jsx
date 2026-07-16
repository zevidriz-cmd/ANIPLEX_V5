import React, { useEffect, useRef, useState } from "react";
import Hls from "hls.js";
import {
  Play, Pause, RotateCcw, RotateCw, Volume2, VolumeX,
  Maximize, Minimize, Settings, Subtitles, SkipForward,
  Lock, LockOpen, ArrowLeft, Loader2, X, PictureInPicture2
} from "lucide-react";
import { useProfile } from "../context/ProfileContext";

export default function VideoPlayer({
  src,
  tracks = [],
  intro,
  outro,
  initialTime = 0,
  onProgress,
  onEnded,
  embedUrl,
  fallbackNotice,
  loadingStatus,
  animeTitle,
  episodeNumber,
  onBack,
  nextEpisode,
  onNext,
  externalLoading = false,
  malId,
  tmdbId,
  tmdbEpisodeInfo,
  audioCategory = "sub",
  onPlaybackError,
  provider = "zoro",
  availableAudioCategories = [],
  onAudioCategoryChange,
  onSubtitleError
}) {
  const videoRef = useRef(null);
  const { saveSettings } = useProfile();
  const containerRef = useRef(null);
  const hlsRef = useRef(null);
  const lastSavedTimeRef = useRef(0);

  const [isPlaying, setIsPlaying] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const [hasPlaybackError, setHasPlaybackError] = useState(false);

  const triggerPlaybackError = () => {
    console.warn("Playback error triggered for provider:", provider);
    setHasPlaybackError(true);
    if (onPlaybackError) {
      onPlaybackError(provider);
    }
  };
  const networkErrorRetryCountRef = useRef(0);
  const mediaErrorRetryCountRef = useRef(0);
  const loadingTimeoutRef = useRef(null);
  const [upNextDismissed, setUpNextDismissed] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [bufferedEnd, setBufferedEnd] = useState(0);
  const [volume, setVolume] = useState(1);
  const [isMuted, setIsMuted] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [isRotatedFallback, setIsRotatedFallback] = useState(false);
  const [showControls, setShowControls] = useState(true);
  const [showSettings, setShowSettings] = useState(false);
  const [playbackSpeed, setPlaybackSpeed] = useState(() => {
    const saved = parseFloat(localStorage.getItem("anistream_playback_speed"));
    return saved && [0.5, 0.75, 1, 1.25, 1.5, 2].includes(saved) ? saved : 1;
  });
  const [aspectRatio, setAspectRatio] = useState("fit"); // "fit", "stretch", "zoom"
  const [isPiP, setIsPiP] = useState(false);
  const [isLongPressSpeedUp, setIsLongPressSpeedUp] = useState(false);
  const longPressTimeoutRef = useRef(null);
  const wasLongPressActiveRef = useRef(false);

  const [showSkipIntro, setShowSkipIntro] = useState(false);
  const [showSkipOutro, setShowSkipOutro] = useState(false);
  const [hlsQualities, setHlsQualities] = useState([]);
  const [currentQuality, setCurrentQuality] = useState(-1); // -1 = Auto
  const [activeQualityHeight, setActiveQualityHeight] = useState(null);
  const [audioTracks, setAudioTracks] = useState([]);
  const [currentAudioTrack, setCurrentAudioTrack] = useState(-1);

  // Safe arrays to prevent settings crash on null/undefined bindings
  const safeTracks = Array.isArray(tracks) ? tracks : [];
  const safeQualities = Array.isArray(hlsQualities) ? hlsQualities : [];
  const safeAudioTracks = Array.isArray(audioTracks) ? audioTracks : [];
  const safeAudioCategories = Array.isArray(availableAudioCategories) ? availableAudioCategories : [];

  // Touch Swipe & Double Tap Gestures
  const touchRef = useRef({
    startX: 0,
    startY: 0,
    startVolume: 1,
    startBrightness: 1,
    isSwipe: false,
    activeSide: null
  });
  const lastTapRef = useRef({ time: 0, x: 0 });

  const [brightness, setBrightness] = useState(1.0);
  const [gestureHUD, setGestureHUD] = useState(null); // { type, value }
  const hudTimeoutRef = useRef(null);
  const playPauseBtnRef = useRef(null);
  const settingsBtnRef = useRef(null);
  const [isLocked, setIsLocked] = useState(false);

  // Subtitle custom states
  const [subSize, setSubSize] = useState(22);
  const [subStyle, setSubStyle] = useState(1);
  const [subColor, setSubColor] = useState("#FFFFFF");
  const [subPosition, setSubPosition] = useState(10);
  const [subOpacity, setSubOpacity] = useState(60);

  const [activeTrackIndex, setActiveTrackIndex] = useState(-1);
  const [currentCuesText, setCurrentCuesText] = useState("");
  const [containerWidth, setContainerWidth] = useState(800);
  const [showNotice, setShowNotice] = useState(false);
  const [noticeData, setNoticeData] = useState(null);
  const [selectedBackupServer, setSelectedBackupServer] = useState("megaplay");

  useEffect(() => {
    if (fallbackNotice) {
      setNoticeData(fallbackNotice);
      setShowNotice(true);
      const timer = setTimeout(() => {
        setShowNotice(false);
      }, 10000);
      return () => clearTimeout(timer);
    } else {
      setShowNotice(false);
      setNoticeData(null);
    }
  }, [fallbackNotice]);

  const loadSubtitleSettings = () => {
    const savedSize = localStorage.getItem("anistream_subtitle_size");
    let initialSize = 22;
    if (savedSize === "small") initialSize = 16;
    else if (savedSize === "medium") initialSize = 22;
    else if (savedSize === "large") initialSize = 30;
    else if (savedSize) {
      const parsed = parseInt(savedSize, 10);
      if (!isNaN(parsed)) initialSize = parsed;
    }

    const savedColor = localStorage.getItem("anistream_subtitle_color");
    let initialColor = "#FFFFFF";
    if (savedColor === "white" || !savedColor) initialColor = "#FFFFFF";
    else if (savedColor === "yellow") initialColor = "#FFE600";
    else initialColor = savedColor;

    let initialOpacity = 60;
    const savedBg = localStorage.getItem("anistream_subtitle_bg");
    if (savedBg === "transparent") initialOpacity = 0;
    else if (savedBg === "opaque") initialOpacity = 100;
    else if (savedBg === "semi-transparent") initialOpacity = 60;
    else {
      const savedOpacityVal = localStorage.getItem("anistream_subtitle_bg_opacity");
      const parsed = parseInt(savedOpacityVal, 10);
      if (!isNaN(parsed)) initialOpacity = parsed;
    }

    const savedStyle = parseInt(localStorage.getItem("anistream_subtitle_style"), 10) || 1;
    const savedPos = parseInt(localStorage.getItem("anistream_subtitle_position"), 10) || 10;

    setSubSize(initialSize);
    setSubStyle(savedStyle);
    setSubColor(initialColor);
    setSubPosition(savedPos);
    setSubOpacity(initialOpacity);
  };

  // Load and listen to settings updates
  useEffect(() => {
    loadSubtitleSettings();
    window.addEventListener("anistream_subtitle_settings_changed", loadSubtitleSettings);
    return () => {
      window.removeEventListener("anistream_subtitle_settings_changed", loadSubtitleSettings);
    };
  }, []);

  // Measure container width
  useEffect(() => {
    if (!containerRef.current) return;
    const resizeObserver = new ResizeObserver((entries) => {
      for (let entry of entries) {
        setContainerWidth(entry.contentRect.width);
      }
    });
    resizeObserver.observe(containerRef.current);
    return () => resizeObserver.disconnect();
  }, []);

  // Set default active track
  useEffect(() => {
    const subsEnabled = localStorage.getItem("anistream_subtitles_enabled") !== "false";
    if (!subsEnabled) {
      setActiveTrackIndex(-1);
      return;
    }

    if (tracks && tracks.length > 0) {
      const englishIndex = tracks.findIndex(t => t.label?.toLowerCase() === "english");
      const defaultIndex = tracks.findIndex(t => t.default || t.isDefault);

      if (englishIndex !== -1) {
        setActiveTrackIndex(englishIndex);
      } else if (defaultIndex !== -1) {
        setActiveTrackIndex(defaultIndex);
      } else {
        setActiveTrackIndex(0);
      }
    } else {
      setActiveTrackIndex(-1);
    }
  }, [tracks]);

  // Handle track modes (set selected track hidden, others disabled)
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const updateTrackModes = () => {
      const textTracks = video.textTracks;
      const selectedTrack = tracks[activeTrackIndex];

      for (let i = 0; i < textTracks.length; i++) {
        const t = textTracks[i];
        const isMatch = selectedTrack && (t.label === selectedTrack.label || (textTracks.length === tracks.length && i === activeTrackIndex));

        if (isMatch) {
          t.mode = "hidden";
        } else {
          t.mode = "disabled";
        }
      }
    };

    updateTrackModes();

    const handleTrackAdded = () => {
      updateTrackModes();
    };

    video.textTracks.addEventListener("addtrack", handleTrackAdded);
    video.addEventListener("loadeddata", updateTrackModes);

    return () => {
      if (video) {
        video.textTracks.removeEventListener("addtrack", handleTrackAdded);
        video.removeEventListener("loadeddata", updateTrackModes);
      }
    };
  }, [activeTrackIndex, src, tracks]);

  // Monitor track cue changes
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const handleCueChange = (e) => {
      const track = e.target;
      if (track.mode === "hidden") {
        const activeCues = track.activeCues;
        if (activeCues && activeCues.length > 0) {
          const text = Array.from(activeCues)
            .map(cue => cue.text)
            .join("\n");
          setCurrentCuesText(text);
        } else {
          setCurrentCuesText("");
        }
      }
    };

    const registerTrackListeners = () => {
      Array.from(video.textTracks).forEach(track => {
        track.removeEventListener("cuechange", handleCueChange);
        track.addEventListener("cuechange", handleCueChange);
      });
    };

    registerTrackListeners();

    const handleTrackAdded = (e) => {
      e.track.addEventListener("cuechange", handleCueChange);
    };

    video.textTracks.addEventListener("addtrack", handleTrackAdded);

    return () => {
      if (video) {
        video.textTracks.removeEventListener("addtrack", handleTrackAdded);
        Array.from(video.textTracks).forEach(track => {
          track.removeEventListener("cuechange", handleCueChange);
        });
      }
    };
  }, [activeTrackIndex, src, tracks]);

  useEffect(() => {
    if (activeTrackIndex === -1) {
      setCurrentCuesText("");
    }
  }, [activeTrackIndex]);

  const showHUD = (type, value) => {
    setGestureHUD({ type, value });
    if (hudTimeoutRef.current) clearTimeout(hudTimeoutRef.current);
    hudTimeoutRef.current = setTimeout(() => setGestureHUD(null), 1000);
  };

  const handlePlayerTouchStart = (e) => {
    if (isLocked) return;

    if (e.touches.length === 2) {
      const t1 = e.touches[0];
      const t2 = e.touches[1];
      const dist = Math.hypot(t1.clientX - t2.clientX, t1.clientY - t2.clientY);
      touchRef.current = {
        ...touchRef.current,
        startPinchDist: dist,
        isPinch: true,
        currentPinchRatio: 1
      };
      return;
    }

    const touch = e.touches[0];
    const rect = containerRef.current.getBoundingClientRect();
    const x = touch.clientX - rect.left;
    const y = touch.clientY - rect.top;
    const w = rect.width;

    const now = Date.now();
    const delay = now - lastTapRef.current.time;

    let isDoubleTap = false;
    if (delay < 300) {
      if (x < w * 0.35) {
        e.preventDefault();
        handleRewind();
        showHUD("seek", "-10s");
        lastTapRef.current = { time: 0, x: 0 };
        isDoubleTap = true;
      } else if (x > w * 0.65) {
        e.preventDefault();
        handleForward();
        showHUD("seek", "+10s");
        lastTapRef.current = { time: 0, x: 0 };
        isDoubleTap = true;
      }
    }

    touchRef.current = {
      startTime: now,
      startX: x,
      startY: y,
      startVolume: volume,
      startBrightness: brightness,
      isSwipe: false,
      isDoubleTap,
      activeSide: x < w / 2 ? "left" : "right"
    };

    if (!isDoubleTap) {
      lastTapRef.current = { time: now, x };
    }
    postponeControlsHide();

    // Long press speedup hold trigger on touchstart
    wasLongPressActiveRef.current = false;
    if (longPressTimeoutRef.current) {
      clearTimeout(longPressTimeoutRef.current);
    }
    if (!isDoubleTap && e.touches.length === 1) {
      longPressTimeoutRef.current = setTimeout(() => {
        const video = videoRef.current;
        if (video && !video.paused) {
          setIsLongPressSpeedUp(true);
          wasLongPressActiveRef.current = true;
        }
      }, 400);
    }
  };

  const handlePlayerTouchMove = (e) => {
    if (isLocked) return;

    if (e.touches.length === 2 && touchRef.current.isPinch) {
      e.preventDefault();
      const t1 = e.touches[0];
      const t2 = e.touches[1];
      const dist = Math.hypot(t1.clientX - t2.clientX, t1.clientY - t2.clientY);
      const startDist = touchRef.current.startPinchDist;
      if (startDist > 0) {
        touchRef.current.currentPinchRatio = dist / startDist;
      }
      return;
    }

    const touch = e.touches[0];
    const rect = containerRef.current.getBoundingClientRect();
    const x = touch.clientX - rect.left;
    const y = touch.clientY - rect.top;

    const dx = x - touchRef.current.startX;
    const dy = y - touchRef.current.startY;

    if ((Math.abs(dy) > 10 || Math.abs(dx) > 10) && !touchRef.current.isSwipe) {
      touchRef.current.isSwipe = true;
    }

    if (touchRef.current.isSwipe) {
      e.preventDefault();
      const height = rect.height;
      const deltaPercent = -dy / height;

      if (touchRef.current.activeSide === "left") {
        const nextBrightness = Math.min(1.0, Math.max(0.1, touchRef.current.startBrightness + deltaPercent * 1.5));
        setBrightness(nextBrightness);
        showHUD("brightness", `${Math.round(nextBrightness * 100)}%`);
      } else {
        const nextVolume = Math.min(1.0, Math.max(0.0, touchRef.current.startVolume + deltaPercent * 1.5));
        setVolume(nextVolume);
        setIsMuted(nextVolume === 0);
        showHUD("volume", `${Math.round(nextVolume * 100)}%`);
      }

      // Clear the speedup timeout if they start swiping
      if (longPressTimeoutRef.current) {
        clearTimeout(longPressTimeoutRef.current);
      }
      if (isLongPressSpeedUp) {
        setIsLongPressSpeedUp(false);
        showHUD("speed", `${playbackSpeed}x`);
      }
    }
  };

  const handlePlayerTouchEnd = (e) => {
    if (longPressTimeoutRef.current) {
      clearTimeout(longPressTimeoutRef.current);
    }

    if (isLongPressSpeedUp) {
      setIsLongPressSpeedUp(false);
      showHUD("speed", `${playbackSpeed}x`);
      e.preventDefault();
      touchRef.current.isSwipe = false;
      return;
    }

    if (touchRef.current.isPinch) {
      const ratio = touchRef.current.currentPinchRatio;
      if (ratio && ratio > 1.15) {
        if (aspectRatio !== "zoom") {
          setAspectRatio("zoom");
          showHUD("aspect", "Zoom (Fill)");
        }
      } else if (ratio && ratio < 0.85) {
        if (aspectRatio !== "fit") {
          setAspectRatio("fit");
          showHUD("aspect", "Fit (16:9)");
        }
      }
      touchRef.current.isPinch = false;
      touchRef.current.currentPinchRatio = null;
      touchRef.current.startPinchDist = null;
      return;
    }

    if (touchRef.current.isDoubleTap) {
      touchRef.current.isDoubleTap = false;
      return;
    }

    if (!touchRef.current.isSwipe) {
      const duration = Date.now() - touchRef.current.startTime;
      if (duration < 250) {
        // Tapped!
        // Check if the user tapped on a button/interactive control
        if (
          e.target.closest(".control-btn") ||
          e.target.closest(".player-back-btn") ||
          e.target.closest(".progress-scrubber") ||
          e.target.closest(".timeline-container") ||
          e.target.closest(".timeline-input") ||
          e.target.closest(".volume-slider") ||
          e.target.closest(".settings-panel") ||
          e.target.closest(".skip-time-overlay") ||
          e.target.closest(".unlock-btn") ||
          e.target.closest(".up-next-overlay")
        ) {
          return;
        }

        e.preventDefault(); // Stop click emulation
        if (isLocked) return;

        setShowControls(prev => {
          const next = !prev;
          if (next) {
            postponeControlsHide();
          }
          return next;
        });
      }
    }
    touchRef.current.isSwipe = false;
  };

  const handlePlayerMouseDown = (e) => {
    if (e.button !== 0) return; // only left click
    if (
      e.target.closest(".control-btn") ||
      e.target.closest(".player-back-btn") ||
      e.target.closest(".progress-scrubber") ||
      e.target.closest(".timeline-container") ||
      e.target.closest(".timeline-input") ||
      e.target.closest(".volume-slider") ||
      e.target.closest(".settings-panel") ||
      e.target.closest(".skip-time-overlay") ||
      e.target.closest(".unlock-btn") ||
      e.target.closest(".up-next-overlay")
    ) {
      return;
    }
    if (isLocked) return;

    wasLongPressActiveRef.current = false;
    if (longPressTimeoutRef.current) {
      clearTimeout(longPressTimeoutRef.current);
    }
    longPressTimeoutRef.current = setTimeout(() => {
      const video = videoRef.current;
      if (video && !video.paused) {
        setIsLongPressSpeedUp(true);
        wasLongPressActiveRef.current = true;
      }
    }, 400);
  };

  const handlePlayerMouseUpOrLeave = (e) => {
    if (longPressTimeoutRef.current) {
      clearTimeout(longPressTimeoutRef.current);
    }
    if (isLongPressSpeedUp) {
      setIsLongPressSpeedUp(false);
      showHUD("speed", `${playbackSpeed}x`);
      if (e) {
        e.preventDefault();
        e.stopPropagation();
      }
    }
  };

  const handleContainerClick = (e) => {
    if (wasLongPressActiveRef.current) {
      wasLongPressActiveRef.current = false;
      e.preventDefault();
      e.stopPropagation();
      return;
    }

    if (
      e.target.closest(".control-btn") ||
      e.target.closest(".player-back-btn") ||
      e.target.closest(".progress-scrubber") ||
      e.target.closest(".timeline-container") ||
      e.target.closest(".timeline-input") ||
      e.target.closest(".volume-slider") ||
      e.target.closest(".settings-panel") ||
      e.target.closest(".skip-time-overlay") ||
      e.target.closest(".unlock-btn") ||
      e.target.closest(".up-next-overlay")
    ) {
      setShowControls(true);
      postponeControlsHide();
      return;
    }
    if (isLocked) return;

    if (!showControls && !window.matchMedia("(pointer: coarse)").matches) {
      handlePlayPause();
    }

    setShowControls(prev => {
      const next = !prev;
      if (next) {
        postponeControlsHide();
      }
      return next;
    });
  };

  // Activity timer for controls auto-hide
  const controlsTimeoutRef = useRef(null);

  const postponeControlsHide = () => {
    if (controlsTimeoutRef.current) clearTimeout(controlsTimeoutRef.current);
    controlsTimeoutRef.current = setTimeout(() => {
      if (isPlaying) setShowControls(false);
    }, 3000);
  };

  const showControlsAndResetTimeout = () => {
    setShowControls(true);
    postponeControlsHide();
  };

  useEffect(() => {
    showControlsAndResetTimeout();
    return () => {
      if (controlsTimeoutRef.current) clearTimeout(controlsTimeoutRef.current);
    };
  }, [isPlaying]);

  useEffect(() => {
    if (showControls) {
      const activeEl = document.activeElement;
      const isInside = containerRef.current && containerRef.current.contains(activeEl);
      if (!isInside && playPauseBtnRef.current) {
        playPauseBtnRef.current.focus();
      }
    }
  }, [showControls]);

  const startLoadingWatchdog = () => {
    if (loadingTimeoutRef.current) clearTimeout(loadingTimeoutRef.current);
    loadingTimeoutRef.current = setTimeout(() => {
      const video = videoRef.current;
      const isActuallyPlaying = video && !video.paused && video.currentTime > 0;
      if (!isActuallyPlaying) {
        console.warn("Loading watchdog fired: stream failed to start within 15 seconds. Triggering fallback...");
        triggerPlaybackError();
        if (hlsRef.current) {
          hlsRef.current.destroy();
        }
      }
    }, 15000);
  };

  const clearLoadingWatchdog = () => {
    if (loadingTimeoutRef.current) {
      clearTimeout(loadingTimeoutRef.current);
      loadingTimeoutRef.current = null;
    }
  };

  // Reset playback error state when the source URL changes
  useEffect(() => {
    setHasPlaybackError(false);
  }, [src]);

  // Load HLS Video or Iframe Fallback
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !src) return;

    // Reset state
    setIsPlaying(false);
    setIsLoading(true);
    setIsTransitioning(false);
    setUpNextDismissed(false);
    setCurrentTime(0);
    setDuration(0);
    lastSavedTimeRef.current = 0;
    setHasPlaybackError(false);
    networkErrorRetryCountRef.current = 0;
    mediaErrorRetryCountRef.current = 0;
    setAudioTracks([]);
    setCurrentAudioTrack(-1);

    startLoadingWatchdog();

    if (Hls.isSupported()) {
      if (hlsRef.current) {
        hlsRef.current.destroy();
      }

      const hls = new Hls({
        enableWorker: true,
        lowLatencyMode: false,
        // Aggressive pre-buffering (YouTube-style): download far ahead so proxy latency never causes stutter
        maxBufferLength: 120,            // Buffer up to 2 minutes ahead of current playback position
        maxMaxBufferLength: 600,         // When bandwidth is good, allow up to 10 minutes of pre-buffered video
        maxBufferSize: 300 * 1024 * 1024, // 300MB memory cap for high-bitrate 1080p streams
        backBufferLength: 30,            // Keep 30s of already-watched video (for rewind without re-fetch)
        maxBufferHole: 0.1,              // Aggressively fill tiny gaps in the buffer to prevent micro-stalls
        startFragPrefetch: true,         // Start downloading the next segment before the current one finishes
        // Retry and recovery tuning
        fragLoadingMaxRetry: 6,          // Retry failed segment downloads up to 6 times
        fragLoadingRetryDelay: 1000,     // Wait 1s between retries
        manifestLoadingMaxRetry: 4,      // Retry playlist fetches up to 4 times
        levelLoadingMaxRetry: 4,         // Retry quality level playlist fetches up to 4 times
        // Stall recovery
        nudgeMaxRetry: 10,               // Try harder to recover from buffer stalls
        nudgeDelay: 0.05,                // Nudge playback position faster to recover from stalls
        highBufferWatchdogPeriod: 3,     // Check for buffer health every 3 seconds
      });
      hlsRef.current = hls;

      hls.loadSource(src);
      hls.attachMedia(video);

      hls.on(Hls.Events.MANIFEST_PARSED, (event, data) => {
        const qualities = hls.levels.map((level, index) => ({
          index,
          height: level.height,
          bitrate: level.bitrate
        }));
        setHlsQualities(qualities);

        // Quality cap settings integration
        const qualityCap = localStorage.getItem("anistream_quality_cap") || "Auto";
        if (qualityCap !== "Auto") {
          const capHeight = parseInt(qualityCap, 10);
          if (!isNaN(capHeight)) {
            let maxLvlIdx = -1;
            hls.levels.forEach((level, idx) => {
              if (level.height <= capHeight) {
                if (maxLvlIdx === -1 || level.height > hls.levels[maxLvlIdx].height) {
                  maxLvlIdx = idx;
                }
              }
            });
            if (maxLvlIdx !== -1) {
              hls.maxLevel = maxLvlIdx;
              setCurrentQuality(maxLvlIdx);
            }
          }
        }

        // Restore initial saved progress if provided
        if (initialTime > 0) {
          video.currentTime = initialTime / 1000; // convert ms to seconds
        }

        // Auto play on manifest parse
        video.play().catch(e => console.log("Auto-play blocked by browser. Ready."));
      });

      hls.on(Hls.Events.LEVEL_SWITCHED, (event, data) => {
        const levelIdx = data.level;
        if (hls.levels[levelIdx]) {
          const height = hls.levels[levelIdx].height;
          console.log(`[Hls.js] Level switched to: ${height}p`);
          setActiveQualityHeight(height);
        }
      });

      // Initialize audio tracks if available
      hls.on(Hls.Events.AUDIO_TRACKS_UPDATED, (event, data) => {
        if (hls.audioTracks && hls.audioTracks.length > 0) {
          setAudioTracks(hls.audioTracks.map(track => ({
            id: track.id,
            name: track.name || track.lang || `Track ${track.id}`,
            lang: track.lang
          })));
          setCurrentAudioTrack(hls.audioTrack);
        }
      });

      hls.on(Hls.Events.AUDIO_TRACK_SWITCHED, (event, data) => {
        setCurrentAudioTrack(data.id);
      });

      hls.on(Hls.Events.ERROR, (event, data) => {
        if (data.fatal) {
          switch (data.type) {
            case Hls.ErrorTypes.NETWORK_ERROR:
              networkErrorRetryCountRef.current++;
              if (networkErrorRetryCountRef.current > 3) {
                console.error("Fatal HLS network error limit reached. Triggering fallback...");
                triggerPlaybackError();
                hls.destroy();
              } else {
                hls.startLoad();
              }
              break;
            case Hls.ErrorTypes.MEDIA_ERROR:
              mediaErrorRetryCountRef.current++;
              if (mediaErrorRetryCountRef.current > 3) {
                console.error("Fatal HLS media error limit reached. Triggering fallback...");
                triggerPlaybackError();
                hls.destroy();
              } else {
                hls.recoverMediaError();
              }
              break;
            default:
              console.error("Fatal HLS error unrecoverable. Triggering fallback:", data.details);
              triggerPlaybackError();
              hls.destroy();
              break;
          }
        }
      });
    } else if (video.canPlayType("application/vnd.apple.mpegurl")) {
      // Native Safari support
      video.src = src;
      const handleNativeError = (e) => {
        console.error("Native video element playback error:", e);
        triggerPlaybackError();
      };
      video.addEventListener("error", handleNativeError);
      video.addEventListener("loadedmetadata", () => {
        if (initialTime > 0) {
          video.currentTime = initialTime / 1000;
        }
        video.play().catch(e => console.log(e));
      });
    }

    return () => {
      clearLoadingWatchdog();
      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
    };
  }, [src, initialTime]);

  // Sync volume state
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    video.volume = volume;
    video.muted = isMuted;
  }, [volume, isMuted]);

  // Sync playback speed state
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    video.playbackRate = isLongPressSpeedUp ? 2.0 : playbackSpeed;
  }, [playbackSpeed, isLongPressSpeedUp, isPlaying, src]);

  // Skip overlays check
  useEffect(() => {
    const skipIntroEnabled = localStorage.getItem("anistream_skip_intro") !== "false";
    const skipOutroEnabled = localStorage.getItem("anistream_skip_outro") !== "false";

    // Check Skip Intro
    if (intro && intro.end > intro.start && currentTime >= intro.start && currentTime <= intro.end) {
      if (skipIntroEnabled && videoRef.current) {
        videoRef.current.currentTime = intro.end;
      } else {
        setShowSkipIntro(true);
      }
    } else {
      setShowSkipIntro(false);
    }

    // Check Skip Outro
    if (outro && outro.end > outro.start && currentTime >= outro.start && currentTime <= outro.end) {
      if (skipOutroEnabled && videoRef.current) {
        videoRef.current.currentTime = outro.end;
      } else {
        setShowSkipOutro(true);
      }
    } else {
      setShowSkipOutro(false);
    }
    // Periodic Progress Saving (every 10s, or when pausing/ending)
    if (onProgress && duration > 0) {
      const curMs = currentTime * 1000;
      const durMs = duration * 1000;

      // Save every 10 seconds (10000ms)
      if (Math.abs(curMs - lastSavedTimeRef.current) >= 10000) {
        onProgress(Math.floor(curMs), Math.floor(durMs));
        lastSavedTimeRef.current = curMs;
      }
    }
  }, [currentTime, duration, intro, outro]);

  // Save progress on unmount to prevent losing progress
  const progressRefs = useRef({ currentTime: 0, duration: 0, onProgress: null });
  useEffect(() => {
    progressRefs.current = { currentTime, duration, onProgress };
  }, [currentTime, duration, onProgress]);

  useEffect(() => {
    return () => {
      const { currentTime: cT, duration: d, onProgress: oP } = progressRefs.current;
      if (oP && d > 0 && cT > 0) {
        oP(Math.floor(cT * 1000), Math.floor(d * 1000));
      }
      if (hudTimeoutRef.current) clearTimeout(hudTimeoutRef.current);
      if (longPressTimeoutRef.current) clearTimeout(longPressTimeoutRef.current);
    };
  }, []);

  // Autoplay countdown handler
  useEffect(() => {
    if (!nextEpisode || upNextDismissed || duration === 0) return;

    const timeRemaining = duration - currentTime;
    if (timeRemaining <= 0.8 && timeRemaining > 0) {
      if (onNext) onNext();
    }
  }, [currentTime, duration, nextEpisode, upNextDismissed, onNext]);

  const handlePlayPause = () => {
    const video = videoRef.current;
    if (!video) return;

    if (video.paused) {
      video.play().then(() => setIsPlaying(true)).catch(e => console.log(e));
    } else {
      video.pause();
      setIsPlaying(false);
      // Save progress on pause
      if (onProgress && duration > 0) {
        onProgress(Math.floor(video.currentTime * 1000), Math.floor(video.duration * 1000));
      }
    }
  };

  const handleRewind = () => {
    const video = videoRef.current;
    if (video && !isNaN(video.duration) && video.duration > 0) {
      video.currentTime = Math.max(0, video.currentTime - 10);
    }
  };

  const handleForward = () => {
    const video = videoRef.current;
    if (video && !isNaN(video.duration) && video.duration > 0) {
      video.currentTime = Math.min(video.duration, video.currentTime + 10);
    }
  };

  const handleVolumeChange = (e) => {
    const val = parseFloat(e.target.value);
    setVolume(val);
    setIsMuted(val === 0);
  };

  const handleMuteToggle = () => {
    setIsMuted(!isMuted);
  };

  const handleFullscreenToggle = () => {
    const container = containerRef.current;
    if (!container) return;

    if (!document.fullscreenElement) {
      container.requestFullscreen()
        .then(() => setIsFullscreen(true))
        .catch(e => console.log(e));
    } else {
      document.exitFullscreen().then(() => setIsFullscreen(false));
    }
  };

  // Sync fullscreen state & programmatically lock/unlock orientation for mobile PWA support
  useEffect(() => {
    const handleFsChange = () => {
      const isFs = !!document.fullscreenElement;
      setIsFullscreen(isFs);

      if (!isFs) {
        setIsRotatedFallback(false);
        if (screen.orientation && screen.orientation.lock) {
          screen.orientation.lock("portrait").catch(err => {
            if (screen.orientation.unlock) screen.orientation.unlock();
            console.warn("Screen orientation lock to portrait failed:", err);
          });
        }
        return;
      }

      // We are in fullscreen
      if (screen.orientation && screen.orientation.lock) {
        screen.orientation.lock("landscape")
          .then(() => {
            setIsRotatedFallback(false);
          })
          .catch(err => {
            console.warn("Screen orientation lock to landscape failed, using CSS rotation fallback:", err);
            if (window.innerHeight > window.innerWidth) {
              setIsRotatedFallback(true);
            }
          });
      } else {
        // Fallback for browsers/emulators without screen.orientation support (like iOS Safari / Chrome DevTools)
        if (window.innerHeight > window.innerWidth) {
          setIsRotatedFallback(true);
        }
      }
    };

    document.addEventListener("fullscreenchange", handleFsChange);
    return () => {
      document.removeEventListener("fullscreenchange", handleFsChange);
      if (screen.orientation && screen.orientation.unlock) {
        try {
          screen.orientation.unlock();
        } catch (e) {
          console.warn("Screen orientation unlock failed:", e);
        }
      }
    };
  }, []);

  // Desktop keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (
        document.activeElement.tagName === "INPUT" ||
        document.activeElement.tagName === "SELECT" ||
        document.activeElement.tagName === "TEXTAREA"
      ) {
        return;
      }

      // 1. Hardware Back/Exit Key Mapping
      if (
        e.key === "Escape" ||
        e.key === "Backspace" ||
        e.key === "BrowserBack" ||
        e.key === "GoBack" ||
        e.keyCode === 27 ||
        e.keyCode === 8 ||
        e.keyCode === 461 ||
        e.keyCode === 10009
      ) {
        e.preventDefault();
        e.stopPropagation();
        if (onBack) onBack();
        return;
      }

      if (isLocked) return;

      // 2. Focused Button Select/Enter Key Interception (whether settings are open or not)
      const activeEl = document.activeElement;
      const isButton = activeEl && (activeEl.tagName === "BUTTON" || activeEl.getAttribute("role") === "button");
      const isInsideControls = containerRef.current && containerRef.current.contains(activeEl);

      if (isInsideControls && isButton) {
        if (
          e.key === "Enter" ||
          e.key === "Select" ||
          e.key === "Ok" ||
          e.key === " " ||
          e.key === "Spacebar" ||
          e.keyCode === 13 ||
          e.keyCode === 32
        ) {
          e.preventDefault();
          e.stopPropagation();
          if (!showControls) {
            setShowControls(true);
            postponeControlsHide();
          }
          activeEl.click();
          return;
        }
      }

      // 3. Settings Menu Key Interception (Prevent player shortcuts from interfering)
      if (showSettings) {
        if (
          e.key === "Enter" ||
          e.key === "Select" ||
          e.key === "Ok" ||
          e.key === " " ||
          e.key === "Spacebar" ||
          e.keyCode === 13 ||
          e.keyCode === 32
        ) {
          const activeEl = document.activeElement;
          if (activeEl && (activeEl.tagName === "BUTTON" || activeEl.getAttribute("role") === "button")) {
            e.preventDefault();
            activeEl.click();
            return;
          }
        }
        // Let Arrow keys move focus inside the settings panel naturally
        if (
          e.key === "ArrowUp" ||
          e.key === "ArrowDown" ||
          e.key === "ArrowLeft" ||
          e.key === "ArrowRight"
        ) {
          showControlsAndResetTimeout(); // Keep controls visible during navigation
          return;
        }
      }

      // 3. Main Player Keyboard Shortcuts
      switch (e.key) {
        case " ":
        case "k":
        case "K":
          e.preventDefault();
          handlePlayPause();
          showControlsAndResetTimeout();
          break;
        case "ArrowLeft":
        case "j":
        case "J":
          e.preventDefault();
          handleRewind();
          showHUD("seek", "-10s");
          showControlsAndResetTimeout();
          break;
        case "ArrowRight":
        case "l":
        case "L":
          e.preventDefault();
          handleForward();
          showHUD("seek", "+10s");
          showControlsAndResetTimeout();
          break;
        case "ArrowUp":
          e.preventDefault();
          setVolume((prev) => {
            const next = Math.min(1.0, prev + 0.1);
            showHUD("volume", `${Math.round(next * 100)}%`);
            return next;
          });
          setIsMuted(false);
          showControlsAndResetTimeout();
          break;
        case "ArrowDown":
          e.preventDefault();
          setVolume((prev) => {
            const next = Math.max(0.0, prev - 0.1);
            showHUD("volume", `${Math.round(next * 100)}%`);
            if (next === 0) setIsMuted(true);
            return next;
          });
          showControlsAndResetTimeout();
          break;
        case "f":
        case "F":
          e.preventDefault();
          handleFullscreenToggle();
          showControlsAndResetTimeout();
          break;
        case "m":
        case "M":
          e.preventDefault();
          handleMuteToggle();
          showControlsAndResetTimeout();
          break;
        case "p":
        case "P":
          e.preventDefault();
          togglePiP();
          break;
        default:
          break;
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [volume, isMuted, isLocked, isFullscreen, isPlaying, duration, currentTime, showSettings, showControls, onBack]);

  const handleProgressScrub = (e) => {
    const video = videoRef.current;
    if (!video || isNaN(duration) || duration <= 0) return;
    const val = parseFloat(e.target.value);
    if (isNaN(val)) return;
    const seekTime = (val / 100) * duration;
    if (!isNaN(seekTime)) {
      video.currentTime = seekTime;
      setCurrentTime(seekTime);
    }
  };

  const changePlaybackSpeed = (speed) => {
    if (videoRef.current) {
      videoRef.current.playbackRate = speed;
      setPlaybackSpeed(speed);
      localStorage.setItem("anistream_playback_speed", String(speed));
    }
    setShowSettings(false);
  };

  const togglePiP = async () => {
    try {
      if (document.pictureInPictureElement) {
        await document.exitPictureInPicture();
        setIsPiP(false);
      } else if (videoRef.current && document.pictureInPictureEnabled) {
        await videoRef.current.requestPictureInPicture();
        setIsPiP(true);
      }
    } catch (err) {
      console.warn("PiP error:", err);
    }
  };

  const changeQuality = (qualityIdx) => {
    if (hlsRef.current) {
      hlsRef.current.currentLevel = qualityIdx;
      setCurrentQuality(qualityIdx);
    }
    setShowSettings(false);
  };

  const changeAudioTrack = (trackId) => {
    if (hlsRef.current) {
      hlsRef.current.audioTrack = trackId;
      setCurrentAudioTrack(trackId);
    }
    setShowSettings(false);
  };

  const updateBuffer = (video) => {
    if (video && video.buffered && video.buffered.length > 0) {
      const time = video.currentTime;
      let activeRangeEnd = 0;
      for (let i = 0; i < video.buffered.length; i++) {
        const start = video.buffered.start(i);
        const end = video.buffered.end(i);
        if (time >= start && time <= end) {
          activeRangeEnd = end;
          break;
        }
      }
      if (activeRangeEnd === 0) {
        activeRangeEnd = video.buffered.end(video.buffered.length - 1);
      }
      setBufferedEnd(activeRangeEnd);
    }
  };

  const handleSkipIntro = () => {
    if (videoRef.current && intro) {
      videoRef.current.currentTime = intro.end;
      setShowSkipIntro(false);
    }
  };

  const handleSkipOutro = () => {
    if (videoRef.current && outro) {
      const autoplaySetting = localStorage.getItem("anistream_autoplay") !== "false";
      if (autoplaySetting && nextEpisode && onNext) {
        try {
          videoRef.current.pause();
        } catch (e) {
          console.warn("Failed to pause video on skip outro:", e);
        }
        setIsTransitioning(true);
        onNext();
      } else {
        videoRef.current.currentTime = outro.end;
        setShowSkipOutro(false);
      }
    }
  };

  const formatTime = (timeInSeconds) => {
    if (isNaN(timeInSeconds)) return "00:00";
    const hours = Math.floor(timeInSeconds / 3600);
    const minutes = Math.floor((timeInSeconds % 3600) / 60);
    const seconds = Math.floor(timeInSeconds % 60);

    const pad = (n) => String(n).padStart(2, "0");
    if (hours > 0) {
      return `${hours}:${pad(minutes)}:${pad(seconds)}`;
    }
    return `${pad(minutes)}:${pad(seconds)}`;
  };

  // Save progress on unmount
  useEffect(() => {
    return () => {
      const video = videoRef.current;
      if (video && onProgress && video.duration > 0) {
        onProgress(Math.floor(video.currentTime * 1000), Math.floor(video.duration * 1000));
      }
    };
  }, [src, duration]);

  // If HLS source is not available or has failed to play, render the Iframe Web Player
  if ((!src || hasPlaybackError) && (embedUrl || malId)) {
    const epNum = episodeNumber || 1;
    const season = tmdbEpisodeInfo?.season || 1;
    const episode = tmdbEpisodeInfo?.episode || epNum;

    let finalEmbedUrl = "";
    if (selectedBackupServer === "megaplay") {
      finalEmbedUrl = `https://megaplay.buzz/stream/mal/${malId}/${epNum}/${audioCategory}`;
    } else if (selectedBackupServer === "vidsrc-to") {
      if (tmdbId) {
        finalEmbedUrl = `https://vidsrc.to/embed/tv/${tmdbId}/${season}/${episode}`;
      } else {
        finalEmbedUrl = `https://vidsrc.to/embed/anime/${malId}/${epNum}`;
      }
    } else if (selectedBackupServer === "vidsrc-me") {
      if (tmdbId) {
        finalEmbedUrl = `https://vidsrc.me/embed/tv/${tmdbId}/${season}/${episode}`;
      } else {
        finalEmbedUrl = `https://vidsrc.me/embed/anime?mal=${malId}&ep=${epNum}`;
      }
    } else if (selectedBackupServer === "embed-su") {
      if (tmdbId) {
        finalEmbedUrl = `https://embed.su/embed/tv/${tmdbId}/${season}/${episode}`;
      } else {
        finalEmbedUrl = `https://vidsrc.to/embed/anime/${malId}/${epNum}`;
      }
    } else {
      finalEmbedUrl = embedUrl;
    }

    const cleanedEmbedUrl = (finalEmbedUrl || "").replace(/(\/stream\/s-[1-9]\/)(\d+)\/(\d+)/, (m, p1, p2, p3) => p1 + p3);
    const separator = cleanedEmbedUrl.includes("?") ? "&" : "?";

    return (
      <div className="iframe-layout-container">
        <div className="iframe-player-wrapper">
          {/* Floating Top Bar for Iframe Player */}
          <div className="iframe-player-top-bar">
            {onBack && (
              <button className="player-back-btn" onClick={onBack} aria-label="Back" type="button">
                <ArrowLeft size={20} />
              </button>
            )}
            <div className="title-info">
              <span className="anime-title">{animeTitle}</span>
              <span className="episode-badge">Episode {episodeNumber}</span>
            </div>
          </div>

          {showNotice && noticeData && (
            <div className={`player-fallback-banner ${noticeData.type}`}>
              <span className="notice-icon">
                {noticeData.type === "warning" ? "⚠️" : "ℹ️"}
              </span>
              <span className="notice-message">{noticeData.message}</span>
              <button className="notice-close" onClick={() => setShowNotice(false)} type="button">
                <X size={14} />
              </button>
            </div>
          )}

          <iframe
            src={`${cleanedEmbedUrl}${separator}autoPlay=1`}
            title="Episode Stream Player"
            allowFullScreen
            allow="autoplay; fullscreen; picture-in-picture"
            className="iframe-player-frame"
          ></iframe>
        </div>

        {/* Server Selector inside Iframe Fallback */}
        <div className="iframe-server-bar">
          <span className="server-bar-label">Select Backup Server:</span>
          <div className="server-bar-buttons">
            {[
              { id: "megaplay", name: "Backup Server 1 (MegaPlay - Direct MAL)" },
              { id: "vidsrc-to", name: "Backup Server 2 (VidSrc.to)" },
              { id: "vidsrc-me", name: "Backup Server 3 (VidSrc.me)" },
              { id: "embed-su", name: "Backup Server 4 (Embed.su)" }
            ].map((srv) => (
              <button
                key={srv.id}
                className={`iframe-server-btn ${selectedBackupServer === srv.id ? "active" : ""}`}
                onClick={() => setSelectedBackupServer(srv.id)}
                type="button"
              >
                {srv.name}
              </button>
            ))}
          </div>
        </div>

        <style>{`
          .iframe-layout-container {
            display: flex;
            flex-direction: column;
            gap: 12px;
            width: 100%;
          }
          .iframe-server-bar {
            display: flex;
            align-items: center;
            gap: 12px;
            background: #111;
            padding: 10px 16px;
            border-radius: 8px;
            border: 1px solid var(--border);
          }
          .server-bar-label {
            font-size: 0.85rem;
            font-weight: 700;
            color: var(--text-secondary);
          }
          .server-bar-buttons {
            display: flex;
            gap: 8px;
            flex-wrap: wrap;
          }
          .iframe-server-btn {
            background-color: var(--bg-card);
            border: 1px solid var(--border);
            color: var(--text-secondary);
            padding: 6px 12px;
            border-radius: 4px;
            cursor: pointer;
            font-family: var(--font-family);
            font-size: 0.8rem;
            font-weight: 600;
            transition: var(--transition);
          }
          .iframe-server-btn:hover {
            background-color: #242424;
            color: white;
            border-color: #444;
          }
          .iframe-server-btn.active {
            background-color: var(--primary);
            color: white;
            border-color: var(--primary);
          }
          .player-fallback-banner {
            position: absolute;
            top: 60px;
            left: 50%;
            transform: translateX(-50%);
            z-index: 50;
            display: flex;
            align-items: center;
            gap: 10px;
            background: rgba(18, 18, 18, 0.95);
            border: 1px solid rgba(255, 255, 255, 0.15);
            padding: 8px 16px;
            border-radius: 6px;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
            color: #fff;
            font-size: 0.85rem;
            pointer-events: auto;
            animation: fadeInSub 0.3s ease;
          }
          .player-fallback-banner.warning {
            border-left: 4px solid #f59e0b;
          }
          .player-fallback-banner.info {
            border-left: 4px solid #3b82f6;
          }
          .player-fallback-banner .notice-icon {
            font-size: 1rem;
          }
          .player-fallback-banner .notice-close {
            background: none;
            border: none;
            color: rgba(255, 255, 255, 0.6);
            cursor: pointer;
            padding: 2px;
            margin-left: 8px;
            display: flex;
            align-items: center;
            transition: color 0.2s;
          }
          .player-fallback-banner .notice-close:hover {
            color: #fff;
          }
          @keyframes fadeInSub {
            from { opacity: 0; transform: translate(-50%, -10px); }
            to { opacity: 1; transform: translate(-50%, 0); }
          }
          .iframe-player-wrapper {
            position: relative;
            width: 100%;
            padding-top: 56.25%; /* 16:9 ratio */
            background: #000;
            border-radius: 8px;
            overflow: hidden;
            border: 1px solid var(--border);
          }
          .iframe-player-frame {
            position: absolute;
            inset: 0;
            width: 100%;
            height: 100%;
            border: none;
            z-index: 1;
          }
          .iframe-player-top-bar {
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            height: 50px;
            background: linear-gradient(to bottom, rgba(0, 0, 0, 0.85) 0%, rgba(0, 0, 0, 0.3) 70%, transparent 100%);
            display: flex;
            align-items: center;
            padding: 0 16px;
            z-index: 10;
            pointer-events: auto;
          }
          .iframe-player-top-bar .title-info {
            display: flex;
            align-items: center;
            gap: 8px;
            color: white;
            font-family: var(--font-family);
          }
          .iframe-player-top-bar .anime-title {
            font-size: 0.95rem;
            font-weight: 700;
            text-shadow: 0 1px 3px rgba(0, 0, 0, 0.8);
          }
          .iframe-player-top-bar .episode-badge {
            background-color: var(--primary);
            color: white;
            padding: 2px 6px;
            border-radius: 4px;
            font-size: 0.7rem;
            font-weight: 700;
          }
          .iframe-player-top-bar .player-back-btn {
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 6px;
            margin-right: 8px;
            border-radius: 50%;
            transition: var(--transition);
          }
          .iframe-player-top-bar .player-back-btn:hover {
            background-color: rgba(255, 255, 255, 0.15);
          }
          @media (max-width: 768px) {
            .iframe-server-bar {
              flex-direction: column;
              align-items: flex-start;
              gap: 8px;
              padding: 8px 12px;
            }
            .server-bar-buttons {
              width: 100%;
            }
            .iframe-server-btn {
              flex: 1 1 auto;
              text-align: center;
              font-size: 0.75rem;
              padding: 5px 10px;
            }
          }
        `}</style>
      </div>
    );
  }

  const getSubFontStyles = () => {
    switch (subStyle) {
      case 2: // Serif
        return { fontFamily: "'Georgia', 'Times New Roman', serif", fontWeight: "normal", textShadow: "0 2px 4px rgba(0, 0, 0, 0.9)" };
      case 3: // Monospace
        return { fontFamily: "'Courier New', Courier, monospace", fontWeight: "normal", textShadow: "0 2px 4px rgba(0, 0, 0, 0.9)" };
      case 4: // Outlined
        return { fontFamily: "var(--font-family)", fontWeight: "bold", textShadow: "-1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000, 1px 1px 0 #000, 0 2px 4px rgba(0, 0, 0, 0.9)" };
      case 5: // Bold
        return { fontFamily: "var(--font-family)", fontWeight: "bold", textShadow: "0 2px 4px rgba(0, 0, 0, 0.9)" };
      case 1: // Sans-Serif
      default:
        return { fontFamily: "var(--font-family)", fontWeight: "normal", textShadow: "0 2px 4px rgba(0, 0, 0, 0.9)" };
    }
  };

  const cleanWebVttText = (text) => {
    if (!text) return "";
    return text
      .replace(/<v[^>]*>/g, "")
      .replace(/<\/v>/g, "")
      .replace(/<c[^>]*>/g, "")
      .replace(/<\/c>/g, "")
      .replace(/<[^>]+>/g, (match) => {
        if (['<b>', '</b>', '<i>', '</i>', '<u>', '</u>', '<br>', '<br/>', '<br />'].includes(match.toLowerCase())) {
          return match;
        }
        return "";
      });
  };

  const calculatedFontSize = Math.max(12, Math.min(48, (subSize * containerWidth) / 1000));

  const subtitleOverlayStyles = {
    position: "absolute",
    left: "50%",
    transform: "translateX(-50%)",
    bottom: `${subPosition}%`,
    zIndex: 36,
    color: subColor,
    fontSize: `${calculatedFontSize}px`,
    backgroundColor: `rgba(0, 0, 0, ${subOpacity / 100})`,
    padding: "6px 12px",
    borderRadius: "6px",
    textAlign: "center",
    maxWidth: "85%",
    wordBreak: "break-word",
    lineHeight: "1.4",
    pointerEvents: "none",
    transition: "bottom 0.2s, font-size 0.1s",
    ...getSubFontStyles()
  };

  return (
    <div
      className={`player-container ${isFullscreen ? "fullscreen" : ""} ${isRotatedFallback ? "fullscreen-portrait-rotated" : ""}`}
      ref={containerRef}
      onMouseMove={showControlsAndResetTimeout}
      onMouseDown={handlePlayerMouseDown}
      onMouseUp={handlePlayerMouseUpOrLeave}
      onMouseLeave={(e) => {
        if (isPlaying) setShowControls(false);
        handlePlayerMouseUpOrLeave(e);
      }}
      onTouchStart={handlePlayerTouchStart}
      onTouchMove={handlePlayerTouchMove}
      onTouchEnd={handlePlayerTouchEnd}
      onTouchCancel={handlePlayerTouchEnd}
      onClick={handleContainerClick}
      onDoubleClick={(e) => {
        if (
          e.target.closest(".control-btn") ||
          e.target.closest(".player-back-btn") ||
          e.target.closest(".progress-scrubber") ||
          e.target.closest(".volume-slider") ||
          e.target.closest(".settings-panel") ||
          e.target.closest(".skip-time-overlay") ||
          e.target.closest(".locked-state-overlay")
        ) {
          return;
        }
        handleFullscreenToggle();
      }}
    >
      {showNotice && noticeData && (
        <div className={`player-fallback-banner ${noticeData.type}`}>
          <span className="notice-icon">
            {noticeData.type === "warning" ? "⚠️" : "ℹ️"}
          </span>
          <span className="notice-message">{noticeData.message}</span>
          <button className="notice-close" onClick={() => setShowNotice(false)} type="button">
            <X size={14} />
          </button>
        </div>
      )}
      {/* Brightness Emulation Overlay */}
      <div
        className="brightness-emulation-overlay"
        style={{
          opacity: 1 - brightness,
          backgroundColor: "black",
          position: "absolute",
          inset: 0,
          pointerEvents: "none",
          zIndex: 45
        }}
      />

      {/* Loading Spinner */}
      {(isLoading || externalLoading || isTransitioning) && (
        <div className="player-loading-overlay flex-center">
          <Loader2 className="spin-icon" size={48} />
        </div>
      )}

      {/* Gesture HUD */}
      {gestureHUD && (
        <div className="gesture-hud-overlay flex-center">
          <div className="gesture-hud-content">
            <span className="gesture-hud-val">
              {gestureHUD.type === "volume" && `🔊 Volume: ${gestureHUD.value}`}
              {gestureHUD.type === "brightness" && `☀️ Brightness: ${gestureHUD.value}`}
              {gestureHUD.type === "seek" && `⏩ Seek: ${gestureHUD.value}`}
              {gestureHUD.type === "aspect" && `📺 Screen Fit: ${gestureHUD.value}`}
              {gestureHUD.type === "speed" && `⚡ Speed: ${gestureHUD.value}`}
            </span>
          </div>
        </div>
      )}

      {/* Long Press 2x Speed Pill */}
      {isLongPressSpeedUp && (
        <div className="long-press-speedup-pill">
          <span className="speedup-icon">▶▶</span>
          <span>2.0X SPEED</span>
        </div>
      )}

      {/* Screen Lock Mobile Overlay */}
      {isLocked && (
        <div
          className="locked-state-overlay"
          onClick={() => {
            setShowControls(prev => {
              const next = !prev;
              if (next) {
                postponeControlsHide();
              }
              return next;
            });
          }}
        >
          {showControls && (
            <button
              className="unlock-btn flex-center"
              onClick={(e) => {
                e.stopPropagation();
                setIsLocked(false);
                showControlsAndResetTimeout();
              }}
            >
              <LockOpen size={20} /> Tap to Unlock
            </button>
          )}
        </div>
      )}

      <video
        ref={videoRef}
        className="video-element"
        style={{
          objectFit: aspectRatio === "fit" ? "contain" : aspectRatio === "stretch" ? "fill" : "cover"
        }}
        onPlay={() => {
          setIsPlaying(true);
          setIsLoading(false);
        }}
        onPause={() => setIsPlaying(false)}
        onTimeUpdate={(e) => {
          const video = e.target;
          setCurrentTime(video.currentTime);
          updateBuffer(video);
          if (video.currentTime > 0 && !video.paused) {
            clearLoadingWatchdog();
          }
        }}
        onProgress={(e) => {
          updateBuffer(e.target);
        }}
        onSeeking={(e) => {
          updateBuffer(e.target);
        }}
        onSeeked={(e) => {
          updateBuffer(e.target);
        }}
        onDurationChange={(e) => setDuration(e.target.duration)}
        onVolumeChange={(e) => {
          const video = e.target;
          setVolume(video.volume);
          setIsMuted(video.muted);
        }}
        onEnded={() => {
          const video = videoRef.current;
          if (video && video.duration > 0 && Math.abs(video.currentTime - video.duration) < 3) {
            if (onEnded) onEnded();
          }
        }}
        onWaiting={() => {
          setIsLoading(true);
          startLoadingWatchdog();
        }}
        onPlaying={() => {
          setIsLoading(false);
          setIsTransitioning(false);
          clearLoadingWatchdog();
        }}
        onCanPlay={() => {
          setIsLoading(false);
          setIsTransitioning(false);
          clearLoadingWatchdog();
        }}
        onLoadStart={() => setIsLoading(true)}
        onLoadedData={() => {
          setIsLoading(false);
          setIsTransitioning(false);
          clearLoadingWatchdog();
        }}
        onError={(e) => {
          console.error("Native HTML5 video element error event triggered:", e);
          triggerPlaybackError();
        }}
        crossOrigin="anonymous"
      >
        {tracks.map((track, i) => (
          <track
            key={i}
            src={track.file}
            kind={track.kind || "subtitles"}
            label={track.label}
            srcLang={track.label?.substring(0, 2).toLowerCase() || "en"}
            default={track.label?.toLowerCase() === "english" || i === 0}
            onError={(e) => {
              console.warn(`[VideoPlayer] Subtitle track failed to load: ${track.label} (${track.file})`);
              if (onSubtitleError) onSubtitleError(track);
            }}
          />
        ))}
      </video>

      {/* Custom DOM Subtitle Overlay */}
      {activeTrackIndex !== -1 && currentCuesText && (
        <div
          className="custom-subtitle-overlay"
          style={subtitleOverlayStyles}
          dangerouslySetInnerHTML={{ __html: cleanWebVttText(currentCuesText).replace(/\n/g, "<br/>") }}
        />
      )}

      {/* Tap/Click capturing overlay when controls are hidden */}
      {!showControls && (
        <div
          className="player-click-overlay"
          style={{
            position: "absolute",
            inset: 0,
            zIndex: 35,
            backgroundColor: "transparent",
            cursor: "pointer"
          }}
        />
      )}

      {/* Intro Skip Overlay */}
      {showSkipIntro && (
        <button className="skip-time-overlay intro" onClick={handleSkipIntro}>
          <SkipForward size={16} /> Skip Intro
        </button>
      )}

      {/* Outro Skip Overlay */}
      {showSkipOutro && !isTransitioning && (
        <button className="skip-time-overlay outro" onClick={handleSkipOutro}>
          <SkipForward size={16} /> Skip Outro
        </button>
      )}

      {/* Up Next Autoplay Overlay */}
      {nextEpisode && !upNextDismissed && !isNaN(duration) && duration > 0 && !isNaN(currentTime) && (duration - currentTime <= 15) && (
        <div className="up-next-overlay" onClick={(e) => e.stopPropagation()}>
          <button
            className="up-next-close-btn"
            onClick={(e) => {
              e.stopPropagation();
              setUpNextDismissed(true);
            }}
            aria-label="Dismiss Up Next"
            type="button"
          >
            <X size={14} />
          </button>

          <div className="up-next-content">
            <span className="up-next-label">Up Next</span>
            <div className="up-next-row">
              <div className="up-next-poster-wrapper">
                <img src={nextEpisode.poster} alt={nextEpisode.title} className="up-next-poster" />
                <button
                  className="up-next-play-icon-btn flex-center"
                  onClick={(e) => {
                    e.stopPropagation();
                    if (onNext) onNext();
                  }}
                  type="button"
                >
                  <Play size={18} fill="white" />
                </button>
              </div>
              <div className="up-next-info">
                <h4 className="up-next-title" title={nextEpisode.title}>{nextEpisode.title}</h4>
                <p className="up-next-desc">Episode {nextEpisode.number}</p>
                <div className="up-next-timer">
                  {duration - currentTime <= 10 ? (
                    <span>Autoplay in <strong>{Math.max(0, Math.floor(duration - currentTime))}</strong>s</span>
                  ) : (
                    <span>Up next soon</span>
                  )}
                </div>
              </div>
            </div>
            <button
              className="btn btn-primary up-next-btn-play"
              onClick={(e) => {
                e.stopPropagation();
                if (onNext) onNext();
              }}
              type="button"
            >
              Play Now
            </button>
          </div>
        </div>
      )}

      {/* Custom Controls UI */}
      <div
        className={`controls-wrapper ${showControls ? "visible" : "hidden"}`}
        onFocusCapture={() => {
          setShowControls(true);
          postponeControlsHide();
        }}
      >
        {/* Top title bar */}
        <div className="player-top-bar">
          {onBack && (
            <button className="player-back-btn" onClick={onBack} aria-label="Back" type="button">
              <ArrowLeft size={24} />
            </button>
          )}
          <div className="title-info">
            <span className="anime-title">{animeTitle}</span>
            <span className="episode-badge">Episode {episodeNumber}</span>
          </div>
        </div>

        {/* Center buttons */}
        {!isLoading && (
          <div className="player-center-controls">
            <button className="control-btn center-btn" onClick={handleRewind}>
              <RotateCcw size={28} />
            </button>
            <button
              ref={playPauseBtnRef}
              className="control-btn center-btn play-pause-btn"
              onClick={handlePlayPause}
            >
              {isPlaying ? <Pause size={38} fill="white" /> : <Play size={38} fill="white" />}
            </button>
            <button className="control-btn center-btn" onClick={handleForward}>
              <RotateCw size={28} />
            </button>
          </div>
        )}

        {/* Bottom controls panel */}
        <div className="player-bottom-panel">
          {/* Progress Timeline Scrubber with Buffer Indicator */}
          <div className="scrubber-wrapper">
            <div className="timeline-container">
              <div className="timeline-track">
                <div
                  className="timeline-buffered"
                  style={{ width: `${duration ? (bufferedEnd / duration) * 100 : 0}%` }}
                />
                <div
                  className="timeline-played"
                  style={{ width: `${duration ? (currentTime / duration) * 100 : 0}%` }}
                />
              </div>
              <input
                type="range"
                min="0"
                max="100"
                step="0.1"
                value={duration ? (currentTime / duration) * 100 : 0}
                onChange={handleProgressScrub}
                className="timeline-input"
              />
            </div>
            <div className="time-display">
              <span>{formatTime(currentTime)}</span>
              <span>/</span>
              <span>{formatTime(duration)}</span>
            </div>
          </div>

          {/* Action buttons row */}
          <div className="controls-row">
            <div className="left-controls">
              <button className="control-btn" onClick={handleMuteToggle}>
                {isMuted ? <VolumeX size={20} /> : <Volume2 size={20} />}
              </button>
              <input
                type="range"
                min="0"
                max="1"
                step="0.05"
                value={isMuted ? 0 : volume}
                onChange={handleVolumeChange}
                className="volume-slider"
              />
            </div>

            <div className="right-controls">
              {/* Screen Fit Quick Toggle */}
              <button
                className="control-btn"
                onClick={(e) => {
                  e.stopPropagation();
                  const nextMode = aspectRatio === "fit" ? "stretch" : aspectRatio === "stretch" ? "zoom" : "fit";
                  const labels = { fit: "Fit", stretch: "Stretch", zoom: "Zoom" };
                  setAspectRatio(nextMode);
                  showHUD("aspect", labels[nextMode]);
                }}
                title="Cycle Screen Fit (Fit / Stretch / Zoom)"
                type="button"
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ transition: "transform 0.2s" }}>
                  <rect x="2" y="4" width="20" height="16" rx="2" />
                  <path d="M6 9V6h3" />
                  <path d="M18 9V6h-3" />
                  <path d="M6 15v3h3" />
                  <path d="M18 15v3h-3" />
                </svg>
              </button>

              {/* Settings Trigger */}
              <div className="settings-menu-wrapper">
                <button
                  ref={settingsBtnRef}
                  className={`control-btn ${showSettings ? "active" : ""}`}
                  onClick={(e) => {
                    e.stopPropagation();
                    const next = !showSettings;
                    setShowSettings(next);
                    if (next) {
                      setShowControls(true);
                      postponeControlsHide();
                    }
                  }}
                >
                  <Settings size={20} />
                </button>

                {showSettings && (
                  <div className="settings-panel">
                    <div className="settings-section">
                      <h4>Playback Speed</h4>
                      <div className="settings-options">
                        {[0.5, 1, 1.25, 1.5, 2].map(speed => (
                          <button
                            key={speed}
                            onClick={() => changePlaybackSpeed(speed)}
                            className={playbackSpeed === speed ? "active" : ""}
                          >
                            {speed}x
                          </button>
                        ))}
                      </div>
                    </div>
                    <div className="settings-section">
                      <h4>Screen Fit</h4>
                      <div className="settings-options">
                        {[
                          { id: "fit", label: "Fit" },
                          { id: "stretch", label: "Stretch" },
                          { id: "zoom", label: "Zoom" }
                        ].map(mode => (
                          <button
                            key={mode.id}
                            onClick={() => {
                              setAspectRatio(mode.id);
                              const labels = { fit: "Fit", stretch: "Stretch", zoom: "Zoom" };
                              showHUD("aspect", labels[mode.id]);
                              setShowSettings(false);
                            }}
                            className={aspectRatio === mode.id ? "active" : ""}
                          >
                            {mode.label}
                          </button>
                        ))}
                      </div>
                    </div>
                    {(safeAudioTracks.length > 0 || (safeAudioCategories && safeAudioCategories.length > 1)) && (
                      <div className="settings-section">
                        <h4>Audio</h4>
                        <div className="settings-options scrollable full-width">
                          {safeAudioTracks.length > 0 ? (
                            safeAudioTracks.map((track) => (
                              <button
                                key={track.id}
                                onClick={() => changeAudioTrack(track.id)}
                                className={currentAudioTrack === track.id ? "active" : ""}
                              >
                                {track.name}
                              </button>
                            ))
                          ) : (
                            safeAudioCategories.map((cat) => (
                              <button
                                key={cat}
                                onClick={() => {
                                  if (cat !== audioCategory && onAudioCategoryChange) {
                                    onAudioCategoryChange(cat);
                                  }
                                  setShowSettings(false);
                                }}
                                className={audioCategory === cat ? "active" : ""}
                              >
                                {cat === "sub" ? "Subbed (Japanese)" : "Dubbed (English)"}
                              </button>
                            ))
                          )}
                        </div>
                      </div>
                    )}
                    {safeTracks.length > 0 && (
                      <div className="settings-section">
                        <h4>Subtitles</h4>
                        <div className="settings-options scrollable">
                          <button
                            onClick={() => {
                              setActiveTrackIndex(-1);
                              localStorage.setItem("anistream_subtitles_enabled", "false");
                              saveSettings();
                              setShowSettings(false);
                            }}
                            className={activeTrackIndex === -1 ? "active" : ""}
                          >
                            Off
                          </button>
                          {safeTracks.map((track, idx) => (
                            <button
                              key={idx}
                              onClick={() => {
                                setActiveTrackIndex(idx);
                                localStorage.setItem("anistream_subtitles_enabled", "true");
                                saveSettings();
                                setShowSettings(false);
                              }}
                              className={activeTrackIndex === idx ? "active" : ""}
                            >
                              {track.label || `Track ${idx + 1}`}
                            </button>
                          ))}
                        </div>
                      </div>
                    )}
                    {safeQualities.length > 0 && (
                      <div className="settings-section">
                        <h4>Quality</h4>
                        <div className="settings-options scrollable">
                          <button
                            onClick={() => changeQuality(-1)}
                            className={currentQuality === -1 ? "active" : ""}
                          >
                            {currentQuality === -1 && activeQualityHeight ? `Auto (${activeQualityHeight}p)` : "Auto"}
                          </button>
                          {safeQualities.map(q => (
                            <button
                              key={q.index}
                              onClick={() => changeQuality(q.index)}
                              className={currentQuality === q.index ? "active" : ""}
                            >
                              {q.height}p
                            </button>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </div>

              {/* Lock Controls Button */}
              {!isLocked && (
                <button
                  className="control-btn"
                  onClick={(e) => {
                    e.stopPropagation();
                    setIsLocked(true);
                    setShowControls(false);
                  }}
                  title="Lock Controls"
                >
                  <Lock size={20} />
                </button>
              )}

              {/* Picture-in-Picture */}
              {document.pictureInPictureEnabled && (
                <button
                  className={`control-btn ${isPiP ? "active" : ""}`}
                  onClick={(e) => { e.stopPropagation(); togglePiP(); }}
                  title="Picture-in-Picture (P)"
                  type="button"
                >
                  <PictureInPicture2 size={20} />
                </button>
              )}

              <button className="control-btn" onClick={handleFullscreenToggle}>
                {isFullscreen ? <Minimize size={20} /> : <Maximize size={20} />}
              </button>
            </div>
          </div>
        </div>
      </div>

      <style>{`
        .player-fallback-banner {
          position: absolute;
          top: 60px;
          left: 50%;
          transform: translateX(-50%);
          z-index: 50;
          display: flex;
          align-items: center;
          gap: 10px;
          background: rgba(18, 18, 18, 0.95);
          border: 1px solid rgba(255, 255, 255, 0.15);
          padding: 8px 16px;
          border-radius: 6px;
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
          color: #fff;
          font-size: 0.85rem;
          pointer-events: auto;
          animation: fadeInSub 0.3s ease;
        }
        .player-fallback-banner.warning {
          border-left: 4px solid #f59e0b;
        }
        .player-fallback-banner.info {
          border-left: 4px solid #3b82f6;
        }
        .player-fallback-banner .notice-icon {
          font-size: 1rem;
        }
        .player-fallback-banner .notice-close {
          background: none;
          border: none;
          color: rgba(255, 255, 255, 0.6);
          cursor: pointer;
          padding: 2px;
          margin-left: 8px;
          display: flex;
          align-items: center;
          transition: color 0.2s;
        }
        .player-fallback-banner .notice-close:hover {
          color: #fff;
        }
        .player-container {
          position: relative;
          width: 100%;
          padding-top: 56.25%; /* 16:9 aspect ratio */
          background-color: #000;
          overflow: hidden;
          border-radius: 8px;
          border: 1px solid var(--border);
        }
        .player-container.fullscreen {
          padding-top: 0;
          height: 100vh;
          width: 100vw;
          border-radius: 0;
          border: none;
        }
        .player-container.fullscreen-portrait-rotated {
          transform: rotate(90deg) !important;
          transform-origin: center !important;
          width: 100vh !important;
          height: 100vw !important;
          position: fixed !important;
          top: 50% !important;
          left: 50% !important;
          transform: translate(-50%, -50%) rotate(90deg) !important;
          z-index: 99999 !important;
        }
        .video-element {
          position: absolute;
          top: 0;
          left: 0;
          width: 100%;
          height: 100%;
          object-fit: contain;
        }
        
        /* Skip Overlays */
        .skip-time-overlay {
          position: absolute;
          bottom: 120px;
          right: 30px;
          z-index: 50;
          background-color: rgba(229, 9, 20, 0.95);
          color: white;
          border: none;
          padding: 10px 20px;
          font-family: var(--font-family);
          font-size: 0.9rem;
          font-weight: 700;
          border-radius: 4px;
          cursor: pointer;
          display: flex;
          align-items: center;
          gap: 0.5rem;
          box-shadow: 0 4px 15px rgba(0, 0, 0, 0.4);
          transition: var(--transition);
        }
        .skip-time-overlay:hover {
          transform: scale(1.05);
          background-color: white;
          color: var(--primary);
        }

        /* Controls styling */
        .controls-wrapper {
          position: absolute;
          inset: 0;
          background: linear-gradient(to bottom, rgba(0,0,0,0.6) 0%, transparent 20%, transparent 80%, rgba(0,0,0,0.8) 100%);
          z-index: 40;
          display: flex;
          flex-direction: column;
          justify-content: space-between;
          padding: 20px;
          transition: opacity 0.3s ease;
        }
        .controls-wrapper.visible { opacity: 1; pointer-events: auto; }
        .controls-wrapper.hidden { opacity: 0; pointer-events: none; }
        
        .player-top-bar {
          display: flex;
          align-items: center;
          justify-content: flex-start;
          width: 100%;
        }
        .player-back-btn {
          background: none;
          border: none;
          color: white;
          cursor: pointer;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 8px;
          margin-right: 12px;
          border-radius: 50%;
          transition: var(--transition);
        }
        .player-back-btn:hover {
          background-color: rgba(255, 255, 255, 0.1);
        }
        .title-info {
          display: flex;
          align-items: center;
          gap: 10px;
        }
        .anime-title {
          font-size: 1.1rem;
          font-weight: 700;
          color: white;
        }
        .episode-badge {
          background-color: rgba(255, 255, 255, 0.2);
          padding: 2px 6px;
          border-radius: 4px;
          font-size: 0.75rem;
          font-weight: 600;
        }

        .player-center-controls {
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 2.5rem;
        }
        .control-btn {
          background: none;
          border: none;
          color: rgba(255, 255, 255, 0.85);
          cursor: pointer;
          transition: var(--transition);
          display: flex;
          align-items: center;
          justify-content: center;
        }
        .control-btn:hover {
          color: white;
          transform: scale(1.1);
        }
        .play-pause-btn {
          background-color: var(--primary);
          width: 70px;
          height: 70px;
          border-radius: 50%;
          box-shadow: 0 4px 10px rgba(0,0,0,0.3);
        }
        .play-pause-btn:hover {
          background-color: var(--primary-hover);
        }

        .player-bottom-panel {
          display: flex;
          flex-direction: column;
          gap: 12px;
        }
        .scrubber-wrapper {
          display: flex;
          align-items: center;
          gap: 15px;
        }
        .timeline-container {
          flex-grow: 1;
          position: relative;
          height: 14px;
          display: flex;
          align-items: center;
          cursor: pointer;
        }
        .timeline-track {
          position: absolute;
          left: 0;
          right: 0;
          height: 4px;
          background: rgba(255, 255, 255, 0.15);
          border-radius: 2px;
          overflow: hidden;
          transition: height 0.15s ease;
        }
        .timeline-container:hover .timeline-track {
          height: 6px;
        }
        .timeline-buffered {
          position: absolute;
          top: 0;
          left: 0;
          height: 100%;
          background: rgba(255, 255, 255, 0.35);
          border-radius: 2px;
          transition: width 0.3s ease;
        }
        .timeline-played {
          position: absolute;
          top: 0;
          left: 0;
          height: 100%;
          background: var(--primary);
          border-radius: 2px;
        }
        .timeline-input {
          position: absolute;
          left: 0;
          right: 0;
          width: 100%;
          height: 100%;
          opacity: 0;
          cursor: pointer;
          margin: 0;
          z-index: 2;
        }
        .timeline-container:hover .timeline-played::after {
          content: '';
          position: absolute;
          right: -6px;
          top: 50%;
          transform: translateY(-50%);
          width: 12px;
          height: 12px;
          background: var(--primary);
          border-radius: 50%;
          box-shadow: 0 0 4px rgba(0, 0, 0, 0.4);
        }
        .time-display {
          font-size: 0.8rem;
          font-weight: 500;
          color: var(--text-secondary);
          display: flex;
          gap: 4px;
          min-width: 90px;
          justify-content: flex-end;
        }

        .controls-row {
          display: flex;
          align-items: center;
          justify-content: space-between;
        }
        .left-controls, .right-controls {
          display: flex;
          align-items: center;
          gap: 15px;
        }
        .volume-slider {
          width: 80px;
          height: 4px;
          cursor: pointer;
          accent-color: var(--primary);
        }

        /* Settings dropdown Panel */
        .settings-menu-wrapper {
          position: relative;
        }
        .settings-panel {
          position: absolute;
          bottom: calc(100% + 15px);
          right: 0;
          background: #141414;
          border: 1px solid var(--border);
          border-radius: 8px;
          padding: 12px;
          width: 200px;
          box-shadow: 0 8px 30px rgba(0, 0, 0, 0.6);
          display: flex;
          flex-direction: column;
          gap: 12px;
          z-index: 50;
          max-height: 280px;
          overflow-y: auto;
          scrollbar-width: thin;
        }
        .settings-panel::-webkit-scrollbar {
          width: 4px;
        }
        .settings-panel::-webkit-scrollbar-track {
          background: transparent;
        }
        .settings-panel::-webkit-scrollbar-thumb {
          background: rgba(255, 255, 255, 0.15);
          border-radius: 2px;
        }
        .settings-panel::-webkit-scrollbar-thumb:hover {
          background: var(--primary);
        }
        .settings-section h4 {
          font-size: 0.75rem;
          text-transform: uppercase;
          letter-spacing: 0.05em;
          color: var(--text-muted);
          margin-bottom: 6px;
        }
        .settings-options {
          display: flex;
          flex-wrap: wrap;
          gap: 4px;
        }
        .settings-options.scrollable {
          max-height: 120px;
          overflow-y: auto;
        }
        .settings-options.full-width button {
          flex: 1 0 100% !important;
          text-align: left;
          padding: 6px 8px;
        }
        .settings-options button {
          flex: 1 0 calc(50% - 4px);
          background: var(--bg-input);
          border: 1px solid var(--border);
          color: var(--text-secondary);
          padding: 4px 6px;
          font-size: 0.75rem;
          border-radius: 4px;
          cursor: pointer;
          font-family: var(--font-family);
          transition: var(--transition);
        }
        .settings-options button.active {
          background: var(--primary);
          color: white;
          border-color: var(--primary);
        }
        .settings-options button:hover:not(.active) {
          background: #2A2A2A;
          color: white;
        }

        /* Subtitle styles custom overrides using cue selector */
        video::cue {
          background-color: ${subOpacity === 0
          ? "transparent"
          : `rgba(0, 0, 0, ${subOpacity / 100})`
        } !important;
          color: ${subColor} !important;
          text-shadow: 0 1px 2px rgba(0, 0, 0, 0.9) !important;
          font-family: var(--font-family) !important;
          font-size: ${subSize}px !important;
        }

        /* Custom DOM Subtitle Overlay */
        .custom-subtitle-overlay {
          pointer-events: none;
          text-align: center;
          user-select: none;
          -webkit-user-select: none;
        }

        /* Loading Spinner Overlay */
        .player-loading-overlay {
          position: absolute;
          inset: 0;
          z-index: 38;
          display: flex;
          align-items: center;
          justify-content: center;
          background: rgba(0, 0, 0, 0.55);
          pointer-events: none;
        }
        .spin-icon {
          color: var(--primary);
          animation: spin 0.8s linear infinite;
        }
        @keyframes spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }

        /* Up Next Autoplay Overlay */
        .up-next-overlay {
          position: absolute;
          bottom: 100px;
          right: 24px;
          z-index: 50;
          background: rgba(20, 20, 20, 0.95);
          backdrop-filter: blur(10px);
          -webkit-backdrop-filter: blur(10px);
          border: 1px solid var(--border);
          border-radius: 12px;
          width: 320px;
          padding: 16px;
          box-shadow: 0 10px 30px rgba(0, 0, 0, 0.8);
          animation: slideInUpNext 0.3s cubic-bezier(0.16, 1, 0.3, 1);
        }
        @keyframes slideInUpNext {
          from { transform: translateY(20px); opacity: 0; }
          to { transform: translateY(0); opacity: 1; }
        }
        .up-next-close-btn {
          position: absolute;
          top: 10px;
          right: 10px;
          background: rgba(255, 255, 255, 0.1);
          border: none;
          color: white;
          width: 24px;
          height: 24px;
          border-radius: 50%;
          cursor: pointer;
          display: flex;
          align-items: center;
          justify-content: center;
          transition: var(--transition);
          z-index: 52;
        }
        .up-next-close-btn:hover {
          background: rgba(255, 255, 255, 0.2);
          transform: scale(1.1);
        }
        .up-next-content {
          display: flex;
          flex-direction: column;
          gap: 12px;
        }
        .up-next-label {
          font-size: 0.75rem;
          text-transform: uppercase;
          letter-spacing: 0.1em;
          color: var(--primary);
          font-weight: 700;
        }
        .up-next-row {
          display: flex;
          gap: 12px;
        }
        .up-next-poster-wrapper {
          position: relative;
          width: 100px;
          height: 56px;
          border-radius: 6px;
          overflow: hidden;
          background: #000;
          flex-shrink: 0;
          border: 1px solid var(--border);
        }
        .up-next-poster {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }
        .up-next-play-icon-btn {
          position: absolute;
          inset: 0;
          background: rgba(0, 0, 0, 0.4);
          border: none;
          color: white;
          cursor: pointer;
          opacity: 0;
          transition: var(--transition);
        }
        .up-next-poster-wrapper:hover .up-next-play-icon-btn {
          opacity: 1;
        }
        .up-next-info {
          display: flex;
          flex-direction: column;
          justify-content: center;
        }
        .up-next-title {
          font-size: 0.85rem;
          font-weight: 700;
          color: white;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
          max-width: 160px;
        }
        .up-next-desc {
          font-size: 0.75rem;
          color: var(--text-secondary);
          margin-top: 2px;
        }
        .up-next-timer {
          font-size: 0.75rem;
          color: var(--text-muted);
          margin-top: 4px;
        }
        .up-next-timer strong {
          color: var(--primary);
        }
        .up-next-btn-play {
          width: 100%;
          padding: 8px !important;
          font-size: 0.85rem !important;
          font-weight: 700 !important;
        }

        /* Gesture HUD styles */
        .gesture-hud-overlay {
          position: absolute;
          inset: 0;
          pointer-events: none;
          z-index: 60;
          display: flex;
          align-items: center;
          justify-content: center;
        }
        .gesture-hud-content {
          background-color: rgba(0, 0, 0, 0.8);
          border-radius: 8px;
          padding: 12px 24px;
          color: white;
          font-weight: 700;
          font-size: 1rem;
          border: 1px solid var(--border);
          box-shadow: 0 4px 15px rgba(0, 0, 0, 0.4);
          animation: scaleHUD 0.15s ease-out;
        }
        @keyframes scaleHUD {
          from { transform: scale(0.85); opacity: 0; }
          to { transform: scale(1); opacity: 1; }
        }

        /* Lock Screen overlays */
        .locked-state-overlay {
          position: absolute;
          inset: 0;
          z-index: 55;
          display: flex;
          align-items: center;
          justify-content: center;
          background: rgba(0, 0, 0, 0.2);
        }
        .unlock-btn {
          background: rgba(229, 9, 20, 0.95);
          color: white;
          font-weight: 700;
          font-size: 0.9rem;
          padding: 10px 20px;
          border: none;
          border-radius: 30px;
          cursor: pointer;
          gap: 8px;
          box-shadow: 0 4px 15px rgba(0,0,0,0.5);
          animation: scaleHUD 0.15s ease-out;
        }
        .unlock-btn:hover {
          background: white;
          color: var(--primary);
        }

        /* Long press 2x Speed Pill styles */
        .long-press-speedup-pill {
          position: absolute;
          top: 24px;
          left: 50%;
          transform: translateX(-50%);
          background: rgba(0, 0, 0, 0.75);
          backdrop-filter: blur(8px);
          border: 1px solid rgba(255, 255, 255, 0.15);
          color: white;
          padding: 8px 16px;
          border-radius: 20px;
          font-size: 0.85rem;
          font-weight: 700;
          letter-spacing: 0.05em;
          display: flex;
          align-items: center;
          gap: 6px;
          z-index: 60;
          pointer-events: none;
          box-shadow: 0 4px 15px rgba(0, 0, 0, 0.5);
          animation: scaleHUD 0.15s ease-out;
        }
        .speedup-icon {
          color: var(--primary);
          animation: blink 1.2s ease-in-out infinite;
        }
        @keyframes blink {
          0%, 100% { opacity: 0.3; }
          50% { opacity: 1; }
        }
        .player-container {
          user-select: none;
          -webkit-user-select: none;
          -webkit-touch-callout: none;
        }

        @media (max-width: 768px) {
          .controls-wrapper {
            padding: 12px;
          }
          .player-center-controls {
            gap: 1.8rem;
          }
          .play-pause-btn {
            width: 56px;
            height: 56px;
          }
          .play-pause-btn svg {
            width: 24px;
            height: 24px;
          }
          .volume-slider {
            display: none !important; /* Hide volume slider on mobile, swipe gesture is used */
          }
          .settings-panel {
            right: -20px;
            width: 180px;
            bottom: calc(100% + 10px);
            padding: 10px;
            max-height: min(220px, 75vh);
            overflow-y: auto;
          }
          .anime-title {
            font-size: 0.95rem;
            max-width: 150px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
          }
          .skip-time-overlay {
            bottom: 80px;
            right: 15px;
            padding: 6px 12px;
            font-size: 0.8rem;
          }
          .up-next-overlay {
            bottom: 75px;
            right: 12px;
            width: 280px;
            padding: 12px;
          }
          .up-next-poster-wrapper {
            width: 80px;
            height: 45px;
          }
          .up-next-title {
            max-width: 140px;
          }
        }
      `}</style>
    </div>
  );
}
