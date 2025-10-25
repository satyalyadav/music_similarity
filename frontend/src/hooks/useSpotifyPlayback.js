import { useEffect, useState } from 'react';
const SDK_URL = 'https://sdk.scdn.co/spotify-player.js';
function loadSdk() {
    if (window.Spotify) {
        return Promise.resolve();
    }
    return new Promise((resolve, reject) => {
        const existing = document.querySelector(`script[src="${SDK_URL}"]`);
        if (existing) {
            existing.addEventListener('load', () => resolve());
            existing.addEventListener('error', () => reject(new Error('Failed to load Spotify SDK')));
            return;
        }
        const script = document.createElement('script');
        script.src = SDK_URL;
        script.async = true;
        script.onload = () => resolve();
        script.onerror = () => reject(new Error('Failed to load Spotify SDK'));
        document.body.appendChild(script);
    });
}
export function useSpotifyPlayback(enabled) {
    const [status, setStatus] = useState('disabled');
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
