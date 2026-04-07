# HuntCore

HuntCore is a Paper plugin for a polished manhunt server loop: one runner, one or more hunters, fresh temporary match worlds, portal-aware tracking, persistent pause/resume, a configurable parkour waiting lobby, and a separate PvP arena side mode.

The repo now contains both the gameplay plugin and the backend pieces needed for stats and a future public dashboard.

## What Is In This Repo

- `src/` contains the Paper plugin
- `backend-api/` contains the real PostgreSQL-backed backend
- `backend-stub/` contains the lightweight fallback/reference backend
- `scripts/` contains the Windows startup helpers

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
- public read routes under `/api/v1/public/*` for a future dashboard
- player lifetime stats based on finished match data

This is the backend the future React dashboard should use.

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
- wait for backend health
- launch Paper
- shut the backend down when Paper exits if the launcher started it

For a reusable local setup, copy:

```text
huntcore-stack.local.example.ps1
```

to:

```text
huntcore-stack.local.ps1
```

and fill in your local Paper path, backend port, and PostgreSQL credentials. The local file is git-ignored.

If you prefer the script directly:

```powershell
.\scripts\start-huntcore-stack.ps1
```

## Public Stats API

The public read API now lives under:

- `/api/v1/public/servers`
- `/api/v1/public/servers/{serverId}`
- `/api/v1/public/matches`
- `/api/v1/public/players`
- `/api/v1/public/players/{playerName}`
- `/api/v1/public/players/{playerName}/matches`

These routes are the intended contract for a future React stats dashboard.

## Future Frontend Direction

The current plan is:

- keep the Java backend separate
- build a React dashboard against `/api/v1/public/*`
- target a static host such as Cloudflare Pages for the frontend

Static hosting only solves the frontend. The Java API and PostgreSQL still need their own host.

## Current Limitations

- The current match flow supports exactly one runner
- Deaths and KD are not tracked in backend player stats
- The React dashboard is not built yet
- The backend is ready for public read traffic, but final always-on internet hosting is still a separate deployment step
