---
name: ollama-env
description: >-
  Ollama GGUF residency env (MAX_LOADED_MODELS=1, KEEP_ALIVE=15m). Sourced by launch/ensure.
---

# ollama-env

This folder **is** the runtime.

Ava-Core `scripts/ollama-env.sh` sources this file.
`OLLAMA_VULKAN=1` and `OLLAMA_IGPU_ENABLE=1` keep GGUF on the 840M. They do not
route inference to the XDNA NPU.

Topic index: `boot-idle-origin`.
