import React, { useEffect, useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useProfile } from "../context/ProfileContext";
import { 
  User, RefreshCw, Sliders, ToggleLeft, ToggleRight, 
  Settings, Type, Trash2, ArrowLeft, LogOut, History, Activity, Shield
} from "lucide-react";

export default function MyAniStreamPage() {
  const { currentUser, logout, changeEmail, changePassword } = useAuth();
  const { activeProfile, selectProfile, saveSettings } = useProfile();
  const navigate = useNavigate();

  // Account Settings Credentials States
  const [emailModalOpen, setEmailModalOpen] = useState(false);
  const [passwordModalOpen, setPasswordModalOpen] = useState(false);
  
  const [currentPassword, setCurrentPassword] = useState("");
  const [newEmail, setNewEmail] = useState("");
  const [confirmEmail, setConfirmEmail] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [modalError, setModalError] = useState("");
  const [modalSuccess, setModalSuccess] = useState("");

  const isGoogleUser = currentUser?.providerData?.some(p => p.providerId === "google.com");

  const handleEmailSubmit = async () => {
    if (!currentPassword || !newEmail || !confirmEmail) {
      setModalError("All fields are required");
      return;
    }
    if (newEmail !== confirmEmail) {
      setModalError("Email confirmation does not match");
      return;
    }
    
    setIsSubmitting(true);
    setModalError("");
    setModalSuccess("");
    
    try {
      await changeEmail(currentPassword, newEmail);
      setModalSuccess("Email updated successfully!");
      setCurrentPassword("");
      setNewEmail("");
      setConfirmEmail("");
      setTimeout(() => {
        setEmailModalOpen(false);
      }, 1500);
    } catch (err) {
      setModalError(err.message || "Failed to update email. Please check your password.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handlePasswordSubmit = async () => {
    if (!currentPassword || !newPassword || !confirmPassword) {
      setModalError("All fields are required");
      return;
    }
    if (newPassword.length < 6) {
      setModalError("New password must be at least 6 characters long");
      return;
    }
    if (newPassword !== confirmPassword) {
      setModalError("Password confirmation does not match");
      return;
    }
    
    setIsSubmitting(true);
    setModalError("");
    setModalSuccess("");
    
    try {
      await changePassword(currentPassword, newPassword);
      setModalSuccess("Password updated successfully!");
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setTimeout(() => {
        setPasswordModalOpen(false);
      }, 1500);
    } catch (err) {
      setModalError(err.message || "Failed to update password. Please check your current password.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const getAvatarUrl = (key) => {
    const mapping = {
      avatar_orange: "/avatars/avatar_shonen.png",
      avatar_blue: "/avatars/avatar_cyber.png",
      avatar_green: "/avatars/avatar_ninja.png",
      avatar_pink: "/avatars/avatar_girl.png",
      avatar_purple: "/avatars/avatar_mascot.png",
      avatar_shonen: "/avatars/avatar_shonen.png",
      avatar_girl: "/avatars/avatar_girl.png",
      avatar_ninja: "/avatars/avatar_ninja.png",
      avatar_mascot: "/avatars/avatar_mascot.png",
      avatar_cyber: "/avatars/avatar_cyber.png",
      avatar_retro: "/avatars/avatar_retro.png"
    };
    return mapping[key] || "/avatars/avatar_shonen.png";
  };

  // Settings State
  const [qualityCap, setQualityCap] = useState("Auto");
  const [autoplay, setAutoplay] = useState(true);
  const [audioPreference, setAudioPreference] = useState("sub");
  const [playbackSpeed, setPlaybackSpeed] = useState(1);
  const [skipIntro, setSkipIntro] = useState(true);
  const [skipOutro, setSkipOutro] = useState(true);

  // Streaming Provider Preferences States
  const [zoroEnabled, setZoroEnabled] = useState(true);
  const [gogoanimeEnabled, setGogoanimeEnabled] = useState(true);
  const [preferredProvider, setPreferredProvider] = useState("zoro");

  // Subtitle custom states
  const COLORS = ["#FFFFFF", "#FFE600", "#4ADE80", "#22D3EE", "#60A5FA", "#F472B6", "#F87171", "#1F2937"];
  const COLOR_NAMES = ["White", "Yellow", "Green", "Cyan", "Blue", "Pink", "Red", "Black"];
  const STYLES = ["Sans-Serif", "Serif", "Monospace", "Outlined", "Bold"];

  const [isEditingSubtitles, setIsEditingSubtitles] = useState(false);
  const [previewBackdrop, setPreviewBackdrop] = useState("anime");

  const [subSize, setSubSize] = useState(22);
  const [subStyle, setSubStyle] = useState(1);
  const [subColorIndex, setSubColorIndex] = useState(0);
  const [subPosition, setSubPosition] = useState(10);
  const [subOpacity, setSubOpacity] = useState(60);

  // Diagnostics State
  const [diagnostics, setDiagnostics] = useState({
    zoro: { status: "idle", details: "" },
    fallback: { status: "idle", details: "" },
    proxy: { status: "idle", details: "" }
  });
  const [isRunningDiagnostics, setIsRunningDiagnostics] = useState(false);

  const runDiagnostics = async () => {
    setIsRunningDiagnostics(true);
    setDiagnostics({
      zoro: { status: "testing", details: "" },
      fallback: { status: "testing", details: "" },
      proxy: { status: "testing", details: "" }
    });

    try {
      const start = Date.now();
      const res = await fetch("https://hianime-api-v2.vercel.app/anime/search?q=naruto");
      if (res.ok) {
        setDiagnostics(prev => ({
          ...prev,
          zoro: { status: "success", details: `Resolved in ${Date.now() - start}ms` }
        }));
      } else {
        setDiagnostics(prev => ({
          ...prev,
          zoro: { status: "failed", details: `HTTP Status: ${res.status}` }
        }));
      }
    } catch (err) {
      setDiagnostics(prev => ({
        ...prev,
        zoro: { status: "failed", details: err.message }
      }));
    }

    try {
      const start = Date.now();
      const res = await fetch("https://api.aniskip.com/v2/skip-times/21/1?types[]=op&types[]=ed&episodeLength=1400");
      if (res.ok) {
        setDiagnostics(prev => ({
          ...prev,
          fallback: { status: "success", details: `Resolved in ${Date.now() - start}ms` }
        }));
      } else {
        setDiagnostics(prev => ({
          ...prev,
          fallback: { status: "failed", details: `HTTP Status: ${res.status}` }
        }));
      }
    } catch (err) {
      setDiagnostics(prev => ({
        ...prev,
        fallback: { status: "failed", details: err.message }
      }));
    }

    try {
      const start = Date.now();
      const res = await fetch("https://corsproxy.io/?https://google.com");
      if (res.ok) {
        setDiagnostics(prev => ({
          ...prev,
          proxy: { status: "success", details: `Resolved in ${Date.now() - start}ms` }
        }));
      } else {
        setDiagnostics(prev => ({
          ...prev,
          proxy: { status: "failed", details: `HTTP Status: ${res.status}` }
        }));
      }
    } catch (err) {
      setDiagnostics(prev => ({
        ...prev,
        proxy: { status: "failed", details: err.message }
      }));
    }

    setIsRunningDiagnostics(false);
  };

  // Load settings on mount
  useEffect(() => {
    const qCap = localStorage.getItem("anistream_quality_cap") || "Auto";
    const autoPlayVal = localStorage.getItem("anistream_autoplay") !== "false";
    const aPref = localStorage.getItem("anistream_audio_preference") || "sub";
    const savedSpeed = parseFloat(localStorage.getItem("anistream_playback_speed")) || 1;
    const sIntro = localStorage.getItem("anistream_skip_intro") !== "false";
    const sOutro = localStorage.getItem("anistream_skip_outro") !== "false";

    // Legacy conversions
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

    let colorIdx = COLORS.indexOf(initialColor.toUpperCase());
    if (colorIdx === -1) colorIdx = 0;

    const savedBg = localStorage.getItem("anistream_subtitle_bg");
    let initialOpacity = 60;
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

    const zEnabled = localStorage.getItem("anistream_zoro_enabled") !== "false";
    const gEnabled = localStorage.getItem("anistream_gogoanime_enabled") !== "false";
    const prefProvider = localStorage.getItem("anistream_preferred_provider") || "zoro";

    setQualityCap(qCap);
    setAutoplay(autoPlayVal);
    setAudioPreference(aPref);
    setPlaybackSpeed(savedSpeed);
    setSkipIntro(sIntro);
    setSkipOutro(sOutro);
    setSubSize(initialSize);
    setSubStyle(savedStyle);
    setSubColorIndex(colorIdx);
    setSubPosition(savedPos);
    setSubOpacity(initialOpacity);
    setZoroEnabled(zEnabled);
    setGogoanimeEnabled(gEnabled);
    setPreferredProvider(prefProvider);
  }, []);

  // Save settings helpers
  const updateSetting = async (key, value, setter) => {
    localStorage.setItem(key, value);
    setter(value);
    try {
      await saveSettings();
    } catch (err) {
      console.warn("Failed to sync setting to Firestore:", err);
    }
  };

  const applyRecommendedSettings = () => {
    setSubSize(22);
    setSubStyle(4);
    setSubColorIndex(0);
    setSubPosition(10);
    setSubOpacity(0);
  };

  const saveSubtitleSettings = async () => {
    localStorage.setItem("anistream_subtitle_size", String(subSize));
    localStorage.setItem("anistream_subtitle_style", String(subStyle));
    localStorage.setItem("anistream_subtitle_color", COLORS[subColorIndex]);
    localStorage.setItem("anistream_subtitle_position", String(subPosition));
    localStorage.setItem("anistream_subtitle_bg_opacity", String(subOpacity));
    localStorage.setItem("anistream_subtitle_bg", subOpacity === 0 ? "transparent" : subOpacity === 100 ? "opaque" : "semi-transparent");
    
    try {
      await saveSettings();
    } catch (err) {
      console.warn("Failed to sync subtitle settings to Firestore:", err);
    }

    window.dispatchEvent(new Event("anistream_subtitle_settings_changed"));
    setIsEditingSubtitles(false);
  };

  const handleLogout = async () => {
    try {
      await logout();
      navigate("/auth");
    } catch (e) {
      console.error("Sign out failed:", e);
    }
  };

  const handleAppReset = () => {
    if (window.confirm("CAUTION: This will clear all local app preferences, cache, quality overrides, and log you out. Are you sure?")) {
      localStorage.clear();
      handleLogout();
    }
  };

  if (!currentUser || !activeProfile) {
    return (
      <div className="my-anistream-page container flex-center" style={{ minHeight: "80vh" }}>
        <p className="text-muted">Loading profile settings...</p>
      </div>
    );
  }

  const getPreviewFontStyles = () => {
    switch (subStyle) {
      case 2: // Serif
        return { fontFamily: "'Georgia', 'Times New Roman', serif", fontWeight: "normal", textShadow: "0 2px 4px rgba(0, 0, 0, 0.8)" };
      case 3: // Monospace
        return { fontFamily: "'Courier New', Courier, monospace", fontWeight: "normal", textShadow: "0 2px 4px rgba(0, 0, 0, 0.8)" };
      case 4: // Outlined
        return { fontFamily: "var(--font-family)", fontWeight: "bold", textShadow: "-1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000, 1px 1px 0 #000, 0 2px 4px rgba(0, 0, 0, 0.8)" };
      case 5: // Bold
        return { fontFamily: "var(--font-family)", fontWeight: "bold", textShadow: "0 2px 4px rgba(0, 0, 0, 0.8)" };
      case 1: // Sans-Serif
      default:
        return { fontFamily: "var(--font-family)", fontWeight: "normal", textShadow: "0 2px 4px rgba(0, 0, 0, 0.8)" };
    }
  };

  const activeColorHex = COLORS[subColorIndex];

  const previewSubStyles = {
    fontSize: `${subSize}px`,
    color: activeColorHex,
    backgroundColor: `rgba(0, 0, 0, ${subOpacity / 100})`,
    padding: "6px 12px",
    borderRadius: "6px",
    textAlign: "center",
    display: "inline-block",
    maxWidth: "90%",
    wordBreak: "break-word",
    lineHeight: "1.4",
    bottom: `${subPosition}%`,
    position: "absolute",
    left: "50%",
    transform: "translateX(-50%)",
    ...getPreviewFontStyles()
  };

  return (
    <div className="my-anistream-page fade-in container">
      {isEditingSubtitles ? (
        <div className="subtitle-edit-mode">
          <div className="subtitle-settings-header">
            <button onClick={() => setIsEditingSubtitles(false)} className="subtitle-back-btn" aria-label="Go Back">
              <ArrowLeft size={24} />
            </button>
            <h2>Subtitle settings</h2>
          </div>

          {/* Live Preview Box */}
          <div className="subtitle-preview-wrapper">
            <div className="preview-options-bar">
              <span>Preview Backdrop:</span>
              <div className="preview-backdrop-buttons">
                <button 
                  className={previewBackdrop === "anime" ? "active" : ""} 
                  onClick={() => setPreviewBackdrop("anime")}
                >
                  Anime Frame
                </button>
                <button 
                  className={previewBackdrop === "dark" ? "active" : ""} 
                  onClick={() => setPreviewBackdrop("dark")}
                >
                  Dark
                </button>
                <button 
                  className={previewBackdrop === "light" ? "active" : ""} 
                  onClick={() => setPreviewBackdrop("light")}
                >
                  Light (Mock)
                </button>
              </div>
            </div>
            <div 
              className={`subtitle-preview-box backdrop-${previewBackdrop}`}
            >
              <div className="subtitle-preview-text" style={previewSubStyles}>
                Lorem Ipsum is simply dummy text.
              </div>
            </div>
          </div>

          {/* Sliders Area */}
          <div className="subtitle-sliders-container">
            {/* Font Size */}
            <div className="slider-row">
              <div className="slider-label">
                <span>Font size</span>
                <span className="slider-value-badge">{subSize}px</span>
              </div>
              <div className="slider-wrapper">
                <input 
                  type="range" 
                  min="14" 
                  max="36" 
                  value={subSize} 
                  onChange={(e) => setSubSize(parseInt(e.target.value, 10))}
                  className="custom-range-slider"
                />
              </div>
            </div>

            {/* Font Style */}
            <div className="slider-row">
              <div className="slider-label">
                <span>Font style</span>
                <span className="slider-value-badge">{STYLES[subStyle - 1]}</span>
              </div>
              <div className="slider-wrapper with-ticks">
                <input 
                  type="range" 
                  min="1" 
                  max="5" 
                  step="1"
                  value={subStyle} 
                  onChange={(e) => setSubStyle(parseInt(e.target.value, 10))}
                  className="custom-range-slider"
                />
                <div className="slider-ticks">
                  {[1, 2, 3, 4, 5].map((val) => (
                    <span 
                      key={val} 
                      className={`slider-tick-dot ${subStyle === val ? "active" : ""}`} 
                      style={{ left: `${((val - 1) / 4) * 100}%` }}
                    />
                  ))}
                </div>
              </div>
            </div>

            {/* Font Color */}
            <div className="slider-row">
              <div className="slider-label">
                <span>Font color</span>
                <span className="slider-value-badge" style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                  <span style={{ width: "12px", height: "12px", borderRadius: "50%", background: activeColorHex, border: "1px solid rgba(255,255,255,0.2)" }} />
                  {COLOR_NAMES[subColorIndex]}
                </span>
              </div>
              <div className="slider-wrapper with-ticks">
                <input 
                  type="range" 
                  min="0" 
                  max="7" 
                  step="1"
                  value={subColorIndex} 
                  onChange={(e) => setSubColorIndex(parseInt(e.target.value, 10))}
                  className="custom-range-slider"
                />
                <div className="slider-ticks">
                  {COLORS.map((col, idx) => (
                    <span 
                      key={idx} 
                      className={`slider-tick-dot ${subColorIndex === idx ? "active" : ""}`} 
                      style={{ left: `${(idx / 7) * 100}%` }}
                    />
                  ))}
                </div>
              </div>
            </div>

            {/* Position */}
            <div className="slider-row">
              <div className="slider-label">
                <span>Position</span>
                <span className="slider-value-badge">{subPosition}% from bottom</span>
              </div>
              <div className="slider-wrapper">
                <input 
                  type="range" 
                  min="5" 
                  max="50" 
                  value={subPosition} 
                  onChange={(e) => setSubPosition(parseInt(e.target.value, 10))}
                  className="custom-range-slider"
                />
              </div>
            </div>

            {/* Opacity */}
            <div className="slider-row">
              <div className="slider-label">
                <span>Opacity</span>
                <span className="slider-value-badge">{subOpacity}%</span>
              </div>
              <div className="slider-wrapper">
                <input 
                  type="range" 
                  min="0" 
                  max="100" 
                  value={subOpacity} 
                  onChange={(e) => setSubOpacity(parseInt(e.target.value, 10))}
                  className="custom-range-slider"
                />
              </div>
            </div>

            {/* Actions Row */}
            <div className="subtitle-actions-row">
              <button 
                onClick={applyRecommendedSettings} 
                className="subtitle-recommended-btn"
                type="button"
              >
                Recommended
              </button>
              <button onClick={saveSubtitleSettings} className="subtitle-save-btn">
                Save
              </button>
            </div>
          </div>
        </div>
      ) : (
        <>
          <div className="my-anistream-header">
            <h1 className="page-title"><User size={24} color="var(--primary)" /> My AniStream</h1>
            <p className="text-muted">Manage your profile, preferences, and system settings</p>
          </div>

          <div className="my-anistream-content-grid">
            {/* Profile Details Card */}
            <section className="settings-card profile-card-hero">
              <div className="profile-hero-info">
                <div className="profile-hero-avatar">
                  <img src={getAvatarUrl(activeProfile.avatarUrl)} alt="" className="profile-hero-avatar-img" />
                </div>
                <div>
                  <h3>{activeProfile.name}</h3>
                  <p className="text-muted">Active Profile</p>
                </div>
              </div>
              <div className="profile-hero-actions" style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                <button 
                  onClick={() => {
                    selectProfile(null);
                    navigate("/profiles");
                  }}
                  className="btn btn-secondary flex-center"
                  style={{ gap: "6px", width: "100%" }}
                >
                  <RefreshCw size={16} /> Switch Profile
                </button>
                <Link 
                  to="/history"
                  className="btn btn-secondary flex-center"
                  style={{ gap: "6px", width: "100%", textDecoration: "none" }}
                >
                  <History size={16} className="text-primary" /> Watch History
                </Link>
              </div>
            </section>

            {/* Account Security Card */}
            <section className="settings-card">
              <div className="card-header-with-icon">
                <Shield size={20} className="text-primary" />
                <h2>Account Credentials & Security</h2>
              </div>
              <div className="settings-options-list">
                <div className="setting-item" style={{ borderBottom: "1px solid rgba(255,255,255,0.05)", paddingBottom: "15px" }}>
                  <div className="setting-info">
                    <h4>Signed In As</h4>
                    <p className="text-white" style={{ fontWeight: 600, fontSize: "0.95rem", marginTop: "4px" }}>
                      {currentUser?.email || "No email registered"}
                    </p>
                  </div>
                  <div className="setting-action">
                    {isGoogleUser ? (
                      <span 
                        className="badge" 
                        style={{ 
                          backgroundColor: "rgba(66, 133, 244, 0.15)", 
                          color: "#4285f4", 
                          border: "1px solid #4285f4",
                          padding: "4px 8px",
                          borderRadius: "6px",
                          fontSize: "0.75rem",
                          fontWeight: "bold"
                        }}
                      >
                        Managed by Google
                      </span>
                    ) : (
                      <span 
                        className="badge" 
                        style={{ 
                          backgroundColor: "rgba(255, 165, 0, 0.15)", 
                          color: "var(--primary)", 
                          border: "1px solid var(--primary)",
                          padding: "4px 8px",
                          borderRadius: "6px",
                          fontSize: "0.75rem",
                          fontWeight: "bold"
                        }}
                      >
                        Email & Password
                      </span>
                    )}
                  </div>
                </div>

                {!isGoogleUser && (
                  <>
                    <div className="setting-item" style={{ borderBottom: "1px solid rgba(255,255,255,0.05)", paddingBottom: "15px", paddingTop: "15px" }}>
                      <div className="setting-info">
                        <h4>Change Account Email</h4>
                        <p className="text-muted">Requires password reauthentication for security</p>
                      </div>
                      <div className="setting-action">
                        <button 
                          onClick={() => {
                            setEmailModalOpen(true);
                            setModalError("");
                            setModalSuccess("");
                            setCurrentPassword("");
                            setNewEmail("");
                            setConfirmEmail("");
                          }} 
                          className="btn btn-secondary btn-sm"
                        >
                          Update Email
                        </button>
                      </div>
                    </div>

                    <div className="setting-item" style={{ paddingTop: "15px" }}>
                      <div className="setting-info">
                        <h4>Change Account Password</h4>
                        <p className="text-muted">Requires verification of your old password</p>
                      </div>
                      <div className="setting-action">
                        <button 
                          onClick={() => {
                            setPasswordModalOpen(true);
                            setModalError("");
                            setModalSuccess("");
                            setCurrentPassword("");
                            setNewPassword("");
                            setConfirmPassword("");
                          }} 
                          className="btn btn-secondary btn-sm"
                        >
                          Update Password
                        </button>
                      </div>
                    </div>
                  </>
                )}
              </div>
            </section>

            {/* Playback Settings Card */}
            <section className="settings-card">
              <div className="card-header-with-icon">
                <Sliders size={20} className="text-primary" />
                <h2>Playback & Quality</h2>
              </div>
              <div className="settings-options-list">
                <div className="setting-item">
                  <div className="setting-info">
                    <h4>Streaming Quality Limit</h4>
                    <p className="text-muted">Caps Hls.js resolution to save cellular data</p>
                  </div>
                  <div className="setting-action">
                    <select 
                      value={qualityCap}
                      onChange={(e) => updateSetting("anistream_quality_cap", e.target.value, setQualityCap)}
                      className="settings-select"
                    >
                      <option value="Auto">Auto (Unlimited)</option>
                      <option value="1080p">1080p Full HD</option>
                      <option value="720p">720p HD</option>
                      <option value="360p">360p Data Saver</option>
                    </select>
                  </div>
                </div>

                <div className="setting-item">
                  <div className="setting-info">
                    <h4>Autoplay Next Episode</h4>
                    <p className="text-muted">Automatically load next episode when current finishes</p>
                  </div>
                  <div className="setting-action">
                    <button 
                      onClick={() => {
                        const nextVal = !autoplay;
                        updateSetting("anistream_autoplay", String(nextVal), setAutoplay);
                      }}
                      className="toggle-switch-btn"
                    >
                      {autoplay ? (
                        <ToggleRight size={38} className="text-primary" />
                      ) : (
                        <ToggleLeft size={38} className="text-muted" />
                      )}
                    </button>
                  </div>
                </div>
                <div className="setting-item">
                  <div className="setting-info">
                    <h4>Preferred Audio Language</h4>
                    <p className="text-muted">Default to Subtitled or Dubbed audio tracks globally</p>
                  </div>
                  <div className="setting-action">
                    <select 
                      value={audioPreference}
                      onChange={(e) => updateSetting("anistream_audio_preference", e.target.value, setAudioPreference)}
                      className="settings-select"
                    >
                      <option value="sub">Subtitled (Japanese Audio)</option>
                      <option value="dub">Dubbed (Local/English Audio)</option>
                    </select>
                  </div>
                </div>

                <div className="setting-item">
                  <div className="setting-info">
                    <h4>Default Playback Speed</h4>
                    <p className="text-muted">Set the default playback speed for all video players</p>
                  </div>
                  <div className="setting-action">
                    <select 
                      value={playbackSpeed}
                      onChange={(e) => updateSetting("anistream_playback_speed", e.target.value, (val) => setPlaybackSpeed(parseFloat(val)))}
                      className="settings-select"
                    >
                      <option value="0.5">0.5x</option>
                      <option value="0.75">0.75x</option>
                      <option value="1">1.0x (Normal)</option>
                      <option value="1.25">1.25x</option>
                      <option value="1.5">1.5x</option>
                      <option value="2">2.0x</option>
                    </select>
                  </div>
                </div>

                <div className="setting-item">
                  <div className="setting-info">
                    <h4>Auto-Skip Intro</h4>
                    <p className="text-muted">Automatically skip anime opening themes</p>
                  </div>
                  <div className="setting-action">
                    <button 
                      onClick={() => {
                        const nextVal = !skipIntro;
                        updateSetting("anistream_skip_intro", String(nextVal), setSkipIntro);
                      }}
                      className="toggle-switch-btn"
                    >
                      {skipIntro ? (
                        <ToggleRight size={38} className="text-primary" />
                      ) : (
                        <ToggleLeft size={38} className="text-muted" />
                      )}
                    </button>
                  </div>
                </div>

                <div className="setting-item">
                  <div className="setting-info">
                    <h4>Auto-Skip Outro</h4>
                    <p className="text-muted">Automatically skip anime ending credits</p>
                  </div>
                  <div className="setting-action">
                    <button 
                      onClick={() => {
                        const nextVal = !skipOutro;
                        updateSetting("anistream_skip_outro", String(nextVal), setSkipOutro);
                      }}
                      className="toggle-switch-btn"
                    >
                      {skipOutro ? (
                        <ToggleRight size={38} className="text-primary" />
                      ) : (
                        <ToggleLeft size={38} className="text-muted" />
                      )}
                    </button>
                  </div>
                </div>
              </div>
            </section>

            {/* Streaming Providers & Priorities Settings Card */}
            <section className="settings-card">
              <div className="card-header-with-icon">
                <Sliders size={20} className="text-primary" />
                <h2>Streaming Providers & Priorities</h2>
              </div>
              <div className="settings-options-list">
                <div className="setting-item">
                  <div className="setting-info">
                    <h4>Enable Zoro Stream Server</h4>
                    <p className="text-muted">Attempts to stream direct video files from Zoro (Megaplay)</p>
                  </div>
                  <div className="setting-action">
                    <button 
                      onClick={() => {
                        const nextVal = !zoroEnabled;
                        updateSetting("anistream_zoro_enabled", String(nextVal), setZoroEnabled);
                      }}
                      className="toggle-switch-btn"
                    >
                      {zoroEnabled ? (
                        <ToggleRight size={38} className="text-primary" />
                      ) : (
                        <ToggleLeft size={38} className="text-muted" />
                      )}
                    </button>
                  </div>
                </div>

                <div className="setting-item">
                  <div className="setting-info">
                    <h4>Enable Gogoanime Stream Server</h4>
                    <p className="text-muted">Attempts to stream direct video files from Gogoanime (AniNeko)</p>
                  </div>
                  <div className="setting-action">
                    <button 
                      onClick={() => {
                        const nextVal = !gogoanimeEnabled;
                        updateSetting("anistream_gogoanime_enabled", String(nextVal), setGogoanimeEnabled);
                      }}
                      className="toggle-switch-btn"
                    >
                      {gogoanimeEnabled ? (
                        <ToggleRight size={38} className="text-primary" />
                      ) : (
                        <ToggleLeft size={38} className="text-muted" />
                      )}
                    </button>
                  </div>
                </div>

                <div className="setting-item">
                  <div className="setting-info">
                    <h4>Preferred Streaming Server</h4>
                    <p className="text-muted">Primary direct video source loaded by default</p>
                  </div>
                  <div className="setting-action">
                    <select 
                      value={preferredProvider}
                      onChange={(e) => updateSetting("anistream_preferred_provider", e.target.value, setPreferredProvider)}
                      className="settings-select"
                      disabled={!zoroEnabled || !gogoanimeEnabled}
                      style={{ opacity: (!zoroEnabled || !gogoanimeEnabled) ? 0.5 : 1 }}
                    >
                      <option value="zoro">Zoro (HiAnime) First</option>
                      <option value="gogoanime">Gogoanime (AniNeko) First</option>
                    </select>
                  </div>
                </div>
              </div>
            </section>

            {/* Subtitle Style Preferences Card */}
            <section className="settings-card" style={{ cursor: "pointer" }} onClick={() => setIsEditingSubtitles(true)}>
              <div className="card-header-with-icon" style={{ borderBottom: "none", marginBottom: 0, paddingBottom: 0 }}>
                <Type size={20} className="text-primary" />
                <div style={{ flexGrow: 1, paddingRight: "10px" }}>
                  <h2 style={{ marginBottom: "2px" }}>Subtitle Styling & Layout</h2>
                  <p className="text-muted" style={{ fontSize: "0.8rem", marginTop: "2px" }}>
                    Customize size, font style, color, position, and background opacity
                  </p>
                </div>
                <span className="text-primary" style={{ fontSize: "0.85rem", fontWeight: "600", display: "inline-flex", alignItems: "center", gap: "6px" }}>
                  Configure <Sliders size={16} />
                </span>
              </div>
            </section>

            {/* Server Status & Diagnostics Card */}
            <section className="settings-card">
              <div className="card-header-with-icon">
                <Activity size={20} className="text-primary" />
                <h2>Server Status & Diagnostics</h2>
              </div>
              <div className="settings-options-list">
                <p className="text-muted" style={{ fontSize: "0.85rem", marginBottom: "10px" }}>
                  Run a live connection test to verify that the AniStream streaming backends are online and functioning.
                </p>

                <div className="diagnostics-list" style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
                  {/* Zoro */}
                  <div className="diagnostic-item" style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", background: "rgba(255,255,255,0.02)", borderRadius: "8px", border: "1px solid var(--border)" }}>
                    <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                      <span style={{ fontSize: "0.9rem", fontWeight: "600", color: "white" }}>Zoro Primary API</span>
                      <span className="text-muted" style={{ fontSize: "0.75rem" }}>aniplex-proxy.f1886391.workers.dev</span>
                    </div>
                    <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                      <span style={{ fontSize: "0.8rem", color: diagnostics.zoro.status === "success" ? "#4ADE80" : diagnostics.zoro.status === "error" ? "#F87171" : "var(--text-muted)", fontWeight: "600" }}>
                        {diagnostics.zoro.status === "testing" ? "Testing..." : diagnostics.zoro.status === "success" ? diagnostics.zoro.details : diagnostics.zoro.status === "error" ? diagnostics.zoro.details : "Not Tested"}
                      </span>
                      <span className={`status-dot ${diagnostics.zoro.status}`} />
                    </div>
                  </div>

                  {/* Fallback API */}
                  <div className="diagnostic-item" style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", background: "rgba(255,255,255,0.02)", borderRadius: "8px", border: "1px solid var(--border)" }}>
                    <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                      <span style={{ fontSize: "0.9rem", fontWeight: "600", color: "white" }}>Fallback Scraper API</span>
                      <span className="text-muted" style={{ fontSize: "0.75rem" }}>/.netlify/functions/fallback-stream</span>
                    </div>
                    <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                      <span style={{ fontSize: "0.8rem", color: diagnostics.fallback.status === "success" ? "#4ADE80" : diagnostics.fallback.status === "error" ? "#F87171" : "var(--text-muted)", fontWeight: "600" }}>
                        {diagnostics.fallback.status === "testing" ? "Testing..." : diagnostics.fallback.status === "success" ? diagnostics.fallback.details : diagnostics.fallback.status === "error" ? diagnostics.fallback.details : "Not Tested"}
                      </span>
                      <span className={`status-dot ${diagnostics.fallback.status}`} />
                    </div>
                  </div>

                  {/* Stream Proxy */}
                  <div className="diagnostic-item" style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", background: "rgba(255,255,255,0.02)", borderRadius: "8px", border: "1px solid var(--border)" }}>
                    <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                      <span style={{ fontSize: "0.9rem", fontWeight: "600", color: "white" }}>HLS Stream Proxy</span>
                      <span className="text-muted" style={{ fontSize: "0.75rem" }}>Cloudflare Workers Traffic Handler</span>
                    </div>
                    <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                      <span style={{ fontSize: "0.8rem", color: diagnostics.proxy.status === "success" ? "#4ADE80" : diagnostics.proxy.status === "error" ? "#F87171" : "var(--text-muted)", fontWeight: "600" }}>
                        {diagnostics.proxy.status === "testing" ? "Testing..." : diagnostics.proxy.status === "success" ? diagnostics.proxy.details : diagnostics.proxy.status === "error" ? diagnostics.proxy.details : "Not Tested"}
                      </span>
                      <span className={`status-dot ${diagnostics.proxy.status}`} />
                    </div>
                  </div>
                </div>

                <div style={{ marginTop: "1rem", display: "flex", justifyContent: "flex-end" }}>
                  <button 
                    onClick={runDiagnostics} 
                    disabled={isRunningDiagnostics} 
                    className="btn btn-primary flex-center"
                    style={{ gap: "8px" }}
                  >
                    {isRunningDiagnostics ? (
                      <>
                        <RefreshCw size={16} className="spin-icon" style={{ animation: "spin 0.8s linear infinite" }} /> Testing Servers...
                      </>
                    ) : (
                      "Run Diagnostics Test"
                    )}
                  </button>
                </div>
              </div>
            </section>

            {/* System Reset & Logout Card */}
            <section className="settings-card dangerous-zone">
              <div className="card-header-with-icon">
                <Settings size={20} className="text-primary" />
                <h2>System & Security</h2>
              </div>
              <div className="settings-options-list">
                <div className="setting-item">
                  <div className="setting-info">
                    <h4>Sign Out</h4>
                    <p className="text-muted">Log out of active session</p>
                  </div>
                  <div className="setting-action">
                    <button onClick={handleLogout} className="btn btn-secondary flex-center" style={{ gap: "6px" }}>
                      <LogOut size={16} /> Sign Out
                    </button>
                  </div>
                </div>

                <div className="setting-item">
                  <div className="setting-info">
                    <h4>App Diagnostics & Reset</h4>
                    <p className="text-muted">Wipes all cached data and resets preferences</p>
                  </div>
                  <div className="setting-action">
                    <button onClick={handleAppReset} className="btn reset-all-app-btn flex-center" style={{ gap: "6px" }}>
                      <Trash2 size={16} /> Reset App
                    </button>
                  </div>
                </div>
              </div>
            </section>
          </div>

          {/* Email Modal Dialog */}
          {emailModalOpen && (
            <div className="settings-modal-overlay">
              <div className="settings-modal-card">
                <h3>Change Account Email</h3>
                <p className="text-muted" style={{ fontSize: "0.85rem", marginBottom: "15px" }}>
                  For security, confirm your old password and enter your new email address.
                </p>
                {modalError && <div className="modal-alert error">{modalError}</div>}
                {modalSuccess && <div className="modal-alert success">{modalSuccess}</div>}
                
                <div className="modal-form-group">
                  <label>Current Password</label>
                  <input 
                    type="password" 
                    value={currentPassword} 
                    onChange={(e) => setCurrentPassword(e.target.value)} 
                    placeholder="Enter current password"
                    className="modal-input"
                  />
                </div>
                
                <div className="modal-form-group">
                  <label>New Email Address</label>
                  <input 
                    type="email" 
                    value={newEmail} 
                    onChange={(e) => setNewEmail(e.target.value)} 
                    placeholder="Enter new email address"
                    className="modal-input"
                  />
                </div>
                
                <div className="modal-form-group">
                  <label>Confirm New Email</label>
                  <input 
                    type="email" 
                    value={confirmEmail} 
                    onChange={(e) => setConfirmEmail(e.target.value)} 
                    placeholder="Confirm new email address"
                    className="modal-input"
                  />
                </div>
                
                <div className="modal-actions">
                  <button 
                    onClick={() => setEmailModalOpen(false)} 
                    className="btn btn-secondary"
                    disabled={isSubmitting}
                  >
                    Cancel
                  </button>
                  <button 
                    onClick={handleEmailSubmit} 
                    className="btn btn-primary"
                    disabled={isSubmitting}
                  >
                    {isSubmitting ? "Updating..." : "Update Email"}
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* Password Modal Dialog */}
          {passwordModalOpen && (
            <div className="settings-modal-overlay">
              <div className="settings-modal-card">
                <h3>Change Account Password</h3>
                <p className="text-muted" style={{ fontSize: "0.85rem", marginBottom: "15px" }}>
                  For security, enter your current password and your new password.
                </p>
                {modalError && <div className="modal-alert error">{modalError}</div>}
                {modalSuccess && <div className="modal-alert success">{modalSuccess}</div>}
                
                <div className="modal-form-group">
                  <label>Current Password</label>
                  <input 
                    type="password" 
                    value={currentPassword} 
                    onChange={(e) => setCurrentPassword(e.target.value)} 
                    placeholder="Enter current password"
                    className="modal-input"
                  />
                </div>
                
                <div className="modal-form-group">
                  <label>New Password</label>
                  <input 
                    type="password" 
                    value={newPassword} 
                    onChange={(e) => setNewPassword(e.target.value)} 
                    placeholder="Enter new password (min. 6 chars)"
                    className="modal-input"
                  />
                </div>
                
                <div className="modal-form-group">
                  <label>Confirm New Password</label>
                  <input 
                    type="password" 
                    value={confirmPassword} 
                    onChange={(e) => setConfirmPassword(e.target.value)} 
                    placeholder="Confirm new password"
                    className="modal-input"
                  />
                </div>
                
                <div className="modal-actions">
                  <button 
                    onClick={() => setPasswordModalOpen(false)} 
                    className="btn btn-secondary"
                    disabled={isSubmitting}
                  >
                    Cancel
                  </button>
                  <button 
                    onClick={handlePasswordSubmit} 
                    className="btn btn-primary"
                    disabled={isSubmitting}
                  >
                    {isSubmitting ? "Updating..." : "Update Password"}
                  </button>
                </div>
              </div>
            </div>
          )}
        </>
      )}

      <style>{`
        .status-dot {
          width: 8px;
          height: 8px;
          border-radius: 50%;
          display: inline-block;
          background-color: var(--text-muted);
        }
        .status-dot.testing {
          background-color: #3b82f6;
          animation: pulseDiag 1s infinite alternate;
        }
        .status-dot.success {
          background-color: #4ade80;
          box-shadow: 0 0 8px rgba(74, 222, 128, 0.4);
        }
        .status-dot.error {
          background-color: #f87171;
          box-shadow: 0 0 8px rgba(248, 113, 113, 0.4);
        }
        @keyframes pulseDiag {
          from { opacity: 0.4; transform: scale(0.9); }
          to { opacity: 1; transform: scale(1.1); }
        }

        .my-anistream-page {
          padding-top: calc(var(--header-height) + 20px);
          padding-bottom: 5rem;
          min-height: 100vh;
        }
        .my-anistream-header {
          margin-bottom: 2rem;
          border-bottom: 1px solid var(--border);
          padding-bottom: 12px;
        }
        .page-title {
          font-size: 1.5rem;
          font-weight: 700;
          color: white;
          display: flex;
          align-items: center;
          gap: 10px;
        }
        .my-anistream-header p {
          font-size: 0.9rem;
          margin-top: 4px;
        }

        .my-anistream-content-grid {
          display: flex;
          flex-direction: column;
          gap: 1.5rem;
          max-width: 700px;
          margin: 0 auto;
        }

        .settings-card {
          background-color: var(--bg-card);
          border: 1px solid var(--border);
          border-radius: 12px;
          padding: 24px;
        }

        .profile-card-hero {
          display: flex;
          align-items: center;
          justify-content: space-between;
          background: linear-gradient(135deg, #181818 0%, #0d0d0d 100%);
          border-color: #2a2a2a;
        }
        .profile-hero-info {
          display: flex;
          align-items: center;
          gap: 1.2rem;
        }
        .profile-hero-avatar {
          width: 60px;
          height: 60px;
          border-radius: 10px;
          display: flex;
          align-items: center;
          justify-content: center;
          font-weight: 800;
          font-size: 1.8rem;
          color: white;
          overflow: hidden;
        }
        .profile-hero-avatar-img {
          width: 100%;
          height: 100%;
          object-fit: cover;
          border-radius: inherit;
        }
        
        .card-header-with-icon {
          display: flex;
          align-items: center;
          gap: 10px;
          margin-bottom: 1.2rem;
          border-bottom: 1px solid rgba(255, 255, 255, 0.05);
          padding-bottom: 10px;
        }
        .card-header-with-icon h2 {
          font-size: 1.15rem;
          font-weight: 700;
          color: white;
        }

        .settings-options-list {
          display: flex;
          flex-direction: column;
          gap: 1.2rem;
        }
        .setting-item {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 1.5rem;
        }
        .setting-info h4 {
          font-size: 0.95rem;
          font-weight: 600;
          color: white;
          margin-bottom: 2px;
        }
        .setting-info p {
          font-size: 0.8rem;
          line-height: 1.2;
        }

        .settings-select {
          background-color: var(--bg-input);
          border: 1px solid var(--border);
          color: white;
          font-family: var(--font-family);
          padding: 8px 12px;
          border-radius: 6px;
          outline: none;
          font-size: 0.85rem;
          min-width: 150px;
          cursor: pointer;
        }
        .settings-select:focus {
          border-color: var(--primary);
        }

        .toggle-switch-btn {
          background: none;
          border: none;
          cursor: pointer;
          padding: 0;
          display: flex;
          align-items: center;
        }

        .reset-all-app-btn {
          background-color: rgba(229, 9, 20, 0.1);
          border: 1px solid rgba(229, 9, 20, 0.2);
          color: #ff4d4d;
          padding: 8px 16px;
          border-radius: 6px;
          cursor: pointer;
          font-weight: 600;
          font-size: 0.85rem;
          transition: var(--transition);
        }
        .reset-all-app-btn:hover {
          background-color: var(--primary);
          color: white;
          border-color: var(--primary);
        }

        /* Upgraded Subtitle Settings Styling */
        .subtitle-edit-mode {
          padding-top: calc(var(--header-height) + 15px);
          max-width: 600px;
          margin: 0 auto;
          padding-bottom: 5rem;
        }
        .subtitle-settings-header {
          display: flex;
          align-items: center;
          margin-bottom: 1.5rem;
          border-bottom: 1px solid var(--border);
          padding-bottom: 12px;
        }
        .subtitle-settings-header h2 {
          font-size: 1.3rem;
          font-weight: 700;
          color: white;
        }
        .subtitle-back-btn {
          background: none;
          border: none;
          color: white;
          cursor: pointer;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 8px;
          margin-right: 8px;
          border-radius: 50%;
          transition: var(--transition);
        }
        .subtitle-back-btn:hover {
          background: #1f1f1f;
        }
        .subtitle-preview-wrapper {
          margin-bottom: 2rem;
          background: #141414;
          border: 1px solid var(--border);
          border-radius: 12px;
          padding: 16px;
        }
        .preview-options-bar {
          display: flex;
          justify-content: space-between;
          align-items: center;
          font-size: 0.85rem;
          color: var(--text-secondary);
          margin-bottom: 12px;
        }
        .preview-backdrop-buttons {
          display: flex;
          gap: 6px;
        }
        .preview-backdrop-buttons button {
          background: #1f1f1f;
          border: 1px solid var(--border);
          color: var(--text-secondary);
          padding: 4px 10px;
          border-radius: 4px;
          font-size: 0.75rem;
          cursor: pointer;
          transition: all 0.2s;
        }
        .preview-backdrop-buttons button.active {
          background: var(--primary);
          color: white;
          border-color: var(--primary);
        }
        .subtitle-preview-box {
          height: 180px;
          border-radius: 8px;
          position: relative;
          overflow: hidden;
          display: flex;
          align-items: center;
          justify-content: center;
          border: 1px solid rgba(255, 255, 255, 0.03);
          transition: all 0.3s;
        }
        .subtitle-preview-box.backdrop-white {
          background-color: #FFFFFF;
        }
        .subtitle-preview-box.backdrop-dark {
          background-color: #0d0d0d;
        }
        .subtitle-preview-box.backdrop-anime {
          background: linear-gradient(to bottom, rgba(10, 10, 20, 0.3), rgba(10, 10, 20, 0.7)), url('https://images.unsplash.com/photo-1578632767115-351597cf2477?q=80&w=600&auto=format&fit=crop') center/cover no-repeat;
        }
        .subtitle-sliders-container {
          display: flex;
          flex-direction: column;
          gap: 1.5rem;
          background: #141414;
          border: 1px solid var(--border);
          border-radius: 12px;
          padding: 24px;
        }
        .slider-row {
          display: flex;
          flex-direction: column;
          gap: 8px;
        }
        .slider-label {
          display: flex;
          justify-content: space-between;
          font-size: 0.95rem;
          font-weight: 600;
          color: white;
        }
        .slider-value-badge {
          font-size: 0.8rem;
          color: var(--text-secondary);
          background: #1f1f1f;
          padding: 2px 8px;
          border-radius: 12px;
          border: 1px solid var(--border);
        }
        .slider-wrapper {
          position: relative;
          width: 100%;
          display: flex;
          align-items: center;
          height: 24px;
        }
        .slider-wrapper.with-ticks {
          margin-bottom: 4px;
        }
        .custom-range-slider {
          -webkit-appearance: none;
          appearance: none;
          width: 100%;
          height: 6px;
          border-radius: 3px;
          background: #1f1f1f;
          outline: none;
          border: 1px solid var(--border);
          z-index: 2;
          position: relative;
          cursor: pointer;
        }
        .custom-range-slider::-webkit-slider-thumb {
          -webkit-appearance: none;
          appearance: none;
          width: 18px;
          height: 18px;
          border-radius: 50%;
          background: var(--primary);
          cursor: pointer;
          border: 2px solid white;
          box-shadow: 0 1px 3px rgba(0,0,0,0.5);
          transition: transform 0.1s;
        }
        .custom-range-slider::-webkit-slider-thumb:hover {
          transform: scale(1.15);
        }
        .custom-range-slider::-moz-range-thumb {
          width: 14px;
          height: 14px;
          border-radius: 50%;
          background: var(--primary);
          cursor: pointer;
          border: 2px solid white;
          box-shadow: 0 1px 3px rgba(0,0,0,0.5);
          transition: transform 0.1s;
        }
        .custom-range-slider::-moz-range-thumb:hover {
          transform: scale(1.15);
        }
        .slider-ticks {
          position: absolute;
          width: 100%;
          height: 4px;
          top: 50%;
          transform: translateY(-50%);
          z-index: 1;
          left: 0;
          pointer-events: none;
        }
        .slider-tick-dot {
          position: absolute;
          width: 6px;
          height: 6px;
          border-radius: 50%;
          background: rgba(255, 255, 255, 0.2);
          top: 50%;
          transform: translate(-50%, -50%);
        }
        .slider-tick-dot.active {
          background: white;
          box-shadow: 0 0 4px var(--primary);
        }
        .subtitle-actions-row {
          display: flex;
          gap: 12px;
          margin-top: 1rem;
        }
        .subtitle-recommended-btn {
          flex: 1;
          background: #1f1f1f;
          border: 1px solid var(--border);
          color: white;
          padding: 12px;
          border-radius: 30px;
          font-size: 0.95rem;
          font-weight: 700;
          cursor: pointer;
          transition: var(--transition);
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
        }
        .subtitle-recommended-btn:hover {
          background: #2a2a2a;
          border-color: #444;
        }
        .subtitle-save-btn {
          flex: 1;
          background: var(--primary);
          border: none;
          color: white;
          padding: 12px;
          border-radius: 30px;
          font-size: 0.95rem;
          font-weight: 700;
          cursor: pointer;
          transition: var(--transition);
          box-shadow: 0 4px 12px rgba(229, 9, 20, 0.2);
        }
        .subtitle-save-btn:hover {
          background: var(--primary-hover);
          transform: translateY(-1px);
        }

        @media (max-width: 768px) {
          .profile-card-hero {
            flex-direction: column;
            align-items: flex-start;
            gap: 1.2rem;
          }
          .profile-hero-actions {
            width: 100%;
          }
          .setting-item {
            flex-direction: column;
            align-items: flex-start;
            gap: 0.8rem;
          }
          .setting-action {
            width: 100%;
          }
          .settings-select {
            width: 100%;
          }
        }

        /* Settings Modal Overlay & Card Styling */
        .settings-modal-overlay {
          position: fixed;
          top: 0;
          left: 0;
          width: 100%;
          height: 100%;
          background: rgba(0, 0, 0, 0.85);
          backdrop-filter: blur(8px);
          display: flex;
          align-items: center;
          justify-content: center;
          z-index: 10000;
          padding: 20px;
        }
        .settings-modal-card {
          background: #121212;
          border: 1px solid #2a2a2a;
          border-radius: 12px;
          padding: 30px;
          width: 100%;
          max-width: 450px;
          box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
        }
        .settings-modal-card h3 {
          font-size: 1.3rem;
          font-weight: 700;
          color: white;
          margin-bottom: 8px;
        }
        .modal-form-group {
          margin-bottom: 16px;
          display: flex;
          flex-direction: column;
          gap: 6px;
        }
        .modal-form-group label {
          font-size: 0.8rem;
          font-weight: 600;
          color: var(--text-secondary);
        }
        .modal-input {
          background: #1a1a1a;
          border: 1px solid #333;
          border-radius: 6px;
          padding: 10px 12px;
          color: white;
          font-size: 0.9rem;
          width: 100%;
          outline: none;
          transition: border-color 0.2s;
        }
        .modal-input:focus {
          border-color: var(--primary);
        }
        .modal-actions {
          display: flex;
          justify-content: flex-end;
          gap: 12px;
          margin-top: 24px;
        }
        .modal-alert {
          border-radius: 6px;
          padding: 10px 12px;
          font-size: 0.85rem;
          margin-bottom: 16px;
          font-weight: 500;
        }
        .modal-alert.error {
          background: rgba(239, 68, 68, 0.15);
          color: #ef4444;
          border: 1px solid #ef4444;
        }
        .modal-alert.success {
          background: rgba(34, 197, 94, 0.15);
          color: #22c55e;
          border: 1px solid #22c55e;
        }
      `}</style>
    </div>
  );
}
