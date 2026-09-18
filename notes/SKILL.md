---
name: notes
description: >-
  Capture short operator notes into this skill's store. Use when someone says
  note/notes (or "add a note" / "make a note") in council chat, or asks to list,
  search, or show saved notes.
---

# notes

When a human says **note** or **notes** in council chat, Ava saves a generalized
note from the last ~10 human messages and acks with the id. Do not invent content.

## Add (CLI)

```bash
python3 ~/.ollama/skills/notes/scripts/notes.py add "the note text"
```

## Read

```bash
python3 ~/.ollama/skills/notes/scripts/notes.py list
python3 ~/.ollama/skills/notes/scripts/notes.py recent
python3 ~/.ollama/skills/notes/scripts/notes.py search "keyword"
python3 ~/.ollama/skills/notes/scripts/notes.py show <id>
```

Only report notes that exist in the store. Do not invent past notes.

## Store

`store/notes.json` — append-only list under this skill.
