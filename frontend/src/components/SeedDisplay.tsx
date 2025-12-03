import { Badge } from "./ui/badge";
import { ImageWithFallback } from "./figma/ImageWithFallback";
import { SeedTrackView } from "../types";

interface SeedDisplayProps {
  track: SeedTrackView;
  strategy: string;
}

export function SeedDisplay({ track, strategy }: SeedDisplayProps) {
  return (
    <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
      <div className="flex gap-6">
        <div className="relative w-24 h-24 flex-shrink-0 rounded-xl overflow-hidden bg-gray-100">
          <ImageWithFallback
            src={track.imageUrl || `https://via.placeholder.com/220?text=${encodeURIComponent(track.name)}`}
            alt={`${track.name} cover art`}
            className="w-full h-full object-cover"
          />
        </div>
        <div className="flex-1 min-w-0">
          <Badge className="mb-2 bg-green-50 text-green-700 border-green-200" style={{ color: 'var(--spotify-green-dark)' }}>
            {strategy}
          </Badge>
          <h2 className="text-xl font-medium text-gray-900 mb-1 line-clamp-1">{track.name}</h2>
          <p className="text-sm text-gray-500 line-clamp-1">{track.artist}</p>
        </div>
      </div>
    </div>
  );
}

