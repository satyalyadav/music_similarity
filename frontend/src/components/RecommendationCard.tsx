import { Button } from "./ui/button";
import { Play, Pause, ExternalLink, Plus, Check } from "lucide-react";
import { ImageWithFallback } from "./figma/ImageWithFallback";
import { Badge } from "./ui/badge";
import { RecommendationTrackView } from "../types";

interface RecommendationCardProps {
  track: RecommendationTrackView;
  isPlaying: boolean;
  isInQueue: boolean;
  playbackEnabled: boolean;
  onPlay: () => void;
  onAddToQueue: () => void;
}

export function RecommendationCard({
  track,
  isPlaying,
  isInQueue,
  playbackEnabled,
  onPlay,
  onAddToQueue,
}: RecommendationCardProps) {
  return (
    <div className="group bg-white rounded-xl border border-gray-100 overflow-hidden hover:shadow-lg transition-all duration-200"
      onMouseEnter={(e) => e.currentTarget.style.borderColor = 'rgba(29, 185, 84, 0.3)'}
      onMouseLeave={(e) => e.currentTarget.style.borderColor = ''}
    >
      <div className="relative aspect-square bg-gray-100">
        <ImageWithFallback
          src={track.imageUrl || `https://via.placeholder.com/180?text=${encodeURIComponent(track.name)}`}
          alt={track.name}
          className="w-full h-full object-cover"
        />
        {playbackEnabled && (
          <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity duration-200 flex items-center justify-center">
            <Button
              size="icon"
              onClick={onPlay}
              className="w-14 h-14 rounded-full bg-white hover:bg-white hover:scale-110 transition-transform duration-200 shadow-xl"
            >
              {isPlaying ? (
                <Pause className="w-6 h-6" style={{ color: 'var(--spotify-green)' }} />
              ) : (
                <Play className="w-6 h-6 ml-0.5" style={{ color: 'var(--spotify-green)' }} />
              )}
            </Button>
          </div>
        )}
        <Badge className="absolute top-3 right-3 bg-white/95 border-0 shadow-sm" style={{ color: 'var(--spotify-green-dark)' }}>
          {track.score.toFixed(2)}
        </Badge>
      </div>
      
      <div className="p-4 space-y-3">
        <div>
          <h4 className="text-gray-900 line-clamp-1 mb-1">{track.name}</h4>
          <p className="text-sm text-gray-500 line-clamp-1">{track.artist}</p>
        </div>
        
        <div className="flex gap-2">
          <Button
            size="sm"
            variant="outline"
            className="flex-1 gap-2"
            onClick={onAddToQueue}
            disabled={isInQueue}
          >
            {isInQueue ? (
              <>
                <Check className="w-3.5 h-3.5" />
                Added
              </>
            ) : (
              <>
                <Plus className="w-3.5 h-3.5" />
                Queue
              </>
            )}
          </Button>
          <Button
            size="sm"
            variant="ghost"
            asChild
          >
            <a
              href={track.spotifyUrl || `https://open.spotify.com/track/${track.spotifyId}`}
              target="_blank"
              rel="noopener noreferrer"
              className="gap-2"
            >
              <ExternalLink className="w-3.5 h-3.5" />
            </a>
          </Button>
        </div>
      </div>
    </div>
  );
}
