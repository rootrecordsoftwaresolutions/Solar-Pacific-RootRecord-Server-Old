Ava Laptop kit
==============

Start-Ava-Laptop.cmd will:
  1) Find RootMC/Ava on any drive (kit drive first)
  2) Auto-install Node.js 20+ (winget / MSI) if missing
  3) Auto-install cloudflared (optional public tunnel) if missing
  4) Auto npm install Ava deps (scripts\ensure-deps.mjs)
  5) Create Ava Ivy handoff folders if needed
  6) Start brain (:8787) + Ava Ivy.exe

You still need RootMC\.env (secrets) and rootmc-realm-api env loader on that tree.

Override: ROOTMC_ROOT
