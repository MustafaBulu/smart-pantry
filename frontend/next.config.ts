import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  allowedDevOrigins: [
    "192.168.1.3",
    "192.168.1.3:3000",
    "http://192.168.1.3",
    "http://192.168.1.3:3000",
    "https://192.168.1.3",
    "https://192.168.1.3:3000",
    "localhost",
    "127.0.0.1",
    "0.0.0.0",
  ],
  images: {
    unoptimized: true,
    domains: [
      "yemeksepeti.dhmedia.io",
      "images.migrosone.com",
      "images.deliveryhero.io",
    ],
    remotePatterns: [
      {
        protocol: "https",
        hostname: "yemeksepeti.dhmedia.io",
        pathname: "/**",
      },
      {
        protocol: "https",
        hostname: "images.migrosone.com",
        pathname: "/**",
      },
      {
        protocol: "https",
        hostname: "images.deliveryhero.io",
        pathname: "/**",
      },
    ],
  },
};

export default nextConfig;
