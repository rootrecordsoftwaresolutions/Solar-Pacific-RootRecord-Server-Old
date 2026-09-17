/**
 * Editable content blobs for whitelabel sites + personal digests (alexrs94).
 * Auth: same license_accounts sessions as Website Hosting.
 */
import type { D1Database } from "@cloudflare/workers-types";
import { json } from "./cors";
import { sessionFromRequest, extractAuthToken, type AuthEnv } from "./primary-auth";

export type ContentEnv = AuthEnv & {
  DB: D1Database;
  /** Comma-separated emails allowed to edit site_key=alexrs94 without owning an rr_sites row. */
  ALEX_SITE_EDITOR_EMAILS?: string;
};

type BlobRow = {
  id: string;
  owner_account_id: string;
  site_key: string;
  kind: string;
  path: string;
  title: string;
  body: string;
  meta_json: string;
  updated_at: string;
  created_at: string;
};

function clamp(raw: unknown, max: number): string {
  return String(raw ?? "")
    .trim()
    .slice(0, max);
}

function parseMeta(raw: string | null | undefined): Record<string, unknown> {
  try {
    const v = JSON.parse(raw || "{}");
    return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

function rowOut(row: BlobRow) {
  return {
    id: row.id,
    siteKey: row.site_key,
    kind: row.kind,
    path: row.path,
    title: row.title,
    body: row.body,
    meta: parseMeta(row.meta_json),
    updatedAt: row.updated_at,
    createdAt: row.created_at,
  };
}

async function requireSession(
  request: Request,
  env: ContentEnv,
): Promise<{ accountId: string; email: string } | Response> {
  const sess = await sessionFromRequest(env, request);
  if (!sess) {
    return json({ detail: extractAuthToken(request) ? "Sign in required." : "Sign in required." }, 401);
  }
  return sess;
}

function alexEditorAllowed(env: ContentEnv, email: string): boolean {
  const raw = String(env.ALEX_SITE_EDITOR_EMAILS || "").trim();
  const list = (raw || "rootrecord@outlook.com,alexanderstorey94@gmail.com,wildecho94@gmail.com,ava@rootrecord.info")
    .split(",")
    .map((s) => s.trim().toLowerCase())
    .filter(Boolean);
  return list.includes(email.trim().toLowerCase());
}

async function assertCanEdit(
  env: ContentEnv,
  sess: { accountId: string; email: string },
  siteKey: string,
): Promise<Response | null> {
  const key = clamp(siteKey, 80).toLowerCase();
  if (!key) return json({ detail: "Missing site key." }, 400);
  if (key === "alexrs94" || key === "alexrs94.site") {
    if (!alexEditorAllowed(env, sess.email)) {
      return json({ detail: "Not allowed to edit this site." }, 403);
    }
    return null;
  }
  // UUID or slug → must own rr_sites row
  try {
    const row = await env.DB.prepare(
      `SELECT id FROM rr_sites WHERE account_id = ? AND (id = ? OR lower(slug) = ?) LIMIT 1`,
    )
      .bind(sess.accountId, key, key)
      .first<{ id: string }>();
    if (!row) return json({ detail: "You do not own this site." }, 403);
  } catch {
    return json({ detail: "Sites unavailable." }, 503);
  }
  return null;
}

async function handleList(
  env: ContentEnv,
  siteKey: string,
  kind: string | null,
): Promise<Response> {
  const key = clamp(siteKey, 80);
  try {
    let rows: BlobRow[] = [];
    if (kind) {
      rows = (
        await env.DB.prepare(
          `SELECT * FROM rr_content_blobs WHERE site_key = ? AND kind = ? ORDER BY updated_at DESC LIMIT 500`,
        )
          .bind(key, kind)
          .all<BlobRow>()
      ).results;
    } else {
      rows = (
        await env.DB.prepare(
          `SELECT * FROM rr_content_blobs WHERE site_key = ? ORDER BY updated_at DESC LIMIT 500`,
        )
          .bind(key)
          .all<BlobRow>()
      ).results;
    }
    return json({ items: (rows || []).map(rowOut) }, 200);
  } catch {
    return json({ detail: "Content table unavailable (run migration 0141).", items: [] }, 503);
  }
}

async function handleGet(env: ContentEnv, siteKey: string, kind: string, path: string): Promise<Response> {
  try {
    const row = await env.DB.prepare(
      `SELECT * FROM rr_content_blobs WHERE site_key = ? AND kind = ? AND path = ? LIMIT 1`,
    )
      .bind(clamp(siteKey, 80), clamp(kind, 40), clamp(path, 200))
      .first<BlobRow>();
    if (!row) return json({ detail: "Not found." }, 404);
    return json({ item: rowOut(row) }, 200);
  } catch {
    return json({ detail: "Content table unavailable." }, 503);
  }
}

async function handleUpsert(
  request: Request,
  env: ContentEnv,
  sess: { accountId: string; email: string },
): Promise<Response> {
  let body: {
    siteKey?: string;
    kind?: string;
    path?: string;
    title?: string;
    body?: string;
    meta?: Record<string, unknown>;
  } = {};
  try {
    body = (await request.json()) as typeof body;
  } catch {
    return json({ detail: "Invalid JSON." }, 400);
  }
  const siteKey = clamp(body.siteKey, 80);
  const kind = clamp(body.kind || "page", 40) || "page";
  const path = clamp(body.path, 200).replace(/^\/+/, "") || "home";
  const gate = await assertCanEdit(env, sess, siteKey);
  if (gate) return gate;

  const title = clamp(body.title, 200);
  const text = String(body.body ?? "").slice(0, 200_000);
  const meta = body.meta && typeof body.meta === "object" ? body.meta : {};
  const now = new Date().toISOString();

  try {
    const existing = await env.DB.prepare(
      `SELECT id FROM rr_content_blobs WHERE site_key = ? AND kind = ? AND path = ? LIMIT 1`,
    )
      .bind(siteKey, kind, path)
      .first<{ id: string }>();

    if (existing?.id) {
      await env.DB.prepare(
        `UPDATE rr_content_blobs SET title = ?, body = ?, meta_json = ?, updated_at = ?, owner_account_id = ?
         WHERE id = ?`,
      )
        .bind(title, text, JSON.stringify(meta), now, sess.accountId, existing.id)
        .run();
      const row = await env.DB.prepare(`SELECT * FROM rr_content_blobs WHERE id = ?`)
        .bind(existing.id)
        .first<BlobRow>();
      return json({ item: row ? rowOut(row) : null }, 200);
    }

    const id = crypto.randomUUID();
    await env.DB.prepare(
      `INSERT INTO rr_content_blobs (
        id, owner_account_id, site_key, kind, path, title, body, meta_json, updated_at, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    )
      .bind(id, sess.accountId, siteKey, kind, path, title, text, JSON.stringify(meta), now, now)
      .run();
    const row = await env.DB.prepare(`SELECT * FROM rr_content_blobs WHERE id = ?`).bind(id).first<BlobRow>();
    return json({ item: row ? rowOut(row) : null }, 201);
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    return json({ detail: "Save failed.", error: msg }, 500);
  }
}

async function handleDelete(
  env: ContentEnv,
  sess: { accountId: string; email: string },
  siteKey: string,
  kind: string,
  path: string,
): Promise<Response> {
  const gate = await assertCanEdit(env, sess, siteKey);
  if (gate) return gate;
  try {
    await env.DB.prepare(
      `DELETE FROM rr_content_blobs WHERE site_key = ? AND kind = ? AND path = ?`,
    )
      .bind(clamp(siteKey, 80), clamp(kind, 40), clamp(path, 200))
      .run();
    return json({ ok: true }, 200);
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    return json({ detail: "Delete failed.", error: msg }, 500);
  }
}

/** Public read (no auth). */
export async function handlePublicContentPath(
  request: Request,
  env: ContentEnv,
  pathname: string,
  method: string,
): Promise<Response | null> {
  const list = pathname.match(/^\/public\/content\/([^/]+)\/?$/);
  if (list) {
    if (method !== "GET") return json({ detail: "Method not allowed." }, 405);
    const url = new URL(request.url);
    return handleList(env, decodeURIComponent(list[1]), url.searchParams.get("kind"));
  }
  const one = pathname.match(/^\/public\/content\/([^/]+)\/([^/]+)\/(.+)$/);
  if (one) {
    if (method !== "GET") return json({ detail: "Method not allowed." }, 405);
    return handleGet(env, decodeURIComponent(one[1]), decodeURIComponent(one[2]), decodeURIComponent(one[3]));
  }
  return null;
}

/** /api/content/* owner routes */
export async function handleContentRoutes(
  request: Request,
  env: ContentEnv,
  sub: string,
  method: string,
): Promise<Response | null> {
  const path = sub.replace(/\/+$/, "") || sub;
  if (!path.startsWith("/content")) return null;

  if (method === "GET" && path.startsWith("/content/")) {
    // /content/:siteKey?kind=
    const rest = path.slice("/content/".length);
    const siteKey = decodeURIComponent(rest.split("/")[0] || "");
    const url = new URL(request.url);
    const sess = await requireSession(request, env);
    if (sess instanceof Response) return sess;
    const gate = await assertCanEdit(env, sess, siteKey);
    if (gate) return gate;
    return handleList(env, siteKey, url.searchParams.get("kind"));
  }

  if (method === "PUT" || method === "POST") {
    if (path === "/content" || path === "/content/upsert") {
      const sess = await requireSession(request, env);
      if (sess instanceof Response) return sess;
      return handleUpsert(request, env, sess);
    }
  }

  if (method === "DELETE") {
    const m = path.match(/^\/content\/([^/]+)\/([^/]+)\/(.+)$/);
    if (m) {
      const sess = await requireSession(request, env);
      if (sess instanceof Response) return sess;
      return handleDelete(env, sess, decodeURIComponent(m[1]), decodeURIComponent(m[2]), decodeURIComponent(m[3]));
    }
  }

  return json({ detail: "Not found." }, 404);
}
