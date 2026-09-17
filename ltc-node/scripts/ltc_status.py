"""Litecoin node + council main wallet. No key dumps. Sends only after equal vote."""
from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Any

_CAR = Path.home() / ".ollama" / "skills" / "ecoflow-river-car" / "scripts"
_BLE = Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "scripts"
for _p in (_CAR, _BLE):
    if str(_p) not in sys.path:
        sys.path.insert(0, str(_p))

CLI_CANDIDATES = (
    Path.home() / "Litecoin" / "bin" / "litecoin-cli",
    Path("/usr/local/bin/litecoin-cli"),
    Path("/usr/bin/litecoin-cli"),
)
DATADIR_DEFAULT = Path("/mnt/Archives/Litecoin")
DEFAULT_MAIN = "ltc1qkwzlku8zweyvn66ghs4xueajqnz2j52499gkcu"
FORBIDDEN = (
    "dumpprivkey",
    "dumpwallet",
    "importprivkey",
)
_ENV_LOADED = False


def _load_central_env() -> None:
    global _ENV_LOADED
    if _ENV_LOADED:
        return
    _ENV_LOADED = True
    for raw in (
        Path(os.getenv("AVA_HOME") or "") / ".env" if os.getenv("AVA_HOME") else None,
        Path("/home/rootrecord/.ollama/skills/origin/.env"),
        Path.cwd() / ".env",
    ):
        if raw is None or not raw.is_file():
            continue
        try:
            text = raw.read_text(encoding="utf-8")
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


def cli_path() -> Path | None:
    env = (os.getenv("AVA_LTC_CLI") or "").strip()
    if env:
        p = Path(env)
        if p.is_file() and os.access(p, os.X_OK):
            return p
    for p in CLI_CANDIDATES:
        if p.is_file() and os.access(p, os.X_OK):
            return p
    return None


def datadir() -> Path:
    env = (os.getenv("AVA_LTC_DATADIR") or "").strip()
    if env:
        return Path(env)
    return DATADIR_DEFAULT


def main_address() -> str:
    _load_central_env()
    return (os.getenv("LTC_MAIN_ADDRESS") or DEFAULT_MAIN).strip()


def _rpc(cmd: str, *params: Any, timeout: int = 20) -> dict[str, Any]:
    if cmd.lower() in FORBIDDEN:
        return {"ok": False, "error": "forbidden_rpc"}
    binary = cli_path()
    if not binary:
        return {"ok": False, "error": "cli_missing"}
    dd = datadir()
    argv = [str(binary)]
    if dd:
        argv.append(f"-datadir={dd}")
    argv.append(cmd)
    for p in params:
        if isinstance(p, (dict, list, bool)) or p is None:
            argv.append(json.dumps(p))
        else:
            argv.append(str(p))
    try:
        raw = subprocess.check_output(argv, text=True, timeout=timeout, stderr=subprocess.STDOUT)
    except FileNotFoundError:
        return {"ok": False, "error": "cli_missing"}
    except subprocess.CalledProcessError as e:
        return {"ok": False, "error": "rpc_failed", "detail": (e.output or "")[-400]}
    except subprocess.TimeoutExpired:
        return {"ok": False, "error": "rpc_timeout"}
    text = (raw or "").strip()
    if not text:
        return {"ok": True, "data": None}
    try:
        return {"ok": True, "data": json.loads(text)}
    except json.JSONDecodeError:
        try:
            return {"ok": True, "data": float(text)}
        except ValueError:
            return {"ok": True, "data": text}


def status() -> dict[str, Any]:
    disk: dict[str, Any] = {}
    try:
        from disk_session import snapshot as disk_snapshot

        disk = disk_snapshot()
    except Exception:
        disk = {
            "usb_or_sata_present": datadir().is_dir(),
            "chain_mounts": [],
            "nvme_only": not datadir().is_dir(),
        }
    cli = cli_path()
    dd = datadir()
    out: dict[str, Any] = {
        "ok": True,
        "cli": str(cli) if cli else None,
        "datadir_configured": str(dd),
        "datadir_present": dd.is_dir(),
        "disk": {
            "usb_or_sata_present": disk.get("usb_or_sata_present"),
            "chain_mounts": disk.get("chain_mounts"),
            "nvme_only": disk.get("nvme_only"),
        },
        "synced": False,
        "balance_allowed": False,
        "miner": False,
    }
    if not disk.get("usb_or_sata_present") and not dd.is_dir():
        out["blocked"] = "external_disk_off"
        out["next"] = "River car DC on, wait for the Litecoin volume, then retry."
        return out
    if not cli:
        out["blocked"] = "cli_missing"
        out["next"] = "Install/restore Litecoin/bin/litecoin-cli. Desktop file pointed at a missing path."
        return out
    info = _rpc("getblockchaininfo")
    if not info.get("ok"):
        out["blocked"] = info.get("error")
        out["rpc"] = {k: info.get(k) for k in ("error", "detail") if info.get(k)}
        return out
    data = info.get("data") if isinstance(info.get("data"), dict) else {}
    blocks = int(data.get("blocks") or 0)
    headers = int(data.get("headers") or 0)
    out["blocks"] = blocks
    out["headers"] = headers
    out["chain"] = data.get("chain")
    synced = bool(blocks) and blocks == headers and not bool(data.get("initialblockdownload"))
    out["synced"] = synced
    out["balance_allowed"] = synced
    if not synced:
        out["blocked"] = "unsynced"
        out["next"] = "Wait until blocks == headers before any balance read."
    return out


def _as_float(v: Any) -> float | None:
    if isinstance(v, bool) or v is None:
        return None
    if isinstance(v, (int, float)):
        return float(v)
    if isinstance(v, str):
        try:
            return float(v.strip())
        except ValueError:
            return None
    return None


def address_balance(addr: str | None = None) -> dict[str, Any]:
    """Confirmed + watch-only received for the council main address."""
    addr = (addr or main_address()).strip()
    st = status()
    out: dict[str, Any] = {
        "ok": False,
        "address": addr,
        "balance": None,
        "unspent": None,
        "status": {k: st.get(k) for k in ("synced", "blocks", "headers", "blocked", "cli")},
    }
    if not st.get("balance_allowed"):
        out["error"] = st.get("blocked") or "wait_for_sync"
        return out
    recv = _rpc("getreceivedbyaddress", addr, 0)
    if recv.get("ok"):
        n = _as_float(recv.get("data"))
        if n is not None:
            out["balance"] = round(n, 8)
            out["ok"] = True
            out["source"] = "getreceivedbyaddress"
    unspent = _rpc("listunspent", 0, 9999999, [addr])
    if unspent.get("ok") and isinstance(unspent.get("data"), list):
        total = 0.0
        for row in unspent["data"]:
            if isinstance(row, dict):
                amt = _as_float(row.get("amount"))
                if amt is not None:
                    total += amt
        out["unspent"] = round(total, 8)
        if not out.get("ok"):
            out["balance"] = out["unspent"]
            out["ok"] = True
            out["source"] = "listunspent"
    if not out.get("ok"):
        wallet = _rpc("getbalance")
        if wallet.get("ok"):
            n = _as_float(wallet.get("data"))
            if n is not None:
                out["wallet_getbalance"] = round(n, 8)
        out["error"] = recv.get("error") or unspent.get("error") or "address_not_in_wallet"
        if recv.get("detail"):
            out["detail"] = recv.get("detail")
    return out


def send(dest: str, amount: float) -> dict[str, Any]:
    """Broadcast from the node wallet after a passed equal council vote."""
    from earnings_vote import send_allowed

    allowed, motion = send_allowed()
    if not allowed:
        return {"ok": False, "error": "no_council_motion", "motion": motion}
    dest = (dest or "").strip()
    if not dest.startswith("ltc1") and not dest.startswith("L"):
        return {"ok": False, "error": "bad_dest"}
    try:
        amt = float(amount)
    except (TypeError, ValueError):
        return {"ok": False, "error": "bad_amount"}
    if amt <= 0:
        return {"ok": False, "error": "bad_amount"}
    st = status()
    if not st.get("balance_allowed"):
        return {"ok": False, "error": st.get("blocked") or "wait_for_sync", "status": st}
    got = _rpc("sendtoaddress", dest, amt, timeout=60)
    if not got.get("ok"):
        return {"ok": False, "error": got.get("error"), "detail": got.get("detail"), "motion": motion}
    return {"ok": True, "txid": got.get("data"), "dest": dest, "amount": amt, "motion": motion}


def balance() -> dict[str, Any]:
    st = status()
    if not st.get("balance_allowed"):
        return {"ok": False, "error": "wait_for_sync", "status": st}
    got = _rpc("getbalance")
    if not got.get("ok"):
        return {"ok": False, "error": got.get("error"), "status": st}
    addr = address_balance()
    return {
        "ok": True,
        "balance": got.get("data"),
        "main": addr,
        "status": {k: st.get(k) for k in ("synced", "blocks", "headers")},
    }


def main(argv: list[str] | None = None) -> int:
    args = list(argv if argv is not None else sys.argv[1:])
    if args[:1] == ["balance"]:
        print(json.dumps(balance(), indent=2))
        return 0
    if args[:1] == ["address"]:
        print(json.dumps(address_balance(args[1] if len(args) > 1 else None), indent=2))
        return 0
    if args[:1] == ["send"] and len(args) >= 3:
        print(json.dumps(send(args[1], float(args[2])), indent=2))
        return 0
    print(json.dumps(status(), indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
