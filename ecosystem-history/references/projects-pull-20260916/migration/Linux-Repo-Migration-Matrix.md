# Linux Repo Migration Matrix — RootRecord / AVA Core

## Evidence-based repo inventory

This file is based on the actual git status of the repos currently present under `/mnt/Projects/RootRecord Core` as of 2026-09-14.

## Migration priority order

### P0 — highest risk / do first

| Repo | Current status | Risk | Linux target location | Notes |
|---|---|---|---|---|
| `Ava-Core` | Branch: `cursor/radio-idle-obs-gates`; 2 modified files | High | `/home/rootrecord/RootRecord/repos/Ava-Core` | Existing branch divergence is already visible; do not move blindly without preserving branch state. |
| `RootRecord-Core-Ops` | Huge dirty state with many modified and untracked report artifacts | High | `/home/rootrecord/RootRecord/repos/RootRecord-Core-Ops` | This repo contains the most operational churn and must be snapshotted before any migration. |
| `Ava-Ivy-Cloud` | Clean branch, but `.runtime/` and `core/` are untracked | Medium-High | `/home/rootrecord/RootRecord/repos/Ava-Ivy-Cloud` | Treat as a live runtime repo, not a clean checkpoint. |
| `RootMC-Net` | Clean branch, but `.runtime/`, `core/`, and `scripts/` untracked | Medium-High | `/home/rootrecord/RootRecord/repos/RootMC-Net` | The runtime and scripts are not yet part of a clean repo baseline. |
| `RootRecord-Cloud` | Clean branch, but `.runtime/`, `core/`, and `scripts/__pycache__` untracked | Medium-High | `/home/rootrecord/RootRecord/repos/RootRecord-Cloud` | Same issue as above: runtime content is present but not committed as a clean state. |

### P1 — migrate next after the high-risk repos are frozen

| Repo | Current status | Risk | Linux target location | Notes |
|---|---|---|---|---|
| `RootRecord-Core-Node` | Clean branch, no dirty files | Medium | `/home/rootrecord/RootRecord/repos/RootRecord-Core-Node` | Safe to stage after the runtime repos are frozen. |
| `RootRecord-Core-Processor` | Clean branch, no dirty files | Medium | `/home/rootrecord/RootRecord/repos/RootRecord-Core-Processor` | Low risk but still belongs in the Linux layout. |
| `RootRecord-RootMC` | Clean branch, no dirty files | Medium | `/home/rootrecord/RootRecord/repos/RootRecord-RootMC` | Good candidate for a clean migration after the dirty repos are handled. |
| `Web-Files` | Clean branch, no dirty files | Low | `/home/rootrecord/RootRecord/repos/Web-Files` | Safe to move once the runtime repos are settled. |

### P2 — low-risk or archive candidates

| Repo | Current status | Risk | Linux target location | Notes |
|---|---|---|---|---|
| `All-Connections` | Clean branch, no dirty files | Low | `/home/rootrecord/RootRecord/repos/All-Connections` | Clean and straightforward. |
| `Ava-Core-Private` | Clean branch, no dirty files | Low | `/home/rootrecord/RootRecord/repos/Ava-Core-Private` | Keep separated from the public runtime tree. |

## Required freeze sequence before any repo moves

1. Record and snapshot the dirty state of each repo.
2. Preserve the current `Ava-Core` branch state (`cursor/radio-idle-obs-gates`) before any branch cleanup.
3. Archive or document the current `RootRecord-Core-Ops` dirty report tree before migrating it.
4. Treat untracked `.runtime/`, `core/`, and `scripts/` directories as live runtime material, not disposable files.
5. Do not move or merge repos until the snapshot is complete and each repo’s state is explicitly named.

## Recommended folder mapping

```text
/home/rootrecord/
├── context/
│   ├── common-bugs/
│   └── ...
├── RootRecord/
│   ├── repos/
│   │   ├── Ava-Core/
│   │   ├── Ava-Core-Private/
│   │   ├── Ava-Ivy-Cloud/
│   │   ├── RootMC-Net/
│   │   ├── RootRecord-Cloud/
│   │   ├── RootRecord-Core-Node/
│   │   ├── RootRecord-Core-Ops/
│   │   ├── RootRecord-Core-Processor/
│   │   ├── RootRecord-RootMC/
│   │   ├── Web-Files/
│   │   └── All-Connections/
│   ├── ops/
│   ├── config/
│   └── archives/
└── ...
```

## Execution order

1. Snapshot and archive `Ava-Core` and `RootRecord-Core-Ops` first.
2. Freeze `Ava-Ivy-Cloud`, `RootMC-Net`, and `RootRecord-Cloud` next.
3. Move the clean repos (`RootRecord-Core-Node`, `RootRecord-Core-Processor`, `RootRecord-RootMC`, `Web-Files`).
4. Reconcile the remaining low-risk repos.
5. Only after the Linux layout is stable should the old Windows-path references be fully retired from the root docs.

## Final rule

The migration should not be treated as a rename exercise. It is a controlled checkpoint and relocation of live operational repos, and the dirty state must remain visible until the Linux layout is proven stable.
