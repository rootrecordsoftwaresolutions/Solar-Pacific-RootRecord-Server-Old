import type { NextConfig } from "next";
import { PUBLIC_API } from "./src/lib/desk-api";

const nextConfig: NextConfig = {
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${PUBLIC_API}/api/:path*` }];
  },
};

export default nextConfig;
