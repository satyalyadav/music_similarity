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
  activateElement?: () => Promise<void>;
}

interface SpotifyNamespace {
  Player: new (options: SpotifyPlayerInit) => SpotifyPlayer;
}

export interface SpotifyPlaybackHandle {
  status: PlaybackStatus;
  error: string | null;
  product: string | null;
  play: (spotifyTrackId: string) => Promise<void>;
  pause: () => Promise<void>;
  resume: () => Promise<void>;
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

    loadSdk()
      .then(() => {
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

        player.connect().catch((err) => {
          if (!cancelled) {
            setError(err instanceof Error ? err.message : 'Unable to connect to Spotify');
            setStatus('error');
          }
        });

        playerRef.current = player;
      })
      .catch((err) => {
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

      const token = await requestPlaybackToken();
      const response = await fetch(`https://api.spotify.com/v1/me/player/play?device_id=${deviceIdRef.current}`, {
        method: 'PUT',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          uris: [`spotify:track:${spotifyTrackId}`]
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
    play,
    pause,
    resume,
    disconnect
  };
}

declare global {
  interface Window {
    Spotify?: SpotifyNamespace;
    onSpotifyWebPlaybackSDKReady?: () => void;
  }
}
