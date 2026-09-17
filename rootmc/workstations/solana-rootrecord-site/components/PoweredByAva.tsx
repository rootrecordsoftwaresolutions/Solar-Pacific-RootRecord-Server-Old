'use client';

import { useEffect, useState } from 'react';

type PoweredByPayload = {
  ok?: boolean;
  cpu?: number | null;
  ram?: number | null;
  soc?: number | null;
};

const API = 'https://ava.rootmc.net/api/powered-by';
const API_FALLBACK = 'https://rootrecord.info/ava/status/api/powered-by';
const HREF = 'https://ava.rootmc.net/';

function fmt(n: number | null | undefined) {
  if (n == null || Number.isNaN(Number(n))) return '—';
  return `${Math.round(Number(n) * 10) / 10}%`;
}

/** Sitewide strip: Powered by Ava + last-hour CPU | RAM | SOC% */
export function PoweredByAva() {
  const [data, setData] = useState<PoweredByPayload | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      for (const url of [API, API_FALLBACK]) {
        try {
          const r = await fetch(url, { cache: 'no-store' });
          if (!r.ok) continue;
          const j = (await r.json()) as PoweredByPayload;
          if (!cancelled && j?.ok) {
            setData(j);
            return;
          }
        } catch {
          /* try next */
        }
      }
      if (!cancelled) setData(null);
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div
      data-testid="powered-by-ava"
      className="border-t border-border bg-ink-900/60"
    >
      <div className="container py-3 flex flex-wrap items-center justify-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
        <a
          href={HREF}
          className="font-semibold text-sol-green hover:underline underline-offset-4"
          rel="noopener noreferrer"
        >
          Powered by Ava
        </a>
        <span className="opacity-40" aria-hidden>
          ·
        </span>
        <span className="tabular-nums text-foreground/80" title="Last hour averages">
          CPU {fmt(data?.ok ? data.cpu : null)}
          <span className="opacity-40 px-1">|</span>
          RAM {fmt(data?.ok ? data.ram : null)}
          <span className="opacity-40 px-1">|</span>
          SOC {fmt(data?.ok ? data.soc : null)}
        </span>
      </div>
    </div>
  );
}
