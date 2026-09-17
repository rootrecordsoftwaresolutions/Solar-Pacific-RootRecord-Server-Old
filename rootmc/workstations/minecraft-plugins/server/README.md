# Root Record local Paper server

**Paper 26.2** (build 60) · **Minecraft 26.2** · **Java 25**

## Start

```powershell
cd Minecraft\server
.\start_paper.bat
```

Uses `start_paper.py` to launch the newest `paper-*.jar` in this folder with `JAVA_HOME` from the batch file (Temurin 25).

## Plugins

Drop jars in `plugins/`, or from the repo root:

```powershell
cd Minecraft
.\build-with-server-jdk.bat :plugins:my-plugin:deployToServer
```

Built plugins are copied to `server/plugins/` automatically on `:build`.

RootMC config lives in **`plugins/RootMC/`** only (not at the server root): `cloud.yml`, `rootmc.yml`, `rootmc-shops.yml`, etc.

Ship **`rootmc-*.jar`** and **`rootmc-shops-*.jar`** on RootMC hosts. See `config-templates/` and `ROOTMC-DAY1-CHECKLIST.md`.

Bundled: **spark** (Paper profiler). Custom Root Record plugins go alongside it.

## Layout (keep / ignore)

| Track in git | Ignore (runtime) |
|--------------|------------------|
| `start_paper.bat`, `start_paper.py` | `world/` |
| `server.properties`, `eula.txt` | `logs/`, `cache/` |
| `config/`, `bukkit.yml`, etc. | `libraries/`, `versions/` |
| | `*.jar` (Paper jar — re-download) |

See `../.gitignore` for the full list.
