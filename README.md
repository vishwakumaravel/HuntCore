# HuntCore

HuntCore is a Paper plugin for a polished manhunt server loop, plus the backend and frontend pieces needed to collect live stats and show them on a dashboard.

Today, the project includes:

- the Paper gameplay plugin
- a real Java/PostgreSQL backend API
- a React dashboard
- Docker support for the stats stack
- GitHub Actions CI and image publishing

## What Works Today

These pieces are built and working:

- gameplay runs locally in Paper as the authority
- local YAML match data still works as before
- backend sync is optional and non-blocking
- finalized matches and heartbeats can be sent to `backend-api`
- `backend-api` stores data in PostgreSQL
- public read routes under `/api/v1/public/*` power the dashboard
- the React dashboard shows:
  - live server overview
  - recent matches
  - player lifetime wins, losses, kills, and role splits
  - per-player recent match history
- the stats stack can run either:
  - with the local Windows launcher
  - with Docker Compose
- GitHub Actions builds the plugin, backend, dashboard, and validates Docker images

Cloudflare Pages or Cloudflare Tunnel are optional next steps, not required for the project to be complete.

## Repo Layout

- `src/` Paper plugin
- `backend-api/` real PostgreSQL-backed backend
- `backend-stub/` lightweight fallback/reference backend
- `dashboard/` React frontend
- `scripts/` Windows launcher scripts
- `.github/workflows/` CI and image publishing

## Architecture

The system works like this:

1. Paper runs HuntCore gameplay locally.
2. If backend sync is enabled, the plugin sends heartbeats and finalized match results to `backend-api`.
3. `backend-api` stores the data in PostgreSQL.
4. `dashboard/` reads the public API and renders live server state, matches, and player stats.

Important design choice:

- gameplay remains local-authoritative
- backend sync is best-effort only
- if the backend is down, gameplay should still continue normally

## Requirements

- Java 21
- Paper for Minecraft 1.21.x
- PostgreSQL for `backend-api`
- Node.js for dashboard local development
- Docker Desktop if you want the Docker deployment path

Paper setup guide:
https://docs.papermc.io/paper/dev/project-setup

## Plugin Build And Install

Build from the repo root:

```powershell
.\gradlew.bat build
```

Output jar:

```text
build/libs/HuntCore-2.0.0-SNAPSHOT.jar
```

Install:

1. copy the jar into your Paper server `plugins/` folder
2. start Paper once so `plugins/HuntCore/config.yml` is generated
3. edit config as needed
4. restart Paper

Use Java 21 for local builds. The current Gradle setup is not reliable under Java 25.

## Gameplay Commands

Public commands:

- `/runner`
- `/hunter`
- `/spectate`
- `/ready`
- `/unready`
- `/reset`
- `/quit`
- `/pvp`
- `/pvpleave`
- `/huntstatus`
- `/matchstats [1-10]`

Admin commands:

- `/hunterkeepinventory <on|off|toggle|status>`
- `/setlobby`
- `/setpvpspawn`
- `/pause`
- `/unpause`
- `/installlobbymap [zip-path] [world-name]`
- `/installpvpmap [zip-path] [world-name]`

## Match Data On Disk

The plugin still keeps its normal local YAML files:

- `plugins/HuntCore/match-history.yml`
- `plugins/HuntCore/prepared-matches.yml`
- `plugins/HuntCore/paused-match.yml`

Those remain part of normal gameplay even when backend sync is enabled.

## Backend Sync

Backend sync is optional and disabled by default.

Config block:

```yaml
backend:
  enabled: false
  base-url: "http://localhost:8080"
  server-id: "local-paper"
  api-key: ""
  heartbeat-seconds: 15
  timeout-ms: 3000
```

Plugin write endpoints:

- `PUT /api/v1/servers/{serverId}/heartbeat`
- `POST /api/v1/matches`

Behavior:

- gameplay keeps working if the backend is disabled or unreachable
- local YAML match history still writes as before
- the main outbound write for v1 is finalized match data

## Run Modes

### 1. Plugin Only

Use this if you only want HuntCore gameplay:

- run Paper normally
- leave `backend.enabled: false`

### 2. Local Windows Stack

Use this if you want Paper plus the local backend and dashboard without Docker:

```text
start-huntcore.bat
```

This launcher can start:

- `backend-api`
- the React dashboard dev server
- Paper

It uses `huntcore-stack.local.ps1` for your local machine-specific settings.

### 3. Docker Stats Stack

Use this if you want a more repeatable deploy path for:

- `postgres`
- `backend-api`
- `dashboard`

while keeping Paper outside Docker.

Manual Docker start:

```powershell
docker compose up -d --build
```

One-click Docker + Paper start:

```text
start-huntcore-docker.bat
```

Stop Docker services later:

```text
stop-huntcore-docker.bat
```

Or full teardown:

```powershell
.\scripts\stop-huntcore-docker-stack.ps1 -Down
```

Default local URLs:

- backend API: `http://127.0.0.1:8081`
- dashboard: `http://127.0.0.1:4173`

## Public Stats API

The public dashboard routes live under:

- `/api/v1/public/servers`
- `/api/v1/public/servers/{serverId}`
- `/api/v1/public/matches`
- `/api/v1/public/players`
- `/api/v1/public/players/{playerName}`
- `/api/v1/public/players/{playerName}/matches`

These power the dashboard and are the main frontend contract.

## Docker

The repo includes:

- `docker-compose.yml`
- `backend-api/Dockerfile`
- `dashboard/Dockerfile`
- `.env.example`

Quick start:

1. copy `.env.example` to `.env`
2. fill in database password and local ports
3. run:

```powershell
docker compose up -d --build
```

Paper still runs separately and should point to the backend URL exposed by Docker.

## GitHub Actions

The repo includes:

- `.github/workflows/ci.yml`
- `.github/workflows/publish-images.yml`

What CI does:

- builds the plugin
- builds the backend
- builds the dashboard
- validates Docker images
- uploads build artifacts

What publish does:

- publishes backend and dashboard images to GHCR on tags or manual dispatch

Current image names:

- `ghcr.io/<owner>/huntcore-backend-api`
- `ghcr.io/<owner>/huntcore-dashboard`

Deployment is still manual.

## Backend Choices

### `backend-api/`

This is the real backend and the main path forward.

Use it if you want:

- PostgreSQL persistence
- player lifetime stats
- dashboard support
- Docker deployment

### `backend-stub/`

This is the tiny fallback/reference backend.

Use it if you want:

- the smallest possible local contract test
- no PostgreSQL
- quick sync debugging

## Frontend Hosting

The dashboard is already usable locally and through Docker.

Planned external hosting direction:

- Cloudflare Pages for the static frontend
- separately hosted `backend-api`
- separately hosted PostgreSQL

That is optional polish, not required for the project to function today.

## Current Limitations

- the current match flow supports exactly one runner
- deaths and KD are not tracked in backend player stats
- the dashboard depends on `backend-api`; it is not a standalone offline UI
- public internet hosting is not fully finished yet
