import React from "react";

export function CardShimmer() {
  return (
    <div className="shimmer-card-loading shimmer-pulse">
      <div className="shimmer-poster shimmer"></div>
      <div className="shimmer-info-box">
        <div className="shimmer-text shimmer"></div>
        <div className="shimmer-subtext shimmer"></div>
      </div>
    </div>
  );
}

export function GridShimmer({ count = 6 }) {
  return (
    <div className="grid-layout">
      {Array.from({ length: count }).map((_, i) => (
        <CardShimmer key={i} />
      ))}
    </div>
  );
}

export function LandscapeCardShimmer() {
  return (
    <div className="shimmer-landscape-card shimmer-pulse">
      <div className="shimmer-landscape-inner shimmer"></div>
      <div className="shimmer-landscape-meta">
        <div className="shimmer-time-badge shimmer"></div>
        <div className="shimmer-landscape-title shimmer"></div>
      </div>
    </div>
  );
}

export function BillboardShimmer() {
  return (
    <div className="billboard-shimmer-loading shimmer shimmer-pulse" />
  );
}

export function DetailsShimmer() {
  return (
    <div className="details-shimmer shimmer-pulse">
      <div className="banner-placeholder-wrapper">
        <div className="banner-placeholder shimmer"></div>
        <div className="container banner-hero-container">
          <div className="poster-placeholder shimmer"></div>
          <div className="meta-placeholder">
            <div className="title-placeholder shimmer"></div>
            <div className="stats-placeholder-row">
              <div className="stat-pill-placeholder shimmer"></div>
              <div className="stat-pill-placeholder shimmer" style={{ width: '45px' }}></div>
              <div className="stat-pill-placeholder shimmer" style={{ width: '70px' }}></div>
            </div>
            <div className="desc-line shimmer" style={{ width: '100%' }}></div>
            <div className="desc-line shimmer" style={{ width: '95%' }}></div>
            <div className="desc-line shimmer" style={{ width: '75%' }}></div>
            <div className="btn-row-placeholder">
              <div className="btn-placeholder shimmer"></div>
              <div className="btn-placeholder shimmer" style={{ width: '130px', background: 'rgba(255,255,255,0.08)' }}></div>
            </div>
          </div>
        </div>
      </div>

      {/* Episode Grid Placeholder */}
      <div className="episodes-placeholder-section">
        <div className="section-title-placeholder shimmer"></div>
        <div className="episodes-grid-placeholder">
          {Array.from({ length: 12 }).map((_, i) => (
            <div key={i} className="episode-square-placeholder shimmer"></div>
          ))}
        </div>
      </div>

      {/* Recommendations Grid Placeholder */}
      <div className="recommendations-placeholder-section">
        <div className="section-title-placeholder shimmer" style={{ width: '220px' }}></div>
        <div className="rec-grid-placeholder">
          {Array.from({ length: 4 }).map((_, i) => (
            <CardShimmer key={i} />
          ))}
        </div>
      </div>
    </div>
  );
}
