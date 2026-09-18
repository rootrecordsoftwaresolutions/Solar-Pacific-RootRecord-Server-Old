"use client";

import { useEffect, useState } from "react";
import { fetchDeskJson, isOffline, OFFLINE } from "@/lib/desk-api";

const ORIGIN_BOARD = "https://origin.avaivy.cloud/status";

type Phase = "loading" | "live" | "offline";

export default function StatusBoard({ title }: { title: string }) {
  const [phase, setPhase] = useState<Phase>("loading");

  useEffect(() => {
    let stop = false;
    const tick = async () => {
      const data = await fetchDeskJson("/api/status", 6000);
      if (!stop) setPhase(isOffline(data) ? "offline" : "live");
    };
    void tick();
    const iv = setInterval(() => void tick(), 30_000);
    return () => {
      stop = true;
      clearInterval(iv);
    };
  }, []);

  if (phase === "loading") {
    return (
      <main
        style={{
          minHeight: "100dvh",
          background: "#0a0e14",
          color: "#f4efe6",
          padding: "3rem 1.25rem",
          fontFamily: "Georgia, serif",
        }}
      >
        <p
          style={{
            letterSpacing: "0.12em",
            textTransform: "uppercase",
            fontSize: "0.72rem",
            color: "#8a9bb0",
          }}
        >
          {title}
        </p>
        <h1 style={{ fontWeight: 500, fontSize: "2.2rem" }}>Checking desk…</h1>
      </main>
    );
  }

  if (phase === "offline") {
    return (
      <main
        style={{
          minHeight: "100dvh",
          background: "#0a0e14",
          color: "#f4efe6",
          padding: "3rem 1.25rem",
          fontFamily: "Georgia, serif",
        }}
      >
        <p
          style={{
            letterSpacing: "0.12em",
            textTransform: "uppercase",
            fontSize: "0.72rem",
            color: "#ff6a2a",
          }}
        >
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
