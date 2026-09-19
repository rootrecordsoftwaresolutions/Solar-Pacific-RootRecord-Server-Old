#!/usr/bin/env node
'use strict';

const { execFile, spawn } = require('child_process');
const net = require('net');
const fs = require('fs');
const path = require('path');

const POLL_MS = Number(process.env.POLL_MS || 2000);
const PACKET_WINDOW_MS = Number(process.env.PACKET_WINDOW_MS || 10000);
const MAX_BUFFERED = Number(process.env.MAX_BUFFERED || 10000);
const SOURCE_NODE = process.env.SOURCE_NODE || 'HawaiiRoot';
const SOURCE_REGION = process.env.SOURCE_REGION || 'local-hawaii';
const AWS_USER = process.env.AWS_USER || 'ubuntu';
const AWS_HOST = process.env.AWS_HOST || '3.139.100.162';
const AWS_REMOTE_DIR = process.env.AWS_REMOTE_DIR || '/home/ubuntu/network-globe/network-globe';
const SSH_KEY = process.env.SSH_KEY || '/home/rootrecord/.ssh/rootrecordkey.pem';
const SSH_CONNECT_TIMEOUT = Number(process.env.SSH_CONNECT_TIMEOUT || 8);
const OUTBOX = process.env.OUTBOX || path.join(__dirname, 'data', 'outbox.ndjson');
const ORIGIN_LABEL = process.env.ORIGIN_LABEL || 'Hawaii';

const stateDir = path.dirname(OUTBOX);
fs.mkdirSync(stateDir, { recursive: true });

let localAddresses = new Set(['127.0.0.1', '::1']);
let flows = new Map();
let packetWindow = [];
let origin = null;
let sshProc = null;
let sshReady = false;
let sshConnecting = false;
let buffer = [];
let shuttingDown = false;

function isPrivateIp(ip) {
  if (!ip) return true;
  if (ip.startsWith('::ffff:')) return isPrivateIp(ip.slice(7));
  const v = net.isIP(ip);
  if (v === 4) {
    const [a,b] = ip.split('.').map(Number);
    return a === 0 || a === 10 || a === 127 || (a === 100 && b >= 64 && b <= 127) ||
      (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31) ||
      (a === 192 && b === 168) || a >= 224;
  }
  if (v === 6) {
    const x = ip.toLowerCase();
    return x === '::' || x === '::1' || x.startsWith('fc') || x.startsWith('fd') ||
      x.startsWith('fe8') || x.startsWith('fe9') || x.startsWith('fea') || x.startsWith('feb') || x.startsWith('ff');
  }
  return true;
}

function parseEndpoint(value) {
  if (!value || value === '*' || value === '*:*') return null;
  value = value.trim();
  if (value.startsWith('[')) {
    const end = value.lastIndexOf(']');
    if (end > 0) {
      const ip = value.slice(1, end).split('%')[0];
      const port = Number(value.slice(end + 1).replace(/^:/, '')) || 0;
      return { ip, port };
    }
  }
  const i = value.lastIndexOf(':');
  if (i > 0) {
    const ip = value.slice(0, i);
    const port = Number(value.slice(i + 1));
    if (net.isIP(ip) && Number.isFinite(port)) return { ip, port };
  }
  return null;
}

function parseSsLine(line) {
  const cols = line.trim().split(/\s+/);
  if (cols.length < 6) return null;
  const protoRaw = cols[0].toLowerCase();
  const proto = protoRaw.startsWith('tcp') ? 'tcp' : protoRaw.startsWith('udp') ? 'udp' : protoRaw;
  const local = parseEndpoint(cols[4]);
  const peer = parseEndpoint(cols[5]);
  if (!local || !peer || !net.isIP(peer.ip) || isPrivateIp(peer.ip)) return null;
  const m = line.match(/users:\(\("([^"]+)"(?:,pid=(\d+))?/);
  const process = m ? m[1] : 'unknown';
  const pid = m && m[2] ? Number(m[2]) : null;
  const key = `${proto}|${local.ip}:${local.port}|${peer.ip}:${peer.port}`;
  return { key, proto, local, peer, process, pid };
}

function refreshLocalAddresses() {
  return new Promise(resolve => {
    execFile('ip', ['-j', 'addr'], { timeout: 3000, maxBuffer: 2 * 1024 * 1024 }, (err, stdout) => {
      if (!err) {
        try {
          const data = JSON.parse(stdout);
          const next = new Set(['127.0.0.1', '::1']);
          for (const iface of data) for (const a of iface.addr_info || []) if (a.local) next.add(a.local.split('%')[0]);
          localAddresses = next;
        } catch {}
      }
      resolve();
    });
  });
}

function runSs() {
  return new Promise(resolve => {
    execFile('ss', ['-H', '-tun', '-p'], { timeout: 3000, maxBuffer: 8 * 1024 * 1024 }, (err, stdout) => {
      if (err) return resolve([]);
      resolve(stdout.split('\n').map(parseSsLine).filter(Boolean));
    });
  });
}

function parseTcpdumpEndpoint(value) {
  value = value.replace(/^\[|\]$/g, '').trim();
  const i = value.lastIndexOf('.');
  if (i <= 0) return null;
  const ip = value.slice(0, i).replace(/^IP6?\s+/, '').trim();
  const port = Number(value.slice(i + 1));
  if (!net.isIP(ip) || !Number.isFinite(port)) return null;
  return { ip, port };
}

function addPacketEvent(src, dst, proto, bytes) {
  const outbound = localAddresses.has(src.ip);
  const local = outbound ? src : dst;
  const remote = outbound ? dst : src;
  if (!localAddresses.has(local.ip) || isPrivateIp(remote.ip)) return;
  packetWindow.push({ key: `${proto}|${local.ip}:${local.port}|${remote.ip}:${remote.port}`, packets: 1, bytes, ts: Date.now() });
  if (packetWindow.length > 50000) packetWindow.splice(0, packetWindow.length - 50000);
}

function handleTcpdumpLine(line) {
  const cleaned = line.trim().replace(/^\S+\s+IP6?\s+/, '').replace(/^IP6?\s+/, '');
  const parts = cleaned.split(' > ');
  if (parts.length < 2) return;
  const src = parseTcpdumpEndpoint(parts[0].trim());
  const dst = parseTcpdumpEndpoint(parts[1].split(':')[0].trim());
  if (!src || !dst) return;
  const is6 = cleaned.startsWith('IP6 ') || line.includes(' IP6 ');
  const proto = /\btcp\b/i.test(line) ? 'tcp' : /\budp\b/i.test(line) ? 'udp' : (is6 ? 'ip6' : 'ip');
  const len = Number((line.match(/\blength\s+(\d+)/) || [])[1] || 0);
  addPacketEvent(src, dst, proto, len);
}

function startTcpdump() {
  if (!(process.getuid && process.getuid() === 0)) return;
  try {
    const p = spawn('tcpdump', ['-l', '-n', '-q', '-i', 'any', 'ip or ip6'], { stdio: ['ignore', 'pipe', 'ignore'] });
    p.stdout.setEncoding('utf8');
    let buf = '';
    p.stdout.on('data', chunk => {
      buf += chunk;
      const lines = buf.split('\n');
      buf = lines.pop() || '';
      for (const line of lines) handleTcpdumpLine(line);
    });
  } catch {}
}

function packetStats(key) {
  const cutoff = Date.now() - PACKET_WINDOW_MS;
  let packets = 0, bytes = 0;
  const keep = [];
  for (const e of packetWindow) {
    if (e.ts >= cutoff) {
      keep.push(e);
      if (e.key === key) {
        packets += e.packets;
        bytes += e.bytes;
      }
    }
  }
  packetWindow = keep;
  return { packets, bytes };
}

function updateFlows(rows) {
  const now = Date.now();
  const next = new Map();
  for (const row of rows) {
    const prev = flows.get(row.key);
    next.set(row.key, { ...row, firstSeen: prev?.firstSeen || now, lastSeen: now });
  }
  for (const [key, flow] of flows) if (!next.has(key) && now - flow.lastSeen < 12000) next.set(key, flow);
  flows = next;
}

async function discoverOrigin() {
  const lat = Number(process.env.ORIGIN_LAT);
  const lng = Number(process.env.ORIGIN_LNG);
  if (Number.isFinite(lat) && Number.isFinite(lng)) {
    origin = { lat, lng, label: ORIGIN_LABEL };
    return;
  }
  try {
    const r = await fetch('https://ipapi.co/json/', { signal: AbortSignal.timeout(5000) });
    const d = await r.json();
    const dlat = Number(d.latitude), dlng = Number(d.longitude);
    if (Number.isFinite(dlat) && Number.isFinite(dlng)) {
      origin = { lat: dlat, lng: dlng, label: d.city ? `${d.city}, ${d.country_name || ''}`.replace(/, $/, '') : ORIGIN_LABEL, ip: d.ip || null, asn: d.asn || null, org: d.org || null };
      return;
    }
  } catch {}
  origin = { lat: 21.3069, lng: -157.8583, label: ORIGIN_LABEL };
}

function makeRecord(flow) {
  const stats = packetStats(flow.key);
  return {
    type: 'network-globe-telemetry',
    version: 1,
    timestamp: Date.now(),
    sourceNode: SOURCE_NODE,
    sourceRegion: SOURCE_REGION,
    source: {
      latitude: origin?.lat ?? null,
      longitude: origin?.lng ?? null,
      label: origin?.label || ORIGIN_LABEL,
      publicIp: origin?.ip || null,
      asn: origin?.asn || null,
      organization: origin?.org || null
    },
    destination: {
      type: 'public-ip',
      ip: flow.peer.ip,
      port: flow.peer.port
    },
    protocol: flow.proto,
    process: flow.process || 'unknown',
    packets: stats.packets,
    bytes: stats.bytes
  };
}

function queueRecord(record) {
  const line = JSON.stringify(record) + '\n';
  if (sshReady && sshProc?.stdin?.writable) {
    try { sshProc.stdin.write(line); return; } catch { sshReady = false; }
  }
  buffer.push(line);
  if (buffer.length > MAX_BUFFERED) buffer.splice(0, buffer.length - MAX_BUFFERED);
  fs.appendFileSync(OUTBOX, line);
}

function sshArgs() {
  const remoteFile = AWS_REMOTE_DIR + '/data/hawaii.ndjson';
  const readyMarker = '__NETWORK_GLOBE_SSH_READY__';
  const args = [
    '-T',
    '-o', 'BatchMode=yes',
    '-o', `ConnectTimeout=${SSH_CONNECT_TIMEOUT}`,
    '-o', 'ServerAliveInterval=15',
    '-o', 'ServerAliveCountMax=3',
    '-o', 'StrictHostKeyChecking=accept-new'
  ];
  if (SSH_KEY) args.push('-i', SSH_KEY);
  args.push(
    `${AWS_USER}@${AWS_HOST}`,
    `mkdir -p ${shellQuote(AWS_REMOTE_DIR + '/data')} && printf '%s\\n' '${readyMarker}' && exec cat >> ${shellQuote(remoteFile)}`
  );
  return args;
}

function shellQuote(s) { return `'${String(s).replace(/'/g, `'\\''`)}'`; }

function connectSsh() {
  if (shuttingDown || sshProc || sshConnecting) return;

  sshConnecting = true;
  const p = spawn('ssh', sshArgs(), { stdio: ['pipe', 'pipe', 'pipe'] });
  sshProc = p;

  let stdoutBuffer = '';

  p.stdin.on('error', () => {
    sshReady = false;
  });

  p.stdout.setEncoding('utf8');
  p.stdout.on('data', chunk => {
    stdoutBuffer += chunk;

    if (!sshReady && stdoutBuffer.includes('__NETWORK_GLOBE_SSH_READY__')) {
      sshReady = true;
      stdoutBuffer = '';

      const pending = buffer.splice(0);
      for (const line of pending) {
        try {
          if (p.stdin.writable) p.stdin.write(line);
          else {
            buffer.push(line);
            break;
          }
        } catch {
          buffer.push(line);
          break;
        }
      }

      console.log('SSH stream ready → AWS');
    }
  });

  p.stderr.setEncoding('utf8');
  p.stderr.on('data', chunk => {
    const msg = chunk.trim();
    if (msg) console.error(`SSH: ${msg}`);
  });

  p.on('spawn', () => {
    sshConnecting = false;
  });

  p.on('error', err => {
    sshReady = false;
    sshConnecting = false;
    console.error(`SSH process error: ${err.message}`);
  });

  p.on('close', (code, signal) => {
    sshReady = false;
    sshConnecting = false;
    if (sshProc === p) sshProc = null;

    if (!shuttingDown) {
      console.error(`SSH stream closed (code=${code}, signal=${signal || 'none'})`);
      setTimeout(connectSsh, 2000);
    }
  });
}

async function collect() {
  await refreshLocalAddresses();
  updateFlows(await runSs());
  for (const flow of flows.values()) queueRecord(makeRecord(flow));
}

async function boot() {
  await refreshLocalAddresses();
  await discoverOrigin();
  startTcpdump();
  connectSsh();
  await collect();
  setInterval(() => collect().catch(() => {}), POLL_MS);
  setInterval(() => connectSsh(), 3000);
  console.log(`Hawaii data collector → ${AWS_USER}@${AWS_HOST}:${AWS_REMOTE_DIR}/data/hawaii.ndjson`);
  console.log(`origin: ${origin?.label || ORIGIN_LABEL}`);
}

function shutdown() {
  shuttingDown = true;
  try { sshProc?.stdin?.end(); } catch {}
  try { sshProc?.kill('SIGTERM'); } catch {}
  process.exit(0);
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
boot().catch(err => { console.error(err); process.exit(1); });
