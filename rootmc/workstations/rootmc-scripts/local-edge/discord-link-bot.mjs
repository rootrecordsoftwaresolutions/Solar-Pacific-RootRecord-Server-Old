/**
 * Discord Gateway listener for Minecraft /link without Cloudflare Workers.
 *
 * Players: /link in-game â†’ DM the RootMC bot: `link ABC123`
 * This process connects outbound to Discord Gateway (PC internet) and completes
 * the bind against the local wrangler API (default http://127.0.0.1:8787).
 *
 * Env (from workspace .env):
 *   DISCORD_ROOTMC_BOT_TOKEN
 *   ROOTMC_EDGE_SIGNING_KEY (optional alternate auth)
 *   ROOTMC_LOCAL_API_BASE (default http://127.0.0.1:8787)
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const WebSocket = globalThis.WebSocket;
if (!WebSocket) {
  console.error("discord-link-bot: Node WebSocket unavailable (need Node 22+)");
  process.exit(1);
}

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..");
const CONFIG_PATH = process.env.ROOTMC_EDGE_CONFIG || path.join(ROOT, "config.json");

function loadEnvFile(filePath) {
  if (!fs.existsSync(filePath)) return;
  for (const line of fs.readFileSync(filePath, "utf8").split(/\r?\n/)) {
    const t = line.trim();
    if (!t || t.startsWith("#")) continue;
    const i = t.indexOf("=");
    if (i < 1) continue;
    const k = t.slice(0, i).trim();
    const v = t.slice(i + 1).trim();
    if (k && v && !process.env[k]) process.env[k] = v;
  }
}

try {
  const cfg = JSON.parse(fs.readFileSync(CONFIG_PATH, "utf8"));
  loadEnvFile(path.join(cfg.workspaceRoot || "D:\\.1 Work Stations\\RootMC", ".env"));
} catch {
  loadEnvFile(path.join("D:\\.1 Work Stations\\RootMC", ".env"));
}

const token = String(process.env.DISCORD_ROOTMC_BOT_TOKEN || "")
  .replace(/^bot\s+/i, "")
  .trim();
const apiBase = String(process.env.ROOTMC_LOCAL_API_BASE || "http://127.0.0.1:8787").replace(/\/+$/, "");
const edgeKey = String(process.env.ROOTMC_EDGE_SIGNING_KEY || "").trim();
const stateDir = (() => {
  try {
    return JSON.parse(fs.readFileSync(CONFIG_PATH, "utf8")).stateDir;
  } catch {
    return path.join(ROOT, "state");
  }
})();

if (!token || token.length < 40) {
  console.error("discord-link-bot: DISCORD_ROOTMC_BOT_TOKEN missing");
  process.exit(1);
}

// Intents: GUILDS(1) + DIRECT_MESSAGES(4096) + MESSAGE_CONTENT(32768)
const INTENTS = 1 | 4096 | 32768;
const LINK_RE = /^\s*(?:link|verify)\s+([A-Za-z0-9]{6})\s*$/i;

function writeStatus(extra = {}) {
  try {
    fs.mkdirSync(stateDir, { recursive: true });
    fs.writeFileSync(
      path.join(stateDir, "discord-link-bot.json"),
      JSON.stringify({ at: new Date().toISOString(), apiBase, ...extra }, null, 2),
    );
  } catch {
    /* ignore */
  }
}

async function completeByCode(code, user) {
  const headers = { "content-type": "application/json" };
  if (edgeKey) headers["x-rootmc-edge-key"] = edgeKey;
  else headers.authorization = `Bot ${token}`;

  const res = await fetch(`${apiBase}/api/realm/minecraft/link/discord/complete-by-code`, {
    method: "POST",
    headers,
    body: JSON.stringify({
      code,
      discord_user_id: String(user.id),
      discord_username: user.username || null,
      discord_global_name: user.global_name || null,
    }),
  });
  const body = await res.json().catch(() => ({}));
  return { ok: res.ok, status: res.status, body };
}

async function replyDm(channelId, content) {
  await fetch(`https://discord.com/api/v10/channels/${channelId}/messages`, {
    method: "POST",
    headers: {
      authorization: `Bot ${token}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({ content }),
  });
}

async function handleMessage(msg) {
  if (!msg || msg.author?.bot) return;
  // DMs only (type 1) â€” avoid guild chat spam
  if (msg.guild_id) return;
  const m = LINK_RE.exec(String(msg.content || ""));
  if (!m) {
    if (/^\s*(?:link|verify)\b/i.test(String(msg.content || ""))) {
      await replyDm(
        msg.channel_id,
        "Send your 6-character code like: `link ABC123` (from `/link` in Minecraft).",
      );
    }
    return;
  }
  const code = m[1].toUpperCase();
  console.log(`discord-link-bot: link attempt user=${msg.author.id} code=${code}`);
  const result = await completeByCode(code, msg.author);
  if (result.ok && result.body?.ok) {
    const name = result.body.minecraft_username || "your character";
    await replyDm(
      msg.channel_id,
      result.body.already_linked
        ? `Already linked to **${name}**.`
        : `Linked to **${name}**. Welcome to RootMC â€” check in-game for your link bonus.`,
    );
    writeStatus({ lastOk: name, lastUser: msg.author.id });
  } else {
    const reason = result.body?.reason || `http_${result.status}`;
    const hint =
      reason === "already_linked"
        ? "This Discord account is already linked to a different Minecraft player."
        : reason === "code_invalid"
          ? "Invalid or expired code. Run `/link` in-game again."
          : `Link failed (${reason}). Is local API up at ${apiBase}?`;
    await replyDm(msg.channel_id, hint);
    writeStatus({ lastError: reason, lastUser: msg.author.id });
  }
}

async function runGateway() {
  const gwRes = await fetch("https://discord.com/api/v10/gateway");
  const gw = await gwRes.json();
  const url = `${gw.url}?v=10&encoding=json`;
  console.log("discord-link-bot: connecting", url);

  let hbInterval = null;
  let seq = null;
  let sessionId = null;

  const ws = new WebSocket(url);

  const onOpen = () => writeStatus({ connected: false, phase: "open" });
  const onMessage = async (ev) => {
    let pkt;
    try {
      pkt = JSON.parse(typeof ev.data === "string" ? ev.data : String(ev.data));
    } catch {
      return;
    }
    if (pkt.s != null) seq = pkt.s;

    if (pkt.op === 10) {
      const ms = pkt.d.heartbeat_interval;
      if (hbInterval) clearInterval(hbInterval);
      // Discord wants first heartbeat after jitter; keep simple interval.
      hbInterval = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify({ op: 1, d: seq }));
      }, ms);
      ws.send(
        JSON.stringify({
          op: 2,
          d: {
            token,
            intents: INTENTS,
            properties: { os: "windows", browser: "rootmc-local-edge", device: "rootmc-local-edge" },
          },
        }),
      );
      return;
    }

    if (pkt.op === 0 && pkt.t === "READY") {
      sessionId = pkt.d.session_id;
      console.log("discord-link-bot: READY as", pkt.d.user?.username);
      writeStatus({ connected: true, sessionId, user: pkt.d.user?.username });
      return;
    }

    if (pkt.op === 0 && pkt.t === "MESSAGE_CREATE") {
      try {
        await handleMessage(pkt.d);
      } catch (err) {
        console.error("discord-link-bot: message handler", err);
      }
    }

    if (pkt.op === 7) {
      console.warn("discord-link-bot: reconnect requested");
      ws.close();
    }

    if (pkt.op === 9) {
      console.warn("discord-link-bot: invalid session, restarting");
      ws.close();
    }
  };
  const onClose = (ev) => {
    const code = ev?.code;
    console.warn("discord-link-bot: ws closed", code);
    if (hbInterval) clearInterval(hbInterval);
    writeStatus({ connected: false, closed: code });
    setTimeout(() => {
      runGateway().catch((e) => {
        console.error(e);
        setTimeout(() => runGateway(), 15000);
      });
    }, 5000);
  };
  const onError = (err) => console.error("discord-link-bot: ws error", err?.message || err);

  ws.addEventListener("open", onOpen);
  ws.addEventListener("message", (ev) => {
    onMessage(ev).catch((e) => console.error(e));
  });
  ws.addEventListener("close", onClose);
  ws.addEventListener("error", onError);
}

console.log(`discord-link-bot: api=${apiBase}`);
writeStatus({ starting: true });
runGateway().catch((e) => {
  console.error(e);
  process.exit(1);
});
