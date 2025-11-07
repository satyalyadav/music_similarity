import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { X, Save, Check } from "lucide-react";
import { ScrollArea } from "./ui/scroll-area";
import { RecommendationTrackView } from "../types";

interface QueuePanelProps {
  queue: RecommendationTrackView[];
  onRemoveFromQueue: (spotifyId: string) => void;
  playlistName: string;
  onPlaylistNameChange: (name: string) => void;
  onSavePlaylist: () => void;
  isSaving: boolean;
  playlistDirty: boolean;
}

export function QueuePanel({
  queue,
  onRemoveFromQueue,
  playlistName,
  onPlaylistNameChange,
  onSavePlaylist,
  isSaving,
  playlistDirty,
}: QueuePanelProps) {
  return (
    <div className="h-full flex flex-col bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
      <div className="p-6 border-b border-gray-100">
        <h3 className="text-gray-900 mb-1">Your queue</h3>
        <p className="text-sm text-gray-500">
          {queue.length} track{queue.length !== 1 ? "s" : ""}
        </p>
      </div>

      <ScrollArea className="flex-1 p-6">
        {queue.length === 0 ? (
          <div className="text-center py-12">
            <div className="w-12 h-12 bg-gray-100 rounded-full flex items-center justify-center mx-auto mb-3">
              <svg
                className="w-6 h-6 text-gray-400"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"
                />
              </svg>
            </div>
            <p className="text-sm text-gray-500">
              Add tracks you like and turn them into a playlist
            </p>
          </div>
        ) : (
          <div className="space-y-2">
            {queue.map((track, index) => (
              <div
                key={track.spotifyId}
                className="group flex items-start gap-3 p-3 rounded-lg hover:bg-gray-50 transition-colors"
              >
                <span className="text-sm text-gray-400 mt-0.5 flex-shrink-0 w-6">
                  {index + 1}
                </span>
                <div className="flex-1 min-w-0">
                  <p className="text-sm text-gray-900 line-clamp-1">
                    {track.name}
                  </p>
                  <p className="text-xs text-gray-500 line-clamp-1">
                    {track.artist}
                  </p>
                </div>
                <Button
                  size="icon"
                  variant="ghost"
                  className="opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0 h-8 w-8"
                  onClick={() => onRemoveFromQueue(track.spotifyId)}
                >
                  <X className="w-4 h-4" />
                </Button>
              </div>
            ))}
          </div>
        )}
      </ScrollArea>

      <div className="p-6 border-t border-gray-100 space-y-4 bg-gray-50">
        <div className="space-y-2">
          <Label htmlFor="playlist-name" className="text-gray-700">
            Playlist name
          </Label>
          <Input
            id="playlist-name"
            value={playlistName}
            onChange={(e) => onPlaylistNameChange(e.target.value)}
            placeholder="My awesome playlist"
            className="bg-white"
          />
        </div>
        <Button
          onClick={onSavePlaylist}
          disabled={queue.length === 0 || !playlistDirty || isSaving}
          className="w-full gap-2 text-white hover:opacity-90"
          style={{
            background:
              queue.length > 0 && playlistDirty && !isSaving
                ? "var(--spotify-green)"
                : undefined,
          }}
        >
          {isSaving ? (
            "Saving…"
          ) : queue.length === 0 ? (
            <>
              <Save className="w-4 h-4" />
              Save as playlist
            </>
          ) : playlistDirty ? (
            <>
              <Save className="w-4 h-4" />
              Save as playlist
            </>
          ) : (
            <>
              <Check className="w-4 h-4" />
              Saved
            </>
          )}
        </Button>
      </div>
    </div>
  );
}
