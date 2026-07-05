import React, { createContext, useContext, useEffect, useState, useRef } from "react";
import { 
  collection, 
  doc, 
  getDocs, 
  getDoc,
  setDoc, 
  deleteDoc, 
  writeBatch,
  onSnapshot
} from "firebase/firestore";
import { db } from "../config/firebase";
import { useAuth } from "./AuthContext";

const ProfileContext = createContext();

export function useProfile() {
  return useContext(ProfileContext);
}

// Utility to generate SHA-256 hex string (browser native)
export async function hashPin(pin) {
  if (!pin) return null;
  const encoder = new TextEncoder();
  const data = encoder.encode(pin);
  const hashBuffer = await crypto.subtle.digest("SHA-256", data);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

export function ProfileProvider({ children }) {
  const { currentUser } = useAuth();
  const [profiles, setProfiles] = useState([]);
  const [activeProfile, setActiveProfile] = useState(null);
  const [loading, setLoading] = useState(true);

  // Load profiles on auth state change
  useEffect(() => {
    if (!currentUser) {
      setProfiles([]);
      setActiveProfile(null);
      setLoading(false);
      return;
    }

    fetchProfilesAndCheckMigration();
  }, [currentUser]);

  // Load active profile from localStorage if saved
  useEffect(() => {
    if (currentUser && profiles.length > 0) {
      const savedProfileId = localStorage.getItem(`active_profile_${currentUser.uid}`);
      if (savedProfileId) {
        const found = profiles.find(p => p.id === savedProfileId);
        if (found) {
          setActiveProfile(found);
        }
      }
    }
  }, [profiles, currentUser]);

  async function fetchProfilesAndCheckMigration() {
    setLoading(true);
    try {
      const uid = currentUser.uid;
      const profilesRef = collection(db, "users", uid, "profiles");
      const snap = await getDocs(profilesRef);

      let list = [];
      if (snap.empty) {
        list = await createDefaultProfileAndMigrate(uid);
      } else {
        list = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
        setProfiles(list);
      }

      // Sync active profile from localStorage before finishing loading
      const savedProfileId = localStorage.getItem(`active_profile_${uid}`);
      if (savedProfileId) {
        const found = list.find(p => p.id === savedProfileId);
        if (found) {
          setActiveProfile(found);
        }
      }
    } catch (e) {
      console.error("Error fetching profiles:", e);
    } finally {
      setLoading(false);
    }
  }

  async function createDefaultProfileAndMigrate(uid) {
    const defaultProfileId = crypto.randomUUID();
    const defaultName = currentUser.displayName || currentUser.email.split("@")[0] || "Boss";
    const defaultAvatar = "avatar_orange"; // Default orange theme avatar identifier

    const profileRef = doc(db, "users", uid, "profiles", defaultProfileId);
    const profileData = {
      id: defaultProfileId,
      name: defaultName,
      avatarUrl: defaultAvatar,
      pin: null,
      bannerUrl: "banner_cyber_city"
    };

    await setDoc(profileRef, profileData);

    // Now migrate legacy root Collections (watchlist, history, ratings) to default profile
    try {
      // 1. Migrate watchlist
      const watchlistRef = collection(db, "users", uid, "watchlist");
      const watchlistSnap = await getDocs(watchlistRef);
      for (const d of watchlistSnap.docs) {
        await setDoc(doc(db, "users", uid, "profiles", defaultProfileId, "watchlist", d.id), d.data());
        await deleteDoc(d.ref);
      }

      // 2. Migrate history
      const historyRef = collection(db, "users", uid, "history");
      const historySnap = await getDocs(historyRef);
      for (const d of historySnap.docs) {
        await setDoc(doc(db, "users", uid, "profiles", defaultProfileId, "history", d.id), d.data());
        await deleteDoc(d.ref);
      }

      // 3. Migrate ratings
      const ratingsRef = collection(db, "users", uid, "ratings");
      const ratingsSnap = await getDocs(ratingsRef);
      for (const d of ratingsSnap.docs) {
        await setDoc(doc(db, "users", uid, "profiles", defaultProfileId, "ratings", d.id), d.data());
        await deleteDoc(d.ref);
      }
    } catch (err) {
      console.warn("Migration warning:", err);
    }

    // Refetch profiles
    const profilesRef = collection(db, "users", uid, "profiles");
    const snap = await getDocs(profilesRef);
    const list = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
    setProfiles(list);
    return list;
  }

  function applySettingsToLocalStorage(settings) {
    if (!settings) return;

    if (settings.defaultAudio) localStorage.setItem("anistream_audio_preference", settings.defaultAudio);
    if (settings.preferredQuality) localStorage.setItem("anistream_quality_cap", settings.preferredQuality);
    if (settings.autoplayNextEpisode !== undefined) localStorage.setItem("anistream_autoplay", String(settings.autoplayNextEpisode));
    if (settings.skipIntro !== undefined) localStorage.setItem("anistream_skip_intro", String(settings.skipIntro));
    if (settings.skipOutro !== undefined) localStorage.setItem("anistream_skip_outro", String(settings.skipOutro));
    if (settings.playbackSpeed !== undefined) localStorage.setItem("anistream_playback_speed", String(settings.playbackSpeed));
    if (settings.preferredProvider) localStorage.setItem("anistream_preferred_provider", settings.preferredProvider);

    if (settings.subtitles) {
      const subs = settings.subtitles;
      if (subs.enabled !== undefined) localStorage.setItem("anistream_subtitles_enabled", String(subs.enabled));
      if (subs.sizeScale !== undefined) {
        const subPx = Math.round(subs.sizeScale * 22);
        localStorage.setItem("anistream_subtitle_size", String(subPx));
      }
      if (subs.color) {
        const COLORS = ["#FFFFFF", "#FFE600", "#4ADE80", "#22D3EE", "#60A5FA", "#F472B6", "#F87171", "#1F2937"];
        const COLOR_NAMES = ["White", "Yellow", "Green", "Cyan", "Blue", "Pink", "Red", "Black"];
        const colorIdx = COLOR_NAMES.indexOf(subs.color);
        if (colorIdx !== -1) {
          localStorage.setItem("anistream_subtitle_color", COLORS[colorIdx]);
        }
      }
      if (subs.bgOpacity !== undefined) {
        const opacityPct = Math.round(subs.bgOpacity * 100);
        localStorage.setItem("anistream_subtitle_bg_opacity", String(opacityPct));
        localStorage.setItem("anistream_subtitle_bg", opacityPct === 0 ? "transparent" : opacityPct === 100 ? "opaque" : "semi-transparent");
      }
      if (subs.style) {
        let styleIdx = 1;
        if (subs.style === "classic_outline") styleIdx = 4;
        else if (subs.style === "serif") styleIdx = 2;
        else if (subs.style === "monospace") styleIdx = 3;
        else if (subs.style === "bold") styleIdx = 5;
        localStorage.setItem("anistream_subtitle_style", String(styleIdx));
      }
      if (subs.position !== undefined) {
        const posPct = Math.round(subs.position * 100);
        localStorage.setItem("anistream_subtitle_position", String(posPct));
      }
    }

    window.dispatchEvent(new Event("anistream_subtitle_settings_changed"));
  }

  async function saveSettings(customSettings = {}) {
    if (!currentUser || !activeProfile) return;
    const uid = currentUser.uid;
    const profileRef = doc(db, "users", uid, "profiles", activeProfile.id);

    const qCap = localStorage.getItem("anistream_quality_cap") || "Auto";
    const autoPlayVal = localStorage.getItem("anistream_autoplay") !== "false";
    const aPref = localStorage.getItem("anistream_audio_preference") || "sub";
    const savedSpeed = parseFloat(localStorage.getItem("anistream_playback_speed")) || 1.0;
    const prefProvider = localStorage.getItem("anistream_preferred_provider") || "zoro";
    
    const sIntro = localStorage.getItem("anistream_skip_intro") !== "false";
    const sOutro = localStorage.getItem("anistream_skip_outro") !== "false";
    
    const subEnabled = localStorage.getItem("anistream_subtitles_enabled") !== "false";
    const subSize = parseInt(localStorage.getItem("anistream_subtitle_size"), 10) || 22;
    const subColor = localStorage.getItem("anistream_subtitle_color") || "#FFFFFF";
    const subOpacity = parseInt(localStorage.getItem("anistream_subtitle_bg_opacity"), 10) || 60;
    const subStyle = parseInt(localStorage.getItem("anistream_subtitle_style"), 10) || 1;
    const subPos = parseInt(localStorage.getItem("anistream_subtitle_position"), 10) || 10;

    const COLORS = ["#FFFFFF", "#FFE600", "#4ADE80", "#22D3EE", "#60A5FA", "#F472B6", "#F87171", "#1F2937"];
    const COLOR_NAMES = ["White", "Yellow", "Green", "Cyan", "Blue", "Pink", "Red", "Black"];
    let colorIdx = COLORS.indexOf(subColor.toUpperCase());
    if (colorIdx === -1) colorIdx = 0;
    const subColorName = COLOR_NAMES[colorIdx];

    let subStyleName = "default";
    if (subStyle === 4) subStyleName = "classic_outline";
    else if (subStyle === 2) subStyleName = "serif";
    else if (subStyle === 3) subStyleName = "monospace";
    else if (subStyle === 5) subStyleName = "bold";

    const settingsData = {
      defaultAudio: aPref,
      preferredQuality: qCap,
      autoplayNextEpisode: autoPlayVal,
      skipIntro: sIntro,
      skipOutro: sOutro,
      playbackSpeed: savedSpeed,
      preferredProvider: prefProvider,
      subtitles: {
        enabled: subEnabled,
        sizeScale: subSize / 22.0,
        color: subColorName,
        bgOpacity: subOpacity / 100.0,
        style: subStyleName,
        position: subPos / 100.0
      },
      ...customSettings
    };

    await setDoc(profileRef, { settings: settingsData }, { merge: true });
    
    setActiveProfile(prev => ({
      ...prev,
      settings: settingsData
    }));
  }

  const unsubscribeRef = useRef(null);

  useEffect(() => {
    if (!currentUser || !activeProfile) {
      if (unsubscribeRef.current) {
        unsubscribeRef.current();
        unsubscribeRef.current = null;
      }
      return;
    }

    const docRef = doc(db, "users", currentUser.uid, "profiles", activeProfile.id);

    if (unsubscribeRef.current) {
      unsubscribeRef.current();
    }

    unsubscribeRef.current = onSnapshot(docRef, (snapshot) => {
      if (snapshot.exists()) {
        const data = snapshot.data();
        
        setActiveProfile(prev => {
          if (!prev || prev.id !== snapshot.id) return prev;
          
          const settingsChanged = JSON.stringify(prev.settings) !== JSON.stringify(data.settings);
          const metaChanged = prev.name !== data.name || prev.avatarUrl !== data.avatarUrl || prev.bannerUrl !== data.bannerUrl;
          
          if (settingsChanged && data.settings) {
            applySettingsToLocalStorage(data.settings);
          }
          
          if (settingsChanged || metaChanged) {
            return { ...prev, ...data };
          }
          return prev;
        });
      }
    });

    return () => {
      if (unsubscribeRef.current) {
        unsubscribeRef.current();
        unsubscribeRef.current = null;
      }
    };
  }, [currentUser, activeProfile?.id]);

  function selectProfile(profile) {
    if (profile) {
      setActiveProfile(profile);
      localStorage.setItem(`active_profile_${currentUser.uid}`, profile.id);
      if (profile.settings) {
        applySettingsToLocalStorage(profile.settings);
      }
    } else {
      setActiveProfile(null);
      localStorage.removeItem(`active_profile_${currentUser.uid}`);
    }
  }

  async function createProfile(name, avatarUrl, pinString, recoveryQuestion, recoveryAnswer, bannerUrl) {
    if (profiles.length >= 4) {
      throw new Error("Maximum of 4 profiles allowed");
    }
    const uid = currentUser.uid;
    const profileId = crypto.randomUUID();
    const hashedPin = pinString ? await hashPin(pinString) : null;

    const profileData = {
      id: profileId,
      name,
      avatarUrl,
      pin: hashedPin,
      recoveryQuestion: hashedPin ? recoveryQuestion : null,
      recoveryAnswer: hashedPin ? recoveryAnswer : null,
      bannerUrl: bannerUrl || "banner_cyber_city"
    };

    await setDoc(doc(db, "users", uid, "profiles", profileId), profileData);
    await fetchProfilesAndCheckMigration();
    return profileData;
  }

  async function updateProfile(profileId, name, avatarUrl, pinString, recoveryQuestion, recoveryAnswer, bannerUrl) {
    const uid = currentUser.uid;
    const profileRef = doc(db, "users", uid, "profiles", profileId);
    
    const updateData = { name, avatarUrl };
    if (bannerUrl !== undefined) {
      updateData.bannerUrl = bannerUrl;
    }
    if (pinString === "REMOVE") {
      updateData.pin = null;
      updateData.recoveryQuestion = null;
      updateData.recoveryAnswer = null;
    } else if (pinString) {
      const hashedPin = await hashPin(pinString);
      updateData.pin = hashedPin;
      updateData.recoveryQuestion = recoveryQuestion;
      updateData.recoveryAnswer = recoveryAnswer;
    }

    await setDoc(profileRef, updateData, { merge: true });
    await fetchProfilesAndCheckMigration();
  }

  async function deleteProfile(profileId) {
    const uid = currentUser.uid;
    await deleteDoc(doc(db, "users", uid, "profiles", profileId));

    try {
      const watchlistRef = collection(db, "users", uid, "profiles", profileId, "watchlist");
      const watchlistSnap = await getDocs(watchlistRef);
      for (const d of watchlistSnap.docs) await deleteDoc(d.ref);

      const historyRef = collection(db, "users", uid, "profiles", profileId, "history");
      const historySnap = await getDocs(historyRef);
      for (const d of historySnap.docs) await deleteDoc(d.ref);

      const ratingsRef = collection(db, "users", uid, "profiles", profileId, "ratings");
      const ratingsSnap = await getDocs(ratingsRef);
      for (const d of ratingsSnap.docs) await deleteDoc(d.ref);
    } catch (err) {
      console.warn("Clean up collections error:", err);
    }

    if (activeProfile && activeProfile.id === profileId) {
      selectProfile(null);
    }

    await fetchProfilesAndCheckMigration();
  }

  const value = {
    profiles,
    activeProfile,
    loading,
    selectProfile,
    createProfile,
    updateProfile,
    deleteProfile,
    saveSettings,
    refreshProfiles: fetchProfilesAndCheckMigration
  };

  return (
    <ProfileContext.Provider value={value}>
      {children}
    </ProfileContext.Provider>
  );
}


