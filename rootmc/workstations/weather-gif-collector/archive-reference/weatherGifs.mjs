/**
 * Weather GIF leftovers on Ava-core (read-only).
 *
 * Collection moved to the Desktop Hawaii-Pacific v7 Python collector.
 * This module still lists / serves files already under media/weather/gifs.
 */
export const WEATHER_GIF_COLLECTOR_DIR =
  "/home/ava-core/Desktop/ava-weather-gif-collector-hawaii-pacific-v7./ava-weather-gif-collector";
export const WEATHER_GIF_COLLECTOR_NOTE =
  "Ava-core no longer downloads weather GIFs. Use the Desktop Hawaii-Pacific v7 collector.";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { AVA_HANDOFF } from "./config.mjs";
import { storePaths, pushStatusEvent } from "./store.mjs";
import { ecoHstDayKey } from "./ecoflow.mjs";
import { isAsleep } from "./sleepMode.mjs";
import { isPoweredOff } from "./powerDown.mjs";
import { readLiveness } from "./liveness.mjs";
import {
  weatherGifLocationKey,
  weatherGifLocationLabel,
  sortLocationKeys,
  organizeWeatherGifTree,
  weatherGifTreeNeedsOrganize,
  isWeatherGifLocationSlug,
  HAWAII_REGION_LOCATIONS,
  LOCATION_LABELS,
} from "./weatherGifLocation.mjs";

const PAGE_TIMEOUT_MS = 30_000;
const DOWNLOAD_TIMEOUT_MS = 180_000;
const MAX_DEPTH = 2;
const DOWNLOAD_CONCURRENCY = 4;
const USER_AGENT = "Mozilla/5.0 (compatible; Ava-Core-Weather-Monitor/1.0)";

export const SOURCE_PAGES = [
  ["GOES18_Hawaii", "https://www.star.nesdis.noaa.gov/goes/sector.php?sat=G18&sector=hi&refresh=true"],
  ["GOES18_Full_Disk", "https://www.star.nesdis.noaa.gov/GOES/fulldisk.php?sat=G18&src=nav"],
  ["GOES18_Northern_Pacific", "https://www.star.nesdis.noaa.gov/goes/sector.php?sat=G18&sector=np"],
  ["GOES18_Tropical_Pacific", "https://www.star.nesdis.noaa.gov/goes/sector.php?sat=G18&sector=tpw"],
  ["GOES19_Eastern_East_Pacific", "https://www.star.nesdis.noaa.gov/goes/sector.php?sat=G19&sector=eep"],
  ["GOES_Viewer", "https://www.star.nesdis.noaa.gov/GOES/index.php"],
  ["HFO", "https://www.weather.gov/hfo"],
  ["HFO_Radar", "https://www.weather.gov/hfo/radar"],
  ["HFO_Satellite", "https://www.weather.gov/hfo/satellite"],
  ["HFO_Analyses", "https://www.weather.gov/hfo/analyses"],
  ["HFO_Marine_AOR", "https://www.weather.gov/hfo/marine_aor"],
  ["HFO_Marine", "https://www.weather.gov/hfo/marine"],
  ["HFO_FireWx", "https://www.weather.gov/hfo/firewx"],
  ["HFO_Hydrology", "https://www.weather.gov/hfo/hydrology"],
  ["HFO_Aviation", "https://www.weather.gov/hfo/aviation"],
  ["HFO_Forecast", "https://www.weather.gov/hfo/forecast"],
  ["HFO_Pacific_Full", "https://www.weather.gov/hfo/enhanced_cpacfull"],
  ["HFO_GFE", "https://www.weather.gov/hfo/gfe_graphics"],
  ["HFO_Tropical", "https://www.weather.gov/hfo/tropical"],
  ["HFO_Obs", "https://www.weather.gov/hfo/obs"],
  ["HFO_HTI", "https://www.weather.gov/hfo/hti"],
  ["HFO_WatchWarn", "https://www.weather.gov/hfo/watchwarn"],
  ["HFO_Model", "https://www.weather.gov/hfo/model"],
];

const NOAA_HOSTS = new Set(["star.nesdis.noaa.gov", "www.star.nesdis.noaa.gov"]);
const NWS_HOSTS = new Set(["weather.gov", "www.weather.gov"]);
const NOAA_PREFIXES = ["/goes/", "/GOES/"];
const MEDIA_EXTS = [".gif", ".png", ".jpg", ".jpeg", ".webp"];
const MIN_MEDIA_BYTES = 2000;
const SKIP_MEDIA_RE =
  /\/bundles\/templating\/|\/social\/|header(_doc)?\.png|usa_gov|xml\.gif|rss-blue|collapse\.png|expand\.png|img_trans|kml-small|favicon|jquery|colorbox|close\.gif|blank\.gif|thumbnail\.jpe?g|\/loops\/[^/]+\/\d+\.(?:gif|png|jpe?g|webp)/i;
const HFO_GRAPHIC_PAGE_RE =
  /\/hfo\/?(radar|satellite|analyses|marine|firewx|hydrology|aviation|forecast|gfe|tropical|model|obs|watchwarn|enhanced|graphics|hti|quake|office_products|lsr)?(?:\/|$|\?)/i;
const HFO_RADAR_SITES = ["HAWAII", "PHKI", "PHKM", "PHMO", "PHWA"];
const HFO_CATALOG_JSON = [
  "gfe_graphics_oahu.json",
  "gfe_graphics_maui.json",
  "gfe_graphics_kauai.json",
  "gfe_graphics_bigisland.json",
  "gfe_graphics_lt.json",
  "gfe_graphics_lt_oahu.json",
  "gfe_graphics_lt_maui.json",
  "gfe_graphics_lt_kauai.json",
  "gfe_graphics_lt_bigisland.json",
  "firewx_graphics.json",
  "firewx_graphics_oahu.json",
  "firewx_graphics_maui.json",
  "firewx_graphics_kauai.json",
  "firewx_graphics_bigisland.json",
  "marine_graphics.json",
  "marinelt_graphics.json",
  "nps_graphics.json",
].map((name) => `https://www.weather.gov/source/hfo/${name}`);
const PACIFIC_TERMS = [
  "hawaii",
  "pacific",
  "pacus",
  "tropical",
  "eastern east pacific",
  "eastern pacific",
  "northern pacific",
  "south pacific",
  "central pacific",
  "honolulu",
  "hfo",
  "marine",
  "ocean",
];

let collecting = false;

function mediaRoot() {
  const fromEnv = String(process.env.AVA_MEDIA_DIR || "").trim();
  return fromEnv || path.join(AVA_HANDOFF || "/home/ava-core/ava", "media");
}

export function weatherGifRoot() {
  return path.join(mediaRoot(), "weather", "gifs");
}

export function weatherGifCurrentDir() {
  return path.join(weatherGifRoot(), "current");
}

export function weatherGifArchiveDir() {
  return path.join(weatherGifRoot(), "archive");
}

function statePath() {
  return path.join(storePaths().dir, "weather-gifs.json");
}

function loadState() {
  try {
    return JSON.parse(fs.readFileSync(statePath(), "utf8"));
  } catch {
    return { lastHourKey: "", lastDay: "", lastAt: 0, lastStats: null, disabledLocations: [], skippedProducts: [] };
  }
}

function saveState(s) {
  fs.mkdirSync(path.dirname(statePath()), { recursive: true });
  fs.writeFileSync(statePath(), JSON.stringify(s, null, 2), "utf8");
}

const NOT_NEEDED_RE = /not[\s._-]*needed/i;

export function isWeatherGifNotNeededName(name) {
  return NOT_NEEDED_RE.test(String(name || ""));
}

export function weatherGifSkippedProductKey(name) {
  let t = String(name || "");
  t = t.replace(/^current-/, "");
  t = t.replace(/^loop-24h-/, "");
  t = t.replace(/\.not[\s._-]*needed.*$/i, "");
  t = t.replace(/[\s._-]*not[\s._-]*needed.*$/i, "");
  t = t.replace(/\.(gif|png|jpe?g|webp)$/i, "");
  t = t.replace(/-\d{8}_\d{6}$/, "");
  t = t.replace(/[^\w]+/g, "_").replace(/^_+|_+$/g, "");
  t = t.replace(/_\d{8}_\d{6}$/, "");
  return t;
}

function skippedProductSet(st = loadState()) {
  return new Set(
    (Array.isArray(st.skippedProducts) ? st.skippedProducts : [])
      .map((k) => weatherGifSkippedProductKey(k).toLowerCase())
      .filter(Boolean),
  );
}

export function isWeatherGifProductSkipped(name, st = loadState()) {
  if (isWeatherGifNotNeededName(name)) return true;
  const key = weatherGifSkippedProductKey(name).toLowerCase();
  if (!key) return false;
  return skippedProductSet(st).has(key);
}

function addSkippedProduct(key) {
  const k = weatherGifSkippedProductKey(key);
  if (!k) return;
  const st = loadState();
  const skipped = skippedProductSet(st);
  skipped.add(k.toLowerCase());
  saveState({
    ...st,
    skippedProducts: [...skipped].sort(),
  });
}

/** Honor files renamed with ".not needed": remember the product and stop collecting it. */
export function learnWeatherGifSkippedFromDisk() {
  const root = weatherGifRoot();
  const marked = [];
  const walk = (dir, left) => {
    if (left < 0 || !fs.existsSync(dir)) return;
    let ents = [];
    try {
      ents = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const ent of ents) {
      if (ent.name.startsWith(".")) continue;
      const abs = path.join(dir, ent.name);
      if (ent.isDirectory()) walk(abs, left - 1);
      else if (ent.isFile() && isWeatherGifNotNeededName(ent.name)) marked.push(abs);
    }
  };
  walk(root, 5);
  for (const abs of marked) {
    addSkippedProduct(path.basename(abs));
    try {
      fs.unlinkSync(abs);
    } catch {
      /* ignore */
    }
  }
  const skipped = skippedProductSet();
  const drop = [];
  const walkDrop = (dir, left) => {
    if (left < 0 || !fs.existsSync(dir)) return;
    let ents = [];
    try {
      ents = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const ent of ents) {
      if (ent.name.startsWith(".")) continue;
      const abs = path.join(dir, ent.name);
      if (ent.isDirectory()) walkDrop(abs, left - 1);
      else if (ent.isFile() && isWeatherGifProductSkipped(ent.name)) drop.push(abs);
    }
  };
  walkDrop(root, 5);
  for (const abs of drop) {
    try {
      fs.unlinkSync(abs);
    } catch {
      /* ignore */
    }
    try {
      const dir = path.dirname(abs);
      if (fs.readdirSync(dir).length === 0) fs.rmdirSync(dir);
    } catch {
      /* ignore */
    }
  }
  return [...skipped].sort();
}

function disabledLocationSet(st = loadState()) {
  return new Set(
    (Array.isArray(st.disabledLocations) ? st.disabledLocations : [])
      .map((k) => String(k || "").trim())
      .filter(Boolean),
  );
}

export function isWeatherGifLocationEnabled(key, st = loadState()) {
  const loc = String(key || "").trim();
  if (!loc) return true;
  return !disabledLocationSet(st).has(loc);
}

export function listDisabledWeatherGifLocations(st = loadState()) {
  return [...disabledLocationSet(st)].sort();
}

export function setWeatherGifLocationEnabled(key, enabled) {
  const loc = String(key || "").trim();
  if (!loc) return { ok: false, detail: "missing_location" };
  const st = loadState();
  const disabled = disabledLocationSet(st);
  if (enabled) disabled.delete(loc);
  else disabled.add(loc);
  const next = { ...st, disabledLocations: [...disabled].sort() };
  saveState(next);
  return {
    ok: true,
    key: loc,
    enabled: Boolean(enabled),
    disabledLocations: next.disabledLocations,
  };
}

export function setWeatherGifLocationPreset(preset) {
  const st = loadState();
  const p = String(preset || "").trim().toLowerCase();
  let disabled = [];
  if (p === "all" || p === "on") {
    disabled = [];
  } else if (p === "hawaii" || p === "hawaii-region") {
    const keep = new Set(HAWAII_REGION_LOCATIONS);
    const present = [];
    try {
      const cur = weatherGifCurrentDir();
      if (fs.existsSync(cur)) {
        present.push(
          ...fs
            .readdirSync(cur, { withFileTypes: true })
            .filter((d) => d.isDirectory() && !d.name.startsWith("."))
            .map((d) => d.name),
        );
      }
    } catch {
      /* ignore */
    }
    disabled = [...new Set([...Object.keys(LOCATION_LABELS), ...present])]
      .filter((k) => k && !keep.has(k))
      .sort();
  } else {
    return { ok: false, detail: "unknown_preset" };
  }
  const next = { ...st, disabledLocations: disabled };
  saveState(next);
  return { ok: true, preset: p, disabledLocations: disabled };
}

export function utcHourKey(now = Date.now()) {
  const d = new Date(now);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}T${pad(d.getUTCHours())}`;
}

const HOUR_MS = 3600_000;

export function weatherGifsDue({ force = false, now = Date.now(), reason = "tick" } = {}) {
  if (force) return { due: true, reason: "force" };
  if (isAsleep(now) || isPoweredOff()) return { due: false, reason: "offline" };
  const st = loadState();
  const day = ecoHstDayKey(now);
  const lastAt = Number(st.lastAt || 0) || 0;
  const isBoot = reason === "boot" || reason === "morning";
  if (isBoot) {
    if (st.lastDay !== day || !lastAt) {
      return { due: true, reason: "morning_start" };
    }
    return { due: false, reason: "already_today", nextAt: lastAt + HOUR_MS };
  }
  if (!lastAt) return { due: true, reason: "morning_start" };
  const nextAt = lastAt + HOUR_MS;
  if (now >= nextAt) return { due: true, reason: "hourly_online", nextAt };
  return { due: false, reason: "wait_hour", nextAt };
}

export function nextWeatherGifsAt(now = Date.now()) {
  const gate = weatherGifsDue({ now, reason: "tick" });
  if (gate.due) return now;
  return Number(gate.nextAt) || now;
}

export function organizeWeatherGifsByLocation() {
  return organizeWeatherGifTree(weatherGifRoot());
}

export function ensureWeatherGifDirs() {
  const root = weatherGifRoot();
  const current = weatherGifCurrentDir();
  const archive = weatherGifArchiveDir();
  fs.mkdirSync(current, { recursive: true });
  fs.mkdirSync(archive, { recursive: true });
  learnWeatherGifSkippedFromDisk();
  const readme = path.join(root, "README.txt");
  if (!fs.existsSync(readme)) {
    fs.writeFileSync(
      readme,
      [
        "Leftover Ava-core weather media (collection moved to Desktop Hawaii-Pacific v7).",
        "current/<location>/  — leftover latest scenes",
        "archive/<location>/<title>/  — leftover snapshots",
        "loops/24h/<location>/  — leftover 24h loops",
        "Live collector: Desktop/ava-weather-gif-collector-hawaii-pacific-v7.",
        "",
      ].join("\n"),
      "utf8",
    );
  }
  if (weatherGifTreeNeedsOrganize(root)) {
    try {
      organizeWeatherGifTree(root);
    } catch {
      /* ignore */
    }
  }
  return { root, current, archive };
}

function sanitizeFilename(name) {
  let s = String(name || "").trim();
  s = s.replace(/[^\w\s-]/g, "");
  s = s.replace(/[-\s]+/g, "_");
  s = s.replace(/^_+|_+$/g, "");
  return s.slice(0, 180) || "weather_image";
}

function stripNoaaStamps(text) {
  return String(text || "")
    .replace(/\d{11,}/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function makeTitle(sourceName, linkText, url) {
  let text = String(linkText || "").trim();
  const lower = text.toLowerCase();
  if (!text || ["animated gif", "gif", "png", "download", "loop", "image"].includes(lower)) {
    try {
      const stem = path.parse(new URL(url).pathname).name;
      text = stem || "image";
    } catch {
      text = "image";
    }
  }
  return sanitizeFilename(
    `${sanitizeFilename(sourceName)}_${sanitizeFilename(stripNoaaStamps(text))}`,
  );
}

function isHttpUrl(url) {
  try {
    const u = new URL(url);
    return u.protocol === "http:" || u.protocol === "https:";
  } catch {
    return false;
  }
}

function isAllowedDiscoveryUrl(url) {
  if (!isHttpUrl(url)) return false;
  const u = new URL(url);
  const host = u.hostname.toLowerCase();
  const p = u.pathname;
  if (NOAA_HOSTS.has(host)) return NOAA_PREFIXES.some((pre) => p.startsWith(pre));
  if (NWS_HOSTS.has(host)) {
    return p === "/hfo" || p.startsWith("/hfo/") || p.startsWith("/images/hfo/") || p.startsWith("/wwamap/");
  }
  return false;
}

function looksHfoGraphicPage(url) {
  try {
    const u = new URL(url);
    if (!NWS_HOSTS.has(u.hostname.toLowerCase())) return false;
    const p = u.pathname;
    if (p === "/hfo" || p === "/hfo/") return true;
    return HFO_GRAPHIC_PAGE_RE.test(p);
  } catch {
    return false;
  }
}

function isHfoMediaUrl(url) {
  try {
    const host = new URL(url).hostname.toLowerCase();
    return host === "weather.gov" || host.endsWith(".weather.gov");
  } catch {
    return false;
  }
}

function isAllowedMediaHost(url) {
  try {
    const host = new URL(url).hostname.toLowerCase();
    return (
      host === "weather.gov" ||
      host.endsWith(".weather.gov") ||
      host === "noaa.gov" ||
      host.endsWith(".noaa.gov")
    );
  } catch {
    return false;
  }
}

function isMediaUrl(url) {
  const lower = String(url || "").toLowerCase();
  if (SKIP_MEDIA_RE.test(lower)) return false;
  if (!isAllowedMediaHost(url)) return false;
  return MEDIA_EXTS.some((ext) => lower.endsWith(ext) || lower.includes(`${ext}?`) || lower.includes(`${ext}#`));
}

function isMediaFilename(name) {
  const lower = String(name || "").toLowerCase();
  return MEDIA_EXTS.some((ext) => lower.endsWith(ext)) && !lower.startsWith(".");
}

function pageLooksPacific(url, text = "") {
  const combined = `${url} ${text}`.toLowerCase();
  return PACIFIC_TERMS.some((t) => combined.includes(t));
}

function looksGoesProduct(url) {
  const lower = String(url || "").toLowerCase();
  return ["sector.php", "sector_band.php", "fulldisk.php", "wfo.php"].some((m) =>
    lower.includes(m),
  );
}

function resolveUrl(base, href) {
  try {
    return new URL(href, base).href;
  } catch {
    return "";
  }
}

function stripTags(html) {
  return String(html || "")
    .replace(/<script[\s\S]*?<\/script>/gi, " ")
    .replace(/<style[\s\S]*?<\/style>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

async function fetchText(url) {
  const ac = new AbortController();
  const t = setTimeout(() => ac.abort(), PAGE_TIMEOUT_MS);
  try {
    const res = await fetch(url, {
      signal: ac.signal,
      headers: { "User-Agent": USER_AGENT, Accept: "text/html,application/json,*/*" },
      redirect: "follow",
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.text();
  } finally {
    clearTimeout(t);
  }
}

function extractGifsFromHtml(html, pageUrl, sourceName) {
  const found = new Map();
  const add = (raw, label = "") => {
    if (!raw) return;
    const href = String(raw).trim();
    if (!href || /^(data:|javascript:|#)/i.test(href)) return;
    const full = resolveUrl(pageUrl, href);
    if (!isHttpUrl(full) || !isMediaUrl(full)) return;
    const title = makeTitle(sourceName, label, full);
    if (isWeatherGifProductSkipped(title) || isWeatherGifNotNeededName(title)) return;
    found.set(full, title);
  };

  const aRe = /<a\s([^>]+)>([\s\S]*?)<\/a>/gi;
  let m;
  while ((m = aRe.exec(html))) {
    const href = /href\s*=\s*["']([^"']+)["']/i.exec(m[1]);
    if (href) add(href[1], stripTags(m[2]));
  }
  const imgRe = /<img\s([^>]+)>/gi;
  while ((m = imgRe.exec(html))) {
    const attrs = m[1];
    const src = /(?:src|data-src|data-original|data-image|data-url)\s*=\s*["']([^"']+)["']/i.exec(
      attrs,
    );
    const alt = /(?:alt|title)\s*=\s*["']([^"']+)["']/i.exec(attrs);
    if (src) add(src[1], alt?.[1] || "");
  }
  const srcRe = /<source\s([^>]+)>/gi;
  while ((m = srcRe.exec(html))) {
    const src = /src\s*=\s*["']([^"']+)["']/i.exec(m[1]);
    if (src) add(src[1], "");
  }
  const dataRe = /data-[a-z0-9_-]+\s*=\s*["']([^"']+\.(?:gif|png|jpe?g|webp)[^"']*)["']/gi;
  while ((m = dataRe.exec(html))) add(m[1], "");
  const quotedRe = /["']((?:https?:\/\/|\/images\/)[^"']{6,220}\.(?:gif|png|jpe?g|webp)(?:\?[^"']{0,80})?)["']/gi;
  while ((m = quotedRe.exec(html))) add(m[1], "");
  const absRe = /https?:\/\/[^\s"'<>]+\.(?:gif|png|jpe?g|webp)(?:\?[^\s"'<>]*)?/gi;
  while ((m = absRe.exec(html))) add(m[0], "");

  return [...found.entries()].map(([url, title]) => ({ title, url }));
}

function extractDiscoveryLinks(html, pageUrl) {
  const out = [];
  const aRe = /<a\s([^>]+)>([\s\S]*?)<\/a>/gi;
  let m;
  while ((m = aRe.exec(html))) {
    const href = /href\s*=\s*["']([^"']+)["']/i.exec(m[1]);
    if (!href) continue;
    const next = resolveUrl(pageUrl, href[1]).split("#")[0];
    if (!isAllowedDiscoveryUrl(next)) continue;
    out.push({ url: next, label: stripTags(m[2]) });
  }
  return out;
}

function hfoOnlyArg() {
  return process.argv.includes("--hfo-only");
}

function activeSourcePages() {
  if (hfoOnlyArg()) return SOURCE_PAGES.filter(([id]) => String(id).startsWith("HFO"));
  return SOURCE_PAGES;
}

async function discoverPages() {
  const queue = activeSourcePages().map(([source, url]) => ({
    source,
    url,
    depth: 0,
    explicit: true,
  }));
  const visited = new Set();
  const pages = [];

  while (queue.length) {
    const item = queue.shift();
    const key = item.url.split("#")[0];
    if (visited.has(key) || item.depth > MAX_DEPTH) continue;
    if (!isAllowedDiscoveryUrl(key)) continue;
    visited.add(key);

    let html;
    try {
      html = await fetchText(key);
    } catch {
      continue;
    }
    const text = stripTags(html);
    const relevant = item.explicit || pageLooksPacific(key, text);
    if (relevant) pages.push({ source: item.source, url: key, html });

    if (item.depth >= MAX_DEPTH) continue;
    let fromNws = false;
    try {
      fromNws = NWS_HOSTS.has(new URL(key).hostname.toLowerCase());
    } catch {
      fromNws = false;
    }
    for (const link of extractDiscoveryLinks(html, key)) {
      if (looksHfoGraphicPage(link.url)) {
        queue.push({
          source: item.source,
          url: link.url,
          depth: item.depth + 1,
          explicit: false,
        });
        continue;
      }
      if (fromNws) continue;
      if (looksGoesProduct(link.url) || pageLooksPacific(link.url, link.label)) {
        queue.push({
          source: item.source,
          url: link.url,
          depth: item.depth + 1,
          explicit: false,
        });
      }
    }
  }

  const unique = new Map();
  for (const p of pages) unique.set(p.url, p);
  return [...unique.values()];
}

function extraHfoRadarMedia() {
  const out = [];
  for (const site of HFO_RADAR_SITES) {
    for (const kind of ["0", "loop"]) {
      const url = `https://radar.weather.gov/ridge/standard/${site}_${kind}.gif`;
      out.push({ title: makeTitle("HFO_Radar", `${site}_${kind}`, url), url });
    }
  }
  return out.filter((g) => !isWeatherGifProductSkipped(g.title));
}

function collectMediaStrings(obj, out) {
  if (obj == null) return;
  if (typeof obj === "string") {
    if (/\.(gif|png|jpe?g|webp)$/i.test(obj.trim())) out.push(obj.trim());
    return;
  }
  if (Array.isArray(obj)) {
    for (const v of obj) collectMediaStrings(v, out);
    return;
  }
  if (typeof obj === "object") {
    for (const v of Object.values(obj)) collectMediaStrings(v, out);
  }
}

function periodStem(url) {
  try {
    const u = new URL(url);
    return `${u.origin}${u.pathname.replace(/_\d+(\.(?:gif|png|jpe?g|webp))$/i, "$1")}`;
  } catch {
    return String(url).replace(/_\d+(\.(?:gif|png|jpe?g|webp))$/i, "$1");
  }
}

function pickCurrentPeriod(items) {
  const best = new Map();
  for (const item of items) {
    const m = String(item.url).match(/_(\d+)(\.(?:gif|png|jpe?g|webp))(?:\?|#|$)/i);
    const n = m ? Number(m[1]) : 0;
    const stem = periodStem(item.url);
    const prev = best.get(stem);
    if (!prev || n < prev.n) best.set(stem, { ...item, n });
  }
  return [...best.values()].map(({ n: _n, ...item }) => item);
}

function catalogUrlsFromPages(pages) {
  const cats = new Set(HFO_CATALOG_JSON);
  const re = /\/source\/hfo\/[a-z0-9_.-]+\.json/gi;
  for (const page of pages) {
    const html = String(page.html || "");
    let m;
    while ((m = re.exec(html))) {
      const full = resolveUrl(page.url, m[0]);
      if (full) cats.add(full);
    }
  }
  return [...cats];
}

async function extractHfoCatalogMedia(pages) {
  const found = [];
  for (const cat of catalogUrlsFromPages(pages)) {
    let data;
    try {
      data = JSON.parse(await fetchText(cat));
    } catch {
      continue;
    }
    const raw = [];
    collectMediaStrings(data, raw);
    for (const href of raw) {
      const url = resolveUrl("https://www.weather.gov/", href);
      if (!isHttpUrl(url) || !isMediaUrl(url)) continue;
      found.push({ title: makeTitle("HFO_GFE", "", url), url });
    }
  }
  return pickCurrentPeriod(found).filter((g) => !isWeatherGifProductSkipped(g.title));
}

function sniffMediaExt(buf) {
  if (!buf || buf.length < 12) return "";
  const ascii6 = buf.subarray(0, 6).toString("ascii");
  if (ascii6 === "GIF87a" || ascii6 === "GIF89a") return ".gif";
  if (buf[0] === 0x89 && buf[1] === 0x50 && buf[2] === 0x4e && buf[3] === 0x47) return ".png";
  if (buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) return ".jpg";
  if (
    buf.subarray(0, 4).toString("ascii") === "RIFF" &&
    buf.subarray(8, 12).toString("ascii") === "WEBP"
  ) {
    return ".webp";
  }
  return "";
}

function fileHash(abs) {
  const h = crypto.createHash("sha256");
  h.update(fs.readFileSync(abs));
  return h.digest("hex");
}

async function downloadWeatherBuffer(url) {
  if (!isAllowedMediaHost(url)) throw new Error("blocked host");
  const ac = new AbortController();
  const t = setTimeout(() => ac.abort(), DOWNLOAD_TIMEOUT_MS);
  try {
    const res = await fetch(url, {
      signal: ac.signal,
      headers: { "User-Agent": USER_AGENT, Accept: "image/gif,image/png,image/jpeg,image/webp,*/*" },
      redirect: "follow",
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const buf = Buffer.from(await res.arrayBuffer());
    if (buf.length < MIN_MEDIA_BYTES) throw new Error("too small");
    const ext = sniffMediaExt(buf);
    if (!ext) throw new Error("not a weather image");
    return { buf, ext };
  } finally {
    clearTimeout(t);
  }
}

function atomicWrite(dest, buf) {
  const tmp = `${dest}.${process.pid}.${Date.now()}.tmp`;
  fs.writeFileSync(tmp, buf);
  fs.renameSync(tmp, dest);
}

async function processGif(title, url, archiveTimestamp) {
  const safe = sanitizeFilename(title);
  if (isWeatherGifProductSkipped(safe) || isWeatherGifProductSkipped(title)) return "skippedProduct";
  const loc = weatherGifLocationKey(safe);
  if (!isWeatherGifLocationEnabled(loc)) return "skippedLocation";
  const currentDir = path.join(weatherGifCurrentDir(), loc);
  fs.mkdirSync(currentDir, { recursive: true });
  const { buf, ext } = await downloadWeatherBuffer(url);
  const currentPath = path.join(currentDir, `current-${safe}${ext}`);
  let oldHash = null;
  let hashFrom = null;
  if (fs.existsSync(currentPath)) hashFrom = currentPath;
  else {
    for (const oldExt of MEDIA_EXTS) {
      const legacyLoc = path.join(currentDir, `current-${safe}${oldExt}`);
      const legacyFlat = path.join(weatherGifCurrentDir(), `current-${safe}${oldExt}`);
      if (fs.existsSync(legacyLoc)) {
        hashFrom = legacyLoc;
        break;
      }
      if (fs.existsSync(legacyFlat)) {
        hashFrom = legacyFlat;
        break;
      }
    }
  }
  if (hashFrom) {
    try {
      oldHash = fileHash(hashFrom);
    } catch {
      oldHash = null;
    }
  }
  const tmpHash = crypto.createHash("sha256").update(buf).digest("hex");
  atomicWrite(currentPath, buf);
  if (hashFrom && hashFrom !== currentPath) {
    try {
      fs.unlinkSync(hashFrom);
    } catch {
      /* ignore */
    }
  }
  if (oldHash === tmpHash) return "unchanged";

  const stamp = archiveTimestamp
    .toISOString()
    .replace(/[-:]/g, "")
    .replace("T", "_")
    .slice(0, 15);
  const archDir = path.join(weatherGifArchiveDir(), loc, safe);
  fs.mkdirSync(archDir, { recursive: true });
  atomicWrite(path.join(archDir, `${safe}-${stamp}${ext}`), buf);
  return "updated";
}

async function mapPool(items, limit, fn) {
  const out = new Array(items.length);
  let i = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) || 1 }, async () => {
    while (i < items.length) {
      const idx = i++;
      try {
        out[idx] = await fn(items[idx], idx);
      } catch (err) {
        out[idx] = { error: err?.message || String(err) };
      }
    }
  });
  await Promise.all(workers);
  return out;
}

export async function refreshWeatherGifs() {
  return {
    ok: true,
    skipped: true,
    reason: "moved",
    collector: WEATHER_GIF_COLLECTOR_DIR,
    message: WEATHER_GIF_COLLECTOR_NOTE,
    updated: 0,
    unchanged: 0,
    failed: 0,
    skippedLocation: 0,
    skippedProduct: 0,
    pages: 0,
    gifs: 0,
  };
}

export async function tickWeatherGifs() {
  return {
    ok: true,
    skipped: true,
    reason: "moved",
    collector: WEATHER_GIF_COLLECTOR_DIR,
    message: WEATHER_GIF_COLLECTOR_NOTE,
  };
}

function listGifFiles(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs
    .readdirSync(dir)
    .filter((n) => isMediaFilename(n) && !isWeatherGifProductSkipped(n) && !isWeatherGifNotNeededName(n))
    .map((name) => {
      const abs = path.join(dir, name);
      let size = 0;
      let mtimeMs = 0;
      try {
        const st = fs.statSync(abs);
        if (!st.isFile()) return null;
        size = st.size;
        mtimeMs = st.mtimeMs;
      } catch {
        return null;
      }
      return { name, size, mtimeMs, path: abs };
    })
    .filter(Boolean)
    .sort((a, b) => String(a.name).localeCompare(String(b.name)));
}

function listLocationSlugs(dir) {
  if (!fs.existsSync(dir)) return [];
  try {
    return fs
      .readdirSync(dir, { withFileTypes: true })
      .filter((d) => d.isDirectory() && !d.name.startsWith(".") && isWeatherGifLocationSlug(d.name))
      .map((d) => d.name);
  } catch {
    return [];
  }
}

function countGifsDeep(dir, depth = 4) {
  let files = 0;
  let latestMtimeMs = 0;
  const walk = (d, left) => {
    if (left < 0 || !fs.existsSync(d)) return;
    let ents = [];
    try {
      ents = fs.readdirSync(d, { withFileTypes: true });
    } catch {
      return;
    }
    for (const ent of ents) {
      if (ent.name.startsWith(".")) continue;
      const abs = path.join(d, ent.name);
      if (ent.isFile() && isMediaFilename(ent.name)) {
        files += 1;
        try {
          latestMtimeMs = Math.max(latestMtimeMs, fs.statSync(abs).mtimeMs);
        } catch {
          /* ignore */
        }
      } else if (ent.isDirectory()) walk(abs, left - 1);
    }
  };
  walk(dir, depth);
  if (!latestMtimeMs && fs.existsSync(dir)) {
    try {
      latestMtimeMs = fs.statSync(dir).mtimeMs;
    } catch {
      latestMtimeMs = 0;
    }
  }
  return { files, latestMtimeMs };
}

function dirMeta(kind, rel, abs, extra = {}) {
  const deep = countGifsDeep(abs);
  return {
    kind,
    rel,
    abs,
    files: deep.files,
    latestMtimeMs: deep.latestMtimeMs,
    ...extra,
  };
}

function listCurrentGifs() {
  const current = weatherGifCurrentDir();
  const out = [];
  const push = (f, loc, urlParts) => {
    out.push({
      ...f,
      location: loc,
      locationLabel: weatherGifLocationLabel(loc),
      url: `/weather/gifs/${urlParts.map(encodeURIComponent).join("/")}`,
    });
  };
  for (const f of listGifFiles(current)) {
    push(f, weatherGifLocationKey(f.name), ["current", f.name]);
  }
  for (const loc of listLocationSlugs(current)) {
    for (const f of listGifFiles(path.join(current, loc))) {
      push(f, loc, ["current", loc, f.name]);
    }
  }
  return out.sort((a, b) => String(a.name).localeCompare(String(b.name)));
}

function countArchiveProducts() {
  const archive = weatherGifArchiveDir();
  let n = 0;
  for (const loc of listLocationSlugs(archive)) {
    const abs = path.join(archive, loc);
    try {
      n += fs.readdirSync(abs, { withFileTypes: true }).filter((d) => d.isDirectory()).length;
    } catch {
      /* ignore */
    }
  }
  return n;
}

export function listWeatherGifDirectories() {
  const { root, current, archive } = ensureWeatherGifDirs();
  const loops = path.join(root, "loops", "24h");
  const directories = [
    dirMeta("root", "media/weather/gifs", root),
    dirMeta("current", "media/weather/gifs/current", current),
    dirMeta("archive", "media/weather/gifs/archive", archive),
    dirMeta("loops-24h", "media/weather/gifs/loops/24h", loops),
  ];
  for (const loc of sortLocationKeys(listLocationSlugs(current))) {
    directories.push(
      dirMeta("location-current", `media/weather/gifs/current/${loc}`, path.join(current, loc), {
        location: loc,
        locationLabel: weatherGifLocationLabel(loc),
      }),
    );
  }
  return {
    ok: true,
    root,
    current,
    archive,
    desktopAlias: "/home/ava-core/Desktop/Ava-media/weather/gifs",
    directories,
  };
}

export async function listWeatherGifsPayload() {
  const st = loadState();
  const tree = listWeatherGifDirectories();
  const current = listCurrentGifs().filter((f) => isWeatherGifLocationEnabled(f.location, st));
  const legacy = listGifFiles(weatherGifRoot())
    .map((f) => ({
      ...f,
      location: weatherGifLocationKey(f.name),
      locationLabel: weatherGifLocationLabel(weatherGifLocationKey(f.name)),
      url: `/weather/gifs/${encodeURIComponent(f.name)}`,
    }))
    .filter((f) => isWeatherGifLocationEnabled(f.location, st));
  let loops = [];
  let loopBuilder = null;
  let loopStatus = { looping: false, lastResult: null };
  try {
    const loopMod = await import("./weatherGifLoops.mjs");
    loops = loopMod.listWeatherGifLoops();
    loopBuilder = loopMod.weatherGifLoopScripts();
    loopStatus = loopMod.weatherGifLoopStatus();
  } catch {
    loops = [];
  }
  loops = loops.filter((f) => isWeatherGifLocationEnabled(f.location, st));
  const locKeys = sortLocationKeys([
    ...new Set([
      ...listLocationSlugs(weatherGifCurrentDir()),
      ...listLocationSlugs(weatherGifArchiveDir()),
      ...listLocationSlugs(path.join(weatherGifRoot(), "loops", "24h")),
      ...Object.keys(LOCATION_LABELS),
    ]),
  ]);
  const locations = locKeys.map((key) => {
    const cur = countGifsDeep(path.join(weatherGifCurrentDir(), key));
    const loopN = countGifsDeep(path.join(weatherGifRoot(), "loops", "24h", key));
    const arch = countGifsDeep(path.join(weatherGifArchiveDir(), key));
    return {
      key,
      label: weatherGifLocationLabel(key),
      enabled: isWeatherGifLocationEnabled(key, st),
      current: cur.files,
      loops: loopN.files,
      archive: arch.files,
      abs: path.join(weatherGifCurrentDir(), key),
    };
  });
  return {
    ok: true,
    updatedAt: new Date().toISOString(),
    state: st,
    collecting: false,
    nextAt: null,
    due: { due: false, reason: "moved" },
    collectorMoved: true,
    collector: WEATHER_GIF_COLLECTOR_DIR,
    message: WEATHER_GIF_COLLECTOR_NOTE,
    updater: {
      jobId: "weather-gifs",
      module: "desktop Hawaii-Pacific v7 collector",
      abs: WEATHER_GIF_COLLECTOR_DIR,
      schedule: "off Ava-core · Desktop collector",
      sources: [],
      lastAt: st.lastAt || null,
      lastReason: "moved",
      lastStats: st.lastStats || null,
      lastDay: st.lastDay || null,
      collecting: false,
      nextAt: null,
      scripts: [
        {
          id: "weather-gifs",
          role: "collector (moved)",
          module: "weathergifs.py",
          abs: WEATHER_GIF_COLLECTOR_DIR,
          schedule: "Desktop Hawaii-Pacific v7 · not Ava-core",
        },
      ],
    },
    loopBuilder,
    loopStatus,
    loops,
    locations,
    disabledLocations: listDisabledWeatherGifLocations(st),
    skippedProducts: [...skippedProductSet(st)].sort(),
    archiveProductCount: countArchiveProducts(),
    ...tree,
    directories: tree.directories.filter((d) => d.kind !== "archive-product"),
    current,
    legacy,
  };
}

export function resolveWeatherGifFile(relParts = []) {
  const root = weatherGifRoot();
  const joined = path.normalize(path.join(root, ...relParts.map((p) => String(p || ""))));
  if (!joined.startsWith(root + path.sep) && joined !== root) return null;
  if (!MEDIA_EXTS.some((ext) => joined.toLowerCase().endsWith(ext))) return null;
  if (!fs.existsSync(joined) || !fs.statSync(joined).isFile()) return null;
  return joined;
}

export function weatherGifContentType(abs) {
  const ext = path.extname(String(abs || "")).toLowerCase();
  if (ext === ".png") return "image/png";
  if (ext === ".jpg" || ext === ".jpeg") return "image/jpeg";
  if (ext === ".webp") return "image/webp";
  return "image/gif";
}

export function weatherGifsMeta(now = Date.now()) {
  const st = loadState();
  return {
    hourKey: utcHourKey(now),
    lastHourKey: st.lastHourKey || null,
    lastDay: st.lastDay || null,
    lastReason: "moved",
    lastAt: st.lastAt || null,
    lastStats: st.lastStats || null,
    nextAt: null,
    collecting: false,
    collectorMoved: true,
    collector: WEATHER_GIF_COLLECTOR_DIR,
    message: WEATHER_GIF_COLLECTOR_NOTE,
    dirs: listWeatherGifDirectories(),
  };
}

const isMain =
  process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const r = await tickWeatherGifs();
  console.log(JSON.stringify(r, null, 2));
}
