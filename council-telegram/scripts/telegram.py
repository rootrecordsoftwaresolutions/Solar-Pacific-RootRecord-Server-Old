"""Telegram Bot API via urllib (stdlib only).

Council uses ONLY tokens from ~/.config/ava-council/secrets.env
(TELEGRAM_AVA_TOKEN / TELEGRAM_BRUCE_TOKEN / TELEGRAM_CARLY_TOKEN).
Never touch AVA_TELEGRAM_BOT_TOKEN / TELEGRAM_BOT_TOKEN or apps/core getUpdates.
"""
from __future__ import annotations

import hashlib
import json
import mimetypes
import ssl
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

API = "https://api.telegram.org"


def _ctx() -> ssl.SSLContext:
    return ssl.create_default_context()


def _url(token: str, method: str) -> str:
    return f"{API}/bot{token}/{method}"


def api_call(
    token: str,
    method: str,
    params: dict[str, Any] | None = None,
    timeout: float = 60.0,
) -> dict[str, Any]:
    if not token:
        return {"ok": False, "description": "missing token"}
    data = None
    headers = {"Content-Type": "application/json"}
    url = _url(token, method)
    if params is not None:
        data = json.dumps(params).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method="POST" if data else "GET")
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=_ctx()) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            return json.loads(body)
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", errors="replace") if e.fp else ""
        try:
            return json.loads(err_body) if err_body else {"ok": False, "description": str(e)}
        except json.JSONDecodeError:
            return {"ok": False, "description": err_body or str(e)}
    except Exception as e:  # noqa: BLE001
        return {"ok": False, "description": str(e)}


def delete_webhook(token: str) -> dict[str, Any]:
    return api_call(token, "deleteWebhook", {"drop_pending_updates": False}, timeout=30)


def get_updates(
    token: str,
    offset: int = 0,
    timeout: int = 25,
    allowed_updates: list[str] | None = None,
) -> dict[str, Any]:
    params: dict[str, Any] = {
        "offset": offset,
        "timeout": timeout,
        "allowed_updates": allowed_updates
        or ["message", "edited_message", "chat_member", "my_chat_member"],
    }
    return api_call(token, "getUpdates", params, timeout=float(timeout) + 15)


def send_chat_action(token: str, chat_id: int | str, action: str = "typing") -> dict[str, Any]:
    return api_call(
        token,
        "sendChatAction",
        {"chat_id": chat_id, "action": action},
        timeout=15,
    )


def edit_message_text(
    token: str,
    chat_id: int | str,
    message_id: int,
    text: str,
    *,
    parse_mode: str | None = None,
) -> dict[str, Any]:
    """Edit a message Ava (or another voice) already posted. Caps at one Telegram chunk."""
    body = (text or "").strip()
    if not body:
        return {"ok": False, "description": "empty"}
    if len(body) > 3500:
        body = body[:3497] + "..."
    params: dict[str, Any] = {
        "chat_id": chat_id,
        "message_id": int(message_id),
        "text": body,
    }
    if parse_mode:
        params["parse_mode"] = parse_mode
    return api_call(token, "editMessageText", params, timeout=45)


def split_chunks(text: str, cap: int = 3500) -> list[str]:
    raw = (text or "").strip()
    if not raw:
        return []
    if len(raw) <= cap:
        return [raw]
    parts: list[str] = []
    rest = raw
    while rest:
        if len(rest) <= cap:
            parts.append(rest)
            break
        chunk = rest[:cap]
        br = max(chunk.rfind("\n\n"), chunk.rfind("\n"), chunk.rfind(". "))
        if br < cap // 3:
            cut = cap
        elif chunk[br : br + 1] == ".":
            cut = br + 1
        else:
            cut = br
        piece = rest[:cut].strip()
        if piece:
            parts.append(piece)
        rest = rest[cut:].strip()
    return parts or [raw[:cap]]


def send_message(
    token: str,
    chat_id: int | str,
    text: str,
    reply_to: int | None = None,
    parse_mode: str | None = None,
    message_thread_id: int | None = None,
) -> dict[str, Any]:
    chunks = split_chunks(text)
    if not chunks:
        return {"ok": False, "description": "empty"}
    last: dict[str, Any] = {"ok": False}
    reply = reply_to
    for chunk in chunks:
        params: dict[str, Any] = {"chat_id": chat_id, "text": chunk}
        if reply is not None:
            params["reply_to_message_id"] = reply
        if parse_mode:
            params["parse_mode"] = parse_mode
        if message_thread_id is not None:
            params["message_thread_id"] = int(message_thread_id)
        last = api_call(token, "sendMessage", params, timeout=45)
        if not last.get("ok"):
            desc = str(last.get("description") or "").lower()
            retried = dict(params)
            if "message_thread_id" in retried and "thread" in desc:
                retried.pop("message_thread_id", None)
                last = api_call(token, "sendMessage", retried, timeout=45)
            if not last.get("ok") and retried.get("reply_to_message_id") is not None:
                retried.pop("reply_to_message_id", None)
                last = api_call(token, "sendMessage", retried, timeout=45)
        if not last.get("ok"):
            print(
                f"telegram send fail {last.get('error_code')} {(last.get('description') or '')[:180]}",
                flush=True,
            )
        if last.get("ok") and isinstance(last.get("result"), dict):
            mid = last["result"].get("message_id")
            if mid is not None:
                reply = int(mid)
    return last


def _send_multipart(
    token: str,
    chat_id: int | str,
    file_path: Path,
    *,
    method: str,
    field: str,
    caption: str = "",
) -> dict[str, Any]:
    if not token:
        return {"ok": False, "description": "missing token"}
    path = Path(file_path)
    if not path.is_file():
        return {"ok": False, "description": "file missing"}
    if path.stat().st_size > 50 * 1024 * 1024:
        return {"ok": False, "description": "file exceeds 50MB Telegram cap"}

    boundary = "----CouncilBoundary7MA4YWxkTrZu0gW"
    filename = path.name
    ctype = mimetypes.guess_type(filename)[0] or "application/octet-stream"
    file_bytes = path.read_bytes()
    digest = hashlib.sha256(file_bytes).hexdigest()
    try:
        from . import transfer

        transfer.note("", path, digest)
        caption = transfer.caption_with_hash(caption or "", digest)
    except Exception:
        pass

    def part(
        name: str,
        value: bytes,
        filename: str | None = None,
        content_type: str | None = None,
    ) -> bytes:
        disp = f'Content-Disposition: form-data; name="{name}"'
        if filename:
            disp += f'; filename="{filename}"'
        chunks = [f"--{boundary}\r\n".encode(), f"{disp}\r\n".encode()]
        if content_type:
            chunks.append(f"Content-Type: {content_type}\r\n".encode())
        chunks.append(b"\r\n")
        chunks.append(value)
        chunks.append(b"\r\n")
        return b"".join(chunks)

    body = b"".join(
        [
            part("chat_id", str(chat_id).encode()),
            part("caption", (caption[:1024] if caption else "").encode("utf-8")),
            part(field, file_bytes, filename=filename, content_type=ctype),
            f"--{boundary}--\r\n".encode(),
        ]
    )
    url = _url(token, method)
    req = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=120, context=_ctx()) as resp:
            return json.loads(resp.read().decode("utf-8", errors="replace"))
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", errors="replace") if e.fp else ""
        try:
            return json.loads(err_body) if err_body else {"ok": False, "description": str(e)}
        except json.JSONDecodeError:
            return {"ok": False, "description": err_body or str(e)}
    except Exception as e:  # noqa: BLE001
        return {"ok": False, "description": str(e)}


def get_file(token: str, file_id: str) -> dict[str, Any]:
    return api_call(token, "getFile", {"file_id": file_id}, timeout=30)


def download_file(token: str, file_path: str, dest: Path, *, timeout: float = 120.0) -> Path | None:
    """Download a Telegram file_path from getFile into dest. Returns dest or None."""
    if not token or not file_path:
        return None
    url = f"{API}/file/bot{token}/{file_path.lstrip('/')}"
    dest = Path(dest)
    dest.parent.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(url, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=_ctx()) as resp:
            dest.write_bytes(resp.read())
        return dest if dest.is_file() and dest.stat().st_size > 0 else None
    except Exception:
        return None


def largest_photo_file_id(message: dict[str, Any]) -> str | None:
    photos = message.get("photo")
    if not isinstance(photos, list) or not photos:
        return None
    best = None
    best_area = -1
    for p in photos:
        if not isinstance(p, dict):
            continue
        w = int(p.get("width") or 0)
        h = int(p.get("height") or 0)
        area = w * h
        fid = str(p.get("file_id") or "")
        if fid and area >= best_area:
            best = fid
            best_area = area
    return best


def send_document(
    token: str,
    chat_id: int | str,
    file_path: Path,
    caption: str = "",
) -> dict[str, Any]:
    return _send_multipart(
        token, chat_id, file_path, method="sendDocument", field="document", caption=caption
    )


def send_audio(
    token: str,
    chat_id: int | str,
    file_path: Path,
    caption: str = "",
) -> dict[str, Any]:
    """MP3/M4A via sendAudio; WAV and the rest as a document so Telegram still takes them."""
    path = Path(file_path)
    suf = path.suffix.lower()
    if suf in {".mp3", ".m4a"}:
        out = _send_multipart(
            token, chat_id, path, method="sendAudio", field="audio", caption=caption
        )
        if out.get("ok"):
            return out
    return send_document(token, chat_id, path, caption=caption)


def get_chat_member(token: str, chat_id: int | str, user_id: int) -> dict[str, Any]:
    return api_call(
        token,
        "getChatMember",
        {"chat_id": chat_id, "user_id": user_id},
        timeout=30,
    )


def delete_message(token: str, chat_id: int | str, message_id: int) -> dict[str, Any]:
    return api_call(
        token,
        "deleteMessage",
        {"chat_id": chat_id, "message_id": message_id},
        timeout=30,
    )


def ban_chat_member(
    token: str,
    chat_id: int | str,
    user_id: int,
    until_date: int | None = None,
    revoke_messages: bool = True,
) -> dict[str, Any]:
    params: dict[str, Any] = {
        "chat_id": chat_id,
        "user_id": user_id,
        "revoke_messages": revoke_messages,
    }
    if until_date is not None:
        params["until_date"] = until_date
    return api_call(token, "banChatMember", params, timeout=30)


def unban_chat_member(
    token: str, chat_id: int | str, user_id: int, only_if_banned: bool = True
) -> dict[str, Any]:
    return api_call(
        token,
        "unbanChatMember",
        {"chat_id": chat_id, "user_id": user_id, "only_if_banned": only_if_banned},
        timeout=30,
    )


def restrict_chat_member(
    token: str,
    chat_id: int | str,
    user_id: int,
    until_date: int | None = None,
    *,
    can_send_messages: bool = False,
) -> dict[str, Any]:
    """Mute-style restrict: strip send rights when can_send_messages is False."""
    permissions = {
        "can_send_messages": can_send_messages,
        "can_send_audios": can_send_messages,
        "can_send_documents": can_send_messages,
        "can_send_photos": can_send_messages,
        "can_send_videos": can_send_messages,
        "can_send_video_notes": can_send_messages,
        "can_send_voice_notes": can_send_messages,
        "can_send_polls": can_send_messages,
        "can_send_other_messages": can_send_messages,
        "can_add_web_page_previews": can_send_messages,
    }
    params: dict[str, Any] = {
        "chat_id": chat_id,
        "user_id": user_id,
        "permissions": json.dumps(permissions),
    }
    if until_date is not None:
        params["until_date"] = until_date
    return api_call(token, "restrictChatMember", params, timeout=30)


def set_my_commands(
    token: str,
    commands: list[dict[str, str]],
    scope: dict[str, Any] | None = None,
) -> dict[str, Any]:
    params: dict[str, Any] = {"commands": commands}
    if scope:
        params["scope"] = scope
    return api_call(token, "setMyCommands", params, timeout=30)


def delete_my_commands(
    token: str,
    scope: dict[str, Any] | None = None,
) -> dict[str, Any]:
    params: dict[str, Any] = {}
    if scope:
        params["scope"] = scope
    return api_call(token, "deleteMyCommands", params, timeout=30)


def set_message_reaction(
    token: str,
    chat_id: int | str,
    message_id: int,
    emoji: str | None,
) -> dict[str, Any]:
    """emoji None clears reactions. Uses Bot API setMessageReaction."""
    if emoji:
        reaction = [{"type": "emoji", "emoji": emoji}]
    else:
        reaction = []
    return api_call(
        token,
        "setMessageReaction",
        {
            "chat_id": chat_id,
            "message_id": message_id,
            "reaction": reaction,
        },
        timeout=20,
    )
