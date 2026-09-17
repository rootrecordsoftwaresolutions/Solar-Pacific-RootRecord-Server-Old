"""Pull unMineable LTC pending withdraw balance. Read-only. Never payout."""
from __future__ import annotations

import hashlib
import hmac
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
API_BASE = "https://api.unmineable.dev"
COIN = "LTC"
DEFAULT_MIN_WITHDRAW = 0.00075
OUT_PATH = Path.home() / ".ollama" / "skills" / "ltc-node" / "state" / "pending_balance.json"
ENV_CANDIDATES = (
    Path(os.getenv("AVA_HOME") or "") / ".env" if os.getenv("AVA_HOME") else None,
    Path("/home/rootrecord/.ollama/skills/origin/.env"),
    Path.cwd() / ".env",
)


def _load_central_env() -> None:
    seen: set[Path] = set()
    for raw in ENV_CANDIDATES:
        if raw is None:
            continue
        path = raw.expanduser()
        try:
            path = path.resolve()
        except OSError:
            continue
        if path in seen or not path.is_file():
            continue
        seen.add(path)
        try:
            text = path.read_text(encoding="utf-8")
        except OSError:
            continue
        for line in text.splitlines():
            s = line.strip()
            if not s or s.startswith("#") or "=" not in s:
                continue
            key, _, val = s.partition("=")
            key = key.strip()
            val = val.strip().strip("'").strip('"')
            if key and key not in os.environ:
                os.environ[key] = val


def _keys() -> tuple[str, str]:
    pub = (
        os.getenv("UNMINEABLE_API_KEY")
        or os.getenv("UNMINEABLE_PUBLIC_KEY")
        or ""
    ).strip()
    sec = (
        os.getenv("UNMINEABLE_API_SECRET")
        or os.getenv("UNMINEABLE_SECRET")
        or ""
    ).strip()
    return pub, sec


def min_withdraw() -> float:
    raw = (os.getenv("UNMINEABLE_LTC_MIN_WITHDRAW") or "").strip()
    if raw:
        try:
            v = float(raw)
            if v > 0:
                return v
        except ValueError:
            pass
    return DEFAULT_MIN_WITHDRAW


def configured() -> bool:
    pub, sec = _keys()
    return pub.startswith("upk_") and sec.startswith("usk_")


def _sign(method: str, path: str, query: str, body: bytes, ts: str, secret: str) -> str:
    body_hash = hashlib.sha256(body or b"").hexdigest()
    payload = "\n".join((method.upper(), path, query or "", ts, body_hash))
    return hmac.new(secret.encode("utf-8"), payload.encode("utf-8"), hashlib.sha256).hexdigest()


def _request(method: str, path: str, query: str = "") -> dict[str, Any]:
    pub, sec = _keys()
    ts = str(int(time.time() * 1000))
    sig = _sign(method, path, query, b"", ts, sec)
    url = API_BASE + path
    if query:
        url = url + "?" + query
    req = urllib.request.Request(
        url,
        method=method.upper(),
        headers={
            "Accept": "application/json",
            "x-user-api-key": pub,
            "x-user-api-timestamp": ts,
            "x-user-api-signature": sig,
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=25) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")[:400]
        raise RuntimeError(f"unmineable {path} {e.code}: {body}") from e
    except urllib.error.URLError as e:
        raise RuntimeError(f"unmineable {path} network: {e.reason}") from e
    try:
        data = json.loads(raw) if raw else {}
    except json.JSONDecodeError as e:
        raise RuntimeError(f"unmineable {path} not_json") from e
    return data if isinstance(data, dict) else {"value": data}


def _to_float(v: Any) -> float | None:
    if v is None or isinstance(v, bool):
        return None
    if isinstance(v, (int, float)):
        return float(v)
    if isinstance(v, str):
        s = v.strip().replace(",", "")
        if not s:
            return None
        try:
            return float(s)
        except ValueError:
            return None
    return None


def _data(payload: dict[str, Any]) -> dict[str, Any]:
    inner = payload.get("data")
    return inner if isinstance(inner, dict) else {}


def _ltc_rows(assets: dict[str, Any]) -> list[dict[str, Any]]:
    rows = _data(assets).get("list") or []
    out: list[dict[str, Any]] = []
    for item in rows:
        if not isinstance(item, dict):
            continue
        coin = str(item.get("coin") or item.get("coin_canonical") or "").upper()
        if coin == COIN:
            out.append(item)
    return out


def _pending_from(stats: dict[str, Any], assets: dict[str, Any]) -> tuple[float, str]:
    best: float | None = None
    for item in _ltc_rows(assets):
        amt = _to_float(item.get("amount"))
        if amt is None:
            continue
        if best is None or amt > best:
            best = amt
    if best is not None:
        return best, "unmineable_assets_list"
    amt = _to_float(_data(stats).get("balance"))
    return (amt or 0.0), "unmineable_assets_LTC_stats"


def _threshold_from(assets: dict[str, Any], withdraw: dict[str, Any]) -> float | None:
    for item in _ltc_rows(assets):
        for key in ("payment_threshold", "min_withdrawal_threshold"):
            n = _to_float(item.get(key))
            if n and n > 0:
                return n
    n = _to_float(_data(withdraw).get("payment_threshold"))
    if n and n > 0:
        return n
    return None


def _attach_wallet(base: dict[str, Any]) -> None:
    from ltc_status import address_balance, main_address

    base["main_wallet"] = main_address()
    try:
        base["wallet"] = address_balance()
    except Exception as e:
        base["wallet"] = {"ok": False, "error": "cli_attach_failed", "detail": str(e)[:120]}


def _council(base: dict[str, Any], vote: bool) -> None:
    if not vote:
        return
    try:
        from earnings_vote import council_block, maybe_vote

        maybe_vote(base)
        base["council"] = council_block(base)
    except Exception as e:
        base["council"] = {"ok": False, "detail": "vote_failed", "error": str(e)[:120]}


def snapshot_path() -> Path:
    return OUT_PATH


def _write(payload: dict[str, Any]) -> Path:
    path = snapshot_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    return path


def poll(*, vote: bool = True) -> dict[str, Any]:
    _load_central_env()
    now = datetime.now(HST)
    threshold = min_withdraw()
    base: dict[str, Any] = {
        "ok": False,
        "source": "unmineable_v1_assets_LTC",
        "coin": COIN,
        "pending_balance": None,
        "unit": "LTC",
        "min_auto_withdraw": threshold,
        "withdraw_ready": False,
        "percent_to_next_withdraw": 0.0,
        "fetched_at": now.isoformat(),
        "fetched_at_unix": int(time.time()),
        "path": str(OUT_PATH),
    }
    if not configured():
        base["detail"] = "not_configured"
        _attach_wallet(base)
        _council(base, vote)
        _write(base)
        return base
    try:
        stats = _request("GET", f"/v1/assets/{COIN}/stats")
        assets = _request("GET", "/v1/assets", "is_active=true")
        try:
            withdraw = _request("GET", f"/v1/assets/{COIN}/withdraw/address")
        except RuntimeError:
            withdraw = {}
    except Exception as e:
        base["detail"] = "poll_failed"
        base["error"] = str(e)[:180]
        _attach_wallet(base)
        _council(base, vote)
        _write(base)
        return base

    pending, pending_source = _pending_from(stats, assets)
    api_min = _threshold_from(assets, withdraw)
    if api_min and api_min > 0:
        threshold = api_min
        base["min_auto_withdraw"] = threshold
        base["min_auto_withdraw_source"] = "unmineable"
    else:
        base["min_auto_withdraw_source"] = "operator"

    pct = (pending / threshold) * 100.0 if threshold else 0.0
    base.update(
        {
            "ok": True,
            "pending_balance": round(pending, 8),
            "pending_source": pending_source,
            "withdraw_ready": pending >= threshold,
            "percent_to_next_withdraw": round(pct, 4),
            "detail": "ok",
        }
    )
    _attach_wallet(base)
    _council(base, vote)
    _write(base)
    return base


def main() -> int:
    out = poll()
    print(json.dumps(out, indent=2))
    return 0 if out.get("ok") or out.get("detail") == "not_configured" else 1


if __name__ == "__main__":
    raise SystemExit(main())
