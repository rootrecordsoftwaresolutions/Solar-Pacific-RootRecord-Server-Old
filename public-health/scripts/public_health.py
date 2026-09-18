#!/usr/bin/env python3
"""Periodic check that status boards and Root Record Radio stay reachable.

Probes public HTTPS + local origin. Alerts the council group on failure.
While AVA Console is up, tries a gentle radio wake when the player is dark.
Never starts origin/Ollama when the console is closed.
"""
from __future__ import annotations

import json
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

STORE = Path.home() / ".ollama" / "skills" / "public-health" / "store"
STATE_PATH = STORE / "state.json"
CONSOLE_UP = Path.home() / ".ollama" / "skills" / "state" / "store" / "ava-console-up"
SECRETS = Path.home() / ".config" / "ava-council" / "secrets.env"
ALERT_COOLDOWN_S = 30 * 60

# Critical surfaces visitors hit.
CHECKS: list[dict[str, Any]] = [
    {
        "id": "status-cloud",
        "url": "https://rootrecord.cloud/status",
        "kind": "html",
        "must_contain": ["status", "Status", "solar", "Solar", "Root"],
        "critical": True,
    },
    {
        "id": "status-api-cloud",
        "url": "https://rootrecord.cloud/api/status",
        "kind": "json",
        "json_keys": ["ok"],
        "critical": True,
    },
    {
        "id": "status-avaivy",
        "url": "https://www.avaivy.cloud/status",
        "kind": "html",
        "must_contain": ["status", "Status", "Root", "Ava"],
        "critical": True,
    },
    {
        "id": "status-api-avaivy",
        "url": "https://www.avaivy.cloud/api/status",
        "kind": "json",
        "json_keys": ["ok"],
        "critical": True,
    },
    {
        "id": "status-online",
        "url": "https://rootrecord.online/status",
        "kind": "html",
        "must_contain": ["status", "Status", "Root"],
        "critical": True,
    },
    {
        "id": "status-api-online",
        "url": "https://rootrecord.online/api/status",
        "kind": "json",
        "json_keys": ["ok"],
        "critical": True,
    },
    {
        "id": "radio-page-cloud",
        "url": "https://rootrecord.cloud/radio",
        "kind": "html",
        "must_contain": ["Radio", "radio"],
        "critical": True,
    },
    {
        "id": "radio-now-cloud",
        "url": "https://rootrecord.cloud/api/radio/now",
        "kind": "json",
        "json_keys": ["ok"],
        "critical": True,
    },
    {
        # Edge whitelist lag — keep soft until rootrecord-cloud is redeployed.
        "id": "radio-status-cloud",
        "url": "https://rootrecord.cloud/api/radio/status",
        "kind": "json",
        "json_keys": ["ok"],
        "critical": False,
    },
    {
        "id": "radio-page-origin",
        "url": "https://origin.avaivy.cloud/radio",
        "kind": "html",
        "must_contain": ["Radio", "radio"],
        "critical": True,
    },
    {
        "id": "radio-status-origin",
        "url": "https://origin.avaivy.cloud/api/radio/status",
        "kind": "json",
        "json_keys": ["ok"],
        "critical": True,
    },
    {
        "id": "status-local",
        "url": "http://127.0.0.1:8787/status",
        "kind": "html",
        "must_contain": ["status", "Status", "Root"],
        "critical": True,
        "console_only": True,
    },
    {
        "id": "radio-local",
        "url": "http://127.0.0.1:8787/radio",
        "kind": "html",
        "must_contain": ["Radio", "radio"],
        "critical": True,
        "console_only": True,
    },
    {
        "id": "radio-status-local",
        "url": "http://127.0.0.1:8787/api/radio/status",
        "kind": "json",
        "json_keys": ["ok"],
        "critical": True,
        "console_only": True,
    },
]


def _load_env() -> dict[str, str]:
    out: dict[str, str] = {}
    if not SECRETS.is_file():
        return out
    for line in SECRETS.read_text(encoding="utf-8", errors="replace").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, _, v = line.partition("=")
        out[k.strip()] = v.strip().strip('"').strip("'")
    return out


def _console_up() -> bool:
    if CONSOLE_UP.is_file():
        return True
    script = Path.home() / ".ollama" / "skills" / "idle-stop" / "scripts" / "console-up.sh"
    if script.is_file():
        try:
            return subprocess.call(["bash", str(script)], timeout=5) == 0
        except Exception:
            return False
    return False


def _fetch(
    url: str,
    *,
    timeout: float = 6.0,
    method: str = "GET",
    data: bytes | None = None,
    retries: int = 1,
) -> tuple[int, bytes, str]:
    last: tuple[int, bytes, str] = (0, b"", "")
    for attempt in range(max(1, retries + 1)):
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("User-Agent", "RootRecord-PublicHealth/1.0")
        req.add_header("Accept", "*/*")
        if data is not None:
            req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                body = resp.read()
                ctype = str(resp.headers.get("Content-Type") or "")
                code = int(getattr(resp, "status", 200) or 200)
                last = (code, body, ctype)
                if code in (403, 408, 425, 429, 502, 503, 504) and attempt < retries:
                    time.sleep(0.6 * (attempt + 1))
                    continue
                return last
        except urllib.error.HTTPError as e:
            body = e.read() if e.fp else b""
            last = (int(e.code), body, "")
            if int(e.code) in (403, 408, 425, 429, 502, 503, 504) and attempt < retries:
                time.sleep(0.6 * (attempt + 1))
                continue
            return last
        except Exception as e:  # noqa: BLE001
            last = (0, str(e).encode(), "")
            if attempt < retries:
                time.sleep(0.6 * (attempt + 1))
                continue
            return last
    return last


def _probe(row: dict[str, Any]) -> dict[str, Any]:
    url = str(row["url"])
    code, body, ctype = _fetch(url)
    ok = 200 <= code < 400
    detail = f"http={code}"
    text = body.decode("utf-8", errors="replace") if body else ""
    if ok and row.get("kind") == "html":
        needles = row.get("must_contain") or []
        if needles and not any(n in text for n in needles):
            ok = False
            detail = f"http={code} missing_marker"
        if "text/html" not in ctype and code == 200 and len(text) < 200:
            # Some edges return empty
            pass
    if ok and row.get("kind") == "json":
        try:
            data = json.loads(text)
        except json.JSONDecodeError:
            ok = False
            detail = f"http={code} not_json"
            data = None
        if ok and isinstance(data, dict):
            for key in row.get("json_keys") or []:
                if key == "ok":
                    if "ok" in data:
                        if data.get("ok") is False:
                            ok = False
                            detail = f"http={code} ok=false"
                            break
                        continue
                    # status boards may omit ok — accept known host/radio fields
                    if not any(
                        k in data
                        for k in (
                            "cpu_pct",
                            "config",
                            "on_air",
                            "serving_public",
                            "local_playback",
                            "host",
                            "uptime_s",
                        )
                    ):
                        ok = False
                        detail = f"http={code} empty_status"
                        break
                    continue
                if key not in data:
                    ok = False
                    detail = f"http={code} missing:{key}"
                    break
    return {
        "id": row["id"],
        "url": url,
        "ok": ok,
        "detail": detail,
        "critical": bool(row.get("critical")),
        "bytes": len(body),
    }


def _radio_heal_local() -> dict[str, Any]:
    """Wake the desk radio and honor sticky on-air while the console is up."""
    out: dict[str, Any] = {"wake": None, "on_air": None, "tunnel": None}
    # If the public tunnel is dark, bring cloudflared back (console-owned only).
    try:
        t_code, _, _ = _fetch("https://origin.avaivy.cloud/health", timeout=6.0, retries=0)
        if t_code != 200:
            script = Path.home() / ".ollama" / "skills" / "public-edge" / "scripts" / "api-tunnel.sh"
            if script.is_file() and not _cloudflared_up():
                subprocess.Popen(  # noqa: S603
                    ["bash", str(script)],
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    start_new_session=True,
                )
                out["tunnel"] = {"started": True, "was_http": t_code}
                time.sleep(2.5)
            else:
                out["tunnel"] = {"started": False, "was_http": t_code, "cloudflared": _cloudflared_up()}
        else:
            out["tunnel"] = {"ok": True}
    except Exception as e:  # noqa: BLE001
        out["tunnel"] = {"error": f"{type(e).__name__}: {e}"}

    code, body, _ = _fetch(
        "http://127.0.0.1:8787/api/radio/wake",
        method="POST",
        data=b"{}",
        timeout=15,
        retries=0,
    )
    out["wake"] = {"http": code}
    try:
        st = json.loads(body.decode("utf-8", errors="replace")) if body else {}
    except json.JSONDecodeError:
        st = {}
    out["wake"]["on_air"] = st.get("on_air")
    out["wake"]["visitor_awake"] = st.get("visitor_awake")
    # If sticky on-air is armed, make sure public listen is actually on.
    if st.get("on_air_sticky") and not st.get("on_air"):
        code2, body2, _ = _fetch(
            "http://127.0.0.1:8787/api/radio",
            method="POST",
            data=json.dumps({"on_air": True}).encode(),
            timeout=20,
            retries=0,
        )
        out["on_air"] = {"http": code2}
        try:
            st2 = json.loads(body2.decode("utf-8", errors="replace")) if body2 else {}
            out["on_air"]["on_air"] = st2.get("on_air")
        except json.JSONDecodeError:
            pass
    return out


def _local_origin_ok() -> bool:
    code, _, _ = _fetch("http://127.0.0.1:8787/health", timeout=3.0, retries=0)
    return code == 200


def _recycle_origin() -> dict[str, Any]:
    """Ask launch to restart a wedged :8787 (console recycle loop)."""
    script = Path.home() / ".ollama" / "skills" / "recycle-origin" / "scripts" / "recycle-origin.sh"
    if not script.is_file():
        return {"ok": False, "detail": "no_script"}
    try:
        completed = subprocess.run(  # noqa: S603
            ["bash", str(script)],
            timeout=20,
            capture_output=True,
            text=True,
        )
        # launch.sh restarts uvicorn on non-zero exit; boot needs a few seconds.
        time.sleep(8.0)
        return {
            "ok": completed.returncode == 0,
            "code": completed.returncode,
            "healthy_after": _local_origin_ok(),
        }
    except Exception as e:  # noqa: BLE001
        return {"ok": False, "detail": f"{type(e).__name__}: {e}"}


def _cloudflared_up() -> bool:
    try:
        return subprocess.call(["pgrep", "-x", "cloudflared"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL) == 0
    except Exception:
        return False


def _load_state() -> dict[str, Any]:
    if STATE_PATH.is_file():
        try:
            raw = json.loads(STATE_PATH.read_text(encoding="utf-8"))
            if isinstance(raw, dict):
                return raw
        except Exception:
            pass
    return {}


def _save_state(st: dict[str, Any]) -> None:
    STORE.mkdir(parents=True, exist_ok=True)
    STATE_PATH.write_text(json.dumps(st, indent=2) + "\n", encoding="utf-8")


def _alert(env: dict[str, str], text: str) -> dict[str, Any]:
    tok = env.get("TELEGRAM_AVA_TOKEN") or ""
    chat = ""
    try:
        sj = Path.home() / ".config" / "ava-council" / "state.json"
        if sj.is_file():
            chat = str(json.loads(sj.read_text()).get("group_chat_id") or "")
    except Exception:
        chat = ""
    chat = chat or env.get("TELEGRAM_GROUP_CHAT_ID") or ""
    if not tok or not chat:
        return {"ok": False, "detail": "no_token_or_chat"}
    payload = json.dumps({"chat_id": chat, "text": text[:3500]}).encode()
    code, body, _ = _fetch(
        f"https://api.telegram.org/bot{tok}/sendMessage",
        method="POST",
        data=payload,
        timeout=15,
    )
    try:
        data = json.loads(body.decode("utf-8", errors="replace")) if body else {}
    except json.JSONDecodeError:
        data = {}
    return {"ok": bool(data.get("ok")) or (200 <= code < 300), "http": code}


def check(*, alert: bool = True, heal: bool = True) -> dict[str, Any]:
    console = _console_up()
    recycle_out: dict[str, Any] | None = None
    local_ok = True
    if console:
        local_ok = _local_origin_ok()
        # Wedged accept-queue on :8787 takes the whole public door with it.
        # recycle-origin only kills listeners; launch.sh brings uvicorn back.
        if heal and not local_ok:
            recycle_out = _recycle_origin()
            local_ok = bool(recycle_out.get("healthy_after")) or _local_origin_ok()

    results: list[dict[str, Any]] = []
    just_recycled = bool(recycle_out and recycle_out.get("ok"))
    for row in CHECKS:
        if row.get("console_only") and not console:
            continue
        url = str(row.get("url") or "")
        # Do not pile HTTPS probes onto a dark tunnel — that is how Recv-Q fills.
        if console and not local_ok and (
            "origin.avaivy.cloud" in url or url.startswith("http://127.0.0.1:8787")
        ):
            results.append(
                {
                    "id": row["id"],
                    "url": url,
                    "ok": False,
                    "detail": "origin_down",
                    "critical": bool(row.get("critical")),
                    "bytes": 0,
                }
            )
            continue
        # Right after a recycle, skip public cloud probes this cycle — boot is busy.
        if just_recycled and url.startswith("https://") and "127.0.0.1" not in url:
            results.append(
                {
                    "id": row["id"],
                    "url": url,
                    "ok": True,
                    "detail": "skipped_post_recycle",
                    "critical": bool(row.get("critical")),
                    "bytes": 0,
                }
            )
            continue
        results.append(_probe(row))

    problems = [
        f"{r['id']}: {r['detail']} ({r['url']})"
        for r in results
        if not r.get("ok") and r.get("critical")
    ]
    soft = [
        f"{r['id']}: {r['detail']}"
        for r in results
        if not r.get("ok") and not r.get("critical")
    ]

    heal_out = None
    if heal and console and local_ok:
        try:
            heal_out = _radio_heal_local()
        except Exception as e:  # noqa: BLE001
            heal_out = {"error": f"{type(e).__name__}: {e}"}
        for row in CHECKS:
            if not str(row.get("id") or "").startswith("radio"):
                continue
            if row.get("console_only") and not console:
                continue
            fresh = _probe(row)
            for i, old in enumerate(results):
                if old.get("id") == fresh.get("id"):
                    results[i] = fresh
                    break
        problems = [
            f"{r['id']}: {r['detail']} ({r['url']})"
            for r in results
            if not r.get("ok") and r.get("critical")
        ]
    if recycle_out is not None:
        heal_out = {"recycle_origin": recycle_out, **(heal_out or {})}

    ok = not problems
    report: dict[str, Any] = {
        "ok": ok,
        "ts": time.time(),
        "console": console,
        "local_ok": local_ok,
        "results": results,
        "problems": problems,
        "soft": soft,
        "heal": heal_out,
    }

    st = _load_state()
    st["last"] = report
    st["last_ok"] = time.time() if ok else st.get("last_ok")
    st["last_fail"] = None if ok else time.time()
    st["last_problems"] = problems

    alerted = False
    if alert and problems:
        last_alert = float(st.get("last_alert_ts") or 0)
        if time.time() - last_alert >= ALERT_COOLDOWN_S:
            msg = (
                "Public health — status / radio not fully up:\n"
                + "\n".join(f"• {p}" for p in problems[:10])
            )
            sent = _alert(_load_env(), msg)
            st["last_alert"] = sent
            if sent.get("ok"):
                st["last_alert_ts"] = time.time()
                alerted = True
    report["alerted"] = alerted
    _save_state(st)
    STORE.mkdir(parents=True, exist_ok=True)
    (STORE / "latest.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return report


def main(argv: list[str] | None = None) -> int:
    import argparse

    p = argparse.ArgumentParser(description="Status + radio public health")
    p.add_argument("--no-alert", action="store_true")
    p.add_argument("--no-heal", action="store_true")
    p.add_argument("--json", action="store_true")
    args = p.parse_args(argv)
    report = check(alert=not args.no_alert, heal=not args.no_heal)
    if args.json:
        print(json.dumps(report, indent=2))
    else:
        print(f"public-health {'OK' if report.get('ok') else 'FAIL'}")
        for p in report.get("problems") or []:
            print(f"  - {p}")
        for s in report.get("soft") or []:
            print(f"  ~ {s}")
        print(f"  console={report.get('console')} checks={len(report.get('results') or [])}")
    return 0 if report.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
