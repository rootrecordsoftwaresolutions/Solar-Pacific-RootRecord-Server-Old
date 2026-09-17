/** Public desk API for Vercel. Tunnel down → OFFLINE, never invented numbers. */

export const PUBLIC_API = (
  process.env.NEXT_PUBLIC_AVA_API ||
  process.env.AVA_PUBLIC_API ||
  "https://api.rootrecord.online"
).replace(/\/$/, "");

export const OFFLINE = "OFFLINE";

export type DeskPayload = Record<string, unknown> & { status?: string };

export function isOffline(data: unknown): boolean {
  if (!data || typeof data !== "object") return true;
  return (data as DeskPayload).status === OFFLINE;
}

export async function fetchDeskJson(path: string, timeoutMs = 8000): Promise<DeskPayload> {
  const url = path.startsWith("http") ? path : `${PUBLIC_API}${path.startsWith("/") ? path : `/${path}`}`;
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
