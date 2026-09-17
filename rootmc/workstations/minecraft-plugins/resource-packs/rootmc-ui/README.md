# rootmc-ui resource pack

Small pack for **FancyHeadlines** / **ChatUi** glyph (`\uE000`, font `rootmc:icons`). Gradients work without this pack; the diamond mark needs it.

Command panels (`/economy`, `/reserve`, `/tax`, `/totals`, `/baltop`, `/mint`, `/try`, heartbeat) use `ChatUi`:

```
◆ Banner title
◆ Tag » body · status
[Button] [Button]
```

Gold gradient tags, pack glyph on every catalog line, no rainbow spam, no URL dumps. Deep stats stay on the website.

## Build zip

From this folder:

```powershell
cd "Plugin Building\Minecraft\resource-packs\rootmc-ui"
Compress-Archive -Path pack.mcmeta,assets -DestinationPath ..\rootmc-ui.zip -Force
Get-FileHash ..\rootmc-ui.zip -Algorithm SHA1
```

Prebuilt zip (when present): `resource-packs/rootmc-ui.zip`  
Current SHA1 (rebuild if you change assets): `41e7a49cb04917e1e0b9a283cfdc2371f32bedab`

## Host + force on Claims / Towny

1. Upload `rootmc-ui.zip` somewhere HTTPS (e.g. `https://rootmc.net/packs/rootmc-ui.zip` on Pages, or Shockbyte file host).
2. In each host’s `server.properties`:

```properties
require-resource-pack=false
resource-pack=https://YOUR_HOST/rootmc-ui.zip
resource-pack-sha1=41e7a49cb04917e1e0b9a283cfdc2371f32bedab
resource-pack-prompt=RootMC UI — headline icons
```

3. Restart the host. Players get prompted (or auto-accept if they already trust the server).

`require-resource-pack=false` keeps the server joinable if someone declines; set `true` only if you want to force the pack.

## Toggle without pack

`plugins/RootMC/rootmc-ui.yml` (written on first Root-Core / Root-Ops / Root-Play / Root-Ranks enable):

```yaml
fancy-headlines: true
use-glyph: false
```

Reload with `/rootcore reload` or restart after editing.
