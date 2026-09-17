/**
 * Merge LuckPerms MySQL data into Root-Perms tables (same Shockbyte DB).
 * Usage from Web Files/rootmc-realm-api:
 *   node ../../scripts/merge-luckperms-into-rootperms.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workspace = path.resolve(__dirname, "..");
const require = createRequire(
  path.join(workspace, "Web Files/rootmc-realm-api/package.json"),
);
const mysql = require("mysql2/promise");

const dbPath = path.join(workspace, "2. RootMC - Towny/plugins/RootMC/database.yml");

function loadDb() {
  const raw = fs.readFileSync(dbPath, "utf8");
  const get = (key) => {
    const m = raw.match(new RegExp(`^[ \\t]*${key}:[ \\t]*"?([^"\\r\\n#]+)"?`, "m"));
    return m ? m[1].trim() : "";
  };
  return {
    host: get("host"),
    port: Number(get("port") || 3306),
    database: get("database"),
    user: get("username"),
    password: get("password").replace(/^"|"$/g, ""),
  };
}

function dashUuid(uuid) {
  let id = String(uuid || "").toLowerCase();
  if (!id.includes("-") && id.length === 32) {
    id = `${id.slice(0, 8)}-${id.slice(8, 12)}-${id.slice(12, 16)}-${id.slice(16, 20)}-${id.slice(20)}`;
  }
  return id;
}

async function main() {
  const cfg = loadDb();
  const conn = await mysql.createConnection({
    host: cfg.host,
    port: cfg.port,
    user: cfg.user,
    password: cfg.password,
    database: cfg.database,
  });

  const [tables] = await conn.query("SHOW TABLES");
  const names = tables.map((r) => Object.values(r)[0]);
  const lp = names.filter((n) => /luckperms/i.test(n));
  console.log("luckperms tables:", lp.join(", ") || "(none)");

  if (lp.length === 0) {
    console.error("No LuckPerms tables found.");
    await conn.end();
    process.exit(1);
  }

  const find = (re) => lp.find((n) => re.test(n));
  const tGroups = find(/_groups$/i) || find(/groups$/i);
  const tGroupPerms = find(/group_permissions$/i);
  const tPlayers = find(/_players$/i) || find(/players$/i);
  const tUserPerms = find(/user_permissions$/i);
  console.log({ tGroups, tGroupPerms, tPlayers, tUserPerms });

  const prefix = "root_";
  await conn.query(`
    CREATE TABLE IF NOT EXISTS ${prefix}perms_group (
      group_id VARCHAR(64) PRIMARY KEY,
      display VARCHAR(64) NOT NULL,
      prefix VARCHAR(128) NOT NULL DEFAULT '',
      priority INT NOT NULL DEFAULT 0,
      updated_at DATETIME NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);
  await conn.query(`
    CREATE TABLE IF NOT EXISTS ${prefix}perms_group_node (
      group_id VARCHAR(64) NOT NULL,
      kind VARCHAR(16) NOT NULL,
      value VARCHAR(191) NOT NULL,
      PRIMARY KEY (group_id, kind, value),
      INDEX idx_perms_gn_group (group_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);
  // widen value column if older 128 schema
  try {
    await conn.query(
      `ALTER TABLE ${prefix}perms_group_node MODIFY value VARCHAR(191) NOT NULL`,
    );
    await conn.query(
      `ALTER TABLE ${prefix}perms_user_node MODIFY value VARCHAR(191) NOT NULL`,
    );
  } catch {
    /* ignore */
  }
  await conn.query(`
    CREATE TABLE IF NOT EXISTS ${prefix}perms_user (
      uuid CHAR(36) PRIMARY KEY,
      username VARCHAR(16) NOT NULL,
      primary_group VARCHAR(64) NOT NULL,
      updated_at DATETIME NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);
  await conn.query(`
    CREATE TABLE IF NOT EXISTS ${prefix}perms_user_node (
      uuid CHAR(36) NOT NULL,
      scope VARCHAR(64) NOT NULL,
      kind VARCHAR(16) NOT NULL,
      value VARCHAR(191) NOT NULL,
      PRIMARY KEY (uuid, scope, kind, value),
      INDEX idx_perms_un_user (uuid, scope)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);

  let groupsMerged = 0;
  let groupNodes = 0;
  let usersMerged = 0;
  let userNodes = 0;

  if (tGroups) {
    const [rows] = await conn.query(`SELECT * FROM \`${tGroups}\``);
    for (const row of rows) {
      const id = String(row.name || "").toLowerCase();
      if (!id) continue;
      const display = String(row.displayname || row.display_name || id).slice(0, 64);
      await conn.query(
        `INSERT INTO ${prefix}perms_group (group_id, display, prefix, priority, updated_at)
         VALUES (?, ?, '', 0, UTC_TIMESTAMP())
         ON DUPLICATE KEY UPDATE display = VALUES(display), updated_at = UTC_TIMESTAMP()`,
        [id, display],
      );
      groupsMerged++;
    }
  }

  if (tGroupPerms) {
    const [rows] = await conn.query(`SELECT * FROM \`${tGroupPerms}\``);
    for (const row of rows) {
      const groupId = String(row.name || row.group_name || "").toLowerCase();
      const permission = String(row.permission || "");
      const value = row.value;
      if (!groupId || !permission) continue;

      if (permission === "weight" || permission.startsWith("weight.")) {
        const w = Number(value);
        if (!Number.isNaN(w)) {
          await conn.query(
            `UPDATE ${prefix}perms_group SET priority = ?, updated_at = UTC_TIMESTAMP() WHERE group_id = ?`,
            [w, groupId],
          );
        }
        continue;
      }
      if (permission === "displayname" || permission.startsWith("displayname.")) {
        await conn.query(
          `UPDATE ${prefix}perms_group SET display = ?, updated_at = UTC_TIMESTAMP() WHERE group_id = ?`,
          [String(value).slice(0, 64), groupId],
        );
        continue;
      }
      if (permission === "prefix" || permission.startsWith("prefix.")) {
        await conn.query(
          `UPDATE ${prefix}perms_group SET prefix = ?, updated_at = UTC_TIMESTAMP() WHERE group_id = ?`,
          [String(value).slice(0, 128), groupId],
        );
        continue;
      }

      let kind = "permission";
      let nodeVal = permission.toLowerCase();
      if (permission.toLowerCase().startsWith("group.")) {
        kind = "group";
        nodeVal = permission.slice(6).toLowerCase();
      } else if (String(value).toLowerCase() === "false") {
        nodeVal = `-${permission.toLowerCase()}`;
      }
      nodeVal = nodeVal.slice(0, 191);

      await conn.query(
        `INSERT IGNORE INTO ${prefix}perms_group (group_id, display, prefix, priority, updated_at)
         VALUES (?, ?, '', 0, UTC_TIMESTAMP())`,
        [groupId, groupId],
      );
      await conn.query(
        `INSERT IGNORE INTO ${prefix}perms_group_node (group_id, kind, value) VALUES (?, ?, ?)`,
        [groupId, kind, nodeVal],
      );
      groupNodes++;
    }
  }

  if (tPlayers) {
    const [rows] = await conn.query(`SELECT * FROM \`${tPlayers}\``);
    for (const row of rows) {
      const id = dashUuid(row.uuid);
      if (!id || id.length < 32) continue;
      const username = String(row.username || row.name || "Unknown").slice(0, 16);
      const primary = String(row.primary_group || "default").toLowerCase() || "default";
      await conn.query(
        `INSERT INTO ${prefix}perms_user (uuid, username, primary_group, updated_at)
         VALUES (?, ?, ?, UTC_TIMESTAMP())
         ON DUPLICATE KEY UPDATE username = VALUES(username),
           primary_group = VALUES(primary_group), updated_at = UTC_TIMESTAMP()`,
        [id, username, primary],
      );
      usersMerged++;
    }
  }

  if (tUserPerms) {
    const [rows] = await conn.query(`SELECT * FROM \`${tUserPerms}\``);
    for (const row of rows) {
      const uuid = dashUuid(row.uuid);
      const permission = String(row.permission || "");
      const value = row.value;
      if (!uuid || !permission) continue;
      let kind = "permission";
      let nodeVal = permission.toLowerCase();
      if (permission.toLowerCase().startsWith("group.")) {
        kind = "group";
        nodeVal = permission.slice(6).toLowerCase();
      } else if (String(value).toLowerCase() === "false") {
        nodeVal = `-${permission.toLowerCase()}`;
      }
      nodeVal = nodeVal.slice(0, 191);
      await conn.query(
        `INSERT IGNORE INTO ${prefix}perms_user_node (uuid, scope, kind, value) VALUES (?, '*', ?, ?)`,
        [uuid, kind, nodeVal],
      );
      userNodes++;
    }
  }

  const [[gCount]] = await conn.query(`SELECT COUNT(*) AS c FROM ${prefix}perms_group`);
  const [[gnCount]] = await conn.query(`SELECT COUNT(*) AS c FROM ${prefix}perms_group_node`);
  const [[uCount]] = await conn.query(`SELECT COUNT(*) AS c FROM ${prefix}perms_user`);
  const [[unCount]] = await conn.query(`SELECT COUNT(*) AS c FROM ${prefix}perms_user_node`);
  const [[defNodes]] = await conn.query(
    `SELECT COUNT(*) AS c FROM ${prefix}perms_group_node WHERE group_id='default' AND kind='permission'`,
  );

  console.log(
    JSON.stringify(
      {
        merged: { groupsMerged, groupNodes, usersMerged, userNodes },
        totals: {
          groups: gCount.c,
          groupNodes: gnCount.c,
          users: uCount.c,
          userNodes: unCount.c,
          defaultPerms: defNodes.c,
        },
      },
      null,
      2,
    ),
  );

  await conn.end();
}

main().catch((e) => {
  console.error(e.message || e);
  process.exit(1);
});
