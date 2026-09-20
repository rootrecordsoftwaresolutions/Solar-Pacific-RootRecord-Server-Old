"""Manual post bridge for Grok Bot → Telegram (no auto trigger)."""
from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path
from typing import Any

from . import sanitize, state, telegram
from .config import Config, load_config


def _note_post_source(cfg: Config, source: str) -> None:
    """Provenance only — never bumps trust."""
    st = state.load_state(cfg.state_path)
    st["last_post_source"] = source or "grok"
    st["last_grok_post_at"] = int(time.time())
    state.save_state(st)


def resolve_chat_id(cfg: Config, chat_id: str | None) -> str:
    st = state.load_state(cfg.state_path)
    cid = (chat_id or "").strip() or str(st.get("group_chat_id") or cfg.telegram_group_chat_id or "").strip()
    if not cid:
        raise SystemExit("No group chat id. Bind the council group first or pass --chat-id.")
    return cid


def post_text(
    cfg: Config,
    voice: str,
    text: str,
    chat_id: str | None = None,
    reply_to: int | None = None,
    source: str = "grok",
) -> dict[str, Any]:
    voice = (voice or "ava").lower().strip()
    if voice not in ("ava", "bruce", "carly"):
        raise SystemExit("voice must be ava|bruce|carly")
    body = sanitize.sanitize_outbound((text or "").strip(), voice=voice)
    if not body:
        raise SystemExit("empty text")
    cid = resolve_chat_id(cfg, chat_id)
    token = cfg.token_for(voice)
    if not token:
        raise SystemExit(f"missing token for {voice}")
    # Tag source lightly only in logs via return; do not alter personality voice in chat
    result = telegram.send_message(token, cid, body, reply_to=reply_to)
    return {"ok": bool(result.get("ok")), "voice": voice, "chat_id": cid, "source": source, "raw": result}


def post_document(
    cfg: Config,
    voice: str,
    path: Path,
    caption: str = "",
    chat_id: str | None = None,
) -> dict[str, Any]:
    voice = (voice or "ava").lower().strip()
    if voice not in ("ava", "bruce", "carly"):
        raise SystemExit("voice must be ava|bruce|carly")
    if not path.is_file():
        raise SystemExit(f"file not found: {path}")
    cid = resolve_chat_id(cfg, chat_id)
    token = cfg.token_for(voice)
    cap = sanitize.sanitize_outbound(caption, voice=voice) if caption else ""
    result = telegram.send_document(token, cid, path, caption=cap[:900] if cap else None)
    return {"ok": bool(result.get("ok")), "voice": voice, "chat_id": cid, "raw": result}


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="apps.council post")
    p.add_argument("--voice", required=True, choices=["ava", "bruce", "carly"])
    p.add_argument("--text", default="", help="Message body (or use --stdin)")
    p.add_argument("--stdin", action="store_true", help="Read message body from stdin")
    p.add_argument("--file", type=Path, help="Optional document to send (sendDocument)")
    p.add_argument("--caption", default="", help="Caption when --file is set")
    p.add_argument("--chat-id", default="", help="Override bound group chat id")
    p.add_argument("--reply-to", type=int, default=None)
    p.add_argument("--source", default="grok", help="Provenance tag for logs only")
    return p


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    cfg = load_config()
    text = args.text
    if args.stdin:
        text = sys.stdin.read()
    if args.file:
        out = post_document(cfg, args.voice, args.file, caption=args.caption or text, chat_id=args.chat_id or None)
        print("ok" if out.get("ok") else "fail", out.get("voice"), "file")
        if not out.get("ok"):
            print(out.get("raw"), file=sys.stderr)
            return 1
        _note_post_source(cfg, "grok")
        return 0
    out = post_text(
        cfg,
        args.voice,
        text,
        chat_id=args.chat_id or None,
        reply_to=args.reply_to,
        source=args.source,
    )
    print("ok" if out.get("ok") else "fail", out.get("voice"))
    if not out.get("ok"):
        print(out.get("raw"), file=sys.stderr)
        return 1
    _note_post_source(cfg, args.source or "grok")
    return 0
