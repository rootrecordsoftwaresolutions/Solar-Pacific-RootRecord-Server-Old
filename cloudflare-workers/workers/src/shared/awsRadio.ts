/**
 * Always-on Root Record Radio on Cloudflare.
 * Real session/heartbeat with 10-minute guest clock; members never cut off.
 */
const GUEST_LIMIT_S = 600;
const PORTAL_ME = "https://rootrecord-api-account.rootrecord.workers.dev/api/auth/me";
const ACCOUNT_URL = "https://rootrecord.cloud/account";
const BILLING_URL = "https://rootrecord.cloud/billing";
const TOP_UP_URL = "https://rootrecord.cloud/root-units";
const GUEST_CACHE_HOST = "https://rr-radio-guest.invalid";

export type Env = {
  // Optional stream origin / tunnel URL for /radio/live.mp3 proxy if used elsewhere.
  RADIO_LIVE_URL?: string;
  [key: string]: unknown;
};

function truthy(v: unknown): boolean {
  if (typeof v === "boolean") return v;
  const s = String(v ?? "").trim().toLowerCase();
  return ["1", "true", "yes", "on", "life", "lifetime", "active", "member"].includes(s);
}

function tierIsPaid(tier: string): boolean {
  const t = (tier || "").trim().toLowerCase();
  if (!t) return false;
  return ["monthly", "member", "pro", "life", "lifetime", "ava"].some((n) => t.includes(n));
}

function subIsActive(sub: string): boolean {
  return ["active", "trialing", "paid", "member"].includes((sub || "").trim().toLowerCase());
}

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : {};
}

/** Mirror desk radio_access.member_from_me (hardened). */
export function memberFromMe(me: Record<string, unknown> | null | undefined): {
  signed_in: boolean;
  member: boolean;
  lifetime: boolean;
  account_id: string;
  email: string;
  balance: number;
} {
  if (!me || me._auth === false) {
    return { signed_in: false, member: false, lifetime: false, account_id: "", email: "", balance: 0 };
  }
  const access = asRecord(me.access);
  const billing = asRecord(me.billing);
  const raw = asRecord(me.raw);
  const rawAccess = asRecord(raw.access);
  const rawBilling = asRecord(raw.billing);

  const pickTier = (...srcs: Record<string, unknown>[]): string => {
    for (const src of srcs) {
      for (const key of ["tier", "plan", "plan_name", "product", "membership"]) {
        const v = src[key];
        if (v == null || v === "") continue;
        return String(v).trim().toLowerCase();
      }
    }
    return "";
  };

  const tier = pickTier(me, access, billing, raw, rawAccess, rawBilling);

  const lifetime =
    truthy(me.life_member) ||
    truthy(me.lifeMember) ||
    truthy(me.lifetime_member) ||
    truthy(me.lifetime) ||
    truthy(access.life_member) ||
    truthy(billing.life_member) ||
    truthy(raw.life_member) ||
    truthy(rawAccess.life_member) ||
    truthy(rawBilling.life_member) ||
    tier === "life" ||
    tier === "lifetime" ||
    (tierIsPaid(tier) && (tier.includes("life") || tier.includes("lifetime")));

  const sub = String(
    me.subscription_status ??
      access.subscription_status ??
      billing.subscription_status ??
      raw.subscription_status ??
      rawBilling.subscription_status ??
      "",
  )
    .trim()
    .toLowerCase();

  const flagPaid =
    truthy(me.member) ||
    truthy(me.is_member) ||
    truthy(me.paid) ||
    truthy(me.pro) ||
    truthy(me.pro_unlocked) ||
    truthy(access.member) ||
    truthy(access.is_member) ||
    truthy(access.pro) ||
    truthy(access.pro_unlocked) ||
    truthy(access.life_member) ||
    truthy(billing.life_member) ||
    truthy(raw.member) ||
    truthy(raw.is_member) ||
    truthy(raw.paid) ||
    truthy(raw.pro) ||
    truthy(raw.pro_unlocked) ||
    truthy(rawAccess.member) ||
    truthy(rawAccess.is_member) ||
    truthy(rawAccess.pro) ||
    truthy(rawAccess.pro_unlocked) ||
    truthy(rawAccess.life_member) ||
    truthy(rawBilling.life_member);

  const paid = lifetime || flagPaid || subIsActive(sub) || tierIsPaid(tier);

  let bal = 0;
  for (const key of ["root_units_balance", "ledger_balance", "balance", "root_units", "balance_display"]) {
    for (const src of [me, access, billing, raw]) {
      const v = src[key];
      const n = typeof v === "number" ? v : Number(v);
      if (!Number.isFinite(n) || n < 0) continue;
      bal = n > 1_000_000 ? Math.floor(n / 100_000_000) : Math.floor(n);
      break;
    }
    if (bal) break;
  }

  const account_id = String(me.account_id ?? me.id ?? me.user_id ?? me.sub ?? "").trim().slice(0, 120);
  const email = String(me.email ?? "").trim().slice(0, 160);
  const signed_in = !!(email || account_id);
  return {
    signed_in,
    member: !!(paid && signed_in),
    lifetime,
    account_id: account_id || email || "",
    email,
    balance: Math.max(0, bal),
  };
}

function publicSessionPayload(identity: ReturnType<typeof memberFromMe>) {
  if (identity.member) {
    return {
      signed_in: true,
      member: true,
      lifetime: identity.lifetime,
      email: identity.email || "",
      balance: identity.balance,
      top_up: identity.balance < 10,
      top_up_url: TOP_UP_URL,
      billing_url: BILLING_URL,
      account_url: ACCOUNT_URL,
      guest_limit_s: GUEST_LIMIT_S,
      can_skip: true,
    };
  }
  if (identity.signed_in) {
    return {
      signed_in: true,
      member: false,
      lifetime: false,
      email: identity.email || "",
      balance: identity.balance,
      top_up: false,
      top_up_url: TOP_UP_URL,
      billing_url: BILLING_URL,
      account_url: ACCOUNT_URL,
      guest_limit_s: GUEST_LIMIT_S,
      can_skip: false,
      need_membership: true,
    };
  }
  return {
    signed_in: false,
    member: false,
    lifetime: false,
    email: "",
    balance: 0,
    top_up: false,
    top_up_url: TOP_UP_URL,
    billing_url: BILLING_URL,
    account_url: ACCOUNT_URL,
    guest_limit_s: GUEST_LIMIT_S,
    can_skip: false,
  };
}

function extractBearer(req: Request): string {
  const auth = req.headers.get("Authorization") || req.headers.get("authorization") || "";
  const m = auth.match(/^Bearer\s+(.+)$/i);
  if (m && m[1]) return decodeURIComponent(m[1].trim());
  const cookie = req.headers.get("Cookie") || req.headers.get("cookie") || "";
  const parts = cookie.split(";");
  for (const part of parts) {
    const p = part.trim();
    if (p.toLowerCase().startsWith("ava_session=")) {
      return decodeURIComponent(p.slice("ava_session=".length).trim());
    }
  }
  return "";
}

function guestIdFrom(req: Request, bodyGuest?: string): string {
  const h = (req.headers.get("X-Guest-Id") || req.headers.get("x-guest-id") || "").trim();
  const g = (bodyGuest || h || "").trim().slice(0, 80);
  return g;
}

async function fetchPortalMe(token: string): Promise<Record<string, unknown> | null> {
  const tok = decodeURIComponent((token || "").trim());
  if (tok.length < 16) return null;
  try {
    const r = await fetch(PORTAL_ME, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${tok}`,
        "User-Agent": "rootrecord-cloud-radio/1.0",
      },
    });
    if (r.status === 401 || r.status === 403) return { _auth: false };
    if (!r.ok) return null;
    const data = (await r.json()) as unknown;
    return data && typeof data === "object" ? (data as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

type GuestRow = { heard_s: number; started_at: number };

async function guestTick(guestId: string, seconds: number, playing: boolean): Promise<{
  ok: boolean;
  allowed: boolean;
  heard_s: number;
  remaining_s: number;
  limit_s: number;
  detail?: string;
}> {
  const gid = (guestId || "").trim().slice(0, 80);
  if (!gid) {
    return { ok: false, allowed: false, heard_s: 0, remaining_s: 0, limit_s: GUEST_LIMIT_S, detail: "missing guest" };
  }
  const cache = caches.default;
  const key = new Request(`${GUEST_CACHE_HOST}/${encodeURIComponent(gid)}`);
  let row: GuestRow = { heard_s: 0, started_at: Math.floor(Date.now() / 1000) };
  try {
    const hit = await cache.match(key);
    if (hit) {
      const j = (await hit.json()) as GuestRow;
      if (j && typeof j.heard_s === "number") row = j;
    }
  } catch {
    /* fresh */
  }
  if (playing && seconds > 0) {
    row.heard_s = Number(row.heard_s || 0) + Math.max(0, seconds);
  }
  if (!row.started_at) row.started_at = Math.floor(Date.now() / 1000);
  const body = JSON.stringify(row);
  try {
    await cache.put(
      key,
      new Response(body, {
        headers: {
          "Content-Type": "application/json",
          "Cache-Control": "max-age=86400",
        },
      }),
    );
  } catch {
    /* best-effort */
  }
  const heard = Number(row.heard_s || 0);
  const remaining = Math.max(0, Math.floor(GUEST_LIMIT_S - heard));
  return {
    ok: true,
    allowed: heard < GUEST_LIMIT_S,
    heard_s: Math.floor(heard),
    remaining_s: remaining,
    limit_s: GUEST_LIMIT_S,
  };
}

function corsHeaders(req: Request, extra: Record<string, string> = {}): Headers {
  const h = new Headers(extra);
  const origin = req.headers.get("Origin") || "*";
  h.set("Access-Control-Allow-Origin", origin === "null" ? "*" : origin);
  h.set("Access-Control-Allow-Credentials", "true");
  h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  h.set(
    "Access-Control-Allow-Headers",
    "content-type, range, authorization, x-guest-id",
  );
  h.set("Vary", "Origin");
  return h;
}

function json(req: Request, data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: corsHeaders(req, {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    }),
  });
}

export function playerHtml(opts?: { liveUrl?: string; brand?: string }): string {
  const brand = opts?.brand || "Root Record Radio";
  // Prefer same-origin live mount; callers may inject a tunnel URL if needed.
  const live = opts?.liveUrl || "/radio/live.mp3";
  // Keep JS template literal safe: no nested backticks.
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>${brand} · Live</title>
<style>
  :root {
    --ink:#e8e4d9; --muted:#9a9588; --ring:#c4a574; --bg:#0c0d10;
    --line:rgba(232,228,217,.12); --bad:#b87a6a;
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
  #status{opacity:.85;font-size:.92rem;color:var(--muted);min-height:1.3em;}
  #status.limit{color:var(--bad);opacity:1;}
  audio#player{position:absolute;width:1px;height:1px;opacity:0;pointer-events:none;}
  .meter{
    width:100%;height:3px;border-radius:2px;background:rgba(232,228,217,.12);
    overflow:hidden;margin-top:.15rem;
  }
  .meter > i{display:block;height:100%;width:35%;background:var(--ring);border-radius:2px;
    animation:pulse 1.6s ease-in-out infinite;}
  .meter.off > i{animation:none;width:0;}
  @keyframes pulse{0%,100%{opacity:.45;transform:translateX(0)}50%{opacity:1;transform:translateX(180%)}}
  .row{display:flex;flex-wrap:wrap;gap:.65rem;align-items:center;}
  button{
    appearance:none;border:1px solid rgba(196,165,116,.35);background:rgba(255,255,255,.04);
    color:var(--ink);padding:.55rem 1rem;border-radius:6px;cursor:pointer;font-size:.9rem;
  }
  button:hover{border-color:var(--ring);}
  button.on{border-color:var(--ring);background:rgba(196,165,116,.15);}
  .hint{font-size:.85rem;color:var(--muted);line-height:1.45;}
  .limit-box{
    display:none;padding:.85rem 1rem;border:1px solid rgba(184,122,106,.45);border-radius:8px;
    background:rgba(184,122,106,.08);font-size:.9rem;line-height:1.45;color:var(--ink);
  }
  .limit-box.show{display:block;}
  .limit-box a{color:var(--ring);}
  a{color:var(--ring);}
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
    <div class="brand">${brand}</div>
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
    <p class="hint">Guests get about 10 minutes; members keep streaming. One continuous board — music, chimes, and reports. Mute only — no pause or skip. (RootRecord)</p>
  </div>
<script>
(function () {
  const LIVE = ${JSON.stringify(live)};
  const ACCOUNT = "https://rootrecord.cloud/account";
  const player = document.getElementById("player");
  const status = document.getElementById("status");
  const btnMute = document.getElementById("btn-mute");
  const btnListen = document.getElementById("btn-listen");
  const meter = document.getElementById("meter");
  const limitBox = document.getElementById("limit-box");
  let muted = false;
  let savedVol = 1;
  let waitTimer = null;
  let heartbeatTimer = null;
  let limitHit = false;
  let isMember = false;
  let reconnectOk = true;

  function setStatus(t, isLimit) {
    status.textContent = t;
    status.classList.toggle("limit", !!isLimit);
  }
  function setMeter(on) { meter.classList.toggle("off", !on); }

  function readCookie(name) {
    try {
      const parts = (document.cookie || "").split(";");
      for (let i = 0; i < parts.length; i++) {
        const p = parts[i].trim();
        if (p.indexOf(name + "=") === 0) return decodeURIComponent(p.slice(name.length + 1));
      }
    } catch (e) {}
    return "";
  }

  function portalToken() {
    try {
      return localStorage.getItem("rootrecord_portal_token")
        || localStorage.getItem("rr_goals_token")
        || readCookie("ava_session")
        || "";
    } catch (e) {
      return readCookie("ava_session") || "";
    }
  }

  function guestId() {
    try {
      let v = localStorage.getItem("rootrecord_portal_device_id");
      if (!v) {
        v = (crypto.randomUUID && crypto.randomUUID()) || ("g" + Math.random().toString(36).slice(2) + Date.now().toString(36));
        localStorage.setItem("rootrecord_portal_device_id", v);
      }
      return v;
    } catch (e) {
      return "anon";
    }
  }

  function authHeaders() {
    const h = { "Content-Type": "application/json", "X-Guest-Id": guestId() };
    const t = portalToken();
    if (t) h.Authorization = "Bearer " + t;
    return h;
  }

  function fmtLeft(sec) {
    const s = Math.max(0, Math.floor(Number(sec) || 0));
    const m = Math.floor(s / 60);
    if (m >= 1) return m + "m left";
    return s + "s left";
  }

  function stopForLimit() {
    limitHit = true;
    reconnectOk = false;
    if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null; }
    try { player.pause(); } catch (e) {}
    try { player.removeAttribute("src"); player.load(); } catch (e) {}
    setMeter(false);
    btnListen.hidden = true;
    limitBox.classList.add("show");
    setStatus("Guest limit reached — sign in or join to continue", true);
  }

  function paintAccess(j) {
    if (!j) return;
    if (j.member || j.allowed === true) {
      isMember = !!j.member;
      if (isMember) {
        setStatus(muted ? "Member · on air (muted)" : "Member · on air");
      } else {
        const left = (j.remaining_s != null) ? fmtLeft(j.remaining_s) : "";
        setStatus(muted
          ? ("Guest · on air (muted)" + (left ? " · " + left : ""))
          : ("Guest · " + (left || "on air")));
      }
      return;
    }
    if (j.allowed === false) stopForLimit();
  }

  async function heartbeat() {
    if (limitHit) return;
    try {
      const playing = !player.paused && !limitHit;
      const r = await fetch("/api/radio/heartbeat", {
        method: "POST",
        headers: authHeaders(),
        body: JSON.stringify({ guest: guestId(), playing: playing }),
        cache: "no-store"
      });
      const j = await r.json();
      if (j && (j.member || j.allowed === true)) { paintAccess(j); return; }
      if (j && j.allowed === false) { stopForLimit(); return; }
      paintAccess(j);
    } catch (e) {}
  }

  function startHeartbeat() {
    if (heartbeatTimer || limitHit) return;
    heartbeat();
    heartbeatTimer = setInterval(heartbeat, 18000);
  }

  function connect() {
    if (limitHit) return;
    if (!player.src || player.getAttribute("data-live") !== LIVE) {
      player.setAttribute("data-live", LIVE);
      player.src = LIVE + (LIVE.indexOf("?") >= 0 ? "&" : "?") + "t=" + Date.now();
    }
    player.muted = false;
    if (!muted) player.volume = savedVol || 1;
    const p = player.play();
    if (p && p.then) {
      p.then(function () {
        btnListen.hidden = true;
        setMeter(!muted);
        startHeartbeat();
      }).catch(function () {
        setStatus("Tap Listen — browser blocked autoplay");
        btnListen.hidden = false;
        setMeter(false);
      });
    } else {
      setStatus("On air");
      setMeter(!muted);
      startHeartbeat();
    }
  }

  btnMute.addEventListener("click", function () {
    muted = !muted;
    if (muted) {
      savedVol = player.volume || 1;
      player.volume = 0;
      btnMute.textContent = "Unmute";
      btnMute.classList.add("on");
      setMeter(false);
    } else {
      player.volume = savedVol || 1;
      btnMute.textContent = "Mute";
      btnMute.classList.remove("on");
      setMeter(true);
      if (player.paused && !limitHit) connect();
    }
  });

  btnListen.addEventListener("click", function () {
    if (limitHit) { location.href = ACCOUNT; return; }
    connect();
  });

  player.addEventListener("playing", function () {
    if (waitTimer) { clearTimeout(waitTimer); waitTimer = null; }
    if (limitHit) { try { player.pause(); } catch (e) {} return; }
    btnListen.hidden = true;
    setMeter(!muted);
    startHeartbeat();
  });
  player.addEventListener("waiting", function () {
    if (limitHit) return;
    if (waitTimer) clearTimeout(waitTimer);
    waitTimer = setTimeout(function () {
      if (!limitHit && player.readyState < 3) setStatus("Buffering…");
    }, 1200);
  });
  player.addEventListener("stalled", function () {
    if (limitHit || !reconnectOk) return;
    setStatus("Reconnecting…");
    setTimeout(function () {
      if (limitHit || !reconnectOk) return;
      player.removeAttribute("data-live");
      connect();
    }, 2500);
  });
  player.addEventListener("error", function () {
    if (limitHit || !reconnectOk) return;
    setStatus("Reconnecting…");
    setTimeout(function () {
      if (limitHit || !reconnectOk) return;
      player.removeAttribute("data-live");
      connect();
    }, 2000);
  });
  player.addEventListener("pause", function () {
    if (muted || limitHit) return;
    if (document.visibilityState === "hidden") return;
    setTimeout(function () {
      if (!muted && !limitHit && player.paused) {
        player.play().catch(function () { btnListen.hidden = false; });
      }
    }, 200);
  });

  connect();
})();
</script>
</body>
</html>`;
}

async function handleSession(req: Request): Promise<Response> {
  const token = extractBearer(req);
  const me = token ? await fetchPortalMe(token) : null;
  const identity = memberFromMe(me);
  const payload = publicSessionPayload(identity);
  return json(req, { ok: true, ...payload });
}

async function handleHeartbeat(req: Request): Promise<Response> {
  let body: { guest?: string; playing?: boolean } = {};
  try {
    body = (await req.json()) as { guest?: string; playing?: boolean };
  } catch {
    body = {};
  }
  const token = extractBearer(req);
  const me = token ? await fetchPortalMe(token) : null;
  const identity = memberFromMe(me);
  const session = publicSessionPayload(identity);
  const guest = guestIdFrom(req, body.guest);
  const playing = !!body.playing;

  if (identity.member) {
    return json(req, {
      ok: true,
      member: true,
      allowed: true,
      session,
      heard_s: 0,
      remaining_s: null,
      limit_s: GUEST_LIMIT_S,
      balance: identity.balance,
    });
  }

  // Match live behavior: ~20s tick per heartbeat while playing.
  const tick = await guestTick(guest, playing ? 20 : 0, playing);
  return json(req, {
    ok: tick.ok,
    member: false,
    session,
    allowed: tick.allowed,
    heard_s: tick.heard_s,
    remaining_s: tick.remaining_s,
    limit_s: tick.limit_s,
    detail: tick.detail,
  });
}

/**
 * Main entry for CF worker radio routes under /radio and /api/radio/*.
 * Returns null if the path is not handled here.
 */
export async function handleAwsRadio(req: Request, env: Env, url?: URL): Promise<Response | null> {
  const u = url || new URL(req.url);
  const path = u.pathname.replace(/\/+$/, "") || "/";

  if (req.method === "OPTIONS" && (path.startsWith("/api/radio") || path.startsWith("/radio"))) {
    return new Response(null, { status: 204, headers: corsHeaders(req) });
  }

  if (path === "/api/radio/session" && req.method === "GET") {
    return handleSession(req);
  }
  if (path === "/api/radio/heartbeat" && req.method === "POST") {
    return handleHeartbeat(req);
  }
  if (path === "/api/radio/vote" || path === "/api/radio/skip") {
    return json(req, { ok: false, accepted: false, detail: "not available on always-on edge" });
  }
  if (path === "/api/radio/now" || path === "/api/radio/status") {
    return json(req, {
      ok: true,
      on_air: true,
      always_on: true,
      title: "Root Record Radio",
      description: "Live from Hawaiʻi",
    });
  }
  if (path === "/api/radio/wake" && req.method === "POST") {
    return json(req, { ok: true, on_air: true, serving_public: true, always_on: true });
  }

  if ((path === "/radio" || path === "/radio/listen") && req.method === "GET") {
    const liveUrl = typeof env.RADIO_LIVE_URL === "string" && env.RADIO_LIVE_URL
      ? env.RADIO_LIVE_URL
      : "/radio/live.mp3";
    return new Response(playerHtml({ liveUrl }), {
      status: 200,
      headers: corsHeaders(req, {
        "Content-Type": "text/html; charset=utf-8",
        "Cache-Control": "no-store",
      }),
    });
  }

  return null;
}

export default { handleAwsRadio, playerHtml, memberFromMe, GUEST_LIMIT_S };
