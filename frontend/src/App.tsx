import { useCallback, useEffect, useMemo, useState } from "react";
import { API_BASE_URL, RECOMMENDATION_LIMIT } from "./config";
import { useSpotifyPlayback } from "./hooks/useSpotifyPlayback";
import {
  PlaylistResponse,
  RecommendationResponse,
  RecommendationTrackView,
  SeedTrackView,
  SeedsResponse,
} from "./types";
import { AuthSection } from "./components/AuthSection";
import { SeedSelector } from "./components/SeedSelector";
import { SeedDisplay } from "./components/SeedDisplay";
import { RecommendationControls } from "./components/RecommendationControls";
import { RecommendationsGrid } from "./components/RecommendationsGrid";
import { QueuePanel } from "./components/QueuePanel";
import { PlayerBar } from "./components/PlayerBar";
import { Alert, AlertDescription } from "./components/ui/alert";
import { Music2, CheckCircle2, AlertCircle } from "lucide-react";
import { toast } from "sonner";
import { Toaster } from "./components/ui/sonner";

type StoredAuth = {
  userId: string;
  displayName?: string | null;
  spotifyId?: string | null;
  product?: string | null;
  imageUrl?: string | null;
};

const STORAGE_KEY = "music-similarity-auth";
const USER_ID_REQUIRED_ERROR =
  "User ID is required. Click Connect Spotify first.";

const defaultPlaylistName = () => `AI Recs ${new Date().toLocaleDateString()}`;

function App() {
  const [userId, setUserId] = useState("");
  const [seedInput, setSeedInput] = useState("");
  const [limit, setLimit] = useState(RECOMMENDATION_LIMIT);

  const [recommendations, setRecommendations] = useState<
    RecommendationTrackView[]
  >([]);
  const [queue, setQueue] = useState<RecommendationTrackView[]>([]);
  const [playlistName, setPlaylistName] = useState(defaultPlaylistName);

  const [seedMeta, setSeedMeta] = useState<
    RecommendationResponse["seed"] | null
  >(null);
  const [strategy, setStrategy] = useState("");
  const [userProfile, setUserProfile] = useState<{
    displayName?: string | null;
    spotifyId?: string | null;
    product?: string | null;
    imageUrl?: string | null;
  } | null>(null);
  const [seedCandidates, setSeedCandidates] = useState<SeedTrackView[]>([]);
  const [searchResults, setSearchResults] = useState<SeedTrackView[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [playbackEnabled, setPlaybackEnabled] = useState(false);
  const [playingTrackId, setPlayingTrackId] = useState<string | null>(null);
  const [seedLoadingTop, setSeedLoadingTop] = useState(false);
  const [seedLoadingRecent, setSeedLoadingRecent] = useState(false);

  const [loading, setLoading] = useState(false);
  const [playlistSaving, setPlaylistSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [lastSavedSignature, setLastSavedSignature] = useState<string | null>(
    null
  );

  const queueIds = useMemo(
    () => new Set(queue.map((track) => track.spotifyId)),
    [queue]
  );
  const playback = useSpotifyPlayback({
    enabled: playbackEnabled,
    userId: userId ? userId : null,
  });
  const queueSignature = useMemo(
    () => (queue.length ? queue.map((t) => t.spotifyId).join(",") : ""),
    [queue]
  );
  const playlistDirty =
    queue.length > 0 && queueSignature !== lastSavedSignature;
  const canUsePlayback = playbackEnabled && playback.status === "ready";
  const isConnected = !!userId;
  // Check for premium status case-insensitively (Spotify may return "premium" or "Premium")
  // Returns: true if premium, false if definitely not premium, undefined if unknown
  const product = userProfile?.product || playback.product;
  const isPremium =
    product == null ? undefined : product.toLowerCase() === "premium";

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const userIdParam = params.get("userId");
    if (userIdParam) {
      const payload: StoredAuth = {
        userId: userIdParam,
        displayName: params.get("displayName"),
        spotifyId: params.get("spotifyId"),
        product: params.get("product"),
        imageUrl: params.get("imageUrl"),
      };
      setUserId(payload.userId);
      setUserProfile({
        displayName: payload.displayName,
        spotifyId: payload.spotifyId,
        product: payload.product,
        imageUrl: payload.imageUrl,
      });
      localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
      ["userId", "displayName", "spotifyId", "product", "imageUrl"].forEach(
        (key) => params.delete(key)
      );
      const search = params.toString();
      const newUrl = `${window.location.pathname}${search ? `?${search}` : ""}${
        window.location.hash
      }`;
      window.history.replaceState(null, "", newUrl);
      return;
    }
    const cached = localStorage.getItem(STORAGE_KEY);
    if (cached) {
      try {
        const parsed: StoredAuth = JSON.parse(cached);
        if (parsed.userId) {
          setUserId(parsed.userId);
          setUserProfile({
            displayName: parsed.displayName,
            spotifyId: parsed.spotifyId,
            product: parsed.product,
            imageUrl: parsed.imageUrl,
          });
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
      product: userProfile?.product ?? null,
      imageUrl: userProfile?.imageUrl ?? null,
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
  }, [userId, userProfile]);

  useEffect(() => {
    if (!playbackEnabled) {
      setPlayingTrackId(null);
    }
  }, [playbackEnabled]);

  useEffect(() => {
    if (playback.status !== "ready") {
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

  // Auto-enable playback for premium users when they connect
  useEffect(() => {
    if (!isConnected) {
      return;
    }

    // Check if user is premium - prioritize userProfile.product since it's available immediately
    // playback.product is only available after playback is enabled
    // Use case-insensitive comparison (Spotify may return "premium" or "Premium")
    const product = userProfile?.product || playback.product;
    const userIsPremium =
      product != null && product.toLowerCase() === "premium";
    const userIsNotPremium =
      product != null && product.toLowerCase() !== "premium";

    // Only auto-enable if we have a definitive premium status and playback isn't already enabled
    if (
      userIsPremium &&
      !playbackEnabled &&
      playback.status !== "loading" &&
      playback.status !== "error"
    ) {
      // Automatically enable playback for premium users
      setPlaybackEnabled(true);
    } else if (userIsNotPremium && playbackEnabled) {
      // Ensure playback is disabled for non-premium users
      setPlaybackEnabled(false);
    }
  }, [
    isConnected,
    userProfile?.product,
    playback.product,
    playback.status,
    playbackEnabled,
  ]);

  async function handleFetch() {
    setSuccess(null);
    if (!userId.trim()) {
      setError(USER_ID_REQUIRED_ERROR);
      toast.error(USER_ID_REQUIRED_ERROR);
      return;
    }
    if (!seedInput.trim()) {
      setError("Paste a Spotify track ID or URL to continue.");
      toast.error("Paste a Spotify track ID or URL to continue.");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const params = new URLSearchParams({
        userId: userId.trim(),
        seed: seedInput.trim(),
        limit: String(limit),
      });
      const response = await fetch(
        `${API_BASE_URL}/recommend?${params.toString()}`
      );
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
      toast.success(`Loaded ${payload.items.length} recommendations`);
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : "Unable to load recommendations";
      setError(errorMessage);
      toast.error(errorMessage);
    } finally {
      setLoading(false);
    }
  }

  function addToQueue(track: RecommendationTrackView) {
    if (queueIds.has(track.spotifyId)) {
      return;
    }
    setQueue((prev) => [...prev, track]);
    setSuccess(null);
    toast.success(`Added "${track.name}" to queue`);
  }

  function removeFromQueue(spotifyId: string) {
    setQueue((prev) => prev.filter((track) => track.spotifyId !== spotifyId));
    setSuccess(null);
  }

  function handlePlaybackToggle(next: boolean) {
    setSuccess(null);
    setError(null);
    setPlaybackEnabled(next);
  }

  async function handlePlayTrack(track: RecommendationTrackView) {
    if (!playbackEnabled) {
      setError("Enable playback to listen without leaving the page.");
      toast.error("Enable playback to listen without leaving the page.");
      return;
    }
    if (!canUsePlayback) {
      setError("Playback is not ready yet. Wait a moment and try again.");
      toast.error("Playback is not ready yet. Wait a moment and try again.");
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
      const errorMessage =
        err instanceof Error ? err.message : "Unable to control playback";
      setError(errorMessage);
      toast.error(errorMessage);
    }
  }

  function handleConnectSpotify() {
    const redirectTarget = `${window.location.origin}${window.location.pathname}`;
    const loginUrl = `${API_BASE_URL}/auth/login?redirect=${encodeURIComponent(
      redirectTarget
    )}`;
    window.location.href = loginUrl;
  }

  function handleDisconnectSpotify() {
    setUserId("");
    setUserProfile(null);
    setPlaybackEnabled(false);
    setSeedCandidates([]);
    setRecommendations([]);
    setQueue([]);
    setSeedMeta(null);
    setPlayingTrackId(null);
    toast.success("Disconnected from Spotify");
  }

  async function handleFetchTopSeeds(timeRange: string = "short_term") {
    setSuccess(null);
    if (!userId.trim()) {
      setError(USER_ID_REQUIRED_ERROR);
      toast.error(USER_ID_REQUIRED_ERROR);
      return;
    }
    setError(null);
    setSeedLoadingTop(true);
    try {
      const params = new URLSearchParams({
        userId: userId.trim(),
        limit: "20",
        mode: "top",
        timeRange,
      });
      const response = await fetch(
        `${API_BASE_URL}/me/seeds?${params.toString()}`
      );
      if (!response.ok) {
        throw new Error(`Seeds API returned ${response.status}`);
      }
      const payload: SeedsResponse = await response.json();
      setSeedCandidates(payload.items);
      if (payload.items.length === 0) {
        setError(
          "Spotify did not return any seed candidates. Try again later."
        );
        toast.error(
          "Spotify did not return any seed candidates. Try again later."
        );
      } else {
        toast.success(`Loaded ${payload.items.length} seed tracks`);
      }
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : "Unable to fetch seed tracks";
      setError(errorMessage);
      toast.error(errorMessage);
    } finally {
      setSeedLoadingTop(false);
    }
  }

  async function handleFetchRecentSeeds() {
    setSuccess(null);
    if (!userId.trim()) {
      setError(USER_ID_REQUIRED_ERROR);
      toast.error(USER_ID_REQUIRED_ERROR);
      return;
    }
    setError(null);
    setSeedLoadingRecent(true);
    try {
      const params = new URLSearchParams({
        userId: userId.trim(),
        limit: "20",
      });
      const response = await fetch(
        `${API_BASE_URL}/me/recent-seeds?${params.toString()}`
      );
      if (!response.ok) {
        throw new Error(`Seeds API returned ${response.status}`);
      }
      const payload: SeedsResponse = await response.json();
      setSeedCandidates(payload.items);
      if (payload.items.length === 0) {
        setError(
          "Spotify did not return any seed candidates. Try again later."
        );
        toast.error(
          "Spotify did not return any seed candidates. Try again later."
        );
      } else {
        toast.success(`Loaded ${payload.items.length} recent tracks`);
      }
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : "Unable to fetch recent tracks";
      setError(errorMessage);
      toast.error(errorMessage);
    } finally {
      setSeedLoadingRecent(false);
    }
  }

  function handleSelectSeed(track: SeedTrackView) {
    setSeedInput(track.id);
    setError(null);
    setSuccess(`Seed ready: ${track.name} — ${track.artist}`);
    toast.success(`Selected: ${track.name}`);
  }

  const handleSearchTracks = useCallback(
    async (query: string) => {
      if (!userId.trim() || !query.trim()) {
        setSearchResults([]);
        return;
      }
      setIsSearching(true);
      setError(null);
      try {
        const params = new URLSearchParams({
          userId: userId.trim(),
          query: query.trim(),
          limit: "20",
        });
        const response = await fetch(
          `${API_BASE_URL}/me/search-tracks?${params.toString()}`
        );
        if (!response.ok) {
          throw new Error(`Search API returned ${response.status}`);
        }
        const payload: SeedsResponse = await response.json();
        setSearchResults(payload.items);
      } catch (err) {
        const errorMessage =
          err instanceof Error ? err.message : "Unable to search tracks";
        setError(errorMessage);
        toast.error(errorMessage);
        setSearchResults([]);
      } finally {
        setIsSearching(false);
      }
    },
    [userId]
  );

  async function handleSavePlaylist() {
    if (!userId.trim()) {
      setError("User ID is required to save playlists.");
      toast.error("User ID is required to save playlists.");
      return;
    }
    if (queue.length === 0) {
      setError("Add at least one track to the queue.");
      toast.error("Add at least one track to the queue.");
      return;
    }
    if (!playlistDirty) {
      setError("No changes to save.");
      toast.error("No changes to save.");
      return;
    }
    setPlaylistSaving(true);
    setError(null);
    setSuccess(null);

    try {
      const response = await fetch(`${API_BASE_URL}/playlist`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          userId: userId.trim(),
          name: playlistName.trim() || defaultPlaylistName(),
          trackIds: queue.map((track) => track.spotifyId),
          publicPlaylist: false,
        }),
      });
      if (!response.ok) {
        throw new Error(`Playlist API returned ${response.status}`);
      }
      const payload: PlaylistResponse = await response.json();
      const successMessage = `Playlist saved! ${
        payload.spotifyUrl ?? ""
      }`.trim();
      setSuccess(successMessage);
      setLastSavedSignature(queueSignature);
      toast.success("Playlist saved to Spotify!");
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : "Unable to save playlist";
      setError(errorMessage);
      toast.error(errorMessage);
    } finally {
      setPlaylistSaving(false);
    }
  }

  async function handleTogglePlay() {
    try {
      if (playingTrackId) {
        if (playback.paused) {
          await playback.resume();
        } else {
          await playback.pause();
        }
      }
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : "Unable to control playback";
      setError(errorMessage);
      toast.error(errorMessage);
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-green-50 via-white to-emerald-50">
      <Toaster />

      {/* Header */}
      <header className="border-b border-gray-100 bg-white/80 backdrop-blur-sm sticky top-0 z-10">
        <div className="max-w-screen-2xl mx-auto px-6 py-8 relative overflow-hidden">
          {/* Decorative background elements */}
          <div
            className="absolute top-0 right-0 w-64 h-64 rounded-full opacity-10 blur-3xl"
            style={{ background: "var(--spotify-green)" }}
          />
          <div className="absolute bottom-0 left-20 w-48 h-48 bg-emerald-400 rounded-full opacity-10 blur-3xl" />

          <div className="relative z-10">
            <div className="flex items-start justify-between gap-6 mb-4">
              <div className="flex items-center gap-4">
                <div className="relative group">
                  <div
                    className="absolute inset-0 rounded-2xl opacity-20 blur-xl group-hover:blur-2xl transition-all duration-300"
                    style={{ background: "var(--spotify-green)" }}
                  />
                  <div
                    className="relative p-3 rounded-2xl shadow-xl group-hover:scale-110 transition-transform duration-300"
                    style={{
                      background:
                        "linear-gradient(135deg, var(--spotify-green), var(--spotify-green-light))",
                    }}
                  >
                    <Music2
                      className="w-8 h-8 text-white animate-pulse"
                      style={{ animationDuration: "3s" }}
                    />
                  </div>
                </div>
                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <h1 className="text-gray-900 bg-gradient-to-r from-gray-900 via-gray-800 to-gray-900 bg-clip-text text-transparent font-bold text-2xl">
                      Music Similarity Studio
                    </h1>
                    <div
                      className="px-3 py-1 rounded-full text-xs"
                      style={{
                        background: "rgba(29, 185, 84, 0.15)",
                        color: "var(--spotify-green-dark)",
                      }}
                    >
                      Powered by Spotify
                    </div>
                  </div>
                  <p className="text-gray-500 text-sm italic">
                    Your personal music discovery engine
                  </p>
                </div>
              </div>
            </div>

            <div className="flex items-start gap-6">
              <div className="flex-1 space-y-2">
                <p className="text-xl text-gray-700">
                  <span className="inline-block mr-2">🎵</span>
                  <span className="font-medium bg-gradient-to-r from-gray-900 to-gray-600 bg-clip-text text-transparent">
                    Paste a track. Get instant vibes.
                  </span>
                </p>
                <p className="text-sm text-gray-500 max-w-2xl leading-relaxed">
                  Powered by Last.fm similarity data and Spotify's catalog.
                  Simply drop in a track, discover perfectly matched songs,
                  build your queue, and save as a playlist in seconds.
                </p>
              </div>

              {/* Musical note decorations */}
              <div
                className="hidden lg:flex items-center gap-8 opacity-30"
                style={{ columnGap: "3.5rem" }}
              >
                <span
                  className="text-gray-400"
                  style={{
                    fontSize: "2rem",
                    animation: "bounce 2s ease-in-out infinite",
                    animationDelay: "0s",
                    display: "inline-block",
                    marginRight: "1.5rem",
                  }}
                >
                  ♪
                </span>
                <span
                  className="text-gray-400"
                  style={{
                    fontSize: "1.75rem",
                    animation: "bounce 2s ease-in-out infinite",
                    animationDelay: "0.3s",
                    display: "inline-block",
                    marginRight: "1.5rem",
                  }}
                >
                  ♫
                </span>
                <span
                  className="text-gray-400"
                  style={{
                    fontSize: "2rem",
                    animation: "bounce 2s ease-in-out infinite",
                    animationDelay: "0.6s",
                    display: "inline-block",
                  }}
                >
                  ♪
                </span>
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-screen-2xl mx-auto px-6 py-8 pb-32">
        <div className="grid lg:grid-cols-[1fr_380px] gap-8">
          {/* Left Column */}
          <div className="space-y-6">
            {/* Auth */}
            <AuthSection
              isConnected={isConnected}
              user={userProfile}
              onConnect={handleConnectSpotify}
              onDisconnect={handleDisconnectSpotify}
            />

            {/* Alerts */}
            {success && (
              <Alert
                className="border-gray-200"
                style={{ backgroundColor: "rgba(29, 185, 84, 0.1)" }}
              >
                <CheckCircle2
                  className="w-4 h-4"
                  style={{ color: "var(--spotify-green-dark)" }}
                />
                <AlertDescription
                  style={{ color: "var(--spotify-green-dark)" }}
                >
                  {success}
                </AlertDescription>
              </Alert>
            )}

            {error && (
              <Alert variant="destructive">
                <AlertCircle className="w-4 h-4" />
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}

            {/* Seed Selection */}
            <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 space-y-6">
              <SeedSelector
                seedInput={seedInput}
                onSeedInputChange={setSeedInput}
                onFetchTopTracks={() => handleFetchTopSeeds()}
                onFetchRecentlyPlayed={handleFetchRecentSeeds}
                seedCandidates={seedCandidates}
                isLoadingTopSeeds={seedLoadingTop}
                isLoadingRecentSeeds={seedLoadingRecent}
                onSeedSelect={handleSelectSeed}
                isConnected={isConnected}
                onSearchTracks={handleSearchTracks}
                searchResults={searchResults}
                isSearching={isSearching}
                onClearCandidates={() => setSeedCandidates([])}
              />

              <RecommendationControls
                limit={limit}
                onLimitChange={setLimit}
                onFetchRecommendations={handleFetch}
                isLoading={loading}
                disabled={!seedInput}
              />
            </div>

            {/* Selected Seed Display */}
            {seedMeta && <SeedDisplay track={seedMeta} strategy={strategy} />}

            {/* Playback Status - Only show message for non-premium users */}
            {isConnected && isPremium === false && (
              <Alert variant="default" className="border-gray-200">
                <AlertCircle className="w-4 h-4" />
                <AlertDescription className="text-sm">
                  Spotify Premium is required for in-browser playback. Upgrade
                  your account to enable this feature.
                </AlertDescription>
              </Alert>
            )}

            {/* Recommendations */}
            <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
              <h2 className="text-gray-900 mb-6">Recommendations</h2>
              <RecommendationsGrid
                tracks={recommendations}
                queuedTrackIds={queueIds}
                currentlyPlayingId={playingTrackId}
                playbackEnabled={playbackEnabled}
                onPlay={handlePlayTrack}
                onAddToQueue={addToQueue}
              />
            </div>
          </div>

          {/* Right Column - Queue Panel */}
          <div className="lg:sticky lg:top-24 h-fit lg:h-[calc(100vh-8rem)]">
            <QueuePanel
              queue={queue}
              onRemoveFromQueue={removeFromQueue}
              playlistName={playlistName}
              onPlaylistNameChange={setPlaylistName}
              onSavePlaylist={handleSavePlaylist}
              isSaving={playlistSaving}
              playlistDirty={playlistDirty}
            />
          </div>
        </div>
      </main>

      {/* Player Bar */}
      {playbackEnabled && playback.status === "ready" && playback.track && (
        <PlayerBar playback={playback} onTogglePlay={handleTogglePlay} />
      )}
    </div>
  );
}

export default App;
