/**
 * One-shot Block Notes dev update → Discord (Root Record global updater bot).
 *
 * Reads DISCORD_BOT_TOKEN from repo-root credentials.env.
 *
 * Usage:
 *   node scripts/discord-post-announcement.mjs --channel 1511793476226912407
 *   node scripts/discord-post-announcement.mjs --broadcast --category blocknotes
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function readEnvFile(p) {
  const out = {};
  const text = fs.readFileSync(p, "utf8");
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    const i = line.indexOf("=");
    if (i <= 0) continue;
    out[line.slice(0, i).trim()] = line.slice(i + 1).trim();
  }
  return out;
}

function arg(name) {
  const idx = process.argv.indexOf(name);
  if (idx === -1) return "";
  return String(process.argv[idx + 1] || "").trim();
}

function toDiscordText(md) {
  return md
    .replace(/^#+\s+/gm, "")
    .replace(/^---\s*$/gm, "──────────")
    .replace(/\[(.+?)\]\((.+?)\)/g, "$1 ($2)")
    .trim();
}

const channelId = arg("--channel");
const broadcast = process.argv.includes("--broadcast");
const category = arg("--category") || "blocknotes";

if (broadcast) {
  const credPath =
    process.env.CREDENTIALS_ENV || path.resolve(__dirname, "../../../../.env");
  const envFile = readEnvFile(credPath);
  const adminKey = String(envFile.RR_PUSH_ADMIN_KEY || envFile.RR_PUSH_ADMIN_SECRET || "").trim();
  const base = String(envFile.RR_API_BASE || "https://api.rootrecord.info/api").replace(/\/+$/, "");
  if (!adminKey) {
    console.error("Missing RR_PUSH_ADMIN_KEY in credentials.env for --broadcast");
    process.exit(1);
  }
  const mdPath = path.resolve(__dirname, "../BLOCK-NOTES-DISCORD-ANNOUNCEMENT.md");
  const raw = fs.readFileSync(mdPath, "utf8");
  const content = toDiscordText(raw);
  const res = await fetch(`${base}/internal/discord-updates-broadcast`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "X-RR-Push-Admin-Key": adminKey,
      "User-Agent": "RootRecord/blocknotes-announcement",
    },
    body: JSON.stringify({
      category,
      embeds: [
        {
          title: "Block Notes — dev update (v1.0.8)",
          description:
            "Native Android Minecraft companion · offline-first notes, coords, build plans, and world maps.\n\n" +
            "Since the first teaser: grid maps (no seed required), World AI reports, coord reorder, full legacy ID reference, and **api.rootmc.net** is live.",
          color: 0x2d6a4f,
          fields: [
            { name: "Try / follow", value: "https://rootrecord.online/products", inline: false },
            { name: "Feedback", value: "Reply here or in-app (More → Send feedback)", inline: false },
          ],
          footer: { text: "Root Record Software Solutions" },
        },
      ],
      content,
    }),
  });
  const text = await res.text();
  if (!res.ok) {
    console.error("Broadcast failed", res.status, text.slice(0, 400));
    process.exit(1);
  }
  console.log("broadcast ok", text);
  process.exit(0);
}

if (!/^\d{10,}$/.test(channelId)) {
  console.error(
    "Usage: node scripts/discord-post-announcement.mjs --channel <channelId> | --broadcast [--category blocknotes]",
  );
  process.exit(1);
}

const credPath =
  process.env.CREDENTIALS_ENV || path.resolve(__dirname, "../../../../.env");
const env = readEnvFile(credPath);
const token = String(env.DISCORD_BOT_TOKEN || "").trim();
if (token.length < 40) {
  console.error("Missing DISCORD_BOT_TOKEN in credentials.env (Root Record global updater bot)");
  process.exit(1);
}

const mdPath = path.resolve(__dirname, "../BLOCK-NOTES-DISCORD-ANNOUNCEMENT.md");
const raw = fs.readFileSync(mdPath, "utf8");

const body = toDiscordText(raw);
const MAX = 1900;

function splitMessages(text) {
  const parts = [];
  let rest = text;
  while (rest.length > MAX) {
    let cut = rest.lastIndexOf("\n\n", MAX);
    if (cut < MAX / 2) cut = rest.lastIndexOf("\n", MAX);
    if (cut < MAX / 2) cut = MAX;
    parts.push(rest.slice(0, cut).trim());
    rest = rest.slice(cut).trim();
  }
  if (rest) parts.push(rest);
  return parts;
}

const chunks = splitMessages(body);

async function post(payload) {
  const res = await fetch(
    `https://discord.com/api/v10/channels/${encodeURIComponent(channelId)}/messages`,
    {
      method: "POST",
      headers: {
        Authorization: `Bot ${token}`,
        "Content-Type": "application/json; charset=utf-8",
        "User-Agent": "RootRecord/blocknotes-announcement",
      },
      body: JSON.stringify({ ...payload, allowed_mentions: { parse: [] } }),
    },
  );
  const text = await res.text().catch(() => "");
  if (!res.ok) throw new Error(`Discord HTTP ${res.status}: ${text.slice(0, 400)}`);
  return JSON.parse(text);
}

const leadEmbed = {
  title: "Block Notes — dev update (v1.0.8)",
  description:
    "Native Android Minecraft companion · offline-first notes, coords, build plans, and world maps.\n\n" +
    "Since the first teaser: grid maps (no seed required), World AI reports, coord reorder, full legacy ID reference, and **api.rootmc.net** is live.",
  color: 0x2d6a4f,
  fields: [
    {
      name: "Try / follow",
      value: "https://rootrecord.online/products",
      inline: false,
    },
    {
      name: "Feedback",
      value: "Reply here or in-app (More → Send feedback)",
      inline: false,
    },
  ],
  footer: { text: "Root Record Software Solutions" },
};

const first = await post({ embeds: [leadEmbed] });
console.log("posted embed", first.id);

for (let i = 0; i < chunks.length; i++) {
  const prefix = chunks.length > 1 ? `( ${i + 1}/${chunks.length} )\n\n` : "";
  const msg = await post({ content: prefix + chunks[i] });
  console.log("posted body", i + 1, msg.id);
  if (i < chunks.length - 1) await new Promise((r) => setTimeout(r, 800));
}

console.log("done", 1 + chunks.length, "message(s)");
