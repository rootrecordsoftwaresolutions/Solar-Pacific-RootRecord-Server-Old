import { NextResponse } from "next/server";

function originBase(): string {
  const raw = (process.env.AVA_ORIGIN_URL || "").replace(/\/$/, "");
  // Never proxy to the retired api.rootrecord.online host (DNS gone → Vercel 502).
  if (!raw || /api\.rootrecord\.online/i.test(raw)) {
    return "https://origin.avaivy.cloud";
  }
  return raw;
}

const PUBLIC_EXACT = new Set([
  "/api/air-quality/current",
  "/api/dashboard",
  "/api/desk/notifications",
  "/api/disruption-banner",
  "/api/earthquakes/global",
  "/api/kilauea",
  "/api/live",
  "/api/health",
  "/api/media/public",
  "/api/mobile/kilauea-live-streams",
  "/api/mobile/kilauea-situation",
  "/api/news/global",
  "/api/photos/gallery",
  "/api/reports",
  "/api/reports/current",
  "/api/site-config",
  "/api/solar",
  "/api/solar/history",
  "/api/solar/rollups",
  "/api/status",
  "/api/weather",
  "/api/minecraft/status",
]);

function apiPath(segments: string[]) {
  return `/api/${segments.join("/")}`;
}

function allowed(path: string) {
  return (
    PUBLIC_EXACT.has(path) ||
    path.startsWith("/api/photos/file/") ||
    path.startsWith("/api/media/public/")
  );
}

async function proxy(req: Request, path: string) {
  if (!allowed(path)) return NextResponse.json({ detail: "not found" }, { status: 404 });
  try {
    const source = new URL(req.url);
    const target = new URL(`${originBase()}${path}`);
    target.search = source.search;
    const response = await fetch(target, {
      method: req.method,
      headers: { accept: req.headers.get("accept") || "application/json" },
      signal: AbortSignal.timeout(15000),
    });
    return new NextResponse(response.body, {
      status: response.status,
      headers: {
        "content-type": response.headers.get("content-type") || "application/json",
        "cache-control": "public, max-age=0, s-maxage=30, stale-while-revalidate=120",
      },
    });
  } catch {
    return NextResponse.json({ ok: false, detail: "origin offline" }, { status: 503 });
  }
}

export async function GET(req: Request, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  return proxy(req, apiPath(path));
}

export async function HEAD(req: Request, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  return proxy(req, apiPath(path));
}
