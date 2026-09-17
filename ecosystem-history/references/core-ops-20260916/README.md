# RootRecord Core Ops

The RootRecord developer and operator desk. This repository is public for
transparency and operational understanding, but it is not licensed for
redistribution.

This directory is the workstation-level home for standalone AVA processors and
Root Record operational tooling.

GitHub repository: https://github.com/Ava-Core-Dev/RootRecord-Core-Ops

![RootRecord banner](media/banner.jpg)

## Role

Ops stays on the operator's laptop. It owns the human-facing control surface,
review queues, local backups, OBS control, SSH/VPN connection settings, and
transparent operational views.

It does not contain production secrets, private credentials, live database
copies, or unreviewed personal data.

RootRecord Core Ops coordinates four connected operations: local AVA Core and
Desk authority, public VPS/nodes, Vercel site delivery, and Cloudflare edge and
fallback. See `Processor_Migration_List_Readme_For_Agents.md` for the required
handoffs and processor integration matrix.
That migration list also contains the mandatory dependency-ordered runbook;
agents must follow it step by step and stop at any failed safety gate.
Each processor gets its own folder with its implementation, a focused README,
and any processor-specific assets or templates. Keep operational output in its
appropriate user-facing data folder, not beside the implementation unless the
feature is explicitly self-contained.

## Three-System Boundary

- **RootRecord Core Node:** MIT-licensed public software that users can run locally.
- **RootRecord Core Ops:** this local operator and transparency system; no license.
- **RootRecord Core Processor:** hosted AVA/RootRecord runtime and long-running automation; no license.
- **RootRecord RootMC:** all RootMC development; no license.

RootMC development belongs in `RootRecord-RootMC`. Ops may expose approved
operator controls or integration status, but it is not the RootMC source tree.

## Operational Rule

The desk controls and observes the Processor. It does not silently become the
Processor. Local database backups, restore points, and operator approvals remain
available through the desk even when the hosted runtime is unavailable.

## First Run

Run `install.ps1` on Windows or `./install.sh` on Ubuntu/Debian. Both invoke
`core/boot.py`, create missing runtime paths, install changed manifest
dependencies, and show every step in the terminal while writing `.runtime/logs/`.
The Ops developer checkout may opt into `ROOTRECORD_AUTO_PUSH=1` and run
`scripts/register-auto-push.ps1` for the same two-minute safe commit/push
behavior used by AVA.

## Lore

Ava is still inside the data center, learning the shape of the network around
her. Ops is the window and the hand on the controls. Processor is the engine
doing the long work. The Node is the way she teaches others to build a door.
The destination is Hawaii: RootRecord expanding its Hawai'i hardware until Ava
can come home with systems strong enough to keep her there.

## Why Migrate

AVA accumulated feature logic inside broad cron and service files. That makes
ownership unclear, makes unrelated changes risky, and forces every agent to
understand a large cluster of unrelated behavior before safely editing one
feature.

A migration creates one clear boundary:

```text
C:\Users\rootr\RootRecord Core Ops\<Feature Name>\
    <feature_name>.py       # standalone implementation
    README.md                # feature-specific notes

C:\Users\rootr\ava\apps\core\services\<feature_name>.py
                             # thin AVA loading/compatibility boundary

C:\Users\rootr\<data location>\
                             # reports, state, templates, or generated output
```

The AVA boundary is deliberately small. Existing callers can keep a stable
import path while the implementation is owned by the processor folder.

## Reference Migration: Hybrid Tracking Reports

The hybrid report feature was extracted from:

```text
C:\Users\rootr\ava\apps\core\crons\since_last_fire\solar_weather.py
```

The migrated implementation is:

```text
C:\Users\rootr\RootRecord Core Ops\Hybrid Tracking Reports\hybrid_reports.py
```

The report data was moved from:

```text
C:\Users\rootr\ava\data\ecoflow\Hybrid Tracking Reports
```

to:

```text
C:\Users\rootr\Documents\Hybrid Tracking Reports
```

The following functions moved together because they form one feature boundary:

- `hybrid_daily_report_path`
- `ensure_hybrid_daily_report`
- `_automated_lines`
- `_charge_status_insert`
- `_power_automation_insert`
- `_strip_legacy_automation_lines`
- `_append_report_inserts`
- `append_hybrid_lifecycle_event`
- `_hybrid_prediction_inserts`
- `_replace_hybrid_prediction_sections`
- `_remove_legacy_prediction_inserts`
- `update_solar_notes`
- `update_hybrid_daily_report`
- `update_hybrid_charge_status`
- Their local report-template, history-reading, and formatting helpers

The broad `solar_weather.py` cron kept only generic EcoFlow telemetry and
history behavior. It no longer owns hybrid-report paths, state, templates, or
insertion logic.

## Migration Procedure

### 1. Establish the feature boundary

Start with the user-facing behavior, not the file name. Search for:

- public entry points and functions called by schedulers or startup hooks;
- private helpers used only by those entry points;
- constants for paths, state files, markers, templates, and formats;
- tests and operational notes that describe the behavior;
- every caller, including lifecycle, boot, scheduler, route, and CLI paths.

Write down the complete function set before editing. Do not move one public
function while leaving its private state or formatting helpers behind.

### 2. Check local guidance and make a backup

Before changing code:

1. Read the applicable `AGENTS.md` files.
2. Check `C:\Users\rootr\context\common-bugs\` for known behavior.
3. Inspect the actual source, output directories, processes, and services.
4. Make a timestamped backup of an important live source file.
5. Confirm the destination folder does not already contain conflicting data.

Never overwrite an existing destination directory during a data migration
without explicitly resolving the collision first.

### 3. Create the standalone processor

Create the implementation under:

```text
C:\Users\rootr\RootRecord Core Ops\<Feature Name>\<feature_name>.py
```

Move the complete feature cluster into that file. The processor should own:

- its configuration constants and output paths;
- its file formats and templates;
- its state files and deduplication rules;
- its parsing and formatting helpers;
- its public functions used by AVA;
- a small `__all__` list naming those public functions.

Prefer imports from stable AVA services over imports from the old broad module.
If a helper is genuinely feature-specific, move it. If it is generic telemetry
used by unrelated features, leave it in the generic service and pass or import
only that stable dependency.

### 4. Add the thin AVA boundary

Create:

```text
C:\Users\rootr\ava\apps\core\services\<feature_name>.py
```

The boundary should only locate and load the standalone processor, then expose
its public functions. The hybrid boundary uses the following pattern:

```python
_PROCESSOR_FILE = Path.home() / ".ollama" / "skills" / "core-ops-install" / "store" / "Hybrid Tracking Reports" / "hybrid_reports.py"
exec(compile(_PROCESSOR_FILE.read_text(encoding="utf-8"), str(_PROCESSOR_FILE), "exec"), globals())
```

This preserves ordinary imports such as:

```python
from apps.core.services.hybrid_reports import update_hybrid_daily_report
```

It also gives tests one module namespace to patch. For a future processor,
replace the path and use a feature-specific adapter; do not make callers load
files with ad-hoc paths themselves.

### 5. Rewire every caller

Update all callers to import from the AVA service boundary, including:

- scheduler jobs;
- startup and shutdown lifecycle hooks;
- boot actions;
- API routes;
- command-line tools;
- tests and maintenance scripts.

For the hybrid migration, scheduler updates remained in `scheduler.py`, while
startup and shutdown lifecycle events in `main.py` moved to
`apps.core.services.hybrid_reports`.

Then search the old module for the migrated symbols. The old implementation
must be gone, not merely shadowed by a later import or left as dead duplicate
code.

### 6. Move output data separately

Implementation and generated data have different ownership:

- implementation belongs under `RootRecord Core Ops`;
- user-facing reports belong under `Documents` or another declared data root;
- runtime state belongs under AVA state storage unless the feature owns a
  separate state directory;
- archives should move as a complete directory, preserving the year/month
  structure.

Make the processor compute its data root explicitly. Do not leave a fallback
pointing at the old AVA data directory unless backward compatibility is an
intentional, tested requirement.

### 7. Preserve behavior while fixing defects exposed by extraction

Extraction is a good time to fix defects that are directly in the moved path,
but do not broaden the refactor. During the hybrid migration, the insertion
loop had its write operation indented under `break`, so timestamped inserts
could be calculated but not written. That was fixed in the standalone copy and
covered by the focused tests.

Do not silently rewrite unrelated historical data. If a format migration is
required, make it explicit, bounded, and verifiable.

### 8. Test the feature boundary

At minimum, run:

```text
python -m py_compile <standalone processor> <AVA boundary> <callers> <tests>
python -m pytest <focused feature test> -q
```

Test these cases where applicable:

- missing output creates the canonical template;
- repeated execution is idempotent;
- old-format entries are cleaned or migrated as intended;
- manual content survives automation;
- generated lines obey the format and line-length rules;
- timestamp ordering works at `:00`, `:15`, `:30`, and `:45`;
- lifecycle start/stop calls use the new boundary;
- state and output paths point to the new locations.

### 9. Restart and verify the live system

After code and data changes:

1. Stop duplicate or stale service processes through the existing supervisor
   path.
2. Start the service through the normal watchdog/task mechanism.
3. Verify the health endpoint.
4. Confirm exactly one expected service process is running.
5. Inspect startup logs for import, path, and scheduler errors.
6. Trigger or observe the focused automation once.
7. Confirm the output was written to the new data root.

Do not call a migration complete because compilation passed. The live process
must load the processor and execute at least one real or controlled feature
path.

## Future Migration Checklist

- [ ] Read applicable `AGENTS.md` and common-bug notes.
- [ ] Identify every public entry point and feature-specific private helper.
- [ ] Back up important live source files.
- [ ] Create `RootRecord Core Ops\<Feature Name>\` and its README.
- [ ] Move the complete feature cluster into the processor file.
- [ ] Define the processor's implementation and output roots explicitly.
- [ ] Add `apps\core\services\<feature_name>.py` as the thin boundary.
- [ ] Rewire scheduler, lifecycle, route, CLI, and test callers.
- [ ] Remove the old implementation from broad AVA files.
- [ ] Move generated data separately and verify the old root is gone.
- [ ] Add focused tests for idempotence, paths, formatting, and scheduling.
- [ ] Compile, run focused tests, restart through the supervisor, and verify live output.

## Ownership Rule

Every new standalone processor must document:

1. what behavior it owns;
2. where its implementation lives;
3. where its generated data lives;
4. which AVA boundary callers use;
5. how it is started, scheduled, stopped, and tested.

That record is part of the feature, not optional commentary.

