---
name: notes
description: >-
  Capture short operator notes into this skill's store. Use when the user says
  "Add a note" (or "add note" / "make a note") in a sentence, or asks to list,
  search, or show saved notes.
---

# notes

When the user says **Add a note** anywhere in a sentence, save the note. Do not
wait for a separate confirmation. Do not invent content they did not write.

## Add

1. Take the text after the trigger (`add a note`, `add note`, `make a note`).
2. Strip leading punctuation (`:`, `—`, `-`, `,`) and surrounding quotes.
3. If nothing remains, ask what to save — once.
4. Save with:

```bash
python3 ~/.ollama/skills/notes/scripts/notes.py add "the note text"
```

5. Reply with one short line: saved, plus the id returned.

## Read

```bash
python3 ~/.ollama/skills/notes/scripts/notes.py list
python3 ~/.ollama/skills/notes/scripts/notes.py recent
python3 ~/.ollama/skills/notes/scripts/notes.py search "keyword"
python3 ~/.ollama/skills/notes/scripts/notes.py show <id>
```

Only report notes that exist in the store. Do not invent past notes.

## Store

`store/notes.json` — append-only list under this skill. No other desk writes here.
