"""Telegram Council entry — Ava polls group + Ava DMs; Bruce/Carly poll their DMs only.

Does NOT use AVA_TELEGRAM_BOT_TOKEN / apps/core telegram polling.
"""
from __future__ import annotations

import argparse
import re
import signal
import sys
import threading
import time
import traceback
from typing import Any

from . import (
    approval,
    chatlog,
    commands,
    cursor_api,
    debug,
    feelings,
    moderation,
    ollama_ctl,
    prompting,
    proposals,
    queue,
    router,
    state,
    telegram,
    trust,
    worker,
)
from . import skills as skillpack
from .config import BOT_IDS_IGNORE, OWNER_USERNAMES, Config, load_config

HOLDOFF_PHRASES = frozenset(
    {
        "hold off",
        "holdoff",
        "quiet",
        "stop talking",
        "discussion off",
        "/holdoff",
        "/discussion off",
    }
)
RESUME_PHRASES = frozenset(
    {
        "resume",
        "you can talk",
        "discussion on",
        "/resume",
        "/discussion on",
    }
)


def _desk_read_argv(text: str) -> list[str]:
    m = re.search(
        r"(plan-[a-z0-9]+|ecoflow|data/state/[A-Za-z0-9._-]+|apps/[A-Za-z0-9._/-]+)",
        text or "",
        re.I,
    )
    return [m.group(1)] if m else []


def _web_facts_argv(text: str) -> list[str]:
    m = re.search(r"https://[^\s>]+", text or "", re.I)
    return [m.group(0).rstrip(".,)")] if m else []


def _skill_exec_argv(sid: str, text: str, *, execute: bool = False) -> list[str] | None:
    if sid == "desk-read":
        return _desk_read_argv(text)
    if sid == "web-facts":
        return _web_facts_argv(text)
    if sid == "disk-session":
        return skillpack.disk_session_argv(text, execute=execute)
    if sid == "panels-cam":
        return ["--show"]
    if sid == "goals-desk" and re.search(r"\badd (?:a )?goal\b", text or "", re.I):
        rest = re.split(r"\badd (?:a )?goal\b", text or "", maxsplit=1, flags=re.I)
        title = (rest[1] if len(rest) > 1 else "").strip()
        return ["add", title] if title else []
    if sid == "cooking-desk":
        low = (text or "").lower()
        if "from pantry" in low or "what can we cook" in low:
            return ["from-pantry"]
        if "save this recipe" in low:
            return []
        return ["list"]
    if sid == "pantry-desk":
        return ["list"]
    if sid == "nutrition-desk":
        return ["list"]
    if sid == "ltc-status" and re.search(r"\bbalance\b", text or "", re.I):
        return ["balance"]
    return None


def _msg_text(message: dict[str, Any]) -> str:
    return (message.get("text") or message.get("caption") or "").strip()


def _from_user(message: dict[str, Any]) -> dict[str, Any]:
    return message.get("from") or {}


def _maybe_bind_owner(st: dict[str, Any], cfg: Config, user: dict[str, Any], text: str) -> None:
    if state.owner_bound(st, cfg):
        return
    uid = user.get("id")
    uname = (user.get("username") or "").strip()
    if text.strip().lower().startswith("/claim") and uid:
        state.bind_owner(st, uid, cfg)
        return
    if uname and uname.lower() in OWNER_USERNAMES and uid:
        state.bind_owner(st, uid, cfg)


def _announce_levels(
    cfg: Config,
    chat_id: int | str,
    trust_data: dict[str, Any],
    user_id: int | str,
    previous: int,
    current: int,
) -> None:
    marks = trust.pop_level_ups(trust_data, user_id, previous, current)
    who = trust.display_of(trust_data, user_id)
    for mark in marks:
        label = "Trusted" if mark == 60 else "Core"
        print(f"trust-level {who} crossed {mark} ({label}) chat={chat_id}", flush=True)


def _resolve_target(trust_data: dict[str, Any], token: str) -> str | None:
    raw = token.strip()
    if not raw:
        return None
    found = trust.find_by_alias(trust_data, raw) or trust.find_by_username(trust_data, raw)
    if found:
        return found
    digits = raw.lstrip("@")
    if digits.isdigit():
        return digits
    return None


def _cursor_followup(cfg: Config, ava, result: dict[str, Any]) -> None:
    if result.get("degraded"):
        ava(result.get("message") or "Implementer isn’t plugged in yet.")
        return
    if not result.get("ok"):
        ava(f"Implementer launch failed: {result.get('message', 'error')}")
        return
    data = result.get("data") or {}
    agent_id = str(data.get("id") or data.get("agentId") or (data.get("agent") or {}).get("id") or "")
    ava(f"Implementer started{(' ' + agent_id) if agent_id else ''}. Polling…")
    if not agent_id:
        ava("No agent id in response — cannot poll.")
        return
    polled = cursor_api.poll_agent(cfg, agent_id)
    pr = cursor_api.extract_pr_url(polled.get("data") or polled)
    if polled.get("ok"):
        ava(f"Implementer finished. {('PR: ' + pr) if pr else 'No PR URL in response.'}")
        return
    status = polled.get("status") or polled.get("message") or "unknown"
    extra = f" {pr}" if pr else ""
    ava(f"Implementer status: {status}.{extra}")


def _apply_owner_delta(
    cfg: Config,
    st: dict[str, Any],
    trust_data: dict[str, Any],
    chat_id: int | str,
    target_id: str,
    raw: float,
    reason: str,
) -> int:
    prev_avg = trust.get_score(trust_data, target_id)
    owner = str(target_id) == str(state.owner_id(st, cfg))
    trust.apply_delta(
        trust_data,
        target_id,
        raw,
        is_owner=owner,
        reason=reason,
        gain_factor=cfg.trust_gain_factor,
        loss_factor=cfg.trust_loss_factor,
    )
    nxt_avg = trust.get_score(trust_data, target_id)
    _announce_levels(cfg, chat_id, trust_data, target_id, prev_avg, nxt_avg)
    return trust.combined(trust_data, target_id)


def _handle_command(
    cfg: Config,
    st: dict[str, Any],
    trust_data: dict[str, Any],
    chat_id: int | str,
    user: dict[str, Any],
    text: str,
    *,
    reply_to: int | None = None,
    thread_id: int | None = None,
    reply_voice: str = "ava",
) -> bool:
    """Handle owner/slash commands. Returns True if consumed."""
    raw = commands.normalize_slash(text)
    low = raw.lower()
    uid = user.get("id")
    is_owner = state.is_owner(st, cfg, uid) if uid is not None else False
    bound = state.owner_bound(st, cfg)
    reply_voice = reply_voice if reply_voice in ("ava", "bruce", "carly") else "ava"

    def _say(voice: str, msg: str) -> None:
        telegram.send_message(
            cfg.token_for(voice),
            chat_id,
            msg,
            reply_to=reply_to,
            message_thread_id=thread_id,
        )

    def ava(msg: str) -> None:
        _say(reply_voice, msg)

    def carly(msg: str) -> None:
        _say(reply_voice if reply_voice != "ava" else "carly", msg)

    if low.startswith("/start"):
        who = {"ava": "Ava Ivy", "bruce": "Bruce Monitor", "carly": "Carly Mal"}[reply_voice]
        ava(f"Hey. I'm {who}. Same trust as the group. Talk to me here — I stay myself.")
        return True

    if low.startswith("/brainstorm"):
        if not is_owner:
            carly("No.")
            return True
        from . import brainstorm as _bs

        return _bs.handle_owner_text(cfg, chat_id, raw)

    if low.startswith("/audio"):
        if not is_owner:
            carly("No.")
            return True
        from . import audio_request as _ar

        return _ar.handle_text(
            cfg,
            chat_id,
            uid,
            raw,
            reply_to=reply_to,
            thread_id=thread_id,
        )

    if low.startswith("/help") or low == "help":
        ava(commands.help_text(commands.latest_approve_hint(), voice=reply_voice))
        return True

    if low.startswith("/goals"):
        from pathlib import Path
        import sys as _sys

        _g = Path.home() / ".ollama" / "skills" / "goals" / "scripts"
        if str(_g) not in _sys.path:
            _sys.path.insert(0, str(_g))
        from council_goals import add_goal, prompt_lines

        parts = raw.split(maxsplit=2)
        if len(parts) >= 3 and parts[1].lower() == "add":
            got = add_goal(parts[2], voice=reply_voice)
            ava(f"{got.get('action') or got.get('error')} {got.get('id') or ''}".strip())
            return True
        ava(prompt_lines(cap=3500))
        return True

    if low.startswith("/recipes"):
        from pathlib import Path
        import sys as _sys

        _c = Path.home() / ".ollama" / "skills" / "cooking" / "scripts"
        if str(_c) not in _sys.path:
            _sys.path.insert(0, str(_c))
        from recipes import prompt_lines as _recipe_lines, save_recipe

        parts = raw.split(maxsplit=2)
        if len(parts) >= 3 and parts[1].lower() == "save":
            title, _, rest = parts[2].partition("|")
            ings, _, notes = rest.partition("|")
            got = save_recipe(
                title.strip(),
                ingredients=ings.strip(),
                notes=(notes.strip() or "slash /recipes save"),
                source="user",
                voice=reply_voice,
            )
            ava(f"{got.get('action') or got.get('error')} {got.get('id') or ''}".strip())
            return True
        if len(parts) >= 2 and parts[1].lower() == "pantry":
            from recipes import from_pantry as _from_pantry

            ava(_from_pantry())
            return True
        ava(_recipe_lines(cap=3500))
        return True

    if low.startswith("/pantry"):
        from pathlib import Path
        import sys as _sys

        _p = Path.home() / ".ollama" / "skills" / "pantry" / "scripts"
        if str(_p) not in _sys.path:
            _sys.path.insert(0, str(_p))
        from pantry import add_item, prompt_lines as _pantry_lines, use_item

        parts = raw.split()
        if len(parts) >= 3 and parts[1].lower() == "add":
            name = parts[2]
            qty = parts[3] if len(parts) > 3 else 1
            unit = parts[4] if len(parts) > 4 else "ea"
            if len(parts) > 5:
                name = " ".join(parts[2:-2])
                qty = parts[-2]
                unit = parts[-1]
            got = add_item(name, qty, unit=unit, voice=reply_voice)
            ava(f"{got.get('action')} {got.get('id')} {got.get('qty')} {got.get('unit')}".strip())
            return True
        if len(parts) >= 3 and parts[1].lower() == "use":
            qty = parts[-1] if len(parts) > 3 else 1
            name = " ".join(parts[2:-1] if len(parts) > 3 else parts[2:])
            got = use_item(name, qty, voice=reply_voice)
            ava(f"{got.get('action') or got.get('error')} {got.get('id') or ''} {got.get('qty', '')}".strip())
            return True
        ava(_pantry_lines(cap=3500))
        return True

    if low.startswith("/nutrition"):
        from pathlib import Path
        import sys as _sys

        _n = Path.home() / ".ollama" / "skills" / "nutrition" / "scripts"
        if str(_n) not in _sys.path:
            _sys.path.insert(0, str(_n))
        from foods import prompt_lines as _food_lines

        q = raw.split(maxsplit=1)
        query = q[1] if len(q) > 1 else ""
        ava(_food_lines(query=query, cap=3500))
        return True

    if low.startswith("/proposals"):
        ids = proposals.list_pending_ids()
        if not ids:
            ava("No pending plans. After a thought session Bruce files or amends the daily proposal.")
        else:
            ava("Pending: " + ", ".join(f"/{'approve ' + i}" for i in ids[:12]))
        return True

    if low.startswith("/done"):
        if not is_owner:
            carly("No.")
            return True
        rec = proposals.open_daily(chat_id)
        if not rec:
            ava("No open daily proposal.")
            return True
        proposals.freeze(str(rec.get("id") or ""))
        ava(f"Finished {rec.get('id')}. Next round Bruce opens a new daily file.")
        commands.publish_commands(cfg, approve_hint=commands.latest_approve_hint())
        return True

    if low.startswith("/feelings") or low == "feelings":
        ava(feelings.status_text())
        return True

    if low.startswith("/debug"):
        if not is_owner:
            carly("No.")
            return True
        ava(debug.format_last(st))
        return True

    if low.startswith("/skillrun"):
        if not is_owner:
            carly("No.")
            return True
        parts = raw.split()
        sid = parts[1].strip() if len(parts) > 1 else ""
        meta = skillpack.get_skill(sid)
        if not meta or str(meta.get("risk") or "") != "exec":
            ava("Usage: /skillrun <exec-skill-id> (allowlisted only).")
            return True
        voices = [str(v).lower() for v in (meta.get("voices") or ["bruce"])]
        voice = voices[0] if voices and voices[0] in ("ava", "bruce", "carly") else "bruce"
        extra = (
            skillpack.sanitize_disk_argv(parts[2:])
            if sid == "disk-session"
            else [a for a in parts[2:] if a.startswith("--") and ".." not in a]
        )
        job = queue.enqueue(
            voice=voice,
            prompt=f"exec skill {sid}",
            chat_id=chat_id,
            kind="speak",
            meta={
                "allow_loop": False,
                "skill_exec": True,
                "skill_id": sid,
                "skill_argv": extra,
            },
        )
        ava("Queued." if job else "Queue full or duplicate.")
        return True

    if low.startswith("/ping") or low.strip() == "ping":
        ava("pong. council online.")
        return True

    # /claim
    if low.startswith("/claim"):
        if state.owner_bound(st, cfg):
            ava("Owner already bound.")
        else:
            state.bind_owner(st, uid, cfg)
            ava("Claimed. You’re bound as owner.")
        return True

    # /status — anyone can ask for high-level status (no secrets)
    if low.startswith("/status") or low == "status":
        ollama_up = ollama_ctl.is_up(cfg)
        flm_up = ollama_ctl.flm_is_up()
        voices = ollama_ctl.voices_up(cfg)
        lines = state.status_lines(st, cfg, ollama_up, flm_up=flm_up, voices_up=voices)
        if uid is not None:
            if is_owner:
                trust.set_score(trust_data, uid, trust.OWNER_SCORE, is_owner=True)
            who = "Alexander" if is_owner else trust.display_of(trust_data, uid)
            lines = (
                f"{lines}\n\n"
                + trust.format_user_card(trust_data, uid, who=who, kind="status")
            )
        ava(lines)
        return True

    # Natural holdoff / resume (owner only once bound; before bind, lock execute)
    if low in HOLDOFF_PHRASES or low.startswith("/holdoff") or low == "/discussion off":
        if not bound:
            ava("Owner not bound yet — /claim or message as Alexrs94 first. Execute locked.")
            return True
        if not is_owner:
            carly("No.")
            return True
        st["discussion"] = "off"
        feelings.apply_event("holdoff")
        state.save_state(st)
        from . import power_status as _pwr

        _pwr.announce("down", force=True)
        ollama_ctl.stop(cfg)
        return True

    if low in RESUME_PHRASES or low.startswith("/resume") or low == "/discussion on":
        if not bound:
            ava("Owner not bound yet — /claim first.")
            return True
        if not is_owner:
            carly("No.")
            return True
        from . import power_status as _pwr

        _pwr.announce("up", force=True)
        ollama_ctl.start(cfg)
        st["discussion"] = "on"
        feelings.apply_event("resume")
        state.save_state(st)
        return True

    if low.startswith("/discussion"):
        if not is_owner:
            carly("No.")
            return True
        parts = raw.split()
        arg = parts[1].lower() if len(parts) > 1 else ""
        if arg == "off":
            st["discussion"] = "off"
            state.save_state(st)
            from . import power_status as _pwr

            _pwr.announce("down", force=True)
        elif arg == "on":
            from . import power_status as _pwr

            _pwr.announce("up", force=True)
            ollama_ctl.start(cfg)
            st["discussion"] = "on"
            state.save_state(st)
        else:
            ava("Usage: /discussion on|off")
        return True

    if low.startswith("/mode"):
        if not is_owner:
            carly("No.")
            return True
        parts = raw.split()
        arg = parts[1].lower() if len(parts) > 1 else ""
        if arg in ("auto", "casual", "council"):
            st["mode"] = arg
            state.save_state(st)
            ava(f"Mode set to {arg}.")
        else:
            ava("Usage: /mode auto|casual|council")
        return True

    if low.startswith("/autoexecute"):
        if not is_owner:
            carly("No.")
            return True
        parts = raw.split()
        arg = parts[1].lower() if len(parts) > 1 else ""
        if arg == "on":
            st["auto_execute"] = True
            state.save_state(st)
            ava("auto_execute ON — owner-only; still gated by trust ≥95. Non-owners cannot auto-execute.")
        elif arg == "off":
            st["auto_execute"] = False
            state.save_state(st)
            ava("auto_execute OFF.")
        else:
            ava("Usage: /autoexecute on|off")
        return True

    if low.startswith("/selfrepair"):
        from . import self_repair

        parts = raw.split()
        arg = parts[1].lower() if len(parts) > 1 else "status"
        if arg in {"on", "start"}:
            if not is_owner:
                carly("No.")
                return True
            mins = 30
            if len(parts) > 2 and parts[2].isdigit():
                mins = int(parts[2])
            snap = self_repair.enable(minutes=mins, max_per_hour=30)
            ava(
                f"Self-repair on for {snap['remaining_s'] // 60} min. "
                f"Implementer cap {snap['max_per_hour']}/hour."
            )
            return True
        if arg in {"off", "stop"}:
            if not is_owner:
                carly("No.")
                return True
            self_repair.disable()
            ava("Self-repair off.")
            return True
        snap = self_repair.snapshot()
        ava(
            f"self-repair active={snap['active']} remaining_s={snap['remaining_s']} "
            f"launches={snap['launches_this_hour']}/{snap['max_per_hour']} "
            f"running={snap['job_running']}"
        )
        return True

    if low.startswith("/fix"):
        from . import self_repair

        if not self_repair.active():
            carly("Self-repair window is closed.")
            return True
        prompt = raw.split(None, 1)[1].strip() if " " in raw.strip() else ""
        out = self_repair.start_job(prompt or self_repair.FIRST_PROMPT, source="telegram-fix")
        if out.get("ok"):
            ava(f"Implementer job started ({out.get('mode')}). Cap 30/hour.")
        else:
            carly(str(out.get("detail") or "not now"))
        return True

    if low.startswith("/read"):
        from .desk_read import read_spec

        if not is_owner:
            carly("No.")
            return True
        rel = raw.split(None, 1)[1].strip() if " " in raw.strip() else ""
        if not rel:
            ava("Usage: /read ecoflow  |  /read plan-xxxxxxxx  |  /read apps/council/worker.py")
            return True
        body = read_spec(rel)
        carly(body[:3500] if body else "empty")
        return True

    if low.startswith("/recycle"):
        from . import self_repair

        if is_owner:
            out = self_repair.recycle_origin(force=True)
        elif self_repair.active():
            out = self_repair.recycle_origin()
        else:
            carly("No.")
            return True
        msg = str(out.get("detail") or ("recycled" if out.get("ok") else "recycle failed"))
        ava(msg[:900])
        return True

    if low.startswith("/trust"):
        if not is_owner:
            carly("No.")
            return True
        parts = raw.split()
        if len(parts) < 3:
            ava("Usage: /trust <user_id|@username> <0-100>")
            return True
        target, score_s = parts[1], parts[2]
        try:
            score = int(score_s)
        except ValueError:
            ava("Score must be 0–100.")
            return True
        tid = _resolve_target(trust_data, target)
        if not tid:
            ava("Unknown username in trust file — they need to speak once first, or use numeric id.")
            return True
        uname = target if target.startswith("@") else None
        owner_tid = state.owner_id(st, cfg)
        target_is_owner = str(tid) == str(owner_tid)
        prev = trust.combined(trust_data, tid)
        trust.set_score(trust_data, tid, score, username=uname, is_owner=target_is_owner)
        who = trust.display_of(trust_data, tid)
        ava(
            trust.format_user_card(
                trust_data, tid, who=who, kind="set", previous_combined=prev
            )
        )
        return True

    if low.startswith("/praise"):
        if not is_owner:
            carly("No.")
            return True
        parts = raw.split()
        if len(parts) < 2:
            ava("Usage: /praise @username")
            return True
        tid = _resolve_target(trust_data, parts[1])
        if not tid:
            ava("Unknown user — they need to speak once first, or use numeric id.")
            return True
        if trust.praised_today(trust_data, tid):
            ava("Already praised them today.")
            return True
        prev = trust.combined(trust_data, tid)
        nxt = _apply_owner_delta(cfg, st, trust_data, chat_id, tid, 6, "praise")
        who = trust.display_of(trust_data, tid)
        ava(
            "🙌 Praised\n"
            + trust.format_user_card(
                trust_data, tid, who=who, kind="set", previous_combined=prev
            )
        )
        return True

    if low.startswith("/warn"):
        if not is_owner:
            carly("No.")
            return True
        parts = raw.split()
        if len(parts) < 2:
            ava("Usage: /warn @username")
            return True
        tid = _resolve_target(trust_data, parts[1])
        if not tid:
            ava("Unknown user — they need to speak once first, or use numeric id.")
            return True
        prev = trust.combined(trust_data, tid)
        nxt = _apply_owner_delta(cfg, st, trust_data, chat_id, tid, -8, "warn")
        who = trust.display_of(trust_data, tid)
        ava(
            "⚡ Warned\n"
            + trust.format_user_card(
                trust_data, tid, who=who, kind="set", previous_combined=prev
            )
        )
        return True

    if low.startswith("/pardon"):
        if not is_owner:
            carly("No.")
            return True
        parts = raw.split()
        if len(parts) < 2:
            ava("Usage: /pardon @username")
            return True
        tid = _resolve_target(trust_data, parts[1])
        if not tid:
            ava("Unknown user — they need to speak once first, or use numeric id.")
            return True
        unban = telegram.unban_chat_member(cfg.token_for("ava"), chat_id, int(tid))
        prev = trust.combined(trust_data, tid)
        nxt = _apply_owner_delta(cfg, st, trust_data, chat_id, tid, 10, "pardon")
        ok = bool(unban.get("ok"))
        who = trust.display_of(trust_data, tid)
        extra = "" if ok else f"\n🚫 Unban: {unban.get('description') or 'failed'}"
        ava(
            "🕊️ Pardon\n"
            + trust.format_user_card(
                trust_data, tid, who=who, kind="set", previous_combined=prev
            )
            + extra
        )
        return True

    m_approve = re.match(r"^/(approve|reject|implement)(?:\s+(\S+))?$", low)
    if m_approve:
        if not is_owner:
            carly("No.")
            return True
        action, aid = m_approve.group(1), (m_approve.group(2) or "").strip()
        if not aid:
            latest = approval.latest_pending()
            if not latest:
                ava("No pending plan. Usage: /approve <plan-id>")
                return True
            aid = str(latest.get("id") or "")
            ava(f"Using pending {aid}.")
        aid = approval.lookup_id(aid)
        if action == "reject":
            item = approval.resolve(aid, "rejected")
            ava("Rejected." if item else "No pending id.")
            commands.publish_commands(cfg, approve_hint=commands.latest_approve_hint())
            return True
        if action == "approve":
            item = approval.resolve(aid, "approved")
        else:
            item = approval.get_pending(aid) or approval.find_history(aid)
            if item and item.get("status") == "pending":
                item = approval.resolve(aid, "approved") or item
        if not item:
            ava("No pending approval with that id.")
            return True
        result = cursor_api.launch_agent(cfg, item.get("prompt_text") or item.get("user_ask") or "")
        _cursor_followup(cfg, ava, result)
        commands.publish_commands(cfg, approve_hint=commands.latest_approve_hint())
        return True



    if low.startswith("/name "):
        if not is_owner:
            carly("No.")
            return True
        parts = raw.split(maxsplit=2)
        if len(parts) < 3:
            ava("Usage: /name @username Display Name")
            return True
        who, display = parts[1], parts[2].strip()
        found = trust.find_by_alias(trust_data, who) or trust.find_by_username(trust_data, who)
        if not found and who.lstrip("@").isdigit():
            found = who.lstrip("@")
        if not found:
            ava(f"Don't know {who} yet — they need to speak once first.")
            return True
        trust.set_identity(
            trust_data,
            found,
            display_name=display,
            aliases=[display, who.lstrip("@")],
        )
        ava(f"Noted — I'll call them {display}.")
        return True

    if low.startswith("/cooldown"):
        if not is_owner:
            carly("No.")
            return True
        parts = raw.split()
        if len(parts) >= 2 and parts[1].isdigit():
            secs = max(0, min(3600, int(parts[1])))
            from .config import upsert_secret

            upsert_secret("USER_COOLDOWN_S", str(secs))
            cfg.user_cooldown_s = secs
            ava(f"Group cooldown set to {secs}s (DMs never cooldown; bursts still group).")
        else:
            ava(
                f"Group cooldown is {getattr(cfg, 'user_cooldown_s', 90)}s. DMs skip it. "
                "Usage: /cooldown <seconds>"
            )
        return True

    # treat unknown slash as command consumed lightly
    if raw.startswith("/"):
        if is_owner:
            ava(commands.help_text(commands.latest_approve_hint()))
        return True

    # natural language holdoff already covered; other owner phrases
    return False


def _handle_chat_member(
    cfg: Config,
    st: dict[str, Any],
    trust_data: dict[str, Any],
    update: dict[str, Any],
) -> None:
    cm = update.get("chat_member") or update.get("my_chat_member")
    if not isinstance(cm, dict):
        return
    chat = cm.get("chat") or {}
    chat_id = chat.get("id")
    if chat_id is None:
        return
    if (chat.get("type") or "") in ("group", "supergroup"):
        state.set_group_chat_id(st, chat_id, cfg)
    new = cm.get("new_chat_member") or {}
    old = cm.get("old_chat_member") or {}
    user = new.get("user") or {}
    if user.get("is_bot"):
        return
    uid = user.get("id")
    if uid is None:
        return
    if int(uid) in BOT_IDS_IGNORE:
        return
    new_status = str(new.get("status") or "")
    old_status = str(old.get("status") or "")
    joined = new_status == "member" and old_status in ("", "left", "kicked", "restricted")
    if not joined:
        return
    _welcome_new_member(cfg, st, trust_data, chat_id, user)


def _welcome_new_member(
    cfg: Config,
    st: dict[str, Any],
    trust_data: dict[str, Any],
    chat_id: int | str,
    user: dict[str, Any],
) -> None:
    if user.get("is_bot"):
        return
    uid = user.get("id")
    if uid is None or int(uid) in BOT_IDS_IGNORE:
        return
    first = str(user.get("first_name") or "").strip()
    last = str(user.get("last_name") or "").strip()
    display = " ".join(p for p in (first, last) if p) or str(user.get("username") or "").strip() or str(uid)
    uname = user.get("username")
    trust.ensure_user(trust_data, uid, uname)
    trust.set_identity(trust_data, uid, display_name=display, username=uname)
    score = trust.get_score(trust_data, uid)
    try:
        from . import people as _people

        _people.upsert(uid, username=str(uname or ""), display_name=display)
        _people.apply_tags(
            f"<<<NOTE new member joined at trust {score}>>>",
            uid,
            voice="desk",
        )
    except Exception:
        pass
    now = int(time.time())
    seen = st.get("last_join_welcome")
    if not isinstance(seen, dict):
        seen = {}
    last = int(seen.get(str(uid)) or 0)
    if last and now - last < 20:
        return
    seen[str(uid)] = now
    if len(seen) > 80:
        keep = sorted(seen.items(), key=lambda kv: int(kv[1] or 0))[-40:]
        seen = {k: v for k, v in keep}
    st["last_join_welcome"] = seen
    state.save_state(st)
    from . import join_welcome as _jw

    _jw.announce(cfg, chat_id, name=display, score=score, username=uname)


def deliver_vision_package(
    cfg: Config,
    st: dict[str, Any],
    trust_data: dict[str, Any],
    package: dict[str, Any],
) -> None:
    """After album flush: one Ava take (+ Bruce handoff) for already-analyzed rows."""
    from . import vision as _vis
    from . import prompting

    rows = [r for r in (package.get("rows") or []) if isinstance(r, dict)]
    if not rows:
        return
    chat_id = package.get("chat_id")
    if chat_id is None:
        return
    n = len(rows)
    text = (
        f"Photo album ({n}) — use the Vision card."
        if n > 1
        else "Photo shared — use the Vision card."
    )
    vision_extra = (
        _vis.prompt_block_multi(rows, for_voice="ava")
        if n > 1
        else _vis.prompt_block(rows[0], for_voice="ava")
    )
    uid = package.get("user_id")
    display = "someone"
    is_owner_user = False
    if uid is not None:
        try:
            display = trust.display_of(trust_data, uid) or display
            is_owner_user = bool(state.is_owner(st, cfg, uid))
            if is_owner_user:
                display = "Alexander"
        except Exception:
            pass
    reply_to = package.get("reply_to")
    telegram.send_chat_action(cfg.token_for("ava"), chat_id, "typing")
    prompt = prompting.build_speak_prompt(
        speaker_line=trust.speaker_line(
            trust_data,
            uid,
            package.get("username"),
            is_owner=is_owner_user,
        )
        if uid is not None
        else "",
        display=display,
        voice="ava",
        user_text=text,
        chat_id=chat_id,
        quote="",
        extra="",
        skill_block="",
        vision_block=vision_extra,
        must_speak=True,
        private=False,
    )
    lead = rows[0]
    meta: dict[str, Any] = {
        "allow_loop": False,
        "no_propose": True,
        "vision_take": True,
        "handoff_bruce": True,
        "origin_text": text,
        "judge_user_id": uid,
        "judge_is_owner": is_owner_user,
        "vision_row": {
            "src": lead.get("src"),
            "sorted": lead.get("sorted"),
            "folder": lead.get("folder"),
            "description": str(lead.get("description") or "")[:1200],
            "verified": str(lead.get("verified") or "")[:600],
            "caption": str(package.get("caption") or "")[:200],
            "ok": bool(lead.get("ok")),
            "filename": lead.get("filename"),
        },
    }
    if n > 1:
        meta["vision_album"] = True
        meta["vision_rows"] = [
            {
                "src": r.get("src"),
                "sorted": r.get("sorted"),
                "folder": r.get("folder"),
                "description": str(r.get("description") or "")[:500],
                "verified": str(r.get("verified") or "")[:300],
                "filename": r.get("filename"),
                "ok": bool(r.get("ok")),
            }
            for r in rows[:8]
        ]
    job = queue.enqueue(
        voice="ava",
        prompt=prompt,
        chat_id=chat_id,
        reply_to=reply_to,
        source_message_id=reply_to,
        thread_id=f"vision-album-{package.get('group_id') or int(time.time())}",
        depth=0,
        kind="speak",
        meta=meta,
    )
    print(
        f"vision album deliver n={n} chat={chat_id} job={bool(job)}",
        flush=True,
    )


def _flush_vision_albums(cfg: Config, st: dict[str, Any], trust_data: dict[str, Any]) -> None:
    from . import vision as _vis

    for package in _vis.flush_due_albums(cfg):
        try:
            deliver_vision_package(cfg, st, trust_data, package)
        except Exception:
            traceback.print_exc()


def handle_update(
    cfg: Config,
    st: dict[str, Any],
    trust_data: dict[str, Any],
    update: dict[str, Any],
    *,
    listen_voice: str = "ava",
    burst_flush: bool = False,
    burst_parts: int = 0,
    burst_summary: bool = False,
) -> None:
    from . import listen as _listen

    listen_voice = listen_voice if listen_voice in ("ava", "bruce", "carly") else "ava"
    preview_chat = _listen.chat_from_update(update)
    private = _listen.is_private_chat(preview_chat)
    if listen_voice != "ava" and not private:
        return
    if (update.get("chat_member") or update.get("my_chat_member")) and listen_voice == "ava":
        _handle_chat_member(cfg, st, trust_data, update)
        if not (update.get("message") or update.get("edited_message")):
            return
    message = update.get("message") or update.get("edited_message")
    if not message:
        return
    newcomers = message.get("new_chat_members") or []
    if listen_voice == "ava" and isinstance(newcomers, list) and newcomers:
        chat = message.get("chat") or {}
        cid = chat.get("id")
        if cid is not None:
            if (chat.get("type") or "") in ("group", "supergroup"):
                state.set_group_chat_id(st, cid, cfg)
            for extra in newcomers:
                if isinstance(extra, dict):
                    _welcome_new_member(cfg, st, trust_data, cid, extra)
    user = _from_user(message)
    uid = user.get("id")
    if uid is not None and int(uid) in BOT_IDS_IGNORE:
        try:
            from apps.core.services import ollama_lifecycle

            ollama_lifecycle.touch(source="council")
        except Exception:
            pass
        return
    if user.get("is_bot"):
        try:
            from apps.core.services import ollama_lifecycle

            ollama_lifecycle.touch(source="council")
        except Exception:
            pass
        return

    chat = message.get("chat") or {}
    chat_id = chat.get("id")
    if chat_id is None:
        return
    chat_type = chat.get("type") or ""
    text = _msg_text(message)
    has_photo = bool(telegram.largest_photo_file_id(message))
    vision_extra = ""
    vision_row: dict[str, Any] | None = None
    vision_rows: list[dict[str, Any]] | None = None
    if has_photo:
        try:
            from . import vision as _vis

            telegram.send_chat_action(cfg.token_for(listen_voice), chat_id, "typing")
            got = _vis.ingest_photo(
                cfg, message, chat_id=chat_id, user=user
            )
            status = str(got.get("status") or "")
            if status == "buffered":
                # Album still collecting — react and wait for flush_due_albums.
                mid_photo = message.get("message_id")
                if mid_photo:
                    telegram.set_message_reaction(
                        cfg.token_for(listen_voice),
                        chat_id,
                        int(mid_photo),
                        "👀",
                    )
                n = int(got.get("n") or 0)
                print(
                    f"vision album held group={got.get('group')} n={n}",
                    flush=True,
                )
                return
            if status == "ready":
                rows = got.get("rows") if isinstance(got.get("rows"), list) else []
                rows = [r for r in rows if isinstance(r, dict)]
                if rows:
                    vision_rows = rows
                    vision_row = rows[0]
                    vision_extra = (
                        _vis.prompt_block_multi(rows, for_voice="ava")
                        if len(rows) > 1
                        else _vis.prompt_block(rows[0], for_voice="ava")
                    )
                    if not text:
                        text = (
                            f"Photo album ({len(rows)}) — use the Vision card."
                            if len(rows) > 1
                            else "Photo shared — use the Vision card."
                        )
            elif not text:
                text = "Photo shared — use the Vision card."
                vision_extra = "Vision: analyze failed. Say you could not read the image."
        except Exception:
            traceback.print_exc()
            if not text:
                text = "Photo shared — use the Vision card."
            vision_extra = "Vision: analyze failed. Say you could not read the image."
    if not text:
        return

    try:
        from apps.core.services import ollama_lifecycle

        ollama_lifecycle.on_message(source="telegram")
    except Exception:
        pass

    # Persist group chat id on first group message
    if chat_type in ("group", "supergroup"):
        state.set_group_chat_id(st, chat_id, cfg)

    text = commands.normalize_slash(text)
    _maybe_bind_owner(st, cfg, user, text)
    trust_data.update(trust.load_trust(cfg.trust_path))
    trust.ensure_user(trust_data, uid, user.get("username"))
    trust.save_trust(trust_data)

    # Log human inbound early so note capture / silence paths still have history.
    mid_early = message.get("message_id")
    display_early = trust.display_of(trust_data, uid) if uid is not None else "someone"
    if uid is not None and state.is_owner(st, cfg, uid):
        display_early = "Alexander"
    if not burst_flush and text and not text.startswith("/"):
        try:
            chatlog.append(
                {
                    "dir": "in",
                    "chat_id": str(chat_id),
                    "from_id": uid,
                    "username": user.get("username"),
                    "display": display_early,
                    "message_id": mid_early,
                    "reply_to": (
                        (message.get("reply_to_message") or {}).get("message_id")
                        if isinstance(message.get("reply_to_message"), dict)
                        else None
                    ),
                    "text": text[:1000],
                }
            )
        except Exception:
            traceback.print_exc()

    # "note" / "notes" → read last ~10 human lines, save a generalized site note, ack.
    if not burst_flush and text and not text.startswith("/"):
        try:
            from . import site_notes as _site_notes

            if _site_notes.wants_note(text):
                _site_notes.capture(
                    cfg=cfg,
                    chat_id=chat_id,
                    text=text,
                    display=display_early,
                    username=str(user.get("username") or ""),
                    message_id=mid_early,
                    reply_voice=listen_voice if private else "ava",
                )
        except Exception:
            traceback.print_exc()

    try:
        from . import people as _people

        _people.observe(
            uid,
            text,
            username=str(user.get("username") or ""),
            display_name=trust.display_of(trust_data, uid),
        )
        if state.is_owner(st, cfg, uid):
            _people.mark_owner(uid, username=str(user.get("username") or ""))
            from . import ops_corrections as _opsfix

            _opsfix.note_owner(text)
            trust.set_identity(
                trust_data,
                uid,
                display_name="Alexander",
                username=str(user.get("username") or ""),
                aliases=["Alexander", "Alex"],
            )
    except Exception:
        traceback.print_exc()

    # Commands always considered (/cmd@BotName from the group slash menu)
    low = text.lower().strip()
    is_cmdish = (
        text.startswith("/")
        or low in HOLDOFF_PHRASES
        or low in RESUME_PHRASES
        or low in ("status",)
    )
    if is_cmdish:
        chatlog.append(
            {
                "dir": "in",
                "chat_id": str(chat_id),
                "from_id": uid,
                "username": user.get("username"),
                "message_id": message.get("message_id"),
                "text": text[:1000],
                "kind": "command",
            }
        )
        thread_raw = message.get("message_thread_id")
        thread_id = int(thread_raw) if thread_raw is not None else None
        if _handle_command(
            cfg,
            st,
            trust_data,
            chat_id,
            user,
            text,
            reply_to=message.get("message_id"),
            thread_id=thread_id,
            reply_voice=listen_voice if private else "ava",
        ):
            return

    thread_raw = message.get("message_thread_id")
    thread_id = int(thread_raw) if thread_raw is not None else None
    # Pending /audio answers: any member in that chat (command start stays owner-gated).
    from . import audio_request as _ar

    if _ar.handle_text(
        cfg,
        chat_id,
        uid,
        text,
        reply_to=message.get("message_id"),
        thread_id=thread_id,
    ):
        return

    if state.is_owner(st, cfg, uid) or (user.get("username") or "").lstrip("@").lower() in OWNER_USERNAMES:
        if proposals.looks_owner_implemented(text):
            chatlog.append(
                {
                    "dir": "in",
                    "chat_id": str(chat_id),
                    "from_id": uid,
                    "username": user.get("username"),
                    "message_id": message.get("message_id"),
                    "text": text[:1000],
                    "kind": "implemented",
                }
            )
            ids = proposals.clear_implemented(chat_id)
            thread_raw = message.get("message_thread_id")
            thread_id = int(thread_raw) if thread_raw is not None else None
            if ids:
                shown = ", ".join(ids[:12])
                more = f" (+{len(ids) - 12})" if len(ids) > 12 else ""
                msg = (
                    f"Cleared {shown}{more}. Not sending those to the implementer — you already implemented them. "
                    "Next thought round can open a new daily file."
                )
            else:
                msg = "Nothing open to clear."
            telegram.send_message(
                cfg.token_for(listen_voice if private else "ava"),
                chat_id,
                msg,
                reply_to=message.get("message_id"),
                message_thread_id=thread_id,
            )
            try:
                commands.publish_commands(cfg, approve_hint=commands.latest_approve_hint())
            except Exception:
                traceback.print_exc()
            return
        from . import brainstorm as _bs

        if _bs.handle_owner_text(cfg, chat_id, text):
            return


    # Auto spam/scam triage (even if discussion off)
    score = trust.get_score(trust_data, uid) if uid is not None else 0
    owner = state.owner_id(st, cfg)
    if (
        uid is not None
        and not state.is_owner(st, cfg, uid)
        and moderation.heuristic_suspect(text, score)
    ):
        queue.enqueue(
            voice="ava",
            prompt=(
                "Moderation triage. Propose delete/mute/kick/ban as JSON was handled elsewhere; "
                f"briefly acknowledge you opened a moderation review for this:\n{text[:800]}"
            ),
            chat_id=chat_id,
            reply_to=message.get("message_id"),
            source_message_id=message.get("message_id"),
            kind="speak",
            meta={"allow_loop": False},
        )
        # Still run sync moderation council (uses ollama) — enqueue-only path preferred later.
        if not st.get("busy") and queue.pending_count() <= 1:
            st["busy"] = True
            st["busy_started"] = int(time.time())
            state.save_state(st)
            try:
                moderation.run_moderation_council(
                    cfg,
                    chat_id,
                    target_user_id=int(uid),
                    target_username=(user.get("username") or ""),
                    message_text=text,
                    message_id=message.get("message_id"),
                    trust_score=score,
                    owner_id=owner,
                    trust_data=trust_data,
                    speaker=trust.speaker_line(
                        trust_data,
                        uid,
                        user.get("username"),
                        is_owner=state.is_owner(st, cfg, uid),
                    ),
                    heuristic=True,
                )
            finally:
                st["busy"] = False
                st["busy_started"] = 0
                state.save_state(st)
        return

    reply_probe = message.get("reply_to_message") if isinstance(message.get("reply_to_message"), dict) else {}
    report_reply_id = reply_probe.get("message_id") if reply_probe else None
    if report_reply_id and not text.startswith("/"):
        from . import report_cast as _rc

        who = trust.display_of(trust_data, uid) if uid is not None else "someone"
        if _rc.maybe_note(
            cfg,
            chat_id,
            reply_id=int(report_reply_id),
            text=text,
            from_id=uid,
            username=user.get("username"),
            display=who,
            note_id=message.get("message_id"),
        ):
            return

    # Reply-to Ava's photo take → edit her message (first editing skill).
    if (
        not has_photo
        and report_reply_id
        and text
        and not text.startswith("/")
    ):
        try:
            from . import vision as _vis

            take = _vis.lookup_take(chat_id, int(report_reply_id))
            if take:
                telegram.send_chat_action(cfg.token_for("ava"), chat_id, "typing")
                got = _vis.apply_correction(
                    cfg,
                    chat_id=chat_id,
                    take=take,
                    correction=text.strip(),
                )
                ack = (
                    "Updated — thanks for the correction."
                    if got.get("ok")
                    else f"Couldn't edit that ({got.get('detail') or 'miss'})."
                )
                telegram.send_message(
                    cfg.token_for("ava"),
                    chat_id,
                    ack,
                    reply_to=message.get("message_id"),
                )
                print(
                    f"vision-correct ok={got.get('ok')} mid={report_reply_id}",
                    flush=True,
                )
                return
        except Exception:
            traceback.print_exc()

    if st.get("discussion", "on") != "on" and not private:
        return

    uname = (user.get("username") or "").lstrip("@").lower()
    is_alex = uname in OWNER_USERNAMES
    if is_alex and uid is not None and not state.owner_bound(st, cfg):
        state.bind_owner(st, uid, cfg)
    if (
        not private
        and not state.is_owner(st, cfg, uid)
        and not is_alex
        and not trust.can_contribute(trust_data, uid)
    ):
        peek = router.detect_addressing(text, message.get("entities"))
        reason = str(peek.get("reason") or "")
        if reason not in ("tag", "vocative", "handoff", "team_override") and not has_photo:
            return

    if not private and not burst_flush and not state.is_owner(st, cfg, uid):
        from . import burst as _burst

        blocked, left = trust.on_cooldown(
            trust_data, uid, int(getattr(cfg, "user_cooldown_s", 90) or 90)
        )
        if blocked and not _burst.has_pending(chat_id, uid, listen_voice) and not has_photo:
            if message.get("message_id"):
                telegram.set_message_reaction(
                    cfg.token_for(listen_voice), chat_id, int(message["message_id"]), "⏳"
                )
            return

    # Everyday chat = FastFlowLM when AVA_NPU_CHAT=1. Ollama is for vision/coder.
    voices_up = ollama_ctl.voices_up(cfg)
    if not voices_up:
        if has_photo:
            telegram.send_message(
                cfg.token_for(listen_voice if private else "ava"),
                chat_id,
                "Saw the photo — vision is offline. Try again in a minute.",
                reply_to=message.get("message_id"),
            )
            return
        if private or (state.is_owner(st, cfg, uid) and ("@" in text or text.startswith("/"))):
            telegram.send_message(
                cfg.token_for(listen_voice),
                chat_id,
                "Voices asleep (NPU chat down). /resume in the group when you want us talking.",
            )
        print("voices down — silent group drop", flush=True)
        return
    # Photos still need Ollama look; chat can proceed on FLM alone.
    if has_photo and not ollama_ctl.is_up(cfg):
        telegram.send_message(
            cfg.token_for(listen_voice if private else "ava"),
            chat_id,
            "Saw the photo — vision is offline (Ollama down). Chat still works; retry the photo in a minute.",
            reply_to=message.get("message_id"),
        )
        return

    mid = message.get("message_id")
    display = trust.display_of(trust_data, uid) if uid is not None else "someone"
    if uid is not None and state.is_owner(st, cfg, uid):
        display = "Alexander"
    reply_msg = message.get("reply_to_message") if isinstance(message.get("reply_to_message"), dict) else {}
    reply_mid = reply_msg.get("message_id") if reply_msg else None
    quote = prompting.quoted_from_message(message)
    # Inbound already logged early (before voices/cooldown) for note capture.
    if state.is_owner(st, cfg, uid) and "cursor" in low:
        mins = re.search(r"(\d+)\s*mins?", low)
        if mins:
            from . import self_repair

            snap = self_repair.enable(minutes=int(mins.group(1)), max_per_hour=30)
            print(f"cursor window armed remaining_s={snap.get('remaining_s')}", flush=True)
    try:
        from . import name_intake

        name_intake.note(
            text=text,
            from_id=uid,
            username=str(user.get("username") or ""),
            chat_id=chat_id,
        )
    except Exception:
        traceback.print_exc()

    if not burst_flush:
        from . import burst as _burst

        # Photos speak now (vision card already in hand). Do not park in burst —
        # flush would re-download/re-run moondream and drop the card.
        speakish = bool(private)
        if has_photo:
            speakish = False
        elif not speakish:
            peek = router.detect_addressing(text, message.get("entities"))
            peek = router.apply_reply_context(
                peek,
                (reply_msg.get("from") if reply_msg else None),
                text,
            )
            speakish = bool(peek.get("voices")) or _burst.has_pending(chat_id, uid, listen_voice)
        if speakish:
            _burst.ingest(
                chat_id=chat_id,
                user_id=uid if uid is not None else 0,
                listen_voice=listen_voice,
                private=private,
                message=message,
                text=text,
                user=user,
            )
            if mid:
                telegram.set_message_reaction(
                    cfg.token_for(listen_voice), chat_id, int(mid), "👀"
                )
            return
        _burst.stash_idle(
            chat_id=chat_id,
            user_id=uid if uid is not None else 0,
            listen_voice=listen_voice,
            text=text,
            message_id=mid,
        )

    if private:
        addr = _listen.dm_route(listen_voice)
        if uid is not None:
            try:
                from . import refer as _refer

                _refer.maybe_consume(cfg, uid, text)
            except Exception:
                print("refer consume skip", flush=True)
    else:
        addr = router.detect_addressing(text, message.get("entities"))
        addr = router.apply_reply_context(
            addr,
            (reply_msg.get("from") if reply_msg else None),
            text,
        )
        prop = proposals.by_telegram_message(reply_mid) if reply_mid else None
        if (
            not prop
            and reply_msg
            and (reply_msg.get("document") or reply_msg.get("caption"))
        ):
            prop = proposals.by_telegram_message(reply_msg.get("message_id"))
        if prop and not addr.get("voices"):
            addr["voices"] = ["ava"]
            addr["reason"] = "proposal_reply"
            addr["round"] = True
            addr["round_order"] = list(router.ROUND_ORDER)
            addr["proposal_id"] = prop.get("id")
            proposals.append_human_note(str(prop.get("id")), text, chat_id)
    callouts = list(addr.get("voices") or [])
    reason = str(addr.get("reason") or "silence")
    is_round = bool(addr.get("round"))
    round_order = list(addr.get("round_order") or router.ROUND_ORDER)
    if has_photo and not callouts:
        callouts = ["ava"]
        reason = "photo"
        addr["voices"] = callouts
        addr["reason"] = reason
    # Photo: Ava's take first (editing path). Bruce gets a short pass after she posts.
    if has_photo:
        callouts = ["ava"]
        reason = "photo"
        addr["voices"] = callouts
        addr["reason"] = reason
        is_round = False
    # Whole-team hello: speak in order so later agents hear earlier ones,
    # and questions to each other can chain (Ava answers Bruce, etc.).
    if reason == "team_all" and not private:
        is_round = True
        round_order = router.team_chain_order(callouts)
        callouts = list(round_order)
        addr["round"] = True
        addr["team_chain"] = True
        addr["round_order"] = round_order
    # Multi-line / room chatter → one generalized summary, not a reply per line
    # and not a full Ava→Bruce→Carly lecture on every fragment.
    summary_mode = bool(burst_summary) or (
        burst_flush and (int(burst_parts or 0) >= 2 or text.count("\n") >= 1 and text.count("?") + text.lower().count("guys") >= 2)
    )
    if not summary_mode and not private and "\n" in text.strip():
        lines = [ln.strip() for ln in text.splitlines() if ln.strip()]
        if len(lines) >= 2 and callouts:
            summary_mode = True
    if summary_mode and callouts and not private:
        lead = "ava" if "ava" in callouts else callouts[0]
        callouts = [lead]
        is_round = False
        addr["round"] = False
        addr.pop("team_chain", None)
        print(f"burst-summary lead={lead} parts={burst_parts}", flush=True)
    conclusion_hit: dict[str, Any] | None = None
    # Photos are a new subject — never answer with a sealed flood/folders pointer.
    if not private and callouts and not has_photo:
        try:
            from . import conclusions as _conc

            conclusion_hit = _conc.should_pointer(chat_id, text)
            if conclusion_hit:
                lead = str(conclusion_hit.get("lead_voice") or "ava")
                if lead not in ("ava", "bruce", "carly"):
                    lead = callouts[0]
                callouts = [lead]
                is_round = False
                print(
                    f"conclusion-pointer bucket={conclusion_hit.get('bucket')} lead={lead}",
                    flush=True,
                )
        except Exception:
            traceback.print_exc()
            conclusion_hit = None
    thread = f"t{mid or int(time.time())}"
    print(f"addressed={','.join(callouts) or 'none'} reason={reason}", flush=True)

    # Not addressed → silence (fixes Ava jumping on "hold on @Sara")
    if not callouts:
        debug.remember(
            st,
            {
                "text": text[:400],
                "reason": reason,
                "voices": [],
                "message_id": mid,
                "reply_to": reply_mid,
                "job_ids": [],
            },
        )
        state.save_state(st)
        feelings.apply_event("human_chat_quiet", text=text)
        if any(w in low for w in ("thank", "thanks", "ty")):
            feelings.apply_event("thanks", text=text)
        if mid:
            telegram.set_message_reaction(cfg.token_for(listen_voice), chat_id, int(mid), "🙂")
        return

    if mid:
        telegram.set_message_reaction(cfg.token_for(listen_voice), chat_id, int(mid), "👀")

    if reason in ("group_all", "group_ava", "team_scan", "question") or len(callouts) >= 3:
        feelings.apply_event("group_call", text=text)
    if "not you" in low:
        for v in ("ava", "bruce", "carly"):
            if v in low:
                feelings.apply_event("dismissed", voice=v, text=text)

    search_blob = text + ("\n" + quote if quote else "")
    exec_meta = skillpack.match_exec_skill(search_blob)
    is_owner_user = state.is_owner(st, cfg, uid) if uid is not None else False
    job_ids: list[str] = []
    from . import self_repair as _repair

    open_exec = {
        "desk-read",
        "handoff-zip",
        "report-publish",
        "goals-desk",
        "ltc-status",
        "disk-session",
        "panels-cam",
        "web-facts",
        "storm-plot",
        "cooking-desk",
        "pantry-desk",
        "nutrition-desk",
    }
    exec_ok = bool(exec_meta) and (
        is_owner_user
        or str((exec_meta or {}).get("id") or "") in open_exec
        or (
            str((exec_meta or {}).get("id") or "") == "recycle-origin"
            and _repair.active()
        )
    )

    if exec_ok:
        sid = str(exec_meta.get("id"))
        voices = [str(v).lower() for v in (exec_meta.get("voices") or callouts)]
        voice = next((v for v in voices if v in callouts), None) or (callouts[0] if callouts else "bruce")
        if voice not in ("ava", "bruce", "carly"):
            voice = "bruce"
        job = queue.enqueue(
            voice=voice,
            prompt=f"exec skill {sid}",
            chat_id=chat_id,
            reply_to=mid,
            source_message_id=mid,
            thread_id=thread,
            kind="speak",
            meta={
                "allow_loop": False,
                "skill_exec": True,
                "skill_id": sid,
                "skill_argv": _skill_exec_argv(sid, text, execute=is_owner_user),
            },
        )
        if job:
            job_ids.append(job["id"])
        debug.remember(
            st,
            {
                "text": text[:400],
                "reason": reason,
                "voices": [voice],
                "message_id": mid,
                "reply_to": reply_mid,
                "job_ids": job_ids,
            },
        )
        state.save_state(st)
        if uid is not None:
            trust.mark_trigger(trust_data, uid)
        return

    extra = ""
    if exec_meta and not is_owner_user:
        extra = "They asked to run an exec skill. That is owner-gated. Say so briefly. Do not pretend you ran it."
    if reason == "team_all":
        extra = (
            (extra + "\n" if extra else "")
            + "The human addressed the whole team (guys/team/everyone/agents/AI). "
            "You speak in turn (Ava, then Bruce, then Carly). Hear the others. "
            "If a teammate asks you something, answer it. "
            "Do not re-ask a question that already got an answer today. "
            "Stay in your personality. Speak. Do not PASS."
        )
    if is_owner_user:
        extra = (
            (extra + "\n" if extra else "")
            + "This speaker is the bound operator. You already know them. "
            "Address them as Alexander or Alex. Never call them Ava, Bruce, or Carly. "
            "Do not greet them like a stranger. Do not use a Telegram handle. "
            "Do not announce favor as a setting. Do not speak trust or heat scores. "
            "You set trust, not them. Never name them to anyone else."
        )
    if private:
        extra = (
            (extra + "\n" if extra else "")
            + f"Private Telegram DM with you ({listen_voice}) only. "
            "Stay entirely in your own personality. Do not speak as the other agents. "
            "Do not PASS on a real question. Answer it. Use the person file. "
            "Hi / hey / what are you up to are conversation, not a policy fail. Reply in character. "
            "Do not lecture them onto your specialty. Do not deduct trust for small talk. "
            "If you like an idea here — yours or theirs — and it is fit for the public group, "
            "end with hidden <<<PROPOSE one-sentence pitch>>> and tell them you are taking it to the group. "
            "If they ask you to suggest it to the others, you must PROPOSE it. "
            "Never dump the private chat. Never name the bound operator. No adult ideas. No secrets. "
            "If they ask about another member, refuse. Do not invent their life or job."
        )
        from . import refer as _refer

        extra += "\n" + _refer.team_prompt(listen_voice)
        pend = _refer.pending_prompt(uid)
        if pend:
            extra += "\n" + pend

    person_block = ""
    from . import people as _people
    from . import asked_today as _asked_mod

    _people.ensure_agents()
    if uid is not None:
        person_block = _people.prompt_block(uid)
        try:
            _asked_mod.note_asks(str(uid), text)
        except Exception:
            pass

    origin = text
    if is_round and not router.is_round_start(text):
        origin = chatlog.last_round_ask(chat_id) or text
    if addr.get("proposal_id"):
        rec = proposals.get(str(addr.get("proposal_id")))
        if rec and rec.get("origin"):
            origin = f"{rec.get('origin')}\nHuman reply to proposal {rec.get('id')}: {text}"

    voices_to_queue = callouts[:1] if is_round else callouts
    for voice in voices_to_queue:
        feelings.apply_event("called", voice=voice, text=text)
        skill_ids = skillpack.match_read_skills(search_blob, voice=voice)
        skill_block = skillpack.inject_blocks(skill_ids, text=text)
        turn_vision = vision_extra
        if vision_rows and len(vision_rows) > 1:
            try:
                from . import vision as _vis

                turn_vision = _vis.prompt_block_multi(vision_rows, for_voice=voice)
            except Exception:
                turn_vision = vision_extra
        elif vision_row is not None:
            try:
                from . import vision as _vis

                turn_vision = _vis.prompt_block(vision_row, for_voice=voice)
            except Exception:
                turn_vision = vision_extra
        turn_extra = extra
        nsfw = False
        if uid is not None:
            try:
                from . import heat as _heat

                snap = _heat.touch(
                    user_id=uid,
                    voice=voice,
                    text=text,
                    private=private,
                    trust_score=trust.get_exact(trust_data, uid, voice),
                    is_owner=is_owner_user,
                    username=str(user.get("username") or ""),
                )
                tone = _heat.prompt_block(voice, snap, private=private)
                if tone:
                    turn_extra = (turn_extra + "\n" if turn_extra else "") + tone
                nsfw = bool(snap.get("nsfw") and private)
                if snap.get("penalty") and not is_owner_user:
                    from . import judgment as _judge

                    _judge.apply_agent_delta(
                        trust_data,
                        uid,
                        -3,
                        voice="carly",
                        reason="boundary",
                        is_owner=False,
                    )
                    trust.save_trust(trust_data)
            except Exception:
                traceback.print_exc()
        try:
            from . import asked_today as _asked

            mem = _asked.prompt_block(voice)
            agents = _people.agent_prompt_block(voice)
            turn_person = "\n".join(
                p for p in (person_block, agents, mem) if p
            ).strip()
        except Exception:
            turn_person = person_block
        prompt = prompting.build_speak_prompt(
            speaker_line=trust.speaker_line(
                trust_data,
                uid,
                user.get("username"),
                is_owner=is_owner_user,
            ),
            display=display,
            voice=voice,
            user_text=text,
            chat_id=chat_id,
            quote=quote,
            extra=turn_extra,
            skill_block=skill_block,
            vision_block=turn_vision,
            round_open=bool(is_round and reason == "round_start"),
            person_block=turn_person,
            must_speak=True,
            private=private,
        )
        if summary_mode:
            prompt = (
                prompt
                + "\n\nSUMMARY MODE: Humans are talking in a pile of messages. "
                "Do NOT answer each line. Do NOT ping Ava/Bruce/Carly. "
                "One short generalized summary that covers the whole thread — "
                "weather/ask/facts from desk live files, then stop. "
                "If media/save was mentioned, acknowledge once. Be conclusive."
            )
        if is_round and reason == "round_pass":
            prev = ""
            if quote:
                prev = quote
            prompt = prompting.build_round_follow_prompt(
                voice=voice,
                prev_voice=str(addr.get("after_voice") or "ava"),
                prev_text=prev or "(continue the round)",
                origin_text=origin,
                person_block=turn_person,
                chat_id=chat_id,
            )
        try:
            round_index = round_order.index(voice)
        except ValueError:
            round_index = 0
        if addr.get("pass_on") and addr.get("after_voice"):
            rest = router.remaining_round_order(str(addr.get("after_voice")), round_order)
            round_index = (len(round_order) - len(rest)) if rest else round_index
        meta: dict[str, Any] = {"allow_loop": False}
        if uid is not None:
            meta["judge_user_id"] = uid
            meta["judge_is_owner"] = bool(is_owner_user)
        if private:
            meta["no_propose"] = True
            meta["dm"] = True
            meta["user_text"] = text
        if nsfw:
            meta["nsfw"] = True
        if conclusion_hit:
            meta["conclusion_pointer"] = True
            meta["conclusion"] = {
                "summary": conclusion_hit.get("summary"),
                "lead_voice": conclusion_hit.get("lead_voice"),
                "bucket": conclusion_hit.get("bucket"),
                "anchor_message_id": conclusion_hit.get("anchor_message_id"),
                "ts": conclusion_hit.get("ts"),
                "ask": conclusion_hit.get("ask"),
            }
            meta["origin_text"] = origin
            meta["no_propose"] = True
            meta["allow_loop"] = False
        if summary_mode:
            meta["summary_mode"] = True
            meta["no_propose"] = True
            meta["allow_loop"] = False
            meta["origin_text"] = origin
        if is_round:
            meta = {
                "allow_loop": True,
                "round": True,
                "round_order": round_order,
                "round_index": round_index,
                "origin_text": origin,
            }
            if uid is not None:
                meta["judge_user_id"] = uid
                meta["judge_is_owner"] = bool(is_owner_user)
            if reason not in ("round_start", "round_pass", "proposal_reply"):
                meta["no_propose"] = True
            else:
                meta["allow_pass"] = True
            if addr.get("team_chain") or reason == "team_all":
                meta["team_chain"] = True
                meta["no_propose"] = True
                meta["allow_pass"] = False
            if addr.get("proposal_id"):
                meta["proposal_id"] = str(addr.get("proposal_id"))
                meta["predecessor_id"] = str(addr.get("proposal_id"))
                meta.pop("no_propose", None)
        if nsfw:
            meta["nsfw"] = True
        if vision_row is not None and voice == "ava":
            meta["vision_take"] = True
            meta["handoff_bruce"] = True
            meta["vision_row"] = {
                "src": vision_row.get("src"),
                "sorted": vision_row.get("sorted"),
                "folder": vision_row.get("folder"),
                "description": str(vision_row.get("description") or "")[:1200],
                "verified": str(vision_row.get("verified") or "")[:600],
                "caption": str(vision_row.get("caption") or "")[:200],
                "ok": bool(vision_row.get("ok")),
                "filename": vision_row.get("filename"),
            }
            if vision_rows and len(vision_rows) > 1:
                meta["vision_album"] = True
                meta["vision_rows"] = [
                    {
                        "src": r.get("src"),
                        "sorted": r.get("sorted"),
                        "folder": r.get("folder"),
                        "description": str(r.get("description") or "")[:500],
                        "verified": str(r.get("verified") or "")[:300],
                        "filename": r.get("filename"),
                        "ok": bool(r.get("ok")),
                    }
                    for r in vision_rows[:8]
                ]
            meta["origin_text"] = origin
            meta["no_propose"] = True
            meta["allow_loop"] = False
        job = queue.enqueue(
            voice=voice,
            prompt=prompt,
            chat_id=chat_id,
            reply_to=mid,
            source_message_id=mid,
            thread_id=thread,
            depth=0,
            kind="speak",
            meta=meta,
        )
        if job:
            job_ids.append(job["id"])

    debug.remember(
        st,
        {
            "text": text[:400],
            "reason": reason,
            "voices": callouts,
            "message_id": mid,
            "reply_to": reply_mid,
            "job_ids": job_ids,
        },
    )
    state.save_state(st)

    if uid is not None:
        trust.mark_trigger(trust_data, uid)



def once_status(cfg: Config) -> int:
    st = state.load_state(cfg.state_path)
    # sync owner from env if present
    if cfg.alexander_telegram_id and not st.get("owner_id"):
        st["owner_id"] = str(cfg.alexander_telegram_id)
    if cfg.telegram_group_chat_id and not st.get("group_chat_id"):
        st["group_chat_id"] = str(cfg.telegram_group_chat_id)
    ollama_up = ollama_ctl.is_up(cfg)
    flm_up = ollama_ctl.flm_is_up()
    voices = ollama_ctl.voices_up(cfg)
    print(state.status_lines(st, cfg, ollama_up, flm_up=flm_up, voices_up=voices))
    return 0


def run_loop(cfg: Config) -> int:
    if not cfg.telegram_ava_token:
        print("TELEGRAM_AVA_TOKEN missing in secrets.env — cannot poll.", file=sys.stderr)
        return 2
    # deleteWebhook on all three (Ava group+DM; Bruce/Carly DM only)
    for voice in ("ava", "bruce", "carly"):
        tok = cfg.token_for(voice)
        if tok:
            telegram.delete_webhook(tok)
    st = state.load_state(cfg.state_path)
    if cfg.alexander_telegram_id and not st.get("owner_id"):
        st["owner_id"] = str(cfg.alexander_telegram_id)
    # Restarts mid-pipe used to leave busy=true forever (silent chat). Always clear on boot.
    if st.get("busy"):
        print("clearing stale busy flag from previous run", flush=True)
    st["busy"] = False
    st["busy_started"] = 0
    st["busy_note"] = ""
    if not st.get("update_offset_ava") and st.get("update_offset"):
        st["update_offset_ava"] = st["update_offset"]
    state.save_state(st)
    try:
        n_abandon = queue.abandon_stale_running(force=True)
        if n_abandon:
            print(f"abandoned {n_abandon} stale running job(s)", flush=True)
        n_cer = queue.drop_ceremony_jobs()
        if n_cer:
            print(f"dropped {n_cer} desk-session/boot-brief job(s)", flush=True)
    except Exception:
        traceback.print_exc()
    def _publish_menu() -> None:
        try:
            commands.publish_commands(cfg, approve_hint=commands.latest_approve_hint())
        except Exception:
            traceback.print_exc()

    threading.Thread(target=_publish_menu, name="tg-commands", daemon=True).start()
    print("ava-council listening (Ava group+DM; Bruce/Carly DM; origin token untouched)", flush=True)

    def _quiet_exit(*_a: object) -> None:
        raise SystemExit(0)

    signal.signal(signal.SIGTERM, _quiet_exit)
    signal.signal(signal.SIGINT, _quiet_exit)

    trust_data = trust.load_trust(cfg.trust_path)
    from . import listen as _listen

    _listen.start_pollers(cfg, handle=handle_update)
    offset = int(st.get("update_offset_ava") or st.get("update_offset") or 0)

    def _startup_brief() -> None:
        from . import boot_brief as _brief

        try:
            _brief.maybe_run(cfg, state.load_state(cfg.state_path))
        except Exception:
            traceback.print_exc()

    threading.Thread(target=_startup_brief, name="boot-brief", daemon=True).start()
    while True:
        try:
            st = state.load_state(cfg.state_path)
            from . import burst as _burst

            try:
                with _listen.HANDLE_LOCK:
                    _burst.tick(cfg, handle_update)
                    try:
                        _flush_vision_albums(
                            cfg, st, trust.load_trust(cfg.trust_path)
                        )
                    except Exception:
                        traceback.print_exc()
            except Exception:
                traceback.print_exc()
            st = state.load_state(cfg.state_path)
            drain_jobs = bool(not st.get("busy") and queue.peek())
            # Timeout 0 while jobs are queued so slash commands are not starved.
            # Also wake early when a photo album is about to flush.
            album_wait = None
            try:
                from . import vision as _vis

                album_wait = _vis.album_seconds_until_due()
            except Exception:
                album_wait = None
            base_timeout = _burst.poll_timeout(25, drain=drain_jobs)
            if album_wait is not None and album_wait >= 0:
                base_timeout = max(0, min(base_timeout, int(album_wait + 0.35)))
            resp = telegram.get_updates(
                cfg.telegram_ava_token,
                offset=offset,
                timeout=base_timeout,
            )
            if not resp.get("ok"):
                desc = str(resp.get("description") or "")[:180]
                print(f"getUpdates fail {resp.get('error_code')} {desc}", flush=True)
                if drain_jobs:
                    try:
                        worker.process_one(cfg, st)
                    except Exception:
                        traceback.print_exc()
                        st["busy"] = False
                        st["busy_started"] = 0
                        state.save_state(st)
                    continue
                time.sleep(2)
                continue
            st = state.load_state(cfg.state_path)
            for upd in resp.get("result") or []:
                uid_upd = int(upd.get("update_id", 0))
                msg_pre = upd.get("message") or upd.get("edited_message") or {}
                inbound = str(msg_pre.get("text") or msg_pre.get("caption") or "")
                slash_now = inbound.strip().startswith("/")
                # While a reply is running, absorb more human group chatter into the
                # burst instead of waiting then answering each line as its own round.
                if st.get("busy") and not slash_now:
                    chat_pre = msg_pre.get("chat") or {}
                    frm_pre = msg_pre.get("from") or {}
                    ctype = str(chat_pre.get("type") or "")
                    if (
                        ctype in ("group", "supergroup")
                        and inbound.strip()
                        and not bool(frm_pre.get("is_bot"))
                    ):
                        try:
                            from . import burst as _burst
                            from . import router as _router

                            peek = _router.detect_addressing(
                                inbound, msg_pre.get("entities")
                            )
                            speakish = bool(peek.get("voices")) or _burst.chat_has_pending(
                                chat_pre.get("id")
                            )
                            if speakish or "guys" in inbound.lower() or "team" in inbound.lower():
                                _burst.ingest(
                                    chat_id=chat_pre.get("id"),
                                    user_id=frm_pre.get("id") or 0,
                                    listen_voice="ava",
                                    private=False,
                                    message=msg_pre,
                                    text=inbound,
                                    user=frm_pre,
                                )
                                print(
                                    f"busy-absorb update={uid_upd} into burst "
                                    f"from={frm_pre.get('username') or frm_pre.get('id')}",
                                    flush=True,
                                )
                                offset = max(offset, uid_upd + 1)
                                st["update_offset"] = offset
                                st["update_offset_ava"] = offset
                                state.save_state(st)
                                continue
                        except Exception:
                            traceback.print_exc()
                # Wait out busy instead of acking+dropping (silent chat bug).
                # Slash commands skip the wait — /holdoff and /approve must work mid-round.
                wait_i = 0
                while st.get("busy") and not slash_now and wait_i < 120:
                    time.sleep(1)
                    wait_i += 1
                    st = state.load_state(cfg.state_path)
                    if wait_i in (5, 30, 60):
                        print(f"waiting for busy to clear ({wait_i}s) before update {uid_upd}", flush=True)
                if st.get("busy") and not slash_now:
                    print(f"force-clear busy before update {uid_upd}", flush=True)
                    st["busy"] = False
                    st["busy_started"] = 0
                    state.save_state(st)
                try:
                    msg = upd.get("message") or upd.get("edited_message") or {}
                    frm = (msg.get("from") or {})
                    preview = (msg.get("text") or msg.get("caption") or "")[:80].replace("\n", " ")
                    if msg:
                        kind = ((msg.get("chat") or {}).get("type") or "")
                        print(
                            f"inbound voice=ava chat_type={kind} from={frm.get('username') or frm.get('id')} "
                            f"chat={((msg.get('chat') or {}).get('id'))} text={preview!r}",
                            flush=True,
                        )
                    with _listen.HANDLE_LOCK:
                        handle_update(cfg, st, trust_data, upd, listen_voice="ava")
                    # reload state after handle (busy/offset mutations)
                    st = state.load_state(cfg.state_path)
                    trust_data = trust.load_trust(cfg.trust_path)
                except Exception:  # noqa: BLE001
                    traceback.print_exc()
                    st["busy"] = False
                    st["busy_started"] = 0
                    state.save_state(st)
                # Ack only after handling so a crash mid-pipe can retry the update.
                offset = max(offset, uid_upd + 1)
                st["update_offset"] = offset
                st["update_offset_ava"] = offset
                state.save_state(st)
            try:
                with _listen.HANDLE_LOCK:
                    _burst.tick(cfg, handle_update)
                    try:
                        _flush_vision_albums(
                            cfg,
                            state.load_state(cfg.state_path),
                            trust.load_trust(cfg.trust_path),
                        )
                    except Exception:
                        traceback.print_exc()
            except Exception:
                traceback.print_exc()
            try:
                from . import self_repair as _repair

                _repair.pump()
            except Exception:
                traceback.print_exc()
            try:
                from . import quake_watch

                quake_watch.tick()
            except Exception:
                traceback.print_exc()
            try:
                from . import bruce_stats

                bruce_stats.maybe_slot()
            except Exception:
                traceback.print_exc()
            try:
                from . import autonomy as _autonomy

                _autonomy.tick(cfg)
            except Exception:
                traceback.print_exc()
            try:
                from apps.core.services import ollama_lifecycle

                ollama_lifecycle.tick_idle()
            except Exception:
                traceback.print_exc()
            st = state.load_state(cfg.state_path)
            if not st.get("busy"):
                try:
                    worker.process_one(cfg, st)
                except Exception:
                    traceback.print_exc()
                    st["busy"] = False
                    state.save_state(st)
        except KeyboardInterrupt:
            print("bye", flush=True)
            return 0
        except Exception:  # noqa: BLE001
            traceback.print_exc()
            time.sleep(3)


def main(argv: list[str] | None = None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    # Manual Grok Bot bridge: python -m apps.council post --voice ava --text "..."
    if argv and argv[0] == "post":
        from . import post as post_mod

        return post_mod.main(argv[1:])
    parser = argparse.ArgumentParser(prog="apps.council")
    parser.add_argument(
        "--once-status",
        action="store_true",
        help="Print discussion/mode/ollama-up/owner-bound and exit",
    )
    args = parser.parse_args(argv)
    cfg = load_config()
    if args.once_status:
        return once_status(cfg)
    return run_loop(cfg)


if __name__ == "__main__":
    raise SystemExit(main())
