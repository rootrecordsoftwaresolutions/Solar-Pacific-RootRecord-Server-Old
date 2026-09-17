"use client";

import { usePathname } from "next/navigation";
import { useState } from "react";
import ChatWidget from "@/components/ChatWidget";
import styles from "./ChatWidget.module.css";

export default function ChatDock() {
  const path = usePathname() || "/";
  const [open, setOpen] = useState(false);
  if (path === "/" || path === "/live/embed" || path === "/wiki" || path.startsWith("/wiki/")) {
    return null;
  }
  return (
    <div className={styles.dock}>
      {open ? (
        <div className={styles.dockPanel}>
          <ChatWidget />
        </div>
      ) : null}
      <button type="button" className={styles.dockBtn} onClick={() => setOpen((v) => !v)}>
        {open ? "Close chat" : "Talk with Ava"}
      </button>
    </div>
  );
}
