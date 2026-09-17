var __defProp = Object.defineProperty;
var __name = (target, value) => __defProp(target, "name", { value, configurable: true });

// src/worker.js
var STATUS_ORIGIN = "https://ava-origin.rootmc.net";
var EDGE_ETA = "https://ava.rootmc.net/api/return-eta";
var STATUS_URL = "https://rootrecord.info/ava/status";
var WIKI_ORIGIN = "https://avaivy.cloud";
var ECO_BAR = `<nav class="eco-bar" aria-label="Ecosystem"><a href="https://rootrecord.info/">RootRecord</a><a href="https://rootmc.net/">RootMC</a><a href="https://avaivy.cloud/wiki/" aria-current="page">Ava</a><a href="https://avaivy.cloud/blog">Blog</a></nav>`;
function wikiRedirectUrl(pathname, search = "") {
  // /ava → /wiki, /ava/foo → /wiki/foo (status proxies handled separately)
  let rest = pathname.replace(/^\/ava\/?/, "/");
  if (!rest.startsWith("/")) rest = `/${rest}`;
  if (rest === "/") rest = "/";
  const dest = new URL(`/wiki${rest === "/" ? "/" : rest}`, WIKI_ORIGIN);
  if (search) dest.search = search.startsWith("?") ? search.slice(1) : search;
  return dest.toString();
}
__name(wikiRedirectUrl, "wikiRedirectUrl");
function redirectToWiki(pathname, search = "") {
  return Response.redirect(wikiRedirectUrl(pathname, search), 301);
}
__name(redirectToWiki, "redirectToWiki");
function avaEdgeErrorHtml(status, heading, lead) {
  return `<!DOCTYPE html><html lang="en"><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width, initial-scale=1"/><title>${status} \u2014 ${heading}</title>
<style>
:root{--bg0:#000d1a;--ink:#fff;--muted:#7a92a8;--accent:#00e5ff;--line:rgba(0,229,255,.16)}
body{margin:0;min-height:100vh;background:linear-gradient(168deg,#000d1a,#001428);color:var(--ink);font-family:DM Sans,system-ui,sans-serif}
.eco-bar{display:flex;justify-content:center;gap:.15rem;padding:.4rem 1rem;border-bottom:1px solid var(--line)}
.eco-bar a{color:var(--muted);text-decoration:none;font-size:.78rem;font-weight:650;letter-spacing:.06em;text-transform:uppercase;padding:.28rem .9rem;border-radius:999px}
.eco-bar a[aria-current=page]{color:var(--accent);background:rgba(0,229,255,.12)}
main{max-width:36rem;margin:12vh auto;padding:0 1.25rem}
.code{color:var(--accent);letter-spacing:.16em;text-transform:uppercase;font-size:.8rem}
h1{margin:.4rem 0 .6rem;font-size:clamp(1.8rem,5vw,2.6rem)}
p{color:var(--muted);line-height:1.5}
.row{display:flex;flex-wrap:wrap;gap:.55rem}
a.btn{display:inline-flex;padding:.55rem .95rem;border-radius:999px;border:1px solid var(--line);color:var(--ink);text-decoration:none;font-weight:650}
a.btn.primary{background:#00e5ff;color:#000d1a;border-color:#00e5ff}
</style></head><body>${ECO_BAR}<main><p class="code">${status}</p><h1>${heading}</h1><p>${lead}</p>
<div class="row"><a class="btn primary" href="https://rootrecord.info/">RootRecord</a><a class="btn" href="https://rootmc.net/">RootMC</a><a class="btn" href="https://play.rootmc.net">play.rootmc.net</a><a class="btn" href="${STATUS_URL}">Ava status</a></div>
</main></body></html>`;
}
__name(avaEdgeErrorHtml, "avaEdgeErrorHtml");
function isSubHost(host) {
  return host === "ava.rootrecord.info" || host.endsWith(".workers.dev");
}
__name(isSubHost, "isSubHost");
function wikiPathname(url) {
  const host = url.hostname;
  let pathname = url.pathname;
  if (isSubHost(host)) {
    if (pathname === "/" || pathname === "") return "/ava/";
    if (!pathname.startsWith("/ava/")) {
      return "/ava" + (pathname.startsWith("/") ? pathname : `/${pathname}`);
    }
  }
  return pathname;
}
__name(wikiPathname, "wikiPathname");
function statusUpstreamPath(pathname) {
  if (pathname === "/ava/status" || pathname === "/ava/status/") return "/status";
  const rest = pathname.replace(/^\/ava\/status/, "") || "/";
  if (rest.startsWith("/api/") || rest === "/health" || rest.startsWith("/phpmyadmin")) {
    return rest;
  }
  if (rest === "/solar" || rest === "/power" || rest === "/home") return rest;
  if (rest === "/offline" || rest === "/offline/") return "/offline";
  return "/status" + (rest.startsWith("/") ? rest : `/${rest}`);
}
__name(statusUpstreamPath, "statusUpstreamPath");
function offlineHtml(eta) {
  const atMs = eta && typeof eta.atMs === "number" ? eta.atMs : null;
  const label = String(eta?.label || "\u2014");
  const note = String(
    eta?.note || "Return ETA = average daytime start + 1 hour wiggle (not sunrise)."
  );
  const avg = eta?.averageLabel ? String(eta.averageLabel) : "";
  const samples = eta?.sampleDays != null ? String(eta.sampleDays) : "";
  return `<!DOCTYPE html><html lang="en"><head>
<meta charset="utf-8"/><meta name="viewport" content="width=device-width, initial-scale=1"/>
<meta http-equiv="refresh" content="60"/><title>Ava Ivy \u2014 offline</title>
<style>
:root{--bg0:#000d1a;--bg1:#001428;--ink:#ffffff;--muted:#7a92a8;--accent:#00e5ff;--accent-glow:rgba(0,229,255,.28);--warn:#ffb020;--line:rgba(0,229,255,.18);--ava-portrait:url("https://avaivy.cloud/wiki/assets/ava-wave-hello-still.png")}
*{box-sizing:border-box}body{margin:0;min-height:100vh;color:var(--ink);font-family:"IBM Plex Sans",Segoe UI,sans-serif;position:relative;isolation:isolate;
background:radial-gradient(1000px 500px at 15% -10%,#00334d 0%,transparent 55%),radial-gradient(800px 420px at 100% 10%,#001a33 0%,transparent 50%),linear-gradient(165deg,var(--bg0),var(--bg1));
display:flex;align-items:center;justify-content:center;padding:24px}
body::before{content:"";position:fixed;right:max(-2vw,-24px);bottom:0;width:min(46vw,560px);height:min(78vh,760px);z-index:0;pointer-events:none;
background:var(--ava-portrait) no-repeat right bottom/contain;opacity:.32;filter:drop-shadow(0 0 48px rgba(0,229,255,.35));
-webkit-mask-image:linear-gradient(90deg,transparent 0%,#000 18%,#000 100%);mask-image:linear-gradient(90deg,transparent 0%,#000 18%,#000 100%)}
@media(max-width:820px){body::before{width:min(70vw,420px);height:min(55vh,480px);opacity:.2;right:-8vw}}
main{max-width:560px;width:100%;text-align:center;position:relative;z-index:1}
.pill{display:inline-block;padding:6px 12px;border-radius:999px;border:1px solid rgba(255,176,32,.45);color:var(--warn);font-size:.75rem;letter-spacing:.06em;text-transform:uppercase;margin-bottom:18px}
h1{font-family:"IBM Plex Serif",Georgia,serif;font-weight:600;font-size:clamp(1.8rem,5vw,2.4rem);margin:0 0 8px;letter-spacing:-.02em}
.sub{color:var(--muted);margin:0 0 28px;line-height:1.45}
.countdown{font-variant-numeric:tabular-nums;font-size:clamp(2.6rem,12vw,4.2rem);font-weight:700;color:var(--accent);letter-spacing:.04em;margin:8px 0 6px;text-shadow:0 0 36px var(--accent-glow)}
.eta-label{font-size:1.15rem;margin:0 0 8px}
.meta{color:var(--muted);font-size:.9rem;margin:0 0 28px;line-height:1.5}
.row{display:flex;flex-wrap:wrap;gap:10px;justify-content:center;margin-bottom:12px}
a.btn{display:inline-block;padding:10px 16px;border-radius:6px;border:1px solid var(--line);color:var(--ink);text-decoration:none;background:rgba(255,255,255,.04)}
a.btn:hover{border-color:var(--accent);box-shadow:0 0 18px rgba(0,229,255,.12)}
a.btn.primary{border-color:var(--accent);background:rgba(0,229,255,.14);color:var(--accent)}
a.btn.nav{font-size:.92rem}
</style></head><body><main>
<div class="pill">Host offline</div>
<h1>Ava Ivy</h1>
<p class="sub">Solar Root Server is powered down or unreachable.</p>
<div class="countdown" id="cd">\u2014</div>
<p class="eta-label">Expected return \xB7 <strong>${label.replace(/</g, "")}</strong></p>
<p class="meta">${note.replace(/</g, "")}${avg ? `<br/>Raw average start ${avg.replace(/</g, "")}${samples ? ` (${samples} days)` : ""}` : ""}</p>
<div class="row">
<a class="btn primary" href="${STATUS_URL}">Check if online</a>
<a class="btn" href="https://ava.rootmc.net/offline">Ava offline board</a>
</div>
<div class="row">
<a class="btn nav" href="https://rootmc.net">RootMC</a>
<a class="btn nav" href="https://avaivy.cloud/wiki/">Ava docs</a>
<a class="btn nav" href="https://rootrecord.info/">RootRecord</a>
</div>
</main>
<script>
const atMs=${atMs == null ? "null" : String(atMs)};
function pad(n){return String(n).padStart(2,"0")}
function fmt(ms){let s=Math.max(0,Math.floor(ms/1000));const h=Math.floor(s/3600);s%=3600;const m=Math.floor(s/60);const sec=s%60;return pad(h)+":"+pad(m)+":"+pad(sec)}
function tick(){const el=document.getElementById("cd");if(!el)return;if(atMs==null){el.textContent="\u2014";return}el.textContent=fmt(atMs-Date.now())}
tick();setInterval(tick,1000);
async function probe(){try{const r=await fetch("https://ava.rootmc.net/health",{cache:"no-store",mode:"cors"});if(r.ok)location.replace(${JSON.stringify(STATUS_URL)})}catch(e){}}
probe();setInterval(probe,30000);
<\/script></body></html>`;
}
__name(offlineHtml, "offlineHtml");
async function offlinePageResponse(detail = "origin down") {
  let eta = null;
  try {
    const r = await fetch(EDGE_ETA, { cf: { cacheTtl: 30 } });
    if (r.ok) eta = await r.json();
  } catch {
  }
  return new Response(offlineHtml(eta), {
    status: 503,
    headers: {
      "content-type": "text/html; charset=utf-8",
      "cache-control": "no-store",
      "x-ava-wiki-proxy": "offline-page",
      "x-ava-detail": String(detail).slice(0, 120)
    }
  });
}
__name(offlinePageResponse, "offlinePageResponse");
function isContextPath(pathname) {
  return pathname === "/ava/context" || pathname === "/ava/context/" || pathname === "/ava/context.md" || pathname.startsWith("/ava/context?");
}
__name(isContextPath, "isContextPath");
function isGoalsPath(pathname) {
  return pathname === "/ava/goals" || pathname === "/ava/goals/" || pathname.startsWith("/ava/goals/");
}
__name(isGoalsPath, "isGoalsPath");
function isLogsPath(pathname) {
  return pathname === "/ava/logs" || pathname === "/ava/logs/" || pathname.startsWith("/ava/logs?");
}
__name(isLogsPath, "isLogsPath");
async function proxyLogs(request, pathname) {
  const url = new URL(request.url);
  const target = new URL("/logs" + url.search, STATUS_ORIGIN);
  const headers = new Headers(request.headers);
  headers.set("Host", target.host);
  const prefix = isSubHost(url.hostname) ? "/status" : "/ava/status";
  headers.set("X-Forwarded-Prefix", prefix);
  headers.set("X-Forwarded-Host", url.host);
  headers.set("X-Forwarded-Proto", "https");
  const upstream = await fetch(target, { method: request.method, headers, redirect: "manual" });
  const outHeaders = new Headers(upstream.headers);
  outHeaders.set("X-Ava-Wiki-Proxy", "logs");
  outHeaders.delete("x-frame-options");
  const ct = outHeaders.get("content-type") || "";
  if (ct.includes("text/html")) {
    let html = await upstream.text();
    const base = `${prefix}/`;
    if (!html.includes(`href="${base}"`)) {
      html = html.replace(/<head([^>]*)>/i, `<head$1><base href="${base}">`);
    }
    return new Response(html, { status: upstream.status, headers: outHeaders });
  }
  return new Response(upstream.body, { status: upstream.status, headers: outHeaders });
}
__name(proxyLogs, "proxyLogs");
function contextUpstreamPath(pathname, search) {
  if (pathname.endsWith(".md") || search && /[?&]format=md\b/i.test(search)) {
    return "/context?format=md";
  }
  if (search && /[?&]format=json\b/i.test(search)) {
    return "/api/context";
  }
  return "/context" + (search || "");
}
__name(contextUpstreamPath, "contextUpstreamPath");
async function proxyContext(request, pathname) {
  const url = new URL(request.url);
  const upstreamPath = contextUpstreamPath(pathname, url.search);
  const target = new URL(upstreamPath, STATUS_ORIGIN);
  if (!upstreamPath.includes("?") && url.search) {
    target.search = url.search;
  }
  const headers = new Headers(request.headers);
  headers.set("Host", target.host);
  const prefix = isSubHost(url.hostname) ? "/status" : "/ava/status";
  headers.set("X-Forwarded-Prefix", prefix);
  headers.set("X-Forwarded-Host", url.host);
  headers.set("X-Forwarded-Proto", "https");
  const upstream = await fetch(target, { method: request.method, headers, redirect: "manual" });
  const outHeaders = new Headers(upstream.headers);
  outHeaders.set("X-Ava-Wiki-Proxy", "context");
  return new Response(upstream.body, { status: upstream.status, headers: outHeaders });
}
__name(proxyContext, "proxyContext");
function goalsUpstreamPath(pathname) {
  const rest = pathname.replace(/^\/ava\/goals/, "") || "/";
  if (rest === "/" || rest === "") return "/goals";
  return "/goals" + (rest.startsWith("/") ? rest : `/${rest}`);
}
__name(goalsUpstreamPath, "goalsUpstreamPath");
async function proxyGoals(request, pathname) {
  const url = new URL(request.url);
  const target = new URL(goalsUpstreamPath(pathname) + url.search, STATUS_ORIGIN);
  const headers = new Headers(request.headers);
  headers.set("Host", target.host);
  const prefix = isSubHost(url.hostname) ? "/status" : "/ava/status";
  headers.set("X-Forwarded-Prefix", prefix);
  headers.set("X-Forwarded-Host", url.host);
  headers.set("X-Forwarded-Proto", "https");
  const upstream = await fetch(target, { method: request.method, headers, redirect: "manual" });
  const outHeaders = new Headers(upstream.headers);
  outHeaders.set("X-Ava-Wiki-Proxy", "goals");
  outHeaders.delete("x-frame-options");
  const ct = outHeaders.get("content-type") || "";
  if (ct.includes("text/html")) {
    let html = await upstream.text();
    const base = `${prefix}/`;
    if (!html.includes(`href="${base}"`) && !/<base\s/i.test(html)) {
      html = html.replace(/<head([^>]*)>/i, `<head$1><base href="${base}">`);
    }
    return new Response(html, { status: upstream.status, headers: outHeaders });
  }
  return new Response(upstream.body, { status: upstream.status, headers: outHeaders });
}
__name(proxyGoals, "proxyGoals");
async function proxyStatus(request, pathname) {
  const url = new URL(request.url);
  const target = new URL(statusUpstreamPath(pathname) + url.search, STATUS_ORIGIN);
  const headers = new Headers(request.headers);
  headers.set("Host", target.host);
  const prefix = isSubHost(url.hostname) ? "/status" : "/ava/status";
  headers.set("X-Forwarded-Prefix", prefix);
  headers.set("X-Forwarded-Host", url.host);
  headers.set("X-Forwarded-Proto", "https");
  const init = { method: request.method, headers, redirect: "manual" };
  if (request.method !== "GET" && request.method !== "HEAD") init.body = request.body;
  let upstream;
  try {
    upstream = await fetch(target, init);
  } catch (err) {
    return offlinePageResponse(err?.message || "fetch failed");
  }
  if (upstream.status >= 502 || upstream.status === 522 || upstream.status === 523 || upstream.status === 524) {
    return offlinePageResponse(`upstream ${upstream.status}`);
  }
  const outHeaders = new Headers(upstream.headers);
  outHeaders.set("X-Ava-Wiki-Proxy", "status");
  outHeaders.delete("x-frame-options");
  const ct = outHeaders.get("content-type") || "";
  if (ct.includes("text/html")) {
    let html = await upstream.text();
    const base = `${prefix}/`;
    if (!html.includes(`href="${base}"`)) {
      html = html.replace(/<head([^>]*)>/i, `<head$1><base href="${base}">`);
    }
    return new Response(html, { status: upstream.status, headers: outHeaders });
  }
  return new Response(upstream.body, { status: upstream.status, headers: outHeaders });
}
__name(proxyStatus, "proxyStatus");
function isReleaseBoardPath(pathname) {
  return pathname === "/ava/status/plugins" || pathname === "/ava/status/plugins/" || pathname.startsWith("/ava/status/plugins?") || pathname === "/ava/status/apps" || pathname === "/ava/status/apps/" || pathname.startsWith("/ava/status/apps?") || pathname.startsWith("/ava/status/api/plugins") || pathname.startsWith("/ava/status/api/apps");
}
__name(isReleaseBoardPath, "isReleaseBoardPath");
function privateReleaseBoardResponse() {
  const body = `<!DOCTYPE html><html><head><meta charset="utf-8"/><title>Private \u2014 Ava Client</title>
<style>body{font-family:system-ui,sans-serif;background:#0b0f14;color:#e8eef7;margin:2rem;max-width:40rem}
a{color:#7dd3fc}</style></head><body>
<h1>Private operator surface</h1>
<p>Plugin / app bump \xB7 build \xB7 release is only available in the <strong>Ava Client</strong> on the OptiPlex.</p>
<p>Downloads: <a href="https://ava.rootmc.net/publicfiles/">ava.rootmc.net/publicfiles</a></p>
</body></html>`;
  return new Response(body, {
    status: 403,
    headers: {
      "content-type": "text/html; charset=utf-8",
      "cache-control": "no-store",
      "x-ava-wiki-proxy": "private-release-board"
    }
  });
}
__name(privateReleaseBoardResponse, "privateReleaseBoardResponse");
var worker_default = {
  async fetch(request, env) {
    const url = new URL(request.url);
    const pathname = wikiPathname(url);
    if (isReleaseBoardPath(pathname)) {
      return privateReleaseBoardResponse();
    }
    if (pathname === "/ava/status/offline" || pathname === "/ava/status/offline/") {
      try {
        const h = await fetch(new URL("/health", STATUS_ORIGIN), { cf: { cacheTtl: 0 } });
        if (h.ok) {
          return Response.redirect(STATUS_URL, 302);
        }
      } catch {
      }
      return offlinePageResponse("explicit offline");
    }
    // Status (and status.json / status APIs) stay proxied on this worker.
    if (pathname === "/ava/status" || pathname.startsWith("/ava/status/")) {
      try {
        return await proxyStatus(request, pathname);
      } catch (err) {
        return offlinePageResponse(err?.message || "status proxy");
      }
    }
    // Logs still need origin proxy (no dedicated avaivy page).
    if (isLogsPath(pathname)) {
      try {
        return await proxyLogs(request, pathname);
      } catch (err) {
        return offlinePageResponse(err?.message || "logs proxy");
      }
    }
    // Context / goals live on avaivy.cloud app routes.
    if (isContextPath(pathname) || pathname === "/ava/context.md") {
      const dest = new URL(pathname.replace(/^\/ava/, "") || "/context", WIKI_ORIGIN);
      if (pathname === "/ava/context.md") dest.pathname = "/context";
      dest.search = url.search;
      return Response.redirect(dest.toString(), 301);
    }
    if (isGoalsPath(pathname)) {
      const rest = pathname.replace(/^\/ava\/goals/, "") || "/";
      const dest = new URL(`/goals${rest === "/" ? "" : rest}`, WIKI_ORIGIN);
      dest.search = url.search;
      return Response.redirect(dest.toString(), 301);
    }
    // Wiki HTML + assets moved to https://avaivy.cloud/wiki
    if (pathname === "/timeline" || pathname === "/timeline/" || pathname.startsWith("/timeline/")) {
      return redirectToWiki("/ava/timeline", url.search);
    }
    if (pathname === "/ava" || pathname.startsWith("/ava/")) {
      return redirectToWiki(pathname, url.search);
    }
    return new Response(
      avaEdgeErrorHtml(404, "Page not found", "That URL is not on this host."),
      { status: 404, headers: { "content-type": "text/html; charset=utf-8" } }
    );
  }
};
export {
  worker_default as default
};
