# HuntCore Dashboard

`dashboard/` is the React frontend for HuntCore's public stats API.

It reads from `backend-api` and shows live state, match history, and player lifetime stats.

## What Works Today

- live server overview
- recent matches page
- player leaderboard
- per-player recent match history
- local development with Vite
- Dockerized self-hosted dashboard

## API Contract

The dashboard reads:

- `GET /api/v1/public/servers`
- `GET /api/v1/public/servers/{serverId}`
- `GET /api/v1/public/matches`
- `GET /api/v1/public/players`
- `GET /api/v1/public/players/{playerName}`
- `GET /api/v1/public/players/{playerName}/matches`

## Stats Shown

- live server state
- active match details when a hunt is running
- recent match history
- player lifetime wins, losses, kills, and role splits
- per-player recent match history

Deaths and KD are intentionally out of scope for this version.

## Local Development

1. make sure `backend-api` is running
2. optionally copy `.env.example` to `.env`
3. install dependencies
4. run the dev server

Example:

```powershell
cd dashboard
npm install
npm run dev
```

Default local API base URL:

```text
http://127.0.0.1:8081
```

Override with:

```text
VITE_API_BASE_URL=https://your-api-host.example.com
```

## Runtime Config

The dashboard supports two API-base paths:

1. `window.__HUNTCORE_CONFIG__.apiBaseUrl`
2. `VITE_API_BASE_URL`

That means:

- Docker/self-hosted mode can inject the API URL at container startup
- static-hosted mode can use the build-time Vite env var

## Local Launchers

Without Docker:

```text
start-huntcore.bat
```

With Docker:

```text
start-huntcore-docker.bat
```

To stop the Docker services afterward:

```text
stop-huntcore-docker.bat
```

## Build

```powershell
cd dashboard
npm run build
```

The build script also writes `dist/404.html` so GitHub Pages-style SPA fallback works.

## Docker

The repo includes a Dockerized dashboard path served by nginx.

Default local URL:

```text
http://127.0.0.1:4173
```

The container reads:

```text
HUNTCORE_DASHBOARD_API_BASE_URL
```

at startup and writes `runtime-config.js`, so the same image can point at different backend URLs without rebuilding.

## Hosting Status

What is ready:

- local dev
- local Windows launcher
- Dockerized self-hosting

What is not fully finished:

- final public Cloudflare Pages deployment
- final public backend exposure and production CORS/domain setup

## Static Hosting Notes

- `public/_redirects` supports Cloudflare Pages SPA routing
- `public/runtime-config.js` provides a safe empty default
- `scripts/postbuild.mjs` copies `index.html` to `404.html` for GitHub Pages fallback behavior
- `HUNTCORE_DASHBOARD_BASE_PATH` can be used for subpath deployments such as GitHub Pages project sites
