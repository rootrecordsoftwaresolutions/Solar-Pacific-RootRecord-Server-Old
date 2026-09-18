"""Public radio player HTML — solid always-on stream. Autoplay; mute only; no pause/skip."""
from __future__ import annotations

import json


def player_html(
    *,
    brand: str = "Root Record Radio",
    site_label: str = "RootRecord",
    live_src: str = "/radio/live.mp3",
) -> str:
    live_js = json.dumps(live_src)
    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>{brand} · Live</title>
<style>
  :root {{
    --ink:#e8e4d9; --muted:#9a9588; --ring:#c4a574; --bg:#0c0d10;
    --line:rgba(232,228,217,.12);
  }}
  * {{ box-sizing:border-box; }}
  html,body{{margin:0;min-height:100%;background:radial-gradient(900px 600px at 50% 0%,#1a1820 0%,var(--bg) 55%);
    color:var(--ink);font-family:system-ui,sans-serif;}}
  .top{{
    display:flex;flex-wrap:wrap;gap:.75rem 1.25rem;align-items:center;justify-content:space-between;
    padding:.85rem 1.25rem;border-bottom:1px solid var(--line);background:rgba(0,0,0,.25);
  }}
  .top nav{{display:flex;flex-wrap:wrap;gap:.85rem 1.1rem;font-size:.88rem;}}
  .top a{{color:var(--muted);text-decoration:none;}}
  .top a:hover{{color:var(--ink);}}
  .wrap{{max-width:32rem;margin:0 auto;padding:2rem 1.25rem 3rem;display:flex;flex-direction:column;gap:1rem;}}
  .brand{{letter-spacing:.2em;text-transform:uppercase;font-size:.72rem;color:var(--muted);}}
  h1{{font-family:Georgia,"Iowan Old Style",serif;font-size:clamp(1.35rem,3.5vw,1.85rem);line-height:1.25;margin:0;font-weight:500;}}
  #status{{opacity:.85;font-size:.92rem;color:var(--muted);min-height:1.3em;}}
  /* Hidden media element — mute-only UI (no native pause/seek) */
  audio#player{{position:absolute;width:1px;height:1px;opacity:0;pointer-events:none;}}
  .meter{{
    width:100%;height:3px;border-radius:2px;background:rgba(232,228,217,.12);
    overflow:hidden;margin-top:.15rem;
  }}
  .meter > i{{display:block;height:100%;width:35%;background:var(--ring);border-radius:2px;
    animation:pulse 1.6s ease-in-out infinite;}}
  .meter.off > i{{animation:none;width:0;}}
  @keyframes pulse{{0%,100%{{opacity:.45;transform:translateX(0)}}50%{{opacity:1;transform:translateX(180%)}}}}
  .row{{display:flex;flex-wrap:wrap;gap:.65rem;align-items:center;}}
  button{{
    appearance:none;border:1px solid rgba(196,165,116,.35);background:rgba(255,255,255,.04);
    color:var(--ink);padding:.55rem 1rem;border-radius:6px;cursor:pointer;font-size:.9rem;
  }}
  button:hover{{border-color:var(--ring);}}
  button.on{{border-color:var(--ring);background:rgba(196,165,116,.15);}}
  .hint{{font-size:.85rem;color:var(--muted);line-height:1.45;}}
  a{{color:var(--ring);}}
</style>
</head>
<body>
  <header class="top">
    <nav aria-label="Homes">
      <a href="https://rootrecord.cloud/">Root Record</a>
      <a href="https://avaivy.cloud/">Ava</a>
      <a href="https://rootmc.net/">RootMC</a>
      <a href="https://rootrecord.cloud/billing">Billing</a>
    </nav>
  </header>
  <div class="wrap">
    <div class="brand">{brand}</div>
    <h1>Live from Hawaiʻi</h1>
    <p id="status">Starting…</p>
    <audio id="player" autoplay playsinline preload="auto"></audio>
    <div class="meter" id="meter" aria-hidden="true"><i></i></div>
    <div class="row">
      <button type="button" id="btn-mute">Mute</button>
      <button type="button" id="btn-listen" hidden>Listen</button>
    </div>
    <p class="hint">One continuous board — music, chimes, and reports. Reports duck the bed. No pause or skip — Mute only. ({site_label})</p>
  </div>
<script>
(function () {{
  const LIVE = {live_js};
  const player = document.getElementById('player');
  const status = document.getElementById('status');
  const btnMute = document.getElementById('btn-mute');
  const btnListen = document.getElementById('btn-listen');
  const meter = document.getElementById('meter');
  let muted = false;
  let savedVol = 1;
  let waitTimer = null;

  function setStatus(t) {{ status.textContent = t; }}
  function setMeter(on) {{ meter.classList.toggle('off', !on); }}

  function connect() {{
    if (!player.src || player.getAttribute('data-live') !== LIVE) {{
      player.setAttribute('data-live', LIVE);
      // Cache-bust only on hard reconnect so browsers refetch the live mount
      player.src = LIVE + (LIVE.indexOf('?') >= 0 ? '&' : '?') + 't=' + Date.now();
    }}
    player.muted = false;
    if (!muted) player.volume = savedVol || 1;
    const p = player.play();
    if (p && p.then) {{
      p.then(function () {{
        setStatus(muted ? 'On air (muted)' : 'On air');
        btnListen.hidden = true;
        setMeter(!muted);
      }}).catch(function () {{
        setStatus('Tap Listen — browser blocked autoplay');
        btnListen.hidden = false;
        setMeter(false);
      }});
    }} else {{
      setStatus('On air');
      setMeter(!muted);
    }}
  }}

  btnMute.addEventListener('click', function () {{
    muted = !muted;
    if (muted) {{
      savedVol = player.volume || 1;
      player.volume = 0;
      btnMute.textContent = 'Unmute';
      btnMute.classList.add('on');
      setStatus('On air (muted)');
      setMeter(false);
    }} else {{
      player.volume = savedVol || 1;
      btnMute.textContent = 'Mute';
      btnMute.classList.remove('on');
      setStatus('On air');
      setMeter(true);
      if (player.paused) connect();
    }}
  }});

  btnListen.addEventListener('click', function () {{ connect(); }});

  player.addEventListener('playing', function () {{
    if (waitTimer) {{ clearTimeout(waitTimer); waitTimer = null; }}
    setStatus(muted ? 'On air (muted)' : 'On air');
    btnListen.hidden = true;
    setMeter(!muted);
  }});
  // Debounce brief Icecast source switches (report inserts) so UI does not flash
  player.addEventListener('waiting', function () {{
    if (waitTimer) clearTimeout(waitTimer);
    waitTimer = setTimeout(function () {{
      if (player.readyState < 3) setStatus('Buffering…');
    }}, 1200);
  }});
  player.addEventListener('stalled', function () {{
    setStatus('Reconnecting…');
    setTimeout(function () {{
      player.removeAttribute('data-live');
      connect();
    }}, 2500);
  }});
  player.addEventListener('error', function () {{
    setStatus('Reconnecting…');
    setTimeout(function () {{
      player.removeAttribute('data-live');
      connect();
    }}, 2000);
  }});
  // Never stay paused unless muted — no pause control on this page
  player.addEventListener('pause', function () {{
    if (muted) return;
    if (document.visibilityState === 'hidden') return;
    setTimeout(function () {{
      if (!muted && player.paused) {{
        player.play().catch(function () {{ btnListen.hidden = false; }});
      }}
    }}, 200);
  }});

  connect();
}})();
</script>
</body>
</html>"""
