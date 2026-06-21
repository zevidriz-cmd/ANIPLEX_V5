import React, { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import { Play, Info, ChevronLeft, ChevronRight, Bookmark } from "lucide-react";

export default function Carousel({ items }) {
  const [currentIndex, setCurrentIndex] = useState(0);

  useEffect(() => {
    if (!items || items.length === 0) return;
    const interval = setInterval(() => {
      setCurrentIndex((prev) => (prev + 1) % items.length);
    }, 7000); // Auto-slide every 7 seconds
    return () => clearInterval(interval);
  }, [items]);

  if (!items || items.length === 0) return null;

  const currentItem = items[currentIndex];

  const handlePrev = () => {
    setCurrentIndex((prev) => (prev - 1 + items.length) % items.length);
  };

  const handleNext = () => {
    setCurrentIndex((prev) => (prev + 1) % items.length);
  };

  return (
    <div 
      className="billboard fade-in" 
      style={{ backgroundImage: `url(${currentItem.poster})` }}
    >
      <button className="carousel-control prev" onClick={handlePrev}>
        <ChevronLeft size={32} />
      </button>
      <button className="carousel-control next" onClick={handleNext}>
        <ChevronRight size={32} />
      </button>

      <div className="container billboard-inner">
        <div className="billboard-content">
          <div className="spotlight-badge"># {currentItem.rank} Spotlight</div>
          <h1 className="spotlight-title">{currentItem.name}</h1>
          
          <div className="spotlight-meta">
            {currentItem.otherInfo?.map((info, i) => (
              <span key={i} className="meta-item">{info}</span>
            ))}
          </div>

          <p className="spotlight-desc">{currentItem.description}</p>

          <div className="spotlight-actions">
            <Link to={`/anime/${currentItem.id}`} className="btn btn-primary" style={{ padding: "0.85rem 2rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
              <Play size={18} fill="white" /> Start Watching E1
            </Link>
            <Link 
              to={`/anime/${currentItem.id}`} 
              className="btn btn-secondary" 
              title="Add to Watchlist"
              style={{ padding: "0.85rem", width: "48px", height: "48px", display: "inline-flex", alignItems: "center", justifyContent: "center" }}
            >
              <Bookmark size={18} />
            </Link>
          </div>
        </div>
      </div>

      <div className="carousel-dots dot-bar-container">
        {items.map((_, index) => (
          <button 
            key={index} 
            className={`dot-bar ${index === currentIndex ? "active" : ""}`}
            onClick={() => setCurrentIndex(index)}
            aria-label={`Go to slide ${index + 1}`}
          ></button>
        ))}
      </div>


      <style>{`
        .billboard-inner {
          width: 100%;
          position: relative;
          z-index: 10;
        }
        .carousel-control {
          position: absolute;
          top: 50%;
          transform: translateY(-50%);
          background: rgba(0, 0, 0, 0.5);
          border: none;
          color: white;
          width: 50px;
          height: 50px;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          cursor: pointer;
          opacity: 0;
          transition: var(--transition);
          z-index: 20;
        }
        .billboard:hover .carousel-control {
          opacity: 0.8;
        }
        .carousel-control:hover {
          opacity: 1 !important;
          background: var(--primary);
        }
        .carousel-control.prev { left: 2%; }
        .carousel-control.next { right: 2%; }
        
        .spotlight-badge {
          background-color: var(--primary);
          color: white;
          padding: 4px 8px;
          font-size: 0.8rem;
          font-weight: 800;
          display: inline-block;
          border-radius: 4px;
          margin-bottom: 1rem;
          text-transform: uppercase;
          letter-spacing: 0.05em;
        }
        .spotlight-title {
          font-size: clamp(2rem, 5vw, 3.5rem);
          font-weight: 800;
          line-height: 1.1;
          margin-bottom: 1rem;
          text-shadow: 2px 2px 4px rgba(0,0,0,0.8);
          overflow: hidden;
          text-overflow: ellipsis;
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
        }
        .spotlight-meta {
          display: flex;
          flex-wrap: wrap;
          gap: 1rem;
          margin-bottom: 1.2rem;
          align-items: center;
        }
        .meta-item {
          background-color: rgba(255, 255, 255, 0.15);
          backdrop-filter: blur(5px);
          padding: 3px 10px;
          font-size: 0.8rem;
          font-weight: 600;
          border-radius: 4px;
          border: 1px solid rgba(255, 255, 255, 0.05);
        }
        .spotlight-desc {
          font-size: 0.95rem;
          line-height: 1.5;
          color: var(--text-secondary);
          margin-bottom: 2rem;
          max-width: 600px;
          text-shadow: 1px 1px 2px rgba(0,0,0,0.8);
          overflow: hidden;
          text-overflow: ellipsis;
          display: -webkit-box;
          -webkit-line-clamp: 3;
          -webkit-box-orient: vertical;
        }
        .spotlight-actions {
          display: flex;
          gap: 1rem;
        }
        .carousel-dots {
          position: absolute;
          bottom: 20px;
          right: 4%;
          display: flex;
          gap: 8px;
          z-index: 20;
        }
        .dot {
          width: 8px;
          height: 8px;
          border-radius: 50%;
          background: rgba(255, 255, 255, 0.4);
          border: none;
          cursor: pointer;
          transition: var(--transition);
        }
        .dot.active {
          background: var(--primary);
          width: 24px;
          border-radius: 4px;
        }
        @media (max-width: 768px) {
          .billboard {
            height: 55vh;
            padding-bottom: 2rem;
          }
          .billboard::before {
            background: linear-gradient(
              to bottom,
              rgba(10, 10, 10, 0.1) 0%,
              rgba(10, 10, 10, 0.6) 60%,
              rgba(10, 10, 10, 1) 100%
            ) !important;
          }
          .carousel-control { display: none; }
          .spotlight-badge {
            font-size: 0.7rem;
            padding: 3px 6px;
            margin-bottom: 0.6rem;
          }
          .spotlight-title {
            font-size: 1.8rem;
            margin-bottom: 0.6rem;
          }
          .spotlight-meta {
            gap: 0.5rem;
            margin-bottom: 0.8rem;
          }
          .meta-item {
            font-size: 0.7rem;
            padding: 2px 6px;
          }
          .spotlight-desc {
            font-size: 0.85rem;
            margin-bottom: 1.2rem;
            max-width: 100%;
            -webkit-line-clamp: 2;
          }
          .spotlight-actions {
            gap: 8px;
          }
          .spotlight-actions .btn {
            padding: 8px 16px;
            font-size: 0.85rem;
          }
        }
      `}</style>
    </div>
  );
}
