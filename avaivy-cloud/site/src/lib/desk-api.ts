/** Public desk API for Vercel. Tunnel down → OFFLINE, never invented numbers.
 * Browser uses same-origin `/api/*` (Next proxy → origin). Server may hit origin directly.
 */

const ORIGIN = (process.env.AVA_ORIGIN_URL || "https://origin.avaivy.cloud").replace(/\/$/, "");

/** Empty in browser = same-origin. Absolute origin only on the server when needed. */
export function publicApiBase(): string {
  if (typeof window !== "undefined") return "";
  const fromEnv = (process.env.NEXT_PUBLIC_AVA_API || process.env.AVA_PUBLIC_API || "").replace(
    /\/$/,
    "",
  );
  // Dead / wrong host — never use api.rootrecord.online
  if (!fromEnv || /api\.rootrecord\.online/i.test(fromEnv)) return ORIGIN;
  return fromEnv;
}

export const PUBLIC_API = publicApiBase();

export const OFFLINE = "OFFLINE";

export type DeskPayload = Record<string, unknown> & { status?: string };

export function isOffline(data: unknown): boolean {
  if (!data || typeof data !== "object") return true;
  return (data as DeskPayload).status === OFFLINE;
}

export async function fetchDeskJson(path: string, timeoutMs = 8000): Promise<DeskPayload> {
  const rel = path.startsWith("/") ? path : `/${path}`;
  const url = path.startsWith("http") ? path : `${publicApiBase()}${rel}`;
  try {
    const res = await fetch(url, {
      cache: "no-store",
      signal: AbortSignal.timeout(timeoutMs),
    });
    const data = (await res.json()) as DeskPayload;
    if (!res.ok || isOffline(data)) return { status: OFFLINE };
    return data;
  } catch {
    return { status: OFFLINE };
  }
}

export function offlineLabel(data: unknown, live: string): string {
  return isOffline(data) ? OFFLINE : live;
}
