import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { Sparkles, Link2, Search, Clock, TrendingUp } from "lucide-react";
import { ImageWithFallback } from "./figma/ImageWithFallback";
import { SeedTrackView } from "../types";
import { useState, useEffect } from "react";
import { cn } from "./ui/utils";

type SeedMode = "paste" | "search" | "top" | "recent";

interface SeedSelectorProps {
  seedInput: string;
  onSeedInputChange: (value: string) => void;
  onFetchTopTracks: () => void;
  onFetchRecentlyPlayed: () => void;
  seedCandidates: SeedTrackView[];
  isLoadingTopSeeds: boolean;
  isLoadingRecentSeeds: boolean;
  onSeedSelect: (track: SeedTrackView) => void;
  isConnected: boolean;
  onSearchTracks?: (query: string) => Promise<void>;
  searchResults?: SeedTrackView[];
  isSearching?: boolean;
  onClearCandidates?: () => void;
}

export function SeedSelector({
  seedInput,
  onSeedInputChange,
  onFetchTopTracks,
  onFetchRecentlyPlayed,
  seedCandidates,
  isLoadingTopSeeds,
  isLoadingRecentSeeds,
  onSeedSelect,
  isConnected,
  onSearchTracks,
  searchResults = [],
  isSearching = false,
  onClearCandidates,
}: SeedSelectorProps) {
  const [selectedMode, setSelectedMode] = useState<SeedMode>("paste");
  const [searchQuery, setSearchQuery] = useState("");
  const [showSearchResults, setShowSearchResults] = useState(false);

  // Debounce search
  useEffect(() => {
    if (!searchQuery.trim() || !onSearchTracks || selectedMode !== "search") {
      setShowSearchResults(false);
      return;
    }

    const timeoutId = setTimeout(() => {
      onSearchTracks(searchQuery.trim());
      setShowSearchResults(true);
    }, 500);

    return () => clearTimeout(timeoutId);
  }, [searchQuery, onSearchTracks, selectedMode]);

  // Clear search results when switching away from search mode
  useEffect(() => {
    if (selectedMode !== "search") {
      setSearchQuery("");
      setShowSearchResults(false);
    }
  }, [selectedMode]);

  const handleSearchSelect = (track: SeedTrackView) => {
    onSeedSelect(track);
    setSearchQuery("");
    setShowSearchResults(false);
  };

  const handleModeChange = (mode: SeedMode) => {
    const previousMode = selectedMode;
    setSelectedMode(mode);

    // Clear candidates when switching between top/recent modes or away from them
    if (
      onClearCandidates &&
      (previousMode === "top" ||
        previousMode === "recent" ||
        mode === "top" ||
        mode === "recent")
    ) {
      if (previousMode !== mode) {
        onClearCandidates();
      }
    }

    // Fetch data when switching to top or recent modes
    if (mode === "top" && isConnected && !isLoadingTopSeeds) {
      onFetchTopTracks();
    } else if (mode === "recent" && isConnected && !isLoadingRecentSeeds) {
      onFetchRecentlyPlayed();
    }
  };

  const renderTrackGrid = (tracks: SeedTrackView[], emptyMessage?: string) => {
    if (tracks.length === 0) {
      if (emptyMessage) {
        return (
          <div className="text-center py-12">
            <p className="text-sm text-gray-500">{emptyMessage}</p>
          </div>
        );
      }
      return null;
    }

    return (
      <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-5 gap-3 max-h-96 overflow-y-auto">
        {tracks.map((track) => (
          <button
            key={track.id}
            onClick={() => onSeedSelect(track)}
            className="group p-3 bg-white rounded-xl border border-gray-100 hover:shadow-md transition-all duration-200 text-left cursor-pointer"
            style={
              {
                "--hover-border": "var(--spotify-green-light)",
              } as React.CSSProperties
            }
            onMouseEnter={(e) =>
              (e.currentTarget.style.borderColor = "var(--spotify-green-light)")
            }
            onMouseLeave={(e) => (e.currentTarget.style.borderColor = "")}
          >
            <div className="aspect-square mb-2 rounded-lg overflow-hidden bg-gray-100">
              <ImageWithFallback
                src={
                  track.imageUrl ||
                  `https://via.placeholder.com/120?text=${encodeURIComponent(
                    track.name
                  )}`
                }
                alt={track.name}
                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-200"
              />
            </div>
            <p className="text-sm text-gray-900 line-clamp-1">{track.name}</p>
            <p className="text-xs text-gray-500 line-clamp-1">{track.artist}</p>
          </button>
        ))}
      </div>
    );
  };

  return (
    <div className="space-y-6">
      <div className="space-y-4">
        <Label className="text-gray-900">Start with a track</Label>

        {/* Mode Selector Tabs */}
        <div className="flex flex-wrap gap-2 border-b border-gray-200 pb-8 mb-4">
          {isConnected && onSearchTracks && (
            <button
              onClick={() => handleModeChange("search")}
              className={cn(
                "flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all cursor-pointer mb-2",
                selectedMode === "search"
                  ? "bg-green-50 text-green-700 border border-green-200"
                  : "text-gray-600 hover:text-gray-900 hover:bg-gray-50"
              )}
              style={
                selectedMode === "search"
                  ? {
                      backgroundColor: "rgba(29, 185, 84, 0.1)",
                      color: "var(--spotify-green-dark)",
                      borderColor: "rgba(29, 185, 84, 0.3)",
                    }
                  : undefined
              }
            >
              <Search className="w-4 h-4" />
              Search
            </button>
          )}

          {isConnected && (
            <>
              <button
                onClick={() => handleModeChange("top")}
                disabled={isLoadingTopSeeds}
                className={cn(
                  "flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed mb-2",
                  selectedMode === "top"
                    ? "bg-green-50 text-green-700 border border-green-200"
                    : "text-gray-600 hover:text-gray-900 hover:bg-gray-50"
                )}
                style={
                  selectedMode === "top"
                    ? {
                        backgroundColor: "rgba(29, 185, 84, 0.1)",
                        color: "var(--spotify-green-dark)",
                        borderColor: "rgba(29, 185, 84, 0.3)",
                      }
                    : undefined
                }
              >
                <TrendingUp className="w-4 h-4" />
                {isLoadingTopSeeds ? "Loading..." : "Top Tracks"}
              </button>

              <button
                onClick={() => handleModeChange("recent")}
                disabled={isLoadingRecentSeeds}
                className={cn(
                  "flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed mb-2",
                  selectedMode === "recent"
                    ? "bg-green-50 text-green-700 border border-green-200"
                    : "text-gray-600 hover:text-gray-900 hover:bg-gray-50"
                )}
                style={
                  selectedMode === "recent"
                    ? {
                        backgroundColor: "rgba(29, 185, 84, 0.1)",
                        color: "var(--spotify-green-dark)",
                        borderColor: "rgba(29, 185, 84, 0.3)",
                      }
                    : undefined
                }
              >
                <Clock className="w-4 h-4" />
                {isLoadingRecentSeeds ? "Loading..." : "Recent"}
              </button>
            </>
          )}

          <button
            onClick={() => handleModeChange("paste")}
            className={cn(
              "flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all cursor-pointer mb-2",
              selectedMode === "paste"
                ? "bg-green-50 text-green-700 border border-green-200"
                : "text-gray-600 hover:text-gray-900 hover:bg-gray-50"
            )}
            style={
              selectedMode === "paste"
                ? {
                    backgroundColor: "rgba(29, 185, 84, 0.1)",
                    color: "var(--spotify-green-dark)",
                    borderColor: "rgba(29, 185, 84, 0.3)",
                  }
                : undefined
            }
          >
            <Link2 className="w-4 h-4" />
            Paste ID
          </button>
        </div>

        {/* Content based on selected mode */}
        <div className="space-y-4">
          {/* Paste ID Mode */}
          {selectedMode === "paste" && (
            <div className="space-y-3">
              <div className="relative">
                <Link2 className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <Input
                  id="seed-input"
                  value={seedInput}
                  onChange={(e) => onSeedInputChange(e.target.value)}
                  placeholder="Paste Spotify track URL or ID..."
                  className="pl-10 h-12 border-gray-200"
                  style={
                    {
                      "--tw-ring-color": "var(--spotify-green)",
                    } as React.CSSProperties
                  }
                  onFocus={(e) =>
                    (e.target.style.borderColor = "var(--spotify-green)")
                  }
                  onBlur={(e) => (e.target.style.borderColor = "")}
                />
              </div>
              <p className="text-sm text-gray-500">
                Paste a Spotify track link or ID to use as your seed
              </p>
            </div>
          )}

          {/* Search Mode */}
          {selectedMode === "search" && isConnected && onSearchTracks && (
            <div className="space-y-3">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <Input
                  id="search-input"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Search for tracks on Spotify..."
                  className="pl-10 h-12 border-gray-200"
                  style={
                    {
                      "--tw-ring-color": "var(--spotify-green)",
                    } as React.CSSProperties
                  }
                  onFocus={(e) =>
                    (e.target.style.borderColor = "var(--spotify-green)")
                  }
                  onBlur={(e) => (e.target.style.borderColor = "")}
                />
                {isSearching && (
                  <div className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-gray-400">
                    Searching...
                  </div>
                )}
              </div>

              {showSearchResults && searchResults.length > 0 && (
                <div className="space-y-2">
                  <p className="text-sm text-gray-700 font-medium">
                    Search results
                  </p>
                  {renderTrackGrid(searchResults)}
                </div>
              )}
              {showSearchResults &&
                !isSearching &&
                searchQuery.trim() &&
                searchResults.length === 0 && (
                  <p className="text-sm text-gray-500">
                    No tracks found. Try a different search.
                  </p>
                )}
              {!showSearchResults && searchQuery.trim() === "" && (
                <p className="text-sm text-gray-500">
                  Start typing to search for tracks on Spotify
                </p>
              )}
            </div>
          )}

          {/* Top Tracks Mode */}
          {selectedMode === "top" && isConnected && (
            <div className="space-y-3">
              {isLoadingTopSeeds ? (
                <div className="text-center py-12">
                  <p className="text-sm text-gray-500">
                    Loading your top tracks...
                  </p>
                </div>
              ) : seedCandidates.length > 0 ? (
                <div className="space-y-2">
                  <p className="text-sm text-gray-700 font-medium">
                    Your top tracks
                  </p>
                  {renderTrackGrid(seedCandidates)}
                </div>
              ) : (
                <div className="space-y-3">
                  <p className="text-sm text-gray-500">
                    Click "Top Tracks" above to load your favorite tracks
                  </p>
                  <Button
                    onClick={onFetchTopTracks}
                    disabled={!isConnected || isLoadingTopSeeds}
                    className="gap-2 text-white hover:opacity-90"
                    style={{
                      background:
                        isConnected && !isLoadingTopSeeds
                          ? "var(--spotify-green)"
                          : undefined,
                    }}
                  >
                    <TrendingUp className="w-4 h-4" />
                    Load Top Tracks
                  </Button>
                </div>
              )}
            </div>
          )}

          {/* Recent Tracks Mode */}
          {selectedMode === "recent" && isConnected && (
            <div className="space-y-3">
              {isLoadingRecentSeeds ? (
                <div className="text-center py-12">
                  <p className="text-sm text-gray-500">
                    Loading your recently played tracks...
                  </p>
                </div>
              ) : seedCandidates.length > 0 ? (
                <div className="space-y-2">
                  <p className="text-sm text-gray-700 font-medium">
                    Recently played
                  </p>
                  {renderTrackGrid(seedCandidates)}
                </div>
              ) : (
                <div className="space-y-3">
                  <p className="text-sm text-gray-500">
                    Click "Recent" above to load your recently played tracks
                  </p>
                  <Button
                    onClick={onFetchRecentlyPlayed}
                    disabled={!isConnected || isLoadingRecentSeeds}
                    className="gap-2 text-white hover:opacity-90"
                    style={{
                      background:
                        isConnected && !isLoadingRecentSeeds
                          ? "var(--spotify-green)"
                          : undefined,
                    }}
                  >
                    <Clock className="w-4 h-4" />
                    Load Recent Tracks
                  </Button>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
