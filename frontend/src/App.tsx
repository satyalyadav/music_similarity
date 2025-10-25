import { FormEvent, useMemo, useState } from 'react';
import { API_BASE_URL, RECOMMENDATION_LIMIT } from './config';
import { RecommendationCard } from './components/RecommendationCard';
import { QueuePanel } from './components/QueuePanel';
import { useSpotifyPlayback } from './hooks/useSpotifyPlayback';
import { PlaylistResponse, RecommendationResponse, RecommendationTrackView } from './types';
import './App.css';

const defaultPlaylistName = () => `AI Recs ${new Date().toLocaleDateString()}`;

function App() {
  const [userId, setUserId] = useState('');
  const [seedInput, setSeedInput] = useState('');
  const [limit, setLimit] = useState(RECOMMENDATION_LIMIT);
  const [isPremium, setIsPremium] = useState(false);
  const [playHere, setPlayHere] = useState(false);

  const [recommendations, setRecommendations] = useState<RecommendationTrackView[]>([]);
  const [queue, setQueue] = useState<RecommendationTrackView[]>([]);
  const [playlistName, setPlaylistName] = useState(defaultPlaylistName);

  const [seedMeta, setSeedMeta] = useState<RecommendationResponse['seed'] | null>(null);
  const [strategy, setStrategy] = useState('');

  const [loading, setLoading] = useState(false);
  const [playlistSaving, setPlaylistSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const playbackStatus = useSpotifyPlayback(isPremium && playHere);

  const queueIds = useMemo(() => new Set(queue.map((track) => track.spotifyId)), [queue]);

  async function handleFetch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSuccess(null);
    if (!userId.trim()) {
      setError('User ID is required (grab it from the auth callback).');
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
              <input
                type="text"
                placeholder="UUID from /auth/callback"
                value={userId}
                onChange={(event) => setUserId(event.target.value)}
              />
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

            <div className="toggles">
              <label className="toggle">
                <input type="checkbox" checked={isPremium} onChange={(e) => {
                  setIsPremium(e.target.checked);
                  if (!e.target.checked) {
                    setPlayHere(false);
                  }
                }} />
                <span>I&apos;m on Spotify Premium</span>
              </label>
              {isPremium && (
                <label className="toggle">
                  <input type="checkbox" checked={playHere} onChange={(e) => setPlayHere(e.target.checked)} />
                  <span>
                    Play here
                    <small className="toggle__hint">{playbackStatus === 'ready' ? 'Player ready' : playbackStatus}</small>
                  </span>
                </label>
              )}
            </div>

            <button type="submit" className="primary" disabled={loading}>
              {loading ? 'Fetching...' : 'Get recommendations'}
            </button>
          </form>

          {error && <p className="alert alert--error">{error}</p>}
          {success && <p className="alert alert--success">{success}</p>}

          {seedMeta && (
            <div className="seed-meta">
              <p className="eyebrow">Seed track</p>
              <h2>{seedMeta.name}</h2>
              <p className="subtitle">{seedMeta.artist} — strategy {strategy}</p>
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
