# HuntCore Backend API

`backend-api/` is the real PostgreSQL-backed backend for HuntCore.

It receives plugin sync data, stores it, and exposes the public read API that the dashboard uses.

## What Works Today

- heartbeat ingest
- finalized match ingest
- PostgreSQL persistence
- public read routes for:
  - live server state
  - recent matches
  - player lifetime stats
  - per-player recent match history
- Docker packaging

## Main Responsibilities

- store the latest heartbeat for each HuntCore server
- store finalized matches
- store per-player per-match rows for stats aggregation
- expose local verification routes
- expose public read routes for the dashboard

## Requirements

- Java 21
- PostgreSQL

## Write Routes

These are written to by the Paper plugin:

- `PUT /api/v1/servers/{serverId}/heartbeat`
- `POST /api/v1/matches`

## Verification Routes

Useful for local testing and troubleshooting:

- `GET /health`
- `GET /api/v1/servers`
- `GET /api/v1/servers/{serverId}`
- `GET /api/v1/matches?limit=50`

## Public Dashboard Routes

These are the dashboard-facing read routes:

- `GET /api/v1/public/servers`
- `GET /api/v1/public/servers/{serverId}`
- `GET /api/v1/public/matches?limit=50&offset=0`
- `GET /api/v1/public/players?sort=wins&limit=50&offset=0`
- `GET /api/v1/public/players/{playerName}`
- `GET /api/v1/public/players/{playerName}/matches?limit=50&offset=0`

Current stats include:

- live server state
- latest completed match
- recent matches
- player lifetime wins, losses, kills
- runner vs hunter role splits
- per-player recent match history

Deaths and KD are intentionally out of scope right now.

## Configuration

Environment variables:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `HUNTCORE_INGEST_API_KEY`
- `HUNTCORE_PUBLIC_ALLOWED_ORIGIN`
- `HUNTCORE_PUBLIC_STALE_THRESHOLD_SECONDS`
- `SERVER_PORT`

Defaults:

- datasource URL: `jdbc:postgresql://localhost:5432/huntcore`
- datasource username: `huntcore`
- datasource password: `huntcore`
- allowed origin: `*`
- stale threshold: `90`
- port: `8080`

If another local web server already uses `8080`, run this backend on `8081` and point HuntCore at `http://127.0.0.1:8081`.

## Local Run

From the repo root:

```powershell
.\gradlew.bat -p backend-api bootRun
```

Or build:

```powershell
.\gradlew.bat -p backend-api build
```

Flyway migrations run automatically on startup.

## Docker

The repo root Docker stack includes:

- `postgres`
- `backend-api`
- `dashboard`

Quick start:

1. copy `.env.example` to `.env`
2. adjust credentials if needed
3. run:

```powershell
docker compose up -d --build
```

Convenience launcher:

```text
start-huntcore-docker.bat
```

Useful checks:

```powershell
docker compose ps
curl http://127.0.0.1:8081/health
```

## Local Windows Launcher

If you are not using Docker, the local launcher path is:

```text
start-huntcore.bat
```

That path starts the backend locally alongside Paper and optionally the dashboard dev server.

## Deployment Status

What is done:

- backend is containerized
- Docker Compose works locally
- GitHub Actions builds and validates it
- GHCR publishing workflow exists

What is not fully finished:

- always-on public hosting
- Cloudflare Tunnel or another public backend exposure path
- final public production domain setup
