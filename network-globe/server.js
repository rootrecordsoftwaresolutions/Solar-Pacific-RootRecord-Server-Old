const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const net = require('net');
const { execFile, spawn } = require('child_process');
const crypto = require('crypto');

const PORT = Number(process.env.PORT || 8081);
const POLL_MS = Number(process.env.POLL_MS || 2000);
const HISTORY_LIMIT = Number(process.env.HISTORY_LIMIT || 2500);
const GEO_TTL_MS = Number(process.env.GEO_TTL_MS || 30 * 24 * 60 * 60 * 1000);
const GEO_DELAY_MS = Number(process.env.GEO_DELAY_MS || 350);

const ROOT = __dirname;
const DATA_DIR = path.join(ROOT, 'data');
const STATE_FILE = path.join(DATA_DIR, 'state.json');
const GEO_FILE = path.join(DATA_DIR, 'geo-cache.json');
const HISTORY_FILE = path.join(DATA_DIR, 'history.json');
const PAGE_FILE = path.join(ROOT, 'index.html');

fs.mkdirSync(DATA_DIR, { recursive: true });

function loadJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return fallback;
  }
}

function atomicWrite(file, value) {
  const tmp = `${file}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(value, null, 2));
  fs.renameSync(tmp, file);
}

const geoCache = loadJson(GEO_FILE, {});
const history = loadJson(HISTORY_FILE, []);
let origin = null;
let localAddresses = new Set();
let currentFlows = new Map();
let packetFlows = new Map();
let geoQueue = [];
let geoBusy = false;
let lastSnapshotAt = 0;
let packetCapture = null;
let packetCaptureStarted = false;

const clients = new Set();

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const sendJson = (code, data) => {
    const body = JSON.stringify(data);
    res.writeHead(code, {
      'Content-Type': 'application/json; charset=utf-8',
      'Cache-Control': 'no-store',
      'Access-Control-Allow-Origin': '*'
    });
    res.end(body);
  };

  if (url.pathname === '/' || url.pathname === '/index.html') {
    res.writeHead(200, {
      'Content-Type': 'text/html; charset=utf-8',
      'Cache-Control': 'no-store'
    });
    fs.createReadStream(PAGE_FILE).pipe(res);
    return;
  }

  if (url.pathname === '/healthz') {
    sendJson(200, {
      ok: true,
      uptimeSec: Math.round(process.uptime()),
      collector: 'ss' + (packetCaptureStarted ? '+tcpdump' : ''),
      flows: currentFlows.size,
      origin
    });
    return;
  }

  if (url.pathname === '/api/state') {
    sendJson(200, buildPayload());
    return;
  }

  if (url.pathname === '/api/history') {
    sendJson(200, history);
    return;
  }

  res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
  res.end('Not found');
});

const wsClients = new Set();

function wsSend(socket, text) {
  if (!socket || socket.destroyed) return;
  const payload = Buffer.from(text);
  let header;
  if (payload.length < 126) {
    header = Buffer.from([0x81, payload.length]);
  } else if (payload.length < 65536) {
    header = Buffer.alloc(4);
    header[0] = 0x81;
    header[1] = 126;
    header.writeUInt16BE(payload.length, 2);
  } else {
    header = Buffer.alloc(10);
    header[0] = 0x81;
    header[1] = 127;
    header.writeBigUInt64BE(BigInt(payload.length), 2);
  }
  socket.write(Buffer.concat([header, payload]));
}

function wsClose(socket) {
  try {
    if (!socket.destroyed) socket.end(Buffer.from([0x88, 0x00]));
  } catch {}
}

server.on('upgrade', (req, socket) => {
  if (req.headers.upgrade?.toLowerCase() !== 'websocket') {
    socket.destroy();
    return;
  }
  const key = req.headers['sec-websocket-key'];
  if (!key) {
    socket.destroy();
    return;
  }
  const accept = crypto.createHash('sha1')
    .update(`${key}258EAFA5-E914-47DA-95CA-C5AB0DC85B11`)
    .digest('base64');
  socket.write([
    'HTTP/1.1 101 Switching Protocols',
    'Upgrade: websocket',
    'Connection: Upgrade',
    `Sec-WebSocket-Accept: ${accept}`
  ].join('\r\n') + '\r\n\r\n');

  wsClients.add(socket);
  socket.setNoDelay(true);
  socket.on('data', data => {
    // Browsers normally send no application frames for this page, but handle ping/close.
    let offset = 0;
    while (offset + 2 <= data.length) {
      const b1 = data[offset];
      const b2 = data[offset + 1];
      const opcode = b1 & 0x0f;
      let len = b2 & 0x7f;
      let headerLen = 2;
      if (len === 126) {
        if (offset + 4 > data.length) break;
        len = data.readUInt16BE(offset + 2);
        headerLen = 4;
      } else if (len === 127) {
        if (offset + 10 > data.length) break;
        const big = data.readBigUInt64BE(offset + 2);
        if (big > BigInt(Number.MAX_SAFE_INTEGER)) break;
        len = Number(big);
        headerLen = 10;
      }
      const masked = Boolean(b2 & 0x80);
      const frameLen = headerLen + (masked ? 4 : 0) + len;
      if (offset + frameLen > data.length) break;
      if (opcode === 0x8) {
        wsClose(socket);
        break;
      }
      if (opcode === 0x9) {
        const pong = Buffer.from([0x8a, 0x00]);
        socket.write(pong);
      }
      offset += frameLen;
    }
  });
  socket.on('close', () => wsClients.delete(socket));
  socket.on('error', () => wsClients.delete(socket));
  wsSend(socket, JSON.stringify(buildPayload()));
});

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function isPrivateIp(ip) {
  if (!ip) return true;
  if (ip.startsWith('::ffff:')) return isPrivateIp(ip.slice(7));
  const version = net.isIP(ip);
  if (version === 4) {
    const p = ip.split('.').map(Number);
    if (p.length !== 4 || p.some(Number.isNaN)) return true;
    const [a, b] = p;
    return a === 0 || a === 10 || a === 127 ||
      (a === 100 && b >= 64 && b <= 127) ||
      (a === 169 && b === 254) ||
      (a === 172 && b >= 16 && b <= 31) ||
      (a === 192 && b === 168) ||
      a >= 224;
  }
  if (version === 6) {
    const lower = ip.toLowerCase();
    return lower === '::' || lower === '::1' ||
      lower.startsWith('fe8') || lower.startsWith('fe9') ||
      lower.startsWith('fea') || lower.startsWith('feb') ||
      lower.startsWith('fc') || lower.startsWith('fd') ||
      lower.startsWith('ff');
  }
  return true;
}

function stripBrackets(value) {
  return value.startsWith('[') && value.endsWith(']') ? value.slice(1, -1) : value;
}

function parseEndpoint(value) {
  if (!value || value === '*' || value === '*:*') return null;
  value = value.trim();

  // IPv6 in ss is normally [addr]:port; wildcard forms can appear as [::]:port.
  if (value.startsWith('[')) {
    const close = value.lastIndexOf(']');
    if (close > 0) {
      const ip = value.slice(1, close);
      const portPart = value.slice(close + 1).replace(/^:/, '');
      return { ip, port: Number(portPart) || 0 };
    }
  }

  const lastColon = value.lastIndexOf(':');
  if (lastColon > 0) {
    const maybePort = value.slice(lastColon + 1);
    const ip = value.slice(0, lastColon);
    if (/^\d+$/.test(maybePort) && net.isIP(ip)) {
      return { ip, port: Number(maybePort) };
    }
  }

  // ss may use bare IPv4:port or bare IPv6:port in unusual builds.
  const dot = value.lastIndexOf('.');
  if (dot > 0 && /^\d+$/.test(value.slice(dot + 1))) {
    const ip = value.slice(0, dot);
    if (net.isIP(ip)) return { ip, port: Number(value.slice(dot + 1)) };
  }
  return null;
}

function extractProcess(line) {
  const m = line.match(/users:\(\(\"([^\"]+)/);
  return m ? m[1] : 'unknown';
}

function parseSsLine(line) {
  const cols = line.trim().split(/\s+/);
  if (cols.length < 5) return null;
  const proto = cols[0];
  const state = cols[1];
  const local = parseEndpoint(cols[4]);
  const peer = parseEndpoint(cols[5]);
  if (!local || !peer || !peer.ip || !net.isIP(peer.ip) || isPrivateIp(peer.ip)) return null;

  const process = extractProcess(line);
  const localKey = `${local.ip}:${local.port}`;
  const peerKey = `${peer.ip}:${peer.port}`;
  const key = `${proto}|${localKey}|${peerKey}`;

  return {
    key,
    proto: proto.toLowerCase(),
    state,
    local,
    peer,
    process
  };
}

function refreshLocalAddresses(callback) {
  execFile('ip', ['-j', 'addr'], { timeout: 3000, maxBuffer: 2 * 1024 * 1024 }, (err, stdout) => {
    if (!err) {
      try {
        const data = JSON.parse(stdout);
        const next = new Set(['127.0.0.1', '::1']);
        for (const iface of data) {
          for (const addr of iface.addr_info || []) {
            if (addr.local) next.add(addr.local.split('%')[0]);
          }
        }
        localAddresses = next;
      } catch {}
    }
    callback();
  });
}

function runSs() {
  return new Promise(resolve => {
    const args = ['-H', '-tun'];
    if (process.getuid && process.getuid() === 0) args.push('-p');
    execFile('ss', args, { timeout: 3000, maxBuffer: 8 * 1024 * 1024 }, (err, stdout) => {
      if (err) {
        resolve([]);
        return;
      }
      const rows = [];
      for (const line of stdout.split('\n')) {
        const parsed = parseSsLine(line);
        if (parsed) rows.push(parsed);
      }
      resolve(rows);
    });
  });
}

function findLocalOriginIp() {
  for (const ip of localAddresses) {
    if (!isPrivateIp(ip) && net.isIP(ip)) return ip;
  }
  return null;
}

async function httpJson(url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 6000);
  try {
    const response = await fetch(url, {
      signal: controller.signal,
      headers: { 'User-Agent': 'network-globe/2.0 local-monitor' }
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return await response.json();
  } finally {
    clearTimeout(timer);
  }
}

async function lookupIp(ip) {
  const cached = geoCache[ip];
  if (cached && cached.ts && Date.now() - cached.ts < GEO_TTL_MS) return cached;

  try {
    const data = await httpJson(`https://ipapi.co/${encodeURIComponent(ip)}/json/`);
    if (data.error) throw new Error(data.reason || 'geolocation error');
    const record = {
      ip,
      lat: Number(data.latitude),
      lng: Number(data.longitude),
      city: data.city || null,
      region: data.region || null,
      country: data.country_name || data.country || null,
      countryCode: data.country_code || null,
      asn: data.asn || null,
      org: data.org || null,
      ts: Date.now()
    };
    if (Number.isFinite(record.lat) && Number.isFinite(record.lng)) {
      geoCache[ip] = record;
      atomicWrite(GEO_FILE, geoCache);
      return record;
    }
  } catch (err) {
    geoCache[ip] = { ip, error: err.message, ts: Date.now() };
    atomicWrite(GEO_FILE, geoCache);
  }
  return geoCache[ip] || null;
}

function enqueueGeo(ip) {
  if (!ip || isPrivateIp(ip) || geoCache[ip]?.lat != null) return;
  if (!geoQueue.includes(ip)) geoQueue.push(ip);
  processGeoQueue();
}

async function processGeoQueue() {
  if (geoBusy || geoQueue.length === 0) return;
  geoBusy = true;
  const ip = geoQueue.shift();
  try {
    await lookupIp(ip);
  } finally {
    geoBusy = false;
    if (geoQueue.length) {
      setTimeout(processGeoQueue, GEO_DELAY_MS);
    }
  }
}

async function discoverOrigin() {
  const forcedLat = Number(process.env.ORIGIN_LAT);
  const forcedLng = Number(process.env.ORIGIN_LNG);
  if (Number.isFinite(forcedLat) && Number.isFinite(forcedLng)) {
    origin = {
      lat: forcedLat,
      lng: forcedLng,
      label: process.env.ORIGIN_LABEL || 'Local network',
      ip: null
    };
    return;
  }

  const publicIp = process.env.PUBLIC_IP || null;
  if (publicIp) {
    const record = await lookupIp(publicIp);
    if (record?.lat != null) {
      origin = {
        lat: record.lat,
        lng: record.lng,
        label: record.city ? `${record.city}, ${record.country}` : 'Public network',
        ip: publicIp,
        asn: record.asn,
        org: record.org
      };
      return;
    }
  }

  try {
    const data = await httpJson('https://ipapi.co/json/');
    if (data.ip) {
      const record = {
        ip: data.ip,
        lat: Number(data.latitude),
        lng: Number(data.longitude),
        city: data.city || null,
        country: data.country_name || data.country || null,
        asn: data.asn || null,
        org: data.org || null,
        ts: Date.now()
      };
      if (Number.isFinite(record.lat) && Number.isFinite(record.lng)) {
        geoCache[data.ip] = record;
        atomicWrite(GEO_FILE, geoCache);
        origin = {
          lat: record.lat,
          lng: record.lng,
          label: record.city ? `${record.city}, ${record.country}` : 'Public network',
          ip: record.ip,
          asn: record.asn,
          org: record.org
        };
      }
    }
  } catch (err) {
    console.warn(`Origin geolocation unavailable: ${err.message}`);
  }
}

function activePacketStats(flow) {
  const stats = packetFlows.get(flow.key);
  if (!stats) return { packets: 0, bytes: 0 };
  return {
    packets: stats.packets,
    bytes: stats.bytes
  };
}

function updateCurrentFlows(rows) {
  const now = Date.now();
  const next = new Map();
  for (const row of rows) {
    const key = row.key;
    const old = currentFlows.get(key);
    const stats = activePacketStats(row);
    next.set(key, {
      ...row,
      firstSeen: old?.firstSeen || now,
      lastSeen: now,
      packets: stats.packets,
      bytes: stats.bytes
    });
    enqueueGeo(row.peer.ip);
  }
  currentFlows = next;
}

function buildPayload() {
  const arcs = [];
  const pointsMap = new Map();
  const now = Date.now();

  if (origin?.lat != null && origin?.lng != null) {
    pointsMap.set('origin', {
      lat: origin.lat,
      lng: origin.lng,
      type: 'origin',
      label: origin.label || 'Local network'
    });
  }

  let packetRate = 0;
  let byteRate = 0;
  for (const flow of currentFlows.values()) {
    const geo = geoCache[flow.peer.ip];
    if (!geo || !Number.isFinite(geo.lat) || !Number.isFinite(geo.lng)) continue;
    const stats = activePacketStats(flow);
    const ageSec = Math.max(1, (now - flow.firstSeen) / 1000);
    const packetsPerSec = stats.packets / ageSec;
    const bytesPerSec = stats.bytes / ageSec;
    packetRate += packetsPerSec;
    byteRate += bytesPerSec;

    const intensity = Math.min(1, 0.12 + Math.log10(1 + packetsPerSec) / 4);
    const process = flow.process || 'network';
    const pointKey = `${geo.lat.toFixed(3)},${geo.lng.toFixed(3)}`;
    pointsMap.set(pointKey, {
      lat: geo.lat,
      lng: geo.lng,
      type: 'remote',
      label: `${geo.city ? geo.city + ', ' : ''}${geo.country || 'Unknown'} · ${geo.org || geo.ip}`
    });

    const color = process.includes('megasync')
      ? ['#a78bfa', '#ffffff']
      : process.includes('git')
        ? ['#60a5fa', '#ffffff']
        : ['#ff6b9d', '#ffffff'];

    arcs.push({
      startLat: origin?.lat ?? 0,
      startLng: origin?.lng ?? 0,
      endLat: geo.lat,
      endLng: geo.lng,
      color,
      stroke: Math.max(0.35, Math.min(2.2, 0.45 + intensity * 1.2)),
      altitude: 0.12 + Math.min(0.25, Math.abs(geo.lat - (origin?.lat ?? 0)) / 500),
      process,
      protocol: flow.proto,
      port: flow.peer.port,
      endpoint: geo.org || 'external network',
      city: geo.city,
      country: geo.country,
      asn: geo.asn,
      org: geo.org,
      packets: stats.packets,
      bytes: stats.bytes,
      packetsPerSec,
      bytesPerSec,
      ageSec
    });
  }

  return {
    type: 'network-globe',
    version: 2,
    ts: now,
    origin,
    arcs,
    points: [...pointsMap.values()],
    stats: {
      activeFlows: currentFlows.size,
      mappedFlows: arcs.length,
      endpoints: new Set(arcs.map(a => a.ip)).size,
      packetRate: Number(packetRate.toFixed(2)),
      bytesPerSec: Math.round(byteRate),
      queue: geoQueue.length,
      geoCached: Object.keys(geoCache).length,
      collector: packetCaptureStarted ? 'ss + tcpdump' : 'ss',
      privileged: Boolean(process.getuid && process.getuid() === 0),
      hostname: os.hostname(),
      updated: now
    }
  };
}

function persistSnapshot(payload) {
  lastSnapshotAt = payload.ts;
  const snapshot = {
    ts: payload.ts,
    origin: payload.origin,
    stats: payload.stats,
    arcs: payload.arcs.map(a => ({
      ip: a.ip,
      city: a.city,
      country: a.country,
      asn: a.asn,
      org: a.org,
      process: a.process,
      protocol: a.protocol,
      port: a.port,
      packets: a.packets,
      bytes: a.bytes,
      packetsPerSec: a.packetsPerSec,
      bytesPerSec: a.bytesPerSec
    }))
  };

  history.push(snapshot);
  if (history.length > HISTORY_LIMIT) history.splice(0, history.length - HISTORY_LIMIT);
  atomicWrite(STATE_FILE, payload);
  atomicWrite(HISTORY_FILE, history);
}

function broadcast() {
  const payload = buildPayload();
  persistSnapshot(payload);
  const body = JSON.stringify(payload);
  for (const ws of wsClients) wsSend(ws, body);
}

function parseTcpdumpEndpoint(value) {
  value = value.replace(/^\[/, '').replace(/\]$/, '');
  const m = value.match(/^(.+)\.(\d+)$/);
  if (!m) return null;
  const ip = m[1];
  const port = Number(m[2]);
  return net.isIP(ip) ? { ip, port } : null;
}

function handlePacketLine(line) {
  if (!line.includes(' > ')) return;
  const parts = line.split(' > ');
  if (parts.length < 2) return;
  const left = parts[0].replace(/^\S+\s+/, '').trim();
  const rightToken = parts[1].split(':')[0].trim();
  const src = parseTcpdumpEndpoint(left);
  const dst = parseTcpdumpEndpoint(rightToken);
  if (!src || !dst) return;
  if (isPrivateIp(src.ip) && isPrivateIp(dst.ip)) return;
  const srcLocal = localAddresses.has(src.ip);
  const dstLocal = localAddresses.has(dst.ip);
  if (!srcLocal && !dstLocal) return;

  const key = `${srcLocal ? 'out' : 'in'}|${src.ip}:${src.port}|${dst.ip}:${dst.port}`;
  const existing = packetFlows.get(key) || { packets: 0, bytes: 0, ts: Date.now() };
  existing.packets += 1;
  const lengthMatch = line.match(/\blength\s+(\d+)/);
  existing.bytes += lengthMatch ? Number(lengthMatch[1]) : 0;
  existing.ts = Date.now();
  packetFlows.set(key, existing);

  // Periodically discard stale packet counters.
  if (packetFlows.size > 5000) {
    const cutoff = Date.now() - 10 * 60 * 1000;
    for (const [k, v] of packetFlows) if (v.ts < cutoff) packetFlows.delete(k);
  }
}

function startPacketCapture() {
  if (!(process.getuid && process.getuid() === 0)) {
    console.log('Packet capture disabled (not running as root). ss flow collection remains active.');
    return;
  }
  try {
    packetCapture = spawn('tcpdump', ['-l', '-n', '-q', '-i', 'any', 'ip or ip6'], {
      stdio: ['ignore', 'pipe', 'pipe']
    });
    packetCapture.stdout.setEncoding('utf8');
    let buffer = '';
    packetCapture.stdout.on('data', chunk => {
      buffer += chunk;
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';
      for (const line of lines) handlePacketLine(line);
    });
    packetCapture.stderr.on('data', chunk => {
      const message = chunk.toString().trim();
      if (message) console.log(`[tcpdump] ${message}`);
    });
    packetCapture.on('error', err => console.warn(`tcpdump unavailable: ${err.message}`));
    packetCapture.on('close', code => {
      packetCaptureStarted = false;
      console.log(`tcpdump exited (${code}); continuing with ss-only collection.`);
    });
    packetCaptureStarted = true;
    console.log('Packet metadata capture enabled. No payload contents are stored.');
  } catch (err) {
    console.warn(`Could not start tcpdump: ${err.message}`);
  }
}

async function collectOnce() {
  refreshLocalAddresses(async () => {
    const rows = await runSs();
    updateCurrentFlows(rows);
    broadcast();
  });
}

(async () => {
  refreshLocalAddresses(async () => {
    await discoverOrigin();
    startPacketCapture();
    await collectOnce();
    setInterval(collectOnce, POLL_MS);
    console.log(`\n  Live Network Globe v2`);
    console.log(`  → http://localhost:${PORT}`);
    console.log(`  → state: ${STATE_FILE}`);
    console.log(`  → geo:   ${GEO_FILE}`);
    console.log(`  → history: ${HISTORY_FILE}\n`);
  });
})();

server.listen(PORT);

function shutdown() {
  if (packetCapture) packetCapture.kill('SIGTERM');
  for (const ws of wsClients) wsClose(ws);
  wsClients.clear();
  server.close(() => process.exit(0));
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
