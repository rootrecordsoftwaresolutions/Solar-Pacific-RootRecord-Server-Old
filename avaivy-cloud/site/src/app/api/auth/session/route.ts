import { NextResponse } from "next/server";

const ACCOUNT_API = (
  process.env.ROOTRECORD_API_ACCOUNT_URL ||
  process.env.NEXT_PUBLIC_ACCOUNT_API ||
  "https://api.rootrecord.info"
).replace(/\/$/, "");

export async function GET(req: Request) {
  const cookie = req.headers.get("cookie") || "";
  const auth = req.headers.get("authorization") || "";
  let token = "";
  const m = cookie.match(/(?:^|;\s*)ava_session=([^;]+)/);
  if (m) {
    try {
      token = decodeURIComponent(m[1]);
    } catch {
      token = m[1];
    }
  }
  if (!token && auth.toLowerCase().startsWith("bearer ")) {
    token = auth.slice(7).trim();
  }
  if (!token) {
    return NextResponse.json({ loggedIn: false, login: "/login" });
  }

  try {
    const r = await fetch(`${ACCOUNT_API}/api/auth/me`, {
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/json",
      },
      signal: AbortSignal.timeout(8000),
    });
    if (!r.ok) {
      return NextResponse.json({ loggedIn: false, login: "/login" });
    }
    const me = (await r.json()) as { email?: string; account_id?: string };
    return NextResponse.json({
      loggedIn: true,
      email: me.email || null,
      account_id: me.account_id || null,
      login: "/login",
    });
  } catch {
    return NextResponse.json({ loggedIn: false, login: "/login" });
  }
}
