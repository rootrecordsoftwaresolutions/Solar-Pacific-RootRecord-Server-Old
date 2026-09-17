# Service: Open WebUI Dashboard

Browser-based chat interface (looks/feels like ChatGPT) for humans to talk to
the named persona bots. Installed natively into the existing venv — **no
Docker**, to keep memory overhead low on 16GB RAM.

## Install

```bash
source ~/pipeline_env/bin/activate
pip install open-webui
open-webui serve
```

Open `http://localhost:8080`. The first account created on that login screen
becomes the admin account.

## Point it at the custom gateway, not raw Ollama

By default Open WebUI looks for raw Ollama models. Reroute it to the custom
FastAPI gateway (`07-services/fastapi-token-server.md`) so persona chats get
token tracking, web search, and codebase-aware skills:

1. Admin Settings → **Connections** → OpenAI API section:
   - **API URL:** `http://localhost:8000/v1`
   - **API Key:** the operator's key (`admin-key-123` in the reference
     schema — see `07-services/fastapi-token-server.md`)
2. Save.

## Create persona bots for users

1. **Workspace** tab → **Models** → **Create a Model**.
2. Name the persona (e.g. "System Explainer Bot", "Sarcastic Code Mentor").
3. **Base Model:** the underlying local model (e.g.
   `gemma2:9b-instruct-q4_K_M`).
4. **System Prompt:** the persona's behavior guidelines — for the three named
   agents, use the drop-in prompts from `04-agent-personas/`.
5. Save.

Users can then open the dashboard from the machine's IP, pick a persona from
the dropdown, and chat immediately — routed through the full pipeline.
