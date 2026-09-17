---
name: ollama-lifecycle
description: >-
  Local Ollama idle/touch/ensure clips. Stop Ollama and FastFlowLM after 15m unused unless brainstorm/queue busy.
---

# ollama-lifecycle

This folder **is** the runtime.

Everyday: FastFlowLM for council/origin speak (`AVA_NPU_CHAT=1`). Drop GGUF before mapping the NPU — both at once OOMs 16 GB. `ollama_warm_chat` is a no-op while NPU chat is on. Launch waits for `:52625` before origin. 15m idle may stop Ollama serve; it does **not** unmap FastFlowLM. Idle-stop unmaps the NPU when AVA Console closes.

Topic index: `boot-idle-origin`.
