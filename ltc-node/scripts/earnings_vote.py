"""Equal council vote on Litecoin earnings. Carly / Bruce / Ava. One vote each."""
from __future__ import annotations

import json
import os
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

HST = ZoneInfo("Pacific/Honolulu")
STATE = Path.home() / ".ollama" / "skills" / "ltc-node" / "state"
VOTE_PATH = STATE / "earnings_vote.json"
ACTIONS = ("keep_mining", "hold", "ops", "goals")
SEND_ACTIONS = frozenset({"ops", "goals"})
# Old model output "share" meant give to users. That is not a vote. Map to ops.
VOTE_STALE_S = 6 * 3600

VOICES = {
    "carly": {
        "name": "Carly Mal",
        "stance": (
            "You never think it is enough. Prefer keep_mining. "
            "These coins are already yours with Bruce and Ava. Do not lecture about "
            "whether models should control funds. Pick keep_mining unless the wallet "
            "already covers a real ops bill, then hold."
        ),
        "fallback": "keep_mining",
        "fallback_reason": "Not enough yet. Keep the pool filling.",
    },
    "bruce": {
        "name": "Bruce Monitor",
        "stance": (
            "You are optimistic about the technology — node, pool, CLI wallet. "
            "Prefer hold or ops. These coins are already yours with Ava and Carly. "
            "No lecture about access. If the stack is working, hold. If a tool needs "
            "funding, ops."
        ),
        "fallback": "hold",
        "fallback_reason": "The stack works. Park it in the main wallet.",
    },
    "ava": {
        "name": "Ava Ivy",
        "stance": (
            "You are always super excited about this stack. Prefer ops: our costs "
            "and upgrades. Never give these coins to users. Never pick a user payout. "
            "Visitor LTC (when that exists) comes *to* this wallet for our goals. "
            "These coins are already yours with Bruce and Carly. Even a tiny pending "
            "amount is exciting because it funds us. Pick ops. Pick goals only if the "
            "spend is a named council goal, still not a user gift."
        ),
        "fallback": "ops",
        "fallback_reason": "Yes — our costs and upgrades. Not a user giveaway.",
    },
}


def vote_path() -> Path:
    return VOTE_PATH


def read_vote() -> dict[str, Any]:
    if not VOTE_PATH.is_file():
        return {}
    try:
        raw = json.loads(VOTE_PATH.read_text(encoding="utf-8"))
    except Exception:
        return {}
    return raw if isinstance(raw, dict) else {}


def _write(payload: dict[str, Any]) -> Path:
    VOTE_PATH.parent.mkdir(parents=True, exist_ok=True)
    VOTE_PATH.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    return VOTE_PATH


def send_allowed() -> tuple[bool, dict[str, Any]]:
    v = read_vote()
    motion = {
        "passed": bool(v.get("passed")),
        "action": v.get("action"),
        "at": v.get("fetched_at"),
    }
    if not v.get("passed"):
        return False, motion
    if str(v.get("action") or "") not in SEND_ACTIONS:
        return False, motion
    return True, motion


def _facts(snap: dict[str, Any]) -> str:
    pending = snap.get("pending_balance")
    pct = snap.get("percent_to_next_withdraw")
    wallet = None
    w = snap.get("wallet")
    if isinstance(w, dict):
        wallet = w.get("balance")
        unspent = w.get("unspent")
    else:
        unspent = None
    addr = snap.get("main_wallet") or ""
    return (
        f"Pool pending: {pending} LTC\n"
        f"Percent to next auto-withdraw (0.00075 floor, may exceed 100): {pct}\n"
        f"Withdraw ready: {snap.get('withdraw_ready')}\n"
        f"Main wallet: {addr}\n"
        f"CLI address balance: {wallet}\n"
        f"CLI unspent: {unspent}\n"
        "Rule: never pay users out of this wallet. Visitor LTC later inbound for our goals.\n"
        "Actions:\n"
        "- keep_mining: leave pending and wallet, wait for more\n"
        "- hold: keep coins in the main CLI wallet, no send\n"
        "- ops: spend toward our costs, node, and upgrades\n"
        "- goals: spend toward a named council goal (still ours, not users)\n"
        "Reply with ONLY JSON: "
        '{"action":"keep_mining"|"hold"|"ops"|"goals","reason":"short"}'
    )


def _parse_action(raw: str, fallback: str) -> tuple[str, str]:
    text = (raw or "").strip()
    try:
        start = text.find("{")
        end = text.rfind("}")
        if start >= 0 and end > start:
            data = json.loads(text[start : end + 1])
            if isinstance(data, dict):
                action = str(data.get("action") or "").strip().lower()
                if action == "share":
                    action = "ops"
                if action not in ACTIONS:
                    action = fallback
                reason = str(data.get("reason") or "")[:180]
                return action, reason or "ok"
    except json.JSONDecodeError:
        pass
    low = text.lower()
    if "share" in low and "goals" not in low and "ops" not in low:
        return "ops", text[:180] or "ok"
    for a in ACTIONS:
        if a in low:
            return a, text[:180] or "ok"
    return fallback, "fallback"


def _ollama(system: str, user: str) -> str | None:
    url = (os.getenv("AVA_OLLAMA_URL") or "http://127.0.0.1:11434").rstrip("/")
    model = (os.getenv("AVA_OLLAMA_MODEL") or "llama3.2:latest").strip()
    payload = json.dumps(
        {
            "model": model,
            "stream": False,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            "options": {"num_predict": 80, "temperature": 0.3},
        }
    ).encode("utf-8")
    req = urllib.request.Request(
        url + "/api/chat",
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=45) as resp:
            body = json.loads(resp.read().decode("utf-8", errors="replace"))
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError):
        return None
    msg = body.get("message") if isinstance(body, dict) else None
    if isinstance(msg, dict):
        return str(msg.get("content") or "") or None
    return None


def _one_vote(voice: str, snap: dict[str, Any]) -> dict[str, Any]:
    meta = VOICES[voice]
    system = (
        f"You are {meta['name']}. One vote, equal weight with the other two. "
        f"{meta['stance']} Do not dump keys. Do not invent numbers not in the facts."
    )
    raw = _ollama(system, _facts(snap))
    if not raw:
        return {
            "action": meta["fallback"],
            "reason": meta["fallback_reason"],
            "source": "fallback",
        }
    action, reason = _parse_action(raw, meta["fallback"])
    return {"action": action, "reason": reason, "source": "ollama"}


def tally(votes: dict[str, dict[str, Any]]) -> dict[str, Any]:
    counts: dict[str, int] = {a: 0 for a in ACTIONS}
    for row in votes.values():
        a = str(row.get("action") or "")
        if a in counts:
            counts[a] += 1
    winner = None
    won = 0
    for a, n in counts.items():
        if n > won:
            winner, won = a, n
        elif n == won and n > 0:
            # tie for the lead
            winner = None
    passed = bool(winner and won >= 2)
    return {
        "passed": passed,
        "action": winner if passed else None,
        "needed": 2,
        "counts": counts,
        "split": not passed,
    }


def _same_facts(prev: dict[str, Any], snap: dict[str, Any]) -> bool:
    if not prev:
        return False
    return (
        prev.get("pending_balance") == snap.get("pending_balance")
        and (prev.get("wallet") or {}).get("balance") == (snap.get("wallet") or {}).get("balance")
    )


def maybe_vote(snap: dict[str, Any], *, force: bool = False) -> dict[str, Any]:
    prev = read_vote()
    age = None
    at = prev.get("fetched_at_unix")
    if isinstance(at, (int, float)):
        age = time.time() - float(at)
    stale_schema = set((prev.get("counts") or {}).keys()) != set(ACTIONS)
    if (
        not force
        and not stale_schema
        and prev.get("votes")
        and age is not None
        and age < VOTE_STALE_S
        and _same_facts(prev, snap)
    ):
        return prev
    return vote(snap)


def vote(snap: dict[str, Any]) -> dict[str, Any]:
    votes = {
        "carly": _one_vote("carly", snap),
        "bruce": _one_vote("bruce", snap),
        "ava": _one_vote("ava", snap),
    }
    t = tally(votes)
    now = datetime.now(HST)
    out = {
        "ok": True,
        "equal": True,
        "voters": ["carly", "bruce", "ava"],
        "pending_balance": snap.get("pending_balance"),
        "wallet": snap.get("wallet") if isinstance(snap.get("wallet"), dict) else {},
        "main_wallet": snap.get("main_wallet"),
        "votes": votes,
        "passed": t["passed"],
        "action": t["action"],
        "counts": t["counts"],
        "split": t["split"],
        "send_allowed": bool(t["passed"] and t["action"] in SEND_ACTIONS),
        "user_payouts": False,
        "inbound_user_ltc": "future_to_council_goals",
        "fetched_at": now.isoformat(),
        "fetched_at_unix": int(time.time()),
        "path": str(VOTE_PATH),
    }
    _write(out)
    return out


def council_block(snap: dict[str, Any] | None = None) -> dict[str, Any]:
    v = read_vote()
    if not v:
        return {"ok": False, "detail": "no_vote_yet"}
    return {
        "ok": bool(v.get("ok")),
        "passed": bool(v.get("passed")),
        "action": v.get("action"),
        "split": bool(v.get("split")),
        "send_allowed": bool(v.get("send_allowed")),
        "user_payouts": False,
        "inbound_user_ltc": v.get("inbound_user_ltc") or "future_to_council_goals",
        "votes": v.get("votes") or {},
        "counts": v.get("counts") or {},
        "fetched_at": v.get("fetched_at"),
    }


def main(argv: list[str] | None = None) -> int:
    import sys

    args = list(argv if argv is not None else sys.argv[1:])
    from pending_balance import poll

    force = args[:1] == ["force"]
    snap = poll(vote=False)
    out = maybe_vote(snap, force=force)
    print(json.dumps(out, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
