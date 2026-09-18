#!/usr/bin/env python3
"""Path mux on :8787 for origin.avaivy.cloud.

Secret solar paths -> 127.0.0.1:8791 (cam_gateway).
Everything else -> 127.0.0.1:8788 (Origin FastAPI, when up).
"""
from __future__ import annotations

import http.client
import json
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

SECRETS = Path.home() / ".ollama" / "skills" / "panels-cam" / "store" / "secrets.env"
SOLAR = ("127.0.0.1", 8791)
ORIGIN = ("127.0.0.1", 8788)
LISTEN = ("127.0.0.1", 8787)


def _secret() -> str:
    if os.environ.get("RR_SOLAR_PATH_SECRET"):
        return os.environ["RR_SOLAR_PATH_SECRET"].strip().strip("/")
    if SECRETS.is_file():
        for line in SECRETS.read_text(encoding="utf-8").splitlines():
            if line.startswith("RR_SOLAR_PATH_SECRET="):
                return line.split("=", 1)[1].strip().strip('"').strip("'").strip("/")
    return ""


class Mux(BaseHTTPRequestHandler):
    server_version = "SolarOriginMux/1.0"

    def log_message(self, fmt: str, *args) -> None:
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def _is_solar(self) -> bool:
        sec = _secret()
        if not sec:
            return False
        path = urlparse(self.path).path
        pref = "/" + sec
        return path == pref or path.startswith(pref + "/")

    def _proxy(self, target: tuple[str, int]) -> None:
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else None
        conn = http.client.HTTPConnection(target[0], target[1], timeout=120)
        try:
            headers = {k: v for k, v in self.headers.items() if k.lower() not in ("host", "connection")}
            conn.request(self.command, self.path, body=body, headers=headers)
            resp = conn.getresponse()
            data = resp.read()
            self.send_response(resp.status)
            for k, v in resp.getheaders():
                if k.lower() in ("transfer-encoding", "connection", "content-length"):
                    continue
                self.send_header(k, v)
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        except Exception as e:
            raw = json.dumps({"ok": False, "error": "upstream", "detail": str(e)[:200]}).encode()
            self.send_response(502)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)
        finally:
            conn.close()

    def do_GET(self) -> None:
        self._proxy(SOLAR if self._is_solar() else ORIGIN)

    def do_POST(self) -> None:
        self._proxy(SOLAR if self._is_solar() else ORIGIN)

    def do_PUT(self) -> None:
        self._proxy(ORIGIN)

    def do_DELETE(self) -> None:
        self._proxy(ORIGIN)

    def do_PATCH(self) -> None:
        self._proxy(ORIGIN)

    def do_HEAD(self) -> None:
        self._proxy(SOLAR if self._is_solar() else ORIGIN)


def main() -> int:
    if not _secret():
        print("RR_SOLAR_PATH_SECRET missing", file=sys.stderr)
        return 2
    httpd = ThreadingHTTPServer(LISTEN, Mux)
    print(f"solar-origin-mux http://{LISTEN[0]}:{LISTEN[1]} (solar->{SOLAR} else->{ORIGIN})", flush=True)
    httpd.serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
