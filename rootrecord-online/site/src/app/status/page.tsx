import type { Metadata } from "next";
import Link from "next/link";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "Status — Root Record",
  description: "Full solar desk and host status for the HI Pacific Solar Root Server.",
};

export default function StatusPage() {
  return (
    <div style={{ display: "flex", flexDirection: "column", minHeight: "calc(100dvh - 4rem)" }}>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: "1rem",
          padding: "0.75rem 1.25rem",
          borderBottom: "1px solid rgba(244, 239, 230, 0.12)",
          background: "#0a0e14",
          color: "#f4efe6",
        }}
      >
        <Link href="/" style={{ color: "#7ec8e3", textDecoration: "none", fontSize: "0.9rem" }}>
          ← Live
        </Link>
        <span style={{ fontSize: "0.85rem", opacity: 0.75 }}>Full status desk</span>
        <a
          href="https://www.avaivy.cloud/status"
          style={{ marginLeft: "auto", color: "#8a9bb0", fontSize: "0.8rem" }}
        >
          Open on Ava Ivy
        </a>
      </div>
      <iframe
        src="https://origin.avaivy.cloud/status"
        title="Host status desk"
        style={{
          flex: 1,
          width: "100%",
          minHeight: "70dvh",
          border: 0,
          background: "#0a0e14",
        }}
      />
    </div>
  );
}
