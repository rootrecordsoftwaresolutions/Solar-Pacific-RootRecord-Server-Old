"""Casual / council / mention_one pipes + artifact zip."""
from __future__ import annotations

import time
import uuid
from pathlib import Path
from typing import Any

from . import artifacts, ollama_client, sanitize, telegram
from .config import NUM_PREDICT, Config, RUNS_DIR
from .personas import system_for


def _user_blob(user_text: str, speaker: str = "") -> str:
    if speaker:
        return f"{speaker}\nUse the person’s display name when given.\n\n{user_text}"
    return user_text


def _post(cfg: Config, voice: str, chat_id: int | str, text: str, reply_to: int | None = None) -> None:
    token = cfg.token_for(voice)
    clean = sanitize.sanitize_outbound(artifacts.strip_file_markers(text), voice=voice)
    telegram.send_message(token, chat_id, clean, reply_to=reply_to)


def run_casual(
    cfg: Config,
    chat_id: int | str,
    user_text: str,
    reply_to: int | None = None,
    speaker: str = "",
) -> dict[str, Any]:
    model = cfg.model_for("ava")
    n_pred = NUM_PREDICT["casual"]
    raw = ollama_client.chat(
        cfg,
        model,
        system_for("ava"),
        _user_blob(user_text, speaker),
        num_predict=n_pred,
        voice="ava",
    )
    run_id = uuid.uuid4().hex[:10]
    run_dir = RUNS_DIR / run_id
    zpath = artifacts.collect_and_zip([raw], run_dir)
    _post(cfg, "ava", chat_id, raw, reply_to=reply_to)
    if zpath:
        telegram.send_document(
            cfg.token_for("ava"),
            chat_id,
            zpath,
            caption=(artifacts.strip_file_markers(raw)[:400] or "Ava note"),
        )
    return {"run_id": run_id, "zip": str(zpath) if zpath else ""}


def run_mention_one(
    cfg: Config,
    chat_id: int | str,
    user_text: str,
    voice: str,
    reply_to: int | None = None,
    speaker: str = "",
) -> dict[str, Any]:
    voice = (voice or "ava").lower()
    if voice not in ("ava", "bruce", "carly"):
        voice = "ava"
    model = cfg.model_for(voice)
    n_pred = NUM_PREDICT["mention"]
    raw = ollama_client.chat(
        cfg,
        model,
        system_for(voice),
        _user_blob(user_text, speaker),
        num_predict=n_pred,
        voice=voice,
    )
    run_id = uuid.uuid4().hex[:10]
    run_dir = RUNS_DIR / run_id
    zpath = artifacts.collect_and_zip([raw], run_dir)
    _post(cfg, voice, chat_id, raw, reply_to=reply_to)
    if zpath:
        telegram.send_document(
            cfg.token_for("ava"),
            chat_id,
            zpath,
            caption=(artifacts.strip_file_markers(raw)[:400] or "note"),
        )
    return {"run_id": run_id, "voice": voice, "zip": str(zpath) if zpath else ""}


def run_council(
    cfg: Config,
    chat_id: int | str,
    user_text: str,
    reply_to: int | None = None,
    want_implement: bool = False,
    speaker: str = "",
) -> dict[str, Any]:
    """Ava → Bruce → Carly → Ava unmodified-summary. Zip on final Ava message."""
    run_id = uuid.uuid4().hex[:10]
    run_dir = RUNS_DIR / run_id
    run_dir.mkdir(parents=True, exist_ok=True)
    ask = _user_blob(user_text, speaker)

    ava1 = ollama_client.chat(
        cfg,
        cfg.model_for("ava"),
        system_for("ava"),
        f"First take on this (Telegram group). Be yourself.\n\n{ask}",
        num_predict=NUM_PREDICT["council"],
        voice="ava",
    )
    _post(cfg, "ava", chat_id, ava1, reply_to=reply_to)
    time.sleep(0.4)

    bruce = ollama_client.chat(
        cfg,
        cfg.model_for("bruce"),
        system_for("bruce"),
        (
            f"User ask:\n{ask}\n\n"
            f"Ava Ivy first take (do not rewrite her words; respond as Bruce):\n{ava1}"
        ),
        num_predict=NUM_PREDICT["council"],
        voice="bruce",
    )
    _post(cfg, "bruce", chat_id, bruce)
    time.sleep(0.4)

    carly = ollama_client.chat(
        cfg,
        cfg.model_for("carly"),
        system_for("carly"),
        (
            f"User ask:\n{ask}\n\n"
            f"Ava Ivy first take:\n{ava1}\n\n"
            f"Bruce Monitor:\n{bruce}\n\n"
            "Respond as Carly Mal (AppSec)."
        ),
        num_predict=NUM_PREDICT["council"],
        voice="carly",
    )
    _post(cfg, "carly", chat_id, carly)
    time.sleep(0.4)

    summary_prompt = (
        "Write Ava Ivy's FINAL SUMMARY. Recap the three voices and open questions / decision needed.\n"
        "Do NOT rewrite or retract your first take — leave it unmodified; summarize alongside it.\n"
        f"Your first take was:\n{ava1}\n\n"
        f"Bruce said:\n{bruce}\n\n"
        f"Carly said:\n{carly}\n\n"
        f"Original user ask:\n{ask}"
    )
    summary = ollama_client.chat(
        cfg,
        cfg.model_for("ava"),
        system_for("ava"),
        summary_prompt,
        num_predict=NUM_PREDICT["summary"],
        voice="ava",
    )

    zpath = artifacts.collect_and_zip([ava1, bruce, carly, summary], run_dir)
    clean_summary = sanitize.sanitize_outbound(artifacts.strip_file_markers(summary), voice="ava")
    if zpath:
        telegram.send_document(
            cfg.token_for("ava"),
            chat_id,
            zpath,
            caption=(clean_summary[:400] or "Council bundle"),
        )
        # also send text if caption truncated heavily
        if len(clean_summary) > 900:
            _post(cfg, "ava", chat_id, clean_summary)
    else:
        _post(cfg, "ava", chat_id, summary)

    meta = {
        "run_id": run_id,
        "ava1": ava1,
        "bruce": bruce,
        "carly": carly,
        "summary": summary,
        "zip": str(zpath) if zpath else "",
        "want_implement": want_implement,
    }
    (run_dir / "meta.txt").write_text(
        f"user:\n{user_text}\n\nava1:\n{ava1}\n\nbruce:\n{bruce}\n\ncarly:\n{carly}\n\nsummary:\n{summary}\n",
        encoding="utf-8",
    )
    return meta


def implement_prompt_bundle(meta: dict[str, Any], user_ask: str) -> str:
    return (
        f"User ask:\n{user_ask}\n\n"
        f"Ava first take:\n{meta.get('ava1', '')}\n\n"
        f"Bruce:\n{meta.get('bruce', '')}\n\n"
        f"Carly:\n{meta.get('carly', '')}\n\n"
        f"Ava summary:\n{meta.get('summary', '')}\n"
    )
