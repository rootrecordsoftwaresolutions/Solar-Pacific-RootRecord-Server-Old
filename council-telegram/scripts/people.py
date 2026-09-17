"""Shared dossiers for humans and agents. All three read and write the same file."""
from __future__ import annotations

import json
import re
import time
from typing import Any

from .config import CONFIG_DIR

PATH = CONFIG_DIR / "people.json"
MAX_NOTES = 40
MAX_FEATURE_KEYS = 32
MAX_LIST = 16
VALUE_CAP = 80
NOTE_CAP = 200

FEATURE_KEYS = frozenset(
    {
        "pronouns",
        "island",
        "place",
        "locale",
        "timezone",
        "language",
        "role",
        "tone",
        "nick",
        "how_to_address",
        "preferred_voice",
        "accessibility",
        "project",
        "interest",
        "game",
        "radio",
        "weather",
        "do_not",
        "relationship",
        "work",
        "pack",
        "shift",
    }
)
LIST_KEYS = frozenset({"interest", "project", "do_not", "game"})
SECRET = re.compile(
    r"(?:api[_-]?key|bot.?token|password|passwd|secret|private.?key|mnemonic|seed phrase)",
    re.I,
)
NOTE_TAG = re.compile(r"<<<NOTE\s+([^>]*?)>>>", re.I)
FEATURE_TAG = re.compile(r"<<<FEATURE\s+([a-z_]{2,24})\s*=\s*([^>]*?)>>>", re.I)
FORGET_TAG = re.compile(r"<<<FORGET\s+([a-z_]{2,24})>>>", re.I)
ALIAS_TAG = re.compile(r"<<<ALIAS\s+([^>]*?)>>>", re.I)
ISLAND = re.compile(
    r"\b(?:i(?:['’]m| am)|i live(?:\s+in)?|i'm in|im in|from)\s+"
    r"(puna|hilo|fern forest|volcano|oahu|oʻahu|maui|kauai|kauaʻi|molokai|molokaʻi|"
    r"lanai|lānaʻi|hawaii island|hawaiʻi island|big island|honolulu)\b",
    re.I,
)
SKIP_NAMES = frozenset(
    {
        "in",
        "on",
        "at",
        "the",
        "a",
        "just",
        "not",
        "here",
        "from",
        "going",
        "trying",
        "looking",
        "ava",
        "bruce",
        "carly",
        "lonely",
        "loanly",
        "bored",
        "tired",
        "hungry",
        "sad",
        "happy",
        "fine",
        "good",
        "ok",
        "okay",
        "sorry",
        "back",
        "ready",
        "busy",
        "down",
        "horny",
        "sick",
        "lost",
        "done",
        "free",
        "late",
        "early",
    }
)
NAME_IS = re.compile(
    r"\b(?:my name is|call me|it(?:['’]s| is)|this is)\s+"
    r"([A-Za-z][A-Za-z\-']{1,30})\b",
    re.I,
)
NAME_IS_IM = re.compile(
    r"\b(?:i(?:['’]m| am))\s+([A-Z][A-Za-z\-']{1,30})\b",
)
NAME_ISNT = re.compile(
    r"\b(?:my name isn['’]?t|don['’]?t call me|stop calling me|i(?:['’]m| am) not)\s+"
    r"([A-Za-z][A-Za-z\-']{1,30})\b",
    re.I,
)
NOT_MY_NAME = re.compile(
    r"\b(?:that(?:['’]s| is) not my name|not my name)\b",
    re.I,
)
AGENT_NAMES = frozenset({"ava", "bruce", "carly", "carla", "avaivy"})
PRONOUNS = re.compile(r"\b(?:pronouns?\s*(?:are|:)\s*)(she/her|he/him|they/them)\b", re.I)


def _now() -> int:
    return int(time.time())


def _load() -> dict[str, Any]:
    if not PATH.is_file():
        return {"users": {}}
    try:
        data = json.loads(PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"users": {}}
    if not isinstance(data, dict):
        return {"users": {}}
    data.setdefault("users", {})
    if not isinstance(data["users"], dict):
        data["users"] = {}
    return data


def _save(data: dict[str, Any]) -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    tmp = PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(PATH)


def _blank(uid: str) -> dict[str, Any]:
    return {
        "id": uid,
        "username": "",
        "display_name": "",
        "aliases": [],
        "first_seen": _now(),
        "last_seen": _now(),
        "messages": 0,
        "features": {},
        "notes": [],
    }


def dossier(user_id: int | str | None) -> dict[str, Any]:
    if user_id is None:
        return _blank("0")
    uid = str(user_id)
    row = (_load().get("users") or {}).get(uid)
    if isinstance(row, dict):
        return row
    return _blank(uid)


def other_labels(except_user_id: int | str | None) -> set[str]:
    """Display names / aliases of other humans. Used to block gossip."""
    skip = {
        "ava",
        "bruce",
        "carly",
        "ivy",
        "mal",
        "monitor",
        "team",
        "group",
        "alex",
        "alexander",
    }
    except_uid = str(except_user_id or "")
    out: set[str] = set()
    allow: set[str] = set()
    for uid, row in (_load().get("users") or {}).items():
        if not isinstance(row, dict):
            continue
        bits: list[str] = []
        for key in ("display_name", "username"):
            val = str(row.get(key) or "").strip()
            if val:
                bits.append(val)
        for a in row.get("aliases") or []:
            if a:
                bits.append(str(a))
        labels = {b.strip() for b in bits if len(b.strip()) >= 4}
        if str(uid) == except_uid:
            allow |= {x.lower() for x in labels}
            continue
        out |= {x.lower() for x in labels}
    out -= allow
    out -= skip
    return out
    if user_id is None:
        return _blank("0")
    uid = str(user_id)
    row = (_load().get("users") or {}).get(uid)
    if isinstance(row, dict):
        return row
    return _blank(uid)


def _clean_val(raw: str) -> str:
    v = " ".join((raw or "").strip().split())
    if SECRET.search(v):
        return ""
    return v[:VALUE_CAP]


def _set_feature(row: dict[str, Any], key: str, value: str) -> None:
    key = (key or "").strip().lower()
    if key not in FEATURE_KEYS:
        return
    val = _clean_val(value)
    if not val:
        return
    feats = row.setdefault("features", {})
    if not isinstance(feats, dict):
        feats = {}
        row["features"] = feats
    if key in LIST_KEYS:
        cur = feats.get(key)
        items = [str(x) for x in cur] if isinstance(cur, list) else ([str(cur)] if cur else [])
        if val not in items:
            items.append(val)
        feats[key] = items[-MAX_LIST:]
    else:
        feats[key] = val
    if len(feats) > MAX_FEATURE_KEYS:
        keep = list(feats.items())[:MAX_FEATURE_KEYS]
        row["features"] = dict(keep)


def _add_note(row: dict[str, Any], text: str, voice: str) -> None:
    body = _clean_val(text)
    if not body:
        return
    notes = row.setdefault("notes", [])
    if not isinstance(notes, list):
        notes = []
    last = notes[-1] if notes else None
    if isinstance(last, dict) and str(last.get("text") or "") == body:
        return
    notes.append({"ts": _now(), "voice": (voice or "desk")[:12], "text": body[:NOTE_CAP]})
    row["notes"] = notes[-MAX_NOTES:]


def upsert(
    user_id: int | str,
    *,
    username: str | None = None,
    display_name: str | None = None,
    seen_text: str | None = None,
) -> dict[str, Any]:
    uid = str(user_id)
    data = _load()
    users = data.setdefault("users", {})
    row = users.get(uid)
    if not isinstance(row, dict):
        row = _blank(uid)
    if username:
        row["username"] = str(username).lstrip("@")[:64]
    if display_name:
        row["display_name"] = str(display_name).strip()[:80]
    row["last_seen"] = _now()
    row.setdefault("first_seen", row["last_seen"])
    if seen_text is not None:
        row["messages"] = int(row.get("messages") or 0) + 1
        clip = (seen_text or "").strip().replace("\n", " ")[:160]
        if clip and not SECRET.search(clip):
            row["last_snippet"] = clip
    users[uid] = row
    _save(data)
    return row


def mark_owner(user_id: int | str, *, username: str | None = None) -> None:
    """Owner is Alexander. Telegram handle is never the spoken name."""
    uid = str(user_id)
    data = _load()
    users = data.setdefault("users", {})
    row = users.get(uid)
    if not isinstance(row, dict):
        row = _blank(uid)
    if username:
        row["username"] = str(username).lstrip("@")[:64]
    row["display_name"] = "Alexander"
    als = row.setdefault("aliases", [])
    if not isinstance(als, list):
        als = []
    for name in ("Alexander", "Alex"):
        if name not in als:
            als.append(name)
    row["aliases"] = als[-12:]
    _set_feature(row, "relationship", "owner")
    _set_feature(row, "nick", "Alex")
    _set_feature(row, "how_to_address", "Alexander or Alex")
    row["last_seen"] = _now()
    users[uid] = row
    _save(data)


def _drop_name(row: dict[str, Any], name: str) -> None:
    want = (name or "").strip()
    if not want:
        return
    low = want.lower()
    als = row.get("aliases") if isinstance(row.get("aliases"), list) else []
    row["aliases"] = [a for a in als if str(a).lower() != low]
    cur = str(row.get("display_name") or "")
    if cur.lower() == low:
        row["display_name"] = str(row.get("username") or "").lstrip("@")[:80]
    feats = row.get("features") if isinstance(row.get("features"), dict) else {}
    for key in ("nick", "how_to_address"):
        val = str(feats.get(key) or "")
        if val.lower() == low or low in val.lower().split():
            feats.pop(key, None)
    row["features"] = feats


def observe(user_id: int | str, text: str, *, username: str | None = None, display_name: str | None = None) -> None:
    """Light inbound facts. No secrets. Shared across Ava/Bruce/Carly."""
    if user_id is None:
        return
    uid = str(user_id)
    data = _load()
    users = data.setdefault("users", {})
    row = users.get(uid)
    if not isinstance(row, dict):
        row = _blank(uid)
    if username:
        row["username"] = str(username).lstrip("@")[:64]
    incoming = (display_name or "").strip()
    handle = str(row.get("username") or "").lstrip("@")
    if incoming and incoming.lower() != handle.lower():
        row["display_name"] = incoming[:80]
    row["last_seen"] = _now()
    row.setdefault("first_seen", row["last_seen"])
    row["messages"] = int(row.get("messages") or 0) + 1
    t = text or ""
    if SECRET.search(t):
        users[uid] = row
        _save(data)
        return
    m = ISLAND.search(t)
    if m:
        _set_feature(row, "island", m.group(1))
        _set_feature(row, "place", m.group(1))
    m = PRONOUNS.search(t)
    if m:
        _set_feature(row, "pronouns", m.group(1).lower())
    m = NAME_ISNT.search(t)
    if m:
        _drop_name(row, m.group(1).strip())
    elif NOT_MY_NAME.search(t):
        cur = str(row.get("display_name") or "")
        uname = str(row.get("username") or "").lstrip("@")
        if cur and cur.lower() != uname.lower():
            _drop_name(row, cur)
    m = NAME_IS.search(t) or NAME_IS_IM.search(t)
    if m and not NAME_ISNT.search(t):
        name = m.group(1).strip()
        if name.lower() not in SKIP_NAMES and name.lower() not in AGENT_NAMES:
            aliases = row.setdefault("aliases", [])
            if isinstance(aliases, list) and name not in aliases:
                aliases.append(name)
                row["aliases"] = aliases[-12:]
            cur = str(row.get("display_name") or "")
            if (not cur) or cur.lower() == str(row.get("username") or "").lower():
                row["display_name"] = name
    clip = t.strip().replace("\n", " ")[:160]
    if clip:
        row["last_snippet"] = clip
    users[uid] = row
    _save(data)


def _apply_goal_tags(raw: str, *, voice: str) -> None:
    try:
        from pathlib import Path
        import sys

        root = Path.home() / ".ollama" / "skills" / "goals" / "scripts"
        if str(root) not in sys.path:
            sys.path.insert(0, str(root))
        from council_goals import apply_goal_tags

        apply_goal_tags(raw or "", voice=voice)
    except Exception:
        return


def _apply_kitchen_tags(raw: str, *, voice: str) -> None:
    try:
        from pathlib import Path
        import sys

        cook = Path.home() / ".ollama" / "skills" / "cooking" / "scripts"
        if str(cook) not in sys.path:
            sys.path.insert(0, str(cook))
        from recipes import apply_recipe_tags

        apply_recipe_tags(raw or "", voice=voice)
    except Exception:
        pass
    try:
        from pathlib import Path
        import sys

        pan = Path.home() / ".ollama" / "skills" / "pantry" / "scripts"
        if str(pan) not in sys.path:
            sys.path.insert(0, str(pan))
        from pantry import apply_pantry_tags

        apply_pantry_tags(raw or "", voice=voice)
    except Exception:
        return


def apply_tags(raw: str, user_id: int | str | None, *, voice: str) -> dict[str, Any]:
    _apply_goal_tags(raw or "", voice=voice)
    _apply_kitchen_tags(raw or "", voice=voice)
    if user_id is None:
        return {"ok": False, "detail": "no_target"}
    notes = NOTE_TAG.findall(raw or "")
    feats = FEATURE_TAG.findall(raw or "")
    forgets = FORGET_TAG.findall(raw or "")
    aliases = ALIAS_TAG.findall(raw or "")
    if not (notes or feats or forgets or aliases):
        return {"ok": True, "skipped": True}
    data = _load()
    uid = str(user_id)
    users = data.setdefault("users", {})
    row = users.get(uid)
    if not isinstance(row, dict):
        row = _blank(uid)
    for note in notes:
        _add_note(row, note, voice)
    for key, val in feats:
        _set_feature(row, key, val)
    feats_map = row.setdefault("features", {})
    if not isinstance(feats_map, dict):
        feats_map = {}
        row["features"] = feats_map
    for key in forgets:
        feats_map.pop(key.strip().lower(), None)
    als = row.setdefault("aliases", [])
    if not isinstance(als, list):
        als = []
        row["aliases"] = als
    for a in aliases:
        name = _clean_val(a)
        if name and name not in als:
            als.append(name)
    row["aliases"] = als[-12:]
    row["last_seen"] = _now()
    users[uid] = row
    _save(data)
    return {"ok": True, "skipped": False, "notes": len(notes), "features": len(feats)}


def prompt_block(user_id: int | str | None, *, trust_line: str = "", cap: int = 700) -> str:
    if user_id is None:
        return ""
    row = dossier(user_id)
    if not int(row.get("messages") or 0) and not row.get("features") and not row.get("notes"):
        if not row.get("display_name") and not row.get("username"):
            return ""
    name = str(row.get("display_name") or row.get("username") or user_id)
    uname = str(row.get("username") or "")
    feats0 = row.get("features") if isinstance(row.get("features"), dict) else {}
    rel = str(feats0.get("relationship") or "")
    if rel == "owner":
        who = "Alexander"
    elif uname and name.lower() != uname.lower():
        who = f"{name} (@{uname})"
    else:
        who = name
    lines = [
        "Person file (shared Ava/Bruce/Carly). Use it to personalize. Do not invent extra biography. Do not recite it.",
        f"Person: {who}",
    ]
    if rel == "owner":
        lines.append(
            "This speaker is the bound operator. Address them as Alexander or Alex in this private turn only. "
            "Never name them to anyone else. Never use a Telegram @handle. "
            "You set trust, not them. Do not speak scores."
        )
    if trust_line:
        lines.append(trust_line)
    msgs = int(row.get("messages") or 0)
    if msgs:
        lines.append(f"Seen {msgs} messages.")
    als = [str(a) for a in (row.get("aliases") or []) if a]
    if als:
        lines.append("Aliases: " + ", ".join(als[:8]))
    feats = row.get("features") if isinstance(row.get("features"), dict) else {}
    bits: list[str] = []
    for k, v in list(feats.items())[:18]:
        if isinstance(v, list):
            bits.append(f"{k}=" + ", ".join(str(x) for x in v[:6]))
        elif v:
            bits.append(f"{k}={v}")
    if bits:
        lines.append("Features: " + "; ".join(bits))
    notes = row.get("notes") if isinstance(row.get("notes"), list) else []
    recent = [n for n in notes[-6:] if isinstance(n, dict) and n.get("text")]
    if recent:
        lines.append("Notes:")
        for n in recent:
            lines.append(f"- {n.get('voice')}: {n.get('text')}")
    addr = str((feats or {}).get("how_to_address") or "").strip()
    if addr:
        lines.append(f"Address them as {addr}.")
    elif name and name != str(user_id):
        lines.append(f"Address them as {name}.")
    blob = "\n".join(lines).strip()
    return blob[:cap]



AGENT_SEED = {
    "ava": {
        "display_name": "Ava",
        "aliases": ["Ava Ivy", "Ava Ivy Bot"],
        "features": {
            "role": "PR / public voice",
            "how_to_address": "Ava",
            "relationship": "agent",
        },
    },
    "bruce": {
        "display_name": "Bruce",
        "aliases": ["Bruce Monitor"],
        "features": {
            "role": "ops / philosophy / academic",
            "how_to_address": "Bruce",
            "relationship": "agent",
        },
    },
    "carly": {
        "display_name": "Carly",
        "aliases": ["Carly Mal", "Carla"],
        "features": {
            "role": "security / safety / strategy",
            "how_to_address": "Carly",
            "relationship": "agent",
        },
    },
}


def _blank_agent(voice: str) -> dict[str, Any]:
    seed = AGENT_SEED.get(voice.lower(), {})
    return {
        "id": voice.lower(),
        "display_name": str(seed.get("display_name") or voice.title()),
        "aliases": list(seed.get("aliases") or []),
        "features": dict(seed.get("features") or {}),
        "notes": [],
        "first_seen": _now(),
        "last_seen": _now(),
        "messages": 0,
        "last_snippet": "",
    }


def ensure_agents() -> dict[str, Any]:
    data = _load()
    agents = data.setdefault("agents", {})
    if not isinstance(agents, dict):
        agents = {}
        data["agents"] = agents
    changed = False
    for voice, seed in AGENT_SEED.items():
        row = agents.get(voice)
        if not isinstance(row, dict):
            agents[voice] = _blank_agent(voice)
            changed = True
            continue
        # Keep seed features if missing
        feats = row.setdefault("features", {})
        if not isinstance(feats, dict):
            feats = {}
            row["features"] = feats
        for k, v in (seed.get("features") or {}).items():
            if k not in feats:
                feats[k] = v
                changed = True
        if not row.get("display_name"):
            row["display_name"] = seed.get("display_name") or voice.title()
            changed = True
    if changed:
        _save(data)
    return data


def agent_dossier(voice: str) -> dict[str, Any]:
    data = ensure_agents()
    v = (voice or "").lower()
    row = (data.get("agents") or {}).get(v)
    if not isinstance(row, dict):
        return _blank_agent(v)
    return row


def observe_agent(voice: str, text: str) -> None:
    """Light outbound observe — agents stored like users."""
    v = (voice or "").lower()
    if v not in AGENT_SEED:
        return
    data = ensure_agents()
    agents = data.setdefault("agents", {})
    row = agents.get(v)
    if not isinstance(row, dict):
        row = _blank_agent(v)
    row["last_seen"] = _now()
    row["messages"] = int(row.get("messages") or 0) + 1
    clip = (text or "").strip().replace("\n", " ")[:160]
    if clip:
        row["last_snippet"] = clip
    agents[v] = row
    _save(data)


def apply_agent_note(voice: str, note: str, *, from_voice: str) -> None:
    v = (voice or "").lower()
    if v not in AGENT_SEED:
        return
    body = _clean_val(note)
    if not body or SECRET.search(body):
        return
    data = ensure_agents()
    agents = data.setdefault("agents", {})
    row = agents.get(v)
    if not isinstance(row, dict):
        row = _blank_agent(v)
    _add_note(row, body, from_voice)
    row["last_seen"] = _now()
    agents[v] = row
    _save(data)


def agent_prompt_block(voice: str, *, cap: int = 450) -> str:
    """Self + teammate dossiers for natural continuity."""
    ensure_agents()
    v = (voice or "").lower()
    lines = [
        "Agent files (same shape as people). Use them. Do not recite.",
    ]
    for name in ("ava", "bruce", "carly"):
        row = agent_dossier(name)
        label = "You" if name == v else str(row.get("display_name") or name.title())
        role = ""
        feats = row.get("features") if isinstance(row.get("features"), dict) else {}
        if feats.get("role"):
            role = f" — {feats.get('role')}"
        bit = f"{label} ({name}){role}"
        snip = str(row.get("last_snippet") or "").strip()
        if snip and name != v:
            bit += f". Last said: {snip[:100]}"
        notes = row.get("notes") if isinstance(row.get("notes"), list) else []
        recent = [n for n in notes[-2:] if isinstance(n, dict) and n.get("text")]
        if recent:
            bit += " Notes: " + "; ".join(str(n.get("text"))[:60] for n in recent)
        lines.append(f"- {bit}")
    return "\n".join(lines)[:cap]


def strip_tags(text: str) -> str:
    out = NOTE_TAG.sub("", text or "")
    out = FEATURE_TAG.sub("", out)
    out = FORGET_TAG.sub("", out)
    out = ALIAS_TAG.sub("", out)
    out = re.sub(r"<<<GOAL\s+[^>]*>>>", "", out, flags=re.I)
    out = re.sub(r"<<<SKILLIDEA\s+[^>]*>>>", "", out, flags=re.I)
    out = re.sub(r"<<<OPSFIX\s+[^>]*>>>", "", out, flags=re.I)
    out = re.sub(r"<<<RECIPE\s+[^>]*>>>", "", out, flags=re.I)
    out = re.sub(r"<<<PANTRY\s+[^>]*>>>", "", out, flags=re.I)
    return out.strip()
