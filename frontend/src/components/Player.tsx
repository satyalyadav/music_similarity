import { useEffect, useMemo, useRef, useState } from 'react';
import { SpotifyPlaybackHandle } from '../hooks/useSpotifyPlayback';

function formatTime(ms?: number): string {
  if (ms == null || !Number.isFinite(ms)) return '0:00';
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

interface PlayerProps {
  playback: SpotifyPlaybackHandle;
  onTogglePlay?: () => void;
}

export function Player({ playback, onTogglePlay }: PlayerProps) {
  const [isScrubbing, setIsScrubbing] = useState(false);
  const [scrubMs, setScrubMs] = useState<number | undefined>(undefined);
  const scrubbingRef = useRef(false);
  const lastScrubValueRef = useRef<number>(0);

  const position = isScrubbing ? scrubMs : playback.positionMs;
  const duration = playback.durationMs ?? 0;

  const progress = useMemo(() => {
    if (!duration || position == null) return 0;
    return Math.max(0, Math.min(100, (position / duration) * 100));
  }, [position, duration]);

  const canSeek = Number.isFinite(duration) && (duration ?? 0) > 0;

  useEffect(() => {
    function handleGlobalUp() {
      if (!scrubbingRef.current) return;
      const value = lastScrubValueRef.current;
      // Commit with last value when pointer is released anywhere
      handleSeekCommit(value);
    }
    window.addEventListener('mouseup', handleGlobalUp);
    window.addEventListener('touchend', handleGlobalUp);
    return () => {
      window.removeEventListener('mouseup', handleGlobalUp);
      window.removeEventListener('touchend', handleGlobalUp);
    };
  }, [duration]);

  useEffect(() => {
    // Reset scrubbing whenever track changes so UI resumes following playback
    scrubbingRef.current = false;
    setIsScrubbing(false);
    setScrubMs(undefined);
  }, [playback.track?.id]);

  async function handleSeekCommit(value: number) {
    if (!canSeek || !scrubbingRef.current) {
      return;
    }
    scrubbingRef.current = false;
    const nextMs = Math.round((value / 100) * duration);
    setIsScrubbing(false);
    setScrubMs(undefined);
    try {
      await playback.seek(nextMs);
    } catch {
      // ignore seek errors
    }
  }

  function handleSeekChange(value: number) {
    if (!canSeek) return;
    scrubbingRef.current = true;
    setIsScrubbing(true);
    const nextMs = Math.round((value / 100) * duration);
    setScrubMs(nextMs);
    lastScrubValueRef.current = value;
  }

  const isPaused = !!playback.paused;

  const fallbackArt = useMemo(() => {
    const title = (playback.track?.name || 'Track').slice(0, 12);
    const svg = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns='http://www.w3.org/2000/svg' width='64' height='64'>
  <defs>
    <linearGradient id='g' x1='0' x2='1' y1='0' y2='1'>
      <stop offset='0%' stop-color='#111827'/>
      <stop offset='100%' stop-color='#1f2937'/>
    </linearGradient>
  </defs>
  <rect width='100%' height='100%' rx='6' fill='url(#g)'/>
  <text x='50%' y='52%' dominant-baseline='middle' text-anchor='middle' fill='#e5e7eb' font-family='sans-serif' font-size='10'>${title.replace(/&/g, '&amp;')}</text>
  <circle cx='12' cy='12' r='4' fill='#6366f1'/>
  <rect x='10' y='28' width='44' height='6' rx='3' fill='#374151'/>
  <rect x='10' y='40' width='36' height='6' rx='3' fill='#4b5563'/>
</svg>`;
    return `data:image/svg+xml;base64,${btoa(svg)}`;
  }, [playback.track?.name]);

  return (
    <div className="player" role="group" aria-label="Playback controls">
      <div className="player__meta">
        <img
          className="player__art"
          src={playback.track?.imageUrl || fallbackArt}
          alt={playback.track?.name || 'Track art'}
          onError={(e) => {
            (e.currentTarget as HTMLImageElement).src = fallbackArt;
          }}
        />
        <div className="player__titles">
          <div className="player__title">{playback.track?.name || '—'}</div>
          <div className="player__subtitle">{playback.track?.artist || ''}</div>
        </div>
      </div>
      
      <div className="player__center">
        <div className="player__control-row">
          <button
            className={`player__play ${isPaused ? 'player__play--primary' : 'player__play--secondary'}`}
            onClick={onTogglePlay}
            disabled={playback.status !== 'ready'}
            aria-label={isPaused ? 'Play' : 'Pause'}
          >
            {isPaused ? '▶' : '❚❚'}
          </button>
        </div>
        <div className="player__progress">
          <span className="player__time">{formatTime(position)}</span>
          <input
            className="player__slider"
            type="range"
            min={0}
            max={100}
            step={0.1}
            value={progress}
            style={{ ['--progress' as any]: `${progress}%` }}
            onPointerDown={(e) => {
              if (!canSeek) return;
              (e.currentTarget as HTMLInputElement).setPointerCapture?.(e.pointerId);
              scrubbingRef.current = true;
              setIsScrubbing(true);
            }}
            onChange={(e) => handleSeekChange(Number(e.target.value))}
            onPointerUp={(e) => handleSeekCommit(Number((e.target as HTMLInputElement).value))}
            onPointerCancel={(e) => handleSeekCommit(Number((e.target as HTMLInputElement).value))}
            onPointerLeave={(e) => {
              if (scrubbingRef.current) {
                handleSeekCommit(Number((e.target as HTMLInputElement).value));
              }
            }}
            aria-label="Seek"
          />
          <span className="player__time">{formatTime(duration)}</span>
        </div>
      </div>
    </div>
  );
}
