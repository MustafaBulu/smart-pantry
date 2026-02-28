import type { Metadata, Viewport } from "next";
import Link from "next/link";
import { Bebas_Neue, Fraunces, Space_Grotesk } from "next/font/google";
import "./globals.css";
import React from "react";
import HeaderLocationInputs from "@/components/HeaderLocationInputs";
import DailyPriceRefreshButton from "@/components/DailyPriceRefreshButton";

const spaceGrotesk = Space_Grotesk({
  variable: "--font-body",
  subsets: ["latin"],
});

const fraunces = Fraunces({
  variable: "--font-display",
  subsets: ["latin"],
});

const bebasNeue = Bebas_Neue({
  variable: "--font-category",
  subsets: ["latin"],
  weight: "400",
});

export const metadata: Metadata = {
  title: "Smart Pantry Console",
  description: "Manage categories, marketplace products, and price history.",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="tr">
      <body className={`${spaceGrotesk.variable} ${fraunces.variable} ${bebasNeue.variable} overflow-x-hidden antialiased`}>
        <div className="min-h-screen bg-[radial-gradient(circle_at_top,#fdf7ee,#f7f4ef_55%,#efe7dc_100%)] text-[#101010]">
          <header className="sticky top-0 z-50 border-b border-black/10 bg-white/70 backdrop-blur">
            <div className="mx-auto flex w-full max-w-7xl flex-col items-stretch gap-3 px-3 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4 sm:px-6">
              <Link href="/" className="group w-full sm:w-auto">
                <p className="text-xs uppercase tracking-[0.3em] text-[#9a5c00]">
                  Smart Pantry Console
                </p>
                <h1 className="display text-xl font-semibold group-hover:text-[#111]">
                  Yonetim Paneli
                </h1>
              </Link>
              <HeaderLocationInputs />
              <div id="header-actions" className="flex w-full flex-wrap items-start justify-start gap-2 sm:w-auto sm:justify-end">
                <DailyPriceRefreshButton />
              </div>
            </div>
          </header>
          <main className="mx-auto w-full max-w-7xl px-3 py-6 sm:px-6 sm:py-10">{children}</main>
        </div>
      </body>
    </html>
  );
}
