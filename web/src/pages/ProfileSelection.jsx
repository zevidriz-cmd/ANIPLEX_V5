import React, { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { useProfile, hashPin } from "../context/ProfileContext";
import { Plus, Edit2, Trash2, X, ShieldAlert, KeyRound, HelpCircle, ArrowLeft, Check, AlertCircle, ChevronRight, Camera, Pencil } from "lucide-react";
import "./ProfileSelection.css";

/* ─── Avatar data ─── */
const AVATAR_CATEGORIES = [
  {
    label: "Anime Heroes",
    avatars: [
      { key: "avatar_shonen", label: "Shonen Protagonist" },
      { key: "avatar_ninja", label: "Cyber Ninja" },
      { key: "avatar_cyber", label: "Cyberpunk Visor" },
    ],
  },
  {
    label: "Magical Collection",
    avatars: [
      { key: "avatar_girl", label: "Magical Girl" },
      { key: "avatar_mascot", label: "Cute Mascot" },
      { key: "avatar_retro", label: "Synthwave Boy" },
    ],
  },
  {
    label: "Cyber Vault",
    avatars: [
      { key: "avatar_cyber", label: "Cyberpunk Visor" },
      { key: "avatar_retro", label: "Synthwave Boy" },
      { key: "avatar_shonen", label: "Shonen Protagonist" },
      { key: "avatar_ninja", label: "Cyber Ninja" },
      { key: "avatar_girl", label: "Magical Girl" },
      { key: "avatar_mascot", label: "Cute Mascot" },
    ],
  },
];

const RECOVERY_QUESTIONS = [
  "What is the name of your first anime?",
  "Who is your favorite anime character?",
  "What was the first anime convention you attended?",
  "What is your favorite anime studio?",
  "What was your childhood nickname?",
];

const AVATAR_URL_MAP = {
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
  avatar_retro: "/avatars/avatar_retro.png",
};

const GLOW_MAP = {
  avatar_orange: "avatar-red-glow",
  avatar_blue: "avatar-blue-glow",
  avatar_green: "avatar-green-glow",
  avatar_pink: "avatar-pink-glow",
  avatar_purple: "avatar-purple-glow",
  avatar_shonen: "avatar-red-glow",
  avatar_girl: "avatar-pink-glow",
  avatar_ninja: "avatar-green-glow",
  avatar_mascot: "avatar-purple-glow",
  avatar_cyber: "avatar-blue-glow",
  avatar_retro: "avatar-yellow-glow",
};

const PULSE_MAP = {
  avatar_orange: "neon-glow-active-red",
  avatar_blue: "neon-glow-active-blue",
  avatar_green: "neon-glow-active-green",
  avatar_pink: "neon-glow-active-pink",
  avatar_purple: "neon-glow-active-purple",
  avatar_shonen: "neon-glow-active-red",
  avatar_girl: "neon-glow-active-pink",
  avatar_ninja: "neon-glow-active-green",
  avatar_mascot: "neon-glow-active-purple",
  avatar_cyber: "neon-glow-active-blue",
  avatar_retro: "neon-glow-active-yellow",
};

const BANNER_MAP = {
  banner_cyber_city: "linear-gradient(135deg, #1a0a2e 0%, #2d1b4e 20%, #0d2137 40%, #1a3a5c 60%, #2e1a3e 80%, #0a1628 100%)",
  banner_sakura: "linear-gradient(135deg, #2d0b1e 0%, #4e1b34 30%, #370d21 60%, #1a2a5c 100%)",
  banner_sunset: "linear-gradient(135deg, #2b0d04 0%, #4e1b0d 30%, #5c2c0d 60%, #1c0d28 100%)",
  banner_deep_space: "linear-gradient(135deg, #050a18 0%, #120924 30%, #210d37 60%, #050c18 100%)",
  banner_forest: "linear-gradient(135deg, #021a0a 0%, #0b3014 30%, #092c20 60%, #0d1228 100%)",
  banner_midnight: "linear-gradient(135deg, #050508 0%, #11111a 30%, #1b1b2a 60%, #08080f 100%)"
};

const BANNER_OPTIONS = [
  { key: "banner_cyber_city", label: "Cyber City Neon" },
  { key: "banner_sakura", label: "Sakura Blossom" },
  { key: "banner_sunset", label: "Retro Sunset" },
  { key: "banner_deep_space", label: "Nebula Space" },
  { key: "banner_forest", label: "Mystic Forest" },
  { key: "banner_midnight", label: "Midnight Velvet" }
];

const getAvatarUrl = (key) => AVATAR_URL_MAP[key] || "/avatars/avatar_shonen.png";
const getGlow = (key) => GLOW_MAP[key] || "avatar-red-glow";
const getPulse = (key) => PULSE_MAP[key] || "neon-glow-active-red";
const getBannerStyle = (key) => BANNER_MAP[key] || BANNER_MAP.banner_cyber_city;

/* ─────────────────────────────────────────── */

export default function ProfileSelection() {
  const { profiles, selectProfile, createProfile, updateProfile, deleteProfile } = useProfile();
  const navigate = useNavigate();

  // Main page state
  const [isManaging, setIsManaging] = useState(false);

  // PIN modal
  const [pinModalProfile, setPinModalProfile] = useState(null);
  const [enteredPin, setEnteredPin] = useState("");
  const [pinError, setPinError] = useState("");
  const [isShaking, setIsShaking] = useState(false);
  const [showRecovery, setShowRecovery] = useState(false);
  const [recoveryAnswer, setRecoveryAnswer] = useState("");
  const [recoveryError, setRecoveryError] = useState("");

  // Full-screen edit profile
  const [editData, setEditData] = useState(null);
  // Avatar picker modal (inside edit)
  const [showAvatarPicker, setShowAvatarPicker] = useState(false);
  const [tempAvatar, setTempAvatar] = useState(null);

  // Banner picker modal
  const [showBannerPicker, setShowBannerPicker] = useState(false);
  const [tempBanner, setTempBanner] = useState(null);

  // Delete confirmation
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(null);

  /* ─── Helpers ─── */
  const fadeTransition = (targetPath, onFadeComplete) => {
    document.body.classList.add("fade-out-page");
    setTimeout(() => {
      if (onFadeComplete) onFadeComplete();
      navigate(targetPath);
      setTimeout(() => {
        document.body.classList.remove("fade-out-page");
      }, 50);
    }, 400);
  };

  /* ─── Profile click ─── */
  const handleProfileClick = (profile) => {
    if (isManaging) {
      openEditProfile(profile);
    } else {
      if (profile.pin) {
        setPinModalProfile(profile);
        setEnteredPin("");
        setPinError("");
        setShowRecovery(false);
        setRecoveryAnswer("");
        setRecoveryError("");
      } else {
        fadeTransition("/", () => {
          selectProfile(profile);
        });
      }
    }
  };

  const openEditProfile = (profile) => {
    setEditData({
      id: profile.id,
      name: profile.name,
      avatarUrl: profile.avatarUrl,
      pinString: "",
      requirePin: !!profile.pin,
      recoveryQuestion: profile.recoveryQuestion || RECOVERY_QUESTIONS[0],
      recoveryAnswer: profile.recoveryAnswer || "",
      hasPin: !!profile.pin,
      isNew: false,
      bannerUrl: profile.bannerUrl || "banner_cyber_city"
    });
  };

  const openNewProfile = () => {
    setEditData({
      name: "",
      avatarUrl: "avatar_shonen",
      pinString: "",
      requirePin: false,
      recoveryQuestion: RECOVERY_QUESTIONS[0],
      recoveryAnswer: "",
      isNew: true,
      bannerUrl: "banner_cyber_city"
    });
  };

  /* ─── PIN logic ─── */
  useEffect(() => {
    if (!pinModalProfile || showRecovery) return;
    const handleKeyDown = (e) => {
      if (e.key >= "0" && e.key <= "9") {
        if (enteredPin.length < 4) setEnteredPin((p) => p + e.key);
      } else if (e.key === "Backspace") {
        setEnteredPin((p) => p.slice(0, -1));
      } else if (e.key === "Escape") {
        setPinModalProfile(null);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [pinModalProfile, enteredPin, showRecovery]);

  useEffect(() => {
    if (enteredPin.length === 4 && pinModalProfile) validatePin();
  }, [enteredPin]);

  const validatePin = async () => {
    setPinError("");
    try {
      const hashed = await hashPin(enteredPin);
      if (hashed === pinModalProfile.pin) {
        setPinModalProfile(null);
        fadeTransition("/", () => {
          selectProfile(pinModalProfile);
        });
      } else {
        setIsShaking(true);
        setPinError("Incorrect PIN");
        setEnteredPin("");
        setTimeout(() => setIsShaking(false), 500);
      }
    } catch {
      setPinError("Error validating PIN.");
    }
  };

  const handleKeypadPress = (val) => {
    if (showRecovery) return;
    if (val === "back") {
      setEnteredPin((p) => p.slice(0, -1));
    } else if (enteredPin.length < 4) {
      setEnteredPin((p) => p + val);
    }
  };

  const handleRecoverySubmit = (e) => {
    e.preventDefault();
    setRecoveryError("");
    if (!recoveryAnswer.trim()) {
      setRecoveryError("Please enter your answer.");
      return;
    }
    if (recoveryAnswer.toLowerCase().trim() === pinModalProfile.recoveryAnswer?.toLowerCase().trim()) {
      setPinModalProfile(null);
      fadeTransition("/", () => {
        selectProfile(pinModalProfile);
      });
    } else {
      setRecoveryError("Incorrect recovery answer.");
    }
  };

  /* ─── Save profile ─── */
  const handleSaveProfile = async (e) => {
    e.preventDefault();
    if (!editData.name.trim()) return;

    let targetPin = null;
    let targetQuestion = null;
    let targetAnswer = null;

    if (editData.requirePin) {
      if (editData.pinString) {
        if (editData.pinString.length !== 4) {
          alert("PIN must be 4 digits.");
          return;
        }
        targetPin = editData.pinString;
      } else if (!editData.isNew && editData.hasPin) {
        targetPin = null;
      } else {
        alert("Please set a 4-digit PIN.");
        return;
      }
      if (!editData.recoveryAnswer.trim()) {
        alert("Please provide an answer to the recovery question.");
        return;
      }
      targetQuestion = editData.recoveryQuestion;
      targetAnswer = editData.recoveryAnswer;
    } else {
      if (!editData.isNew && editData.hasPin) {
        targetPin = "REMOVE";
      }
    }

    try {
      if (editData.isNew) {
        await createProfile(editData.name, editData.avatarUrl, targetPin, targetQuestion, targetAnswer, editData.bannerUrl);
      } else {
        await updateProfile(editData.id, editData.name, editData.avatarUrl, targetPin, targetQuestion, targetAnswer, editData.bannerUrl);
      }
      setEditData(null);
    } catch (err) {
      alert(err.message || "Failed to save profile.");
    }
  };

  const handleConfirmDelete = async () => {
    if (!showDeleteConfirm) return;
    try {
      await deleteProfile(showDeleteConfirm);
      setShowDeleteConfirm(null);
      setEditData(null);
    } catch {
      alert("Failed to delete profile.");
    }
  };

  /* ─── Avatar picker ─── */
  const openAvatarPicker = () => {
    setTempAvatar(editData.avatarUrl);
    setShowAvatarPicker(true);
  };

  const confirmAvatarSelection = () => {
    setEditData({ ...editData, avatarUrl: tempAvatar });
    setShowAvatarPicker(false);
  };

  /* ════════════════════════════════════════════
     RENDER
     ════════════════════════════════════════════ */

  // ──── Full-screen Edit Profile ────
  if (editData) {
    return (
      <div className="edit-profile-page">
        {/* Top bar */}
        <div className="edit-profile-topbar">
          <h1 className="edit-profile-heading">
            {editData.isNew ? "Create Profile" : "Edit Profile"}
          </h1>
          <div className="edit-profile-topbar-actions">
            <button className="ep-btn-cancel" onClick={() => setEditData(null)}>
              Cancel
            </button>
            <button className="ep-btn-save" onClick={handleSaveProfile}>
              Save
            </button>
          </div>
        </div>

        {/* Banner */}
        <div className="edit-profile-banner" style={{ background: getBannerStyle(editData.bannerUrl) }}>
          <div className="edit-profile-banner-gradient" />
          <button className="edit-profile-banner-btn" type="button" onClick={() => {
            setTempBanner(editData.bannerUrl);
            setShowBannerPicker(true);
          }}>
            <Camera size={16} />
            <span>Update Background Image</span>
          </button>

          {/* Avatar overlapping banner */}
          <div className="edit-profile-avatar-area">
            <div
              className={`edit-profile-avatar-ring ${getGlow(editData.avatarUrl)}`}
              onClick={openAvatarPicker}
            >
              <img
                src={getAvatarUrl(editData.avatarUrl)}
                alt="Avatar"
                className="edit-profile-avatar-img"
              />
              <div className="edit-profile-avatar-edit-badge">
                <Pencil size={14} />
              </div>
            </div>
            {/* Side edit icon */}
            <button className="edit-profile-side-edit" type="button" onClick={openAvatarPicker}>
              <Pencil size={16} />
            </button>
          </div>
        </div>

        {/* Form body */}
        <form className="edit-profile-form" onSubmit={handleSaveProfile}>
          {/* Name */}
          <div className="ep-field">
            <label className="ep-field-label">Profile Name</label>
            <input
              type="text"
              className="ep-field-input"
              value={editData.name}
              onChange={(e) => setEditData({ ...editData, name: e.target.value })}
              placeholder="Enter name"
              maxLength={15}
              required
            />
            <span className="ep-field-hint">
              This is seen within your household and can be changed anytime.
            </span>
          </div>

          {/* PIN Lock section */}
          <div className="ep-security-box">
            <div className="ep-security-toggle">
              <div className="ep-security-info">
                <KeyRound size={18} className="ep-security-icon" />
                <div>
                  <span className="ep-security-title">Require Profile PIN Lock</span>
                  <span className="ep-security-subtitle">Lock profile with a 4-digit security code</span>
                </div>
              </div>
              <input
                type="checkbox"
                checked={editData.requirePin}
                onChange={(e) => setEditData({ ...editData, requirePin: e.target.checked })}
                className="ep-checkbox"
              />
            </div>

            {editData.requirePin && (
              <div className="ep-security-fields animate-fadeIn">
                <div className="ep-field">
                  <label className="ep-field-label-sub">
                    {editData.hasPin ? "Update 4-Digit PIN (leave blank to keep)" : "4-Digit PIN Code"}
                  </label>
                  <input
                    type="password"
                    maxLength={4}
                    className="ep-field-input-sub"
                    value={editData.pinString}
                    onChange={(e) =>
                      setEditData({ ...editData, pinString: e.target.value.replace(/\D/g, "") })
                    }
                    placeholder={editData.hasPin ? "•••• (Unchanged)" : "••••"}
                  />
                </div>
                <div className="ep-field">
                  <label className="ep-field-label-sub">Security Question (For Recovery)</label>
                  <select
                    className="ep-field-select"
                    value={editData.recoveryQuestion}
                    onChange={(e) =>
                      setEditData({ ...editData, recoveryQuestion: e.target.value })
                    }
                  >
                    {RECOVERY_QUESTIONS.map((q) => (
                      <option key={q} value={q}>{q}</option>
                    ))}
                  </select>
                </div>
                <div className="ep-field">
                  <label className="ep-field-label-sub">Recovery Answer</label>
                  <input
                    type="text"
                    className="ep-field-input-sub"
                    value={editData.recoveryAnswer}
                    onChange={(e) =>
                      setEditData({ ...editData, recoveryAnswer: e.target.value })
                    }
                    placeholder="Secret Answer"
                    required={editData.requirePin}
                  />
                </div>
              </div>
            )}
          </div>

          {/* Delete button for existing profiles */}
          {!editData.isNew && (
            <button
              type="button"
              className="ep-delete-btn"
              onClick={() => setShowDeleteConfirm(editData.id)}
            >
              <Trash2 size={16} />
              <span>Delete This Profile</span>
            </button>
          )}
        </form>

        {/* ──── Avatar Selection Modal ──── */}
        {showAvatarPicker && (
          <div className="avatar-picker-overlay">
            <div className="avatar-picker-modal">
              {/* Header */}
              <div className="avatar-picker-header">
                <div className="avatar-picker-header-left">
                  <div className={`avatar-picker-preview-ring ${getGlow(tempAvatar)}`}>
                    <img
                      src={getAvatarUrl(tempAvatar)}
                      alt="Selected"
                      className="avatar-picker-preview-img"
                    />
                  </div>
                  <div className="avatar-picker-header-text">
                    <h2 className="avatar-picker-title">Avatar Selection</h2>
                    <p className="avatar-picker-subtitle">
                      Choose your avatar! You can change it at any time.
                    </p>
                    <div className="avatar-picker-actions">
                      <button
                        className="ap-btn-cancel"
                        onClick={() => setShowAvatarPicker(false)}
                      >
                        Cancel
                      </button>
                      <button
                        className="ap-btn-save"
                        onClick={confirmAvatarSelection}
                        disabled={tempAvatar === editData.avatarUrl}
                      >
                        Save
                      </button>
                    </div>
                  </div>
                </div>
                <button
                  className="avatar-picker-close"
                  onClick={() => setShowAvatarPicker(false)}
                >
                  <X size={22} />
                </button>
              </div>

              {/* Category rows */}
              <div className="avatar-picker-body">
                {AVATAR_CATEGORIES.map((cat) => (
                  <AvatarCategoryRow
                    key={cat.label}
                    category={cat}
                    selectedKey={tempAvatar}
                    onSelect={setTempAvatar}
                  />
                ))}
              </div>
            </div>
          </div>
        )}

        {/* ──── Banner Selection Modal ──── */}
        {showBannerPicker && (
          <div className="avatar-picker-overlay">
            <div className="avatar-picker-modal">
              {/* Header */}
              <div className="avatar-picker-header">
                <div className="avatar-picker-header-left banner-picker-header-left">
                  <div className="avatar-picker-preview-banner" style={{ background: getBannerStyle(tempBanner) }}>
                    <div style={{ color: "#fff", textShadow: "0 1px 3px rgba(0,0,0,0.8)" }}>Preview</div>
                  </div>
                  <div className="avatar-picker-header-text banner-picker-header-text">
                    <h2 className="avatar-picker-title">Background Banner</h2>
                    <p className="avatar-picker-subtitle">
                      Choose a premium gradient theme for your profile backdrop!
                    </p>
                    <div className="avatar-picker-actions">
                      <button
                        className="ap-btn-cancel"
                        onClick={() => setShowBannerPicker(false)}
                      >
                        Cancel
                      </button>
                      <button
                        className="ap-btn-save"
                        onClick={() => {
                          setEditData({ ...editData, bannerUrl: tempBanner });
                          setShowBannerPicker(false);
                        }}
                        disabled={tempBanner === editData.bannerUrl}
                      >
                        Save
                      </button>
                    </div>
                  </div>
                </div>
                <button
                  className="avatar-picker-close"
                  onClick={() => setShowBannerPicker(false)}
                >
                  <X size={22} />
                </button>
              </div>

              {/* Body with grid of banner choices */}
              <div className="avatar-picker-body" style={{ padding: "24px" }}>
                <div className="banners-grid">
                  {BANNER_OPTIONS.map((opt) => (
                    <button
                      key={opt.key}
                      type="button"
                      className={`banner-option-card ${tempBanner === opt.key ? "selected" : ""}`}
                      onClick={() => setTempBanner(opt.key)}
                    >
                      <div className="banner-option-preview" style={{ background: getBannerStyle(opt.key) }}>
                        {tempBanner === opt.key && (
                          <div className="banner-option-check flex-center">
                            <Check size={14} />
                          </div>
                        )}
                      </div>
                      <span className="banner-option-label">{opt.label}</span>
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ──── Delete Confirm ──── */}
        {showDeleteConfirm && (
          <div className="modal-overlay z-top flex-center">
            <div className="delete-confirm-card">
              <div className="delete-alert-icon flex-center">
                <ShieldAlert size={24} />
              </div>
              <h3 className="delete-title">Delete Profile?</h3>
              <p className="delete-desc">
                Are you sure? This profile's entire watchlist and watch history will be permanently wiped. This action is irreversible.
              </p>
              <div className="delete-confirm-actions">
                <button className="btn-keep-profile" onClick={() => setShowDeleteConfirm(null)}>
                  No, Keep It
                </button>
                <button className="btn-confirm-delete" onClick={handleConfirmDelete}>
                  Yes, Delete
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }

  // ──── Main Profile Selection Page ────
  return (
    <div className="profiles-page">
      <div className="bg-gradient-radial-nebula" />

      <div className="profiles-container">
        <div className="profiles-content-card glass-card">
          <div className="decor-orb orb-top-right" />
          <div className="decor-orb orb-bottom-left" />

          <h1 className="profiles-title neon-glow-text">
            {isManaging ? "Manage Profiles" : "Who's going on an adventure?"}
          </h1>

          <div className="profiles-grid">
            {profiles.map((profile) => (
              <div
                key={profile.id}
                className="profile-card group"
                onClick={() => handleProfileClick(profile)}
              >
                <div
                  className={`profile-avatar-wrapper ${getGlow(profile.avatarUrl)} ${
                    isManaging ? "animate-wiggle" : ""
                  }`}
                >
                  <div className="profile-avatar-inner">
                    <img
                      src={getAvatarUrl(profile.avatarUrl)}
                      alt={profile.name}
                      className="profile-avatar-img"
                    />
                  </div>
                  {isManaging && (
                    <div className="edit-badge flex-center">
                      <Edit2 size={16} />
                    </div>
                  )}
                </div>
                <span className="profile-name">{profile.name}</span>
              </div>
            ))}

            {profiles.length < 4 && (
              <div className="profile-card group" onClick={openNewProfile}>
                <div className="profile-avatar-wrapper add-new-circle">
                  <Plus size={36} className="add-plus-icon" />
                </div>
                <span className="profile-name">Add Profile</span>
              </div>
            )}
          </div>

          <button
            className={`btn-action-done ${isManaging ? "active" : ""}`}
            onClick={() => setIsManaging(!isManaging)}
          >
            {isManaging ? "Done" : "Manage Profiles"}
          </button>
        </div>
      </div>

      {/* ──── PIN Entry Modal ──── */}
      {pinModalProfile && (
        <div className="modal-overlay flex-center">
          <div
            className={`glass-card modal-card-pin text-center relative flex-center flex-column ${getPulse(
              pinModalProfile.avatarUrl
            )}`}
          >
            <button className="close-modal-btn" onClick={() => setPinModalProfile(null)}>
              <X size={22} />
            </button>

            {!showRecovery ? (
              <div className="modal-content-wrapper flex flex-column items-center">
                <div className="avatar-wrapper-pulse">
                  <div className="absolute-bg-glow" />
                  <div className={`modal-avatar-pin ${getGlow(pinModalProfile.avatarUrl)}`}>
                    <img
                      src={getAvatarUrl(pinModalProfile.avatarUrl)}
                      alt="Avatar"
                      className="w-full h-full object-cover rounded-full"
                    />
                  </div>
                </div>
                <h2 className="modal-title-pin">Enter Profile PIN</h2>
                <p className="modal-subtitle-pin">Accessing {pinModalProfile.name}</p>

                {pinError && (
                  <div className="error-banner animate-shake">
                    <AlertCircle size={14} />
                    <span>{pinError}</span>
                  </div>
                )}

                <div className={`dots-row flex justify-center items-center ${isShaking ? "animate-shake" : ""}`}>
                  {[0, 1, 2, 3].map((idx) => (
                    <div
                      key={idx}
                      className={`dot-indicator ${enteredPin.length > idx ? "filled" : ""}`}
                    />
                  ))}
                </div>

                <div className="keypad-grid">
                  {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((val) => (
                    <button
                      key={val}
                      onClick={() => handleKeypadPress(val.toString())}
                      className="keypad-btn"
                    >
                      {val}
                    </button>
                  ))}
                  <div />
                  <button onClick={() => handleKeypadPress("0")} className="keypad-btn">
                    0
                  </button>
                  <button
                    onClick={() => handleKeypadPress("back")}
                    className="keypad-btn backspace-btn"
                  >
                    Delete
                  </button>
                </div>

                <button onClick={() => setShowRecovery(true)} className="forgot-pin-link">
                  Forgot PIN?
                </button>
              </div>
            ) : (
              <div className="modal-content-wrapper flex flex-column w-full relative">
                <div className="recovery-top-border" />
                <button
                  onClick={() => setShowRecovery(false)}
                  className="back-to-pin-btn flex items-center"
                >
                  <ArrowLeft size={14} style={{ marginRight: "6px" }} />
                  <span>Back to PIN lock</span>
                </button>

                <div className="recovery-header flex flex-column items-center text-center">
                  <div className="recovery-header-icon flex-center">
                    <HelpCircle size={32} />
                  </div>
                  <div className="recovery-header-title text-center">
                    <h2>
                      Security Question <span className="highlight-color">verification</span>
                    </h2>
                    <p className="recovery-intro">
                      To protect your account, please verify your identity by answering your recovery question.
                    </p>
                  </div>
                </div>

                <div className="recovery-question-block">
                  <div className="shimmer" />
                  <span className="recovery-question-label">Question</span>
                  <p className="recovery-question-text">"{pinModalProfile.recoveryQuestion}"</p>
                </div>

                {recoveryError && (
                  <div className="error-banner animate-shake">
                    <AlertCircle size={14} />
                    <span>{recoveryError}</span>
                  </div>
                )}

                <form onSubmit={handleRecoverySubmit} style={{ width: "100%" }} className="recovery-form">
                  <div className="form-group-custom relative">
                    <input
                      type="text"
                      className="form-input-custom input-gradient-focus pt-6"
                      id="security_answer"
                      placeholder=" "
                      value={recoveryAnswer}
                      onChange={(e) => setRecoveryAnswer(e.target.value)}
                      required
                      autoFocus
                    />
                    <label className="floating-label" htmlFor="security_answer">
                      Answer here...
                    </label>
                  </div>

                  <div className="recovery-actions flex gap-4 mt-6">
                    <button type="button" onClick={() => setShowRecovery(false)} className="btn-recovery-cancel">
                      Cancel
                    </button>
                    <button type="submit" className="btn-recovery-verify">
                      Verify
                    </button>
                  </div>
                </form>

                <div className="recovery-tip">
                  <AlertCircle size={14} className="text-muted" />
                  <p>
                    Answers are case-insensitive. If you forgot your answer, please contact the Nebula Support terminal.
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

/* ─── Avatar Category Row sub-component ─── */
function AvatarCategoryRow({ category, selectedKey, onSelect }) {
  const scrollRef = useRef(null);

  const scrollRight = () => {
    if (scrollRef.current) {
      scrollRef.current.scrollBy({ left: 200, behavior: "smooth" });
    }
  };

  return (
    <div className="avatar-category">
      <h3 className="avatar-category-title">{category.label}</h3>
      <div className="avatar-category-row-wrapper">
        <div className="avatar-category-row" ref={scrollRef}>
          {category.avatars.map((av, i) => (
            <button
              key={`${av.key}-${i}`}
              className={`avatar-option ${selectedKey === av.key ? "selected" : ""}`}
              onClick={() => onSelect(av.key)}
              title={av.label}
            >
              <img src={getAvatarUrl(av.key)} alt={av.label} className="avatar-option-img" />
              {selectedKey === av.key && (
                <div className="avatar-option-check">
                  <Check size={12} />
                </div>
              )}
            </button>
          ))}
        </div>
        <button className="avatar-row-scroll-btn" onClick={scrollRight}>
          <ChevronRight size={24} />
        </button>
      </div>
    </div>
  );
}
