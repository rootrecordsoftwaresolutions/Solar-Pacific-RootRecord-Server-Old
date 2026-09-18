/**
 * Reads host state from same-origin /api (proxied to origin.avaivy.cloud).
 * Desk dark / tunnel down → status OFFLINE. Never invents numbers.
 */

import { fetchDeskJson, isOffline, OFFLINE, PUBLIC_API } from "./desk-api";

export const AVA_ORIGIN = PUBLIC_API;

export interface HostStatus {
  status?: string;
  version?: string;
  ts?: string;
  uptime_s?: number;
  host?: string;
  cpu_pct?: number;
  mem_pct?: number;
  heartbeat_age_s?: number | null;
  streaming?: boolean;
  live?: { streaming?: boolean; scene?: string | null };
  config?: Record<string, unknown>;
}

export async function getHostStatus(
  revalidateSeconds = 30
): Promise<HostStatus> {
  const data = await fetchDeskJson("/api/status", 6000);
  if (isOffline(data)) return { status: OFFLINE };
  return data as HostStatus;
}

export function formatUptime(seconds: number): string {
  const d = Math.floor(seconds / 86400);
  const h = Math.floor((seconds % 86400) / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (d > 0) return `${d}d ${h}h`;
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}

