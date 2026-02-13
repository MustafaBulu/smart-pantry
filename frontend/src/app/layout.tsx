import type { Metadata } from "next";
import Link from "next/link";
import { Fraunces, Space_Grotesk } from "next/font/google";
import "./globals.css";
import React from "react";

const spaceGrotesk = Space_Grotesk({
  variable: "--font-body",
  subsets: ["latin"],
});

const fraunces = Fraunces({
  variable: "--font-display",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Smart Pantry Console",
  description: "Manage categories, marketplace products, and price history.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="tr">
      <body className={`${spaceGrotesk.variable} ${fraunces.variable} antialiased`}>
        <div className="min-h-screen bg-[radial-gradient(circle_at_top,#fdf7ee,#f7f4ef_55%,#efe7dc_100%)] text-[#101010]">
          <header className="border-b border-black/10 bg-white/70 backdrop-blur">
            <div className="mx-auto flex w-full max-w-6xl flex-wrap items-center justify-between gap-4 px-6 py-4">
              <div>
                <p className="text-xs uppercase tracking-[0.3em] text-[#9a5c00]">
                  Smart Pantry Console
                </p>
                <h1 className="display text-xl font-semibold">Yonetim Paneli</h1>
              </div>
              <nav className="flex flex-wrap items-center gap-3 text-sm">
                <Link className="rounded-full border border-black/10 px-4 py-2 text-[#6b655c] transition hover:bg-[#f4ede3]" href="/">
                  Kategoriler
                </Link>
                <Link className="rounded-full border border-black/10 px-4 py-2 text-[#6b655c] transition hover:bg-[#f4ede3]" href="/marketplace">
                  Marketplace
                </Link>
                <Link className="rounded-full border border-black/10 px-4 py-2 text-[#6b655c] transition hover:bg-[#f4ede3]" href="/search">
                  Arama
                </Link>
                <Link className="rounded-full border border-black/10 px-4 py-2 text-[#6b655c] transition hover:bg-[#f4ede3]" href="/history">
                  Fiyat Gecmisi
                </Link>
              </nav>
            </div>
          </header>
          <main className="mx-auto w-full max-w-6xl px-6 py-10">{children}</main>
        </div>
      </body>
    </html>
  );
}