import { FormEvent, useEffect, useMemo, useState } from 'react';
import { API_BASE_URL, RECOMMENDATION_LIMIT } from './config';
import { RecommendationCard } from './components/RecommendationCard';
import { QueuePanel } from './components/QueuePanel';
import { useSpotifyPlayback } from './hooks/useSpotifyPlayback';
import { PlaylistResponse, RecommendationResponse, RecommendationTrackView, SeedTrackView, SeedsResponse } from './types';
import './App.css';

type StoredAuth = {
  userId: string;
  displayName?: string | null;
  spotifyId?: string | null;
  product?: string | null;
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
  const [userProfile, setUserProfile] = useState<{ displayName?: string | null; spotifyId?: string | null; product?: string | null } | null>(null);
  const [seedCandidates, setSeedCandidates] = useState<SeedTrackView[]>([]);
  const [playbackEnabled, setPlaybackEnabled] = useState(false);
  const [playingTrackId, setPlayingTrackId] = useState<string | null>(null);
  const [seedLoading, setSeedLoading] = useState(false);

  const [loading, setLoading] = useState(false);
  const [playlistSaving, setPlaylistSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const queueIds = useMemo(() => new Set(queue.map((track) => track.spotifyId)), [queue]);
  const playback = useSpotifyPlayback({ enabled: playbackEnabled, userId: userId ? userId : null });
  const canUsePlayback = playbackEnabled && playback.status === 'ready';
  const playbackToggleDisabled = !userId || playback.status === 'loading';
  const playbackStatusMessage = useMemo(() => {
    if (!userId) {
      return 'Connect Spotify to enable playback.';
    }
    if (playback.error) {
      return playback.error;
    }
    switch (playback.status) {
      case 'needs-user':
        return 'Connect Spotify to enable playback.';
      case 'loading':
        return 'Connecting to the Spotify player…';
      case 'ready':
        return 'Player ready. Press play on any track below.';
      case 'error':
        return 'Playback unavailable at the moment.';
      case 'disabled':
      default:
        return 'Toggle playback to listen without leaving the page.';
    }
  }, [userId, playback.status, playback.error]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const userIdParam = params.get('userId');
    if (userIdParam) {
      const payload: StoredAuth = {
        userId: userIdParam,
        displayName: params.get('displayName'),
        spotifyId: params.get('spotifyId'),
        product: params.get('product')
      };
      setUserId(payload.userId);
      setUserProfile({ displayName: payload.displayName, spotifyId: payload.spotifyId, product: payload.product });
      localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
      ['userId', 'displayName', 'spotifyId', 'product'].forEach((key) => params.delete(key));
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
          setUserProfile({ displayName: parsed.displayName, spotifyId: parsed.spotifyId, product: parsed.product });
        }
      } catch {
        localStorage.removeItem(STORAGE_KEY);
      }
    }
  }, []);

  useEffect(() => {
    if (!userId) {
      localStorage.removeItem(STORAGE_KEY);
      setSeedCandidates([]);
      setPlaybackEnabled(false);
      return;
    }
    const payload: StoredAuth = {
      userId,
      displayName: userProfile?.displayName ?? null,
      spotifyId: userProfile?.spotifyId ?? null,
      product: userProfile?.product ?? null
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
  }, [userId, userProfile]);

  useEffect(() => {
    if (!playbackEnabled) {
      setPlayingTrackId(null);
    }
  }, [playbackEnabled]);

  useEffect(() => {
    if (playback.status !== 'ready') {
      setPlayingTrackId(null);
    }
  }, [playback.status]);

  useEffect(() => {
    if (!playback.product) {
      return;
    }
    setUserProfile((prev) => {
      if (!prev) {
        return prev;
      }
      if (prev.product === playback.product) {
        return prev;
      }
      return { ...prev, product: playback.product };
    });
  }, [playback.product]);

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
      setSeedCandidates([]);
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

  function handlePlaybackToggle(next: boolean) {
    setSuccess(null);
    setError(null);
    setPlaybackEnabled(next);
  }

  async function handlePlayTrack(track: RecommendationTrackView) {
    if (!playbackEnabled) {
      setError('Enable playback to listen without leaving the page.');
      return;
    }
    if (!canUsePlayback) {
      setError('Playback is not ready yet. Wait a moment and try again.');
      return;
    }
    setSuccess(null);
    try {
      setError(null);
      if (playingTrackId === track.spotifyId) {
        await playback.pause();
        setPlayingTrackId(null);
      } else {
        await playback.play(track.spotifyId);
        setPlayingTrackId(track.spotifyId);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to control playback');
    }
  }

  function handleConnectSpotify() {
    const redirectTarget = `${window.location.origin}${window.location.pathname}`;
    const loginUrl = `${API_BASE_URL}/auth/login?redirect=${encodeURIComponent(redirectTarget)}`;
    window.location.href = loginUrl;
  }

  async function handleFetchSeeds() {
    setSuccess(null);
    if (!userId.trim()) {
      setError('User ID is required. Click Connect Spotify first.');
      return;
    }
    setError(null);
    setSeedLoading(true);
    try {
      const params = new URLSearchParams({ userId: userId.trim(), limit: '20' });
      const response = await fetch(`${API_BASE_URL}/me/seeds?${params.toString()}`);
      if (!response.ok) {
        throw new Error(`Seeds API returned ${response.status}`);
      }
      const payload: SeedsResponse = await response.json();
      setSeedCandidates(payload.items);
      if (payload.items.length === 0) {
        setError('Spotify did not return any seed candidates. Try again later.');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to fetch seed tracks');
    } finally {
      setSeedLoading(false);
    }
  }

  function handleSelectSeed(track: SeedTrackView) {
    setSeedInput(track.id);
    setError(null);
    setSuccess(`Seed ready: ${track.name} — ${track.artist}`);
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
              <div className="seed-actions">
                <button type="button" className="secondary" onClick={handleFetchSeeds} disabled={seedLoading}>
                  {seedLoading ? 'Loading top tracks…' : 'Use my top tracks'}
                </button>
              </div>
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

          <div className="playback-panel">
            <label className="toggle playback-toggle">
              <input
                type="checkbox"
                checked={playbackEnabled}
                disabled={playbackToggleDisabled}
                onChange={(event) => handlePlaybackToggle(event.target.checked)}
              />
              <span>Play recommendations here (Spotify Premium required)</span>
            </label>
            <p className="form-hint">{playbackStatusMessage}</p>
          </div>

          {error && <p className="alert alert--error">{error}</p>}
          {success && <p className="alert alert--success">{success}</p>}

          {seedCandidates.length > 0 && (
            <div className="seed-picker">
              <p className="eyebrow">Pick a seed from your Spotify profile</p>
              <p className="seed-picker__hint">We fetched your top and recent tracks. Choose one to populate the seed field.</p>
              <div className="seed-picker__grid">
                {seedCandidates.map((track) => (
                  <button
                    type="button"
                    key={track.id}
                    className="seed-card"
                    onClick={() => handleSelectSeed(track)}
                  >
                    <img
                      src={track.imageUrl || `https://via.placeholder.com/120?text=${encodeURIComponent(track.name)}`}
                      alt={track.name}
                      className="seed-card__art"
                      loading="lazy"
                    />
                    <div className="seed-card__body">
                      <span className="seed-card__title">{track.name}</span>
                      <span className="seed-card__subtitle">{track.artist}</span>
                    </div>
                  </button>
                ))}
              </div>
            </div>
          )}

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
                onPlay={() => handlePlayTrack(track)}
                playDisabled={!canUsePlayback}
                isPlaying={playingTrackId === track.spotifyId}
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
