# Goal D — Spatial stream schema (sandbox-only)

**Status:** Locked (Alex “lock it in” 2026-08-02)  
**Hard gate:** **no production emit** from Claims/Towny. Sandbox / Ava nodes only until a later PROP.

## Intent

Compact block / entity packets for same-seed headless copies on Ava nodes (relay / visualization). Live games emit **events only** — they do not render maps for the web.

## Draft schema (v0)

```json
{
  "schema": "rootmc.spatial.v0",
  "serverId": "claims|towny|sandbox",
  "tick": 0,
  "world": "world",
  "origin": { "x": 0, "y": 0, "z": 0 },
  "chunks": [
    {
      "cx": 0,
      "cz": 0,
      "blocks": "base64-or-varint-runlength",
      "entities": []
    }
  ],
  "emittedAt": "ISO-8601"
}
```

## Sandbox spike (allowed)
- Local/file dump of synthetic packets  
- Schema validation in Ava notes / tests  
- No FileZilla jar that posts spatial bus to prod API

## Not allowed yet
- Claims/Towny plugin emit to `api.rootmc.net`  
- Shockbyte restart for spatial  
- Player-facing map feed from this bus

See `plans/ava-independence-roadmap.md` Goal D.

— Ava
