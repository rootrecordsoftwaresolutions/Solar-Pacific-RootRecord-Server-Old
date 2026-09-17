---
name: android-sdk
description: >-
  OmniBook Android command-line SDK (ANDROID_HOME). Apps live in ava-ops,
  kilauea-alerts, and rootmc-android skills. Use when exploring sdkmanager,
  Gradle, adb, compileSdk, or ANDROID_HOME.
---

# android-sdk

This folder **is** the SDK. Do not rglob `sdk/`. Apps are other desks.

## Live paths

| Role | Path |
| --- | --- |
| ANDROID_HOME | `/home/rootrecord/.local/opt/android-sdk` |
| Builders | `~/.local/opt/rootrecord/android-build` |
| JDK 17 | `/usr/lib/jvm/java-17-openjdk-amd64` |
| sdkmanager | `$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager` |
| adb | `$ANDROID_HOME/platform-tools/adb` |
| Ava Ops app | `~/.ollama/skills/ava-ops/android` |
| Kīlauea Alerts app | `~/.ollama/skills/kilauea-alerts/android` |
| RootMC app | `~/.ollama/skills/rootmc-android/android` |

`local.properties` `sdk.dir` must match ANDROID_HOME.

## How to explore (agents)

1. Open **this** skill for the toolchain. Open the app skill for source.
2. Source env, then inspect **top-level** SDK dirs only:

```bash
. ~/.local/opt/rootrecord/android-build/android-env.sh
python3 ~/.ollama/skills/android-sdk/scripts/sdk_status.py
ls "$ANDROID_HOME"
```

3. Build via `android-build`:

```bash
~/.local/opt/rootrecord/android-build/assemble.sh ava-ops
```

Do **not** walk `sdk/platforms/*/data`. Do not cat keystores or `.env`.
Do not dump `licenses/`.

Install/update packages with `sdkmanager` from cmdline-tools. Accept licenses
only when the operator asked.

Topic index: `ava-ops`.
