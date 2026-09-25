import type { NextConfig } from "next";

/**
 * Next.js configuration.
 *
 * The dev/prod server proxies all `/api/*` requests to the Spring Boot backend
 * running on http://localhost:8080, so the browser can call the API on the same
 * origin and avoid CORS during local development.
 */
const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: "http://localhost:8080/api/:path*",
      },
    ];
  },
};

export default nextConfig;
