# Future Work & Context for Other AIs

This file exists so that Cursor, Codex, Grok, or any other assistant can continue the project with full awareness of the original conversation and intent.

## Original User Context (Sep 18, 2026)

- User was frustrated with MEGA, Dropbox, GoFile, and Google Drive while trying to share a large (~10 GB) `.ollama` folder containing many thousands of files.
- Eventually decided to compress it and try `storage.to`.
- While waiting for the upload, user shared a screenshot of a beautiful “LIVE NETWORK ACTIVITY” globe animation and said they really liked it.
- User asked how to build a similar animation for **localhost and AWS**.
- This starter was created as the first concrete deliverable.

## User Preferences Observed

- Prefers simple, working solutions over complex frameworks.
- Dislikes services that force single-file uploads or freeze.
- Values good visual design (liked the specific pink/dark aesthetic).
- Currently on Starlink and has expressed frustration with rate limits / quotas on the Grok paid plan.
- Wants documentation rich enough that another AI can take over cleanly.

## Suggested Next Milestones

### Milestone 1 – Real localhost traffic
- Parse active connections
- Add basic GeoIP
- Keep the same visual style

### Milestone 2 – Polish
- Better performance with many arcs
- UI controls
- Configurable colors / speed
- Graceful handling of missing GeoIP data

### Milestone 3 – AWS
- WebSocket API Gateway or a small persistent Node service
- VPC Flow Logs or custom agent
- Authentication if needed

### Milestone 4 – Production hardening
- HTTPS
- Rate limiting
- Historical playback
- Multiple data sources

## Important Technical Constraints

- Keep the data contract simple (`arcs` + `points`) so the frontend stays stable.
- Prefer zero or minimal build tooling unless the user asks for a more complex stack.
- The visual style (dark globe, pink accents, soft atmosphere) should be preserved unless the user requests a change.

## How Another AI Should Continue

1. Read this file + `README.md` + `ARCHITECTURE.md` first.
2. Confirm the current state still runs with `npm start`.
3. Ask the user whether they want real localhost data or AWS next.
4. Prefer incremental, working changes over large rewrites.

## Files That Should Not Be Deleted

- `README.md`
- `ARCHITECTURE.md`
- `EXTENDING.md`
- `FUTURE.md`

These exist specifically to preserve context across AI hand-offs.
