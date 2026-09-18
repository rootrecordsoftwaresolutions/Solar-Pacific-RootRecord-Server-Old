"""Load secrets.env + defaults. Never print secret values.

Council tokens ONLY: TELEGRAM_AVA_TOKEN / TELEGRAM_BRUCE_TOKEN / TELEGRAM_CARLY_TOKEN
from ~/.config/ava-council/secrets.env. Never read AVA_TELEGRAM_BOT_TOKEN or
TELEGRAM_BOT_TOKEN (origin apps/core long-poll — different bot).
"""
from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

CONFIG_DIR = Path.home() / ".config" / "ava-council"
SECRETS_PATH = CONFIG_DIR / "secrets.env"
STATE_PATH = CONFIG_DIR / "state.json"
TRUST_PATH = CONFIG_DIR / "trust.json"
RUNS_DIR = CONFIG_DIR / "runs"
MOD_LOG_DIR = CONFIG_DIR / "mod-log"

# Bot ids to ignore (no echo loops)
BOT_IDS_IGNORE = frozenset({8999193882, 8801392115, 8804639312})

CLASSIFIER_MODEL = "llama3.2:3b-instruct-q4_K_M"
DEFAULT_CHAT_MODEL = "llama3.2:3b-instruct-q4_K_M"
# Specialists for timed /brainstorm only. Everyday chat stays on DEFAULT_CHAT_MODEL.
BRUCE_MODEL_DEFAULT = "llama3.1:8b-instruct-q4_K_M"
# Already on this disk (~4.2GB). Structured critique for AppSec/safety/strategy.
# No pentest / exploit models.
CARLY_MODEL_DEFAULT = "mistral:7b-instruct-v0.3-q4_K_M"
# Dolphin 3.0 Llama 3.2 3B Q4_K_M (~2.0GB). Half dolphin-mistral; fits 16GB + 840M.
HEAT_MODEL_DEFAULT = "nchapman/dolphin3.0-llama3:3b"
CODER_MODELS_BLOCKED = frozenset(
    {
        "qwen2.5-coder",
        "codegemma",
        "deepseek-coder-v2",
        "qwen2.5-coder:7b",
        "qwen2.5-coder:1.5b",
    }
)

OLLAMA_BASE = "http://127.0.0.1:11434"
CURSOR_AGENTS_URL = "https://api.cursor.com/v1/agents"
OWNER_USERNAME = "alexrs94"
OWNER_USERNAMES = frozenset({"alexrs94", "rootrecordadmin"})

NUM_PREDICT = {
    "speak": 420,
    "casual": 420,
    "council": 480,
    "brainstorm": 220,
    "summary": 400,
    "classifier": 80,
    "mention": 420,
}

# Desk live facts must survive. Old 3800 chopped weather/NWS off the end of the system prompt.
PROMPT_CHAR_CAP = 7200
HISTORY_CHAR_CAP = 1800
SEND_STAGGER_S = 1.4
THREAD_PILEON_S = 15

CHAT_TIMEOUT_S = 180

# Per-human group trigger cooldown (seconds). DMs skip it. Bots are free.
# Bursts group back-to-back lines. One council/casual pipeline at a time is state.busy.
DEFAULT_USER_COOLDOWN_S = 90
DEFAULT_TRUST_GAIN_FACTOR = 0.5
DEFAULT_TRUST_LOSS_FACTOR = 1.5


def _parse_env_file(path: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    if not path.is_file():
        return out
    text = path.read_text(encoding="utf-8", errors="replace")
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        key, _, val = line.partition("=")
        key = key.strip()
        val = val.strip()
        if (val.startswith('"') and val.endswith('"')) or (
            val.startswith("'") and val.endswith("'")
        ):
            val = val[1:-1]
        out[key] = val
    return out


def _float_secret(raw: str, default: float) -> float:
    try:
        return float(raw)
    except (TypeError, ValueError):
        return default


def upsert_secret(key: str, value: str) -> None:
    """Update one key in secrets.env without logging other values."""
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    lines: list[str] = []
    found = False
    if SECRETS_PATH.is_file():
        raw = SECRETS_PATH.read_text(encoding="utf-8", errors="replace")
        for line in raw.splitlines():
            stripped = line.strip()
            if stripped and not stripped.startswith("#") and "=" in stripped:
                k = stripped.split("=", 1)[0].strip()
                if k == key:
                    lines.append(f"{key}={value}")
                    found = True
                    continue
            lines.append(line)
    if not found:
        if lines and lines[-1].strip():
            lines.append("")
        lines.append(f"{key}={value}")
    SECRETS_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")
    try:
        os.chmod(SECRETS_PATH, 0o600)
    except OSError:
        pass


@dataclass
class Config:
    telegram_ava_token: str = ""
    telegram_bruce_token: str = ""
    telegram_carly_token: str = ""
    alexander_telegram_id: str = ""
    telegram_group_chat_id: str = ""
    cursor_api_key: str = ""
    cursor_default_repo: str = "https://github.com/Ava-Core-Dev/ava-core.git"
    keep_one_loaded: bool = True
    classifier_model: str = CLASSIFIER_MODEL
    chat_model: str = DEFAULT_CHAT_MODEL
    ava_model: str = ""
    bruce_model: str = ""
    carly_model: str = ""
    heat_model: str = HEAT_MODEL_DEFAULT
    ollama_base: str = OLLAMA_BASE
    config_dir: Path = CONFIG_DIR
    state_path: Path = STATE_PATH
    trust_path: Path = TRUST_PATH
    runs_dir: Path = RUNS_DIR
    user_cooldown_s: int = DEFAULT_USER_COOLDOWN_S
    trust_gain_factor: float = DEFAULT_TRUST_GAIN_FACTOR
    trust_loss_factor: float = DEFAULT_TRUST_LOSS_FACTOR

    def _safe_model(self, m: str) -> str:
        name = (m or "").strip() or self.chat_model
        base = name.split(":")[0].lower()
        for blocked in CODER_MODELS_BLOCKED:
            if blocked in name.lower() or blocked == base:
                return self.chat_model
        return name

    def specialist_session(self) -> bool:
        try:
            from apps.council import brainstorm

            return brainstorm.status() in {"running", "wrapping"}
        except Exception:
            return False

    def model_for(self, voice: str, *, dm: bool = False, full_thought: bool | None = None) -> str:
        """One 3B for every voice. 7B/8B specialists do not fit beside FastFlowLM RSS."""
        del voice, dm, full_thought
        return self._safe_model(self.chat_model)

    def token_for(self, voice: str) -> str:
        return {
            "ava": self.telegram_ava_token,
            "bruce": self.telegram_bruce_token,
            "carly": self.telegram_carly_token,
        }.get(voice, self.telegram_ava_token)


def load_config() -> Config:
    env = _parse_env_file(SECRETS_PATH)
    # Also allow process env to override (systemd EnvironmentFile populates os.environ)
    def g(key: str, default: str = "") -> str:
        return (os.environ.get(key) or env.get(key) or default).strip()

    keep = g("KEEP_ONE_LOADED", "true").lower() not in ("0", "false", "no", "off")
    cfg = Config(
        telegram_ava_token=g("TELEGRAM_AVA_TOKEN"),
        telegram_bruce_token=g("TELEGRAM_BRUCE_TOKEN"),
        telegram_carly_token=g("TELEGRAM_CARLY_TOKEN"),
        alexander_telegram_id=g("ALEXANDER_TELEGRAM_ID"),
        telegram_group_chat_id=g("TELEGRAM_GROUP_CHAT_ID"),
        cursor_api_key=g("CURSOR_API_KEY"),
        cursor_default_repo=g(
            "CURSOR_DEFAULT_REPO", "https://github.com/Ava-Core-Dev/ava-core.git"
        ),
        keep_one_loaded=keep,
        classifier_model=g("CLASSIFIER_MODEL", CLASSIFIER_MODEL) or CLASSIFIER_MODEL,
        chat_model=g("CHAT_MODEL", DEFAULT_CHAT_MODEL) or DEFAULT_CHAT_MODEL,
        ava_model=g("AVA_MODEL"),
        bruce_model=g("BRUCE_MODEL", BRUCE_MODEL_DEFAULT) or BRUCE_MODEL_DEFAULT,
        carly_model=g("CARLY_MODEL", CARLY_MODEL_DEFAULT) or CARLY_MODEL_DEFAULT,
        heat_model=g("HEAT_MODEL", HEAT_MODEL_DEFAULT) or HEAT_MODEL_DEFAULT,
        ollama_base=g("OLLAMA_BASE", OLLAMA_BASE) or OLLAMA_BASE,
        user_cooldown_s=int(g("USER_COOLDOWN_S", str(DEFAULT_USER_COOLDOWN_S)) or DEFAULT_USER_COOLDOWN_S),
        trust_gain_factor=_float_secret(g("TRUST_GAIN_FACTOR"), DEFAULT_TRUST_GAIN_FACTOR),
        trust_loss_factor=_float_secret(g("TRUST_LOSS_FACTOR"), DEFAULT_TRUST_LOSS_FACTOR),
    )
    cfg.config_dir.mkdir(parents=True, exist_ok=True)
    cfg.runs_dir.mkdir(parents=True, exist_ok=True)
    MOD_LOG_DIR.mkdir(parents=True, exist_ok=True)
    return cfg
