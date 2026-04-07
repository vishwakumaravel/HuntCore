# HuntCore Backend Stub

`backend-stub/` is the lightweight reference backend for HuntCore.

It exists mainly for:

- quick local contract testing
- debugging plugin sync without PostgreSQL
- keeping a tiny fallback implementation around

It is not the long-term production backend. `backend-api/` is the real path forward.

## What It Supports

Plugin ingest routes:

- `PUT /api/v1/servers/{serverId}/heartbeat`
- `POST /api/v1/matches`

Simple inspection routes:

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

Notes:

- format is internal to the stub
- persisted matches reload on startup
- heartbeats do not reload on startup

## When To Use It

Use `backend-stub/` if you want:

- the smallest possible local backend
- no PostgreSQL setup
- quick API contract checks

Use `backend-api/` if you want:

- real persisted backend data
- player lifetime stats
- public read endpoints for a future dashboard
