import { RecommendationTrackView } from "../types";
import "./QueuePanel.css";

interface Props {
  tracks: RecommendationTrackView[];
  onRemove: (spotifyId: string) => void;
}

export function QueuePanel({ tracks, onRemove }: Props) {
  if (tracks.length === 0) {
    return (
      <section className="queue">
        <h2>Your queue</h2>
        <p className="queue__empty">
          Add tracks you like and turn them into a playlist.
        </p>
      </section>
    );
  }

  return (
    <section className="queue">
      <div className="queue__header">
        <h2>Your queue</h2>
        <span>
          {tracks.length} track{tracks.length === 1 ? "" : "s"}
        </span>
      </div>
      <ul>
        {tracks.map((track) => (
          <li key={track.spotifyId}>
            <div>
              <p className="queue__track-name">{track.name}</p>
              <p className="queue__track-artist">{track.artist}</p>
            </div>
            <button
              onClick={() => onRemove(track.spotifyId)}
              aria-label={`Remove ${track.name}`}
            >
              &times;
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
