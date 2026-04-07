# HuntCore Backend API

`backend-api/` is the real PostgreSQL-backed backend for HuntCore.

It accepts plugin ingest writes, stores match/server data, and exposes the public read API that a future React dashboard should consume.

## What It Does

- stores the latest heartbeat per HuntCore server
- stores finalized matches
- stores per-player per-match rows for lifetime stats
- exposes local verification routes
- exposes public read routes for dashboard use

## Requirements

- Java 21
- PostgreSQL

## Plugin Ingest Routes

- `PUT /api/v1/servers/{serverId}/heartbeat`
- `POST /api/v1/matches`

These routes are written to by the Paper plugin, not by the browser dashboard.

## Verification Routes

These are useful for local testing and ops:

- `GET /health`
- `GET /api/v1/servers`
- `GET /api/v1/servers/{serverId}`
- `GET /api/v1/matches?limit=50`

## Public Dashboard Routes

These are the routes a future frontend should consume:

- `GET /api/v1/public/servers`
- `GET /api/v1/public/servers/{serverId}`
- `GET /api/v1/public/matches?limit=50&offset=0`
- `GET /api/v1/public/players?sort=wins&limit=50&offset=0`
- `GET /api/v1/public/players/{playerName}`
- `GET /api/v1/public/players/{playerName}/matches?limit=50&offset=0`

Current public stats include:

- live server state
- latest completed match
- recent matches
- player lifetime wins/losses/kills
- runner vs hunter role splits
- per-player recent match history

Deaths and KD are intentionally out of scope for now.

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
- public allowed origin: `*`
- public stale threshold: `90`
- port: `8080`

If another local web server already uses `8080`, set `SERVER_PORT=8081` and point HuntCore's `backend.base-url` at `http://127.0.0.1:8081`.

## Run

From the repo root with Java 21:

```powershell
.\gradlew.bat -p backend-api bootRun
```

Or build:

```powershell
.\gradlew.bat -p backend-api build
```

Flyway migrations run automatically on startup.

## One-Click Local Startup

The simplest local Windows flow is:

```text
start-huntcore.bat
```

That launcher starts the backend and Paper together.

For a reusable local setup:

1. copy `huntcore-stack.local.example.ps1` to `huntcore-stack.local.ps1`
2. fill in your Paper path, backend port, and PostgreSQL credentials
3. use `start-huntcore.bat`

You can also call the script directly:

```powershell
.\scripts\start-huntcore-stack.ps1
```

## Public API Notes

- `/api/v1/public/*` is intentionally read-only
- public routes are currently unauthenticated by design
- CORS defaults to `*` for easy local development
- set `HUNTCORE_PUBLIC_ALLOWED_ORIGIN` to your future frontend domain when you deploy publicly
- `isOnline` is derived from the age of the latest heartbeat

## Frontend Hosting Direction

The intended next step is a React dashboard hosted separately from this backend.

Current target:

- frontend on a static host such as Cloudflare Pages
- backend-api hosted separately
- PostgreSQL hosted separately

Static frontend hosting does not replace the Java backend or database.
