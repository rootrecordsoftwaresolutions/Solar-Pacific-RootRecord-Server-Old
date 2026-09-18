#!/usr/bin/env python3
"""Light solar-cam gateway behind Cloudflare Tunnel.

GET  /<secret>/health
POST /<secret>/power-on|power-off
GET  /<secret>/current.jpg   (one ffmpeg still — rare; AWS owns GIF)

Heavy work stays on AWS.
"""
from __future__ import annotations

import json
import os
import sys
import tempfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from cam_power_session import power_off, power_on  # noqa: E402
from panels_grab import grab_jpeg  # noqa: E402

SECRETS = Path.home() / ".ollama" / "skills" / "panels-cam" / "store" / "secrets.env"
STATE = Path.home() / ".ollama" / "skills" / "panels-cam" / "store" / "gateway-state.json"


def _load_env() -> dict[str, str]:
    env: dict[str, str] = {}
    if SECRETS.is_file():
        for line in SECRETS.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, _, v = line.partition("=")
            env[k.strip()] = v.strip().strip('"').strip("'")
    for k in ("RR_SOLAR_PATH_SECRET", "RR_SOLAR_LISTEN", "RR_SOLAR_CHANNEL"):
        if os.environ.get(k):
            env[k] = os.environ[k].strip()
    return env


def _save_state(row: dict[str, Any]) -> None:
    STATE.parent.mkdir(parents=True, exist_ok=True)
    tmp = STATE.with_suffix(".tmp")
    tmp.write_text(json.dumps(row, indent=2) + "\n", encoding="utf-8")
    tmp.replace(STATE)


class Handler(BaseHTTPRequestHandler):
    server_version = "SolarCamGateway/1.0"

    def log_message(self, fmt: str, *args: Any) -> None:
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def _secret(self) -> str:
        return str(getattr(self.server, "path_secret", "") or "")

    def _json(self, code: int, body: dict[str, Any]) -> None:
        raw = json.dumps(body).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def _deny(self) -> None:
        self._json(404, {"ok": False, "error": "not_found"})

    def _rest(self) -> str | None:
        path = urlparse(self.path).path
        secret = self._secret().strip("/")
        if not secret:
            return None
        pref = "/" + secret
        if path != pref and not path.startswith(pref + "/"):
            return None
        return path[len(pref) :].lstrip("/") or "health"

    def do_GET(self) -> None:  # noqa: N802
        rest = self._rest()
        if rest is None:
            self._deny()
            return
        if rest == "health":
            self._json(200, {"ok": True, "service": "solar-cam-gateway"})
            return
        if rest == "current.jpg":
            self._still()
            return
        self._deny()

    def do_POST(self) -> None:  # noqa: N802
        rest = self._rest()
        if rest is None:
            self._deny()
            return
        if rest == "power-on":
            out = power_on(execute=True, settle_s=8.0)
            _save_state({"we_powered": bool(out.get("we_powered")), "last": out})
            self._json(200 if out.get("ok") else 500, out)
            return
        if rest == "power-off":
            we = None
            try:
                we = bool(json.loads(STATE.read_text(encoding="utf-8")).get("we_powered"))
            except Exception:
                pass
            out = power_off(execute=True, we_powered=we)
            self._json(200 if out.get("ok") else 500, out)
            return
        self._deny()

    def _still(self) -> None:
        env = _load_env()
        ch = int(env.get("RR_SOLAR_CHANNEL") or 1)
        with tempfile.NamedTemporaryFile(suffix=".jpg", delete=False) as tmp:
            out = Path(tmp.name)
        try:
            grab = grab_jpeg(channel=ch, stream=0, out=out)
            if not grab.get("ok") or not out.is_file():
                self._json(503, {"ok": False, "error": grab.get("error") or "grab_failed"})
                return
            data = out.read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "image/jpeg")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("X-Solar-Cam-Lit", "1" if grab.get("lit") else "0")
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)
        finally:
            try:
                out.unlink(missing_ok=True)
            except Exception:
                pass


def main() -> int:
    env = _load_env()
    secret = (env.get("RR_SOLAR_PATH_SECRET") or "").strip()
    if len(secret) < 16:
        print("RR_SOLAR_PATH_SECRET missing/short", file=sys.stderr)
        return 2
    listen = (env.get("RR_SOLAR_LISTEN") or "127.0.0.1:8791").strip()
    host, _, port_s = listen.partition(":")
    port = int(port_s or "8791")
    httpd = ThreadingHTTPServer((host or "127.0.0.1", port), Handler)
    httpd.path_secret = secret  # type: ignore[attr-defined]
    print(f"solar-cam-gateway http://{host}:{port}/<secret>/…", flush=True)
    httpd.serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
