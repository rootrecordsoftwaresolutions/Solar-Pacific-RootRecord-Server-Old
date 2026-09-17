/**
 * Website Hosting API.
 * Auth: license_accounts sessions via sessionFromRequest (same as Visiting Hawaiʻi).
 * Create requires sign-in. Recurring $10/mo via Stripe Checkout (website_hosting product).
 */

import type { D1Database } from "@cloudflare/workers-types";
import { json } from "./cors";
import { sessionFromRequest, extractAuthToken, type AuthEnv } from "./primary-auth";
import {
  createWebsiteHostingCheckout,
  websiteHostingCheckoutAvailable,
} from "./billing-stripe";

export type SitesEnv = AuthEnv & {
  DB: D1Database;
  SITE_URL?: string;
  /** Recurring Price id for Website Hosting ($10/mo). Optional for scaffold. */
  STRIPE_WEBSITE_HOSTING_PRICE_ID?: string;
  STRIPE_SECRET_KEY?: string;
  CLOUDFLARE_ACCOUNT_ID?: string;
  PAGES_PROJECT_NAME?: string;
  CLOUDFLARE_API_TOKEN?: string;
  CLOUDFLARE_API_KEY?: string;
  CLOUDFLARE_EMAIL?: string;
};

type SiteRow = {
  id: string;
  account_id: string;
  slug: string | null;
  title: string;
  custom_domain: string | null;
  nameserver_status: string;
  trial_ends_at: string | null;
  subscription_status: string;
  stripe_subscription_id: string | null;
  config_json: string;
  created_at: string;
  updated_at: string;
};

type InvoiceRow = {
  id: string;
  site_id: string;
  amount_cents: number;
  due_at: string;
  paid_at: string | null;
  stripe_invoice_id: string | null;
  status: string;
  created_at: string;
  updated_at: string;
};

const TRIAL_DAYS = 14;
const MONTHLY_CENTS = 1000;
/** Fallback NS shown before zone provisioning returns zone-specific values. */
const CF_NS_DEFAULT = ["anahi.ns.cloudflare.com", "yoxall.ns.cloudflare.com"] as const;

function clampText(raw: unknown, max: number): string {
  return String(raw ?? "")
    .trim()
    .slice(0, max);
}

function parseConfig(raw: string | null | undefined): Record<string, unknown> {
  try {
    const v = JSON.parse(raw || "{}");
    return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

function siteAccessActive(row: SiteRow, now = new Date()): boolean {
  const status = String(row.subscription_status || "").toLowerCase();
  if (status === "active") return true;
  // incomplete = created, awaiting $10/mo Website Hosting subscription — still editable
  if (status === "incomplete") return true;
  if (status === "trialing") {
    if (!row.trial_ends_at) return true;
    const ends = Date.parse(row.trial_ends_at);
    return Number.isFinite(ends) ? ends > now.getTime() : true;
  }
  return false;
}

function publicPathFor(row: SiteRow, siteUrl: string): string {
  const base = (siteUrl || "https://rootrecord.info").replace(/\/+$/, "");
  // Vanity path: rootrecord.info/<uuid>-Website (custom domain preferred when set)
  if (row.custom_domain && String(row.nameserver_status || "").toLowerCase() === "active") {
    return `https://${row.custom_domain.replace(/^https?:\/\//, "").replace(/\/+$/, "")}`;
  }
  return `${base}/${encodeURIComponent(row.id)}-Website`;
}

function hostingMeta(cfg: Record<string, unknown>): {
  nameservers?: string[];
  zoneId?: string;
  pagesDomain?: string;
} {
  const h = cfg._hosting;
  if (!h || typeof h !== "object" || Array.isArray(h)) return {};
  const o = h as Record<string, unknown>;
  const ns = Array.isArray(o.nameservers)
    ? o.nameservers.map((x) => String(x)).filter(Boolean)
    : undefined;
  return {
    nameservers: ns,
    zoneId: o.zoneId ? String(o.zoneId) : undefined,
    pagesDomain: o.pagesDomain ? String(o.pagesDomain) : undefined,
  };
}

function rowToOwner(row: SiteRow, siteUrl: string) {
  const active = siteAccessActive(row);
  const cfg = parseConfig(row.config_json);
  const host = hostingMeta(cfg);
  const nameservers =
    host.nameservers && host.nameservers.length >= 2 ? host.nameservers : [...CF_NS_DEFAULT];
  return {
    id: row.id,
    accountId: row.account_id,
    slug: row.slug,
    title: row.title,
    customDomain: row.custom_domain,
    nameserverStatus: row.nameserver_status,
    nameservers,
    defaultDomain: true,
    trialEndsAt: row.trial_ends_at,
    subscriptionStatus: row.subscription_status,
    stripeSubscriptionId: row.stripe_subscription_id,
    config: cfg,
    accessActive: active,
    paywall: !active,
    publicUrl: publicPathFor(row, siteUrl),
    altPublicUrl: `${(siteUrl || "https://rootrecord.info").replace(/\/+$/, "")}/sites/${encodeURIComponent(row.id)}`,
    monthlyPriceCents: MONTHLY_CENTS,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    cfZoneId: host.zoneId || null,
  };
}

function rowToPublic(row: SiteRow) {
  const active = siteAccessActive(row);
  const status = String(row.subscription_status || "").toLowerCase();
  const paid = status === "active";
  return {
    id: row.id,
    slug: row.slug,
    title: row.title || "Untitled site",
    customDomain: row.custom_domain,
    config: parseConfig(row.config_json),
    subscriptionStatus: row.subscription_status,
    trialEndsAt: row.trial_ends_at,
    paywall: !active,
    accessActive: active,
    /** Free / trial keep attribution; paid removes it. */
    showBuiltByBanner: !paid,
    /** Trial CTA to convert. */
    showBuildYourOwn: status === "trialing" || status === "incomplete",
    monthlyPriceCents: MONTHLY_CENTS,
  };
}

async function getSiteById(db: D1Database, id: string): Promise<SiteRow | null> {
  return db.prepare("SELECT * FROM rr_sites WHERE id = ?").bind(id).first<SiteRow>();
}

async function getSiteForAccount(db: D1Database, accountId: string): Promise<SiteRow | null> {
  return db
    .prepare("SELECT * FROM rr_sites WHERE account_id = ? LIMIT 1")
    .bind(accountId)
    .first<SiteRow>();
}

async function openInvoiceIfNeeded(db: D1Database, site: SiteRow): Promise<InvoiceRow | null> {
  if (siteAccessActive(site)) return null;
  const existing = await db
    .prepare(
      `SELECT * FROM rr_site_invoices
       WHERE site_id = ? AND status IN ('open', 'past_due')
       ORDER BY due_at DESC LIMIT 1`,
    )
    .bind(site.id)
    .first<InvoiceRow>();

  const now = new Date();
  if (existing) {
    const dueMs = Date.parse(existing.due_at);
    const ageDays = Number.isFinite(dueMs) ? (now.getTime() - dueMs) / (86400 * 1000) : 0;
    // Recur: if unpaid invoice is older than ~30 days, mark past_due and open a new one.
    if (ageDays >= 30) {
      const stamped = now.toISOString();
      await db
        .prepare(
          `UPDATE rr_site_invoices SET status = 'past_due', updated_at = ? WHERE id = ? AND status = 'open'`,
        )
        .bind(stamped, existing.id)
        .run();
      // fall through to create a fresh open invoice
    } else {
      if (existing.status === "open" && ageDays >= 7) {
        await db
          .prepare(`UPDATE rr_site_invoices SET status = 'past_due', updated_at = ? WHERE id = ?`)
          .bind(now.toISOString(), existing.id)
          .run();
        existing.status = "past_due";
      }
      return existing;
    }
  }

  const id = crypto.randomUUID();
  const due = now.toISOString();
  const created = due;
  await db
    .prepare(
      `INSERT INTO rr_site_invoices (
        id, site_id, amount_cents, due_at, paid_at, stripe_invoice_id, status, created_at, updated_at
      ) VALUES (?, ?, ?, ?, NULL, NULL, 'open', ?, ?)`,
    )
    .bind(id, site.id, MONTHLY_CENTS, due, created, created)
    .run();

  // Keep site subscription marker in past_due when access lapsed
  try {
    await db
      .prepare(
        `UPDATE rr_sites SET subscription_status = CASE
           WHEN lower(subscription_status) IN ('active','trialing') THEN 'past_due'
           ELSE subscription_status
         END, updated_at = ? WHERE id = ?`,
      )
      .bind(created, site.id)
      .run();
  } catch {
    /* ignore */
  }

  return {
    id,
    site_id: site.id,
    amount_cents: MONTHLY_CENTS,
    due_at: due,
    paid_at: null,
    stripe_invoice_id: null,
    status: "open",
    created_at: created,
    updated_at: created,
  };
}

async function requireSession(
  request: Request,
  env: SitesEnv,
): Promise<{ accountId: string; email: string } | Response> {
  const sess = await sessionFromRequest(env, request);
  if (!sess) {
    const detail = extractAuthToken(request) ? "Sign in required." : "Sign in required.";
    return json({ detail }, 401);
  }
  return sess;
}

async function handleCreate(request: Request, env: SitesEnv, sess: { accountId: string }): Promise<Response> {
  const existing = await getSiteForAccount(env.DB, sess.accountId);
  if (existing) {
    return json({ detail: "This account already has a site.", site: rowToOwner(existing, env.SITE_URL || "") }, 409);
  }

  let body: { title?: string; slug?: string; config?: Record<string, unknown> } = {};
  try {
    body = (await request.json()) as typeof body;
  } catch {
    body = {};
  }

  const title = clampText(body.title, 120) || "My site";
  let slug = clampText(body.slug, 64)
    .toLowerCase()
    .replace(/[^a-z0-9-]+/g, "-")
    .replace(/^-+|-+$/g, "");
  if (slug.length < 2) slug = "";

  const config =
    body.config && typeof body.config === "object" && !Array.isArray(body.config)
      ? body.config
      : { theme: "default", pages: [{ path: "/", title: "Home", body: "" }] };

  const now = new Date();
  const id = crypto.randomUUID();
  const created = now.toISOString();

  try {
    await env.DB.prepare(
      `INSERT INTO rr_sites (
        id, account_id, slug, title, custom_domain, nameserver_status,
        trial_ends_at, subscription_status, stripe_subscription_id, config_json,
        created_at, updated_at
      ) VALUES (?, ?, ?, ?, NULL, 'none', NULL, 'incomplete', NULL, ?, ?, ?)`,
    )
      .bind(
        id,
        sess.accountId,
        slug || null,
        title,
        JSON.stringify(config),
        created,
        created,
      )
      .run();
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    if (/UNIQUE|unique/i.test(msg)) {
      return json({ detail: "Slug already taken. Choose another." }, 409);
    }
    return json({ detail: "Could not create site (migration may be pending).", error: msg }, 503);
  }

  const row = await getSiteById(env.DB, id);
  if (!row) return json({ detail: "Created but failed to load site." }, 500);
  return json(
    {
      site: rowToOwner(row, env.SITE_URL || ""),
      priceCents: MONTHLY_CENTS,
      note: "Site created. Subscribe to Website Hosting ($10/mo) to keep it live on a recurring plan.",
      checkoutAvailable: websiteHostingCheckoutAvailable(env),
    },
    201,
  );
}

async function handleCheckout(
  env: SitesEnv,
  sess: { accountId: string; email: string },
  siteId: string,
): Promise<Response> {
  const row = await getSiteById(env.DB, siteId);
  if (!row || row.account_id !== sess.accountId) return json({ detail: "Site not found." }, 404);

  if (!websiteHostingCheckoutAvailable(env)) {
    return json(
      {
        detail:
          "Website Hosting checkout is not configured (STRIPE_SECRET_KEY + STRIPE_WEBSITE_HOSTING_PRICE_ID).",
      },
      503,
    );
  }

  const priceId = String(env.STRIPE_WEBSITE_HOSTING_PRICE_ID || "").trim();
  const secret = String(env.STRIPE_SECRET_KEY || "").trim();
  const result = await createWebsiteHostingCheckout({
    secretKey: secret,
    priceId,
    customerEmail: sess.email,
    accountId: sess.accountId,
    siteId: row.id,
    siteUrl: env.SITE_URL || "https://rootrecord.info",
  });
  if (!result.ok) return json({ detail: result.message }, 502);
  return json({ url: result.url, sessionId: result.sessionId, priceCents: MONTHLY_CENTS }, 200);
}

async function handleMe(env: SitesEnv, sess: { accountId: string }): Promise<Response> {
  const row = await getSiteForAccount(env.DB, sess.accountId);
  let invoice: InvoiceRow | null = null;
  if (row && !siteAccessActive(row)) {
    try {
      invoice = await openInvoiceIfNeeded(env.DB, row);
    } catch {
      invoice = null;
    }
  }
  return json(
    {
      site: row ? rowToOwner(row, env.SITE_URL || "") : null,
      invoice: invoice
        ? {
            id: invoice.id,
            amountCents: invoice.amount_cents,
            dueAt: invoice.due_at,
            paidAt: invoice.paid_at,
            status: invoice.status,
            stripeInvoiceId: invoice.stripe_invoice_id,
          }
        : null,
      priceCents: MONTHLY_CENTS,
      trialDays: TRIAL_DAYS,
      stripePriceConfigured: Boolean(
        (env.STRIPE_WEBSITE_HOSTING_PRICE_ID || "").trim().startsWith("price_"),
      ),
    },
    200,
  );
}

async function handlePatch(
  request: Request,
  env: SitesEnv,
  sess: { accountId: string },
  siteId: string,
): Promise<Response> {
  const row = await getSiteById(env.DB, siteId);
  if (!row || row.account_id !== sess.accountId) return json({ detail: "Site not found." }, 404);

  let body: { title?: string; slug?: string; config?: Record<string, unknown> } = {};
  try {
    body = (await request.json()) as typeof body;
  } catch {
    return json({ detail: "Invalid JSON." }, 400);
  }

  const title = body.title != null ? clampText(body.title, 120) || row.title : row.title;
  let slug = row.slug;
  if (body.slug != null) {
    const next = clampText(body.slug, 64)
      .toLowerCase()
      .replace(/[^a-z0-9-]+/g, "-")
      .replace(/^-+|-+$/g, "");
    slug = next.length >= 2 ? next : null;
  }
  let configJson = row.config_json;
  if (body.config && typeof body.config === "object" && !Array.isArray(body.config)) {
    configJson = JSON.stringify({ ...parseConfig(row.config_json), ...body.config });
  }
  const now = new Date().toISOString();

  try {
    await env.DB.prepare(
      `UPDATE rr_sites SET title = ?, slug = ?, config_json = ?, updated_at = ? WHERE id = ? AND account_id = ?`,
    )
      .bind(title, slug, configJson, now, siteId, sess.accountId)
      .run();
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    if (/UNIQUE|unique/i.test(msg)) return json({ detail: "Slug already taken." }, 409);
    return json({ detail: "Update failed.", error: msg }, 500);
  }

  const updated = await getSiteById(env.DB, siteId);
  return json({ site: updated ? rowToOwner(updated, env.SITE_URL || "") : null }, 200);
}

type CfAuth =
  | { kind: "token"; token: string }
  | { kind: "key"; email: string; key: string };

function cfAuthFromEnv(env: SitesEnv): CfAuth | null {
  const token = String(env.CLOUDFLARE_API_TOKEN || "").trim();
  if (token.length >= 20) return { kind: "token", token };
  const email = String(env.CLOUDFLARE_EMAIL || "").trim();
  const key = String(env.CLOUDFLARE_API_KEY || "").trim();
  if (email && key.length >= 20) return { kind: "key", email, key };
  return null;
}

function cfHeaders(auth: CfAuth): HeadersInit {
  if (auth.kind === "token") {
    return { Authorization: `Bearer ${auth.token}`, "Content-Type": "application/json" };
  }
  return {
    "X-Auth-Email": auth.email,
    "X-Auth-Key": auth.key,
    "Content-Type": "application/json",
  };
}

async function provisionDomainInCloudflare(
  env: SitesEnv,
  domain: string,
): Promise<{
  ok: boolean;
  provisioned: boolean;
  nameservers: string[];
  zoneId?: string;
  pagesAttached?: boolean;
  detail?: string;
}> {
  const auth = cfAuthFromEnv(env);
  const accountId = String(env.CLOUDFLARE_ACCOUNT_ID || "").trim();
  const project = String(env.PAGES_PROJECT_NAME || "rootrecord-website").trim();
  if (!auth || !accountId) {
    return {
      ok: true,
      provisioned: false,
      nameservers: [...CF_NS_DEFAULT],
      detail:
        "Domain saved. Cloudflare API credentials not configured on Worker — point nameservers to the Root Record NS below; ops can finish zone attach.",
    };
  }

  const headers = cfHeaders(auth);
  let zoneId = "";
  let nameservers: string[] = [...CF_NS_DEFAULT];
  let status = "pending";

  // Existing zone?
  const listRes = await fetch(
    `https://api.cloudflare.com/client/v4/zones?name=${encodeURIComponent(domain)}&account.id=${encodeURIComponent(accountId)}`,
    { headers },
  );
  const listJson = (await listRes.json().catch(() => ({}))) as {
    success?: boolean;
    result?: Array<{ id?: string; name_servers?: string[]; status?: string }>;
    errors?: Array<{ message?: string }>;
  };
  if (listJson.success && Array.isArray(listJson.result) && listJson.result[0]?.id) {
    zoneId = String(listJson.result[0].id);
    if (Array.isArray(listJson.result[0].name_servers) && listJson.result[0].name_servers.length) {
      nameservers = listJson.result[0].name_servers.map(String);
    }
    status = String(listJson.result[0].status || "pending");
  } else {
    const createRes = await fetch("https://api.cloudflare.com/client/v4/zones", {
      method: "POST",
      headers,
      body: JSON.stringify({
        name: domain,
        account: { id: accountId },
        jump_start: true,
        type: "full",
      }),
    });
    const createJson = (await createRes.json().catch(() => ({}))) as {
      success?: boolean;
      result?: { id?: string; name_servers?: string[]; status?: string };
      errors?: Array<{ message?: string; code?: number }>;
    };
    if (!createJson.success || !createJson.result?.id) {
      const err =
        createJson.errors?.map((e) => e.message).filter(Boolean).join("; ") ||
        "Cloudflare zone create failed.";
      return {
        ok: false,
        provisioned: false,
        nameservers: [...CF_NS_DEFAULT],
        detail: err,
      };
    }
    zoneId = String(createJson.result.id);
    if (Array.isArray(createJson.result.name_servers) && createJson.result.name_servers.length) {
      nameservers = createJson.result.name_servers.map(String);
    }
    status = String(createJson.result.status || "pending");
  }

  let pagesAttached = false;
  try {
    const pagesRes = await fetch(
      `https://api.cloudflare.com/client/v4/accounts/${encodeURIComponent(accountId)}/pages/projects/${encodeURIComponent(project)}/domains`,
      {
        method: "POST",
        headers,
        body: JSON.stringify({ name: domain }),
      },
    );
    const pagesJson = (await pagesRes.json().catch(() => ({}))) as {
      success?: boolean;
      errors?: Array<{ message?: string; code?: number }>;
    };
    //  already exists is fine
    if (
      pagesJson.success ||
      (pagesJson.errors || []).some((e) => /already|exist/i.test(String(e.message || "")))
    ) {
      pagesAttached = true;
    }
  } catch {
    /* Pages attach is best-effort; NS still works for ecosystem */
  }

  return {
    ok: true,
    provisioned: true,
    nameservers,
    zoneId,
    pagesAttached,
    detail:
      status === "active"
        ? "Domain is active in Cloudflare."
        : "Zone created/linked. Point your registrar nameservers to the values returned, then wait for active.",
  };
}

async function handleDomainPost(
  request: Request,
  env: SitesEnv,
  sess: { accountId: string },
  siteId: string,
): Promise<Response> {
  const row = await getSiteById(env.DB, siteId);
  if (!row || row.account_id !== sess.accountId) return json({ detail: "Site not found." }, 404);

  let body: { domain?: string; provision?: boolean } = {};
  try {
    body = (await request.json()) as typeof body;
  } catch {
    return json({ detail: "Invalid JSON." }, 400);
  }

  const domain = clampText(body.domain, 253)
    .toLowerCase()
    .replace(/^https?:\/\//, "")
    .replace(/\/+$/, "");
  if (!domain || !/^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$/.test(domain)) {
    return json({ detail: "Enter a valid domain (e.g. alexrs94.site)." }, 400);
  }

  const provision = body.provision !== false;
  const prov = provision
    ? await provisionDomainInCloudflare(env, domain)
    : {
        ok: true,
        provisioned: false,
        nameservers: [...CF_NS_DEFAULT],
        detail: "Domain saved (provision skipped).",
      };

  if (!prov.ok) {
    return json({ detail: prov.detail || "Could not provision domain in Cloudflare." }, 502);
  }

  const now = new Date().toISOString();
  const cfg = parseConfig(row.config_json);
  cfg._hosting = {
    nameservers: prov.nameservers,
    zoneId: prov.zoneId || null,
    pagesDomain: domain,
    pagesAttached: Boolean(prov.pagesAttached),
    updatedAt: now,
  };
  const nsStatus = "pending";

  try {
    await env.DB.prepare(
      `UPDATE rr_sites SET custom_domain = ?, nameserver_status = ?, config_json = ?, updated_at = ?
       WHERE id = ? AND account_id = ?`,
    )
      .bind(domain, nsStatus, JSON.stringify(cfg), now, siteId, sess.accountId)
      .run();
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    if (/UNIQUE|unique/i.test(msg)) {
      return json({ detail: "That domain is already linked to another site." }, 409);
    }
    return json({ detail: "Could not save domain.", error: msg }, 500);
  }

  const updated = await getSiteById(env.DB, siteId);
  return json(
    {
      site: updated ? rowToOwner(updated, env.SITE_URL || "") : null,
      nameservers: prov.nameservers,
      provisioned: prov.provisioned,
      pagesAttached: Boolean(prov.pagesAttached),
      instructions:
        "Custom domain is optional — your default Root Record URL already works. " +
        "For this custom domain: at your registrar, set nameservers to the two values returned. " +
        "Root Record Cloudflare will serve the site once DNS is active.",
      detail: prov.detail,
    },
    200,
  );
}

async function handlePublicGet(env: SitesEnv, siteId: string): Promise<Response> {
  const id = clampText(siteId, 64);
  if (!id) return json({ detail: "Missing site id." }, 400);

  let row: SiteRow | null = null;
  try {
    row = await getSiteById(env.DB, id);
    if (!row) {
      row = await env.DB.prepare("SELECT * FROM rr_sites WHERE slug = ? LIMIT 1").bind(id).first<SiteRow>();
    }
  } catch {
    return json({ detail: "Sites table unavailable (run migration 0140).", paywall: true }, 503);
  }

  if (!row) return json({ detail: "Site not found.", paywall: true }, 404);

  let invoice: InvoiceRow | null = null;
  if (!siteAccessActive(row)) {
    try {
      invoice = await openInvoiceIfNeeded(env.DB, row);
    } catch {
      /* ignore */
    }
  }

  return json(
    {
      ...rowToPublic(row),
      invoice: invoice
        ? {
            amountCents: invoice.amount_cents,
            dueAt: invoice.due_at,
            status: invoice.status,
          }
        : null,
    },
    200,
  );
}

/** Non-/api path: GET /public/sites/:id */
export async function handlePublicSitesPath(
  request: Request,
  env: SitesEnv,
  pathname: string,
  method: string,
): Promise<Response | null> {
  const m = pathname.match(/^\/public\/sites\/([^/]+)$/);
  if (!m) return null;
  if (method !== "GET") return json({ detail: "Method not allowed." }, 405);
  return handlePublicGet(env, decodeURIComponent(m[1]));
}

/** /api subpaths: /sites, /sites/me, /sites/:id, /sites/:id/domain, /public/sites/:id */
export async function handleSitesRoutes(
  request: Request,
  env: SitesEnv,
  sub: string,
  method: string,
): Promise<Response | null> {
  const path = sub.replace(/\/+$/, "") || sub;

  if (method === "GET" && path.startsWith("/public/sites/")) {
    const id = path.slice("/public/sites/".length).split("/")[0] || "";
    return handlePublicGet(env, decodeURIComponent(id));
  }

  if (!path.startsWith("/sites")) return null;

  if (method === "POST" && path === "/sites") {
    const sess = await requireSession(request, env);
    if (sess instanceof Response) return sess;
    return handleCreate(request, env, sess);
  }

  if (method === "GET" && path === "/sites/me") {
    const sess = await requireSession(request, env);
    if (sess instanceof Response) return sess;
    return handleMe(env, sess);
  }

  const patchMatch = path.match(/^\/sites\/([^/]+)$/);
  if (method === "PATCH" && patchMatch) {
    const sess = await requireSession(request, env);
    if (sess instanceof Response) return sess;
    return handlePatch(request, env, sess, decodeURIComponent(patchMatch[1]));
  }

  const domainMatch = path.match(/^\/sites\/([^/]+)\/domain$/);
  if (method === "POST" && domainMatch) {
    const sess = await requireSession(request, env);
    if (sess instanceof Response) return sess;
    return handleDomainPost(request, env, sess, decodeURIComponent(domainMatch[1]));
  }

  const checkoutMatch = path.match(/^\/sites\/([^/]+)\/checkout$/);
  if (method === "POST" && checkoutMatch) {
    const sess = await requireSession(request, env);
    if (sess instanceof Response) return sess;
    return handleCheckout(env, sess, decodeURIComponent(checkoutMatch[1]));
  }

  if (path.startsWith("/sites")) {
    return json({ detail: "Not found." }, 404);
  }
  return null;
}
