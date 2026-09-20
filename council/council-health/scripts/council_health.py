#!/usr/bin/env python3
"""Periodic check that Ava/Bruce/Carly council systems can actually talk.

Gates everyday chat on FastFlowLM (NPU), not Ollama GGUF. Ollama is optional
(vision/coder). Never starts origin/Ollama/FLM when AVA Console is closed.
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

STORE = Path.home() / ".ollama" / "skills" / "council-health" / "store"
STATE_PATH = STORE / "state.json"
LOG_PATH = Path.home() / ".ollama" / "skills" / "logs" / "store" / "ava-council.log"
CONSOLE_UP = Path.home() / ".ollama" / "skills" / "state" / "store" / "ava-console-up"
SECRETS = Path.home() / ".config" / "ava-council" / "secrets.env"
CHAT_LOG = Path.home() / ".config" / "ava-council" / "chat.jsonl"
ALERT_COOLDOWN_S = 30 * 60
OUTBOUND_STALE_S = 45 * 60


def _load_env() -> dict[str, str]:
    out: dict[str, str] = {}
    if not SECRETS.is_file():
        return out
    for line in SECRETS.read_text(encoding="utf-8", errors="replace").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, _, v = line.partition("=")
        v = v.strip().strip('"').strip("'")
        out[k.strip()] = v
    return out


def _http_json(url: str, timeout: float = 4.0) -> tuple[bool, Any]:
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            return True, json.loads(body) if body else {}
    except Exception as e:  # noqa: BLE001
        return False, f"{type(e).__name__}: {e}"


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


def _council_pids() -> list[int]:
    pids: list[int] = []
    pid_file = Path.home() / ".ollama" / "skills" / "state" / "store" / "ava-council.pid"
    if pid_file.is_file():
        try:
            raw = pid_file.read_text(encoding="utf-8").strip()
            if raw.isdigit() and Path(f"/proc/{raw}").exists():
                pids.append(int(raw))
        except Exception:
            pass
    try:
        out = subprocess.check_output(["ps", "-eo", "pid,args"], text=True, timeout=5)
    except Exception:
        return sorted(set(pids))
    for line in out.splitlines():
        if "apps.council" not in line:
            continue
        if "council_health" in line or "council-health" in line:
            continue
        parts = line.split(None, 1)
        if not parts:
            continue
        try:
            pid = int(parts[0])
        except ValueError:
            continue
        pids.append(pid)
    return sorted(set(pids))


def _get_me(token: str) -> dict[str, Any]:
    if not token:
        return {"ok": False, "detail": "no_token"}
    ok, body = _http_json(f"https://api.telegram.org/bot{token}/getMe", timeout=8)
    if not ok:
        return {"ok": False, "detail": str(body)[:120]}
    if isinstance(body, dict) and body.get("ok"):
        user = body.get("result") or {}
        return {"ok": True, "username": user.get("username")}
    return {"ok": False, "detail": "getMe_false"}


def _tail_log_issues(path: Path, *, window_s: int = 600) -> dict[str, Any]:
    if not path.is_file():
        return {"ok": True, "detail": "no_log"}
    try:
        data = path.read_bytes()
        text = data[-120_000:].decode("utf-8", errors="replace")
    except Exception as e:  # noqa: BLE001
        return {"ok": False, "detail": f"log_read:{type(e).__name__}"}
    lines = text.splitlines()
    # Only score since the latest listen boot — older 409 storms must not keep failing forever.
    start = 0
    for i, ln in enumerate(lines):
        if "ava-council listening" in ln:
            start = i
    recent = lines[start:]
    conflict = sum(1 for ln in recent if "409 Conflict" in ln)
    voices_drop = sum(1 for ln in recent if "voices down" in ln)
    silent = sum(1 for ln in recent if "addressed=none reason=silence" in ln)
    inbound = sum(1 for ln in recent if ln.startswith("inbound voice="))
    addressed = sum(1 for ln in recent if ln.startswith("addressed="))
    burst = sum(1 for ln in recent if ln.startswith("burst ingest"))
    ok = conflict < 5
    return {
        "ok": ok,
        "conflict_409": conflict,
        "voices_drop": voices_drop,
        "inbound": inbound,
        "addressed": addressed,
        "burst": burst,
        "silence": silent,
        "window_hint_s": window_s,
        "since_listen": True,
    }


def _last_outbound_age_s() -> float | None:
    if not CHAT_LOG.is_file():
        return None
    try:
        # Scan last 200 lines for dir=out
        data = CHAT_LOG.read_bytes()[-120_000:].decode("utf-8", errors="replace")
    except Exception:
        return None
    last_ts: float | None = None
    for line in data.splitlines():
        if '"dir": "out"' not in line and '"dir":"out"' not in line:
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError:
            m = re.search(r'"ts"\s*:\s*([0-9.]+)', line)
            if m:
                last_ts = float(m.group(1))
            continue
        if row.get("dir") == "out" and row.get("ts"):
            try:
                last_ts = float(row["ts"])
            except (TypeError, ValueError):
                pass
    if last_ts is None:
        return None
    return max(0.0, time.time() - last_ts)


def _flm_chat_probe() -> dict[str, Any]:
    url = (os.getenv("AVA_FLM_URL") or "http://127.0.0.1:52625").rstrip("/") + "/v1/chat/completions"
    model = (os.getenv("AVA_FLM_MODEL") or "llama3.2:3b").strip()
    payload = json.dumps(
        {
            "model": model,
            "messages": [{"role": "user", "content": "Reply with OK only."}],
            "max_tokens": 8,
        }
    ).encode("utf-8")
    req = urllib.request.Request(
        url, data=payload, headers={"Content-Type": "application/json"}, method="POST"
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            body = json.loads(resp.read().decode("utf-8", errors="replace"))
        content = (
            ((body.get("choices") or [{}])[0].get("message") or {}).get("content") or ""
        ).strip()
        return {"ok": bool(content), "preview": content[:40]}
    except Exception as e:  # noqa: BLE001
        return {"ok": False, "detail": f"{type(e).__name__}: {e}"[:120]}


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


def _alert(cfg_env: dict[str, str], text: str, *, group_id: str | None) -> dict[str, Any]:
    tok = cfg_env.get("TELEGRAM_AVA_TOKEN") or ""
    chat = group_id or cfg_env.get("TELEGRAM_GROUP_CHAT_ID") or ""
    if not tok or not chat:
        return {"ok": False, "detail": "no_token_or_chat"}
    payload = json.dumps({"chat_id": chat, "text": text[:3500]}).encode("utf-8")
    req = urllib.request.Request(
        f"https://api.telegram.org/bot{tok}/sendMessage",
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=12) as resp:
            body = json.loads(resp.read().decode("utf-8", errors="replace"))
        return {"ok": bool(body.get("ok")), "detail": None}
    except Exception as e:  # noqa: BLE001
        return {"ok": False, "detail": f"{type(e).__name__}: {e}"[:120]}


def check(*, alert: bool = True, probe_chat: bool = True) -> dict[str, Any]:
    env = _load_env()
    console = _console_up()
    pids = _council_pids()
    origin_ok, _ = _http_json("http://127.0.0.1:8787/health", timeout=3)
    recycle_out: dict[str, Any] | None = None
    if console and not origin_ok:
        script = Path.home() / ".ollama" / "skills" / "recycle-origin" / "scripts" / "recycle-origin.sh"
        if script.is_file():
            try:
                completed = subprocess.run(  # noqa: S603
                    ["bash", str(script)],
                    timeout=20,
                    capture_output=True,
                    text=True,
                )
                time.sleep(4.0)
                origin_ok, _ = _http_json("http://127.0.0.1:8787/health", timeout=3)
                recycle_out = {
                    "ok": completed.returncode == 0,
                    "healthy_after": bool(origin_ok),
                }
            except Exception as e:  # noqa: BLE001
                recycle_out = {"ok": False, "detail": f"{type(e).__name__}: {e}"[:120]}
    flm_ok, _ = _http_json(
        (os.getenv("AVA_FLM_URL") or "http://127.0.0.1:52625").rstrip("/") + "/v1/models",
        timeout=3,
    )
    ollama_ok, _ = _http_json("http://127.0.0.1:11434/api/tags", timeout=3)

    bots = {
        "ava": _get_me(env.get("TELEGRAM_AVA_TOKEN", "")),
        "bruce": _get_me(env.get("TELEGRAM_BRUCE_TOKEN", "")),
        "carly": _get_me(env.get("TELEGRAM_CARLY_TOKEN", "")),
    }
    log_issues = _tail_log_issues(LOG_PATH)
    outbound_age = _last_outbound_age_s()
    chat_probe = _flm_chat_probe() if (probe_chat and flm_ok and console) else {"ok": None, "detail": "skipped"}

    # Voices = FLM when NPU chat on (default)
    npu = os.getenv("AVA_NPU_CHAT", "1").strip().lower() not in {"0", "false", "off", "no"}
    voices_ok = bool(flm_ok) if npu else bool(ollama_ok)

    problems: list[str] = []
    if console:
        if len(pids) == 0:
            problems.append("council process missing (launch owns restart)")
        elif len(pids) > 1:
            problems.append(f"duplicate council pids={pids} (409 risk)")
        if not voices_ok:
            problems.append("voices down — FastFlowLM not answering" if npu else "voices down — Ollama down")
        if probe_chat and chat_probe.get("ok") is False:
            problems.append(f"FLM chat probe failed ({chat_probe.get('detail')})")
        if not origin_ok:
            problems.append("origin :8787 health failed")
        if not log_issues.get("ok"):
            problems.append(f"getUpdates 409×{log_issues.get('conflict_409')} in recent log")
        for name, row in bots.items():
            if not row.get("ok"):
                problems.append(f"{name} getMe failed ({row.get('detail')})")
        if outbound_age is not None and outbound_age > OUTBOUND_STALE_S and voices_ok:
            # After a fresh listen, inbound with zero addressed lines = swallow.
            if (
                int(log_issues.get("inbound") or 0) >= 3
                and int(log_issues.get("addressed") or 0) == 0
                and int(log_issues.get("voices_drop") or 0) == 0
            ):
                problems.append(
                    f"council may be swallowing chat (last outbound {int(outbound_age/60)}m ago; inbound without address)"
                )
            elif int(log_issues.get("voices_drop") or 0) >= 2:
                problems.append("voices down drops in log — chat gate failing")
    else:
        # Console closed — desk should be idle. Flag only if council still running.
        if pids:
            problems.append(f"console closed but council still running pids={pids}")

    ok = not problems
    report: dict[str, Any] = {
        "ok": ok,
        "ts": time.time(),
        "console": console,
        "council_pids": pids,
        "origin": origin_ok,
        "recycle_origin": recycle_out,
        "flm": flm_ok,
        "ollama": ollama_ok,
        "voices": voices_ok,
        "bots": {k: {"ok": v.get("ok"), "username": v.get("username")} for k, v in bots.items()},
        "log": log_issues,
        "outbound_age_s": outbound_age,
        "chat_probe": chat_probe,
        "problems": problems,
    }

    st = _load_state()
    st["last"] = report
    st["last_ok"] = time.time() if ok else st.get("last_ok")
    st["last_fail"] = None if ok else time.time()
    st["last_problems"] = problems

    alerted = False
    if alert and problems and console:
        last_alert = float(st.get("last_alert_ts") or 0)
        if time.time() - last_alert >= ALERT_COOLDOWN_S:
            group = None
            try:
                sj = Path.home() / ".config" / "ava-council" / "state.json"
                if sj.is_file():
                    group = str(json.loads(sj.read_text()).get("group_chat_id") or "") or None
            except Exception:
                group = None
            msg = (
                "Council health check — systems not fully functional:\n"
                + "\n".join(f"• {p}" for p in problems[:8])
                + "\n(Carly/Ava/Bruce periodic check)"
            )
            sent = _alert(env, msg, group_id=group)
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

    p = argparse.ArgumentParser(description="Council Ava/Bruce/Carly health check")
    p.add_argument("--no-alert", action="store_true")
    p.add_argument("--no-probe", action="store_true", help="Skip FLM chat completion probe")
    p.add_argument("--json", action="store_true")
    args = p.parse_args(argv)
    report = check(alert=not args.no_alert, probe_chat=not args.no_probe)
    if args.json:
        print(json.dumps(report, indent=2))
    else:
        status = "OK" if report.get("ok") else "FAIL"
        print(f"council-health {status}")
        for prob in report.get("problems") or []:
            print(f"  - {prob}")
        bots = report.get("bots") or {}
        print(
            f"  console={report.get('console')} pids={report.get('council_pids')} "
            f"voices={report.get('voices')} flm={report.get('flm')} ollama={report.get('ollama')} "
            f"origin={report.get('origin')}"
        )
        print(
            "  bots "
            + " ".join(
                f"{k}={'@' + str(v.get('username')) if v.get('ok') else 'DOWN'}"
                for k, v in bots.items()
            )
        )
    return 0 if report.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
