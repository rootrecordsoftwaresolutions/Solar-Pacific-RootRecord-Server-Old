# Architecture

## Runtime flow

1. `server.js` discovers local addresses with `ip -j addr`.
2. The server discovers the public-network origin using the configured origin or ipapi.co.
3. `ss -H -tun` is polled every `POLL_MS` milliseconds.
4. Public remote peers are retained as candidate globe endpoints.
5. Previously unseen endpoints are queued for IP geolocation and cached locally.
6. In root/full-telemetry mode, `tcpdump` observes packet metadata to add packet and byte activity.
7. The server builds a `{ type, origin, arcs, points, stats }` payload and broadcasts it over WebSocket.
8. The browser renders the payload with globe.gl.
9. The same payload is persisted to `data/state.json` and a rolling `data/history.json`.

## Privacy model

Payload contents are never written to disk. Private/local IPs do not become globe destinations. The browser receives approximate geographic endpoint data, not raw remote IPs as a required part of the visual model.

## Geo cache

`data/geo-cache.json` stores resolved endpoint metadata. Entries are reused for `GEO_TTL_MS` to avoid unnecessary API traffic.
