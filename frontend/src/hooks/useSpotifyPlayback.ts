import { useEffect, useState } from 'react';

const SDK_URL = 'https://sdk.scdn.co/spotify-player.js';

type PlaybackStatus = 'disabled' | 'loading' | 'ready' | 'error';

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

export function useSpotifyPlayback(enabled: boolean) {
  const [status, setStatus] = useState<PlaybackStatus>('disabled');

  useEffect(() => {
    if (!enabled) {
      setStatus('disabled');
      return;
    }
    let cancelled = false;
    setStatus('loading');
    loadSdk()
      .then(() => {
        if (!cancelled) {
          setStatus('ready');
        }
      })
      .catch(() => {
        if (!cancelled) {
          setStatus('error');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [enabled]);

  return status;
}

declare global {
  interface Window {
    Spotify?: unknown;
    onSpotifyWebPlaybackSDKReady?: () => void;
  }
}
