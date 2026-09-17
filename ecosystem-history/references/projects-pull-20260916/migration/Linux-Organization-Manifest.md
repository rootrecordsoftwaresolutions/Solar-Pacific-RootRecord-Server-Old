# Linux Organization Manifest

Updated: 2026-09-09

## Canonical Home Roots

- `Ava`: `/home/rootrecord/Ava-Core` is the live local desk checkout. The
  launcher, `.env`, data, and runtime paths use this Capitalized root.
- `Music`: `/home/rootrecord/Music` is the general music library.
- `Ava Media`: `/home/rootrecord/Ava-Core/Media` is Ava's application media root;
  it must not be merged into the general `Music` folder.
- `Documents/Migration`: migration plans, manifests, and restore notes.
- `Archives/Migration`: migrated archives and checksums.
- `Models/Ava`: local model files and model configuration.
- `Projects`: Capitalized index of active project checkouts. Its entries are
  symlinks so existing runtime paths remain stable. The four GitHub working
  directories themselves live directly under `/home/rootrecord`.
- `Media`: standalone migrated documentation payload formerly at lowercase
  `/home/rootrecord/media`.

## Moves Completed

- `Modelfile` -> `Models/Ava/Modelfile`
- `web.zip` -> `Archives/Migration/web.zip`
- `media` -> `Media`
- Added `/home/rootrecord/RootRecord-Core-Processor`,
  `/home/rootrecord/RootRecord-Core-Node`, `/home/rootrecord/RootRecord-Core-Ops`,
  and `/home/rootrecord/RootRecord-RootMC` as the four physical GitHub working
  directories.
- Added `Projects/Ava`, `Projects/RootRecord-Core-Ops`,
  `Projects/RootRecord-Cloud`, `Projects/RootMC-Net`,
  `Projects/RootRecord-Core-Node`, and `Projects/Ava-Ivy-Cloud` symlink index

## Deliberately Preserved

- The legacy Windows-shaped runtime tree under `/home/rootrecord/Ava-Core` remains
  preserved and archived at
  `ava/data/migration-archives/legacy-windows-runtime-20260909.tar.gz`.
- The active application repositories use direct Capitalized paths; no lowercase
  checkout aliases remain.
- The large `Music` and `Ava-Core/Media` trees were not merged; they have different
  ownership and retention rules.

## Next Organization Pass

1. Review the archived SQLite and historical state files.
2. Move only confirmed duplicates out of the legacy archive.
3. Add any additional purpose folders under `Documents`, `Archives`, or
   `Models` only when ownership is clear.