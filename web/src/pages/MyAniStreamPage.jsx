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
  const [subSize, setSubSize] = useState("medium");
  const [subColor, setSubColor] = useState("white");
  const [subBg, setSubBg] = useState("semi-transparent");

  // Load settings on mount
  useEffect(() => {
    const qCap = localStorage.getItem("anistream_quality_cap") || "Auto";
    const autoPlayVal = localStorage.getItem("anistream_autoplay") !== "false";
    const aPref = localStorage.getItem("anistream_audio_preference") || "sub";
    const sSize = localStorage.getItem("anistream_subtitle_size") || "medium";
    const sColor = localStorage.getItem("anistream_subtitle_color") || "white";
    const sBg = localStorage.getItem("anistream_subtitle_bg") || "semi-transparent";

    setQualityCap(qCap);
    setAutoplay(autoPlayVal);
    setAudioPreference(aPref);
    setSubSize(sSize);
    setSubColor(sColor);
    setSubBg(sBg);
  }, []);

  // Save settings helpers
  const updateSetting = (key, value, setter) => {
    localStorage.setItem(key, value);
    setter(value);
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

  return (
    <div className="my-anistream-page fade-in container">
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
          </div>
        </section>

        {/* Subtitle Style Preferences Card */}
        <section className="settings-card">
          <div className="card-header-with-icon">
            <Type size={20} className="text-primary" />
            <h2>Subtitle Settings</h2>
          </div>
          <div className="settings-options-list">
            <div className="setting-item">
              <div className="setting-info">
                <h4>Font Size</h4>
                <p className="text-muted">Adjust size of subtitle cues</p>
              </div>
              <div className="setting-action">
                <select 
                  value={subSize}
                  onChange={(e) => updateSetting("anistream_subtitle_size", e.target.value, setSubSize)}
                  className="settings-select"
                >
                  <option value="small">Small</option>
                  <option value="medium">Medium</option>
                  <option value="large">Large</option>
                </select>
              </div>
            </div>

            <div className="setting-item">
              <div className="setting-info">
                <h4>Font Color</h4>
                <p className="text-muted">Change subtitle cue text color</p>
              </div>
              <div className="setting-action">
                <select 
                  value={subColor}
                  onChange={(e) => updateSetting("anistream_subtitle_color", e.target.value, setSubColor)}
                  className="settings-select"
                >
                  <option value="white">White</option>
                  <option value="yellow">Yellow</option>
                </select>
              </div>
            </div>

            <div className="setting-item">
              <div className="setting-info">
                <h4>Background Opacity</h4>
                <p className="text-muted">Backdrop behind subtitle text for readability</p>
              </div>
              <div className="setting-action">
                <select 
                  value={subBg}
                  onChange={(e) => updateSetting("anistream_subtitle_bg", e.target.value, setSubBg)}
                  className="settings-select"
                >
                  <option value="transparent">Transparent</option>
                  <option value="semi-transparent">Semi-Transparent</option>
                  <option value="opaque">Opaque</option>
                </select>
              </div>
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

        .dangerous-zone {
          border-color: rgba(229, 9, 20, 0.2);
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
