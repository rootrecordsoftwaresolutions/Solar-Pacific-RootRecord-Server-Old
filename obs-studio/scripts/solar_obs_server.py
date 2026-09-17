#!/usr/bin/env python3
"""
Ava Ivy Solar OBS Live Server
Serves a live-updating overlay for OBS Browser Source.
Run this, then point OBS Browser Source to http://localhost:8765
"""

import re
import time
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime

try:
    import requests
except ImportError:
    import subprocess, sys
    subprocess.check_call([sys.executable, "-m", "pip", "install", "requests", "-q"])
    import requests

PORT = 8765
URL = "https://rootrecord.info/ava/status/solar"
CACHE = {
    "battery": "—",
    "solar": "—",
    "load": "—",
    "energy": "—",
    "eco": "—",
    "host": "—",
    "updated": "—"
}

def scrape():
    global CACHE
    try:
        r = requests.get(URL, timeout=12, headers={"User-Agent": "AvaSolarOBS/1.0"})
        text = r.text

        def grab(patterns, default="—"):
            for p in patterns:
                m = re.search(p, text, re.I | re.S)
                if m:
                    return m.group(1).strip()
            return default

        battery = grab([
            r'(\d+%)\s*avg day',
            r'Battery now[^0-9]*(\d+%)',
            r'battery\s+(\d+%)',
        ])
        solar = grab([
            r'(\d+\s*W)\s*panel',
            r'panel in[^0-9]*(\d+\s*W)',
            r'Current input[^0-9]*(\d+\s*W)',
        ])
        load = grab([
            r'Load now[^0-9]*(\d+\s*W)',
            r'(\d+\s*W)\s*day avg',
            r'Load Out[^0-9]*(\d+\s*W)',
        ])
        energy = grab([
            r'Energy today[^0-9]*([\d.]+\s*kWh)',
            r'([\d.]+\s*kWh est\.)',
        ])
        eco = "LIVE" if re.search(r'EcoFlow\s*(live|LIVE|online)', text, re.I) else "—"
        host = "Online" if re.search(r'host online|Online / Working|host online Working', text, re.I) else "—"

        CACHE.update({
            "battery": battery,
            "solar": solar or "0 W",
            "load": load,
            "energy": energy,
            "eco": eco,
            "host": host,
            "updated": datetime.now().strftime("%H:%M:%S")
        })
        print(f"[{CACHE['updated']}] Updated → Battery {battery} | Solar {CACHE['solar']} | Load {load}")
    except Exception as e:
        print(f"Scrape error: {e}")

def poll_loop():
    while True:
        scrape()
        time.sleep(40)

HTML = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <title>Ava Ivy Solar · OBS Live</title>
  <style>
    :root {
      --bg: rgba(0, 10, 22, 0.85);
      --border: rgba(0, 229, 255, 0.4);
      --accent: #00e5ff;
      --solar: #ffd54a;
      --text: #f0f7ff;
      --muted: #8ba3b8;
      --green: #4ade80;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    html, body {
      background: transparent !important;
      font-family: "Segoe UI", Inter, system-ui, sans-serif;
      overflow: hidden;
    }
    .overlay {
      background: var(--bg);
      border: 1px solid var(--border);
      border-radius: 14px;
      padding: 14px 18px 15px;
      width: 340px;
      color: var(--text);
      backdrop-filter: blur(8px);
      box-shadow: 0 4px 24px rgba(0,0,0,0.5);
    }
    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
      padding-bottom: 8px;
      border-bottom: 1px solid rgba(0,229,255,0.18);
    }
    .brand { font-size: 15px; font-weight: 700; }
    .brand span { color: var(--accent); }
    .live {
      font-size: 11px; color: var(--green); font-weight: 600;
      display: flex; align-items: center; gap: 5px;
    }
    .dot {
      width: 7px; height: 7px; background: var(--green);
      border-radius: 50%; animation: pulse 1.5s infinite;
    }
    @keyframes pulse {
      0%, 100% { opacity: 1; } 50% { opacity: 0.35; }
    }
    .grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 10px 16px;
    }
    .metric label {
      display: block; font-size: 10px; text-transform: uppercase;
      letter-spacing: 0.07em; color: var(--muted); margin-bottom: 2px;
    }
    .metric .val {
      font-size: 20px; font-weight: 650;
      font-variant-numeric: tabular-nums; line-height: 1.15;
    }
    .metric .val.solar { color: var(--solar); }
    .metric .val.accent { color: var(--accent); }
    .footer {
      margin-top: 11px; font-size: 11px; color: var(--muted);
      display: flex; justify-content: space-between;
    }
  </style>
</head>
<body>
  <div class="overlay">
    <div class="header">
      <div class="brand"><span>Ava Ivy</span> · Solar</div>
      <div class="live"><div class="dot"></div> LIVE</div>
    </div>
    <div class="grid">
      <div class="metric"><label>Battery</label><div class="val accent" id="battery">—</div></div>
      <div class="metric"><label>Solar In</label><div class="val solar" id="solar">—</div></div>
      <div class="metric"><label>Load</label><div class="val" id="load">—</div></div>
      <div class="metric"><label>Today</label><div class="val" id="energy">—</div></div>
      <div class="metric"><label>EcoFlow</label><div class="val" id="eco">—</div></div>
      <div class="metric"><label>Host</label><div class="val" id="host">—</div></div>
    </div>
    <div class="footer">
      <span>rootrecord.info</span>
      <span id="updated">—</span>
    </div>
  </div>
  <script>
    async function refresh() {
      try {
        const r = await fetch('/stats');
        const d = await r.json();
        document.getElementById('battery').textContent = d.battery;
        document.getElementById('solar').textContent   = d.solar;
        document.getElementById('load').textContent    = d.load;
        document.getElementById('energy').textContent  = d.energy;
        document.getElementById('eco').textContent     = d.eco;
        document.getElementById('host').textContent    = d.host;
        document.getElementById('updated').textContent = d.updated + ' HST';
      } catch(e) {}
    }
    refresh();
    setInterval(refresh, 15000);  // UI polls every 15s
  </script>
</body>
</html>
"""

class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass  # silence default logging

    def do_GET(self):
        if self.path == "/stats":
            import json
            body = json.dumps(CACHE).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            body = HTML.encode()
            self.send_response(200)
            self.send_header("Content-Type", "text/html")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

if __name__ == "__main__":
    print(f"Ava Ivy Solar OBS Server starting on http://localhost:{PORT}")
    print("In OBS → Browser Source → URL: http://localhost:8765")
    print("Width ~360  Height ~220")
    print("Press Ctrl+C to stop\n")

    # initial scrape + background poller
    scrape()
    t = threading.Thread(target=poll_loop, daemon=True)
    t.start()

    server = HTTPServer(("127.0.0.1", PORT), Handler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nServer stopped.")
