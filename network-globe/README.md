# Live Network Activity Globe

A minimal, self-contained live network traffic visualization inspired by Starlink-style globe animations.

**Goal:** Show real-time (or simulated) network connections as animated arcs and points on a 3D dark Earth globe.

This project is designed so that another AI assistant (Cursor, Codex, Grok, etc.) or a human developer can pick it up with full context.

---

## Quick Start

```bash
cd network-globe
npm install
npm start
```

Open: **http://localhost:8080**

You should see a dark rotating globe with pink glowing arcs updating every ~1.8 seconds.

---

## Project Structure

```
network-globe/
├── index.html          # Frontend – globe.gl visualization + WebSocket client
├── server.js           # Backend – HTTP server + WebSocket + traffic generator
├── package.json        # Dependencies (only `ws`)
├── README.md           # This file – full context & documentation
├── ARCHITECTURE.md     # Deeper technical design notes
├── EXTENDING.md        # How to plug in real localhost / AWS traffic
└── FUTURE.md           # Ideas for Cursor / Codex / Grok to continue
```

---

## Current Behavior

- Uses **globe.gl** (Three.js based) for the 3D globe.
- Night Earth texture + soft purple atmosphere.
- Pink/magenta arcs with animated dashes.
- Points at connection endpoints.
- Auto-rotation enabled.
- WebSocket connection on port 8080.
- Server generates **simulated** traffic between major cities every 1.8 s.
- Frontend automatically reconnects if the WebSocket drops.

---

## Dependencies

Only one production dependency:

- `ws` – WebSocket server

Everything else (globe.gl, Three.js) is loaded from CDN in the browser.

---

## How the Data Flows

```
server.js
  └── generateTraffic()  →  creates array of arcs + points
  └── broadcasts JSON every 1.8s over WebSocket

index.html
  └── WebSocket onmessage
  └── globe.arcsData(...) + globe.pointsData(...)
```

Expected payload shape:

```json
{
  "arcs": [
    {
      "startLat": 37.77,
      "startLng": -122.42,
      "endLat": 51.51,
      "endLng": -0.13,
      "color": ["#ff6b9d", "#ffffff"]
    }
  ],
  "points": [
    { "lat": 37.77, "lng": -122.42 },
    { "lat": 51.51, "lng": -0.13 }
  ]
}
```

---

## Design Decisions (for future AI context)

1. **Why globe.gl?**  
   Closest out-of-the-box match to the visual style the user liked (dark globe, glowing arcs, live feel). Very little boilerplate.

2. **Why simulated traffic first?**  
   User was still uploading a large file and waiting. Getting a beautiful working visual first was higher priority than real metrics.

3. **Why single port (8080)?**  
   Serves both the static HTML and the WebSocket. Simplest possible setup for localhost.

4. **Why no build step?**  
   Zero-config. Just `npm start`. Easy for any AI or human to run immediately.

5. **Color palette**  
   Main accent: `#ff6b9d` (pink/magenta) to match the reference animation the user shared. Occasional purple for variety.

---

## Known Limitations (intentional for v1)

- Traffic is currently **fake** (random city pairs).
- No authentication / security on the WebSocket.
- No persistence or historical data.
- No clustering or performance optimizations for very high connection counts.
- IP → geolocation is not implemented yet.

These are documented so the next AI knows what still needs work.

---

## What the User Originally Wanted

- Liked a specific “LIVE NETWORK ACTIVITY” globe animation (dark background, pink/white dots + arcs).
- Wants to run something similar on **localhost** and later on **AWS**.
- Context: user was in the middle of uploading a large 10 GB archive and discussing rate-limit frustrations with Grok.

---

## Next Logical Steps (see also EXTENDING.md and FUTURE.md)

1. Replace `generateTraffic()` with real data sources.
2. Add a simple IP-to-lat/lng lookup (MaxMind GeoLite2 or a free API).
3. Create an AWS version (VPC Flow Logs → Lambda → WebSocket / API Gateway).
4. Optional: add controls (pause, speed, filter by region, etc.).

---

## License

MIT – do whatever you want with it.
