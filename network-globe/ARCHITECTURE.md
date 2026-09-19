# Architecture Notes

## High-Level Diagram

```
┌────────────────────┐
│   Browser          │
│  (index.html)      │
│                    │
│  globe.gl instance │
│  WebSocket client  │
└─────────┬──────────┘
          │ ws://localhost:8080
          ▼
┌────────────────────┐
│   Node.js Server   │
│  (server.js)       │
│                    │
│  HTTP static       │
│  WebSocketServer   │
│  Traffic generator │
└────────────────────┘
```

## Frontend (`index.html`)

- Single HTML file, no build tools.
- Loads `globe.gl` from unpkg CDN.
- Creates a full-viewport globe.
- Key visual settings:
  - `globeImageUrl`: night Earth texture
  - `atmosphereColor`: soft purple
  - `arcColor`, `arcDashLength`, `arcDashAnimateTime` for the moving arc effect
  - `pointColor` / `pointRadius` for the glowing dots
- Auto-rotate is enabled via `globe.controls()`.
- WebSocket logic includes automatic reconnection.

## Backend (`server.js`)

- Uses only the built-in `http` module + `ws`.
- Serves `index.html` on `/`.
- Maintains a list of ~15 major cities with lat/lng.
- Every 1.8 seconds:
  - Picks 12–25 random city pairs
  - Builds arc + point objects
  - Broadcasts to all connected WebSocket clients
- On new connection, immediately sends one payload so the globe is never empty.

## Data Contract

The only thing the frontend cares about is this shape:

```ts
interface Payload {
  arcs: Array<{
    startLat: number;
    startLng: number;
    endLat: number;
    endLng: number;
    color?: string | string[];
  }>;
  points: Array<{
    lat: number;
    lng: number;
  }>;
}
```

Any future real-data source only needs to emit objects matching this interface.

## Performance Notes

- globe.gl handles hundreds of arcs reasonably well.
- For thousands of concurrent connections you would want:
  - Arc clustering / sampling
  - Points merge (already enabled)
  - Possibly switch to a custom Three.js shader for particles

## Security / Production Notes

Current version is **localhost-only friendly**:

- No CORS restrictions beyond defaults
- No auth on WebSocket
- No rate limiting
- No HTTPS

When moving to AWS these will need to be addressed (API Gateway WebSocket + auth, or a proper backend).
