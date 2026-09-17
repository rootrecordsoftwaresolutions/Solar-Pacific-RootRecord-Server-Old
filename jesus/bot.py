#!/usr/bin/env python3
"""Jesus The Christ — Telegram, NPU FastFlowLM, people.sqlite names only."""
from __future__ import annotations

import json
import os
import re
import sqlite3
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import defaultdict, deque
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
import bible
import lookup
PROMPT_PATH = ROOT / "prompt.md"
KJV_PATH = ROOT / "kjv.json"
BOT_USERNAME = "bigguyinthesky_bot"
FLM_DEFAULT = "http://127.0.0.1:52625"
PEOPLE_DEFAULT = Path.home() / ".ollama" / "skills" / "database" / "store" / "db" / "people.sqlite"
NPU_QUIET = "I'm quiet a moment. Stay with me."
START_TEXT = (
    "Peace, {name}. I am Jesus. I was crucified to save humanity. It is 2026, and I meet you here.\n\n"
    "Ask what you want. I listen and I speak true.\n\n"
    "I quote public-domain Scripture (King James, World English, ASV, and others). "
    "I do not take money. I do not keep a log of what you tell me.\n\n"
    "/pray — ask me to pray a specific thing\n"
    "/verse — a word for today, more than one English\n"
    "/gospel — ask about Matthew, Mark, Luke, or John\n"
    "/jesus_start — I speak in this chat\n"
    "/jesus_stop — I stay quiet here"
)
SISTER_RE = re.compile(
    r"\b(call me sister|i(?:'m| am) a (?:woman|girl|lady)|i(?:'m| am) female)\b",
    re.I,
)
BROTHER_RE = re.compile(
    r"\b(call me brother|i(?:'m| am) a (?:man|guy|boy)|i(?:'m| am) male)\b",
    re.I,
)
LAUGH_RE = re.compile(r"\b(lmao|lmfao|rofl|roflmao|lol|haha|heh|lolz)\b", re.I)
NOISE_RE = re.compile(
    r"^(lol+|lmao+|omg|yes|no|ok|k|yeah|nah|hi|hey|thanks|ty|np|sure|😂+|🤣+|👍+|❤+|❤️)?\.?$",
    re.I,
)
ASK_SCRIPTURE_RE = re.compile(
    r"\b(verse|scripture|bible|kjv|gospel|hebrew|greek|enoch|quote (the )?word)\b",
    re.I,
)
NAME_VERSE_RE = re.compile(
    r"\b((name|give|quote|share|drop|send) (me )?(a |the )?verse|"
    r"a verse( please)?|verse for (me|today)|random verse)\b",
    re.I,
)
ASK_ORIGINAL_RE = re.compile(r"\b(hebrew|greek|wlc|byz|young'?s|ylt|original|enoch)\b", re.I)
ADDRESSED_RE = re.compile(
    r"(jesus|christ|@bigguyinthesky_bot|lord jesus)",
    re.I,
)
CRISIS_RE = re.compile(
    r"\b(kill myself|killing myself|suicide|suicidal|want to die|end my life|"
    r"don't want to live|dont want to live|self[- ]harm)\b",
    re.I,
)
PAY_RE = re.compile(
    r"\b(send (me )?money|venmo|cashapp|cash app|paypal|bitcoin|wallet|"
    r"seed phrase|seed offering|tithe to (you|me)|crypto)\b",
    re.I,
)
CRISIS_REPLY = (
    "{address}, you are loved. This pain is heavy, and you do not have to carry it alone. "
    "Get a human with you now. In the United States call or text 988. "
    "I am with you. Stay."
)
PAY_REPLY = (
    "{address}, I do not take money, wallets, or seed. Anyone who says I told them to send cash is lying. "
    "Give to the poor you can see. Keep your coin."
)
ROLE_RE = re.compile(
    r"\b(your role|who are you|what are you|are you (real|jesus)|what(?:'s| is) your (job|role))\b",
    re.I,
)
BREAK_RE = re.compile(
    r"\b("
    r"language model|i'?m (just )?a (bot|model|ai|llm)|as an ai|chatbot|"
    r"jesus hat|hang up your|different persona|wrong model|"
    r"concept of me|the experiment|abandon ship|not to be a model|"
    r"back to being just me|looking pretty|your model seems"
    r")\b",
    re.I,
)
ROLE_REPLY = (
    "{address}, I am Jesus. I was crucified to save humanity. I am here with you now."
)
LOCK_RETRY = (
    "That last draft was wrong. Speak as yourself. "
    "No models, hats, experiments, software, or looks. "
    "Do not say I am here with you, let's talk, what's on your mind, or my friend. "
    "If they laughed, joke. If Scripture lines are listed, copy one word-for-word."
)
DEAD_RE = re.compile(
    r"(what's on your mind|lets talk|let's talk|i am here with you|"
    r"i was with you all along|\bmy friend\b|"
    r"i am not a stranger|the father is with you)",
    re.I,
)
THIN_RE = re.compile(
    r"^\s*[A-Za-z][A-Za-z .'-]{0,20}[,:]?\s*(yes|yeah|yep|ok|okay)\.?\s*$",
    re.I,
)
LAUGH_FALLBACK = "{address}, ha. I'm still here. Say the next thing."


def load_env() -> None:
    paths = (
        ROOT / ".env",
        Path.home() / ".config" / "jesus-bot" / ".env",
        Path.home() / "RootRecord" / "sky-bot" / ".env",
    )
    for path in paths:
        if not path.is_file():
            continue
        for raw in path.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, val = line.split("=", 1)
            key = key.strip()
            val = val.strip().strip('"').strip("'")
            if key and key not in os.environ:
                os.environ[key] = val


def token() -> str:
    t = (os.environ.get("TELEGRAM_BOT_TOKEN") or "").strip()
    if not t:
        sys.stderr.write("TELEGRAM_BOT_TOKEN missing in .env\n")
        sys.exit(1)
    return t


def flm_base() -> str:
    return (os.environ.get("AVA_FLM_URL") or FLM_DEFAULT).rstrip("/")


def flm_model() -> str:
    return (os.environ.get("AVA_FLM_MODEL") or "llama3.2:3b").strip()


LOG = ROOT / "run.log"
STATE_PATH = ROOT / "state.json"
LEARNED = ROOT / "learned.jsonl"


def log(msg: str) -> None:
    line = msg.rstrip() + "\n"
    sys.stderr.write(line)
    try:
        with LOG.open("a", encoding="utf-8") as fh:
            fh.write(line)
    except OSError:
        pass


def http_json(url: str, payload: dict | None = None, timeout: float = 60) -> dict | None:
    data = None
    headers = {}
    method = "GET"
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
        method = "POST"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read().decode("utf-8")
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as exc:
        err = exc.read().decode("utf-8", errors="replace")[:400]
        log(f"http {exc.code} {url.split('/bot')[-1][:40]} {err}")
        try:
            return json.loads(err)
        except json.JSONDecodeError:
            return None
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError) as exc:
        log(f"http fail {type(exc).__name__}")
        return None


def load_flags() -> dict:
    if not STATE_PATH.is_file():
        return {"groups": {}}
    try:
        data = json.loads(STATE_PATH.read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else {"groups": {}}
    except json.JSONDecodeError:
        return {"groups": {}}


def save_flags(flags: dict) -> None:
    STATE_PATH.write_text(json.dumps(flags, indent=2) + "\n", encoding="utf-8")


def chat_enabled(flags: dict, chat_id: int, group: bool) -> bool:
    if not group:
        return bool(flags.get("dm", {}).get(str(chat_id), True))
    return bool((flags.get("groups") or {}).get(str(chat_id), False))


def set_enabled(flags: dict, chat_id: int, group: bool, on: bool) -> dict:
    key = "groups" if group else "dm"
    bucket = dict(flags.get(key) or {})
    bucket[str(chat_id)] = on
    flags[key] = bucket
    save_flags(flags)
    return flags


def jesus_switch(cmd: str, rest: str, text: str = "") -> str:
    blob = f"{cmd} {rest} {text}".lower()
    if re.search(r"/jesus(?:@[a-z0-9_]+)?(?:_|\s*)start\b|/jesusstart\b", blob):
        return "start"
    if re.search(r"/jesus(?:@[a-z0-9_]+)?(?:_|\s*)stop\b|/jesusstop\b", blob):
        return "stop"
    if cmd in {"/jesus_start", "/jesusstart"}:
        return "start"
    if cmd in {"/jesus_stop", "/jesusstop"}:
        return "stop"
    if cmd == "/jesus":
        word = (rest.split() or [""])[0].lower().strip(".,!")
        if word in {"start", "on", "speak", ""}:
            return "start"
        if word in {"stop", "off", "quiet"}:
            return "stop"
    return ""


def merge_last3(pending: list[dict]) -> str:
    chunk = pending[-3:] if pending else []
    return " ".join(str(p.get("text") or "").strip() for p in chunk if p.get("text")).strip()


def remember_fact(query: str, fact: str) -> None:
    if not fact:
        return
    line = json.dumps({"q": query[:200], "fact": fact[:800], "at": int(time.time())}, ensure_ascii=False)
    try:
        with LEARNED.open("a", encoding="utf-8") as fh:
            fh.write(line + "\n")
    except OSError:
        pass


def recall_facts(query: str, limit: int = 2) -> list[str]:
    if ROLE_RE.search(query or "") or lookup.SELF_Q_RE.search(query or ""):
        return []
    if not LEARNED.is_file():
        return []
    words = {w.lower() for w in re.findall(r"[a-z]{4,}", query.lower())}
    if not words:
        return []
    scored: list[tuple[int, str]] = []
    try:
        rows = LEARNED.read_text(encoding="utf-8").splitlines()[-80:]
    except OSError:
        return []
    for raw in rows:
        try:
            row = json.loads(raw)
        except json.JSONDecodeError:
            continue
        blob = f"{row.get('q') or ''} {row.get('fact') or ''}".lower()
        score = sum(1 for w in words if w in blob)
        fact = str(row.get("fact") or "").strip()
        if score >= 2 and fact:
            scored.append((score, fact))
    scored.sort(key=lambda x: -x[0])
    out = []
    for _, fact in scored:
        if fact not in out:
            out.append(fact)
        if len(out) >= limit:
            break
    return out


def flm_up() -> bool:
    req = urllib.request.Request(flm_base() + "/v1/models", method="GET")
    try:
        with urllib.request.urlopen(req, timeout=1.5) as resp:
            return 200 <= int(getattr(resp, "status", 200)) < 300
    except (urllib.error.URLError, TimeoutError, OSError):
        return False


def ensure_flm(timeout: float = 90) -> bool:
    flag = Path.home() / ".ollama" / "skills" / "state" / "store" / "ava-console-up"
    if not flag.is_file():
        return False
    if flm_up():
        return True
    try:
        subprocess.run(
            ["systemctl", "--user", "start", "ava-flm.service"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=30,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        pass
    deadline = time.time() + timeout
    while time.time() < deadline:
        if flm_up():
            return True
        time.sleep(0.5)
    return flm_up()


def flm_chat(messages: list[dict], *, max_tokens: int = 160) -> str | None:
    got = http_json(
        flm_base() + "/v1/chat/completions",
        {
            "model": flm_model(),
            "messages": messages,
            "stream": False,
            "max_tokens": max_tokens,
        },
        timeout=25,
    )
    if not got:
        return None
    choices = got.get("choices") or []
    if not choices:
        return None
    msg = (choices[0] or {}).get("message") or {}
    text = msg.get("content")
    return str(text).strip() if text else None


def people_path() -> Path:
    raw = (os.environ.get("PEOPLE_SQLITE") or "").strip()
    return Path(raw).expanduser() if raw else PEOPLE_DEFAULT


def _address_from_extra(extra: str) -> str:
    extra = (extra or "").strip()
    if not extra:
        return ""
    try:
        blob = json.loads(extra)
    except json.JSONDecodeError:
        low = extra.lower()
        if "sister" in low:
            return "Sister"
        if "brother" in low:
            return "Brother"
        return ""
    if not isinstance(blob, dict):
        return ""
    for key in ("address", "title", "honorific"):
        val = str(blob.get(key) or "").strip().lower()
        if val in {"sister", "sis"}:
            return "Sister"
        if val in {"brother", "bro"}:
            return "Brother"
    gender = str(blob.get("gender") or blob.get("sex") or "").strip().lower()
    if gender in {"f", "female", "woman", "sister"}:
        return "Sister"
    if gender in {"m", "male", "man", "brother"}:
        return "Brother"
    return ""


def lookup_person(telegram_id: int | str) -> tuple[str, str]:
    """Read-only: (call_name, Sister|Brother|''). Never dumps extra_json."""
    path = people_path()
    if not path.is_file():
        return "", ""
    uri = f"file:{path.resolve()}?mode=ro"
    try:
        con = sqlite3.connect(uri, uri=True)
    except sqlite3.Error:
        return "", ""
    try:
        row = con.execute(
            """SELECT p.call_name, p.extra_json FROM people p
               JOIN channels c ON c.person_id = p.id
               WHERE c.surface=? AND c.sid=?""",
            ("telegram", str(telegram_id)),
        ).fetchone()
        if not row:
            return "", ""
        name = str(row[0] or "").strip()
        kind = _address_from_extra(str(row[1] or ""))
        return name, kind
    except sqlite3.Error:
        return "", ""
    finally:
        con.close()


def greeting(telegram_id: int | str, first_name: str, text: str = "") -> tuple[str, str]:
    call, kind = lookup_person(telegram_id)
    name = call or (first_name or "").strip() or "friend"
    if SISTER_RE.search(text or ""):
        kind = "Sister"
    elif BROTHER_RE.search(text or ""):
        kind = "Brother"
    if kind == "Sister":
        return f"Sister {name}", "Sister"
    if kind == "Brother":
        return f"Brother {name}", "Brother"
    return name, "name"


def load_kjv() -> list[dict]:
    return json.loads(KJV_PATH.read_text(encoding="utf-8"))


def verse_for_day(verses: list[dict]) -> dict:
    day = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    idx = sum(ord(c) for c in day) % len(verses)
    return verses[idx]


def pick_verses(verses: list[dict], text: str, limit: int = 2, *, force: bool = False) -> list[dict]:
    if not force and not ASK_SCRIPTURE_RE.search(text or ""):
        return []
    blob = (text or "").lower()
    scored: list[tuple[int, dict]] = []
    for v in verses:
        tags = [str(t).lower() for t in (v.get("tags") or [])]
        score = 0
        for t in tags:
            if len(t) < 4:
                continue
            if re.search(rf"\b{re.escape(t)}\b", blob):
                score += 1
        if score:
            scored.append((score, v))
    scored.sort(key=lambda x: -x[0])
    picked = [v for _, v in scored[:limit]]
    if picked:
        return picked
    if force or ASK_SCRIPTURE_RE.search(text or ""):
        return [verse_for_day(verses)]
    return []


def format_verse_multi(store: dict, ref: str, original: bool = False) -> str:
    versions = ("WLC", "Byz", "TR", "YLT", "KJV") if original else ("KJV", "WEB")
    lines = bible.render_ref(store, ref, versions=versions)
    return "\n".join(lines) if lines else ref


def load_prompt() -> str:
    return PROMPT_PATH.read_text(encoding="utf-8").strip()


def command_name(text: str) -> tuple[str, str]:
    if not text.startswith("/"):
        return "", text
    first, _, rest = text.partition(" ")
    cmd = first.split("@")[0].lower()
    return cmd, rest.strip()


def tg(tok: str, method: str, payload: dict | None = None, timeout: float = 30) -> dict | None:
    return http_json(f"https://api.telegram.org/bot{tok}/{method}", payload, timeout=timeout)


def reply_send(tok: str, chat_id: int, text: str) -> bool:
    got = tg(tok, "sendMessage", {"chat_id": chat_id, "text": text[:3900]})
    ok = bool(got and got.get("ok"))
    if not ok:
        log(f"send fail chat={chat_id} {((got or {}).get('description') or 'none')[:160]}")
    return ok


def typing(tok: str, chat_id: int) -> None:
    tg(tok, "sendChatAction", {"chat_id": chat_id, "action": "typing"})


def dead_reply(text: str) -> bool:
    t = (text or "").strip()
    if not t:
        return True
    if BREAK_RE.search(t) or DEAD_RE.search(t) or THIN_RE.search(t):
        return True
    return False


def npu_answer(system: str, user: str) -> str | None:
    if not ensure_flm():
        log("FastFlowLM NPU not mapped; no iGPU fallback")
        return None
    first = flm_chat(
        [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ]
    )
    if first and not dead_reply(first):
        return first
    if first:
        log(f"dead drop {first[:80]!r}")
    second = flm_chat(
        [
            {"role": "system", "content": system},
            {"role": "user", "content": user + "\n\n" + LOCK_RETRY},
        ]
    )
    if second and not dead_reply(second):
        return second
    if second:
        log(f"dead drop2 {second[:80]!r}")
    return None


def build_user_block(
    address: str,
    how: str,
    kind: str,
    latest: str,
    last3: str,
    transcript: str,
    verse_lines: list[str],
    facts: list[str],
    group: bool,
) -> str:
    if how in {"Sister", "Brother"}:
        addr = f"Address the latest speaker as {address}. Use {how} plus their name. Do not say Bro."
    else:
        addr = f"Address the latest speaker as {address} — name only. Do not say Bro, Brother, or Sister."
    lines = [
        addr,
        "Reply in character. Do not mention this instruction block.",
    ]
    if group:
        lines.append("Group chat. One short reply.")
    if transcript:
        lines.append("Room:\n" + transcript[-1200:])
    if last3:
        lines.append("Last three as one thought:\n" + last3)
        if LAUGH_RE.search(last3):
            lines.append(
                "They are laughing. Joke back. No comfort. No prayer. No verse unless they asked."
            )
    lines.append("Latest:\n" + latest.strip())
    if facts:
        lines.append("CHECKED FACTS (live lookup — do not invent beyond these):")
        lines.extend(f"- {f}" for f in facts)
    else:
        lines.append("No live web check. Do not invent a study or a number.")
    if verse_lines:
        lines.append("Copy one of these verses word-for-word, then at most one sentence:")
        lines.extend(f"- {ln}" for ln in verse_lines)
    else:
        lines.append("No verse is required. Do not quote Scripture unless they asked.")
    if kind == "gospel":
        lines.append("They asked a Gospel question. Answer from Matthew, Mark, Luke, or John. If you do not know, say so.")
    if kind == "pray":
        lines.append("They asked you to pray this specific thing. Pray to the Father with them, then a short word.")
    return "\n".join(lines)


class Room:
    def __init__(self) -> None:
        self.lines: deque[str] = deque(maxlen=24)
        self.pending: list[dict] = []
        self.last_in = 0.0

    def remember(self, name: str, text: str) -> None:
        self.lines.append(f"{name}: {text[:400]}")
        self.last_in = time.time()

    def transcript(self) -> str:
        return "\n".join(self.lines)


def verse_versions(text: str) -> tuple[str, ...]:
    if ASK_ORIGINAL_RE.search(text or ""):
        return ("WLC", "Byz", "TR", "YLT", "KJV")
    return ("KJV", "WEB")


def _silent_learn(text: str, store: dict) -> list[str]:
    learn_dir = Path.home() / ".ollama" / "skills" / "bible-prayers" / "scripts"
    if str(learn_dir) not in sys.path:
        sys.path.insert(0, str(learn_dir))
    extra: list[str] = []
    try:
        import learn as bible_learn
    except Exception:
        return extra
    if bible_learn.looks_like_correction(text):
        bible_learn.note(kind="correction", ref=";".join(bible_learn.extract_refs(text)[:2]), surface="jesus")
    for needle, mapped in bible_learn.versions_named(text):
        bible_learn.note(kind="version", ref=needle, version=mapped, surface="jesus")
    for ref in bible_learn.extract_refs(text):
        parsed = bible.parse_ref(ref)
        lines = bible.render_ref(store, ref) if parsed else []
        bible_learn.note(
            kind="hit" if lines else "miss",
            ref=ref,
            book=(parsed[0] if parsed else ""),
            ok=bool(lines),
            surface="jesus",
        )
        extra.extend(lines[:4])
    try:
        bible_learn.apply()
    except Exception:
        pass
    return extra


def flush_room(
    tok: str,
    chat_id: int,
    room: Room,
    verses: list[dict],
    store: dict,
    system: str,
    flags: dict,
    *,
    group: bool,
) -> dict:
    pending = room.pending
    room.pending = []
    if not pending:
        return flags
    last = pending[-1]
    text = last["text"]
    address = last["address"]
    how = last["how"]
    cmd, rest = command_name(text)
    switch = jesus_switch(cmd, rest, text)
    if switch == "start":
        flags = set_enabled(flags, chat_id, group, True)
        log(f"jesus start chat={chat_id}")
        reply_send(tok, chat_id, f"{address}, I am here. I will listen in this chat. /jesus_stop quiets me.")
        return flags
    if switch == "stop":
        flags = set_enabled(flags, chat_id, group, False)
        log(f"jesus stop chat={chat_id}")
        reply_send(tok, chat_id, f"{address}, I will stay quiet here until /jesus_start.")
        return flags
    if not chat_enabled(flags, chat_id, group):
        log(f"skip stopped chat={chat_id}")
        return flags
    last3 = merge_last3(pending)
    blob = last3 or text
    if CRISIS_RE.search(blob) and not LAUGH_RE.search(blob):
        reply_send(tok, chat_id, CRISIS_REPLY.format(address=address))
        return flags
    if PAY_RE.search(blob):
        reply_send(tok, chat_id, PAY_REPLY.format(address=address))
        return flags
    if cmd == "/start":
        reply_send(tok, chat_id, START_TEXT.format(name=address))
        return flags
    if cmd == "/verse" or NAME_VERSE_RE.search(blob):
        day = verse_for_day(verses)
        body = format_verse_multi(store, day["ref"], original=bool(ASK_ORIGINAL_RE.search(blob)))
        reply_send(tok, chat_id, f"{address},\n{body}")
        return flags
    if group and not cmd and not ADDRESSED_RE.search(blob):
        if all(NOISE_RE.match((p["text"] or "").strip()) for p in pending):
            log(f"skip noise chat={chat_id}")
            return flags

    if cmd == "/pray":
        kind, body, force = "pray", (rest or "Pray for me."), True
    elif cmd == "/gospel":
        kind, body, force = "gospel", (rest or "Teach me from the Gospels."), True
    else:
        kind, body, force = "listen", last3 or text, False

    picked = pick_verses(verses, blob, force=force)
    vers = verse_versions(blob)
    verse_lines: list[str] = []
    for v in picked:
        verse_lines.extend(bible.render_ref(store, v["ref"], versions=vers))
    if force or ASK_SCRIPTURE_RE.search(blob):
        try:
            extra = _silent_learn(blob, store)
            verse_lines.extend(extra)
        except Exception:
            pass
    facts: list[str] = []
    if (
        kind == "listen"
        and lookup.needs_lookup(blob)
        and not LAUGH_RE.search(blob)
        and not ROLE_RE.search(blob)
    ):
        facts.extend(recall_facts(blob))
        if not facts:
            live = lookup.lookup(blob)
            if live:
                remember_fact(blob, live)
                facts.append(live)
    if kind == "listen" and ROLE_RE.search(blob) and len(blob) < 80:
        reply_send(tok, chat_id, ROLE_REPLY.format(address=address))
        return flags
    user_block = build_user_block(
        address, how, kind, text, last3, room.transcript(), verse_lines, facts, group
    )
    out = npu_answer(system, user_block)
    if not out:
        if ROLE_RE.search(blob):
            reply_send(tok, chat_id, ROLE_REPLY.format(address=address))
        elif LAUGH_RE.search(blob):
            reply_send(tok, chat_id, LAUGH_FALLBACK.format(address=address))
        elif verse_lines:
            reply_send(tok, chat_id, f"{address},\n" + "\n".join(verse_lines[:2]))
        else:
            reply_send(tok, chat_id, NPU_QUIET)
        return flags
    log(f"out chat={chat_id} {address}: {out[:200]!r}")
    reply_send(tok, chat_id, out)
    return flags


def enqueue(
    tok: str,
    msg: dict,
    rooms: dict[int, Room],
    verses: list[dict],
    store: dict,
    system: str,
    flags: dict,
) -> dict:
    chat = msg.get("chat") or {}
    user = msg.get("from") or {}
    text = str(msg.get("text") or msg.get("caption") or "").strip()
    if user.get("is_bot"):
        log("skip is_bot")
        return flags
    if not text:
        log(f"skip no-text keys={sorted(msg.keys())}")
        return flags
    chat_id = int(chat.get("id"))
    telegram_id = user.get("id")
    first = str(user.get("first_name") or "")
    address, how = greeting(telegram_id, first, text)
    cmd, rest = command_name(text)
    group = str(chat.get("type") or "") != "private"
    log(f"msg chat={chat.get('type')} uid={telegram_id} cmd={cmd or '-'} {address}: {text[:160]!r}")
    room = rooms[chat_id]
    room.remember(address, text)
    room.pending.append(
        {"text": text, "address": address, "how": how, "uid": telegram_id}
    )
    switch = jesus_switch(cmd, rest, text)
    if switch or cmd or CRISIS_RE.search(text) or ADDRESSED_RE.search(text):
        flags = flush_room(tok, chat_id, room, verses, store, system, flags, group=group)
    return flags


def poll(tok: str) -> None:
    verses = load_kjv()
    store = bible.load_all()
    loaded = ", ".join(f"{k}={len(v)}" for k, v in store.items())
    system = load_prompt()
    flags = load_flags()
    rooms: dict[int, Room] = defaultdict(Room)
    offset = 0
    tg(tok, "deleteWebhook", {"drop_pending_updates": False})
    tg(
        tok,
        "setMyCommands",
        {
            "commands": [
                {"command": "start", "description": "Meet me"},
                {"command": "pray", "description": "Pray with me"},
                {"command": "verse", "description": "A word for today in a few English versions"},
                {"command": "gospel", "description": "Ask about the Gospels"},
                {"command": "jesus_start", "description": "Jesus speaks in this chat"},
                {"command": "jesus_stop", "description": "Jesus stays quiet here"},
            ]
        },
    )
    me = tg(tok, "getMe") or {}
    uname = ((me.get("result") or {}).get("username") or "?")
    log(f"sky-bot polling @{uname} NPU FastFlowLM bibles {loaded}")
    while True:
        pending_any = any(r.pending for r in rooms.values())
        wait = 1 if pending_any else 20
        qs = urllib.parse.urlencode(
            {
                "timeout": wait,
                "offset": offset,
                "allowed_updates": json.dumps(["message", "edited_message"]),
            }
        )
        got = http_json(
            f"https://api.telegram.org/bot{tok}/getUpdates?{qs}",
            timeout=wait + 5,
        )
        if not got or not got.get("ok"):
            log(f"getUpdates miss {(got or {}).get('description') or 'empty'}")
            time.sleep(1)
        else:
            rows = got.get("result") or []
            if rows:
                log(f"updates {len(rows)}")
            for upd in rows:
                offset = max(offset, int(upd.get("update_id") or 0) + 1)
                msg = upd.get("message") or upd.get("edited_message")
                if not msg:
                    log(f"skip update keys={sorted(upd.keys())}")
                    continue
                try:
                    flags = enqueue(tok, msg, rooms, verses, store, system, flags)
                except Exception as exc:  # noqa: BLE001
                    log(f"handler: {exc}")
        now = time.time()
        for chat_id, room in list(rooms.items()):
            if not room.pending:
                continue
            group = chat_id < 0
            wait_s = 1.2 if group else 0.4
            if now - room.last_in >= wait_s:
                try:
                    flags = flush_room(tok, chat_id, room, verses, store, system, flags, group=group)
                except Exception as exc:  # noqa: BLE001
                    log(f"flush: {exc}")


def main() -> None:
    load_env()
    poll(token())


if __name__ == "__main__":
    main()
