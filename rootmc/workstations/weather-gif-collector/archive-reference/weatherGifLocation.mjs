/**
 * Map NOAA GOES / NWS HFO GIF names onto location folders.
 * Used by collect, archive, 24h loops, and the weather GIF board.
 */
import fs from "node:fs";
import path from "node:path";

/** NESDIS ABI/GLM sector code → folder slug. */
export const NOAA_SECTOR_LOCATIONS = {
  HI: "hawaii",
  NP: "northern-pacific",
  TPW: "tropical-pacific",
  EEP: "eastern-pacific",
  SP: "south-pacific",
  TSP: "tropical-south-pacific",
  WUS: "west-us",
  PNW: "pacific-northwest",
  PSW: "pacific-southwest",
  AK: "alaska",
  CAK: "central-alaska",
  SEA: "southeast-alaska",
  GWAS: "gulf-of-alaska",
  MEX: "mexico",
  CAN: "canada",
  NA: "north-america",
  CONUS: "conus",
  CAR: "caribbean",
  CGL: "great-lakes",
  NR: "northeast-us",
  NE: "northeast-us",
  SE: "southeast-us",
  SR: "southern-rockies",
  EUS: "east-us",
  GA: "gulf",
  SMV: "southern-mississippi",
  UMV: "upper-mississippi",
  TAW: "tropical-atlantic",
  PR: "puerto-rico",
  CAM: "central-america",
  NSA: "northern-south-america",
  SSA: "southern-south-america",
};

export const LOCATION_LABELS = {
  hawaii: "Hawaii",
  kauai: "Kauai",
  oahu: "Oahu",
  maui: "Maui",
  "big-island": "Big Island",
  molokai: "Molokai",
  lanai: "Lanai",
  "hawaii-marine": "Hawaii marine",
  "northern-pacific": "Northern Pacific",
  "tropical-pacific": "Tropical Pacific",
  "eastern-pacific": "Eastern Pacific",
  "south-pacific": "South Pacific",
  "tropical-south-pacific": "Tropical South Pacific",
  "west-us": "West US",
  "pacific-northwest": "Pacific Northwest",
  "pacific-southwest": "Pacific Southwest",
  alaska: "Alaska",
  "central-alaska": "Central Alaska",
  "southeast-alaska": "Southeast Alaska",
  "gulf-of-alaska": "Gulf of Alaska",
  mexico: "Mexico",
  canada: "Canada",
  "north-america": "North America",
  conus: "CONUS",
  caribbean: "Caribbean",
  "great-lakes": "Great Lakes",
  "northeast-us": "Northeast US",
  "southeast-us": "Southeast US",
  "southern-rockies": "Southern Rockies",
  "east-us": "East US",
  gulf: "Gulf",
  "southern-mississippi": "Southern Mississippi Valley",
  "upper-mississippi": "Upper Mississippi Valley",
  "tropical-atlantic": "Tropical Atlantic",
  "puerto-rico": "Puerto Rico",
  "central-america": "Central America",
  "northern-south-america": "Northern South America",
  "southern-south-america": "Southern South America",
  "full-disk": "Full disk",
  other: "Other",
};

export const HAWAII_ISLAND_LOCATIONS = [
  "hawaii",
  "kauai",
  "oahu",
  "maui",
  "big-island",
  "molokai",
  "lanai",
  "hawaii-marine",
];

export const HAWAII_REGION_LOCATIONS = [
  ...HAWAII_ISLAND_LOCATIONS,
  "northern-pacific",
  "tropical-pacific",
  "eastern-pacific",
];

export const LOCATION_ORDER = [
  "hawaii",
  "kauai",
  "oahu",
  "maui",
  "big-island",
  "molokai",
  "lanai",
  "hawaii-marine",
  "northern-pacific",
  "tropical-pacific",
  "eastern-pacific",
  "south-pacific",
  "tropical-south-pacific",
  "pacific-northwest",
  "pacific-southwest",
  "west-us",
  "alaska",
  "central-alaska",
  "southeast-alaska",
  "gulf-of-alaska",
  "full-disk",
];

export function weatherGifLocationLabel(key) {
  return LOCATION_LABELS[key] || key || "Other";
}

export function isWeatherGifLocationSlug(name) {
  return Boolean(LOCATION_LABELS[String(name || "")]);
}

export function sortLocationKeys(keys) {
  const rank = new Map(LOCATION_ORDER.map((k, i) => [k, i]));
  return [...keys].sort((a, b) => {
    const ra = rank.has(a) ? rank.get(a) : 500;
    const rb = rank.has(b) ? rank.get(b) : 500;
    if (ra !== rb) return ra - rb;
    if (a === "other") return 1;
    if (b === "other") return -1;
    return a.localeCompare(b);
  });
}

export function weatherGifLocationKey(name) {
  const t = String(name || "");
  const low = t.toLowerCase();

  if (/(^|[^a-z])kauai([^a-z]|$)|_phki_|phki_/i.test(low)) return "kauai";
  if (/(^|[^a-z])maui([^a-z]|$)/i.test(low)) return "maui";
  if (/(^|[^a-z])(oahu|honolulu)([^a-z]|$)|hnl_/i.test(low)) return "oahu";
  if (/big[_-]?island|hilo|kona|_phkm_|phkm_|_phwa_|phwa_/i.test(low)) return "big-island";
  if (/molokai|_phmo_|phmo_/i.test(low)) return "molokai";
  if (/(^|[^a-z])lanai([^a-z]|$)/i.test(low)) return "lanai";
  if (/(^|_)bi_|nps/i.test(low)) return "big-island";

  const sec = t.match(/GOES\d+_(?:ABI|GLM)_([A-Z0-9]+)_/i);
  if (sec) {
    const mapped = NOAA_SECTOR_LOCATIONS[sec[1].toUpperCase()];
    if (mapped) return mapped;
  }

  if (/cphc|marine_aor|sea_surface/.test(low)) return "hawaii-marine";
  if (/hfo_/.test(low) && /npac/.test(low)) return "northern-pacific";
  if (/hfo_/.test(low) || /hawaii_loop|hawaii_0/.test(low)) return "hawaii";
  if (/goes18_hawaii/.test(low) || /goes19_hawaii/.test(low)) return "hawaii";
  if (/northern_pacific/.test(low)) return "northern-pacific";
  if (/tropical_pacific/.test(low)) return "tropical-pacific";
  if (/eastern_east_pacific|eastern_pacific/.test(low)) return "eastern-pacific";
  if (/full_disk/.test(low)) return "full-disk";
  return "other";
}

function safeRename(src, dest) {
  if (src === dest) return "same";
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  if (!fs.existsSync(src)) return "missing";
  if (fs.existsSync(dest)) {
    try {
      const a = fs.statSync(src);
      const b = fs.statSync(dest);
      if (a.ino === b.ino && a.dev === b.dev) {
        if (src !== dest) fs.unlinkSync(src);
        return "same";
      }
      if (b.mtimeMs >= a.mtimeMs && b.size >= a.size) {
        fs.unlinkSync(src);
        return "kept-dest";
      }
      fs.unlinkSync(dest);
    } catch {
      /* replace */
    }
  }
  try {
    fs.renameSync(src, dest);
    return "moved";
  } catch {
    fs.copyFileSync(src, dest);
    fs.unlinkSync(src);
    return "copied";
  }
}

function rmdirIfEmpty(dir) {
  try {
    if (fs.readdirSync(dir).length === 0) fs.rmdirSync(dir);
  } catch {
    /* ignore */
  }
}

function moveGifFile(src, destRoot, loc) {
  const dest = path.join(destRoot, loc, path.basename(src));
  return safeRename(src, dest);
}

/**
 * Move flat current/archive/loops GIFs into location folders.
 * Cheap no-op once everything already lives under a location slug.
 */
export function organizeWeatherGifTree(root) {
  const current = path.join(root, "current");
  const archive = path.join(root, "archive");
  const loops = path.join(root, "loops", "24h");
  const stats = { moved: 0, kept: 0, skipped: 0, locations: {} };

  const bump = (loc, how) => {
    if (how === "moved" || how === "copied") stats.moved += 1;
    else if (how === "kept-dest") stats.kept += 1;
    else stats.skipped += 1;
    stats.locations[loc] = (stats.locations[loc] || 0) + (how === "moved" || how === "copied" ? 1 : 0);
  };

  if (fs.existsSync(current)) {
    for (const name of fs.readdirSync(current)) {
      const abs = path.join(current, name);
      let st;
      try {
        st = fs.statSync(abs);
      } catch {
        continue;
      }
      if (!st.isFile() || !/\.(gif|png|jpe?g|webp)$/i.test(name)) continue;
      const loc = weatherGifLocationKey(name);
      bump(loc, moveGifFile(abs, current, loc));
    }
  }

  if (fs.existsSync(loops)) {
    for (const name of fs.readdirSync(loops)) {
      const abs = path.join(loops, name);
      let st;
      try {
        st = fs.statSync(abs);
      } catch {
        continue;
      }
      if (!st.isFile() || !name.toLowerCase().endsWith(".gif") || name.startsWith("_")) continue;
      const loc = weatherGifLocationKey(name);
      bump(loc, moveGifFile(abs, loops, loc));
    }
  }

  if (fs.existsSync(archive)) {
    for (const name of fs.readdirSync(archive)) {
      const abs = path.join(archive, name);
      let st;
      try {
        st = fs.statSync(abs);
      } catch {
        continue;
      }
      if (!st.isDirectory() || name.startsWith(".")) continue;
      if (isWeatherGifLocationSlug(name)) continue;
      const loc = weatherGifLocationKey(name);
      const destDir = path.join(archive, loc, name);
      if (fs.existsSync(destDir)) {
        for (const f of fs.readdirSync(abs)) {
          bump(loc, safeRename(path.join(abs, f), path.join(destDir, f)));
        }
        rmdirIfEmpty(abs);
      } else {
        fs.mkdirSync(path.join(archive, loc), { recursive: true });
        const how = safeRename(abs, destDir);
        bump(loc, how === "moved" || how === "copied" ? how : how);
      }
    }
  }

  return { ok: true, ...stats };
}

export function weatherGifTreeNeedsOrganize(root) {
  const current = path.join(root, "current");
  const loops = path.join(root, "loops", "24h");
  const archive = path.join(root, "archive");
  const hasFlatGif = (dir) => {
    if (!fs.existsSync(dir)) return false;
    try {
      return fs.readdirSync(dir).some((n) => /\.(gif|png|jpe?g|webp)$/i.test(n) && !n.startsWith("."));
    } catch {
      return false;
    }
  };
  if (hasFlatGif(current) || hasFlatGif(loops)) return true;
  if (!fs.existsSync(archive)) return false;
  try {
    return fs.readdirSync(archive, { withFileTypes: true }).some(
      (d) => d.isDirectory() && !d.name.startsWith(".") && !isWeatherGifLocationSlug(d.name),
    );
  } catch {
    return false;
  }
}
