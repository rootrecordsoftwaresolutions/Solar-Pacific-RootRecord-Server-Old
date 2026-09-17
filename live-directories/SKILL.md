---
name: live-directories
description: >-
  Topic index for the machine path map. The runner is the fs-index function
  desk. Use when locating files on this OmniBook; grep fs-index/paths.txt.
---

# Live directories (topic)

**Runtime is `fs-index`.** Open `~/.ollama/skills/fs-index/` for how it fires, the script, and `paths.txt`.

This folder is the grouping. Do not keep a second indexer here.

Manual:

```bash
python3 ~/.ollama/skills/fs-index/scripts/incremental_fs_index.py
```

Grep `~/.ollama/skills/fs-index/paths.txt`. Do not cat secrets. Do not dump database files.
