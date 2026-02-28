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
    remotePatterns: [
      {
        protocol: "https",
        hostname: "yemeksepeti.dhmedia.io",
      },
      {
        protocol: "https",
        hostname: "images.migrosone.com",
      },
      {
        protocol: "https",
        hostname: "images.deliveryhero.io",
      },
    ],
  },
};

export default nextConfig;
