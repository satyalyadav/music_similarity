import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useMemo, useState } from 'react';
import { API_BASE_URL, RECOMMENDATION_LIMIT } from './config';
import { RecommendationCard } from './components/RecommendationCard';
import { QueuePanel } from './components/QueuePanel';
import { useSpotifyPlayback } from './hooks/useSpotifyPlayback';
import './App.css';
const defaultPlaylistName = () => `AI Recs ${new Date().toLocaleDateString()}`;
function App() {
    const [userId, setUserId] = useState('');
    const [seedInput, setSeedInput] = useState('');
    const [limit, setLimit] = useState(RECOMMENDATION_LIMIT);
    const [isPremium, setIsPremium] = useState(false);
    const [playHere, setPlayHere] = useState(false);
    const [recommendations, setRecommendations] = useState([]);
    const [queue, setQueue] = useState([]);
    const [playlistName, setPlaylistName] = useState(defaultPlaylistName);
    const [seedMeta, setSeedMeta] = useState(null);
    const [strategy, setStrategy] = useState('');
    const [loading, setLoading] = useState(false);
    const [playlistSaving, setPlaylistSaving] = useState(false);
    const [error, setError] = useState(null);
    const [success, setSuccess] = useState(null);
    const playbackStatus = useSpotifyPlayback(isPremium && playHere);
    const queueIds = useMemo(() => new Set(queue.map((track) => track.spotifyId)), [queue]);
    async function handleFetch(event) {
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
            const payload = await response.json();
            setRecommendations(payload.items);
            setSeedMeta(payload.seed);
            setStrategy(payload.strategy);
            setQueue([]);
            setSuccess(`Loaded ${payload.items.length} recommendations`);
        }
        catch (err) {
            setError(err instanceof Error ? err.message : 'Unable to load recommendations');
        }
        finally {
            setLoading(false);
        }
    }
    function addToQueue(track) {
        if (queueIds.has(track.spotifyId)) {
            return;
        }
        setQueue((prev) => [...prev, track]);
    }
    function removeFromQueue(spotifyId) {
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
            const payload = await response.json();
            setSuccess(`Playlist saved! ${payload.spotifyUrl ?? ''}`.trim());
        }
        catch (err) {
            setError(err instanceof Error ? err.message : 'Unable to save playlist');
        }
        finally {
            setPlaylistSaving(false);
        }
    }
    return (_jsxs("div", { className: "app-shell", children: [_jsx("header", { children: _jsxs("div", { children: [_jsx("p", { className: "eyebrow", children: "Music Similarity Studio" }), _jsx("h1", { children: "Paste a track. Get instant vibes." }), _jsx("p", { className: "subtitle", children: "We call Last.fm + Spotify for you. Queue your favorite matches and save them as a playlist in two clicks." })] }) }), _jsxs("main", { children: [_jsxs("section", { className: "panel", children: [_jsxs("form", { className: "form", onSubmit: handleFetch, children: [_jsxs("label", { children: ["User ID", _jsx("input", { type: "text", placeholder: "UUID from /auth/callback", value: userId, onChange: (event) => setUserId(event.target.value) })] }), _jsxs("label", { children: ["Spotify track link or ID", _jsx("input", { type: "text", placeholder: "https://open.spotify.com/track/...", value: seedInput, onChange: (event) => setSeedInput(event.target.value) })] }), _jsxs("label", { children: ["How many recs?", _jsx("input", { type: "number", min: 5, max: 50, value: limit, onChange: (event) => setLimit(Number(event.target.value)) })] }), _jsxs("div", { className: "toggles", children: [_jsxs("label", { className: "toggle", children: [_jsx("input", { type: "checkbox", checked: isPremium, onChange: (e) => {
                                                            setIsPremium(e.target.checked);
                                                            if (!e.target.checked) {
                                                                setPlayHere(false);
                                                            }
                                                        } }), _jsx("span", { children: "I'm on Spotify Premium" })] }), isPremium && (_jsxs("label", { className: "toggle", children: [_jsx("input", { type: "checkbox", checked: playHere, onChange: (e) => setPlayHere(e.target.checked) }), _jsxs("span", { children: ["Play here", _jsx("small", { className: "toggle__hint", children: playbackStatus === 'ready' ? 'Player ready' : playbackStatus })] })] }))] }), _jsx("button", { type: "submit", className: "primary", disabled: loading, children: loading ? 'Fetching...' : 'Get recommendations' })] }), error && _jsx("p", { className: "alert alert--error", children: error }), success && _jsx("p", { className: "alert alert--success", children: success }), seedMeta && (_jsxs("div", { className: "seed-meta", children: [_jsx("p", { className: "eyebrow", children: "Seed track" }), _jsx("h2", { children: seedMeta.name }), _jsxs("p", { className: "subtitle", children: [seedMeta.artist, " \u2014 strategy ", strategy] })] }))] }), _jsxs("section", { className: "layout", children: [_jsxs("div", { className: "recommendations", children: [recommendations.length === 0 && (_jsx("p", { className: "placeholder", children: "Run a recommendation to see top matches." })), recommendations.map((track) => (_jsx(RecommendationCard, { track: track, onAdd: addToQueue, disabled: queueIds.has(track.spotifyId) }, track.spotifyId)))] }), _jsxs("aside", { children: [_jsx(QueuePanel, { tracks: queue, onRemove: removeFromQueue }), _jsxs("div", { className: "playlist", children: [_jsxs("label", { children: ["Playlist name", _jsx("input", { type: "text", value: playlistName, onChange: (event) => setPlaylistName(event.target.value) })] }), _jsx("button", { className: "primary", disabled: playlistSaving, onClick: handleSavePlaylist, children: playlistSaving ? 'Saving…' : 'Save as playlist' })] })] })] })] })] }));
}
export default App;
