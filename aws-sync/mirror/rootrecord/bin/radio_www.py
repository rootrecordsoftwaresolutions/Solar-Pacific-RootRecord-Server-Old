#!/usr/bin/env python3
from __future__ import annotations
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
UPSTREAM = "http://127.0.0.1:8000/rootrecord.mp3"
HTML = """<!DOCTYPE html>
<html lang=\"en\"><head>
<meta charset=\"UTF-8\"/><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>
<title>Root Record Radio · Live</title>
<style>
html,body{margin:0;min-height:100%;background:#0c0d10;color:#e8e4d9;font-family:system-ui,sans-serif}
.wrap{max-width:32rem;margin:0 auto;padding:2rem 1.25rem}
.brand{letter-spacing:.2em;text-transform:uppercase;font-size:.72rem;color:#9a9588}
h1{font-family:Georgia,serif;font-weight:500}
#status{color:#9a9588} audio{width:100%;margin-top:1rem}
</style></head><body><div class=\"wrap\">
<div class=\"brand\">Root Record Radio</div>
<h1>Live from Hawaiʻi</h1>
<p id=\"status\">Connecting…</p>
<audio id=\"player\" controls autoplay playsinline></audio>
<p style=\"color:#9a9588;font-size:.85rem\">Always-on AWS stream.</p>
</div><script>
(function(){var L=\"/rootrecord.mp3\",p=document.getElementById(\"player\"),s=document.getElementById(\"status\");
function go(){p.src=L+\"?t=\"+Date.now();p.play().then(function(){s.textContent=\"On air\"}).catch(function(){s.textContent=\"Tap play\"})}
p.onerror=function(){s.textContent=\"Reconnecting…\";setTimeout(go,2000)};p.onplaying=function(){s.textContent=\"On air\"};go()})();
</script></body></html>"""
class H(BaseHTTPRequestHandler):
  def log_message(self,*a): return
  def do_GET(self):
    path=self.path.split("?",1)[0]
    if path in ("/","/radio","/radio/","/radio/listen"):
      b=HTML.encode(); self.send_response(200)
      self.send_header("content-type","text/html; charset=utf-8"); self.send_header("cache-control","no-store")
      self.send_header("content-length",str(len(b))); self.end_headers(); self.wfile.write(b); return
    if path.startswith("/rootrecord.mp3") or path=="/radio/live.mp3":
      try:
        req=urllib.request.Request(UPSTREAM,headers={"Icy-MetaData":"0","User-Agent":"rr-radio-www/1"})
        with urllib.request.urlopen(req,timeout=60) as resp:
          self.send_response(200)
          self.send_header("content-type", resp.headers.get("content-type") or "audio/mpeg")
          self.send_header("cache-control","no-store"); self.send_header("access-control-allow-origin","*")
          self.end_headers()
          while True:
            c=resp.read(16384)
            if not c: break
            self.wfile.write(c)
      except Exception as e:
        m=str(e).encode(); self.send_response(502); self.send_header("content-type","text/plain")
        self.send_header("content-length",str(len(m))); self.end_headers(); self.wfile.write(m)
      return
    self.send_response(404); self.end_headers()
ThreadingHTTPServer(("127.0.0.1",8088),H).serve_forever()
