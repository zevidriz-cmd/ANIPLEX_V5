import React, { useEffect, useState } from "react";
import { collection, query, orderBy, getDocs, doc, deleteDoc, writeBatch } from "firebase/firestore";
import { db } from "../config/firebase";
import { useAuth } from "../context/AuthContext";
import { useProfile } from "../context/ProfileContext";
import AnimeCard from "../components/AnimeCard";
import { GridShimmer } from "../components/Shimmer";
import { History, Trash2 } from "lucide-react";
import { Link } from "react-router-dom";

export default function HistoryPage() {
  const { currentUser } = useAuth();
  const { activeProfile } = useProfile();
  
  const [historyList, setHistoryList] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchHistory = async () => {
    if (!currentUser || !activeProfile) return;
    setLoading(true);
    try {
      const q = query(
        collection(db, "users", currentUser.uid, "profiles", activeProfile.id, "history"),
        orderBy("updatedAt", "desc")
      );
      const snap = await getDocs(q);
      const list = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      setHistoryList(list);
    } catch (e) {
      console.error("Error fetching watch history:", e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchHistory();
  }, [currentUser, activeProfile]);

  const handleRemoveItem = async (animeId) => {
    if (!currentUser || !activeProfile) return;
    const docRef = doc(db, "users", currentUser.uid, "profiles", activeProfile.id, "history", animeId);
    try {
      await deleteDoc(docRef);
      setHistoryList(historyList.filter(item => item.animeId !== animeId));
    } catch (e) {
      console.error(e);
    }
  };

  const handleClearAll = async () => {
    if (!currentUser || !activeProfile || historyList.length === 0) return;
    if (!window.confirm("Are you sure you want to clear your entire watch history? This cannot be undone.")) return;

    try {
      const batch = writeBatch(db);
      historyList.forEach((item) => {
        const docRef = doc(db, "users", currentUser.uid, "profiles", activeProfile.id, "history", item.animeId);
        batch.delete(docRef);
      });
      await batch.commit();
      setHistoryList([]);
    } catch (e) {
      console.error("Failed to clear watch history:", e);
    }
  };

  return (
    <div className="history-page fade-in container">
      <div className="history-header">
        <h1 className="page-title"><History size={24} color="var(--primary)" /> Watch History</h1>
        {!loading && historyList.length > 0 && (
          <div className="history-actions-row">
            <button className="btn btn-secondary clear-hist-btn" onClick={handleClearAll}>
              <Trash2 size={16} /> Clear All
            </button>
          </div>
        )}
      </div>

      {loading ? (
        <GridShimmer count={6} />
      ) : historyList.length > 0 ? (
        <div className="grid-layout">
          {historyList.map((item) => {
            const percent = item.totalDuration > 0 ? (item.progressPosition / item.totalDuration) * 100 : 0;
            
            let progressText = `Episode ${item.episodeNumber}`;
            if (item.totalDuration > 0) {
              const remainingMin = Math.floor((item.totalDuration - item.progressPosition) / 60000);
              progressText += ` • ${remainingMin > 0 ? `${remainingMin}m remaining` : "Finished"}`;
            } else {
              progressText += ` • Resume`;
            }

            return (
              <AnimeCard 
                key={item.animeId}
                id={item.animeId}
                name={item.animeTitle}
                poster={item.poster}
                progressPercent={percent}
                episodeInfo={progressText}
                onRemove={handleRemoveItem}
                episodeId={item.episodeId}
                audioCategory={item.audioCategory}
              />
            );
          })}
        </div>
      ) : (
        <div className="history-empty text-center flex-center">
          <div>
            <History size={54} className="empty-icon" style={{ margin: "0 auto 1.5rem" }} />
            <h2>No watch history</h2>
            <p className="text-muted" style={{ margin: "0.5rem 0 1.5rem" }}>
              Episodes you watch on AniStream will show up here so you can easily resume playing.
            </p>
            <Link to="/" className="btn btn-primary">
              Browse Anime
            </Link>
          </div>
        </div>
      )}

      <style>{`
        .history-page {
          padding-top: calc(var(--header-height) + 20px);
          min-height: 100vh;
        }
        .history-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
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
        .clear-hist-btn {
          color: #ff8080;
          border: 1px solid rgba(255, 128, 128, 0.15);
          font-size: 0.85rem;
          padding: 0.4rem 1rem;
        }
        .clear-hist-btn:hover {
          background-color: rgba(229, 9, 20, 0.1);
          border-color: rgba(229, 9, 20, 0.3);
        }
        .history-empty {
          min-height: 50vh;
        }
        .empty-icon {
          color: var(--text-muted);
        }
      `}</style>
    </div>
  );
}
