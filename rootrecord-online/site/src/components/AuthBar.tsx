"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import styles from "./AuthBar.module.css";

const PORTAL_TOKEN_KEY = "rootrecord_portal_token";
const GOALS_TOKEN_KEY = "rr_goals_token";
const DEVICE_KEY = "rootrecord_portal_device_id";
/** Prefer *.rootrecord.info so credentialed CORS + SSO cookie can work. */
const API_BASE =
  process.env.NEXT_PUBLIC_ACCOUNT_API || "https://api.rootrecord.info";

type Me = { email?: string; account_id?: string } | null;

function deviceId(): string {
  if (typeof window === "undefined") return "web";
  let id = localStorage.getItem(DEVICE_KEY);
  if (!id || id.length < 8) {
    const bytes = new Uint8Array(24);
    crypto.getRandomValues(bytes);
    id = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
    localStorage.setItem(DEVICE_KEY, id);
  }
  return id;
}

function writeAllTokens(tok: string) {
  localStorage.setItem(PORTAL_TOKEN_KEY, tok);
  localStorage.setItem(GOALS_TOKEN_KEY, tok);
  try {
    const maxAge = 60 * 60 * 24 * 30;
    document.cookie = `ava_session=${encodeURIComponent(tok)}; path=/; max-age=${maxAge}; SameSite=Lax; Secure`;
  } catch {
    /* ignore */
  }
}

function clearAllTokens() {
  localStorage.removeItem(PORTAL_TOKEN_KEY);
  localStorage.removeItem(GOALS_TOKEN_KEY);
  try {
    document.cookie = "ava_session=; path=/; max-age=0; SameSite=Lax; Secure";
  } catch {
    /* ignore */
  }
}

function readAnyToken(): string {
  return localStorage.getItem(PORTAL_TOKEN_KEY) || localStorage.getItem(GOALS_TOKEN_KEY) || "";
}

type Props = {
  brandLabel: string;
  studioHref?: string;
  /** Extra login after account auth (e.g. goals API). */
  onAccountOk?: (email: string, password: string) => Promise<void>;
};

export function AuthBar({ brandLabel, studioHref, onAccountOk }: Props) {
  const [me, setMe] = useState<Me>(null);
  const [open, setOpen] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [status, setStatus] = useState("");

  async function refresh() {
    const t = readAnyToken();
    if (!t) {
      setMe(null);
      return;
    }
    writeAllTokens(t);
    try {
      const r = await fetch(`${API_BASE}/api/auth/me`, {
        headers: {
          Authorization: `Bearer ${t}`,
          "X-Guest-Id": deviceId(),
        },
        credentials: "include",
      });
      if (!r.ok) {
        setMe(null);
        return;
      }
      setMe(await r.json());
    } catch {
      setMe(null);
    }
  }

  useEffect(() => {
    refresh();
  }, []);

  async function login() {
    setStatus("Signing in…");
    const did = deviceId();
    try {
      const r = await fetch(`${API_BASE}/api/auth/login`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Guest-Id": did,
        },
        credentials: "include",
        body: JSON.stringify({
          email: email.trim(),
          password,
          device_id: did,
        }),
      });
      const j = await r.json().catch(() => ({}));
      if (!r.ok) {
        setStatus(String(j.detail || "Sign in failed"));
        return;
      }
      const tok = j.token || j.access_token || j.session_token;
      if (tok) writeAllTokens(tok);
      if (onAccountOk) {
        try {
          await onAccountOk(email.trim(), password);
        } catch {
          /* account OK is enough for header */
        }
      }
      setStatus("OK");
      setOpen(false);
      setPassword("");
      await refresh();
    } catch {
      setStatus("Network error");
    }
  }

  function logout() {
    clearAllTokens();
    setMe(null);
  }

  return (
    <div className={styles.authBar}>
      {me?.email ? (
        <>
          <span className={styles.authEmail}>{me.email}</span>
          {studioHref ? <Link href={studioHref}>Studio</Link> : null}
          <button type="button" className={styles.authBtn} onClick={logout}>
            Sign out
          </button>
        </>
      ) : (
        <button type="button" className={styles.authBtn} onClick={() => setOpen(true)}>
          Sign in
        </button>
      )}
      {open ? (
        <div className={styles.authModal} role="dialog" aria-modal="true" aria-label="Sign in">
          <h3>Sign in</h3>
          <p className={styles.brandLine}>Root Record account — branded for {brandLabel}</p>
          <label>
            Email
            <input
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              type="email"
              autoComplete="username"
              onKeyDown={(e) => e.key === "Enter" && login()}
            />
          </label>
          <label>
            Password
            <input
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              type="password"
              autoComplete="current-password"
              onKeyDown={(e) => e.key === "Enter" && login()}
            />
          </label>
          {status ? <p className={styles.status}>{status}</p> : null}
          <div className={styles.authActions}>
            <button type="button" className={`${styles.authBtn} ${styles.primary}`} onClick={login}>
              Sign in
            </button>
            <button type="button" className={styles.authBtn} onClick={() => setOpen(false)}>
              Cancel
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
