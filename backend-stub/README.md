# HuntCore Backend Stub

`backend-stub/` is the tiny reference backend for HuntCore.

It exists for quick local testing when you do not want PostgreSQL or the full real backend.

## What It Is Good For

- lightweight plugin contract testing
- quick debugging of plugin sync
- keeping a minimal fallback implementation around

It is not the long-term production backend. `backend-api/` is the real path forward.

## Supported Routes

Plugin ingest routes:

- `PUT /api/v1/servers/{serverId}/heartbeat`
- `POST /api/v1/matches`

Inspection routes:

- `GET /health`
- `GET /api/v1/servers`
- `GET /api/v1/servers/{serverId}`
- `GET /api/v1/matches?limit=50`

## Storage Behavior

- heartbeats are memory-only
- finalized matches are kept in memory
- finalized matches are also persisted to a small local file so they survive restarts

So the stub is partly persistent, but only for finished matches.

## Run

From the repo root with Java 21:

```powershell
.\gradlew.bat -p backend-stub run
```

Or build:

```powershell
.\gradlew.bat -p backend-stub build
```

Default bind:

- host: `0.0.0.0`
- port: `8080`

## Environment Variables

- `HUNTCORE_HOST`
- `HUNTCORE_PORT`
- `PORT`
- `HUNTCORE_API_KEY`
- `HUNTCORE_DATA_DIR`

If `HUNTCORE_API_KEY` is set, write requests must include `X-HuntCore-Api-Key`.

## Persistence

Default persisted file:

```text
data/matches.log
```

Persisted matches reload on startup.
Heartbeats do not reload on startup.

## When To Use It

Use `backend-stub/` if you want:

- the smallest possible local backend
- no PostgreSQL
- a quick sync/debug target

Use `backend-api/` if you want:

- real persistence
- player lifetime stats
- dashboard support
- Docker deployment
- the main project architecture
