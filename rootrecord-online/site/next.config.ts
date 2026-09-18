import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Desk /api/* is handled by src/app/api/[...path]/route.ts → origin.avaivy.cloud.
  // No external rewrite — keeps the App Router allowlist and OFFLINE JSON path.
};

export default nextConfig;
