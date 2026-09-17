/**
 * Vanity hosted-site URLs → static shell at /sites/index.html
 *   /<uuid>-Website
 *   /sites/<uuid>
 */
type Env = { ASSETS: { fetch: (input: Request | string) => Promise<Response> } };

type PagesContext = {
  request: Request;
  next: () => Promise<Response>;
  env: Env;
};

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function wantsSiteShell(pathname: string): boolean {
  const parts = pathname.replace(/\/+$/, "").split("/").filter(Boolean);
  if (parts.length === 1) {
    const m = decodeURIComponent(parts[0]).match(/^([0-9a-f-]{36})-Website$/i);
    if (m && UUID_RE.test(m[1])) return true;
  }
  if (parts[0] === "sites" && parts[1]) {
    const id = decodeURIComponent(parts[1]);
    if (UUID_RE.test(id)) return true;
  }
  return false;
}

export async function onRequest(context: PagesContext): Promise<Response> {
  const url = new URL(context.request.url);
  if (!wantsSiteShell(url.pathname)) {
    return context.next();
  }
  const assetUrl = new URL("/sites/index.html", url.origin);
  return context.env.ASSETS.fetch(assetUrl.toString());
}
