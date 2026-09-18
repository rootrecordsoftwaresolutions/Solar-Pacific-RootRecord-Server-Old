"""Public radio player HTML — solid always-on stream. Autoplay; mute only; no skip."""
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
  audio{{width:100%;margin-top:.35rem;}}
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
    <audio id="player" controls autoplay playsinline preload="auto"></audio>
    <div class="row">
      <button type="button" id="btn-mute">Mute</button>
      <button type="button" id="btn-play" hidden>Play</button>
    </div>
    <p class="hint">One continuous board — music, chimes, and reports. Reports duck the bed to half volume. No skips. Use Mute if you need quiet. ({site_label})</p>
  </div>
<script>
(function () {{
  const LIVE = {live_js};
  const player = document.getElementById('player');
  const status = document.getElementById('status');
  const btnMute = document.getElementById('btn-mute');
  const btnPlay = document.getElementById('btn-play');
  let muted = false;
  let savedVol = 1;

  function setStatus(t) {{ status.textContent = t; }}

  function connect() {{
    // One solid stream URL — do not tear down on events (that caused pauses).
    if (!player.src || player.getAttribute('data-live') !== LIVE) {{
      player.setAttribute('data-live', LIVE);
      player.src = LIVE;
    }}
    player.muted = false;
    const p = player.play();
    if (p && p.then) {{
      p.then(function () {{
        setStatus(muted ? 'On air (muted)' : 'On air');
        btnPlay.hidden = true;
      }}).catch(function () {{
        setStatus('Tap Play — browser blocked autoplay');
        btnPlay.hidden = false;
      }});
    }} else {{
      setStatus('On air');
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
    }} else {{
      player.volume = savedVol || 1;
      btnMute.textContent = 'Mute';
      btnMute.classList.remove('on');
      setStatus('On air');
      // Never pause — only restore volume
      if (player.paused) connect();
    }}
  }});

  btnPlay.addEventListener('click', function () {{
    connect();
  }});

  player.addEventListener('playing', function () {{
    setStatus(muted ? 'On air (muted)' : 'On air');
    btnPlay.hidden = true;
  }});
  player.addEventListener('waiting', function () {{ setStatus('Buffering…'); }});
  player.addEventListener('stalled', function () {{ setStatus('Reconnecting…'); }});
  // If the network drops the Icecast socket, reload src without a pause UX loop
  player.addEventListener('error', function () {{
    setStatus('Reconnecting…');
    setTimeout(function () {{
      player.removeAttribute('data-live');
      connect();
    }}, 2000);
  }});
  // Block pause except when volume is muted (user intent) — resume if something else pauses
  player.addEventListener('pause', function () {{
    if (muted) return;
    if (document.visibilityState === 'hidden') return;
    setTimeout(function () {{
      if (!muted && player.paused) {{
        player.play().catch(function () {{ btnPlay.hidden = false; }});
      }}
    }}, 300);
  }});

  connect();
}})();
</script>
</body>
</html>"""
