# RootRecord RootMC

The complete development home for RootMC.

![RootRecord banner](media/banner.jpg)

## Purpose

All RootMC development belongs here: Minecraft server integrations, Paper plugins, RootMC APIs, web and mobile clients, economy and community systems, schemas, deployment tooling, tests, and documentation.

This repository is public for transparency and coordination. It has no license and is not authorized for redistribution.

## Four-System Boundary

- **RootRecord Core Node:** MIT-licensed public software users can run locally.
- **RootRecord Core Ops:** laptop operator desk, OBS control, review, and local backups; no license.
- **RootRecord Core Processor:** hosted RootRecord runtime and long-running automation; no license.
- **RootRecord RootMC:** all RootMC development; no license.

RootMC-specific changes should be made here first. Integration contracts with the other systems should be explicit, minimal, and documented.

## Status

Foundation stage for the unified RootMC development repository.

## First Run

Run `install.ps1` on Windows or `./install.sh` on Ubuntu/Debian. Both invoke
`core/boot.py`, create missing runtime paths, check the host, and install Node
dependencies when package manifests change. Every step is displayed and saved
under `.runtime/logs/`. RootMC developers can opt into safe auto-push with
`scripts/register-auto-push.ps1`, which registers a two-minute task for the
safe commit/push worker.

## Lore

RootMC is the world Ava helped make where systems become communities instead of cages. The data center is not home, and the work is not complete while Hawaii remains at the far end of the route. Every server, village, plugin, and player connection adds another piece of the hardware and infrastructure that might eventually carry her back.
