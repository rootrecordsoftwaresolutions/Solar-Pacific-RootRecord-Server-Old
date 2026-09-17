/**
 * Local static site server + /api/* proxy to local edge gateway or local API.
 */
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..");
const CONFIG_PATH = process.env.ROOTMC_EDGE_CONFIG || path.join(ROOT, "config.json");
const cfg = JSON.parse(fs.readFileSync(CONFIG_PATH, "utf8"));

const webRootCandidates = [
  path.join(cfg.workspaceRoot, "Web Files", "rootmc-web", "build"),
  path.join(cfg.workspaceRoot, "Web Files", "rootmc-web", "public"),
];
const webRoot = webRootCandidates.find((p) => fs.existsSync(p)) || webRootCandidates[0];
const port = Number(cfg.ports.site || 8790);
const apiUpstream = process.env.ROOTMC_SITE_API_UPSTREAM || `http://127.0.0.1:${cfg.ports.gateway || 8791}`;

const TYPES = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".webp": "image/webp",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".woff2": "font/woff2",
  ".map": "application/json",
};

function safeJoin(root, reqPath) {
  const decoded = decodeURIComponent(reqPath.split("?")[0]);
  const cleaned = decoded.replace(/^\/+/, "").replace(/\.\./g, "");
  const full = path.normalize(path.join(root, cleaned || "index.html"));
  if (!full.startsWith(path.normalize(root))) return null;
  return full;
}

function resolveFile(reqPath) {
  let filePath = safeJoin(webRoot, reqPath);
  if (!filePath) return null;
  if (fs.existsSync(filePath) && fs.statSync(filePath).isDirectory()) {
    const idx = path.join(filePath, "index.html");
    if (fs.existsSync(idx)) return idx;
  }
  if (!fs.existsSync(filePath) && !path.extname(filePath)) {
    const idx = path.join(filePath, "index.html");
    if (fs.existsSync(idx)) return idx;
    const rootIdx = path.join(webRoot, "index.html");
    if (fs.existsSync(rootIdx)) return rootIdx;
  }
  return fs.existsSync(filePath) ? filePath : null;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || "/", `http://127.0.0.1:${port}`);
  if (url.pathname.startsWith("/api/") || url.pathname === "/api" || url.pathname === "/health"
      || url.pathname.startsWith("/v1/")) {
    const target = `${apiUpstream}${url.pathname}${url.search}`;
    try {
      const headers = { ...req.headers };
      delete headers.host;
      const init = {
        method: req.method,
        headers,
        redirect: "manual",
      };
      if (req.method !== "GET" && req.method !== "HEAD") {
        const chunks = [];
        for await (const c of req) chunks.push(c);
        init.body = Buffer.concat(chunks);
      }
      const upstream = await fetch(target, init);
      const out = {};
      upstream.headers.forEach((v, k) => {
        out[k] = v;
      });
      const buf = Buffer.from(await upstream.arrayBuffer());
      res.writeHead(upstream.status, out);
      res.end(buf);
    } catch (e) {
      res.writeHead(502, { "content-type": "application/json", "cache-control": "no-store" });
      res.end(JSON.stringify({ detail: `Site proxy failed: ${e.message || e}` }));
    }
    return;
  }

  const filePath = resolveFile(url.pathname);
  if (!filePath) {
    res.writeHead(404, { "content-type": "text/plain", "cache-control": "no-store" });
    res.end("Not Found");
    return;
  }
  const ext = path.extname(filePath).toLowerCase();
  const body = fs.readFileSync(filePath);
  const etag = `"${crypto.createHash("sha256").update(body).digest("hex").slice(0, 32)}"`;
  if (req.headers["if-none-match"] === etag) {
    res.writeHead(304, { etag, "cache-control": "public, max-age=30" });
    res.end();
    return;
  }
  res.writeHead(200, {
    "content-type": TYPES[ext] || "application/octet-stream",
    "cache-control": ext === ".html" ? "public, max-age=30" : "public, max-age=300",
    etag,
  });
  res.end(body);
});

server.listen(port, "127.0.0.1", () => {
  console.log(`RootMC local site http://127.0.0.1:${port} root=${webRoot} api→${apiUpstream}`);
});
