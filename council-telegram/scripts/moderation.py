"""Moderation council: Ava proposes → Bruce + Carly vote → Ava executes if pass."""
from __future__ import annotations

import json
import re
import time
import uuid
from typing import Any

from . import ollama_client, telegram, trust
from .config import BOT_IDS_IGNORE, MOD_LOG_DIR, NUM_PREDICT, Config, RUNS_DIR
from .personas import system_for

SPAM_HINTS = re.compile(
    r"(t\.me/\+|crypto\s*airdrop|double\s*your|whatsapp\s*\+|guaranteed\s*profit|"
    r"seed\s*phrase|connect\s*wallet|free\s*nitro|claim\s*reward|investment\s*platform|"
    r"dm\s*me\s*for\s*(?:paid|work)|@admin\s*verify)",
    re.I,
)

VOTE_SYSTEM = """You are voting on a Telegram group moderation action for Root Record.
Reply with ONLY JSON: {"vote":"yes"|"no"|"abstain","action":"delete"|"mute"|"kick"|"ban"|"none","hours":0,"reason":"short"}
- yes = support the proposed action (or your stricter action if you set action)
- Prefer delete for one-off spam; mute (hours) for repeat noise; kick for clear scammers; ban for persistent/malicious.
- Never ban Alexander / group owner. Never leak secrets. Be empirical (Bruce) or AppSec-strict (Carly).
"""


def heuristic_suspect(text: str, trust_score: int) -> bool:
    if not text:
        return False
    if SPAM_HINTS.search(text):
        return True
    # lots of URLs + low trust
    urls = len(re.findall(r"https?://", text))
    if trust_score < 25 and urls >= 2:
        return True
    if trust_score < 15 and urls >= 1 and len(text) > 80:
        return True
    return False


def _parse_vote(raw: str) -> dict[str, Any]:
    raw = (raw or "").strip()
    try:
        start = raw.find("{")
        end = raw.rfind("}")
        if start >= 0 and end > start:
            data = json.loads(raw[start : end + 1])
            if isinstance(data, dict):
                vote = str(data.get("vote", "abstain")).lower()
                if vote not in ("yes", "no", "abstain"):
                    vote = "abstain"
                action = str(data.get("action", "none")).lower()
                if action not in ("delete", "mute", "kick", "ban", "none"):
                    action = "none"
                try:
                    hours = int(data.get("hours") or 0)
                except (TypeError, ValueError):
                    hours = 0
                return {
                    "vote": vote,
                    "action": action,
                    "hours": max(0, min(168, hours)),
                    "reason": str(data.get("reason") or "")[:200],
                }
    except json.JSONDecodeError:
        pass
    low = raw.lower()
    if "yes" in low and "no" not in low.split("yes")[0][-10:]:
        return {"vote": "yes", "action": "delete", "hours": 0, "reason": "parse-fallback"}
    return {"vote": "abstain", "action": "none", "hours": 0, "reason": "unparseable"}


def _severity(action: str) -> int:
    return {"none": 0, "delete": 1, "mute": 2, "kick": 3, "ban": 4}.get(action, 0)


def tally(
    ava_proposal: dict[str, Any], bruce: dict[str, Any], carly: dict[str, Any]
) -> dict[str, Any]:
    votes = [ava_proposal, bruce, carly]
    yes = sum(1 for v in votes if v.get("vote") == "yes")
    no = sum(1 for v in votes if v.get("vote") == "no")
    # Chosen action = max severity among yes votes; if tie-ish, prefer Ava's proposal when she said yes
    yes_actions = [v for v in votes if v.get("vote") == "yes"]
    if yes >= 2:
        best = max(yes_actions, key=lambda v: _severity(str(v.get("action"))))
        action = best.get("action") or "delete"
        hours = int(best.get("hours") or 0)
        if action == "mute" and hours <= 0:
            hours = 24
        passed = True
    else:
        action = "none"
        hours = 0
        passed = False
    return {
        "passed": passed,
        "yes": yes,
        "no": no,
        "action": action,
        "hours": hours,
        "votes": {"ava": ava_proposal, "bruce": bruce, "carly": carly},
    }


RAW_DELTAS = {
    "mute": -15,
    "kick": -30,
    "ban": -50,
    "delete": -20,
    "none": -2,
}


def _append_mod_log(payload: dict[str, Any]) -> None:
    try:
        MOD_LOG_DIR.mkdir(parents=True, exist_ok=True)
        day = time.strftime("%Y-%m-%d")
        path = MOD_LOG_DIR / f"{day}.jsonl"
        with path.open("a", encoding="utf-8") as fh:
            fh.write(json.dumps(payload, separators=(",", ":")) + "\n")
    except OSError:
        pass


def _member_protected(cfg: Config, chat_id: int | str, user_id: int) -> str | None:
    if int(user_id) in BOT_IDS_IGNORE:
        return "council_bot"
    info = telegram.get_chat_member(cfg.token_for("ava"), chat_id, user_id)
    result = info.get("result") if isinstance(info.get("result"), dict) else {}
    user = result.get("user") or {}
    if user.get("is_bot"):
        return "bot"
    status = str(result.get("status") or "")
    if status in {"creator", "administrator"}:
        return status
    return None


def _trust_after_mod(
    cfg: Config,
    trust_data: dict[str, Any] | None,
    *,
    target_user_id: int,
    action: str,
    ok: bool,
    heuristic: bool,
) -> int | None:
    if not trust_data:
        return None
    raw = RAW_DELTAS.get(action if ok else "none")
    if raw is None:
        return None
    if not ok and not heuristic:
        raw = 0
    if raw == 0:
        return trust.get_score(trust_data, target_user_id)
    return trust.apply_delta(
        trust_data,
        target_user_id,
        raw,
        reason=f"mod:{action if ok else 'no-action'}",
        gain_factor=cfg.trust_gain_factor,
        loss_factor=cfg.trust_loss_factor,
    )


def run_moderation_council(
    cfg: Config,
    chat_id: int | str,
    *,
    target_user_id: int,
    target_username: str,
    message_text: str,
    message_id: int | None,
    trust_score: int,
    owner_id: str,
    trust_data: dict[str, Any] | None = None,
    speaker: str = "",
    heuristic: bool = False,
) -> dict[str, Any]:
    """Ava proposes in public; Bruce+Carly vote (posted); Ava executes if ≥2 yes."""
    if str(target_user_id) == str(owner_id):
        telegram.send_message(
            cfg.token_for("ava"),
            chat_id,
            "That's the group owner — no vote.",
            reply_to=message_id,
        )
        return {"ok": False, "reason": "owner_protected"}

    run_id = uuid.uuid4().hex[:10]
    who = speaker or f"@{target_username or 'user'}"
    context = (
        f"Suspect message from {who} (id {target_user_id}), "
        f"trust_score={trust_score}:\n<<<\n{message_text[:1500]}\n>>>\n"
        "Propose moderation. Prefer least privilege that stops harm."
        " Use the person's display name when given."
    )

    ava_raw = ollama_client.chat(
        cfg,
        cfg.model_for("ava"),
        system_for("ava")
        + "\nYou are lead moderator for this vote. Propose an action; the others will vote.\n"
        + VOTE_SYSTEM,
        context,
        num_predict=NUM_PREDICT.get("classifier", 80) + 40,
        voice="ava",
    )
    ava_v = _parse_vote(ava_raw)
    if ava_v["action"] == "none" and ava_v["vote"] == "yes":
        ava_v["action"] = "delete"

    telegram.send_message(
        cfg.token_for("ava"),
        chat_id,
        f"🛡️ Moderation council — possible spam/scam.\n"
        f"Proposal: {ava_v['action']}"
        + (f" ({ava_v['hours']}h)" if ava_v["action"] == "mute" else "")
        + f" — {ava_v.get('reason') or 'review'}\n"
        f"Bruce, Carly: vote.",
        reply_to=message_id,
    )

    bruce_raw = ollama_client.chat(
        cfg,
        cfg.model_for("bruce"),
        system_for("bruce") + "\n" + VOTE_SYSTEM,
        f"Ava proposed: {json.dumps(ava_v)}\n\n{context}",
        num_predict=120,
        voice="bruce",
    )
    bruce_v = _parse_vote(bruce_raw)
    telegram.send_message(
        cfg.token_for("bruce"),
        chat_id,
        f"Vote: {bruce_v['vote'].upper()} → {bruce_v['action']}. {bruce_v.get('reason') or ''}".strip(),
    )

    carly_raw = ollama_client.chat(
        cfg,
        cfg.model_for("carly"),
        system_for("carly") + "\n" + VOTE_SYSTEM,
        f"Ava proposed: {json.dumps(ava_v)}\nBruce: {json.dumps(bruce_v)}\n\n{context}",
        num_predict=120,
        voice="carly",
    )
    carly_v = _parse_vote(carly_raw)
    telegram.send_message(
        cfg.token_for("carly"),
        chat_id,
        f"Vote: {carly_v['vote'].upper()} → {carly_v['action']}. {carly_v.get('reason') or ''}".strip(),
    )

    result = tally(ava_v, bruce_v, carly_v)
    result["run_id"] = run_id

    if not result["passed"]:
        telegram.send_message(
            cfg.token_for("ava"),
            chat_id,
            f"Council: no action ({result['yes']} yes / {result['no']} no). Watching.",
        )
        nxt = _trust_after_mod(
            cfg,
            trust_data,
            target_user_id=target_user_id,
            action="none",
            ok=False,
            heuristic=heuristic,
        )
        result["trust"] = nxt
        _append_mod_log(
            {
                "ts": int(time.time()),
                "run_id": run_id,
                "passed": False,
                "action": "none",
                "target": target_user_id,
                "trust": nxt,
            }
        )
        try:
            log_dir = RUNS_DIR / f"mod-{run_id}"
            log_dir.mkdir(parents=True, exist_ok=True)
            (log_dir / "result.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        except OSError:
            pass
        return result

    # Execute as Ava (lead moderator)
    action = result["action"]
    if action in {"mute", "kick", "ban"}:
        blocked = _member_protected(cfg, chat_id, target_user_id)
        if blocked:
            telegram.send_message(
                cfg.token_for("ava"),
                chat_id,
                f"Council wanted {action} but target is {blocked} — skipped.",
            )
            result["exec"] = {"ok": False, "description": f"protected:{blocked}"}
            _append_mod_log(
                {
                    "ts": int(time.time()),
                    "run_id": run_id,
                    "passed": True,
                    "action": action,
                    "skipped": blocked,
                    "target": target_user_id,
                }
            )
            return result

    exec_out = execute_action(
        cfg,
        chat_id,
        action=action,
        user_id=target_user_id,
        message_id=message_id,
        hours=int(result.get("hours") or 0),
    )
    result["exec"] = exec_out
    label = {
        "delete": "deleted the message",
        "mute": f"muted for {result.get('hours') or 24}h",
        "kick": "removed from the group",
        "ban": "banned",
    }.get(action, action)
    ok = bool(exec_out.get("ok"))
    nxt = _trust_after_mod(
        cfg,
        trust_data,
        target_user_id=target_user_id,
        action=action,
        ok=ok,
        heuristic=heuristic,
    )
    result["trust"] = nxt
    telegram.send_message(
        cfg.token_for("ava"),
        chat_id,
        f"Council passed ({result['yes']}/3). I {label}."
        + ("" if ok else f" (API: {exec_out.get('description') or 'failed'})"),
    )
    _append_mod_log(
        {
            "ts": int(time.time()),
            "run_id": run_id,
            "passed": True,
            "action": action,
            "ok": ok,
            "target": target_user_id,
            "trust": nxt,
        }
    )
    try:
        log_dir = RUNS_DIR / f"mod-{run_id}"
        log_dir.mkdir(parents=True, exist_ok=True)
        (log_dir / "result.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    except OSError:
        pass
    return result


def execute_action(
    cfg: Config,
    chat_id: int | str,
    *,
    action: str,
    user_id: int,
    message_id: int | None,
    hours: int = 0,
) -> dict[str, Any]:
    token = cfg.token_for("ava")
    if action == "delete":
        if message_id is None:
            return {"ok": False, "description": "no message_id"}
        return telegram.delete_message(token, chat_id, message_id)
    if action == "mute":
        until = int(time.time()) + max(1, hours or 24) * 3600
        out = telegram.restrict_chat_member(token, chat_id, user_id, until_date=until, can_send_messages=False)
        if message_id is not None:
            telegram.delete_message(token, chat_id, message_id)
        return out
    if action == "kick":
        # ban then unban = kick
        ban = telegram.ban_chat_member(token, chat_id, user_id, revoke_messages=True)
        if ban.get("ok"):
            telegram.unban_chat_member(token, chat_id, user_id)
        if message_id is not None:
            telegram.delete_message(token, chat_id, message_id)
        return ban
    if action == "ban":
        out = telegram.ban_chat_member(token, chat_id, user_id, revoke_messages=True)
        if message_id is not None:
            telegram.delete_message(token, chat_id, message_id)
        return out
    return {"ok": False, "description": "unknown action"}
