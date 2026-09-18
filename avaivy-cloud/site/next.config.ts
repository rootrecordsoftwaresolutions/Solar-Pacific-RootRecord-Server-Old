import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Low-RAM OmniBook: one static page at a time avoids worker OOM during prerender.
  experimental: {
    staticGenerationMaxConcurrency: 1,
    staticGenerationMinPagesPerWorker: 200,
  },
  async redirects() {
    return [
      { source: "/index", destination: "/media", permanent: false },
      { source: "/chat", destination: "/#talk", permanent: false },
      { source: "/chat/", destination: "/#talk", permanent: false },
    ];
  },
  async headers() {
    return [
      {
        source: "/live/embed",
        headers: [{ key: "Content-Security-Policy", value: "frame-ancestors *" }],
      },
    ];
  },
  async rewrites() {
    // Desk /api/* is handled by src/app/api/[...path]/route.ts → origin.avaivy.cloud.
    // Do not rewrite to an external host here (retired api.rootrecord.online caused
    // Vercel DNS_HOSTNAME_NOT_FOUND and skipped the App Router proxy entirely).
    return [
      { source: "/wiki", destination: "/wiki/index.html" },
      { source: "/wiki/", destination: "/wiki/index.html" },
      { source: "/wiki/build", destination: "/wiki/build.html" },
      { source: "/wiki/timeline", destination: "/wiki/timeline.html" },
      { source: "/wiki/events", destination: "/wiki/events.html" },
    ];
  },
};

export default nextConfig;
