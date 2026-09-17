---
name: android-build
description: >-
  Linux Android build helpers (bump version, assemble, stage APK/AAB). Use when
  building Ava Ops, Kīlauea Alerts, or RootMC APKs on this OmniBook. Not the SDK
  itself (android-sdk) and not app source.
---

# android-build

This folder **is** the builder. Installed at
`~/.local/opt/rootrecord/android-build` (symlink here). SDK is
`~/.local/opt/android-sdk`. JDK 17 is `/usr/lib/jvm/java-17-openjdk-amd64`.
Do not invent watts, SOC, or player counts. Do not cat keystores.

```bash
. ~/.local/opt/rootrecord/android-build/android-env.sh
~/.local/opt/rootrecord/android-build/assemble.sh ava-ops          # debug
~/.local/opt/rootrecord/android-build/assemble.sh kilauea-alerts --release
~/.local/opt/rootrecord/android-build/assemble.sh rootmc-android --release --bump
```

Apps: `ava-ops`, `kilauea-alerts`, `rootmc-android` (see `apps.json`).
Topic: `android-sdk`.
