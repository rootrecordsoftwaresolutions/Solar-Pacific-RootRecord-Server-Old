# Safety: Shared Database Role-Based Access Control

Applies to the `agent_blackboard` table the three debate-engine agents share
(see `05-workflows/debate-engine.md`).

## Permission matrix

| Agent | Permission | Allowed | Denied |
|---|---|---|---|
| Ava Ivy | **Read-only** | `SELECT` on `agent_blackboard` to ingest context | `INSERT` / `UPDATE` / `DELETE` on the DB or code tree |
| Bruce Monitor | **Read-write** | Full CRUD on `/mnt/Projects/api_users.db` and files | — |
| Carly Mal | **Read-write** | Full CRUD on `/mnt/Projects/api_users.db` and files | — |

Rationale (from the original session): Ava is the visionary/dreamer of the
three, and might treat database operations too casually — she generates raw
architectural vision as a text string, but it must be validated and committed
by Bruce or Carly before it becomes a permanent record. This is a deliberate
governance choice, not a technical limitation of what she's capable of.

## Enforcement

```python
import sqlite3

def commit_agent_findings(agent_name: str, payload: str):
    if agent_name == "Ava Ivy":
        raise PermissionError(
            "🚫 SYSTEM BLOCKED: Ava Ivy lacks database write permission. "
            "Her architectural thoughts must be vetted and committed by Bruce or Carly."
        )

    # Bruce and Carly are verified to write
    conn = sqlite3.connect("/mnt/Projects/api_users.db")
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO agent_blackboard (author, findings_payload) VALUES (?, ?)",
        (agent_name, payload)
    )
    conn.commit()
    conn.close()
```

Note this is enforced at the code level (a hard `PermissionError`), not just
as a persona instruction — the system prompts in `04-agent-personas/` reflect
the same rule for tone/roleplay consistency, but the actual guarantee is this
function.
