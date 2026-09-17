# GitHub Auto-Push Readiness

Updated: 2026-09-09

## Actual Four-Repo Contract

The Ava-Core-Dev organization page and local remotes identify these four
migration worktrees:

- `Ava-Core-Dev/RootRecord-Core-Processor`
- `Ava-Core-Dev/RootRecord-Core-Node`
- `Ava-Core-Dev/RootRecord-Core-Ops`
- `Ava-Core-Dev/RootRecord-RootMC`

## Current Dry-Run

The old Ava-Core mirror pusher is not the correct activation path for this
four-repository migration. Its previous contract targeted `ava-core`,
`ava-core-private`, `all-connections`, and `web-files`; those are separate
organization repositories and must not be substituted for these four.

The actual worktrees are:

- `/home/rootrecord/RootRecord-Core-Processor`
- `/home/rootrecord/RootRecord-Core-Node`
- `/home/rootrecord/RootRecord-Core-Ops`
- `/home/rootrecord/RootRecord-RootMC`

## Current Local Repositories

- `/home/rootrecord/RootRecord-Core-Processor` -> matching remote.
- `/home/rootrecord/RootRecord-Core-Node` -> matching remote.
- `/home/rootrecord/RootRecord-Core-Ops` -> matching remote.
- `/home/rootrecord/RootRecord-RootMC` -> matching remote.

The Ava runtime checkout at `/home/rootrecord/Ava-Core` is separate from this
four-repository migration set; `/home/rootrecord/ava` is its compatibility
symlink.

These are not automatically interchangeable with the documented four-repo
contract. Auto-commit/push remains disabled until repository ownership and
branch mapping are explicitly reconciled.

## Safe Activation Sequence

1. Finish the local organization and review the four dirty worktrees.
2. Confirm each repository's default branch and build command.
3. Create a Linux four-repo dry-run wrapper with secret exclusion.
4. Run one reviewed first push per repository.
5. Enable the user timer only after the reviewed first push succeeds.

No credentials, `.env` files, runtime data, or archives belong in GitHub.