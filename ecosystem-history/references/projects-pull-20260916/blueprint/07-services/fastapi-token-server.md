# Service: FastAPI Token-Tracked Gateway (`server.py`)

The single production entry point. Wraps the local Ollama models behind a
standard OpenAI-compatible API so VS Code, Open WebUI, and any future client
can all talk to it the same way, while adding per-user auth, token tracking,
and streaming.

## Requirements

1. **OpenAI-compatible chat completions endpoint**, streaming via
   Server-Sent Events, so clients don't block waiting for the full response.
2. **Per-user API keys** stored in SQLite (`/mnt/Projects/api_users.db`),
   with a `tier` field (`unlimited` for the operator, `limited` for external
   users) and a running `tokens_consumed` counter.
3. **Auth via `Authorization: Bearer <key>` header** — reject with 401/403 if
   missing or invalid.
4. **Enforcement:** `limited`-tier users get blocked once they exceed a usage
   cap (e.g. 50,000 tokens) — this protects the local hardware from being
   overwhelmed by external traffic.
5. **Chat log audit table** — every inbound message and outbound reply logged
   alongside its token cost, for quality review later.
6. **Admin CLI**, running concurrently with the API server (a background
   thread listening on stdin), for:
   - generating new client API keys (`create_user_via_cli`)
   - listing existing users and their tier/usage

## Schema

```sql
CREATE TABLE IF NOT EXISTS users (
    api_key TEXT PRIMARY KEY,
    username TEXT NOT NULL,
    tier TEXT DEFAULT 'limited',      -- 'unlimited' for the operator
    tokens_consumed INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS chat_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp REAL,
    api_key TEXT,
    username TEXT,
    user_message TEXT,
    bot_response TEXT,
    tokens_used INTEGER,
    FOREIGN KEY(api_key) REFERENCES users(api_key)
);
```

Database path per `06-permissions-and-safety/filesystem-sandboxing.md`:
`/mnt/Projects/api_users.db`.

## Web-search skill

No API key required — scrapes DuckDuckGo's HTML results directly:

```python
def skill_web_search(query: str) -> str:
    """Live web search extraction (no API key needed)."""
    try:
        encoded_query = urllib.parse.quote(query)
        url = f"https://duckduckgo.com{encoded_query}"
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=5) as response:
            html = response.read().decode('utf-8')
        snippets = []
        start = 0
        while True:
            start = html.find('class="result__snippet"', start)
            if start == -1 or len(snippets) >= 2:
                break
            start = html.find('>', start) + 1
            end = html.find('</a>', start)
            snippets.append(html[start:end].replace('<b>', '').replace('</b>', '').strip())
            start = end
        return "\n".join([f"- {s}" for s in snippets]) if snippets else "No context found."
    except Exception:
        return "Search pipeline unavailable."
```

## Startup

```bash
source ~/pipeline_env/bin/activate
pip install fastapi uvicorn openai
python server.py   # boots http://127.0.0.1:8000
```

## Reference implementation

Full server (schema init, key generation, streaming handler, token
accounting) consolidated in `reference-code/server.py`.

## Roadmap note

The original session frames this as eventually becoming a public multi-tenant
API ("users are mainly going to be using it as a chat bot for understanding
our system and services"). The token-tracking design above already
anticipates that (per-user tiers, usage caps). See `08-future-roadmap.md` for
what's still unspecified about that expansion.
