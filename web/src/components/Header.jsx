import React, { useState } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useProfile } from "../context/ProfileContext";
import { 
  Search, User, LogOut, RefreshCw, Film, Calendar, 
  History, Bookmark, Home, Flame, Menu, Settings, Bell, Gift, X 
} from "lucide-react";

export default function Header() {
  const { currentUser, logout } = useAuth();
  const { activeProfile, selectProfile } = useProfile();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const navigate = useNavigate();

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

  const handleLogout = async () => {
    try {
      await logout();
      navigate("/auth");
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <>
      <header className="header glass">
        <div className="header-container container">
          <div style={{ display: "flex", alignItems: "center", gap: "1rem" }}>
            {currentUser && activeProfile && (
              <button 
                className="hamburger-btn" 
                onClick={() => setDrawerOpen(true)}
                title="Menu"
                style={{
                  background: "none",
                  border: "none",
                  color: "white",
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "center",
                  padding: "4px",
                  marginRight: "2px"
                }}
              >
                <Menu size={22} />
              </button>
            )}
            <Link to="/" className="logo">
              Ani<span>Stream</span>
            </Link>
          </div>

          {currentUser && activeProfile && (
            <nav className="nav-links">
              <NavLink to="/" className={({ isActive }) => isActive ? "nav-link active" : "nav-link"}>
                Home
              </NavLink>
              <div className="nav-link-dropdown-wrapper">
                <NavLink to="/new-and-hot" className={({ isActive }) => isActive ? "nav-link active" : "nav-link"}>
                  <Flame size={16} /> New & Hot
                </NavLink>
                <div className="nav-link-dropdown-menu">
                  <NavLink to="/new-and-hot" className="nav-dropdown-item">
                    Trending & Schedule
                  </NavLink>
                  <NavLink to="/seasonal" className="nav-dropdown-item">
                    Seasonal Anime
                  </NavLink>
                </div>
              </div>
              <NavLink to="/watchlist" className={({ isActive }) => isActive ? "nav-link active" : "nav-link"}>
                <Bookmark size={16} /> Watchlist
              </NavLink>
              <NavLink to="/history" className={({ isActive }) => isActive ? "nav-link active" : "nav-link"}>
                <History size={16} /> History
              </NavLink>
              <NavLink to="/my-anistream" className={({ isActive }) => isActive ? "nav-link active" : "nav-link"}>
                <User size={16} /> Preferences
              </NavLink>
            </nav>
          )}

          <div className="header-actions">
            {currentUser && activeProfile && (
              <Link to="/search" className="search-btn" title="Search Anime">
                <Search size={20} />
              </Link>
            )}

            {currentUser && (
              <div className="profile-dropdown-wrapper">
                <button 
                  className="profile-trigger" 
                  onClick={() => setDrawerOpen(true)}
                >
                  {activeProfile ? (
                    <div className="avatar-circle overflow-hidden bg-zinc-800 border border-zinc-700">
                      <img src={getAvatarUrl(activeProfile.avatarUrl)} alt="" className="w-full h-full object-cover" />
                    </div>
                  ) : (
                    <div className="avatar-circle default">
                      <User size={18} />
                    </div>
                  )}
                  <span className="profile-name-span">{activeProfile?.name || "Select Profile"}</span>
                </button>
              </div>
            )}
          </div>
        </div>
      </header>

      {/* Sidebar Drawer Panel */}
      {drawerOpen && (
        <div className="drawer-backdrop" onClick={() => setDrawerOpen(false)} />
      )}
      <div className={`sidebar-drawer ${drawerOpen ? "open" : ""}`}>
        <div className="drawer-header">
          {currentUser && activeProfile ? (
            <Link to="/my-anistream" className="drawer-profile-card" onClick={() => setDrawerOpen(false)}>
              <div className="drawer-avatar overflow-hidden bg-zinc-800 border border-zinc-700">
                <img src={getAvatarUrl(activeProfile.avatarUrl)} alt="" className="w-full h-full object-cover" />
              </div>
              <div className="drawer-profile-info">
                <span className="drawer-profile-name">{activeProfile.name}</span>
                <span className="drawer-profile-role">Premium Member</span>
              </div>
            </Link>
          ) : (
            <div className="drawer-profile-card">
              <div className="drawer-avatar default">
                <User size={20} />
              </div>
              <div className="drawer-profile-info">
                <span className="drawer-profile-name">Guest</span>
              </div>
            </div>
          )}
          <button className="drawer-edit-btn" onClick={() => setDrawerOpen(false)} title="Close menu">
            <X size={20} />
          </button>
        </div>
        
        <div className="drawer-content">
          {currentUser && activeProfile ? (
            <>
              <button 
                className="drawer-menu-item" 
                onClick={() => {
                  setDrawerOpen(false);
                  selectProfile(null);
                  navigate("/profiles");
                }}
              >
                <RefreshCw size={18} />
                <span>Switch Profile</span>
              </button>
              
              <NavLink 
                to="/my-anistream" 
                className={({ isActive }) => isActive ? "drawer-menu-item active" : "drawer-menu-item"}
                onClick={() => setDrawerOpen(false)}
              >
                <Settings size={18} />
                <span>Settings</span>
              </NavLink>
              
              <NavLink 
                to="/watchlist" 
                className={({ isActive }) => isActive ? "drawer-menu-item active" : "drawer-menu-item"}
                onClick={() => setDrawerOpen(false)}
              >
                <Bookmark size={18} />
                <span>Watchlist</span>
              </NavLink>
              
              <NavLink 
                to="/history" 
                className={({ isActive }) => isActive ? "drawer-menu-item active" : "drawer-menu-item"}
                onClick={() => setDrawerOpen(false)}
              >
                <History size={18} />
                <span>History</span>
              </NavLink>
              
              <NavLink 
                to="/seasonal" 
                className={({ isActive }) => isActive ? "drawer-menu-item active" : "drawer-menu-item"}
                onClick={() => setDrawerOpen(false)}
              >
                <Calendar size={18} />
                <span>Seasonal Anime</span>
              </NavLink>
              
              <button 
                className="drawer-menu-item" 
                onClick={() => {
                  setDrawerOpen(false);
                  alert("No new notifications");
                }}
              >
                <Bell size={18} />
                <span>Notifications</span>
              </button>
              
              <button 
                className="drawer-menu-item" 
                onClick={() => {
                  setDrawerOpen(false);
                  alert("Redeem Gift Card features coming soon!");
                }}
              >
                <Gift size={18} />
                <span>Gift Card</span>
              </button>
              
              <button 
                onClick={() => { 
                  setDrawerOpen(false); 
                  handleLogout(); 
                }} 
                className="drawer-menu-item logout"
              >
                <LogOut size={18} />
                <span>Log Out</span>
              </button>
            </>
          ) : (
            <NavLink to="/auth" className="drawer-menu-item" onClick={() => setDrawerOpen(false)}>
              <User size={18} />
              <span>Log In</span>
            </NavLink>
          )}
        </div>
      </div>

      {currentUser && activeProfile && (
        <nav className="mobile-bottom-nav glass">
          <NavLink to="/" className={({ isActive }) => isActive ? "mobile-nav-item active" : "mobile-nav-item"}>
            <Home size={20} />
            <span>Home</span>
          </NavLink>
          <NavLink to="/new-and-hot" className={({ isActive }) => isActive ? "mobile-nav-item active" : "mobile-nav-item"}>
            <Flame size={20} />
            <span>New & Hot</span>
          </NavLink>
          <NavLink to="/watchlist" className={({ isActive }) => isActive ? "mobile-nav-item active" : "mobile-nav-item"}>
            <Bookmark size={20} />
            <span>My List</span>
          </NavLink>
          <NavLink to="/history" className={({ isActive }) => isActive ? "mobile-nav-item active" : "mobile-nav-item"}>
            <History size={20} />
            <span>History</span>
          </NavLink>
          <button 
            type="button"
            onClick={() => setDrawerOpen(true)}
            className="mobile-nav-item"
            style={{ background: "none", border: "none", cursor: "pointer", fontFamily: "inherit" }}
          >
            <div className="mobile-nav-avatar overflow-hidden bg-zinc-800 border border-zinc-700">
              <img src={getAvatarUrl(activeProfile.avatarUrl)} alt="" className="w-full h-full object-cover" />
            </div>
            <span>Profile</span>
          </button>
        </nav>
      )}

      <style>{`
        .header {
          position: fixed;
          top: 0;
          left: 0;
          right: 0;
          height: var(--header-height);
          z-index: 100;
          display: flex;
          align-items: center;
          transition: var(--transition);
        }
        .header-container {
          display: flex;
          align-items: center;
          justify-content: space-between;
          width: 100%;
        }
        .logo {
          font-size: 1.6rem;
          font-weight: 800;
          color: white;
          text-decoration: none;
          letter-spacing: -1px;
        }
        .logo span {
          color: var(--primary);
        }
        .nav-links {
          display: flex;
          align-items: center;
          gap: 1.8rem;
        }
        .nav-link {
          color: var(--text-secondary);
          text-decoration: none;
          font-size: 0.95rem;
          font-weight: 500;
          display: flex;
          align-items: center;
          gap: 0.4rem;
          transition: var(--transition);
        }
        .nav-link:hover, .nav-link.active {
          color: white;
        }
        .nav-link.active {
          font-weight: 700;
          color: var(--primary);
        }
        .nav-link-dropdown-wrapper {
          position: relative;
          display: inline-block;
          padding: 10px 0;
        }
        .nav-link-dropdown-menu {
          display: none;
          position: absolute;
          top: calc(100% - 4px);
          left: 50%;
          transform: translateX(-50%);
          background-color: #141414;
          border: 1px solid var(--border);
          border-radius: 8px;
          padding: 8px;
          min-width: 170px;
          box-shadow: 0 10px 25px rgba(0,0,0,0.5);
          z-index: 1000;
          flex-direction: column;
          gap: 6px;
        }
        .nav-link-dropdown-wrapper:hover .nav-link-dropdown-menu {
          display: flex;
        }
        .nav-dropdown-item {
          color: var(--text-secondary);
          text-decoration: none;
          font-size: 0.85rem;
          font-weight: 600;
          padding: 8px 12px;
          border-radius: 6px;
          transition: var(--transition);
          white-space: nowrap;
          display: block;
        }
        .nav-dropdown-item:hover, .nav-dropdown-item.active {
          color: white;
          background-color: rgba(255,255,255,0.06);
        }
        .nav-dropdown-item.active {
          color: var(--primary);
        }
        .header-actions {
          display: flex;
          align-items: center;
          gap: 1.2rem;
        }
        .search-btn {
          color: var(--text-secondary);
          background: none;
          border: none;
          cursor: pointer;
          transition: var(--transition);
          display: flex;
          align-items: center;
        }
        .search-btn:hover {
          color: white;
          transform: scale(1.1);
        }
        .profile-dropdown-wrapper {
          position: relative;
        }
        .profile-trigger {
          display: flex;
          align-items: center;
          gap: 0.6rem;
          background: none;
          border: none;
          cursor: pointer;
          color: white;
        }
        .profile-name-span {
          font-weight: 600;
          font-size: 0.9rem;
        }
        .avatar-circle {
          width: 36px;
          height: 36px;
          border-radius: 6px;
          display: flex;
          align-items: center;
          justify-content: center;
          font-weight: 700;
          font-size: 1rem;
          color: white;
          overflow: hidden;
        }
        .avatar-circle img {
          width: 100%;
          height: 100%;
          object-fit: cover;
          border-radius: inherit;
        }
        .avatar-circle.avatar_orange { background: linear-gradient(135deg, #FF9900, #FF5E00); }
        .avatar-circle.avatar_blue { background: linear-gradient(135deg, #0070F3, #00C6FF); }
        .avatar-circle.avatar_green { background: linear-gradient(135deg, #00C851, #00E676); }
        .avatar-circle.avatar_pink { background: linear-gradient(135deg, #FF4081, #FF80AB); }
        .avatar-circle.avatar_purple { background: linear-gradient(135deg, #AA00FF, #E040FB); }
        .avatar-circle.default { background: #333333; }
        
        .dropdown-menu {
          position: absolute;
          right: 0;
          top: calc(100% + 10px);
          background: #141414;
          border: 1px solid var(--border);
          border-radius: 6px;
          width: 180px;
          padding: 0.5rem;
          box-shadow: 0 10px 25px rgba(0, 0, 0, 0.5);
          display: flex;
          flex-direction: column;
          gap: 0.2rem;
          animation: fadeIn 0.2s ease-out;
        }
        .dropdown-item {
          display: flex;
          align-items: center;
          gap: 0.6rem;
          padding: 0.6rem 0.8rem;
          background: none;
          border: none;
          color: var(--text-secondary);
          font-family: var(--font-family);
          font-size: 0.85rem;
          text-align: left;
          cursor: pointer;
          border-radius: 4px;
          width: 100%;
          transition: var(--transition);
        }
        .dropdown-item:hover {
          background: rgba(255, 255, 255, 0.05);
          color: white;
        }
        .dropdown-item.logout:hover {
          background: rgba(229, 9, 20, 0.1);
          color: var(--primary);
        }
        /* Mobile bottom nav styling */
        .mobile-bottom-nav {
          display: none;
        }
        @media (max-width: 768px) {
          .header {
            background-color: #0A0A0A !important;
            backdrop-filter: blur(20px);
            -webkit-backdrop-filter: blur(20px);
            height: 60px;
            border-bottom: 1px solid rgba(255, 255, 255, 0.05);
          }
          .profile-name-span, .nav-links, .profile-dropdown-wrapper {
            display: none !important;
          }
          .mobile-bottom-nav {
            display: flex;
            position: fixed;
            bottom: 0;
            left: 0;
            right: 0;
            height: 60px;
            background-color: rgba(10, 10, 10, 0.85);
            backdrop-filter: blur(10px);
            border-top: 1px solid var(--border);
            z-index: 100;
            justify-content: space-around;
            align-items: center;
            padding: 4px 0;
          }
          .mobile-nav-item {
            display: flex;
            flex-direction: column;
            align-items: center;
            text-decoration: none;
            color: var(--text-secondary);
            font-size: 0.65rem;
            font-weight: 500;
            gap: 4px;
            transition: var(--transition);
          }
          .mobile-nav-item:hover, .mobile-nav-item.active {
            color: white;
          }
          .mobile-nav-item.active {
            color: var(--primary);
            font-weight: 700;
          }
          .mobile-nav-avatar {
            width: 22px;
            height: 22px;
            border-radius: 4px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 800;
            font-size: 0.65rem;
            color: white;
            border: 1.5px solid transparent;
            overflow: hidden;
          }
          .mobile-nav-avatar img {
            width: 100%;
            height: 100%;
            object-fit: cover;
            border-radius: inherit;
          }
          .mobile-nav-item.active .mobile-nav-avatar {
            border-color: var(--primary);
          }
          .mobile-nav-avatar.avatar_orange { background: linear-gradient(135deg, #FF9900, #FF5E00); }
          .mobile-nav-avatar.avatar_blue { background: linear-gradient(135deg, #0070F3, #00C6FF); }
          .mobile-nav-avatar.avatar_green { background: linear-gradient(135deg, #00C851, #00E676); }
          .mobile-nav-avatar.avatar_pink { background: linear-gradient(135deg, #FF4081, #FF80AB); }
          .mobile-nav-avatar.avatar_purple { background: linear-gradient(135deg, #AA00FF, #E040FB); }
        }
      `}</style>
    </>
  );
}
