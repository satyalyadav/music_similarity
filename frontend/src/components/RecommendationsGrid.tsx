import { RecommendationCard } from "./RecommendationCard";
import { RecommendationTrackView } from "../types";

interface RecommendationsGridProps {
  tracks: RecommendationTrackView[];
  queuedTrackIds: Set<string>;
  currentlyPlayingId: string | null;
  playbackEnabled: boolean;
  onPlay: (track: RecommendationTrackView) => void;
  onAddToQueue: (track: RecommendationTrackView) => void;
}

export function RecommendationsGrid({
  tracks,
  queuedTrackIds,
  currentlyPlayingId,
  playbackEnabled,
  onPlay,
  onAddToQueue,
}: RecommendationsGridProps) {
  if (tracks.length === 0) {
    return (
      <div className="text-center py-12">
        <div className="w-16 h-16 bg-gray-100 rounded-full flex items-center justify-center mx-auto mb-4">
          <svg className="w-8 h-8 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3" />
          </svg>
        </div>
        <p className="text-sm text-gray-500 mb-1">No recommendations yet</p>
        <p className="text-xs text-gray-400">Select a seed track to discover similar music</p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
      {tracks.map((track) => (
        <RecommendationCard
          key={track.spotifyId}
          track={track}
          isPlaying={currentlyPlayingId === track.spotifyId}
          isInQueue={queuedTrackIds.has(track.spotifyId)}
          playbackEnabled={playbackEnabled}
          onPlay={() => onPlay(track)}
          onAddToQueue={() => onAddToQueue(track)}
        />
      ))}
    </div>
  );
}


