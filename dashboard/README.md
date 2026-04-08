# HuntCore Dashboard

`dashboard/` is the React frontend for HuntCore's public stats API.

It consumes the public read routes exposed by `backend-api/`:

- `GET /api/v1/public/servers`
- `GET /api/v1/public/servers/{serverId}`
- `GET /api/v1/public/matches`
- `GET /api/v1/public/players`
- `GET /api/v1/public/players/{playerName}`
- `GET /api/v1/public/players/{playerName}/matches`

## What It Shows

- live server status
- active match details when a hunt is running
- recent match history
- player lifetime wins, losses, kills, and role splits
- per-player recent match history

Deaths and KD are intentionally out of scope for this version.

## Local Development

1. Make sure `backend-api` is running.
2. Copy `.env.example` to `.env` if you want a different API base URL.
3. Install dependencies.
4. Start the Vite dev server.

Example:

```powershell
cd dashboard
npm install
npm run dev
```

Or use the repo root launcher:

```text
start-huntcore.bat
```

When the dashboard is enabled in `huntcore-stack.local.ps1`, the launcher will start:

- `backend-api`
- the Vite dashboard dev server
- Paper

Default API base URL:

```text
http://127.0.0.1:8081
```

Override with:

```text
VITE_API_BASE_URL=https://your-api-host.example.com
```

The dashboard also supports runtime config for Dockerized/self-hosted deployments.
It will use `window.__HUNTCORE_CONFIG__.apiBaseUrl` first and fall back to `VITE_API_BASE_URL`.

## Build

```powershell
cd dashboard
npm run build
```

The build script also writes `dist/404.html` so GitHub Pages can serve the SPA shell on deep links.

## Docker

The repo root now includes a Docker Compose path that serves the built dashboard from a lightweight web server container.

Default local dashboard URL:

```text
http://127.0.0.1:4173
```

If you want Docker plus Paper together in one command, use:

```text
start-huntcore-docker.bat
```

To stop the Docker services afterward, use:

```text
stop-huntcore-docker.bat
```

The container reads:

```text
HUNTCORE_DASHBOARD_API_BASE_URL
```

at startup and writes `runtime-config.js`, so you can point the same image at a different backend without rebuilding it.

## Hosting Direction

Primary target:

- Cloudflare Pages Free

Why:

- static hosting works well for a React SPA
- `_redirects` in `public/` supports BrowserRouter-style routes cleanly
- it pairs well with a separately hosted Java API

Fallback:

- GitHub Pages

The frontend can be static-hosted for free, but it still depends on an always-on `backend-api` and PostgreSQL somewhere else.

## Static Hosting Notes

- `public/_redirects` is included for Cloudflare Pages SPA routing
- `public/runtime-config.js` provides a safe empty default for static hosts and local dev
- `scripts/postbuild.mjs` copies `index.html` to `404.html` for GitHub Pages fallback behavior
- set `HUNTCORE_DASHBOARD_BASE_PATH=/your-repo-name/` before `npm run build` if you deploy to a GitHub Pages project site instead of a root domain
- set `HUNTCORE_PUBLIC_ALLOWED_ORIGIN` on `backend-api` to your deployed frontend origin when you publish publicly
