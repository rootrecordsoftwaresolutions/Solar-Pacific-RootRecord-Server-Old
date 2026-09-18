/**
 * Always-on Root Record Radio via AWS Icecast (Cloudflare tunnel).
 * Does not depend on AVA Console / origin wake.
 */

import type { AvaEnv } from "./types";

const DEFAULT_UPSTREAM =
  "https://choice-cities-establishment-million.trycloudflare.com/rootrecord.mp3";

export function radioUpstream(env: AvaEnv): string {
  const raw = (env.RR_RADIO_UPSTREAM || DEFAULT_UPSTREAM).trim();
  if (!raw) return DEFAULT_UPSTREAM;
  if (raw.endsWith(".mp3") || raw.includes("/rootrecord.mp3")) return raw;
  return raw.replace(/\/$/, "") + "/rootrecord.mp3";
}

export function isAwsRadioPath(path: string): boolean {
  const p = path.replace(/\/+$/, "") || "/";
  return (
    p === "/radio" ||
    p === "/radio/listen" ||
    p === "/radio/live.mp3" ||
    path.startsWith("/radio/live.mp3") ||
    p === "/api/radio/now" ||
    p === "/api/radio/status" ||
    p === "/api/radio/session" ||
    p === "/api/radio/wake" ||
    p === "/api/radio/steering" ||
    p === "/api/radio/hurricane" ||
    p === "/api/radio/vote" ||
    p === "/api/radio/heartbeat" ||
    p === "/api/radio/skip"
  );
}

function playerHtml(): string {
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>Root Record Radio · Live</title>
<style>
  :root {
    --ink:#e8e4d9; --muted:#9a9588; --ring:#c4a574; --bg:#0c0d10;
    --line:rgba(232,228,217,.12);
  }
  * { box-sizing:border-box; }
  html,body{margin:0;min-height:100%;background:radial-gradient(900px 600px at 50% 0%,#1a1820 0%,var(--bg) 55%);
    color:var(--ink);font-family:system-ui,sans-serif;}
  .top{
    display:flex;flex-wrap:wrap;gap:.75rem 1.25rem;align-items:center;justify-content:space-between;
    padding:.85rem 1.25rem;border-bottom:1px solid var(--line);background:rgba(0,0,0,.25);
  }
  .top nav{display:flex;flex-wrap:wrap;gap:.85rem 1.1rem;font-size:.88rem;}
  .top a{color:var(--muted);text-decoration:none;}
  .top a:hover{color:var(--ink);}
  .wrap{max-width:32rem;margin:0 auto;padding:2rem 1.25rem 3rem;display:flex;flex-direction:column;gap:1rem;}
  .brand{letter-spacing:.2em;text-transform:uppercase;font-size:.72rem;color:var(--muted);}
  h1{font-family:Georgia,"Iowan Old Style",serif;font-size:clamp(1.35rem,3.5vw,1.85rem);line-height:1.25;margin:0;font-weight:500;}
  #status{opacity:.8;font-size:.9rem;color:var(--muted);}
  audio{width:100%;margin-top:.35rem;}
  .hint{font-size:.85rem;color:var(--muted);line-height:1.45;}
  a{color:var(--ring);}
</style>
</head>
<body>
  <header class="top">
    <nav aria-label="Homes">
      <a href="https://rootrecord.cloud/">Root Record</a>
      <a href="https://avaivy.cloud/">Ava</a>
      <a href="https://rootmc.net/">RootMC</a>
    </nav>
  </header>
  <div class="wrap">
    <div class="brand">Root Record Radio</div>
    <h1>Live from Hawaiʻi</h1>
    <p id="status">Connecting…</p>
    <audio id="player" controls preload="none" playsinline></audio>
    <p class="hint">Music beds and desk audio from the Root Record always-on board. If play stalls, refresh — the stream keeps running on the server.</p>
  </div>
<script>
(function () {
  const LIVE = "/radio/live.mp3";
  const player = document.getElementById("player");
  const status = document.getElementById("status");
  function go() {
    player.src = LIVE + "?t=" + Date.now();
    const p = player.play();
    if (p && p.then) {
      p.then(function () { status.textContent = "On air"; })
       .catch(function () { status.textContent = "Tap play to listen"; });
    } else {
      status.textContent = "On air";
    }
  }
  player.addEventListener("error", function () {
    status.textContent = "Reconnecting…";
    setTimeout(go, 2500);
  });
  player.addEventListener("stalled", function () {
    status.textContent = "Buffering…";
  });
  player.addEventListener("playing", function () {
    status.textContent = "On air";
  });
  go();
})();
</script>
</body>
</html>`;
}

async function proxyIcecast(request: Request, upstream: string): Promise<Response> {
  const headers = new Headers();
  const range = request.headers.get("Range");
  if (range) headers.set("Range", range);
  headers.set("User-Agent", "RootRecord-Cloud-Radio/1.0");
  headers.set("Icy-MetaData", "0");

  const upstreamRes = await fetch(upstream, {
    method: "GET",
    headers,
    redirect: "follow",
  });

  const out = new Headers();
  const pass = [
    "content-type",
    "icy-name",
    "icy-description",
    "icy-genre",
    "icy-br",
    "icy-sr",
    "cache-control",
    "expires",
    "pragma",
  ];
  for (const name of pass) {
    const v = upstreamRes.headers.get(name);
    if (v) out.set(name, v);
  }
  if (!out.has("content-type")) out.set("content-type", "audio/mpeg");
  out.set("cache-control", "no-cache, no-store");
  out.set("access-control-allow-origin", "*");
  out.set("access-control-allow-methods", "GET, HEAD, OPTIONS");

  if (request.method === "HEAD") {
    return new Response(null, { status: upstreamRes.status, headers: out });
  }
  return new Response(upstreamRes.body, { status: upstreamRes.status, headers: out });
}

/** Handle /radio* and /api/radio* for always-on AWS stream. */
export async function handleAwsRadio(
  request: Request,
  env: AvaEnv,
  path: string,
): Promise<Response | null> {
  if (!isAwsRadioPath(path)) return null;

  const p = path.replace(/\/+$/, "") || "/";
  const upstream = radioUpstream(env);

  if (request.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: {
        "access-control-allow-origin": "*",
        "access-control-allow-methods": "GET, HEAD, POST, OPTIONS",
        "access-control-allow-headers": "content-type, range",
        "access-control-max-age": "86400",
      },
    });
  }

  if (p === "/radio" || p === "/radio/listen") {
    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response(null, { status: 405 });
    }
    return new Response(playerHtml(), {
      status: 200,
      headers: {
        "content-type": "text/html; charset=utf-8",
        "cache-control": "no-cache",
      },
    });
  }

  if (p === "/radio/live.mp3" || path.startsWith("/radio/live.mp3")) {
    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response(null, { status: 405 });
    }
    try {
      return await proxyIcecast(request, upstream);
    } catch (err) {
      return Response.json(
        {
          ok: false,
          detail: err instanceof Error ? err.message : "upstream",
          upstream_host: (() => {
            try {
              return new URL(upstream).host;
            } catch {
              return "unknown";
            }
          })(),
        },
        { status: 502 },
      );
    }
  }

  // Desk APIs — always report on-air when AWS stream is configured
  if (p === "/api/radio/wake" && (request.method === "POST" || request.method === "GET")) {
    return Response.json({
      ok: true,
      on_air: true,
      serving_public: true,
      src: "/radio/live.mp3",
      listen: "/radio/listen",
    });
  }

  if (
    p === "/api/radio/now" ||
    p === "/api/radio/status" ||
    p === "/api/radio/session" ||
    p === "/api/radio/steering" ||
    p === "/api/radio/hurricane"
  ) {
    return Response.json({
      ok: true,
      on_air: true,
      serving_public: true,
      src: "/radio/live.mp3",
      live: "/radio/live.mp3",
      title: "Root Record Radio",
      description: "Always-on from Hawaiʻi",
      source: "rr-aws",
    });
  }

  if (p === "/api/radio/vote" || p === "/api/radio/heartbeat" || p === "/api/radio/skip") {
    return Response.json({ ok: true, accepted: false, detail: "aws_always_on_stream" });
  }

  return null;
}
