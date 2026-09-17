# Workflow: Backup Policy

Stated requirement from the original session (kept as a requirement — no
implementation was specified in the transcript, so this is the spec a build
agent should implement against):

1. **Per-edit backups:** every file edit made by the agents should be added to
   a daily zip archive, so each day's changes are recoverable as a group.
2. **Pre-edit system backups:** before making edits, archive the affected
   files/state — "the system as a whole, or however the best way to archive
   things before making edits."
3. **Periodic backups in the agents' own workflow:** the agents themselves
   should trigger a backup pass after major edits and builds — this should be
   a standing part of their operating loop, not something a human has to
   remember to run.

## Suggested shape (not yet specified in the original session — flag for the
build agent to confirm before implementing)

- Store backups on `/mnt/Projects` (the mutable workspace), never inside
  `/home/rootrecord` (see `06-permissions-and-safety/filesystem-sandboxing.md`).
- Natural fit with the existing proposal/execution gate
  (`05-workflows/proposal-execution-gate.md`): a backup of the
  pre-change state could be taken automatically as part of
  `execute_approved_archive()`, immediately before extraction.
- "Daily zip folder for each edit" suggests one zip per day, with each edit
  appended to that day's archive, rather than one zip per edit.
