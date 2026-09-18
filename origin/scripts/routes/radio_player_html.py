"""Public radio player HTML — mute-only UX, guest clock heartbeat, Icecast debounce."""
from __future__ import annotations


def player_html(
    *,
    brand: str = "Root Record Radio",
    site_label: str = "RootRecord",
    live_src: str = "/radio/live.mp3",
) -> str:
    import json
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
    --line:rgba(232,228,217,.12); --bad:#b87a6a;
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
  #status.limit{{color:var(--bad);opacity:1;}}
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
  .limit-box{{
    display:none;padding:.85rem 1rem;border:1px solid rgba(184,122,106,.45);border-radius:8px;
    background:rgba(184,122,106,.08);font-size:.9rem;line-height:1.45;color:var(--ink);
  }}
  .limit-box.show{{display:block;}}
  .limit-box a{{color:var(--ring);}}
  a{{color:var(--ring);}}
</style>
</head>
<body>
  <header class="top">
    <nav aria-label="Homes">
      <a href="https://rootrecord.cloud/">Root Record</a>
      <a href="https://avaivy.cloud/">Ava</a>
      <a href="https://rootmc.net/">RootMC</a>
      <a href="https://rootrecord.cloud/account">Account</a>
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
    <div class="limit-box" id="limit-box" role="status">
      Guest listen wrapped up (~10 minutes). 
      <a href="https://rootrecord.cloud/account">Sign in</a> or 
      <a href="https://rootrecord.cloud/billing">become a member</a> to keep streaming.
      <div class="row" style="margin-top:.65rem">
        <a href="https://rootrecord.cloud/account"><button type="button">Account / refresh membership</button></a>
        <a href="https://rootrecord.cloud/billing"><button type="button">Billing</button></a>
      </div>
    </div>
    <p class="hint">Guests get about 10 minutes; members keep streaming. One continuous board — music, chimes, and reports. Mute only — no pause or skip. ({site_label})</p>
  </div>
<script>
(function () {{
  const LIVE = {live_js};
  const ACCOUNT = 'https://rootrecord.cloud/account';
  const BILLING = 'https://rootrecord.cloud/billing';
  const player = document.getElementById('player');
  const status = document.getElementById('status');
  const btnMute = document.getElementById('btn-mute');
  const btnListen = document.getElementById('btn-listen');
  const meter = document.getElementById('meter');
  const limitBox = document.getElementById('limit-box');
  let muted = false;
  let savedVol = 1;
  let waitTimer = null;
  let heartbeatTimer = null;
  let limitHit = false;
  let isMember = false;
  let reconnectOk = true;

  function setStatus(t, isLimit) {{
    status.textContent = t;
    status.classList.toggle('limit', !!isLimit);
  }}
  function setMeter(on) {{ meter.classList.toggle('off', !on); }}

  function readCookie(name) {{
    try {{
      const parts = (';.cookie || '').split(';');
      for (let i = 0; i < parts.length; i++) {{
        const p = parts[i].trim();
        if (p.indexOf(name + '=') === 0) return decodeURIComponent(p.slice(name.length + 1));
      }}
    }} catch (e) {{}}
    return '';
  }}

  function portalToken() {{
    try {{
      return localStorage.getItem('rootrecord_portal_token')
        || localStorage.getItem('rr_goals_token')
        || readCookie('ava_session')
        || '';
    }} catch (e) {{
      return readCookie('ava_session') || '';
    }}
  }}

  function guestId() {{
    try {{
      let v = localStorage.getItem('rootrecord_portal_device_id');
      if (!v) {{
        v = (crypto.randomUUID && crypto.randomUUID()) || ('g' + Math.random().toString(36).slice(2) + Date.now().toString(36));
        localStorage.setItem('rootrecord_portal_device_id', v);
      }}
      return v;
    }} catch (e) {{
      return 'anon';
    }}
  }}

  function authHeaders() {{
    const h = {{ 'Content-Type': 'application/json', 'X-Guest-Id': guestId() }};
    const t = portalToken();
    if (t) h.Authorization = 'Bearer ' + t;
    return h;
  }}

  function fmtLeft(sec) {{
    const s = Math.max(0, Math.floor(Number(sec) || 0));
    const m = Math.floor(s / 60);
    if (m >= 1) return m + 'm left';
    return s + 's left';
  }}

  function stopForLimit() {{
    limitHit = true;
    reconnectOk = false;
    if (heartbeatTimer) {{ clearInterval(heartbeatTimer); heartbeatTimer = null; }}
    try {{ player.pause(); }} catch (e) {{}}
    try {{ player.removeAttribute('src'); player.load(); }} catch (e) {{}}
    setMeter(false);
    btnListen.hidden = true;
    limitBox.classList.add('show');
    setStatus('Guest limit reached — sign in or join to continue', true);
  }}

  function paintAccess(j) {{
    if (!j) return;
    if (j.member || j.allowed === true) {{
      isMember = !!j.member;
      if (isMember) {{
        setStatus(muted ? 'Member · on air (muted)' : 'Member · on air');
      }} else {{
        const left = (j.remaining_s != null) ? fmtLeft(j.remaining_s) : '';
        setStatus(muted
          ? ('Guest · on air (muted)' + (left ? ' · ' + left : ''))
          : ('Guest · ' + (left || 'on air')));
      }}
      return;
    }}
    if (j.allowed === false) {{
      stopForLimit();
    }}
  }}

  async function heartbeat() {{
    if (limitHit) return;
    try {{
      const playing = !player.paused && !limitHit;
      const r = await fetch('/api/radio/heartbeat', {{
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({{ guest: guestId(), playing: playing }}),
        cache: 'no-store'
      }});
      const j = await r.json();
      if (j && (j.member || j.allowed === true)) {{
        paintAccess(j);
        return;
      }}
      if (j && j.allowed === false) {{
        stopForLimit();
        return;
      }}
      paintAccess(j);
    }} catch (e) {{}}
  }}

  function startHeartbeat() {{
    if (heartbeatTimer || limitHit) return;
    heartbeat();
    heartbeatTimer = setInterval(heartbeat, 18000);
  }}

  function connect() {{
    if (limitHit) return;
    if (!player.src || player.getAttribute('data-live') !== LIVE) {{
      player.setAttribute('data-live', LIVE);
      player.src = LIVE + (LIVE.indexOf('?') >= 0 ? '&' : '?') + 't=' + Date.now();
    }}
    player.muted = false;
    if (!muted) player.volume = savedVol || 1;
    const p = player.play();
    if (p && p.then) {{
      p.then(function () {{
        btnListen.hidden = true;
        setMeter(!muted);
        startHeartbeat();
        if (!isMember) setStatus(muted ? 'Guest · on air (muted)' : 'On air');
        else setStatus(muted ? 'Member · on air (muted)' : 'Member · on air');
      }}).catch(function () {{
        setStatus('Tap Listen — browser blocked autoplay');
        btnListen.hidden = false;
        setMeter(false);
      }});
    }} else {{
      setStatus('On air');
      setMeter(!muted);
      startHeartbeat();
    }}
  }}

  btnMute.addEventListener('click', function () {{
    muted = !muted;
    if (muted) {{
      savedVol = player.volume || 1;
      player.volume = 0;
      btnMute.textContent = 'Unmute';
      btnMute.classList.add('on');
      setMeter(false);
      if (!limitHit) setStatus(isMember ? 'Member · on air (muted)' : 'Guest · on air (muted)');
    }} else {{
      player.volume = savedVol || 1;
      btnMute.textContent = 'Mute';
      btnMute.classList.remove('on');
      setMeter(true);
      if (!limitHit) setStatus(isMember ? 'Member · on air' : 'On air');
      if (player.paused && !limitHit) connect();
    }}
  }});

  btnListen.addEventListener('click', function () {{
    if (limitHit) {{
      location.href = ACCOUNT;
      return;
    }}
    connect();
  }});

  player.addEventListener('playing', function () {{
    if (waitTimer) {{ clearTimeout(waitTimer); waitTimer = null; }}
    if (limitHit) {{ try {{ player.pause(); }} catch (e) {{}} return; }}
    btnListen.hidden = true;
    setMeter(!muted);
    startHeartbeat();
  }});
  // Debounce brief Icecast source switches (report inserts) so UI does not flash
  player.addEventListener('waiting', function () {{
    if (limitHit) return;
    if (waitTimer) clearTimeout(waitTimer);
    waitTimer = setTimeout(function () {{
      if (!limitHit && player.readyState < 3) setStatus('Buffering…');
    }}, 1200);
  }});
  player.addEventListener('stalled', function () {{
    if (limitHit || !reconnectOk) return;
    setStatus('Reconnecting…');
    setTimeout(function () {{
      if (limitHit || !reconnectOk) return;
      player.removeAttribute('data-live');
      connect();
    }}, 2500);
  }});
  player.addEventListener('error', function () {{
    if (limitHit || !reconnectOk) return;
    setStatus('Reconnecting…');
    setTimeout(function () {{
      if (limitHit || !reconnectOk) return;
      player.removeAttribute('data-live');
      connect();
    }}, 2000);
  }});
  // Never stay paused unless muted or guest-limited — no pause control on this page
  player.addEventListener('pause', function () {{
    if (muted || limitHit) return;
    if (document.visibilityState === 'hidden') return;
    setTimeout(function () {{
      if (!muted && !limitHit && player.paused) {{
        player.play().catch(function () {{ btnListen.hidden = false; }});
      }}
    }}, 200);
  }});

  connect();
}})();
</script>
</body>
</html>"""
