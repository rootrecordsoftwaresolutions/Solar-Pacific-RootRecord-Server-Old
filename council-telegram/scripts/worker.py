"""Process one queued Ollama job at a time."""
from __future__ import annotations

import re
import threading
import time
from pathlib import Path
from typing import Any

from . import chatlog, feelings, ollama_client, ollama_ctl, queue, router, sanitize, telegram
from .config import HEAT_MODEL_DEFAULT, NUM_PREDICT, SEND_STAGGER_S, Config
from .personas import system_for

_last_send_at = 0.0
_JOB_LOCK = threading.Lock()


def _react(
    cfg: Config,
    chat_id: int | str,
    message_id: int | None,
    emoji: str | None,
    *,
    voice: str = "ava",
) -> None:
    if not message_id:
        return
    who = voice if voice in ("ava", "bruce", "carly") else "ava"
    telegram.set_message_reaction(cfg.token_for(who), chat_id, int(message_id), emoji)


def _send(
    cfg: Config,
    voice: str,
    chat_id: int | str,
    text: str,
    reply_to: int | None,
    *,
    job_id: str | None = None,
    is_owner_chat: bool = False,
    forbid_names: set[str] | None = None,
) -> int | None:
    global _last_send_at
    clean = sanitize.sanitize_outbound(
        text,
        voice=voice,
        allow_operator_name=bool(is_owner_chat),
        forbid_names=forbid_names,
    )
    elapsed = time.time() - _last_send_at
    if _last_send_at and elapsed < SEND_STAGGER_S:
        time.sleep(SEND_STAGGER_S - elapsed)
    res = telegram.send_message(cfg.token_for(voice), chat_id, clean, reply_to=reply_to)
    _last_send_at = time.time()
    try:
        from apps.core.services import ollama_lifecycle

        ollama_lifecycle.on_ai_use()
    except Exception:
        pass
    if not res.get("ok"):
        return None
    mid = ((res.get("result") or {}) if isinstance(res.get("result"), dict) else {}).get("message_id")
    chatlog.append(
        {
            "dir": "out",
            "voice": voice,
            "chat_id": str(chat_id),
            "message_id": mid,
            "reply_to": reply_to,
            "text": clean[:1500],
            "job_id": job_id,
        }
    )
    return int(mid) if mid is not None else None


def _person_prompt(cfg: Config, meta: dict[str, Any], *, voice: str | None = None) -> str:
    parts: list[str] = []
    try:
        from . import people

        uid = meta.get("judge_user_id")
        if uid is not None:
            parts.append(people.prompt_block(uid))
        if voice:
            people.ensure_agents()
            parts.append(people.agent_prompt_block(voice))
    except Exception:
        pass
    try:
        from . import asked_today

        if voice:
            parts.append(asked_today.prompt_block(voice))
    except Exception:
        pass
    return "\n".join(p for p in parts if p).strip()



def _enqueue_agent_follows(
    cfg: Config,
    *,
    job: dict[str, Any],
    meta: dict[str, Any],
    voice: str,
    clean: str,
    chat_id: int | str,
    reply_to: int | None,
    source_mid: int | None,
    depth: int,
    skip: set[str] | None = None,
    force_question: bool = False,
) -> None:
    """Queue a natural reply when one agent asks another (vocative or handoff)."""
    if depth >= queue.MAX_LOOP_DEPTH:
        return
    if meta.get("dm"):
        return
    if meta.get("conclusion_pointer"):
        return
    if meta.get("summary_mode"):
        return
    origin = str(meta.get("origin_text") or meta.get("user_text") or "")[:800]
    try:
        from . import conclusions as _conc

        # Once a topic is sealed, do not ping-pong the same lecture agent→agent.
        if origin and _conc.covers(chat_id, origin):
            print("agent-follow skip — topic sealed", flush=True)
            return
        if clean and _conc.covers(chat_id, clean):
            print("agent-follow skip — reply topic sealed", flush=True)
            return
    except Exception:
        pass
    skip = set(skip or set())
    try:
        from . import asked_today
        from . import prompting
    except Exception:
        return
    targets = asked_today.follow_targets(clean, from_voice=voice)
    for v in router.detect_agent_calls_in_reply(clean, from_voice=voice):
        if v not in targets:
            if force_question or "?" in clean:
                targets.append(v)
    for nxt in targets:
        if nxt in skip or nxt == voice:
            continue
        if depth + 1 > queue.MAX_LOOP_DEPTH:
            break
        follow_prompt = prompting.build_round_follow_prompt(
            voice=nxt,
            prev_voice=voice,
            prev_text=clean[:1500],
            origin_text=origin or "(team thread)",
            person_block=_person_prompt(cfg, meta, voice=nxt),
            chat_id=chat_id,
        )
        follow_prompt += (
            "\n\nA teammate asked you something. Answer them directly. "
            "Do not re-ask what already has an answer today. "
            "One clear reply is enough."
        )
        queue.enqueue(
            voice=nxt,
            prompt=follow_prompt,
            chat_id=chat_id,
            reply_to=reply_to,
            source_message_id=source_mid,
            thread_id=job.get("thread_id"),
            depth=depth + 1,
            kind="speak",
            meta={
                "allow_loop": False,
                "from_voice": voice,
                "agent_follow": True,
                "no_propose": True,
                "judge_user_id": meta.get("judge_user_id"),
                "judge_is_owner": bool(meta.get("judge_is_owner")),
                "origin_text": origin,
            },
        )
        print(f"agent-follow {voice} -> {nxt}", flush=True)


def _is_pass(text: str) -> bool:
    t = re.sub(r"<<<[^>]+>>>", "", text or "")
    t = re.sub(r"[.\s]+$", "", t.strip())
    t = re.sub(r"\s+", " ", t)
    return t.upper() in {"PASS", "NO ADD", "NOTHING TO ADD", "SKIP"}


def _publish_proposal(cfg: Config, chat_id: int | str, meta: dict[str, Any], reply_to: int | None) -> None:
    from . import commands, proposals

    predecessor = str(meta.get("predecessor_id") or meta.get("proposal_id") or "").strip()
    rec = proposals.publish(
        chat_id=chat_id,
        origin=str(meta.get("origin_text") or ""),
        thread_id=str(meta.get("thread_id") or ""),
        predecessor_id=predecessor,
    )
    path = rec.get("path") if isinstance(rec.get("path"), str) else str(rec.get("path") or "")
    pid = rec.get("id") or ""
    if not path:
        return
    if rec.get("amended_this_round"):
        cap = (
            f"Daily proposal {pid} — amended, not rewritten.\n"
            f"Reply to add notes. Owner: /done when this daily is finished, /approve {pid}"
        )
    else:
        cap = (
            f"Daily proposal {pid} — new file. Same-day rounds amend this file.\n"
            f"Owner: /done when finished, /approve {pid}"
        )
    res = telegram.send_document(
        cfg.token_for("bruce"),
        chat_id,
        Path(path),
        caption=sanitize.sanitize_outbound(cap, voice="bruce")[:900],
    )
    mid = ((res.get("result") or {}) if isinstance(res.get("result"), dict) else {}).get("message_id")
    if mid:
        proposals.remember_telegram_message(pid, mid)
    if meta.get("brainstorm_wrap"):
        try:
            from .handoff import send_to_chat

            send_to_chat(cfg, chat_id)
        except Exception:
            pass
    commands.publish_commands(cfg, approve_hint=pid)
    from . import self_repair

    if self_repair.active() and path:
        try:
            body = Path(path).read_text(encoding="utf-8", errors="replace")[:8000]
        except OSError:
            body = ""
        if body:
            self_repair.start_job(body, source=f"proposal-{pid}")


def process_one(cfg: Config, st: dict[str, Any]) -> bool:
    """Returns True if a job ran."""
    with _JOB_LOCK:
        return _process_one_locked(cfg, st)


def _process_one_locked(cfg: Config, st: dict[str, Any]) -> bool:
    job = queue.peek()
    if not job:
        return False
    jid = job["id"]
    queue.mark(jid, "running")
    try:
        from apps.core.services import ollama_lifecycle

        ollama_lifecycle.ensure_clips()
        ollama_lifecycle.on_ai_use()
        from . import ollama_client as _oc

        if _oc._npu_chat_env():
            ollama_ctl.flm_start()
            if not ollama_ctl.voices_up(cfg):
                ollama_ctl.start(cfg)
        elif not ollama_ctl.is_up(cfg):
            ollama_ctl.start(cfg)
    except Exception:
        pass
    st["busy"] = True
    st["busy_started"] = int(time.time())
    from . import state as state_mod

    state_mod.save_state(st)

    chat_id = job["chat_id"]
    voice = job["voice"]
    reply_to = job.get("reply_to")
    source_mid = job.get("source_message_id")
    depth = int(job.get("depth") or 0)
    prompt = job.get("prompt") or ""
    kind = job.get("kind") or "speak"
    meta = job.get("meta") or {}

    try:
        if meta.get("skill_exec"):
            from . import skills as skillpack

            _react(cfg, chat_id, source_mid, "⚡", voice=voice)
            telegram.send_chat_action(cfg.token_for(voice), chat_id, "typing")
            extra = meta.get("skill_argv") if isinstance(meta.get("skill_argv"), list) else None
            out = skillpack.run_exec(str(meta.get("skill_id") or ""), extra_argv=extra)
            raw_out = str(out.get("text") or "no data")
            from .handoff import parse_zip_line

            zip_path = parse_zip_line(raw_out)
            photo_path = None
            shown_lines = []
            for ln in raw_out.splitlines():
                if ln.startswith("HANDOFF_ZIP="):
                    continue
                if ln.startswith("PANELS_PHOTO="):
                    from pathlib import Path as _P

                    cand = _P(ln.split("=", 1)[1].strip())
                    if (
                        cand.is_file()
                        and cand.suffix.lower() in {".jpg", ".jpeg", ".png"}
                        and "panels-cam" in str(cand.resolve())
                    ):
                        photo_path = cand
                    continue
                shown_lines.append(ln)
            shown = "\n".join(shown_lines)
            body = sanitize.sanitize_outbound(shown or "no data", voice=voice)
            _send(cfg, voice, chat_id, body, reply_to=reply_to, job_id=jid)
            if zip_path:
                telegram.send_document(
                    cfg.token_for("bruce"),
                    chat_id,
                    zip_path,
                    caption="Handoff zip — copies only. No secrets. For manual / non-API use.",
                )
            if photo_path:
                telegram.send_document(
                    cfg.token_for(voice),
                    chat_id,
                    photo_path,
                    caption="Rear Shed panels",
                )
            _react(cfg, chat_id, source_mid, "✅", voice=voice)
            queue.mark(jid, "done")
            return True

        if meta.get("conclusion_pointer"):
            from . import conclusions as _conc

            _react(cfg, chat_id, source_mid, "⚡", voice=voice)
            telegram.send_chat_action(cfg.token_for(voice), chat_id, "typing")
            hit = meta.get("conclusion") if isinstance(meta.get("conclusion"), dict) else {}
            display = "friend"
            try:
                from . import trust as _trust
                from . import state as _state

                uid = meta.get("judge_user_id")
                if uid is not None:
                    td = _trust.load_trust(cfg.trust_path)
                    display = _trust.display_of(td, uid) or display
            except Exception:
                pass
            if meta.get("judge_is_owner"):
                display = "Alexander"
            body = _conc.pointer_text(hit, voice=voice, display=display)
            mid_out = _send(
                cfg,
                voice,
                chat_id,
                body,
                reply_to=source_mid or reply_to,
                job_id=jid,
                is_owner_chat=bool(meta.get("judge_is_owner")),
            )
            feelings.apply_event("spoke", voice=voice)
            _react(cfg, chat_id, source_mid, "✅", voice=voice)
            print(
                f"conclusion-pointer-sent voice={voice} mid={mid_out} bucket={hit.get('bucket')}",
                flush=True,
            )
            queue.mark(jid, "done")
            return True

        _react(cfg, chat_id, source_mid, "⚡", voice=voice)
        telegram.send_chat_action(cfg.token_for(voice), chat_id, "typing")
        predict_key = "speak" if kind in ("speak", "casual") else kind
        n_pred = NUM_PREDICT.get(predict_key, NUM_PREDICT.get("speak", 420))
        try:
            from . import brainstorm as _bs

            if _bs.status() in {"running", "wrapping"}:
                n_pred = NUM_PREDICT.get("brainstorm", 220)
        except Exception:
            pass
        use_heat = bool(meta.get("nsfw") or meta.get("heat")) and voice in ("ava", "carly")
        dm = bool(meta.get("dm"))
        # Private Ava/Carly DMs: always Dolphin heat model + heat tone (chatbot style).
        if dm and voice in ("ava", "carly"):
            use_heat = True
        model = cfg.model_for(voice, dm=dm)
        from . import heat as heat_mod

        if not heat_mod.model_installed(cfg, model):
            print(f"voice-model missing {model} voice={voice} — stay {cfg.chat_model}", flush=True)
            model = cfg.chat_model
        if use_heat:
            want = str(getattr(cfg, "heat_model", "") or HEAT_MODEL_DEFAULT)
            if heat_mod.model_installed(cfg, want):
                model = want
                print(f"heat-model {want} voice={voice} dm={dm} job={jid}", flush=True)
            else:
                use_heat = False
                print(f"heat-model missing {want} — stay {model}", flush=True)
        sys_prompt = system_for(voice, heat=use_heat)
        raw = ollama_client.chat(
            cfg,
            model,
            sys_prompt,
            prompt,
            num_predict=n_pred,
            timeout=180,
            voice=voice,
        )
        if isinstance(raw, str) and raw.startswith("[ollama"):
            print(f"ollama-down voice={voice} job={jid}", flush=True)
            _send(
                cfg,
                voice,
                chat_id,
                "Brain's down on this box. Give it a minute.",
                reply_to=reply_to,
                job_id=jid,
                is_owner_chat=bool(meta.get("judge_is_owner")),
            )
            queue.mark(jid, "failed")
            _react(cfg, chat_id, source_mid, None, voice=voice)
            return False
        try:
            from . import judgment, people

            judgment.apply_from_reply(cfg, st, raw, voice=voice, meta=meta, chat_id=chat_id)
            people.apply_tags(raw, meta.get("judge_user_id"), voice=voice)
        except Exception:
            print("judgment skip", flush=True)
        owner_turn = bool(meta.get("judge_is_owner"))
        forbid = None
        if meta.get("dm"):
            from . import people as people_mod

            forbid = people_mod.other_labels(meta.get("judge_user_id"))
        clean = sanitize.sanitize_outbound(
            raw,
            voice=voice,
            allow_operator_name=owner_turn,
            forbid_names=forbid,
        )
        passed = _is_pass(clean)
        if passed and not meta.get("allow_pass"):
            print(f"pass-retry {voice} job={jid}", flush=True)
            raw = ollama_client.chat(
                cfg,
                model,
                sys_prompt,
                prompt
                + "\n\nDo not reply PASS. Speak now in character, one or two finished sentences.",
                num_predict=n_pred,
                timeout=180,
                voice=voice,
            )
            clean = sanitize.sanitize_outbound(
                raw,
                voice=voice,
                allow_operator_name=owner_turn,
                forbid_names=forbid,
            )
            passed = _is_pass(clean)
        if not passed:
            mid_out = _send(
                cfg,
                voice,
                chat_id,
                clean,
                reply_to=reply_to,
                job_id=jid,
                is_owner_chat=owner_turn,
                forbid_names=forbid,
            )
            feelings.apply_event("spoke", voice=voice)
            if (
                mid_out
                and voice == "ava"
                and meta.get("vision_take")
                and isinstance(meta.get("vision_row"), dict)
            ):
                try:
                    from . import vision as _vis
                    from . import prompting

                    _vis.register_take(
                        chat_id=chat_id,
                        message_id=int(mid_out),
                        vision_row=dict(meta.get("vision_row") or {}),
                        body=clean,
                        photo_message_id=int(source_mid) if source_mid is not None else None,
                        from_user_id=meta.get("judge_user_id"),
                    )
                    print(f"vision-take registered mid={mid_out}", flush=True)
                    if meta.get("handoff_bruce") and depth < queue.MAX_LOOP_DEPTH:
                        vrow = dict(meta.get("vision_row") or {})
                        album_rows = meta.get("vision_rows")
                        if isinstance(album_rows, list) and len(album_rows) > 1:
                            vblock = _vis.prompt_block_multi(
                                [r for r in album_rows if isinstance(r, dict)],
                                for_voice="bruce",
                            )
                            origin_bit = f"Photo album ({len(album_rows)})"
                        else:
                            vblock = _vis.prompt_block(vrow, for_voice="bruce")
                            origin_bit = "Photo shared"
                        bruce_prompt = prompting.build_speak_prompt(
                            speaker_line="",
                            display="Alexander" if meta.get("judge_is_owner") else "friend",
                            voice="bruce",
                            user_text=str(meta.get("origin_text") or origin_bit),
                            chat_id=chat_id,
                            quote="",
                            extra=(
                                "Ava already posted her first take on this photo (below). "
                                "Add a brief ops/context note only. Prefer verified prices. "
                                "Do not repeat her correction footer.\n\n"
                                f"Ava's take:\n{clean[:900]}"
                            ),
                            skill_block="",
                            vision_block=vblock,
                            must_speak=True,
                            private=bool(meta.get("dm")),
                        )
                        queue.enqueue(
                            voice="bruce",
                            prompt=bruce_prompt,
                            chat_id=chat_id,
                            reply_to=mid_out,
                            source_message_id=source_mid,
                            thread_id=job.get("thread_id"),
                            depth=depth + 1,
                            kind="speak",
                            meta={
                                "allow_loop": False,
                                "no_propose": True,
                                "vision_bruce_pass": True,
                                "origin_text": meta.get("origin_text"),
                                "judge_user_id": meta.get("judge_user_id"),
                                "judge_is_owner": bool(meta.get("judge_is_owner")),
                                "from_voice": "ava",
                            },
                        )
                        print("vision-take handoff bruce queued", flush=True)
                except Exception:
                    print("vision-take register/handoff skip", flush=True)
            try:
                from . import brainstorm as _bs

                _bs.note_said(voice, clean)
            except Exception:
                pass
            try:
                from . import people as _people
                from . import asked_today as _asked

                _people.observe_agent(voice, clean)
                _asked.note_answer(voice, clean)
                _asked.note_asks(voice, clean)
            except Exception:
                print("agent-memory skip", flush=True)
            try:
                from . import conclusions as _conc

                ask_for_seal = str(
                    meta.get("origin_text") or meta.get("user_text") or ""
                ).strip()
                if ask_for_seal and mid_out:
                    _conc.observe_reply(
                        chat_id=chat_id,
                        ask=ask_for_seal,
                        voice=voice,
                        text=clean,
                        message_id=mid_out,
                    )
                    # Seal when the team chain finishes, or on a solo reply.
                    seal_now = False
                    if meta.get("team_chain") or meta.get("round"):
                        order = list(meta.get("round_order") or router.ROUND_ORDER)
                        idx = int(meta.get("round_index") or 0)
                        seal_now = idx >= len(order) - 1
                    else:
                        seal_now = True
                    if seal_now:
                        _conc.seal(
                            chat_id=chat_id,
                            ask=ask_for_seal,
                            voice=voice,
                            message_id=mid_out,
                        )
            except Exception:
                print("conclusion-seal skip", flush=True)
        if meta.get("dm") and not passed:
            try:
                from . import dm_propose

                dm_propose.maybe_lift(
                    cfg, st, voice=voice, raw=raw, meta=meta, dm_chat_id=chat_id
                )
            except Exception:
                print("dm-lift skip", flush=True)
            try:
                from . import refer as _refer

                if not meta.get("nsfw"):
                    _refer.note_offer(
                        meta.get("judge_user_id"), raw, from_voice=voice
                    )
            except Exception:
                print("refer offer skip", flush=True)
        _react(cfg, chat_id, source_mid, "✅", voice=voice)

        if meta.get("round") and not meta.get("dm"):
            order = list(meta.get("round_order") or router.ROUND_ORDER)
            idx = int(meta.get("round_index") or 0)
            nxt_idx = idx + 1
            chained = False
            if nxt_idx < len(order) and depth < queue.MAX_LOOP_DEPTH:
                nxt = order[nxt_idx]
                origin = str(meta.get("origin_text") or "")
                from . import prompting

                follow_prompt = prompting.build_round_follow_prompt(
                    voice=nxt,
                    prev_voice=voice,
                    prev_text=clean if not passed else "(passed — nothing to add)",
                    origin_text=origin,
                    person_block=_person_prompt(cfg, meta, voice=nxt),
                    chat_id=chat_id,
                )
                nxt_job = queue.enqueue(
                    voice=nxt,
                    prompt=follow_prompt,
                    chat_id=chat_id,
                    reply_to=reply_to,
                    source_message_id=source_mid,
                    thread_id=job.get("thread_id"),
                    depth=depth + 1,
                    kind="speak",
                    meta={
                        "allow_loop": True,
                        "round": True,
                        "round_order": order,
                        "round_index": nxt_idx,
                        "origin_text": origin,
                        "from_voice": voice,
                        "no_propose": bool(meta.get("no_propose")),
                        "team_chain": bool(meta.get("team_chain")),
                        "judge_user_id": meta.get("judge_user_id"),
                        "judge_is_owner": bool(meta.get("judge_is_owner")),
                        "allow_pass": bool(meta.get("allow_pass")),
                        "brainstorm_wrap": bool(meta.get("brainstorm_wrap")),
                        "predecessor_id": str(
                            meta.get("predecessor_id") or meta.get("proposal_id") or ""
                        ),
                    },
                )
                chained = nxt_job is not None
                if nxt_job is None:
                    print(f"round chain skip voice={nxt} idx={nxt_idx}", flush=True)
            if not chained and not meta.get("no_propose"):
                tid = str(job.get("thread_id") or "")
                if tid in ("desk-session", "boot-brief"):
                    print(f"skip proposal file for {tid}", flush=True)
                else:
                    meta = dict(meta)
                    meta["thread_id"] = tid
                    _publish_proposal(cfg, chat_id, meta, reply_to)
            # Teammate asked someone who already spoke (or outside next) — queue a reply.
            if not passed and depth < queue.MAX_LOOP_DEPTH:
                _enqueue_agent_follows(
                    cfg,
                    job=job,
                    meta=meta,
                    voice=voice,
                    clean=clean,
                    chat_id=chat_id,
                    reply_to=reply_to,
                    source_mid=source_mid,
                    depth=depth,
                    skip=set(order[nxt_idx : nxt_idx + 1]) if nxt_idx < len(order) else set(),
                )
        elif not meta.get("dm") and not passed:
            allow = bool(meta.get("allow_loop")) or bool(meta.get("team_chain"))
            # Always allow one hop when a teammate is clearly asked a question.
            _enqueue_agent_follows(
                cfg,
                job=job,
                meta={**meta, "allow_loop": True if allow else meta.get("allow_loop")},
                voice=voice,
                clean=clean,
                chat_id=chat_id,
                reply_to=reply_to,
                source_mid=source_mid,
                depth=depth,
                skip=set(),
                force_question=True,
            )
        queue.mark(jid, "done")
    except Exception:
        queue.mark(jid, "failed")
        _react(cfg, chat_id, source_mid, None, voice=voice)
        print(f"queue job {jid} failed ({voice})", flush=True)
    finally:
        st["busy"] = False
        st["busy_started"] = 0
        state_mod.save_state(st)
    return True
