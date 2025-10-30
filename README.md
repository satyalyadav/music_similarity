# Music Similarity Studio

Find Spotify recommendations using Last.fm + Spotify signals and listen inline with the Spotify Web Playback SDK.

## Quick start (Docker Compose)

Prereqs: Docker + Docker Compose, a Spotify app (client id/secret), and a Last.fm API key.

1) Create a root `.env` with your secrets:

```
# Spotify OAuth
SPOTIFY_CLIENT_ID=your_spotify_client_id
SPOTIFY_CLIENT_SECRET=your_spotify_client_secret
# Where the backend listens inside Docker
SPOTIFY_REDIRECT_URI=http://localhost:8080/auth/callback

# Last.fm (used for seed discovery)
LASTFM_API_KEY=your_lastfm_api_key

# Optional CORS (frontend served from 5173)
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
```

2) (Optional) Create `api/.env` for any local overrides. Most users can skip this.

3) Start everything:

```
docker compose up --build
```

Services:
- API: http://localhost:8080
- Frontend: http://localhost:5173

Click "Connect Spotify" in the app and complete OAuth. You’ll need a Spotify account; Premium is required for in-browser playback.

## Development (without Docker)
- Backend (Java 21 + Maven):
  - `cd api && ./mvnw spring-boot:run`
- Frontend (Node 18+):
  - `cd frontend && npm install && npm run dev`
  - Set `VITE_API_BASE_URL=http://localhost:8080`

## Environment reference
Backend reads configuration from environment variables (or `.env`):
- `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET`, `SPOTIFY_REDIRECT_URI`
- `LASTFM_API_KEY`
- `APP_CORS_ALLOWED_ORIGINS` (default `http://localhost:5173`)
- Database (auto-provisioned via Docker):
  - `SPRING_DATASOURCE_URL` (defaults to Postgres service in compose)
  - `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`

## Resetting auth while testing
- Clear browser storage: run `localStorage.removeItem('music-similarity-auth')` in the console and refresh
- Or revoke the app at your Spotify account’s Apps page and reconnect

## License
MIT
