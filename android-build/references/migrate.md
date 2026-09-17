# Migrate `android-build`

Status: **moved** 2026-09-16 HST.

Windows `bump-and-build-release.bat` / `bump-mobile-version.ps1` stay in app
trees as history. Live builders are Linux scripts in this skill, installed at
`~/.local/opt/rootrecord/android-build`.

SDK binaries: `~/.local/opt/android-sdk` (this PC cannot write `/opt`).
