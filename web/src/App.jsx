import React from "react";
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from "react-router-dom";
import { AuthProvider, useAuth } from "./context/AuthContext";
import { ProfileProvider, useProfile } from "./context/ProfileContext";

// Components
import Header from "./components/Header";
import Footer from "./components/Footer";

// Pages
import HomePage from "./pages/HomePage";
import DetailPage from "./pages/DetailPage";
import PlayerPage from "./pages/PlayerPage";
import SearchPage from "./pages/SearchPage";
import WatchlistPage from "./pages/WatchlistPage";
import HistoryPage from "./pages/HistoryPage";
import SchedulePage from "./pages/SchedulePage";
import AuthPage from "./pages/AuthPage";
import ProfileSelection from "./pages/ProfileSelection";
import NewAndHotPage from "./pages/NewAndHotPage";
import MyAniStreamPage from "./pages/MyAniStreamPage";

function OrientationGuard() {
  const location = useLocation();
  const isWatchPage = location.pathname.startsWith("/watch");

  if (isWatchPage) return null;

  return (
    <div className="portrait-lock-overlay">
      <div className="portrait-lock-content">
        <div className="phone-icon-wrapper">
          <svg className="phone-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
            <rect x="5" y="2" width="14" height="20" rx="2" ry="2" />
            <line x1="12" y1="18" x2="12.01" y2="18" strokeWidth="3" strokeLinecap="round" />
          </svg>
          <svg className="rotate-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67" />
          </svg>
        </div>
        <h2>Please Rotate Your Device</h2>
        <p>AniStream is optimized for Portrait mode on mobile devices. Rotate your phone back to enjoy browsing.</p>
      </div>
      <style>{`
        .portrait-lock-overlay {
          display: none;
          position: fixed;
          inset: 0;
          z-index: 99999;
          background: rgba(10, 10, 10, 0.95);
          backdrop-filter: blur(20px);
          -webkit-backdrop-filter: blur(20px);
          align-items: center;
          justify-content: center;
          text-align: center;
          padding: 24px;
        }

        .portrait-lock-content {
          max-width: 320px;
          animation: scaleIn 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
        }

        .phone-icon-wrapper {
          position: relative;
          width: 80px;
          height: 80px;
          margin: 0 auto 24px;
        }

        .phone-icon {
          width: 100%;
          height: 100%;
          color: var(--text-primary);
          animation: rotatePhone 2.5s ease-in-out infinite;
        }

        .rotate-arrow {
          position: absolute;
          top: 15px;
          right: 15px;
          width: 28px;
          height: 28px;
          color: var(--primary);
          animation: spin 2.5s linear infinite;
        }

        .portrait-lock-content h2 {
          font-size: 1.5rem;
          font-weight: 700;
          color: var(--text-primary);
          margin-bottom: 12px;
        }

        .portrait-lock-content p {
          font-size: 0.9rem;
          color: var(--text-secondary);
          line-height: 1.5;
        }

        @keyframes rotatePhone {
          0%, 100% { transform: rotate(0deg); }
          40%, 60% { transform: rotate(-90deg); }
        }

        @keyframes spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(-360deg); }
        }

        @keyframes scaleIn {
          from { opacity: 0; transform: scale(0.9); }
          to { opacity: 1; transform: scale(1); }
        }

        @media screen and (orientation: landscape) and (max-width: 1024px) {
          .portrait-lock-overlay {
            display: flex;
          }
          body {
            overflow: hidden !important;
          }
        }
      `}</style>
    </div>
  );
}

// Route protection: requires authentication
function RequireAuth({ children }) {
  const { currentUser, loading } = useAuth();

  if (loading) {
    return <div className="loading-screen flex-center">Loading session...</div>;
  }

  if (!currentUser) {
    return <Navigate to="/auth" replace />;
  }

  return children;
}

// Route protection: requires authentication AND selected profile
function RequireProfile({ children }) {
  const { currentUser, loading: authLoading } = useAuth();
  const { activeProfile, loading: profileLoading } = useProfile();

  if (authLoading || profileLoading) {
    return <div className="loading-screen flex-center">Loading profiles...</div>;
  }

  if (!currentUser) {
    return <Navigate to="/auth" replace />;
  }

  if (!activeProfile) {
    return <Navigate to="/profiles" replace />;
  }

  return children;
}

// Standard App Shell Layout
function AppLayout({ children }) {
  return (
    <div className="app-layout">
      <Header />
      <main className="main-content">{children}</main>
      <Footer />
      <style>{`
        .app-layout {
          display: flex;
          flex-direction: column;
          min-height: 100vh;
        }
        .main-content {
          flex-grow: 1;
        }
        .loading-screen {
          height: 100vh;
          width: 100vw;
          background-color: var(--bg);
          font-size: 1.2rem;
          font-weight: 600;
          color: var(--text-secondary);
        }
      `}</style>
    </div>
  );
}

export default function App() {
  return (
    <Router>
      <AuthProvider>
        <ProfileProvider>
          <OrientationGuard />
          <Routes>
            {/* Auth routes */}
            <Route path="/auth" element={<AuthPage />} />
            
            {/* Profile Selection */}
            <Route 
              path="/profiles" 
              element={
                <RequireAuth>
                  <ProfileSelection />
                </RequireAuth>
              } 
            />

            {/* Protected Streaming Routes */}
            <Route 
              path="/" 
              element={
                <RequireProfile>
                  <AppLayout>
                    <HomePage />
                  </AppLayout>
                </RequireProfile>
              } 
            />
            
            <Route 
              path="/anime/:id" 
              element={
                <RequireProfile>
                  <AppLayout>
                    <DetailPage />
                  </AppLayout>
                </RequireProfile>
              } 
            />

            <Route 
              path="/watch/:animeId/:episodeId" 
              element={
                <RequireProfile>
                  <PlayerPage />
                </RequireProfile>
              } 
            />

            <Route 
              path="/search" 
              element={
                <RequireProfile>
                  <AppLayout>
                    <SearchPage />
                  </AppLayout>
                </RequireProfile>
              } 
            />

            <Route 
              path="/watchlist" 
              element={
                <RequireProfile>
                  <AppLayout>
                    <WatchlistPage />
                  </AppLayout>
                </RequireProfile>
              } 
            />

            <Route 
              path="/history" 
              element={
                <RequireProfile>
                  <AppLayout>
                    <HistoryPage />
                  </AppLayout>
                </RequireProfile>
              } 
            />

            <Route 
              path="/schedule" 
              element={
                <RequireProfile>
                  <AppLayout>
                    <SchedulePage />
                  </AppLayout>
                </RequireProfile>
              } 
            />

            <Route 
              path="/new-and-hot" 
              element={
                <RequireProfile>
                  <AppLayout>
                    <NewAndHotPage />
                  </AppLayout>
                </RequireProfile>
              } 
            />

            <Route 
              path="/my-anistream" 
              element={
                <RequireProfile>
                  <AppLayout>
                    <MyAniStreamPage />
                  </AppLayout>
                </RequireProfile>
              } 
            />

            {/* Catch-all Redirect */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </ProfileProvider>
      </AuthProvider>
    </Router>
  );
}
