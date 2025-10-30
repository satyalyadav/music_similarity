# Music Similarity Studio

Find Spotify recommendations using Last.fm + Spotify signals and listen inline with the Spotify Web Playback SDK.

## Stack
- Frontend: Vite + React + TypeScript
- Backend: Spring Boot (Java 21), WebFlux
- DB: Postgres (Docker)
- Integrations: Spotify Web API + Web Playback SDK, Last.fm API

## Quick start

1) Install Docker
- Download and install Docker Desktop from the official site: https://www.docker.com/get-started
  - Linux users can follow the Engine/Compose install guides linked there.

2) Create credentials
- Spotify App (for OAuth + playback):
  - Go to the Spotify Developer Dashboard: https://developer.spotify.com/dashboard
  - Create an app, then in Settings set a Redirect URI to `http://localhost:8080/auth/callback`.
  - Copy your Client ID and Client Secret.
- Last.fm API Key:
  - Create an API account here: https://www.last.fm/api/account/create
  - Copy your API Key.

3) Configure environment
- Copy the example file and fill in your values:
```
cp .env.example .env
```

4) Run the project
```
docker compose up --build
```

Open the app at http://localhost:5173

Click “Connect Spotify” and complete OAuth. Spotify Premium is required for in‑browser playback.

## License
MIT
