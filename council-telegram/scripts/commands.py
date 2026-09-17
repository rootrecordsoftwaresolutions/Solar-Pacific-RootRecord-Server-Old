"""Telegram slash-command dropdowns. Full menu on Ava. Short DM menu on Bruce/Carly."""
from __future__ import annotations

from typing import Any

from . import telegram
from .config import Config

# Bot API: command 1–32 chars [a-z0-9_], description ≤256.
COMMANDS: list[tuple[str, str]] = [
    ("claim", "Bind yourself as owner (once)"),
    ("status", "Discussion, mode, ollama, your trust"),
    ("ping", "Council online check"),
    ("feelings", "Mood snapshot (numbers)"),
    ("holdoff", "Quiet + stop Ollama (owner)"),
    ("resume", "Discussion on + start Ollama (owner)"),
    ("discussion", "on or off (owner)"),
    ("mode", "auto | casual | council (owner)"),
    ("autoexecute", "on or off — still owner-gated (owner)"),
    ("trust", "<user> <0-100> (owner)"),
    ("praise", "@user (owner)"),
    ("warn", "@user (owner)"),
    ("pardon", "@user + unban (owner)"),
    ("approve", "Approve plan for implementer (id auto if one)"),
    ("reject", "Reject a pending plan id (owner)"),
    ("implement", "Send approved/pending plan to implementer (owner)"),
    ("fix", "Implementer self-repair job (window)"),
    ("read", "Read an allowlisted repo file"),
    ("recycle", "Recycle Ava Core on :8787"),
    ("selfrepair", "status | off (owner off)"),
    ("brainstorm", "Thought session — asks topic and how long"),
    ("audio", "Make a WAV — who speaks, then paste the script"),
    ("name", "@user Display Name (owner)"),
    ("cooldown", "<seconds> group trigger cooldown (owner; DMs skip)"),
    ("debug", "Last address decision (owner)"),
    ("skillrun", "<id> allowlisted exec skill (owner)"),
    ("proposals", "List pending plan ids"),
    ("goals", "Council goals; /goals add <title>"),
    ("recipes", "Recipes; /recipes save title | ingredients"),
    ("pantry", "Food stock; /pantry add name qty unit"),
    ("nutrition", "Nutrient-dense food lookup"),
    ("done", "Freeze the open daily proposal (owner)"),
    ("help", "Full command list"),
]

DM_COMMANDS: list[tuple[str, str]] = [
    ("start", "Who I am"),
    ("ping", "Online check"),
    ("status", "Discussion, ollama, your trust"),
    ("feelings", "Mood snapshot"),
    ("help", "What I can do here"),
    ("goals", "List or add a goal"),
    ("recipes", "List or save a recipe"),
    ("pantry", "What is on the shelf"),
    ("nutrition", "Dense food lookup"),
    ("audio", "Make a WAV — who speaks, then paste the script"),
]


def normalize_slash(text: str) -> str:
    """Strip Telegram group bot suffix: /status@SomeBot args → /status args."""
    raw = (text or "").strip()
    if not raw.startswith("/"):
        return raw
    first, _, rest = raw.partition(" ")
    cmd = first.split("@", 1)[0]
    if rest:
        return f"{cmd} {rest}".strip()
    return cmd


def bot_command_payload(approve_hint: str = "") -> list[dict[str, str]]:
    hint = (approve_hint or "").strip()
    out: list[dict[str, str]] = []
    for name, desc in COMMANDS:
        if name == "approve" and hint:
            desc = f"Approve {hint} → implementer"
        out.append({"command": name, "description": desc[:256]})
    return out


def help_text(approve_hint: str = "", *, voice: str = "ava") -> str:
    who = (voice or "ava").lower()
    if who in ("bruce", "carly"):
        label = "Bruce" if who == "bruce" else "Carly"
        lines = [
            f"This is a private chat with {label} only. Trust from the group still applies (40+ to talk).",
            "Group slash commands stay on Ava.",
        ]
        for name, desc in DM_COMMANDS:
            lines.append(f"/{name} — {desc}")
        return "\n".join(lines)
    lines = ["Council commands (group menu is Ava). DMs: message that bot — Ava, Bruce, or Carly."]
    for name, desc in COMMANDS:
        extra = f"  (pending {approve_hint})" if name == "approve" and approve_hint else ""
        lines.append(f"/{name} — {desc}{extra}")
    lines.append(
        "Bruce amends one daily proposal file. /done finishes it so the next round can open a new file. "
        "Reply to the file to add notes. /brainstorm asks what to think about and how long (min 10 minutes). Stop wraps early. "
        "/audio asks who should speak, then for the full script, then posts a WAV."
    )
    return "\n".join(lines)


def publish_commands(cfg: Config, approve_hint: str = "") -> None:
    payload = bot_command_payload(approve_hint)
    dm_payload = [{"command": n, "description": d[:256]} for n, d in DM_COMMANDS]
    scopes: list[dict[str, str] | None] = [None, {"type": "all_group_chats"}, {"type": "all_private_chats"}]
    ava = cfg.token_for("ava")
    if ava:
        for scope in scopes:
            telegram.set_my_commands(ava, payload, scope=scope)
    for voice in ("bruce", "carly"):
        token = cfg.token_for(voice)
        if not token:
            continue
        telegram.delete_my_commands(token, scope=None)
        telegram.delete_my_commands(token, scope={"type": "all_group_chats"})
        telegram.set_my_commands(token, dm_payload, scope={"type": "all_private_chats"})


def latest_approve_hint() -> str:
    from . import approval

    item = approval.latest_pending()
    return str(item.get("id") or "") if item else ""
