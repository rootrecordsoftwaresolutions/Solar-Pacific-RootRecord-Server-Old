# Ava Desktop to Private APK Parity

Date: 2026-09-14

The Electron app at `All-Connections/ava-desktop` is the source inventory for the private mobile control panel. The APK should initially preserve the full operator surface. Features can be retired later only after they are explicitly marked redundant.

## Product Surface

The Electron renderer currently exposes these pages:

- Terminal
- Minecraft
- Streaming Ops
- Core
- Discord
- Slack
- Telegram
- Post
- Feedback
- Crons
- Desk
- Reports
- Weather GIFs
- Business Manager
- Finance
- Release
- Links
- Settings

The application contains approximately 12,000 lines across the Electron main process, preload bridge, renderer, libraries, and embedded local web/core assets.

## IPC Parity Inventory

These are the Electron bridge capabilities that must be represented in the APK or in its operations API.

### Connection and environment

- `env-status`
- `connection-get`
- `connection-save`
- `connection-test`
- `list-presets`

### Social channels and posting

- `list-discord-channels`
- `list-discord-private`
- `list-slack-channels`
- `list-telegram-chats`
- `history`
- `send`
- `post`
- `list-all-post-targets`
- `post-all`
- `discord-edit`
- `discord-delete`

### Media and content preparation

- `media-root`
- `media-list`
- `media-pick`
- `media-import`
- `media-open`
- `rewrite-preview`
- `summarize`
- `rewrite-providers`

The APK cannot reproduce desktop file pickers and local-folder opening directly. These become remote media browsing/upload/download operations backed by the operations API.

### Ava core and conversation

- `core-status`
- `core-chat`
- `core-cancel`
- `core-enhance`
- `core-gold`

The APK calls a remote API. It must not start Ollama, Python, Node, or a local Ava process.

### Feedback governance

- `feedback-targets`
- `feedback-list`
- `feedback-process-next`
- `feedback-ack`
- `feedback-dual-post`
- `feedback-delete-discord`
- `feedback-delete-slack`
- `feedback-clear-discord`
- `feedback-clear-slack`
- `feedback-clear-all`

These should remain behind an authenticated operator API and produce an audit event for every mutation.

### Scheduled work and reporting

- `cron-status`
- `cron-run`
- `cron-config`
- `reports-status`
- `activity`
- `early-login`

The APK requests status and queues actions. It does not own the scheduler. Scheduler execution remains on the current backend until the VPS exists, then moves behind the same API contract.

### Finance and business

- `finance`
- `finance-action`
- `finance-receipt`
- `biz`
- `biz-action`

Receipt upload becomes an authenticated multipart upload. Local desktop receipt selection is not part of the APK contract.

### Operations command catalog

The Electron command catalog includes these groups:

- Desk: rebuild blogs, publish RootMC website
- Crons: list schedule, run history, individual cron jobs
- Report DMs: list subscribers, test report DM
- Reports: economy brief, hour recap, urgent Telegram, morning summary, morning brief, EOD brief, channel dumps, catch-up updates, finance notifications, finance smoke, restart message, business status, clock in/out, early login, absolute ops
- Catchup: phase catchup, unreplied catchup, cron catchup, standing catchup, snappy catchup, operator catchup, full channel scan
- Lifecycle: restart Ava, rich presence, dependency check, HTTP restart, HTTP upgrade, health, status
- Cleanup: purge junk, purge dark spam, close proposals, scrub manipulation mentions
- Data: D1 export, D1 full sync, harvest reactions, log query
- Danger: disable Cloudflare crons, undeploy API workers
- Voice: startup, chimes, system status, late-night, Kilauea notices, economy notices, server notices
- Services: phpMyAdmin, Ollama model list, Ava API status

The APK should initially display the complete catalog, but actions must carry explicit risk metadata (`read`, `safe-write`, `destructive`) and require confirmation for destructive actions.

### Files, links, and release controls

- `open-folder`
- `open-path`
- `reveal-path`
- `list-links`
- `open-link`
- `release-status`
- `release-action`
- `ops-catalog`
- `ops-run`
- `ops-cancel`

Desktop path-opening actions become links, remote artifact browsing, release status, and release-job controls. The APK must never receive arbitrary filesystem paths or shell commands.

### Minecraft and Shockbyte

- `mc-status`
- `mc-log`
- `mc-control`
- `mc-rcon`

These are not local Paper/systemd controls for the mobile design. They become a Shockbyte provider adapter with:

- production server status
- test server status
- start, stop, restart
- console tail
- player list
- allowlisted RCON commands
- operation result and audit record

The APK must not contain Shockbyte panel credentials or connect directly to RCON. The authenticated operations backend owns the Shockbyte credentials and RCON connection.

### Streaming

- `stream-status`
- `stream-action`

Streaming actions should be represented as remote commands. OBS process management, local device paths, and local capture controls are laptop-only until a remote streaming host exists.

## Portable Versus Laptop-Only Behavior

### Portable to APK through an operations API

- status dashboards
- social history and posting
- feedback queue governance
- core chat and rewrite actions
- reports and cron actions
- finance and business actions
- release job status and execution
- Shockbyte server controls and RCON
- streaming status and remote actions
- notifications and audit history

### Must be replaced by a remote API abstraction

- local media picker/import
- local media folders
- local receipt picker
- local path reveal/open
- local process spawning
- local Python/Node command execution
- local Ollama lifecycle
- local cron ownership
- local OBS process control

### Should not be copied into the APK

- arbitrary shell execution
- direct credentials for Discord, Slack, Telegram, Shockbyte, Cloudflare, or databases
- raw filesystem paths
- unrestricted RCON input
- Electron preload semantics
- local service startup and shutdown logic

## Backend Contract Shape

The future operations API should expose stable capability endpoints rather than mirroring Electron IPC names forever:

```text
GET  /v1/ops/capabilities
GET  /v1/ops/health
GET  /v1/ops/activity
GET  /v1/ops/audit
GET  /v1/ops/minecraft/servers
GET  /v1/ops/minecraft/servers/:id/status
GET  /v1/ops/minecraft/servers/:id/log
POST /v1/ops/minecraft/servers/:id/actions
POST /v1/ops/minecraft/servers/:id/rcon
GET  /v1/ops/social/:surface/history
POST /v1/ops/social/:surface/messages
GET  /v1/ops/feedback
POST /v1/ops/feedback/actions
GET  /v1/ops/jobs
POST /v1/ops/jobs/:id/run
GET  /v1/ops/releases/:kind
POST /v1/ops/releases/:kind/actions
```

The API can run on the laptop through the existing Ava origin during preparation. When the VPS becomes available, the same API contract moves there without requiring an APK update.

## Migration Order

1. Freeze the Electron behavior as the parity reference.
2. Define typed request/response models for the operations API.
3. Implement a laptop-backed development adapter.
4. Implement the Shockbyte provider for Minecraft status, lifecycle, logs, and allowlisted RCON.
5. Build the private APK shell and port read-only dashboards first.
6. Port safe writes and confirmations.
7. Port destructive actions behind explicit confirmation and audit checks.
8. Add push notifications.
9. Move the operations API from laptop to VPS when available.
10. Retire Electron only after APK parity and a successful rollback test.

## Current Decision

The Electron app remains a reference implementation during the migration. It is not the long-term runtime. The private APK is the future full operator surface, while Shockbyte remains the Minecraft host and the future VPS hosts the operations API and Ava services.
