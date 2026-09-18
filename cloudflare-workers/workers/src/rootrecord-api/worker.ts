/**
 * rootrecord-api — Pages frontend + /v1 account proxy + Ava /api proxy
 */
import { avaIsAwake } from "../shared/heartbeat";
import { isPublicData, isPrivatePath } from "../shared/publicPaths";
import { fetchFrontend, offlineApiJson, proxyToOrigin, wantsJson } from "../shared/proxy";
import { statusJson } from "../shared/statusPage";
import { goalsHiddenPage, maintenancePage } from "../shared/maintenancePage";
import type { AvaEnv, ScheduledEvent } from "../shared/types";

const ORIGIN = "https://origin.avaivy.cloud";
const PAGES_FRONTEND = "https://rootrecord-info.pages.dev";

function withCredentialCors(request: Request, res: Response): Response {
  const origin = request.headers.get("Origin") || "";
  const headers = new Headers(res.headers);
  headers.delete("content-encoding");
  if (
    origin === "https://rootrecord.info" ||
    origin === "https://www.rootrecord.info" ||
    origin === "https://rootrecord.online" ||
    origin === "https://rootrecord.cloud" ||
    origin === "https://www.rootrecord.cloud" ||
    origin.endsWith(".rootrecord.info") ||
    origin.endsWith(".rootrecord.online") ||
    origin.endsWith(".rootrecord.cloud") ||
    origin.endsWith(".pages.dev") ||
    origin.endsWith(".vercel.app") ||
    origin === "https://avaivy.cloud" ||
    origin === "https://www.avaivy.cloud" ||
    origin === "https://alexrs94.site" ||
    origin === "https://www.alexrs94.site"
  ) {
    headers.set("Access-Control-Allow-Origin", origin);
    headers.set("Access-Control-Allow-Credentials", "true");
    headers.set("Vary", "Origin");
    headers.set(
      "Access-Control-Allow-Headers",
      "Accept, Authorization, Cookie, X-Guest-Id, X-RR-App-Id, Content-Type, Cache-Control, Pragma",
    );
    headers.set("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS");
    headers.set("Access-Control-Max-Age", "86400");
  }
  return new Response(res.body, { status: res.status, statusText: res.statusText, headers });
}

export default {
  async fetch(request: Request, env: AvaEnv): Promise<Response> {
    const url = new URL(request.url);
    const origin = env.AVA_ORIGIN_URL || ORIGIN;
    const path = url.pathname;

    if (
      path === "/ops" ||
      path.startsWith("/ops/") ||
      path.startsWith("/api/ops") ||
      path === "/ava/ops" ||
      path.startsWith("/ava/ops/") ||
      path === "/api/business" ||
      path.startsWith("/api/business/")
    ) {
      return new Response(null, { status: 404 });
    }

    if (
      path === "/goals" ||
      path.startsWith("/goals/") ||
      path.startsWith("/api/goals")
    ) {
      return goalsHiddenPage();
    }

    // Hold public account dashboards until inventory is sorted.
    const accountApiPaths =
      path === "/api/me" ||
      path.startsWith("/api/me/") ||
      path === "/api/locations" ||
      path.startsWith("/api/locations/") ||
      path === "/api/auth" ||
      path.startsWith("/api/auth/");
    if (accountApiPaths) {
      if (request.method === "OPTIONS") {
        return withCredentialCors(request, new Response(null, { status: 204 }));
      }
      const accept = request.headers.get("Accept") || "";
      if (accept.includes("application/json")) {
        return withCredentialCors(
          request,
          new Response(JSON.stringify({ error: "dashboards_held", detail: "Account dashboards are hidden until inventory is verified." }), {
            status: 503,
            headers: { "Content-Type": "application/json" },
          }),
        );
      }
      return maintenancePage();
    }

    // Account API proxy (same-origin when called as rootrecord.info/v1/*)
    if (path === "/v1" || path.startsWith("/v1/")) {
      if (request.method === "OPTIONS") {
        return withCredentialCors(request, new Response(null, { status: 204 }));
      }
      return withCredentialCors(
        request,
        new Response(JSON.stringify({ error: "dashboards_held" }), {
          status: 503,
          headers: { "Content-Type": "application/json" },
        }),
      );
    }

    if (path === "/ava/status.json") return statusJson(env);

    if (
      path === "/status" || path === "/status/" ||
      path === "/ava/status" || path === "/ava/status/" ||
      path === "/ava" || path === "/ava/"
    ) {
      return proxyToOrigin(request, {
        originUrl: origin,
        path: "/status",
        timeoutMs: 8000,
        offlineFallback: () => maintenancePage(),
      });
    }

    // Root Record Radio — player + stream + desk APIs (same as ava-api).
    const radioPath = path.replace(/\/+$/, "") || "/";
    if (
      radioPath === "/radio" ||
      radioPath === "/radio/listen" ||
      path.startsWith("/radio/") ||
      radioPath === "/api/radio/now" ||
      radioPath === "/api/radio/steering" ||
      radioPath === "/api/radio/session" ||
      radioPath === "/api/radio/status" ||
      radioPath === "/api/radio/hurricane" ||
      radioPath === "/api/radio/wake" ||
      radioPath === "/api/radio/vote" ||
      radioPath === "/api/radio/heartbeat" ||
      radioPath === "/api/radio/skip"
    ) {
      const { isRadioStreamPath, isPublicWrite, isReadMethod } = await import("../shared/publicPaths");
      if (
        !isReadMethod(request.method) &&
        !isPublicWrite(request.method, radioPath) &&
        !isPublicWrite(request.method, path)
      ) {
        return new Response(null, { status: 405 });
      }
      return proxyToOrigin(request, {
        originUrl: origin,
        path: path,
        timeoutMs: isRadioStreamPath(radioPath) || isRadioStreamPath(path) ? 0 : 15000,
        noTimeout: isRadioStreamPath(radioPath) || isRadioStreamPath(path),
        offlineFallback: () => maintenancePage(),
      });
    }

    if (isPrivatePath(path)) {
      return new Response(null, { status: 404 });
    }
    if (path === "/health" || isPublicData(path)) {
      return proxyToOrigin(request, {
        originUrl: origin,
        path: path.startsWith("/ava/") ? path.slice("/ava".length) : undefined,
        offlineFallback: () =>
          wantsJson(request, path) ? offlineApiJson() : maintenancePage(),
      });
    }

    if (path === "/api/site-config" || path === "/api/site-config.json") {
      try {
        return await fetchFrontend(request, PAGES_FRONTEND);
      } catch {
        return maintenancePage();
      }
    }

    try {
      return await fetchFrontend(request, PAGES_FRONTEND);
    } catch {
      return maintenancePage();
    }
  },

  async scheduled(_event: ScheduledEvent, env: AvaEnv): Promise<void> {
    const { probeOrigin } = await import("../shared/uptime");
    if (await probeOrigin(env, env.AVA_ORIGIN_URL || ORIGIN)) return;
    if (await avaIsAwake(env)) return;
  },
};
