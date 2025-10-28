import { RecommendationTrackView } from '../types';
import './RecommendationCard.css';

interface Props {
  track: RecommendationTrackView;
  onAdd: (track: RecommendationTrackView) => void;
  disabled?: boolean;
  onPlay?: (track: RecommendationTrackView) => void;
  playDisabled?: boolean;
  isPlaying?: boolean;
}

export function RecommendationCard({ track, onAdd, disabled, onPlay, playDisabled, isPlaying }: Props) {

  return (
    <article className="card">
      <img
        src={track.imageUrl || `https://via.placeholder.com/180?text=${encodeURIComponent(track.name)}`}
        alt={`${track.name} cover art`}
        className="card__image"
        loading="lazy"
      />
      <div className="card__content">
        <div className="card__text">
          <p className="card__artist">{track.artist}</p>
          <h3>{track.name}</h3>
          <p className="card__score">score {track.score.toFixed(2)}</p>
        </div>
        <div className="card__actions">
          {onPlay && (
            <button type="button" className="btn" disabled={playDisabled} onClick={() => onPlay(track)}>
              {isPlaying ? 'Pause' : 'Play'}
            </button>
          )}
          <a
            className="btn btn--ghost"
            href={`https://open.spotify.com/track/${track.spotifyId}`}
            target="_blank"
            rel="noopener noreferrer"
          >
            Open in Spotify
          </a>
          <button type="button" className="btn" disabled={disabled} onClick={() => onAdd(track)}>
            {disabled ? 'Added' : 'Add to queue'}
          </button>
        </div>
      </div>
    </article>
  );
}
