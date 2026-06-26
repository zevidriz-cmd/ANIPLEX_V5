import React, { useEffect, useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useProfile } from "../context/ProfileContext";
import { 
  User, RefreshCw, Sliders, ToggleLeft, ToggleRight, 
  Settings, Type, Trash2, ArrowLeft, LogOut, History
} from "lucide-react";

export default function MyAniStreamPage() {
  const { currentUser, logout } = useAuth();
  const { activeProfile, selectProfile } = useProfile();
  const navigate = useNavigate();

  // Settings State
  const [qualityCap, setQualityCap] = useState("Auto");
  const [autoplay, setAutoplay] = useState(true);
  const [audioPreference, setAudioPreference] = useState("sub");
  const [playbackSpeed, setPlaybackSpeed] = useState(1);

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

  // Load settings on mount
  useEffect(() => {
    const qCap = localStorage.getItem("anistream_quality_cap") || "Auto";
    const autoPlayVal = localStorage.getItem("anistream_autoplay") !== "false";
    const aPref = localStorage.getItem("anistream_audio_preference") || "sub";
    const savedSpeed = parseFloat(localStorage.getItem("anistream_playback_speed")) || 1;

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

    setQualityCap(qCap);
    setAutoplay(autoPlayVal);
    setAudioPreference(aPref);
    setPlaybackSpeed(savedSpeed);
    setSubSize(initialSize);
    setSubStyle(savedStyle);
    setSubColorIndex(colorIdx);
    setSubPosition(savedPos);
    setSubOpacity(initialOpacity);
  }, []);

  // Save settings helpers
  const updateSetting = (key, value, setter) => {
    localStorage.setItem(key, value);
    setter(value);
  };

  const applyRecommendedSettings = () => {
    setSubSize(22);
    setSubStyle(4); // Outlined (highest legibility on video backgrounds)
    setSubColorIndex(0); // White
    setSubPosition(10); // 10% bottom offset
    setSubOpacity(0); // 0% opacity (transparent background box, outline handles contrast)
  };

  const saveSubtitleSettings = () => {
    localStorage.setItem("anistream_subtitle_size", String(subSize));
    localStorage.setItem("anistream_subtitle_style", String(subStyle));
    localStorage.setItem("anistream_subtitle_color", COLORS[subColorIndex]);
    localStorage.setItem("anistream_subtitle_position", String(subPosition));
    localStorage.setItem("anistream_subtitle_bg_opacity", String(subOpacity));
    localStorage.setItem("anistream_subtitle_bg", subOpacity === 0 ? "transparent" : subOpacity === 100 ? "opaque" : "semi-transparent");
    
    // Dispatch event to live player if open
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
                <div className={`profile-hero-avatar ${activeProfile.avatarUrl}`}>
                  {activeProfile.name.charAt(0).toUpperCase()}
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
        </>
      )}

      <style>{`
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
        }
        .profile-hero-avatar.avatar_orange { background: linear-gradient(135deg, #FF9900, #FF5E00); }
        .profile-hero-avatar.avatar_blue { background: linear-gradient(135deg, #0070F3, #00C6FF); }
        .profile-hero-avatar.avatar_green { background: linear-gradient(135deg, #00C851, #00E676); }
        .profile-hero-avatar.avatar_pink { background: linear-gradient(135deg, #FF4081, #FF80AB); }
        .profile-hero-avatar.avatar_purple { background: linear-gradient(135deg, #AA00FF, #E040FB); }
        
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
      `}</style>
    </div>
  );
}
