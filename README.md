# Music Similarity Studio

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)

This is a simple app that came from the idea of finding music using a single song you like a lot. It uses Spotify and Last.fm to try and come up with songs similar to your favorite song. It is is a pretty simple app right now but it could be developed into more to find even more authentic recommendations. I might deploy it to a service. I am looking at ways to take Spotify login out of this so users can use it without logging in. Enjoy!

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
MIT — see [LICENSE](./LICENSE)
