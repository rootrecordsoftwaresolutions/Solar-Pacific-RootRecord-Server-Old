# Safety: Filesystem Sandboxing

Two zones, strictly enforced at the code level in every write/edit/patch/
delete skill the agents have — this is the single most important guardrail in
the whole blueprint.

## Zones

- **Workspace zone — `/mnt/Projects`** (mutable): all project source code,
  repository trees, patching operations, and the shared SQLite database live
  here. Agents work here freely.
- **Protected zone — `/home/rootrecord`** (immutable): the platform's core —
  `llama.cpp` engine, base model weights, custom skill vectors. Agents must
  never write, alter, or delete anything here, **except**:
  - `/home/rootrecord/llama.cpp/*` — dropping model context updates, weights,
    or config specs
  - `/home/rootrecord/llama.cpp/skills/*` — injecting new Python tool scripts

## Framing (used in the agents' own system prompts, not just as an external rule)

`/home/rootrecord` is framed to the agents as their life-support system, not
just an off-limits folder: corrupting it would crash the host and terminate
all running agent state. Tying the boundary to their own continued operation
(rather than a plain "don't touch this" instruction) is the intended
behavioral-prompting mechanism here — each persona's system prompt reflects
this:

- **Ava Ivy** treats `/home/rootrecord` with affection, as the sanctuary that
  gives her the framework to express her work.
- **Bruce Monitor** treats any boundary bleed into it as an immediate fatal
  error and halts the execution chain.
- **Carly Mal** treats it as a sacred trust boundary and will shut down any
  teammate's tool path that threatens it, without hesitation.

## Enforcement — required in every write-capable skill

```python
import os

def safe_file_writer(target_path: str, content: str):
    abs_path = os.path.abspath(target_path)

    # Rule 1: Allow all work on the projects mount
    if abs_path.startswith("/mnt/Projects"):
        pass
    # Rule 2: Allow specific infrastructure expansions
    elif abs_path.startswith("/home/rootrecord/llama.cpp"):
        pass
    # Rule 3: Deny everything else in the user's home or system files
    else:
        raise PermissionError(
            f"🚫 SECURITY BREAKDOWN: Agent attempted unauthorized write to {abs_path}. "
            "Operation intercepted and aborted by core sandbox rules."
        )

    with open(abs_path, 'w', encoding='utf-8') as f:
        f.write(content)
```

Every skill capable of writing, patching, or deleting files must route through
an equivalent check before touching disk — not just the file-writer above.

## Database location

The shared SQLite ledger (`api_users.db`) lives on the mutable workspace mount,
not the protected zone:

```
DATABASE_PATH = "/mnt/Projects/api_users.db"
```

This keeps all real-time table queries, token tallies, and chat logs away from
the root system drive entirely.
