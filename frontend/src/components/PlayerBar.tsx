import { Button } from "./ui/button";
import { Slider } from "./ui/slider";
import { Play, Pause } from "lucide-react";
import { ImageWithFallback } from "./figma/ImageWithFallback";
import { SpotifyPlaybackHandle } from "../hooks/useSpotifyPlayback";

interface PlayerBarProps {
  playback: SpotifyPlaybackHandle;
  onTogglePlay: () => void;
}

function formatTime(ms?: number): string {
  if (ms == null || !Number.isFinite(ms)) return "0:00";
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

export function PlayerBar({ playback, onTogglePlay }: PlayerBarProps) {
  const track = playback.track;
  if (!track) return null;

  const positionMs = playback.positionMs ?? 0;
  const durationMs = playback.durationMs ?? 0;
  const isPlaying = !playback.paused;

  const handleSeek = (value: number[]) => {
    const position = value[0];
    const ms = Math.round((position / 100) * durationMs);
    playback.seek(ms).catch(() => {
      // ignore seek errors
    });
  };

  const progress = durationMs > 0 ? (positionMs / durationMs) * 100 : 0;

  return (
    <div className="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-200 shadow-2xl z-50">
      <div className="max-w-screen-2xl mx-auto px-6 py-4">
        <div className="flex items-center gap-6">
          {/* Track Info */}
          <div className="flex items-center gap-4 w-80">
            <div className="w-14 h-14 rounded-lg overflow-hidden bg-gray-100 flex-shrink-0 shadow-sm">
              <ImageWithFallback
                src={track.imageUrl || `https://via.placeholder.com/56?text=${encodeURIComponent(track.name || "Track")}`}
                alt={track.name || "Track"}
                className="w-full h-full object-cover"
              />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-sm text-gray-900 truncate">{track.name || "—"}</p>
              <p className="text-xs text-gray-500 truncate">{track.artist || ""}</p>
            </div>
          </div>

          {/* Playback Controls */}
          <div className="flex-1 space-y-2">
            <div className="flex justify-center">
              <Button
                size="icon"
                onClick={onTogglePlay}
                className="w-10 h-10 rounded-full shadow-lg hover:opacity-90"
                style={{ background: 'var(--spotify-green)' }}
                disabled={playback.status !== "ready"}
              >
                {isPlaying ? (
                  <Pause className="w-5 h-5 text-white" />
                ) : (
                  <Play className="w-5 h-5 text-white ml-0.5" />
                )}
              </Button>
            </div>
            
            <div className="flex items-center gap-3">
              <span className="text-xs text-gray-500 w-10 text-right">
                {formatTime(positionMs)}
              </span>
              <Slider
                value={[progress]}
                max={100}
                step={0.1}
                onValueChange={handleSeek}
                className="flex-1 [&_[role=slider]]:h-3 [&_[role=slider]]:w-3 [&_.relative]:h-1"
              />
              <span className="text-xs text-gray-500 w-10">
                {formatTime(durationMs)}
              </span>
            </div>
          </div>

          {/* Right spacer for balance */}
          <div className="w-80" />
        </div>
      </div>
    </div>
  );
}




