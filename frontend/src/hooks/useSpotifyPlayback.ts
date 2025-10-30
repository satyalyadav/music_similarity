import { useCallback, useEffect, useRef, useState } from 'react';
import { API_BASE_URL } from '../config';

const SDK_URL = 'https://sdk.scdn.co/spotify-player.js';

export type PlaybackStatus = 'disabled' | 'needs-user' | 'loading' | 'ready' | 'error';

interface UseSpotifyPlaybackOptions {
  enabled: boolean;
  userId: string | null;
}

interface SpotifyPlayerInit {
  name: string;
  getOAuthToken: (cb: (token: string) => void) => void;
  volume?: number;
}

interface SpotifyPlayer {
  connect(): Promise<boolean>;
  disconnect(): void;
  addListener(event: string, callback: (...args: any[]) => void): void;
  removeListener(event: string, callback?: (...args: any[]) => void): void;
  pause(): Promise<void>;
  resume(): Promise<void>;
  seek?(positionMs: number): Promise<void>;
  getCurrentState?: () => Promise<any>;
  setVolume?: (volume: number) => Promise<void>;
  getVolume?: () => Promise<number>;
  activateElement?: () => Promise<void>;
}

interface SpotifyNamespace {
  Player: new (options: SpotifyPlayerInit) => SpotifyPlayer;
}

export interface SpotifyPlaybackHandle {
  status: PlaybackStatus;
  error: string | null;
  product: string | null;
  positionMs?: number;
  durationMs?: number;
  paused?: boolean;
  volume?: number;
  track?: {
    id?: string | null;
    name?: string | null;
    artist?: string | null;
    imageUrl?: string | null;
  };
  play: (spotifyTrackId: string) => Promise<void>;
  pause: () => Promise<void>;
  resume: () => Promise<void>;
  seek: (positionMs: number) => Promise<void>;
  setVolume: (volume01: number) => Promise<void>;
  disconnect: () => void;
}

let loadingPromise: Promise<void> | null = null;

if (typeof window !== 'undefined' && !window.onSpotifyWebPlaybackSDKReady) {
  window.onSpotifyWebPlaybackSDKReady = () => {
    // placeholder to keep the SDK happy until we actually toggle playback
  };
}

function loadSdk(): Promise<void> {
  if (window.Spotify) {
    return Promise.resolve();
  }
  if (loadingPromise) {
    return loadingPromise;
  }
  loadingPromise = new Promise((resolve, reject) => {
    window.onSpotifyWebPlaybackSDKReady = () => {
      resolve();
    };
    const script = document.createElement('script');
    script.src = SDK_URL;
    script.async = true;
    script.onerror = () => {
      loadingPromise = null;
      reject(new Error('Failed to load Spotify SDK'));
    };
    document.body.appendChild(script);
  });
  return loadingPromise;
}

type PlaybackTokenResponse = {
  eligible: boolean;
  premium?: boolean | null;
  product?: string | null;
  missingScopes?: string[];
  accessToken?: string | null;
};

export function useSpotifyPlayback({ enabled, userId }: UseSpotifyPlaybackOptions): SpotifyPlaybackHandle {
  const [status, setStatus] = useState<PlaybackStatus>('disabled');
  const [error, setError] = useState<string | null>(null);
  const [product, setProduct] = useState<string | null>(null);
  const playerRef = useRef<SpotifyPlayer | null>(null);
  const deviceIdRef = useRef<string | null>(null);
  const [positionMs, setPositionMs] = useState<number | undefined>(undefined);
  const [durationMs, setDurationMs] = useState<number | undefined>(undefined);
  const [paused, setPaused] = useState<boolean | undefined>(undefined);
  const [volume, setVolume] = useState<number | undefined>(undefined);
  const [track, setTrack] = useState<{ id?: string | null; name?: string | null; artist?: string | null; imageUrl?: string | null } | undefined>(
    undefined
  );
  const pollTimerRef = useRef<number | null>(null);
  const lastSeekAtRef = useRef<number | null>(null);
  const lastSeekTargetRef = useRef<number | null>(null);
  const seekAnimTimerRef = useRef<number | null>(null);
  const seekAnimStartRef = useRef<number | null>(null);
  const seekAnimBaseRef = useRef<number | null>(null);
  const currentTrackIdRef = useRef<string | null>(null);
  const switchingTracksRef = useRef<boolean>(false);
  const nextTrackIdRef = useRef<string | null>(null);
  const previousVolumeRef = useRef<number | null>(null);

  async function waitForPause(timeoutMs = 1000) {
    if (!playerRef.current || typeof playerRef.current.getCurrentState !== 'function') {
      return;
    }
    const started = Date.now();
    while (Date.now() - started < timeoutMs) {
      try {
        const state = await playerRef.current.getCurrentState!();
        if (!state || state.paused) {
          return;
        }
      } catch {
        return;
      }
      await new Promise((resolve) => setTimeout(resolve, 50));
    }
  }

  async function isWidevineSupported(): Promise<boolean> {
    try {
      const anyNavigator: any = navigator as any;
      if (!('requestMediaKeySystemAccess' in anyNavigator)) {
        return false;
      }
      // Probe for Widevine EME support. This mirrors Spotify SDK requirements.
      const config: MediaKeySystemConfiguration = {
        initDataTypes: ['cenc'],
        audioCapabilities: [
          { contentType: 'audio/mp4; codecs="mp4a.40.2"' },
          { contentType: 'audio/webm; codecs="opus"' }
        ],
        distinctiveIdentifier: 'optional',
        persistentState: 'optional',
        sessionTypes: ['temporary']
      } as unknown as MediaKeySystemConfiguration;
      await (navigator as any).requestMediaKeySystemAccess('com.widevine.alpha', [config]);
      return true;
    } catch {
      return false;
    }
  }

  const requestPlaybackToken = useCallback(async () => {
    if (!userId) {
      throw new Error('Missing user ID for playback');
    }
    const response = await fetch(`${API_BASE_URL}/playback/token?userId=${encodeURIComponent(userId)}`);
    if (!response.ok) {
      const bodyText = await response.text();
      let detail = 'Unable to fetch playback token';
      if (bodyText) {
        try {
          const parsed = JSON.parse(bodyText);
          detail = parsed.message || parsed.error || detail;
        } catch {
          detail = bodyText;
        }
      }
      setProduct(null);
      throw new Error(detail);
    }
    const payload: PlaybackTokenResponse = await response.json();
    setProduct(payload.product ?? null);
    if (!payload.eligible || !payload.accessToken) {
      if (payload.premium === false) {
        throw new Error('Spotify Premium is required for Web Playback.');
      }
      if (payload.missingScopes && payload.missingScopes.length > 0) {
        throw new Error(`Reauthorize Spotify to grant: ${payload.missingScopes.join(', ')}`);
      }
      throw new Error('Playback not available for this account. Try reconnecting Spotify.');
    }
    return payload.accessToken;
  }, [userId]);

  useEffect(() => {
    if (!enabled) {
      setStatus('disabled');
      setError(null);
      if (!userId) {
        setProduct(null);
      }
      if (playerRef.current) {
        playerRef.current.disconnect();
        playerRef.current = null;
        deviceIdRef.current = null;
      }
      return;
    }
    if (!userId) {
      setStatus('needs-user');
      setProduct(null);
      return;
    }

    let cancelled = false;
    setStatus('loading');
    setError(null);

    (async () => {
      // First check for EME/Widevine support; some browsers/environments (like headless) lack it
      const supported = await isWidevineSupported();
      if (cancelled) {
        return;
      }
      if (!supported) {
        setError('This browser does not support Spotify Web Playback (DRM/Widevine). Try Chrome or Edge.');
        setStatus('error');
        return;
      }

      await loadSdk();

        if (cancelled) {
          return;
        }
        if (!window.Spotify) {
          throw new Error('Spotify SDK missing');
        }

        const player = new window.Spotify.Player({
          name: 'Music Similarity Studio',
          getOAuthToken: async (cb) => {
            try {
              const token = await requestPlaybackToken();
              cb(token);
            } catch (err) {
              if (!cancelled) {
                setError(err instanceof Error ? err.message : 'Playback auth error');
                setStatus('error');
              }
            }
          },
          volume: 0.8
        });

        player.addListener('ready', ({ device_id }: { device_id: string }) => {
          if (cancelled) {
            return;
          }
          deviceIdRef.current = device_id;
          setStatus('ready');
        });

        player.addListener('not_ready', () => {
          if (cancelled) {
            return;
          }
          setStatus('loading');
        });

        const handleError = ({ message }: { message: string }) => {
          if (cancelled) {
            return;
          }
          setError(message || 'Spotify playback error');
          setStatus('error');
        };

        player.addListener('initialization_error', handleError);
        player.addListener('authentication_error', handleError);
        player.addListener('account_error', handleError);
        player.addListener('playback_error', handleError);

        // Keep local playback state in sync for UI (progress, metadata)
        const applyState = (state: any) => {
          if (!state) {
            setPositionMs(undefined);
            setDurationMs(undefined);
            setPaused(undefined);
            setTrack(undefined);
            return;
          }

          const maybeCurrent = state.track_window?.current_track;
          if (switchingTracksRef.current) {
            if (!maybeCurrent?.id || maybeCurrent.id !== nextTrackIdRef.current) {
              return;
            }
            switchingTracksRef.current = false;
            currentTrackIdRef.current = maybeCurrent.id;
            nextTrackIdRef.current = null;
            lastSeekAtRef.current = null;
            if (seekAnimTimerRef.current) {
              window.clearInterval(seekAnimTimerRef.current);
              seekAnimTimerRef.current = null;
            }
            setPositionMs(0);
            if (typeof playerRef.current?.setVolume === 'function' && previousVolumeRef.current != null) {
              playerRef.current
                ?.setVolume(previousVolumeRef.current)
                .catch(() => undefined);
            }
            previousVolumeRef.current = null;
          }

          // Avoid clobbering an in-flight seek for a short window
          const now = Date.now();
          const withinSeekWindow = !!lastSeekAtRef.current && now - lastSeekAtRef.current < 600;
          const incomingPos = state.position ?? 0;
          if (withinSeekWindow && lastSeekTargetRef.current != null) {
            const lowerBound = Math.max(0, lastSeekTargetRef.current - 120);
            if (incomingPos < lowerBound) {
              // ignore backward jitter after seek, but animate UI forward briefly
              if (seekAnimTimerRef.current == null && seekAnimBaseRef.current != null && seekAnimStartRef.current != null) {
                seekAnimTimerRef.current = window.setInterval(() => {
                  if (!lastSeekAtRef.current || !lastSeekTargetRef.current || !seekAnimStartRef.current || !seekAnimBaseRef.current) {
                    if (seekAnimTimerRef.current) {
                      window.clearInterval(seekAnimTimerRef.current);
                      seekAnimTimerRef.current = null;
                    }
                    return;
                  }
                  const elapsed = Date.now() - seekAnimStartRef.current;
                  const base = seekAnimBaseRef.current || 0;
                  const nextUiPos = Math.min(base + elapsed, (durationMs ?? base + elapsed));
                  setPositionMs(nextUiPos);
                }, 100) as unknown as number;
              }
            } else {
              setPositionMs(incomingPos);
              if (incomingPos >= lowerBound) {
                lastSeekTargetRef.current = null;
                lastSeekAtRef.current = null;
                if (seekAnimTimerRef.current) {
                  window.clearInterval(seekAnimTimerRef.current);
                  seekAnimTimerRef.current = null;
                }
              }
            }
          } else {
            setPositionMs(incomingPos);
            if (seekAnimTimerRef.current) {
              window.clearInterval(seekAnimTimerRef.current);
              seekAnimTimerRef.current = null;
            }
          }
          setDurationMs(state.duration ?? 0);
          setPaused(!!state.paused);
          if (maybeCurrent) {
            const imageUrl = Array.isArray(maybeCurrent.album?.images) && maybeCurrent.album.images.length > 0 ? maybeCurrent.album.images[0].url : null;
            const artist = Array.isArray(maybeCurrent.artists) && maybeCurrent.artists.length > 0 ? maybeCurrent.artists.map((a: any) => a.name).join(', ') : null;
            setTrack({ id: maybeCurrent.id ?? null, name: maybeCurrent.name ?? null, artist, imageUrl });
            if (currentTrackIdRef.current && maybeCurrent.id && maybeCurrent.id !== currentTrackIdRef.current) {
              setPositionMs(0);
            }
            currentTrackIdRef.current = maybeCurrent.id ?? null;
          } else {
            setTrack(undefined);
          }
        };

        player.addListener('player_state_changed', applyState);

        // Lightweight polling to keep progress smooth between state events
        if (typeof player.getCurrentState === 'function') {
          if (pollTimerRef.current) {
            window.clearInterval(pollTimerRef.current);
          }
          pollTimerRef.current = window.setInterval(async () => {
            try {
              const state = await player.getCurrentState!();
              applyState(state);
            } catch {
              // ignore polling errors
            }
          }, 500) as unknown as number;
        }

        player.connect().catch((err) => {
          if (!cancelled) {
            setError(err instanceof Error ? err.message : 'Unable to connect to Spotify');
            setStatus('error');
          }
        });

        playerRef.current = player;
        // Initialize volume if supported
        try {
          if (typeof player.getVolume === 'function') {
            const v = await player.getVolume!();
            if (!cancelled) setVolume(v);
          }
        } catch {
          // ignore getVolume failures
        }
      })().catch((err) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Unable to load Spotify SDK');
          setStatus('error');
        }
      });

    return () => {
      cancelled = true;
      if (playerRef.current) {
        playerRef.current.disconnect();
        playerRef.current = null;
      }
      deviceIdRef.current = null;
      if (pollTimerRef.current) {
        window.clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
      if (seekAnimTimerRef.current) {
        window.clearInterval(seekAnimTimerRef.current);
        seekAnimTimerRef.current = null;
      }
    };
  }, [enabled, requestPlaybackToken, userId]);

  const play = useCallback(
    async (spotifyTrackId: string) => {
      if (!playerRef.current || !deviceIdRef.current) {
        throw new Error('Player not ready yet');
      }
      if (!userId) {
        throw new Error('Missing user ID for playback');
      }

      try {
        await playerRef.current.activateElement?.();
      } catch {
        // ignored: activateElement fails silently if already active
      }

      // If switching tracks, mute and pause via Web API to stop device audio immediately
      const activeTrackId = currentTrackIdRef.current;
      const switchingTracks = !!activeTrackId && activeTrackId !== spotifyTrackId;
      if (switchingTracks) {
        switchingTracksRef.current = true;
        nextTrackIdRef.current = spotifyTrackId;
        try {
          if (typeof playerRef.current.getVolume === 'function') {
            try { previousVolumeRef.current = await playerRef.current.getVolume!(); } catch {}
          }
          if (typeof playerRef.current.setVolume === 'function') {
            try { await playerRef.current.setVolume!(0); } catch {}
          }
          await new Promise((resolve) => setTimeout(resolve, 120));
          try {
            await playerRef.current.pause();
          } catch {}
          const tokenForPause = await requestPlaybackToken();
          await fetch(`https://api.spotify.com/v1/me/player/pause?device_id=${deviceIdRef.current}`,
            { method: 'PUT', headers: { Authorization: `Bearer ${tokenForPause}` } }
          ).catch(() => {});
          await waitForPause();
        } catch {
          // non-fatal
        }
      } else {
        currentTrackIdRef.current = spotifyTrackId;
      }

            lastSeekAtRef.current = null;
            lastSeekTargetRef.current = null;

      const token = await requestPlaybackToken();
      const response = await fetch(`https://api.spotify.com/v1/me/player/play?device_id=${deviceIdRef.current}`, {
        method: 'PUT',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          uris: [`spotify:track:${spotifyTrackId}`],
          position_ms: 0
        })
      });

      if (!response.ok) {
        if (response.status === 404) {
          throw new Error('Spotify needs an active device. Open Spotify on any device and try again.');
        }
        const message = await response.text();
        throw new Error(message || 'Unable to start playback');
      }
    },
    [requestPlaybackToken, userId]
  );

  const pause = useCallback(async () => {
    if (!playerRef.current) {
      return;
    }
    await playerRef.current.pause();
  }, []);

  const resume = useCallback(async () => {
    if (!playerRef.current) {
      return;
    }
    await playerRef.current.resume();
  }, []);

  const seek = useCallback(async (nextPositionMs: number) => {
    if (!playerRef.current || typeof playerRef.current.seek !== 'function') {
      return;
    }
    if (!Number.isFinite(nextPositionMs)) {
      return;
    }
    const clamped = Math.max(0, Math.min(nextPositionMs, durationMs ?? nextPositionMs));
    // Optimistically update position and suppress external updates briefly
    setPositionMs(clamped);
    lastSeekAtRef.current = Date.now();
    lastSeekTargetRef.current = clamped;
    seekAnimStartRef.current = Date.now();
    seekAnimBaseRef.current = clamped;
    if (seekAnimTimerRef.current) {
      window.clearInterval(seekAnimTimerRef.current);
      seekAnimTimerRef.current = null;
    }
    await playerRef.current.seek(clamped);
    try {
      const state = await playerRef.current.getCurrentState?.();
      if (state && typeof state.position === 'number') {
        setPositionMs(state.position);
        if (lastSeekTargetRef.current != null && state.position >= Math.max(0, lastSeekTargetRef.current - 120)) {
          lastSeekTargetRef.current = null;
          lastSeekAtRef.current = null;
          if (seekAnimTimerRef.current) {
            window.clearInterval(seekAnimTimerRef.current);
            seekAnimTimerRef.current = null;
          }
        }
      }
    } catch {}
  }, [durationMs]);

  const setVolumeApi = useCallback(async (next: number) => {
    if (!playerRef.current || typeof playerRef.current.setVolume !== 'function') {
      return;
    }
    const clamped = Math.max(0, Math.min(1, next));
    try {
      await playerRef.current.setVolume!(clamped);
      setVolume(clamped);
    } catch {
      // ignore setVolume errors
    }
  }, []);

  const disconnect = useCallback(() => {
    if (playerRef.current) {
      playerRef.current.disconnect();
      playerRef.current = null;
    }
    deviceIdRef.current = null;
    setStatus('disabled');
  }, []);

  return {
    status,
    error,
    product,
    positionMs,
    durationMs,
    paused,
    volume,
    track,
    play,
    pause,
    resume,
    seek,
    setVolume: setVolumeApi,
    disconnect
  };
}

declare global {
  interface Window {
    Spotify?: SpotifyNamespace;
    onSpotifyWebPlaybackSDKReady?: () => void;
  }
}
