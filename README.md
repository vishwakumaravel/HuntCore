# HuntCore

HuntCore is a Paper plugin for a polished manhunt server loop: one runner, one or more hunters, fresh temporary match worlds, portal-aware tracking, persistent pause/resume, a configurable parkour waiting lobby, and a separate PvP arena side mode.

The repo now contains the gameplay plugin plus the backend and frontend pieces needed for live stats and a public dashboard.

## What Is In This Repo

- `src/` contains the Paper plugin
- `backend-api/` contains the real PostgreSQL-backed backend
- `backend-stub/` contains the lightweight fallback/reference backend
- `dashboard/` contains the React stats frontend
- `scripts/` contains the Windows startup helpers
- `.github/workflows/` contains CI and image publishing workflows

## Requirements

- Java 21
- Paper for Minecraft 1.21.x
- PostgreSQL if you want to use `backend-api`

Paper setup guide:
https://docs.papermc.io/paper/dev/project-setup

## Gameplay Highlights

- One runner versus one or more hunters
- Infinite hunter respawns
- Fresh temporary overworld, Nether, and End per round
- Runner wins by killing the Ender Dragon in the fresh match End
- Cross-dimension hunter compass tracking with portal memory
- Spectator mode that stays out of ready checks and win conditions
- Persistent `/pause` and `/unpause`, including clean server restart resume
- Prescouted match-world cache with nearby POI selection
- Importable waiting-lobby maps and PvP arena maps from `.zip` worlds
- In-game `/huntstatus` and `/matchstats`

## Build The Plugin

From the project root:

```powershell
.\gradlew.bat build
```

Use Java 21 for local builds. The current Gradle setup is not reliable under Java 25 yet.

Output jar:

```text
build/libs/HuntCore-2.0.0-SNAPSHOT.jar
```

## Install The Plugin

1. Build the jar.
2. Copy `build/libs/HuntCore-2.0.0-SNAPSHOT.jar` into your Paper server `plugins/` folder.
3. Start the server once so `plugins/HuntCore/config.yml` is generated.
4. Adjust config values if needed.
5. Restart the server.

## Commands

Public commands:

- `/runner` select the runner role
- `/hunter` select the hunter role
- `/spectate` toggle spectator mode
- `/ready` mark yourself ready
- `/unready` remove your ready status
- `/reset` return to the waiting lobby spawn
- `/quit` leave the current match, forfeit, and return to the waiting lobby
- `/pvp` enter the PvP arena
- `/pvpleave` leave the PvP arena and restore your previous state
- `/huntstatus` show current lobby, cache, and match status
- `/matchstats [1-10]` show recent recorded match results

Admin commands:

- `/hunterkeepinventory <on|off|toggle|status>` toggle hunter keep-inventory behavior
- `/setlobby` save your current location as the waiting lobby spawn
- `/setpvpspawn` save your current location as the PvP arena spawn
- `/pause` pause the current match
- `/unpause` resume a paused match when the runner and at least one hunter are online
- `/installlobbymap [zip-path] [world-name]` import a dedicated waiting-lobby world
- `/installpvpmap [zip-path] [world-name]` import a dedicated PvP arena world

## Match Data On Disk

The plugin still keeps its local YAML data:

- `plugins/HuntCore/match-history.yml`
- `plugins/HuntCore/prepared-matches.yml`
- `plugins/HuntCore/paused-match.yml`

Those files remain part of the normal gameplay flow even when backend sync is enabled.

## Optional Backend Sync

HuntCore can push best-effort backend updates without affecting gameplay.

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

Plugin-side write endpoints:

- `PUT /api/v1/servers/{serverId}/heartbeat`
- `POST /api/v1/matches`

Behavior notes:

- backend sync is optional and disabled by default
- gameplay remains local-authoritative if the backend is down
- local YAML match history still writes as before
- if another local web server already uses `8080`, point HuntCore at another port such as `8081`

## Backend Options

### `backend-api/`

This is the real backend.

It provides:

- ingest routes for plugin heartbeats and finished matches
- PostgreSQL-backed storage
- verification routes for local ops/testing
- public read routes under `/api/v1/public/*` for the React dashboard
- player lifetime stats based on finished match data

This is the backend the React dashboard should use.

See [backend-api/README.md](/c:/Users/vkper/Downloads/HuntCore/backend-api/README.md).

### `backend-stub/`

This is the lightweight fallback/reference backend.

It is still useful for:

- very lightweight local contract testing
- debugging plugin sync without PostgreSQL
- keeping a minimal reference implementation around

It is not the long-term production path.

See [backend-stub/README.md](/c:/Users/vkper/Downloads/HuntCore/backend-stub/README.md).

## Local Startup

For the closest replacement to the old one-click `start.bat` workflow, use:

```text
start-huntcore.bat
```

That launcher can:

- start `backend-api`
- start the React dashboard
- wait for backend health
- launch Paper
- shut the backend and dashboard down when Paper exits if the launcher started them

For a reusable local setup, copy:

```text
huntcore-stack.local.example.ps1
```

to:

```text
huntcore-stack.local.ps1
```

and fill in your local Paper path, backend port, and PostgreSQL credentials. The local file is git-ignored.

Default local dashboard URL:

```text
http://127.0.0.1:4173
```

## Docker Startup

For a one-command Docker-backed startup, use:

```text
start-huntcore-docker.bat
```

That launcher will:

- start or refresh `postgres`, `backend-api`, and `dashboard` with Docker Compose
- wait for backend and dashboard readiness
- launch Paper afterward

When Paper exits, the Docker services stay running by default so the dashboard and backend can remain available.

To stop the Docker services later, use:

```text
stop-huntcore-docker.bat
```

If you want a full teardown instead of a stop, run:

```powershell
.\scripts\stop-huntcore-docker-stack.ps1 -Down
```

If you prefer the script directly:

```powershell
.\scripts\start-huntcore-stack.ps1
```

## Docker Deployment

The repo now includes a Docker deployment path for the stats stack:

- `postgres`
- `backend-api`
- `dashboard`

Paper stays outside Docker in this phase so your live gameplay setup does not have to change.

Quick start:

1. Copy `.env.example` to `.env`
2. Adjust database password, allowed origin, and dashboard API URL if needed
3. Start the stack

```powershell
docker compose up -d --build
```

Default local container URLs:

- backend API: `http://127.0.0.1:8081`
- dashboard: `http://127.0.0.1:4173`

If you want Docker plus Paper together in one step, use `start-huntcore-docker.bat` instead of running Compose and Paper separately.

For a deployed Paper server, point HuntCore's `backend.base-url` at the Dockerized backend URL instead of your local Windows launcher port.

## Public Stats API

The public read API now lives under:

- `/api/v1/public/servers`
- `/api/v1/public/servers/{serverId}`
- `/api/v1/public/matches`
- `/api/v1/public/players`
- `/api/v1/public/players/{playerName}`
- `/api/v1/public/players/{playerName}/matches`

These routes are the intended contract for the React stats dashboard in `dashboard/`.

## Dashboard Frontend

Phase 3 now lives in `dashboard/`.

It provides:

- live server overview
- recent match history
- player lifetime stats
- per-player recent match history

The dashboard reads `/api/v1/public/*` directly and is meant to stay a static frontend.

Primary hosting target:

- Cloudflare Pages

Fallback:

- GitHub Pages

Static hosting only solves the frontend. The Java API and PostgreSQL still need their own host.

See [dashboard/README.md](/c:/Users/vkper/Downloads/HuntCore/dashboard/README.md).

## GitHub Actions

The repo now includes GitHub Actions for:

- plugin CI build
- backend API CI build
- dashboard CI build
- Docker image build validation
- GHCR image publishing on tags or manual dispatch

Workflow files:

- `.github/workflows/ci.yml`
- `.github/workflows/publish-images.yml`

The workflows publish:

- `ghcr.io/<owner>/huntcore-backend-api`
- `ghcr.io/<owner>/huntcore-dashboard`

Deployment is still manual:

```powershell
docker compose pull
docker compose up -d
```

## Current Limitations

- The current match flow supports exactly one runner
- Deaths and KD are not tracked in backend player stats
- The backend is ready for public read traffic, but final always-on internet hosting is still a separate deployment step
