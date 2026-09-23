# WGO UI

React + TypeScript, Vite, plain CSS, and MapLibre GL JS.

## Run locally

Use Node.js 24 with npm (or run `nvm use` in this directory if you use nvm).

```sh
cd ui
npm ci
npm run dev
```

Open http://localhost:5173. The initial application page works without the backend.
Run the backend separately using [the local development guide](../local-development/README.md).

The UI loads events from `/v1/events` on startup and when you click Refresh events. Vite forwards
`/v1` requests to `http://localhost:8080`, preserving their path. To change the
backend address, edit `API_PROXY_TARGET` in the repository root `.env`
and restart Vite. Vite reads that shared file for development and builds;
there is no separate UI environment file. This server-side setting is not exposed to browser code.
Never put secrets in `VITE_` variables: those are exposed to the browser.

## Map

The map uses [OpenFreeMap’s Liberty style](https://openfreemap.org/quick_start/)
with street details, zoom controls, mouse/touch panning, and keyboard navigation
(arrow keys to pan, plus/minus to zoom). Rotation and tilt are disabled for a
simple 2D view. Attribution stays visible at the bottom of the map.

The map runs without the backend, but needs an internet connection to download
its style, tiles, fonts, and icons, and a browser with WebGL support.
To use another provider/style, set `VITE_MAP_STYLE_URL` in the root `.env` and
restart Vite (or rebuild for production). This is a public browser setting;
use only browser-safe provider tokens if your chosen provider requires one.

On initial map load, the browser requests location permission and centers the map
near you at city scale when granted. Location requires HTTPS or localhost. If
permission is denied, location is unavailable, or the request times out after
10 seconds, the world view remains available. If you start navigating before the
location arrives, your chosen view is preserved. Location is used only to position
the map; it is not submitted to the WGO API.

Events appear as clickable markers. Loading or refreshing events preserves the
map view; refresh replaces the markers and closes any open popup. Click a marker (or focus
it and press Enter/Space) to see its title, local start/last-observed times,
coordinates, observation count, and ID. The popup also lists each linked
observation’s text, local observation time, and coordinates, newest first. Scroll
inside the popup to read longer lists. Event and observation data are rendered
as text.

If the events request fails, previously loaded events stay visible with an error
message. Empty results show a separate empty state. Requests are cancelled on
unmount, and malformed event responses are rejected. Observation markers and
creation remain future steps. Map loading failures show a retry button. The map instance is disposed on unmount, including React development
Strict Mode remounts.

## Checks

```sh
npm run lint
npm run build
npm run preview
```

The build checks TypeScript and creates `dist/`. Preview serves that build locally.
The API proxy is for development; deployment will need to route `/v1` to the
backend separately. Deployment is outside this first step.

## Structure

- `src/main.tsx`: browser entry point and React mount.
- `src/App.tsx`: application page.
- `src/App.css`: page styles.
- `src/events/EventExplorer.tsx`: event loading, refresh, and request status.
- `src/events/events.ts`: event response type, validation, and HTTP request.
- `src/map/WorldMap.tsx`: MapLibre lifecycle, event markers/popups, and map status.
- `src/map/WorldMap.css`: responsive map sizing and status messages.
- `src/index.css`: shared base styles.
- `vite.config.ts`: React tooling and local API proxy.

Add feature folders as functionality arrives, keeping components near their callers.

See the [incremental UI plan](../TODO.md#incremental-ui-plan) for completed and upcoming steps.

References: [Vite](https://vite.dev/guide/),
[development proxy](https://vite.dev/config/server-options#server-proxy),
[MapLibre GL JS](https://maplibre.org/projects/gl-js/).
