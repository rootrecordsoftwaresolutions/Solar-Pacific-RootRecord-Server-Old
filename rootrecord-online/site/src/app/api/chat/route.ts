import { NextResponse } from "next/server";

const ORIGIN = process.env.AVA_ORIGIN_URL || "https://origin.avaivy.cloud";
const FALLBACK =
  "Ask about solar, the host, Kilauea, or weather. RootMC if you want the game. " +
  "Live board: https://rootrecord.cloud.";

export async function POST(req: Request) {
  const body = await req.json().catch(() => ({}));
  const message = String(body.message || "").trim();
  try {
    const response = await fetch(`${ORIGIN}/api/chat`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        cookie: req.headers.get("cookie") || "",
      },
      body: JSON.stringify({ message, surface: "public", history: [] }),
      signal: AbortSignal.timeout(60000),
    });
    if (response.ok) {
      const data = await response.json();
      if (data?.reply) return NextResponse.json(data);
    }
  } catch {
    // The local origin may be offline; public chat still gets a useful reply.
  }
  return NextResponse.json({ reply: FALLBACK, brain: "directory" });
}
