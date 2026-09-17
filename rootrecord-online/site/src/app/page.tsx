import styles from "./page.module.css";
import DashboardClient from "@/components/DashboardClient";
import { fetchDeskJson } from "@/lib/desk-api";

export const revalidate = 30;

async function fetchAll() {
  const [status, solar, mc, kilauea, weather] = await Promise.all([
    fetchDeskJson("/api/status"),
    fetchDeskJson("/api/solar"),
    fetchDeskJson("/api/minecraft/status"),
    fetchDeskJson("/api/kilauea"),
    fetchDeskJson("/api/weather"),
  ]);
  return { status, solar, mc, kilauea, weather };
}

export default async function Dashboard() {
  const data = await fetchAll();
  return <DashboardClient initial={data as any} />;
}
