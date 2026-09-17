# Service: VS Code Integration

Two ways to hook the local pipeline into VS Code, from lightest to fullest:

## Option A — Continue extension straight to Ollama

1. Install the **Continue** extension (Continue Dev, Inc.) from the VS Code
   Extensions tab.
2. Open Continue's `config.json` (gear icon in the sidebar) and point it at
   local models directly:

```json
{
  "models": [
    {
      "title": "My Custom Local Pipeline",
      "provider": "ollama",
      "model": "qwen2.5-coder:7b-instruct-q4_K_M"
    }
  ],
  "tabAutocompleteModel": {
    "title": "Fast Autocomplete",
    "provider": "ollama",
    "model": "deepseek-coder:1.5b-instruct-q8_0"
  },
  "embeddingsProvider": {
    "provider": "ollama",
    "model": "nomic-embed-text"
  }
}
```

- Sub-second tab autocomplete runs on the tiny `deepseek-coder:1.5b` model in
  the background.
- `@codebase` in the Continue chat panel triggers local indexing via
  `nomic-embed-text`.
- Heavier structural questions pull in `qwen2.5-coder:7b` or `llama3.1:8b`
  on-demand.

## Option B — Through the custom FastAPI gateway (full pipeline)

To route through the custom tool-routing/web-search/token-tracking pipeline
(`07-services/fastapi-token-server.md`) instead of talking to Ollama directly:

1. Start `server.py` (see `07-services/fastapi-token-server.md`) — it exposes
   an OpenAI-compatible endpoint at `http://localhost:8000/v1`.
2. Point Continue's config at it instead:

```json
{
  "models": [
    {
      "title": "My Custom Local Pipeline",
      "provider": "openai",
      "model": "gemma2:9b-instruct-q4_K_M",
      "apiBase": "http://localhost:8000/v1"
    }
  ]
}
```

Now chatting through "My Custom Local Pipeline" in VS Code routes through the
custom tool-routing, web-search, and token-tracking logic instead of the
default Ollama endpoint.
