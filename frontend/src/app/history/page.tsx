"use client";

import { useState } from "react";
import { API_BASE, request } from "@/lib/api";
import type {
  CategoryPriceSummaryResponse,
  PriceHistoryResponse,
} from "@/lib/types";

type MarketplaceCode = "YS" | "MG" | "";

export default function HistoryPage() {
  const [productId, setProductId] = useState("");
  const [categoryName, setCategoryName] = useState("");
  const [marketplaceCode, setMarketplaceCode] = useState<MarketplaceCode>("");
  const [productHistory, setProductHistory] = useState<PriceHistoryResponse[]>(
    []
  );
  const [categorySummary, setCategorySummary] = useState<
    CategoryPriceSummaryResponse[]
  >([]);
  const [status, setStatus] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const handleProductHistory = async () => {
    if (!productId.trim()) {
      setStatus("Urun ID gerekli.");
      return;
    }
    setBusy(true);
    setStatus(null);
    try {
      const params = new URLSearchParams();
      if (marketplaceCode) {
        params.set("marketplaceCode", marketplaceCode);
      }
      const suffix = params.toString() ? `?${params.toString()}` : "";
      const data = await request<PriceHistoryResponse[]>(
        `/products/${productId}/prices${suffix}`
      );
      setProductHistory(data);
      setStatus(`${data.length} fiyat kaydi getirildi.`);
    } catch (err) {
      setStatus(`Fiyat gecmisi hatasi: ${(err as Error).message}`);
    } finally {
      setBusy(false);
    }
  };

  const handleCategorySummary = async () => {
    if (!categoryName.trim()) {
      setStatus("Kategori adi gerekli.");
      return;
    }
    setBusy(true);
    setStatus(null);
    try {
      const params = new URLSearchParams();
      if (marketplaceCode) {
        params.set("marketplaceCode", marketplaceCode);
      }
      const suffix = params.toString() ? `?${params.toString()}` : "";
      const data = await request<CategoryPriceSummaryResponse[]>(
        `/categories/${encodeURIComponent(categoryName)}/prices${suffix}`
      );
      setCategorySummary(data);
      setStatus(`${data.length} urun ozeti getirildi.`);
    } catch (err) {
      setStatus(`Kategori ozeti hatasi: ${(err as Error).message}`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex flex-col gap-8">
      <section className="rounded-3xl border border-black/10 bg-white/80 p-6 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)] backdrop-blur">
        <div>
          <p className="text-xs uppercase tracking-[0.3em] text-[#9a5c00]">
            Fiyat Gecmisi
          </p>
          <h2 className="display text-3xl font-semibold">
            Son 1 yilin fiyat hareketleri
          </h2>
          <p className="mt-2 text-sm text-[#6b655c]">
            Urun ve kategori bazinda fiyat trendlerini inceleyin.
          </p>
        </div>
        <div className="mt-5 flex flex-wrap items-center gap-3 text-xs text-[#6b655c]">
          <span className="rounded-full border border-black/10 bg-white px-3 py-1">
            API: {API_BASE}
          </span>
          <select
            className="rounded-full border border-black/10 bg-white px-3 py-1 text-xs focus:outline-none"
            value={marketplaceCode}
            onChange={(event) => setMarketplaceCode(event.target.value as MarketplaceCode)}
          >
            <option value="">Marketplace (hepsi)</option>
            <option value="YS">YS - Yemeksepeti</option>
            <option value="MG">MG - Migros</option>
          </select>
          <span className="flex items-center gap-2 rounded-full border border-black/10 bg-white px-3 py-1">
            <img
              src="/yemeksepeti-logo.png"
              alt="Yemeksepeti"
              className="h-4 w-4 rounded-sm object-contain"
            />
            Yemeksepeti
          </span>
          <span className="flex items-center gap-2 rounded-full border border-black/10 bg-white px-3 py-1">
            <img
              src="/migros-logo.png"
              alt="Migros"
              className="h-4 w-4 rounded-sm object-contain"
            />
            Migros
          </span>
        </div>
      </section>

      <section className="grid gap-6 lg:grid-cols-[1fr_1fr]">
        <div className="rounded-3xl border border-black/10 bg-white p-5 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)]">
          <h3 className="display text-xl">Urun Fiyat Gecmisi</h3>
          <p className="mt-1 text-xs text-[#6b655c]">
            Varsayilan olarak son 1 yil getiriliyor.
          </p>
          <div className="mt-4 flex flex-wrap gap-3">
            <input
              className="flex-1 rounded-xl border border-black/10 bg-white px-3 py-2 text-sm focus:border-amber-400 focus:outline-none focus:ring-4 focus:ring-(--ring)"
              placeholder="Urun ID"
              value={productId}
              onChange={(event) => setProductId(event.target.value)}
            />
            <button
              className="rounded-xl bg-[#111] px-4 py-2 text-sm font-semibold text-white transition hover:bg-black/80"
              type="button"
              onClick={handleProductHistory}
              disabled={busy}
            >
              Getir
            </button>
          </div>
          <div className="mt-4 space-y-2 text-sm">
            {productHistory.length === 0 ? (
              <p className="text-[#6b655c]">Henuz kayit yok.</p>
            ) : (
              productHistory.slice(0, 12).map((item) => (
                <div
                  key={item.id}
                  className="rounded-2xl border border-black/5 bg-[#f9f4ee] px-3 py-2"
                >
                  <div className="flex items-center justify-between text-xs text-[#6b655c]">
                    <span className="flex items-center gap-2">
                      {item.marketplaceCode === "YS" && (
                        <img
                          src="/yemeksepeti-logo.png"
                          alt="Yemeksepeti"
                          className="h-4 w-4 rounded-sm object-contain"
                        />
                      )}
                      {item.marketplaceCode === "MG" && (
                        <img
                          src="/migros-logo.png"
                          alt="Migros"
                          className="h-4 w-4 rounded-sm object-contain"
                        />
                      )}
                      <span>
                        {item.marketplaceCode === "YS"
                          ? "Yemeksepeti"
                          : item.marketplaceCode === "MG"
                            ? "Migros"
                            : item.marketplaceCode}
                      </span>
                    </span>
                    <span>{item.recordedAt}</span>
                  </div>
                  <div className="mt-1 text-sm font-semibold text-[#111]">
                    {item.price}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="rounded-3xl border border-black/10 bg-white p-5 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)]">
          <h3 className="display text-xl">Kategori Ozeti</h3>
          <p className="mt-1 text-xs text-[#6b655c]">
            Her urun icin min/max/ortalama degerleri.
          </p>
          <div className="mt-4 flex flex-wrap gap-3">
            <input
              className="flex-1 rounded-xl border border-black/10 bg-white px-3 py-2 text-sm focus:border-amber-400 focus:outline-none focus:ring-4 focus:ring-(--ring)"
              placeholder="Kategori adi"
              value={categoryName}
              onChange={(event) => setCategoryName(event.target.value)}
            />
            <button
              className="rounded-xl border border-[#d97706] px-4 py-2 text-sm font-semibold text-[#9a5c00] transition hover:bg-amber-50"
              type="button"
              onClick={handleCategorySummary}
              disabled={busy}
            >
              Ozeti Getir
            </button>
          </div>
          <div className="mt-4 space-y-2 text-sm">
            {categorySummary.length === 0 ? (
              <p className="text-[#6b655c]">Henuz ozet yok.</p>
            ) : (
              categorySummary.slice(0, 12).map((item) => (
                <div
                  key={item.productId}
                  className="rounded-2xl border border-black/5 bg-[#f9f4ee] px-3 py-2"
                >
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-[#111]">
                      {item.productName}
                    </span>
                    <span className="text-xs text-[#6b655c]">
                      {item.lastRecordedAt ?? "-"}
                    </span>
                  </div>
                  <div className="mt-1 grid grid-cols-3 gap-2 text-xs text-[#6b655c]">
                    <span>Min: {item.minPrice}</span>
                    <span>Max: {item.maxPrice}</span>
                    <span>Ort: {item.avgPrice}</span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </section>

      <section className="rounded-3xl border border-black/10 bg-white p-4 text-sm text-[#6b655c] shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)]">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <span>Durum: {status ?? "Hazir."}</span>
          <span>Veriler son 1 yil icin otomatik geliyor.</span>
        </div>
      </section>
    </div>
  );
}
