import { FormEvent, useEffect, useMemo, useState } from 'react';
import { API_BASE_URL, RECOMMENDATION_LIMIT } from './config';
import { RecommendationCard } from './components/RecommendationCard';
import { QueuePanel } from './components/QueuePanel';
import { PlaylistResponse, RecommendationResponse, RecommendationTrackView } from './types';
import './App.css';

type StoredAuth = {
  userId: string;
  displayName?: string | null;
  spotifyId?: string | null;
};

const STORAGE_KEY = 'music-similarity-auth';

const defaultPlaylistName = () => `AI Recs ${new Date().toLocaleDateString()}`;

function App() {
  const [userId, setUserId] = useState('');
  const [seedInput, setSeedInput] = useState('');
  const [limit, setLimit] = useState(RECOMMENDATION_LIMIT);

  const [recommendations, setRecommendations] = useState<RecommendationTrackView[]>([]);
  const [queue, setQueue] = useState<RecommendationTrackView[]>([]);
  const [playlistName, setPlaylistName] = useState(defaultPlaylistName);

  const [seedMeta, setSeedMeta] = useState<RecommendationResponse['seed'] | null>(null);
  const [strategy, setStrategy] = useState('');
  const [userProfile, setUserProfile] = useState<{ displayName?: string | null; spotifyId?: string | null } | null>(null);

  const [loading, setLoading] = useState(false);
  const [playlistSaving, setPlaylistSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const queueIds = useMemo(() => new Set(queue.map((track) => track.spotifyId)), [queue]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const userIdParam = params.get('userId');
    if (userIdParam) {
      const payload: StoredAuth = {
        userId: userIdParam,
        displayName: params.get('displayName'),
        spotifyId: params.get('spotifyId')
      };
      setUserId(payload.userId);
      setUserProfile({ displayName: payload.displayName, spotifyId: payload.spotifyId });
      localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
      ['userId', 'displayName', 'spotifyId'].forEach((key) => params.delete(key));
      const search = params.toString();
      const newUrl = `${window.location.pathname}${search ? `?${search}` : ''}${window.location.hash}`;
      window.history.replaceState(null, '', newUrl);
      return;
    }
    const cached = localStorage.getItem(STORAGE_KEY);
    if (cached) {
      try {
        const parsed: StoredAuth = JSON.parse(cached);
        if (parsed.userId) {
          setUserId(parsed.userId);
          setUserProfile({ displayName: parsed.displayName, spotifyId: parsed.spotifyId });
        }
      } catch {
        localStorage.removeItem(STORAGE_KEY);
      }
    }
  }, []);

  useEffect(() => {
    if (!userId) {
      localStorage.removeItem(STORAGE_KEY);
      return;
    }
    const payload: StoredAuth = {
      userId,
      displayName: userProfile?.displayName ?? null,
      spotifyId: userProfile?.spotifyId ?? null
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
  }, [userId, userProfile]);

  async function handleFetch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSuccess(null);
    if (!userId.trim()) {
      setError('User ID is required. Click Connect Spotify first.');
      return;
    }
    if (!seedInput.trim()) {
      setError('Paste a Spotify track ID or URL to continue.');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const params = new URLSearchParams({
        userId: userId.trim(),
        seed: seedInput.trim(),
        limit: String(limit)
      });
      const response = await fetch(`${API_BASE_URL}/recommend?${params.toString()}`);
      if (!response.ok) {
        throw new Error(`API returned ${response.status}`);
      }
      const payload: RecommendationResponse = await response.json();
      setRecommendations(payload.items);
      setSeedMeta(payload.seed);
      setStrategy(payload.strategy);
      setQueue([]);
      setSuccess(`Loaded ${payload.items.length} recommendations`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to load recommendations');
    } finally {
      setLoading(false);
    }
  }

  function addToQueue(track: RecommendationTrackView) {
    if (queueIds.has(track.spotifyId)) {
      return;
    }
    setQueue((prev) => [...prev, track]);
  }

  function removeFromQueue(spotifyId: string) {
    setQueue((prev) => prev.filter((track) => track.spotifyId !== spotifyId));
  }

  function handleConnectSpotify() {
    const redirectTarget = `${window.location.origin}${window.location.pathname}`;
    const loginUrl = `${API_BASE_URL}/auth/login?redirect=${encodeURIComponent(redirectTarget)}`;
    window.location.href = loginUrl;
  }

  async function handleSavePlaylist() {
    if (!userId.trim()) {
      setError('User ID is required to save playlists.');
      return;
    }
    if (queue.length === 0) {
      setError('Add at least one track to the queue.');
      return;
    }
    setPlaylistSaving(true);
    setError(null);
    setSuccess(null);

    try {
      const response = await fetch(`${API_BASE_URL}/playlist`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          userId: userId.trim(),
          name: playlistName.trim() || defaultPlaylistName(),
          trackIds: queue.map((track) => track.spotifyId),
          publicPlaylist: false
        })
      });
      if (!response.ok) {
        throw new Error(`Playlist API returned ${response.status}`);
      }
      const payload: PlaylistResponse = await response.json();
      setSuccess(`Playlist saved! ${payload.spotifyUrl ?? ''}`.trim());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to save playlist');
    } finally {
      setPlaylistSaving(false);
    }
  }

  return (
    <div className="app-shell">
      <header>
        <div>
          <p className="eyebrow">Music Similarity Studio</p>
          <h1>Paste a track. Get instant vibes.</h1>
          <p className="subtitle">
            We call Last.fm + Spotify for you. Queue your favorite matches and save them as a playlist in two clicks.
          </p>
        </div>
      </header>

      <main>
        <section className="panel">
          <form className="form" onSubmit={handleFetch}>
            <label>
              User ID
              <div className="field-with-action">
                <input
                  type="text"
                  placeholder="Auto-filled after Spotify login"
                  value={userId}
                  onChange={(event) => setUserId(event.target.value)}
                />
                <button type="button" className="secondary" onClick={handleConnectSpotify}>
                  {userId ? 'Reconnect' : 'Connect Spotify'}
                </button>
              </div>
              <span className="form-hint">We will drop your user ID in here after you authorize Spotify.</span>
              {userProfile?.displayName && (
                <span className="form-hint form-hint--success">
                  Connected as {userProfile.displayName}
                  {userProfile.spotifyId ? ` (${userProfile.spotifyId})` : ''}
                </span>
              )}
            </label>

            <label>
              Spotify track link or ID
              <input
                type="text"
                placeholder="https://open.spotify.com/track/..."
                value={seedInput}
                onChange={(event) => setSeedInput(event.target.value)}
              />
            </label>

            <label>
              How many recs?
              <input
                type="number"
                min={5}
                max={50}
                value={limit}
                onChange={(event) => setLimit(Number(event.target.value))}
              />
            </label>

            <button type="submit" className="primary" disabled={loading}>
              {loading ? 'Fetching...' : 'Get recommendations'}
            </button>
          </form>

          {error && <p className="alert alert--error">{error}</p>}
          {success && <p className="alert alert--success">{success}</p>}

          {seedMeta && (
            <div className="seed-meta">
              <img
                src={seedMeta.imageUrl || `https://via.placeholder.com/220?text=${encodeURIComponent(seedMeta.name)}`}
                alt={`${seedMeta.name} cover art`}
                className="seed-meta__art"
                loading="lazy"
              />
              <div className="seed-meta__content">
                <p className="eyebrow">Seed track</p>
                <h2>{seedMeta.name}</h2>
                <p className="subtitle">{seedMeta.artist}</p>
                {strategy && <p className="seed-meta__strategy">Strategy: {strategy}</p>}
              </div>
            </div>
          )}
        </section>

        <section className="layout">
          <div className="recommendations">
            {recommendations.length === 0 && (
              <p className="placeholder">Run a recommendation to see top matches.</p>
            )}
            {recommendations.map((track) => (
              <RecommendationCard
                key={track.spotifyId}
                track={track}
                onAdd={addToQueue}
                disabled={queueIds.has(track.spotifyId)}
              />
            ))}
          </div>

          <aside>
            <QueuePanel tracks={queue} onRemove={removeFromQueue} />

            <div className="playlist">
              <label>
                Playlist name
                <input
                  type="text"
                  value={playlistName}
                  onChange={(event) => setPlaylistName(event.target.value)}
                />
              </label>
              <button className="primary" disabled={playlistSaving} onClick={handleSavePlaylist}>
                {playlistSaving ? 'Saving…' : 'Save as playlist'}
              </button>
            </div>
          </aside>
        </section>
      </main>

    </div>
  );
}

export default App;
