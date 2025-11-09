import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { Sparkles, Link2 } from "lucide-react";
import { ImageWithFallback } from "./figma/ImageWithFallback";
import { SeedTrackView } from "../types";

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
}: SeedSelectorProps) {
  return (
    <div className="space-y-6">
      <div className="space-y-3">
        <Label htmlFor="seed-input" className="text-gray-900">
          Start with a track
        </Label>
        <div className="flex gap-3">
          <div className="relative flex-1">
            <Link2 className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <Input
              id="seed-input"
              value={seedInput}
              onChange={(e) => onSeedInputChange(e.target.value)}
              placeholder="Paste Spotify track URL or ID..."
              className="pl-10 h-12 border-gray-200"
              style={{
                '--tw-ring-color': 'var(--spotify-green)',
              } as React.CSSProperties}
              onFocus={(e) => e.target.style.borderColor = 'var(--spotify-green)'}
              onBlur={(e) => e.target.style.borderColor = ''}
            />
          </div>
          <Button
            onClick={onFetchTopTracks}
            disabled={!isConnected || isLoadingTopSeeds}
            className="gap-2 h-12 px-6 text-white hover:opacity-90"
            style={{ background: isConnected && !isLoadingTopSeeds ? 'var(--spotify-green)' : undefined }}
          >
            <Sparkles className="w-4 h-4" />
            {isLoadingTopSeeds ? "Loading..." : "Use my top tracks"}
          </Button>
          <Button
            onClick={onFetchRecentlyPlayed}
            disabled={!isConnected || isLoadingRecentSeeds}
            className="gap-2 h-12 px-6 text-white hover:opacity-90"
            style={{ background: isConnected && !isLoadingRecentSeeds ? 'var(--spotify-green)' : undefined }}
          >
            <Sparkles className="w-4 h-4" />
            {isLoadingRecentSeeds ? "Loading..." : "Use my recently played"}
          </Button>
        </div>
        <p className="text-sm text-gray-500">
          Paste a Spotify track link or discover from your listening history
        </p>
      </div>

      {seedCandidates.length > 0 && (
        <div className="space-y-3">
          <p className="text-sm text-gray-700">Pick a seed from your favorites</p>
          <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-5 gap-3">
            {seedCandidates.map((track) => (
              <button
                key={track.id}
                onClick={() => onSeedSelect(track)}
                className="group p-3 bg-white rounded-xl border border-gray-100 hover:shadow-md transition-all duration-200 text-left"
                style={{
                  '--hover-border': 'var(--spotify-green-light)',
                } as React.CSSProperties}
                onMouseEnter={(e) => e.currentTarget.style.borderColor = 'var(--spotify-green-light)'}
                onMouseLeave={(e) => e.currentTarget.style.borderColor = ''}
              >
                <div className="aspect-square mb-2 rounded-lg overflow-hidden bg-gray-100">
                  <ImageWithFallback
                    src={track.imageUrl || `https://via.placeholder.com/120?text=${encodeURIComponent(track.name)}`}
                    alt={track.name}
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-200"
                  />
                </div>
                <p className="text-sm text-gray-900 line-clamp-1">{track.name}</p>
                <p className="text-xs text-gray-500 line-clamp-1">{track.artist}</p>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}


