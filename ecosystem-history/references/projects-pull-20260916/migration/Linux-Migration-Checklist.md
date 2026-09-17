# Linux Migration Checklist — RootRecord / AVA Core

## Goal

Establish a Linux-first working layout for the RootRecord and AVA Core ecosystem without losing the last known-good state from the Windows-era repos.

## Phase 0 — Freeze and snapshot

Before moving any repo or path:

- Record the current branch, dirty files, and untracked files for every repo under /mnt/Projects.
- Snapshot the live data and any generated artifacts that were not yet committed.
- Preserve a timestamped archive of any repo or folder that is in a dirty or diverged state.
- Treat the backup as the stopping point, not a “maybe” state.

## Phase 1 — Root conventions

Update the root guidance to Linux-first behavior:

- Persistent knowledge: /home/rootrecord/context/
- Common bug notes: /home/rootrecord/context/common-bugs/
- Active project workspace: /mnt/Projects
- Linux host convention: /home/rootrecord as the canonical home directory

This is the first required migration step because every nested AGENTS file will inherit the same conventions.

## Phase 2 — Define the target structure

Use a clear target layout such as:

- /home/rootrecord/RootRecord/
- /home/rootrecord/RootRecord/repos/
- /home/rootrecord/RootRecord/ops/
- /home/rootrecord/RootRecord/config/
- /mnt/Projects/RootRecord Core/

Keep the root project and the live repo staging area separate. Do not collapse the old Windows paths into the new Linux layout without the repo inventory being mapped first.

## Phase 3 — Repo-by-repo migration order

Migrate in dependency order and do not mix repo types together in one pass.

1. Root operational guidance and AGENTS files
2. RootRecord Core Ops
3. Ava-Core
4. RootMC-Net
5. RootRecord-Core-Node / RootRecord-Core-Processor
6. Public-facing deployment repos
7. Archive or old Windows-only content

A repo is not considered migrated until:

- the path is consistent with Linux conventions;
- the scripts and docs no longer assume Windows-only locations;
- the runtime checks still pass;
- backup and rollback behavior are still documented.

## Phase 4 — Safety gates before runtime changes

Re-establish the hard rules before re-enabling local AI or mining behavior:

- mining and local inference must be mutually exclusive
- filesystem sandboxing remains enforced
- DB permissions remain explicit
- agent writes remain gated and auditable
- no code path may silently bypass the proposal/execution gate

## Phase 5 — Validation

After the migration setup is in place, validate:

- the root AGENTS file is Linux-first and consistent;
- the repo inventory is mapped and backed up;
- local service startup works under Linux;
- Ollama can start without the old Windows-specific assumptions;
- the runtime health checks and repo scripts still run;
- the archive and dirty state remain recoverable.

## Exit criteria

The migration is considered complete only when:

- the repos are organized under a Linux-safe layout;
- the Windows path references are either corrected or explicitly marked as legacy;
- the operational docs match the actual running filesystem;
- the backup and rollback path is still available if a repo fails to migrate.

## Immediate next task

The next concrete step is to inventory each repo under /mnt/Projects/RootRecord Core and convert it into a Linux-safe directory map before any code is edited again.
