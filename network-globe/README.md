# Live Network Activity Globe — Real Data Edition

This version removes the original simulated city-to-city traffic generator.

The server continuously observes the local machine's network sockets with `ss`, maps public remote IP addresses to approximate geographic coordinates, and pushes live arcs to the browser over WebSocket.

When launched with root privileges, it also starts `tcpdump` in metadata-only mode to count observed packets and payload lengths. It does **not** save packet payload contents.

## Run

```bash
cd network-globe
npm install
npm start
```

Then open:

```text
http://localhost:8081
```

For packet-rate and byte-rate telemetry as well as socket/process visibility where permitted:

```bash
./start-full-telemetry.sh
```

The full mode uses `sudo` only for the local collector so `tcpdump` can inspect packet metadata.

## Automatic data storage

The server creates and continuously updates:

```text
network-globe/data/state.json
network-globe/data/geo-cache.json
network-globe/data/history.json
```

`state.json` is the latest globe state. `geo-cache.json` prevents repeated geolocation requests for the same IP. `history.json` stores a rolling history of recent snapshots.

## What is being collected

- Active TCP/UDP network sockets reported by Linux `ss`.
- Local endpoint and remote endpoint IP/port.
- Protocol and, when the process table is visible, the owning process name.
- Approximate packet counts and observed payload lengths in full telemetry mode.
- Remote IP geography, ASN, organization, city/country when returned by the geolocation service.

Local/private destinations are filtered out of the globe so the map emphasizes external network flows.

## Important geography limitation

IP geolocation is approximate. It represents the network registration/estimated location of an IP and should not be interpreted as the physical location of an individual or exact server.

The default geolocation integration uses ipapi.co because it provides an HTTPS JSON endpoint for IPv4/IPv6 geolocation. The collector caches results so the same IP is not repeatedly looked up.

## Optional origin override

To avoid automatic public-IP discovery, set a fixed origin yourself:

```bash
ORIGIN_LAT=21.3 ORIGIN_LNG=-157.8 ORIGIN_LABEL="Network Core" npm start
```

Or run with:

```bash
PUBLIC_IP=1.2.3.4 npm start
```

## Embed the globe

The same page can be used in an iframe:

```html
<iframe
  src="http://YOUR-HOST:8081/?embed=1"
  width="100%"
  height="700"
  style="border:0"
  loading="lazy"
></iframe>
```

The `embed=1` flag hides the on-screen HUD while leaving the live globe active.

## Architecture

```text
Linux sockets / packet metadata
          |
          v
       server.js
          |
   +------+-------+
   |              |
   v              v
  ss            tcpdump
   |              |
   +------+-------+
          v
     flow state
          |
     IP geolocation
          |
          v
     WebSocket JSON
          |
          v
      index.html
          |
          v
       globe.gl
```

No random city generator remains in the runtime.
