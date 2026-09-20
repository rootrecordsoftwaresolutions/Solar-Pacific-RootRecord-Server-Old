---
name: kilauea-alerts
description: >-
  Kīlauea Alerts Android app (Play). Use when editing the volcano phone app,
  Gradle, compose screens, or assembling that APK.
---

# kilauea-alerts

This folder **is** the app. Gradle root: `android/`. SDK desk: `android-sdk`.
Do not rglob the SDK. Do not cat keystores, `local.properties` secrets, or `.env`.

```bash
. ~/.local/opt/rootrecord/android-build/android-env.sh
~/.local/opt/rootrecord/android-build/assemble.sh kilauea-alerts
```

Ava-Core `workstations/android/kilauea-alerts` is a symlink shim.

Topic index: `weather-kilauea`. Toolchain: `android-sdk`.
