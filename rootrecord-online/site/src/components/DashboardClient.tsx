"use client";

import { useEffect, useState } from "react";
import styles from "../app/page.module.css";
import StatTile from "./StatTile";
import DataCard from "./DataCard";
import GuestChat from "./GuestChat";

import { isOffline, OFFLINE } from "@/lib/desk-api";

interface Props {
  initial: { status: any; solar: any; mc: any; kilauea?: any; weather?: any };
}

export default function DashboardClient({ initial }: Props) {
  const [data, setData] = useState(initial);
  const [lastRefresh, setLastRefresh] = useState(new Date());

  // Client-side poll every 30s
  useEffect(() => {
    const iv = setInterval(async () => {
      try {
        const [sr, mr, mcr, kr, wr] = await Promise.all([
          fetch("/api/status"),
          fetch("/api/solar"),
          fetch("/api/minecraft/status"),
          fetch("/api/kilauea"),
          fetch("/api/weather"),
        ]);
        const parse = async (r: Response) => {
          try {
            const j = await r.json();
            if (!r.ok || j?.status === OFFLINE) return { status: OFFLINE };
            return j;
          } catch {
            return { status: OFFLINE };
          }
        };
        const [status, solar, mc, kilauea, weather] = await Promise.all([
          parse(sr), parse(mr), parse(mcr), parse(kr), parse(wr),
        ]);
        setData({ status, solar, mc, kilauea, weather });
        setLastRefresh(new Date());
      } catch {}
    }, 30_000);
    return () => clearInterval(iv);
  }, []);

  const { status, solar, mc, kilauea, weather } = data;
  const deskUp = status && !isOffline(status);
  const hstTime = new Intl.DateTimeFormat("en-US", {
    timeZone: "Pacific/Honolulu",
    hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false,
  }).format(lastRefresh);

  return (
    <div className={styles.wrap}>
      <div className={styles.content}>
        {/* Status row */}
        <div className={styles.topRow}>
          <StatTile
            label="Root Server"
            value={deskUp ? "Online" : OFFLINE}
            pill={deskUp ? "green" : "red"}
            sub={deskUp ? `CPU ${status.cpu_pct ?? "?"}%  ·  RAM ${status.mem_pct ?? "?"}%` : OFFLINE}
          />
          <StatTile
            label="Uptime"
            value={deskUp ? `${Math.floor(status.uptime_s / 3600)}h ${Math.floor((status.uptime_s % 3600) / 60)}m` : OFFLINE}
            pill="cyan"
            sub={deskUp ? (status?.hostname ?? "") : OFFLINE}
          />
          <StatTile
            label="Minecraft"
            value={mc && !isOffline(mc) && mc?.online ? `${mc.players?.online ?? 0} / ${mc.players?.max ?? 0}` : OFFLINE}
            pill={mc && !isOffline(mc) && mc?.online ? "green" : "red"}
            sub="play.rootmc.net"
          />
          <StatTile
            label="HST"
            value={hstTime}
            pill="amber"
            sub={`Last refresh ${hstTime}`}
          />
        </div>

        {/* Solar */}
        <div className={styles.section}>
          <h2 className={styles.sectionTitle}>Solar</h2>
          <div className={styles.grid2}>
            <DataCard title="Solar Status" data={solar} keys={["voltage", "current", "power_w", "battery_pct", "state"]} />
            <DataCard title="Today's Generation" data={solar} keys={["kwh_today", "kwh_total", "panel_temp_c"]} />
          </div>
        </div>

        {/* Kīlauea */}
        <div className={styles.section}>
          <h2 className={styles.sectionTitle}>Kīlauea · USGS</h2>
          <DataCard
            title="Latest Activity"
            data={kilauea}
            keys={["alert_level", "multiplier", "events_nearby", "max_magnitude", "updated_at"]}
            placeholder="Kīlauea data — available when Ava is online."
          />
        </div>

        {/* NOAA Weather */}
        <div className={styles.section}>
          <h2 className={styles.sectionTitle}>NOAA Weather · Big Island</h2>
          <DataCard
            title="Current Conditions"
            data={weather}
            keys={["period", "temperature_f", "forecast", "alerts_active"]}
            placeholder="NOAA weather — available when Ava is online."
          />
        </div>

        {/* Minecraft detail */}
        <div className={styles.section}>
          <h2 className={styles.sectionTitle}>RootMC</h2>
          <DataCard title="Server Details" data={mc} keys={["version", "motd", "latency_ms"]} />
        </div>

        <div className={styles.section}>
          <h2 className={styles.sectionTitle}>Talk to Ava</h2>
          <GuestChat />
        </div>
      </div>
    </div>
  );
}
