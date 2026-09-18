import { NextResponse } from "next/server";
import { DIRECTORY } from "@/lib/publicReplies";

function originBase(): string {
  const raw = (process.env.AVA_ORIGIN_URL || "").replace(/\/$/, "");
  if (!raw || /api\.rootrecord\.online/i.test(raw)) {
    return "https://origin.avaivy.cloud";
  }
  return raw;
}

export async function POST(req: Request) {
  const body = await req.json().catch(() => ({}));
  const message = String(body.message || "").trim();
  const surface = String(body.surface || "public");

  try {
    const r = await fetch(`${originBase()}/api/chat`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        cookie: req.headers.get("cookie") || "",
      },
      body: JSON.stringify({
        message,
        context: body.context || "",
        surface,
        history: Array.isArray(body.history) ? body.history : [],
        session_id: String(body.session_id || ""),
      }),
      signal: AbortSignal.timeout(60000),
    });
    if (r.ok) {
      const data = await r.json();
      if (data?.reply) return NextResponse.json(data);
    }
  } catch {
    /* origin dark — fall through */
  }

  return NextResponse.json({
    reply: DIRECTORY,
    brain: "directory",
    login: "https://avaivy.cloud/login",
  });
}
