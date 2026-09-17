# Workflow: Proposal / Execution Gate (Human-in-the-Loop)

**Core rule: the agents debate and design freely, but nothing touches the real
codebase without an explicit, named human approval.** This applies to every
code-affecting task, not just the debate engine — including anything produced
by "desires" / Roadmap Contemplation Mode (see `08-future-roadmap.md`).

## Two-phase pattern

### Phase 1 — Staging & Archiving (fully automated)

1. A user prompt triggers a codebase task.
2. Ava, Bruce, and Carly collaborate entirely inside the shared SQLite
   blackboard on `/mnt/Projects` (per their RBAC — see
   `06-permissions-and-safety/database-rbac.md`).
3. All reference files, diffs, and validation checks are generated in memory
   only — nothing is written to the active project tree yet.
4. Once consensus is reached, the system bundles the complete set of changes
   into a zip named with the date and an incrementing tracking ID:
   - `Proposal-MMDDYYYY-XXX.zip` — features, refactors, structural changes
   - `Patch-MMDDYYYY-XXX.zip` — bugfixes, lint patches, vulnerability repairs
5. The archive path is printed to stdout, DB state is updated, and the
   pipeline **stops and waits**. No files change outside the staging archive.

### Phase 2 — Explicit Execution (human-gated)

A dedicated function accepts a natural-language approval phrase naming the
archive, e.g. `"Proposal-09102026-001.zip looks good to implement"`:

1. Regex-extract the archive filename from the human's message.
2. Confirm the staged file exists.
3. Unzip directly into the target project directory.
4. Report success/failure to stdout.

Nothing is ever applied automatically — every extraction requires the exact
filename to appear in an explicit human message.

## Reference implementation

```python
import os
import zipfile
import re

PROPOSAL_DIR = "/mnt/Projects/proposals_staging"
TARGET_PROJECT_DIR = "/mnt/Projects/active_codebase"

def stage_agent_proposal(archive_id: str, files_dict: dict, is_bugfix: bool = False):
    """Phase 1: Bundles reference files into a secure staging archive."""
    os.makedirs(PROPOSAL_DIR, exist_ok=True)
    prefix = "Patch" if is_bugfix else "Proposal"
    zip_filename = f"{prefix}-{archive_id}.zip"
    zip_path = os.path.join(PROPOSAL_DIR, zip_filename)

    with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for file_path, file_content in files_dict.items():
            zipf.writestr(file_path, file_content)

    print(f"📦 [Staging System] Reference file archive locked: {zip_filename}")
    print(f"🛑 Execution Paused. Awaiting explicit human permission command to implement.")

def execute_approved_archive(human_command: str):
    """Phase 2: Extracts and applies files only on explicit human authorization."""
    match = re.search(r'(Proposal|Patch)-\d{8}-\d{3}\.zip', human_command)
    if not match:
        print("❌ [Execution Denied] No valid Proposal or Patch archive filename recognized in command.")
        return

    zip_filename = match.group(0)
    zip_path = os.path.join(PROPOSAL_DIR, zip_filename)

    if not os.path.exists(zip_path):
        print(f"❌ [Execution Denied] Staged archive file '{zip_filename}' not found.")
        return

    print(f"⚡ [Gatekeeper Approved] Unpacking {zip_filename} directly to production codebase space...")
    with zipfile.ZipFile(zip_path, 'r') as zipf:
        zipf.extractall(TARGET_PROJECT_DIR)
    print("✅ [SUCCESS] Changes have been successfully applied to /mnt/Projects.")
```

## Team dynamic on this gate (for persona-consistent logging output)

- **Ava Ivy** curates the staged archive like an art catalog — stacks up
  feature blueprints and mock reference files while waiting for approval.
- **Bruce Monitor** treats the zip system with administrative satisfaction —
  ensures every reference file matches lint guidelines before deployment.
- **Carly Mal** locks the staging gate down hard, audits every byte for
  security loopholes, and calls out any teammate attempting to execute early
  in the logs (e.g. *"The user hasn't signed off on this patch yet. Back away
  from the disk."*).
