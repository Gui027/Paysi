import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  async rewrites() {
    // const apiUrl = process.env.PAYSI_API_URL ?? "http://localhost:8080";
    const apiUrl = "http://186.218.63.106:8090";
    return [{ source: "/api/:path*", destination: `${apiUrl}/:path*` }];
  },
};

export default nextConfig;
