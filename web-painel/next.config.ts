import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  async rewrites() {
    const apiUrl = process.env.PAYSI_API_URL ?? "http://localhost:8080";
    return [{ source: "/api/:path*", destination: `${apiUrl}/:path*` }];
  },
};

export default nextConfig;
