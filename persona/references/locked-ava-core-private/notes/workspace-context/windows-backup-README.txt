RootMC / Windows → Linux migration backup
Created: 2026-08-01
Source: this PC (Windows)
Destination: E:\windows backup

INCLUDED
- C:\Users\store profile: Desktop, Documents, Downloads, Pictures, Videos, Music,
  Favorites, Saved Games, Contacts, Links, Searches, RootRecord
- Dotfolders: .cursor, .cloudflared, .android, .dotnet, .gradle, .nuget
- AppData\Roaming (Cursor, Discord, Minecraft, Prism, etc.)
- AppData\Local selective (Programs, RootMC, browsers, Discord) — Temp EXCLUDED
- C:\Android SDK tree
- D:\.1 Work Stations (RootMC + all workstations — needed for Linux)
- D:\.1 Work Stations\.credentials

EXCLUDED (not useful / regenerable / OS)
- C:\Windows, Program Files, ProgramData bulk, pagefile, hiberfil, Recycle Bin
- AppData\Local\Temp and package temp caches
- D:\old (~175GB legacy) — run separately if you want it
- D:\SteamLibrary (game — reinstall on Linux)
- node_modules under workstations are included (large); reinstallable via npm/gradle

Restore tip on Linux: copy Work Stations + .env/.credentials first; treat AppData as reference.
