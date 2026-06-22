import React, { useState, useEffect, useRef } from "react";
import { Link } from "react-router-dom";
import { Play, Info, Trash2, MoreVertical } from "lucide-react";

export default function AnimeCard({ 
  id, 
  name, 
  poster, 
  type, 
  episodes, 
  rating,
  progressPercent,
  episodeInfo,
  onRemove,
  episodeId,
  audioCategory,
  onClick
}) {
  const subCount = episodes?.sub;
  const dubCount = episodes?.dub;

  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);

  useEffect(() => {
    function handleClickOutside(event) {
      if (menuRef.current && !menuRef.current.contains(event.target)) {
        setMenuOpen(false);
      }
    }
    if (menuOpen) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [menuOpen]);

  const handleToggleMenu = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setMenuOpen(!menuOpen);
  };

  const handleRemoveClick = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setMenuOpen(false);
    if (onRemove) {
      onRemove(id);
    }
  };

  return (
    <div className="anime-card" onClick={onClick}>
      <Link 
        to={episodeId ? `/watch/${id}/${episodeId}${audioCategory ? `?audio=${audioCategory}` : ""}` : `/anime/${id}`} 
        className="anime-card-link"
      >
        <div className="poster-wrapper">
          <img src={poster} alt={name} loading="lazy" className="poster-img" />
          <div className="poster-overlay">
            <span className="play-icon-circle">
              <Play size={20} fill="white" />
            </span>
          </div>
          
          {type && <span className="type-badge">{type}</span>}
          {rating && <span className="rating-badge">★ {rating}</span>}
          
          <div className="episodes-badges">
            {subCount !== undefined && subCount !== null && (
              <span className="badge sub">SUB {subCount}</span>
            )}
            {dubCount !== undefined && dubCount !== null && (
              <span className="badge dub">DUB {dubCount}</span>
            )}
          </div>

          {progressPercent !== undefined && (
            <div className="progress-bar-container">
              <div 
                className="progress-bar-fill" 
                style={{ width: `${progressPercent}%` }}
              ></div>
            </div>
          )}
        </div>
        
        <div className="card-info">
          <h3 className="anime-name" title={name}>{name}</h3>
          {episodeInfo && <p className="episode-info">{episodeInfo}</p>}
        </div>
      </Link>
      
      {onRemove && (
        <div className="card-options-wrapper" ref={menuRef}>
          <button 
            onClick={handleToggleMenu} 
            className="options-trigger-btn"
            title="Options"
          >
            <MoreVertical size={16} />
          </button>
          {menuOpen && (
            <div className="card-options-menu">
              {episodeId ? (
                <Link 
                  to={`/watch/${id}/${episodeId}${audioCategory ? `?audio=${audioCategory}` : ''}`} 
                  className="menu-item-link"
                  onClick={() => setMenuOpen(false)}
                >
                  <Play size={12} fill="white" /> Resume
                </Link>
              ) : (
                <Link 
                  to={`/anime/${id}`} 
                  className="menu-item-link"
                  onClick={() => setMenuOpen(false)}
                >
                  <Play size={12} fill="white" /> Play
                </Link>
              )}
              <Link 
                to={`/anime/${id}`} 
                className="menu-item-link"
                onClick={() => setMenuOpen(false)}
              >
                <Info size={12} /> Info
              </Link>
              <button onClick={handleRemoveClick} className="menu-item-btn remove">
                <Trash2 size={12} /> Remove
              </button>
            </div>
          )}
        </div>
      )}

      <style>{`
        .anime-card {
          position: relative;
          background-color: var(--bg-card);
          border-radius: 8px;
          overflow: hidden;
          transition: var(--transition);
          box-shadow: 0 4px 10px rgba(0, 0, 0, 0.3);
        }
        .anime-card:hover {
          transform: translateY(-5px) scale(1.03);
          box-shadow: 0 10px 20px rgba(0, 0, 0, 0.5);
        }
        .anime-card-link {
          text-decoration: none;
          color: inherit;
          display: flex;
          flex-direction: column;
          height: 100%;
        }
        .poster-wrapper {
          position: relative;
          width: 100%;
          padding-top: 142%; /* 1:1.42 aspect ratio */
          background-color: #1a1a1a;
          overflow: hidden;
        }
        .poster-img {
          position: absolute;
          top: 0;
          left: 0;
          width: 100%;
          height: 100%;
          object-fit: cover;
          transition: transform 0.5s ease;
        }
        .anime-card:hover .poster-img {
          transform: scale(1.05);
        }
        .poster-overlay {
          position: absolute;
          inset: 0;
          background: rgba(0, 0, 0, 0.4);
          opacity: 0;
          display: flex;
          align-items: center;
          justify-content: center;
          transition: var(--transition);
          z-index: 2;
        }
        .anime-card:hover .poster-overlay {
          opacity: 1;
        }
        .play-icon-circle {
          width: 44px;
          height: 44px;
          border-radius: 50%;
          background-color: var(--primary);
          display: flex;
          align-items: center;
          justify-content: center;
          color: white;
          transform: scale(0.8);
          transition: transform 0.25s cubic-bezier(0.175, 0.885, 0.32, 1.275);
        }
        .anime-card:hover .play-icon-circle {
          transform: scale(1);
        }
        .type-badge {
          position: absolute;
          top: 8px;
          left: 8px;
          background-color: rgba(10, 10, 10, 0.8);
          color: white;
          padding: 2px 6px;
          font-size: 0.7rem;
          font-weight: 700;
          border-radius: 4px;
          z-index: 3;
        }
        .rating-badge {
          position: absolute;
          top: 8px;
          right: 8px;
          background-color: rgba(245, 166, 35, 0.95);
          color: #0a0a0a;
          padding: 2px 6px;
          font-size: 0.7rem;
          font-weight: 800;
          border-radius: 4px;
          z-index: 3;
        }
        .episodes-badges {
          position: absolute;
          bottom: 8px;
          left: 8px;
          display: flex;
          gap: 4px;
          z-index: 3;
        }
        .badge {
          font-size: 0.65rem;
          font-weight: 700;
          padding: 1px 4px;
          border-radius: 3px;
        }
        .badge.sub {
          background-color: #0070F3;
          color: white;
        }
        .badge.dub {
          background-color: var(--secondary);
          color: #0a0a0a;
        }
        .progress-bar-container {
          position: absolute;
          bottom: 0;
          left: 0;
          right: 0;
          height: 4px;
          background-color: rgba(255, 255, 255, 0.2);
          z-index: 4;
        }
        .progress-bar-fill {
          height: 100%;
          background-color: var(--primary);
        }
        .card-info {
          padding: 10px;
          display: flex;
          flex-direction: column;
          flex-grow: 1;
        }
        .anime-name {
          font-size: 0.85rem;
          font-weight: 600;
          line-height: 1.25rem;
          color: var(--text-primary);
          overflow: hidden;
          text-overflow: ellipsis;
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          margin-bottom: 2px;
          padding-right: 18px; /* Avoid overlapping the 3-dot trigger button */
        }
        .episode-info {
          font-size: 0.75rem;
          color: var(--text-secondary);
          margin-top: 2px;
        }
        .card-options-wrapper {
          position: absolute;
          bottom: 8px;
          right: 8px;
          z-index: 10;
        }
        .options-trigger-btn {
          background: rgba(0, 0, 0, 0.7);
          border: 1px solid rgba(255, 255, 255, 0.1);
          color: var(--text-secondary);
          border-radius: 50%;
          width: 28px;
          height: 28px;
          display: flex;
          align-items: center;
          justify-content: center;
          cursor: pointer;
          transition: var(--transition);
        }
        .options-trigger-btn:hover {
          color: white;
          background: rgba(255, 255, 255, 0.15);
          border-color: rgba(255, 255, 255, 0.25);
        }
        .card-options-menu {
          position: absolute;
          bottom: calc(100% + 6px);
          right: 0;
          background: #141414;
          border: 1px solid var(--border);
          border-radius: 8px;
          padding: 4px;
          display: flex;
          flex-direction: column;
          gap: 2px;
          min-width: 120px;
          box-shadow: 0 5px 25px rgba(0,0,0,0.6);
          z-index: 20;
          animation: fadeIn 0.15s ease-out;
        }
        .menu-item-link, .menu-item-btn {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 6px 8px;
          color: var(--text-secondary);
          text-decoration: none;
          font-size: 0.75rem;
          font-weight: 600;
          background: none;
          border: none;
          border-radius: 4px;
          width: 100%;
          text-align: left;
          cursor: pointer;
          font-family: var(--font-family);
          transition: var(--transition);
        }
        .menu-item-link:hover, .menu-item-btn:hover {
          background: rgba(255, 255, 255, 0.05);
          color: white;
        }
        .menu-item-btn.remove:hover {
          background: rgba(229, 9, 20, 0.1);
          color: var(--primary);
        }
      `}</style>
    </div>
  );
}
