/**
 * Local edge gateway: disk cache, preference-aware upstream, optional HMAC signing.
 * Sits in front of wrangler/site local ports. Tunnel points here (or at site/api ports).
 */

import http from "node:http";
import https from "node:https";
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..");
const CONFIG_PATH = process.env.ROOTMC_EDGE_CONFIG || path.join(ROOT, "config.json");

function loadConfig() {
  return JSON.parse(fs.readFileSync(CONFIG_PATH, "utf8"));
}

function ensureDir(p) {
  fs.mkdirSync(p, { recursive: true });
}

function sha256Hex(buf) {
  return crypto.createHash("sha256").update(buf).digest("hex");
}

function cacheKey(method, urlPath, search) {
  return sha256Hex(`${method}:${urlPath}?${search || ""}`);
}

function isCacheablePath(method, urlPath) {
  if (method !== "GET" && method !== "HEAD") return false;
  const p = urlPath.replace(/\/+$/, "") || "/";
  const blocked = [
    "/api/account",
    "/api/developer",
    "/v1/discord",
    "/api/rootmc/server/heartbeat",
    "/api/rootmc/connection-preference",
    "/api/rootmc/connection_preference",
    "/api/realm/minecraft",
    "/api/mobile",
  ];
  if (blocked.some((b) => p === b || p.startsWith(b + "/") || p.startsWith(b))) return false;
  if (p.includes("/sync") || p.includes("/heartbeat")) return false;
  if (p.startsWith("/maps/") || p.includes("tiles") || p.endsWith(".png") || p.endsWith(".webp")) return true;
  if (p.startsWith("/api/rootmc/") || p.startsWith("/api/v2/") || p === "/health" || p === "/api/health") {
    return true;
  }
  if (/\.(js|css|html|json|svg|woff2?|png|jpg|webp|ico)$/i.test(p)) return true;
  return false;
}

function readPreference(stateDir) {
  const p = path.join(stateDir, "current_connection_preference.json");
  try {
    let raw = fs.readFileSync(p, "utf8");
    if (raw.charCodeAt(0) === 0xfeff) raw = raw.slice(1);
    return JSON.parse(raw);
  } catch {
    return { preference: "local", reason: "default_local_gateway" };
  }
}

function readCacheStats(cacheDir) {
  const statsPath = path.join(cacheDir, "_stats.json");
  try {
    return JSON.parse(fs.readFileSync(statsPath, "utf8"));
  } catch {
    return { hits: 0, misses: 0, bytes: 0, entries: 0, oldest: null };
  }
}

function writeCacheStats(cacheDir, stats) {
  ensureDir(cacheDir);
  fs.writeFileSync(path.join(cacheDir, "_stats.json"), JSON.stringify(stats, null, 2));
}

function loadCacheEntry(cacheDir, key) {
  const metaPath = path.join(cacheDir, `${key}.meta.json`);
  const bodyPath = path.join(cacheDir, `${key}.body`);
  if (!fs.existsSync(metaPath) || !fs.existsSync(bodyPath)) return null;
  try {
    const meta = JSON.parse(fs.readFileSync(metaPath, "utf8"));
    const body = fs.readFileSync(bodyPath);
    return { meta, body };
  } catch {
    return null;
  }
}

function saveCacheEntry(cacheDir, key, meta, body, maxBytes) {
  ensureDir(cacheDir);
  const stats = readCacheStats(cacheDir);
  const bodyPath = path.join(cacheDir, `${key}.body`);
  const metaPath = path.join(cacheDir, `${key}.meta.json`);
  fs.writeFileSync(bodyPath, body);
  fs.writeFileSync(metaPath, JSON.stringify(meta));
  stats.misses = (stats.misses || 0) + 0;
  stats.entries = (stats.entries || 0) + 1;
  stats.bytes = (stats.bytes || 0) + body.length;
  if (!stats.oldest) stats.oldest = meta.storedAt;
  writeCacheStats(cacheDir, stats);
  if (stats.bytes > maxBytes) {
    // simple prune: delete oldest half of meta files
    const files = fs.readdirSync(cacheDir).filter((f) => f.endsWith(".meta.json"));
    const metas = files
      .map((f) => {
        try {
          const m = JSON.parse(fs.readFileSync(path.join(cacheDir, f), "utf8"));
          return { f, storedAt: m.storedAt || "" };
        } catch {
          return { f, storedAt: "" };
        }
      })
      .sort((a, b) => String(a.storedAt).localeCompare(String(b.storedAt)));
    const drop = metas.slice(0, Math.ceil(metas.length / 3));
    for (const d of drop) {
      const keyName = d.f.replace(/\.meta\.json$/, "");
      try {
        fs.unlinkSync(path.join(cacheDir, d.f));
        fs.unlinkSync(path.join(cacheDir, `${keyName}.body`));
      } catch {
        /* ignore */
      }
    }
  }
}

function signBody(bodyBuf, key) {
  if (!key || key.length < 16) return null;
  const ts = Math.floor(Date.now() / 1000).toString();
  const h = crypto.createHmac("sha256", key);
  h.update(`${ts}.`);
  h.update(bodyBuf);
  return { signature: h.digest("hex"), timestamp: ts };
}

function fetchUpstream(url, req, timeoutMs) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const lib = u.protocol === "https:" ? https : http;
    const headers = { ...req.headers };
    delete headers.host;
    delete headers["content-length"];
    const opts = {
      protocol: u.protocol,
      hostname: u.hostname,
      port: u.port || (u.protocol === "https:" ? 443 : 80),
      path: u.pathname + u.search,
      method: req.method,
      headers,
      timeout: timeoutMs,
    };
    const upstream = lib.request(opts, (res) => {
      const chunks = [];
      res.on("data", (c) => chunks.push(c));
      res.on("end", () => {
        resolve({
          statusCode: res.statusCode || 502,
          headers: res.headers,
          body: Buffer.concat(chunks),
        });
      });
    });
    upstream.on("timeout", () => {
      upstream.destroy();
      reject(new Error("upstream_timeout"));
    });
    upstream.on("error", reject);
    if (req.method !== "GET" && req.method !== "HEAD") {
      req.pipe(upstream);
    } else {
      upstream.end();
    }
  });
}

function pickUpstream(cfg, pref, hostHeader, urlPath) {
  const preference = pref?.preference === "cloudflare" ? "cloudflare" : "local";
  const isMap = hostHeader.includes("map") || urlPath.startsWith("/maps") || urlPath.includes("bluemap");
  // Gen 2 / api2 retired — route former api2 hosts/paths to main API.
  const isApi =
    hostHeader.includes("api")
    || urlPath.startsWith("/api")
    || urlPath.startsWith("/v1/")
    || urlPath.startsWith("/v2/")
    || urlPath === "/health"
    || urlPath === "/__edge/health";

  if (preference === "cloudflare") {
    if (isMap) return cfg.upstreams.cfMap;
    if (isApi) return cfg.upstreams.cfApi;
    return cfg.upstreams.cfSite;
  }
  if (isMap) return cfg.upstreams.map;
  if (isApi) return cfg.upstreams.api;
  return cfg.upstreams.site;
}

function applyCors(headers) {
  if (!headers["access-control-allow-origin"]) {
    headers["access-control-allow-origin"] = "*";
  }
}

async function main() {
  const cfg = loadConfig();
  ensureDir(cfg.cacheDir);
  ensureDir(cfg.stateDir);
  ensureDir(cfg.persistDir);
  const signingKey = process.env.ROOTMC_EDGE_SIGNING_KEY || "";
  const port = Number(cfg.ports.gateway || 8791);
  const timeoutMs = Number(cfg.originTimeoutMs || 2500);
  const ttlDefault = Number(cfg.cache?.defaultTtlSeconds || 60);
  const ttlMap = Number(cfg.cache?.mapTtlSeconds || 300);
  const staleIfError = Number(cfg.cache?.staleIfErrorSeconds || 3600);
  const maxBytes = Number(cfg.cache?.maxBytes || 512 * 1024 * 1024);

  const server = http.createServer(async (req, res) => {
    const started = Date.now();
    try {
      const host = String(req.headers.host || "localhost");
      const u = new URL(req.url || "/", `http://${host}`);

      if (u.pathname === "/__edge/preference" && req.method === "GET") {
        const pref = readPreference(cfg.stateDir);
        const body = Buffer.from(JSON.stringify(pref));
        res.writeHead(200, {
          "content-type": "application/json; charset=utf-8",
          "cache-control": "no-store",
        });
        res.end(body);
        return;
      }

      // Paper polls api-local → gateway for this path; serve authoritative local-edge state
      // (local wrangler D1 may still be default_cloudflare until preference PUT succeeds).
      const prefPath = u.pathname.replace(/\/+$/, "") || "/";
      if (
        (prefPath === "/api/rootmc/connection-preference" || prefPath === "/api/rootmc/connection_preference")
        && req.method === "GET"
      ) {
        const pref = readPreference(cfg.stateDir);
        const preference = pref.preference === "local" ? "local" : "cloudflare";
        const payload = {
          ...pref,
          preference,
          active_provider: preference === "local" ? "solar" : "cloudflare",
          node_discord_username:
            preference === "local" && pref.node_discord_username
              ? pref.node_discord_username
              : null,
          source: pref.source || "local_edge_gateway",
        };
        const body = Buffer.from(JSON.stringify(payload));
        res.writeHead(200, {
          "content-type": "application/json; charset=utf-8",
          "cache-control": "public, max-age=5, stale-while-revalidate=15",
          "x-rootmc-pref-source": "local_edge_file",
        });
        res.end(body);
        return;
      }

      if (u.pathname === "/__edge/cache-stats" && req.method === "GET") {
        const stats = readCacheStats(cfg.cacheDir);
        res.writeHead(200, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
        res.end(JSON.stringify(stats));
        return;
      }

      if (u.pathname === "/__edge/health" && req.method === "GET") {
        const pref = readPreference(cfg.stateDir);
        res.writeHead(200, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
        res.end(JSON.stringify({ status: "ok", service: "local-edge-gateway", preference: pref.preference }));
        return;
      }

      if (u.pathname === "/__edge/status" && req.method === "GET") {
        const pref = readPreference(cfg.stateDir);
        const stats = readCacheStats(cfg.cacheDir);
        const total = (stats.hits || 0) + (stats.misses || 0);
        const hitRate = total > 0 ? Math.round((1000 * (stats.hits || 0)) / total) / 10 : 0;
        res.writeHead(200, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
        res.end(
          JSON.stringify({
            service: "rootmc-local-edge-terminal",
            preference: pref,
            cache: { ...stats, hit_rate: hitRate },
            ports: cfg.ports,
            checked_at: new Date().toISOString(),
          }),
        );
        return;
      }

      const pref = readPreference(cfg.stateDir);
      const base = pickUpstream(cfg, pref, host, u.pathname);
      const target = `${base}${u.pathname}${u.search}`;
      const key = cacheKey(req.method || "GET", u.pathname, u.search);
      const cacheable = isCacheablePath(req.method || "GET", u.pathname);
      const ttl = u.pathname.includes("map") || u.pathname.includes("tile") ? ttlMap : ttlDefault;

      let upstreamResult = null;
      let fromCache = false;
      let stale = false;

      try {
        upstreamResult = await fetchUpstream(target, req, timeoutMs);
      } catch (err) {
        const cached = cacheable ? loadCacheEntry(cfg.cacheDir, key) : null;
        if (cached) {
          const age = (Date.now() - Date.parse(cached.meta.storedAt || 0)) / 1000;
          if (age <= staleIfError) {
            upstreamResult = {
              statusCode: cached.meta.statusCode || 200,
              headers: cached.meta.headers || {},
              body: cached.body,
            };
            fromCache = true;
            stale = true;
          }
        }
        if (!upstreamResult) {
          res.writeHead(502, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
          res.end(JSON.stringify({ detail: "Upstream unavailable", error: String(err.message || err) }));
          return;
        }
      }

      if (
        cacheable
        && upstreamResult.statusCode >= 500
      ) {
        const cached = loadCacheEntry(cfg.cacheDir, key);
        if (cached) {
          const age = (Date.now() - Date.parse(cached.meta.storedAt || 0)) / 1000;
          if (age <= staleIfError) {
            upstreamResult = {
              statusCode: cached.meta.statusCode || 200,
              headers: { ...cached.meta.headers },
              body: cached.body,
            };
            fromCache = true;
            stale = true;
          }
        }
      }

      if (cacheable && !fromCache && upstreamResult.statusCode === 200) {
        const etag = `"${sha256Hex(upstreamResult.body).slice(0, 32)}"`;
        const headers = { ...upstreamResult.headers };
        headers["etag"] = etag;
        headers["cache-control"] = `public, max-age=${ttl}, stale-while-revalidate=${Math.min(300, ttl * 2)}`;
        headers["x-rootmc-cacheable"] = "1";
        saveCacheEntry(
          cfg.cacheDir,
          key,
          { statusCode: 200, headers, storedAt: new Date().toISOString(), path: u.pathname },
          upstreamResult.body,
          maxBytes,
        );
        upstreamResult.headers = headers;
        const inm = req.headers["if-none-match"];
        if (inm && inm === etag) {
          res.writeHead(304, headers);
          res.end();
          return;
        }
      } else if (fromCache) {
        const stats = readCacheStats(cfg.cacheDir);
        stats.hits = (stats.hits || 0) + 1;
        writeCacheStats(cfg.cacheDir, stats);
      } else if (cacheable) {
        const cached = loadCacheEntry(cfg.cacheDir, key);
        if (cached) {
          const age = (Date.now() - Date.parse(cached.meta.storedAt || 0)) / 1000;
          if (age <= ttl && upstreamResult.statusCode !== 200) {
            /* keep upstream */
          } else if (age <= ttl) {
            /* fresh enough unused */
          }
        }
        const stats = readCacheStats(cfg.cacheDir);
        stats.misses = (stats.misses || 0) + 1;
        writeCacheStats(cfg.cacheDir, stats);
      }

      const outHeaders = { ...upstreamResult.headers };
      delete outHeaders["content-length"];
      delete outHeaders["transfer-encoding"];
      applyCors(outHeaders);
      outHeaders["x-rootmc-edge"] = "1";
      outHeaders["x-rootmc-preference"] = pref.preference || "local";
      outHeaders["x-rootmc-cache"] = fromCache ? (stale ? "STALE" : "HIT") : "MISS";
      outHeaders["x-rootmc-upstream-ms"] = String(Date.now() - started);

      if (outHeaders["x-rootmc-cacheable"] === "1" || outHeaders["X-RootMC-Cacheable"] === "1") {
        const signed = signBody(upstreamResult.body, signingKey);
        if (signed) {
          outHeaders["x-rootmc-cache-signature"] = signed.signature;
          outHeaders["x-rootmc-cache-timestamp"] = signed.timestamp;
          outHeaders["x-rootmc-cache-alg"] = "HMAC-SHA256";
        }
      }

      res.writeHead(upstreamResult.statusCode, outHeaders);
      if (req.method === "HEAD") res.end();
      else res.end(upstreamResult.body);
    } catch (e) {
      res.writeHead(500, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
      res.end(JSON.stringify({ detail: String(e && e.message ? e.message : e) }));
    }
  });

  server.listen(port, "127.0.0.1", () => {
    console.log(`RootMC local-edge gateway on http://127.0.0.1:${port}`);
  });
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
