"use client";

import { useEffect, useState } from "react";
import { fetchDeskJson, isOffline, OFFLINE, PUBLIC_API } from "@/lib/desk-api";

const ORIGIN_BOARD = `${PUBLIC_API.replace("api.rootrecord.online", "origin.avaivy.cloud")}/status`;

export default function StatusBoard({ title }: { title: string }) {
  const [offline, setOffline] = useState(false);

  useEffect(() => {
    let stop = false;
    const tick = async () => {
      const data = await fetchDeskJson("/api/status", 6000);
      if (!stop) setOffline(isOffline(data));
    };
    void tick();
    const iv = setInterval(() => void tick(), 30_000);
    return () => {
      stop = true;
      clearInterval(iv);
    };
  }, []);

  if (offline) {
    return (
      <main style={{ minHeight: "100dvh", background: "#0a0e14", color: "#f4efe6", padding: "3rem 1.25rem", fontFamily: "Georgia, serif" }}>
        <p style={{ letterSpacing: "0.12em", textTransform: "uppercase", fontSize: "0.72rem", color: "#ff6a2a" }}>
          {title}
        </p>
        <h1 style={{ fontWeight: 500, fontSize: "2.2rem" }}>{OFFLINE}</h1>
        <p>The desk API tunnel is down. Live numbers will return when the Root Server is up.</p>
      </main>
    );
  }

  return (
    <iframe
      src={ORIGIN_BOARD}
      title={title}
      style={{
        display: "block",
        width: "100%",
        height: "100dvh",
        border: 0,
        background: "#0a0e14",
      }}
    />
  );
}
