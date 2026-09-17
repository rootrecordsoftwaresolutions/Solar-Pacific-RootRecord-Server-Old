#!/usr/bin/env python3
"""Ava Ops Bluetooth-first bridge using standard Linux socket (AF_BLUETOOTH) and D-Bus BlueZ Profile1.

Zero external Python dependencies needed (uses built-in socket + system dbus).
"""

from __future__ import annotations

import json
import os
import socket
import sys
import threading
import time
from typing import Any

HOST_API_BASE_URL = os.environ.get("AVA_OPS_HOST_API_BASE_URL", "http://127.0.0.1:8787")
HOST_TOKEN = os.environ.get("AVA_OPS_HOST_TOKEN", "")
BT_CHANNEL = int(os.environ.get("AVA_OPS_BT_CHANNEL", "6"))
BT_NAME = os.environ.get("AVA_OPS_BT_NAME", "Ava Ops")
SPP_UUID = "6f70735f-7265-6c61-7963-6f6e6e656374"


def json_error(detail: str, **extra: Any) -> str:
    payload = {"ok": False, "detail": detail}
    payload.update(extra)
    return json.dumps(payload, separators=(",", ":"))


def read_json_line(sock: socket.socket) -> dict[str, Any]:
    buffer = b""
    while True:
        chunk = sock.recv(4096)
        if not chunk:
            break
        buffer += chunk
        if b"\n" in chunk:
            break
        if len(buffer) > 65536:
            raise ValueError("request_too_large")
    if not buffer:
        raise ValueError("empty_request")
    text = buffer.decode("utf-8", errors="strict").strip()
    if not text:
        raise ValueError("empty_request")
    try:
        payload = json.loads(text)
    except json.JSONDecodeError as exc:
        raise ValueError(f"invalid_json: {exc.msg}") from exc
    if not isinstance(payload, dict):
        raise ValueError("request_not_object")
    return payload


def validate_request(request: dict[str, Any]) -> None:
    token = request.get("token")
    if HOST_TOKEN and token != HOST_TOKEN:
        raise PermissionError("invalid_token")
    if "op" not in request:
        raise ValueError("missing_op")


def forward_local_api(op: str, payload: Any) -> dict[str, Any]:
    path_map = {
        "servers": "/api/ops/servers",
        "perform": "/api/ops/perform",
        "rcon": "/api/ops/rcon",
        "dashboard": "/api/ops/mobile-dashboard",
        "features": "/api/ops/features",
        "set_features": "/api/ops/features",
        "idle_stop": "/api/ops/idle-stop",
    }
    path = path_map.get(op)
    if not path:
        raise ValueError(f"unsupported_op:{op}")

    import urllib.request

    method = "GET"
    if op in {"perform", "rcon", "set_features", "idle_stop"}:
        method = "POST"
    headers = {"Accept": "application/json"}
    data = None
    if method == "POST":
        data = json.dumps(payload or {}).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(
        HOST_API_BASE_URL.rstrip("/") + path,
        data=data,
        headers=headers,
        method=method,
    )
    timeout = 20 if op == "dashboard" else 8
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            body = response.read().decode("utf-8", errors="replace")
            if not body:
                return {"ok": True}
            parsed = json.loads(body)
            if isinstance(parsed, dict):
                return parsed
            return {"ok": True, "data": parsed}
    except Exception as exc:
        return {"ok": False, "detail": f"host_api_unreachable:{exc}"}


def _trim_dashboard_for_rfcomm(result: dict[str, Any]) -> dict[str, Any]:
    """Keep the phone board tiny even if origin still returns the desktop reports blob."""
    out = dict(result)
    reports = result.get("reports")
    if isinstance(reports, dict):
        current = reports.get("current") if isinstance(reports.get("current"), dict) else {}
        freshness = reports.get("freshness") if isinstance(reports.get("freshness"), dict) else {}
        due = reports.get("due_today")
        if due is None:
            due = reports.get("dueToday")
        if not isinstance(due, list):
            due = []
        out["reports"] = {
            "ok": bool(reports.get("ok")),
            "hstDay": reports.get("hstDay"),
            "current": {
                "exists": bool(current.get("exists")),
                "name": current.get("name"),
                "mtimeMs": current.get("mtimeMs"),
            },
            "due_today": due,
            "freshness": {"ok": freshness.get("ok")},
        }
    weather = out.get("weather")
    if isinstance(weather, dict) and weather.get("temperature_f") is not None and not isinstance(
        weather.get("temperature_f"), str
    ):
        out["weather"] = {**weather, "temperature_f": str(weather.get("temperature_f"))}
    return out


def handle_message(request: dict[str, Any]) -> str:
    validate_request(request)
    op = str(request["op"]).lower().strip()
    payload = request.get("payload") or {k: v for k, v in request.items() if k != "token"}
    result = forward_local_api(op, payload)
    if op == "dashboard" and isinstance(result, dict):
        result = _trim_dashboard_for_rfcomm(result)
    # Board payload uses `ok` for Ava health; do not treat that as a transport failure.
    transport_failed = result.get("ok") is False and not (
        op == "dashboard" and ("generated_at" in result or "weather" in result or "host" in result)
    )
    if transport_failed:
        return json_error(str(result.get("detail", "host_api_failed")))
    response = {"ok": True, "op": op}
    if isinstance(result, dict):
        response.update(result)
        if op == "dashboard":
            response["ok"] = True
            response["board_ok"] = bool(result.get("ok"))
    return json.dumps(response, separators=(",", ":")) + "\n"


def session_loop(client_sock: socket.socket, client_info: Any) -> None:
    print(f"Accepted Bluetooth connection from: {client_info}", flush=True)
    try:
        while True:
            request = read_json_line(client_sock)
            try:
                reply = handle_message(request)
            except PermissionError as exc:
                reply = json_error(str(exc))
            except ValueError as exc:
                reply = json_error(str(exc))
            except Exception as exc:
                reply = json_error(f"internal_error:{exc}")
            client_sock.sendall(reply.encode("utf-8"))
    except Exception as exc:
        print(f"Client disconnected: {exc}", flush=True)
    finally:
        try:
            client_sock.close()
        except Exception:
            pass


def register_sdp_profile() -> bool:
    """Let BlueZ own RFCOMM + SDP. Android UUID connects land in NewConnection, not a second bind."""
    try:
        import dbus
        import dbus.service
        import dbus.mainloop.glib
        from gi.repository import GLib

        dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
        bus = dbus.SystemBus()
        manager = dbus.Interface(
            bus.get_object("org.bluez", "/org/bluez"),
            "org.bluez.ProfileManager1",
        )

        class Profile(dbus.service.Object):
            @dbus.service.method("org.bluez.Profile1", in_signature="", out_signature="")
            def Release(self):
                pass

            @dbus.service.method("org.bluez.Profile1", in_signature="o", out_signature="")
            def RequestDisconnection(self, device):
                pass

            @dbus.service.method("org.bluez.Profile1", in_signature="oha{sv}", out_signature="")
            def NewConnection(self, device, fd, properties):
                unix_fd = fd.take() if hasattr(fd, "take") else int(fd)
                threading.Thread(
                    target=self._serve_fd,
                    args=(unix_fd, str(device)),
                    daemon=True,
                ).start()

            @staticmethod
            def _serve_fd(unix_fd: int, device: str) -> None:
                sock = socket.fromfd(unix_fd, socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
                try:
                    os.close(unix_fd)
                except OSError:
                    pass
                session_loop(sock, device)

        profile_path = "/rootrecord/ava/ops_profile"
        Profile(bus, profile_path)

        opts = {
            "Name": BT_NAME,
            "Role": "server",
            "Channel": dbus.UInt16(BT_CHANNEL),
            "Service": SPP_UUID,
            "RequireAuthentication": False,
            "RequireAuthorization": False,
            "AutoConnect": False,
        }
        try:
            manager.RegisterProfile(profile_path, SPP_UUID, opts)
        except Exception as exc:
            if "AlreadyExists" not in type(exc).__name__ and "already" not in str(exc).lower():
                raise
            try:
                manager.UnregisterProfile(profile_path)
            except Exception:
                pass
            manager.RegisterProfile(profile_path, SPP_UUID, opts)
        print(f"[BlueZ] Registered SDP Profile {SPP_UUID} on RFCOMM channel {BT_CHANNEL}", flush=True)

        loop = GLib.MainLoop()
        threading.Thread(target=loop.run, daemon=True).start()
        return True
    except Exception as e:
        print(f"[BlueZ] Note: D-Bus Profile registration skipped ({e}), falling back to direct RFCOMM bind.", flush=True)
        return False


def serve_bound_rfcomm() -> None:
    server_sock = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
    server_sock.bind((socket.BDADDR_ANY, BT_CHANNEL))
    server_sock.listen(1)
    print(f"Ava Ops Bluetooth bridge listening on RFCOMM channel {BT_CHANNEL} ({BT_NAME})", flush=True)
    try:
        while True:
            try:
                client_sock, client_info = server_sock.accept()
                session_loop(client_sock, client_info)
            except KeyboardInterrupt:
                raise
            except Exception as exc:
                print(f"Accept error: {exc}", flush=True)
                time.sleep(1)
    finally:
        server_sock.close()


def serve() -> None:
    if register_sdp_profile():
        print(f"Ava Ops Bluetooth bridge waiting on BlueZ Profile1 ({BT_NAME})", flush=True)
        while True:
            time.sleep(3600)
    serve_bound_rfcomm()


if __name__ == "__main__":
    try:
        serve()
    except KeyboardInterrupt:
        print("Ava Ops Bluetooth bridge stopped.", flush=True)
        sys.exit(0)
    except Exception as exc:
        print(f"Ava Ops Bluetooth bridge failed: {exc}", file=sys.stderr, flush=True)
        sys.exit(1)
