---
name: ensure-ava-runtime
description: >-
  Health-check origin :8787 + Ollama only while AVA Console is up. Never start them when the terminal is closed.
---

# ensure-ava-runtime

This folder **is** the runtime.

If `ava-console-up` is missing, this script exits without starting anything. Launch owns origin and Ollama. Ava-Core `scripts/ensure-ava-runtime.sh` execs this. Sources ollama-env skill.

Topic index: `boot-idle-origin`.
