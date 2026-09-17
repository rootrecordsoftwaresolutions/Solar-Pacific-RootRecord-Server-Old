# Cloudflare Tunnel setup for RootMC local-edge

## One-time

```powershell
winget install --id Cloudflare.cloudflared
cloudflared tunnel login
cloudflared tunnel create rootmc-local-edge
```

Copy the tunnel UUID into `config.yml` and set `credentials-file` to the JSON under `%USERPROFILE%\.cloudflared\`.

## Staging DNS

In Cloudflare DNS for `rootmc.net`, CNAME:

| Name | Target |
|------|--------|
| `api-local` | `<tunnel-id>.cfargotunnel.com` (proxied) |
| `api2-local` | same |
| `map-local` | same |
| `site-local` | same |

Or use Zero Trust → Networks → Tunnels → Public Hostname UI.

## Run

`Start-LocalEdge.ps1` starts `cloudflared tunnel --config ... run` when `cloudflared` is on PATH and `config.yml` exists with a real tunnel id.

## Production cutover

1. `Assert-PreOnlineSync.ps1 -Apply` until exit 0 (D1 pull + Discord Gateway bot + Official peer configs).
2. Fill this tunnel id, start stack **without** `-SkipTunnel`.
3. `Invoke-EdgeCutover.ps1 -Preference local` (staging first; `-AllowProduction` after validation).

Requires `CLOUDFLARE_API_TOKEN` + `CLOUDFLARE_ZONE_ID` (+ `ROOTMC_TUNNEL_ID`) in workspace `.env`.

Discord OAuth (browser `/verify`) needs redirect URI on the public API host:
`https://ava-origin.rootmc.net/v1/discord/rootmc/callback` (staging) and/or production `api.rootmc.net` after cutover — add in Discord Developer Portal.

Minecraft `/link` also works **without** tunnel via DM: `link ABC123` to the RootMC bot (`discord-link-bot.mjs`).
