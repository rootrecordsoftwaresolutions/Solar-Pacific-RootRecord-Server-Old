---
name: rootmc-android
description: >-
  RootMC Android app (Play). Use when editing the Minecraft phone app, Gradle,
  compose screens, or assembling that APK. Not the live game server.
---

# rootmc-android

This folder **is** the app. Gradle root: `android/`. Live game/Gold is the
`rootmc` skill. SDK desk: `android-sdk`.
Do not rglob the SDK. Do not cat keystores, `local.properties` secrets, or `.env`.

```bash
. ~/.local/opt/rootrecord/android-build/android-env.sh
~/.local/opt/rootrecord/android-build/assemble.sh rootmc-android
```

Ava-Core `workstations/android/rootmc` is a symlink shim.

Topic index: `rootmc`. Toolchain: `android-sdk`.
