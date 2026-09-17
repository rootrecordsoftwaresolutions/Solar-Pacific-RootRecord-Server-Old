import { readFileSync, writeFileSync, readdirSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const polygonFile = join(
  root,
  "Server Handoffs\2. RootMC - Towny/plugins/RootRecord/spawnarea-refined.txt",
);
const holoDir = join(
  root,
  "Server Handoffs\2. RootMC - Towny/plugins/DecentHolograms/holograms",
);

const SPAWN_HOLOS = [
  "welcome.yml",
  "basiccommands.yml",
  "territorieshelp.yml",
  "goldecohelp.yml",
  "townyhelp.yml",
  "votehelp.yml",
  "rules.yml",
  "adminlist.yml",
  "baltopplayersmcommo.yml",
  "baltoptownsnations.yml",
];

const PLATFORM_Y = 98;
const INSET = 3;
const MIN_SEP = 6;
const SEED = 0x726f6f74; // "root"

function parsePolygon(text) {
  const pts = [];
  for (const line of text.split(/\r?\n/)) {
    const m = line.match(/^\d+,world,(-?\d+),(-?\d+)$/);
    if (m) pts.push([Number(m[1]), Number(m[2])]);
  }
  if (pts.length < 3) throw new Error("polygon too small");
  return pts;
}

function seededRand(state) {
  let s = state >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 0x100000000;
  };
}

function pointInPolygon(x, z, poly) {
  let inside = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const [xi, zi] = poly[i];
    const [xj, zj] = poly[j];
    const intersect =
      zi > z !== zj > z && x < ((xj - xi) * (z - zi)) / (zj - zi) + xi;
    if (intersect) inside = !inside;
  }
  return inside;
}

function distToSegment(px, pz, ax, az, bx, bz) {
  const dx = bx - ax;
  const dz = bz - az;
  const len2 = dx * dx + dz * dz;
  if (len2 < 1e-9) return Math.hypot(px - ax, pz - az);
  let t = ((px - ax) * dx + (pz - az) * dz) / len2;
  t = Math.max(0, Math.min(1, t));
  const qx = ax + t * dx;
  const qz = az + t * dz;
  return Math.hypot(px - qx, pz - qz);
}

function distToPolygonEdge(x, z, poly) {
  let min = Infinity;
  for (let i = 0; i < poly.length; i++) {
    const [ax, az] = poly[i];
    const [bx, bz] = poly[(i + 1) % poly.length];
    min = Math.min(min, distToSegment(x, z, ax, az, bx, bz));
  }
  return min;
}

function bbox(poly) {
  let minX = Infinity,
    maxX = -Infinity,
    minZ = Infinity,
    maxZ = -Infinity;
  for (const [x, z] of poly) {
    minX = Math.min(minX, x);
    maxX = Math.max(maxX, x);
    minZ = Math.min(minZ, z);
    maxZ = Math.max(maxZ, z);
  }
  return { minX, maxX, minZ, maxZ };
}

function pickPoints(poly, count, rand) {
  const candidates = [];
  const { minX, maxX, minZ, maxZ } = bbox(poly);
  for (let x = minX + INSET; x <= maxX - INSET; x++) {
    for (let z = minZ + INSET; z <= maxZ - INSET; z++) {
      if (!pointInPolygon(x + 0.5, z + 0.5, poly)) continue;
      if (distToPolygonEdge(x + 0.5, z + 0.5, poly) < INSET) continue;
      candidates.push({ x: x + 0.5, z: z + 0.5 });
    }
  }
  if (candidates.length < count) {
    throw new Error(`only ${candidates.length} interior cells for ${count} holograms`);
  }
  // Fisherâ€“Yates shuffle with seeded rand
  for (let i = candidates.length - 1; i > 0; i--) {
    const j = Math.floor(rand() * (i + 1));
    [candidates[i], candidates[j]] = [candidates[j], candidates[i]];
  }
  const picked = [];
  for (const c of candidates) {
    if (picked.some((p) => Math.hypot(p.x - c.x, p.z - c.z) < MIN_SEP)) continue;
    picked.push(c);
    if (picked.length >= count) break;
  }
  if (picked.length < count) {
    throw new Error(`only placed ${picked.length}/${count} holograms (min sep ${MIN_SEP})`);
  }
  return picked;
}

function fmt(n) {
  return n.toFixed(3);
}

function setLocation(filePath, x, z, y = PLATFORM_Y) {
  let text = readFileSync(filePath, "utf8");
  const loc = `location: world:${fmt(x)}:${fmt(y)}:${fmt(z)}`;
  if (!/^location: /m.test(text)) throw new Error(`no location in ${filePath}`);
  text = text.replace(/^location: .+$/m, loc);
  writeFileSync(filePath, text);
}

const poly = parsePolygon(readFileSync(polygonFile, "utf8"));
const rand = seededRand(SEED);
const points = pickPoints(poly, SPAWN_HOLOS.length, rand);

console.log("# Spawn hologram placements (inside spawnarea-refined.txt polygon)\n");
const rows = [];
for (let i = 0; i < SPAWN_HOLOS.length; i++) {
  const name = SPAWN_HOLOS[i];
  const file = join(holoDir, name);
  const { x, z } = points[i];
  const y = PLATFORM_Y;
  setLocation(file, x, z, y);
  rows.push({ name, x, z, y });
  console.log(`${name}\tworld:${fmt(x)}:${fmt(y)}:${fmt(z)}`);
}
